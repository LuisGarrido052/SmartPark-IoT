package cl.inacap.estacionamiento.data.repository

import cl.inacap.estacionamiento.data.local.SmartParkDb
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.security.PasswordHasher
import cl.inacap.estacionamiento.security.SessionManager
import cl.inacap.estacionamiento.security.Validaciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ResultadoAuth {
    data class Exito(val sesion: Sesion) : ResultadoAuth()
    data class Error(val mensaje: String) : ResultadoAuth()
}

/**
 * Autenticación local con SQLite.
 * Controles de seguridad: hash PBKDF2, bloqueo temporal tras 5 intentos fallidos,
 * mensaje de error genérico (no revela si el usuario existe) y sesión cifrada con expiración.
 */
class AuthRepository(
    private val db: SmartParkDb,
    private val sesiones: SessionManager
) {

    suspend fun login(usuario: String, password: CharArray): ResultadoAuth = withContext(Dispatchers.IO) {
        try {
            val fila = db.buscarUsuario(usuario.trim())
                ?: return@withContext ResultadoAuth.Error(MENSAJE_GENERICO).also {
                    db.registrarEvento("SEGURIDAD", "Intento de acceso con usuario inexistente")
                }

            val ahora = System.currentTimeMillis()
            if (fila.bloqueadoHasta > ahora) {
                val segundos = (fila.bloqueadoHasta - ahora) / 1000 + 1
                return@withContext ResultadoAuth.Error("Cuenta bloqueada temporalmente. Intenta en $segundos s")
            }

            if (!PasswordHasher.verificar(password, fila.hash, fila.sal)) {
                val intentos = fila.intentosFallidos + 1
                if (intentos >= MAX_INTENTOS) {
                    db.actualizarIntentos(fila.id, 0, ahora + BLOQUEO_MS)
                    db.registrarEvento("SEGURIDAD", "Cuenta '${fila.usuario}' bloqueada por $MAX_INTENTOS intentos fallidos")
                    return@withContext ResultadoAuth.Error("Demasiados intentos. Cuenta bloqueada por ${BLOQUEO_MS / 1000} s")
                }
                db.actualizarIntentos(fila.id, intentos, 0)
                return@withContext ResultadoAuth.Error("$MENSAJE_GENERICO (${MAX_INTENTOS - intentos} intentos restantes)")
            }

            db.actualizarIntentos(fila.id, 0, 0)
            val sesion = Sesion(fila.usuario, fila.rol, fila.patente, ahora + SessionManager.DURACION_MS)
            sesiones.guardar(sesion)
            db.registrarEvento("ACCESO", "Inicio de sesión: ${fila.usuario} (${fila.rol.etiqueta})")
            ResultadoAuth.Exito(sesion)
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun registrar(
        usuario: String,
        password: CharArray,
        rol: Rol,
        patente: String,
        codigoOperador: String
    ): ResultadoAuth = withContext(Dispatchers.IO) {
        try {
            val nombre = usuario.trim()
            if (db.existeUsuario(nombre)) return@withContext ResultadoAuth.Error("Ese nombre de usuario ya existe")
            // Solo personal autorizado puede crear cuentas de Operador (mínimo privilegio)
            if (rol == Rol.OPERADOR && codigoOperador != CODIGO_OPERADOR) {
                return@withContext ResultadoAuth.Error("Código de autorización de operador incorrecto")
            }
            val patenteFinal = if (rol == Rol.CONDUCTOR) Validaciones.normalizarPatente(patente) else ""
            val id = db.crearUsuario(nombre, password, rol, patenteFinal)
            if (id < 0) return@withContext ResultadoAuth.Error("No se pudo crear la cuenta")
            db.registrarEvento("ACCESO", "Nueva cuenta creada: $nombre (${rol.etiqueta})")
            val sesion = Sesion(nombre, rol, patenteFinal, System.currentTimeMillis() + SessionManager.DURACION_MS)
            sesiones.guardar(sesion)
            ResultadoAuth.Exito(sesion)
        } finally {
            password.fill('\u0000')
        }
    }

    fun sesionActual(): Sesion? = sesiones.obtener()

    fun cerrarSesion() = sesiones.cerrar()

    companion object {
        const val MAX_INTENTOS = 5
        const val BLOQUEO_MS = 60_000L
        private const val MENSAJE_GENERICO = "Usuario o contraseña incorrectos"
        /** Código que entrega el administrador del recinto para crear cuentas de Operador. */
        const val CODIGO_OPERADOR = "OT-2026"
    }
}
