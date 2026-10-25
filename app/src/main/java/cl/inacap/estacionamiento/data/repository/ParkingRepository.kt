package cl.inacap.estacionamiento.data.repository

import cl.inacap.estacionamiento.comm.ConexionIoT
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.comm.ProtocoloArduino
import cl.inacap.estacionamiento.comm.ProtocoloTelefono
import cl.inacap.estacionamiento.comm.TipoEnlace
import cl.inacap.estacionamiento.data.local.SmartParkDb
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Evento
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.security.CanalSeguro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Fuente única de verdad del estacionamiento (patrón Repository).
 *
 * Reglas de autoridad:
 *  - El teléfono del OPERADOR es la autoridad: valida reservas y comandos, guarda en SQLite
 *    y difunde el estado al Conductor y a la placa Arduino.
 *  - El CONDUCTOR conectado al Operador envía solicitudes y muestra el estado que recibe.
 *  - Si el Conductor no está conectado a otro teléfono, trabaja en modo local (demostración).
 */
class ParkingRepository(private val db: SmartParkDb) {

    /** Toda la lógica corre en un único hilo: evita condiciones de carrera (ej. dos reservas simultáneas). */
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    /** Los sockets bloquean al leer/esperar, por eso corren en el pool de E/S. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _plazas = MutableStateFlow<List<Plaza>>(emptyList())
    val plazas: StateFlow<List<Plaza>> = _plazas

    private val _barreras = MutableStateFlow(Barrera.entries.associateWith { false })
    val barreras: StateFlow<Map<Barrera, Boolean>> = _barreras

    private val _eventos = MutableStateFlow<List<Evento>>(emptyList())
    val eventos: StateFlow<List<Evento>> = _eventos

    private val _filtroEventos = MutableStateFlow<String?>(null)
    val filtroEventos: StateFlow<String?> = _filtroEventos

    private val _enlaceTelefono = MutableStateFlow<ConexionIoT?>(null)
    val enlaceTelefono: StateFlow<ConexionIoT?> = _enlaceTelefono

    private val _enlaceArduino = MutableStateFlow<ConexionIoT?>(null)
    val enlaceArduino: StateFlow<ConexionIoT?> = _enlaceArduino

    /** Avisos breves para mostrar en pantalla (Snackbar). */
    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val avisos: SharedFlow<String> = _avisos

    /** PIN de vinculación que muestra el Operador. */
    val pinOperador: String = CanalSeguro.generarPin()

    private var canal: CanalSeguro? = null
    @Volatile private var sesion: Sesion? = null
    private val trabajosEnlace = mutableMapOf<TipoEnlace, Job>()
    private val cierresAutomaticos = mutableMapOf<Barrera, Job>()

    init {
        scope.launch {
            _plazas.value = db.leerPlazas()
            refrescarEventos()
        }
        // Temporizador: libera las reservas vencidas (solo el teléfono con autoridad)
        scope.launch {
            while (true) {
                delay(1_000)
                if (esAutoridad()) liberarReservasVencidas()
            }
        }
    }

    private val rol: Rol? get() = sesion?.rol

    fun iniciarSesion(s: Sesion) = scope.launch { sesion = s }

    fun cerrarSesion() = scope.launch {
        desconectarInterno(TipoEnlace.TELEFONO)
        desconectarInterno(TipoEnlace.ARDUINO)
        sesion = null
    }

    private fun telefonoConectado() = _enlaceTelefono.value?.estado?.value?.fase == FaseConexion.CONECTADO

    /** ¿Este teléfono decide el estado? (Operador, o Conductor sin otro teléfono conectado) */
    fun esAutoridad(): Boolean = rol == Rol.OPERADOR || !telefonoConectado()

    // ===================== Conexiones =====================

    /**
     * Activa un enlace. Para TELEFONO se exige PIN: con él se derivan las llaves de cifrado y firma.
     */
    fun conectar(enlace: ConexionIoT, pin: String? = null) = scope.launch {
        desconectarInterno(enlace.tipo)
        if (enlace.tipo == TipoEnlace.TELEFONO) {
            canal = CanalSeguro(pin ?: pinOperador)
            _enlaceTelefono.value = enlace
        } else {
            _enlaceArduino.value = enlace
        }
        enlace.iniciar(io)
        trabajosEnlace[enlace.tipo] = scope.launch {
            launch {
                enlace.estado.collect { e ->
                    if (e.fase == FaseConexion.CONECTADO) alConectar(enlace)
                }
            }
            enlace.lineas.collect { linea ->
                try {
                    if (enlace.tipo == TipoEnlace.TELEFONO) procesarTelefono(linea) else procesarArduino(linea)
                } catch (e: Exception) {
                    evento("CONEXION", "Mensaje no procesado: ${e.message}")
                }
            }
        }
        evento("CONEXION", "Iniciando enlace ${enlace.medio}")
    }

    fun desconectar(tipo: TipoEnlace) = scope.launch { desconectarInterno(tipo) }

    private fun desconectarInterno(tipo: TipoEnlace) {
        trabajosEnlace.remove(tipo)?.cancel()
        val flujo = if (tipo == TipoEnlace.TELEFONO) _enlaceTelefono else _enlaceArduino
        flujo.value?.let {
            it.cerrar()
            evento("CONEXION", "Enlace ${it.medio} cerrado")
        }
        flujo.value = null
    }

    private suspend fun alConectar(enlace: ConexionIoT) {
        evento("CONEXION", "Enlace establecido: ${enlace.medio}")
        when (enlace.tipo) {
            TipoEnlace.ARDUINO -> enlace.enviar(ProtocoloArduino.PEDIR_ESTADO)
            TipoEnlace.TELEFONO ->
                if (rol == Rol.OPERADOR) difundirEstado()
                else enviarTelefono(ProtocoloTelefono.mensaje(ProtocoloTelefono.SOLICITAR_ESTADO))
        }
    }

    private suspend fun enviarTelefono(json: JSONObject) {
        val enlace = _enlaceTelefono.value ?: return
        val c = canal ?: return
        enlace.enviar(c.proteger(json))
    }

    private suspend fun enviarArduino(linea: String) {
        _enlaceArduino.value?.enviar(linea)
    }

    private suspend fun difundirEstado() {
        if (rol != Rol.OPERADOR) return
        enviarTelefono(ProtocoloTelefono.estado(_plazas.value, _barreras.value))
    }

    // ===================== Mensajes entrantes =====================

    private suspend fun procesarTelefono(linea: String) {
        val json = try {
            canal?.abrir(linea) ?: return
        } catch (e: Exception) {
            // Mensaje alterado, con PIN distinto, antiguo o repetido: se rechaza y se audita
            val motivo = (e as? SecurityException)?.message ?: "MENSAJE_INVALIDO"
            evento("SEGURIDAD", "Trama rechazada: $motivo")
            return
        }
        val usuario = json.optString("usuario", "conductor")
        when (json.optString("tipo")) {
            ProtocoloTelefono.SOLICITAR_ESTADO -> difundirEstado()
            ProtocoloTelefono.ESTADO -> if (rol == Rol.CONDUCTOR) {
                _plazas.value = ProtocoloTelefono.leerPlazas(json)
                _barreras.value = ProtocoloTelefono.leerBarreras(json)
                db.guardarPlazas(_plazas.value)
            }
            ProtocoloTelefono.RESERVAR -> if (rol == Rol.OPERADOR) {
                val r = aplicarReserva(json.getInt("plaza"), json.optString("patente"), usuario)
                responder(r)
            }
            ProtocoloTelefono.CANCELAR -> if (rol == Rol.OPERADOR) {
                responder(aplicarCancelacion(json.getInt("plaza"), json.optString("patente"), usuario))
            }
            ProtocoloTelefono.ABRIR_BARRERA -> if (rol == Rol.OPERADOR) {
                val barrera = Barrera.valueOf(json.getString("barrera"))
                responder(aplicarAperturaConductor(barrera, json.optString("patente")))
            }
            ProtocoloTelefono.RESPUESTA -> _avisos.emit(json.optString("mensaje"))
        }
    }

    private suspend fun responder(mensaje: String) {
        enviarTelefono(ProtocoloTelefono.mensaje(ProtocoloTelefono.RESPUESTA).put("mensaje", mensaje))
    }

    private suspend fun procesarArduino(linea: String) {
        when (val m = ProtocoloArduino.interpretar(linea)) {
            is ProtocoloArduino.Mensaje.Sensor -> aplicarSensor(m.plazaId, m.ocupada)
            is ProtocoloArduino.Mensaje.EstadoBarrera -> {
                if (_barreras.value[m.barrera] != m.abierta) {
                    _barreras.update { it + (m.barrera to m.abierta) }
                    evento("BARRERA", "Barrera ${m.barrera.etiqueta} ${if (m.abierta) "ABIERTA" else "CERRADA"} (Arduino)")
                    difundirEstado()
                }
            }
            null -> Unit // Líneas de depuración del Arduino se ignoran
        }
    }

    // ===================== Acciones públicas =====================

    /** Conductor: reservar una plaza libre por 15 minutos. */
    fun reservar(plazaId: Int) = scope.launch {
        val s = sesion ?: return@launch
        if (esAutoridad()) {
            _avisos.emit(aplicarReserva(plazaId, s.patente, s.nombreUsuario))
        } else {
            enviarTelefono(
                ProtocoloTelefono.mensaje(ProtocoloTelefono.RESERVAR)
                    .put("plaza", plazaId).put("patente", s.patente).put("usuario", s.nombreUsuario)
            )
        }
    }

    fun cancelarReserva(plazaId: Int) = scope.launch {
        val s = sesion ?: return@launch
        if (esAutoridad()) {
            _avisos.emit(aplicarCancelacion(plazaId, s.patente, s.nombreUsuario))
        } else {
            enviarTelefono(
                ProtocoloTelefono.mensaje(ProtocoloTelefono.CANCELAR)
                    .put("plaza", plazaId).put("patente", s.patente).put("usuario", s.nombreUsuario)
            )
        }
    }

    /** Conductor: solicitar apertura de barrera (requiere reserva activa o vehículo dentro). */
    fun solicitarApertura(barrera: Barrera) = scope.launch {
        val s = sesion ?: return@launch
        if (esAutoridad()) {
            _avisos.emit(aplicarAperturaConductor(barrera, s.patente))
        } else {
            enviarTelefono(
                ProtocoloTelefono.mensaje(ProtocoloTelefono.ABRIR_BARRERA)
                    .put("barrera", barrera.name).put("patente", s.patente).put("usuario", s.nombreUsuario)
            )
        }
    }

    /** Operador: anulación manual de barrera (abrir/cerrar). */
    fun operarBarrera(barrera: Barrera, abrir: Boolean) = scope.launch {
        if (rol != Rol.OPERADOR) {
            _avisos.emit("Acción reservada al rol Operador"); return@launch
        }
        cambiarBarrera(barrera, abrir, autoCerrar = false, motivo = "manual (operador)")
    }

    /** Operador: cambiar el estado de una plaza en contingencia (Libre -> Ocupada -> Libre). */
    fun cambiarEstadoManual(plazaId: Int) = scope.launch {
        if (rol != Rol.OPERADOR) return@launch
        val p = _plazas.value.firstOrNull { it.id == plazaId } ?: return@launch
        val nuevo = if (p.estado == EstadoPlaza.LIBRE) EstadoPlaza.OCUPADA else EstadoPlaza.LIBRE
        actualizarPlaza(p.copy(estado = nuevo, patente = "", reservaHasta = 0))
        if (p.estado == EstadoPlaza.RESERVADA) enviarArduino(ProtocoloArduino.reservada(plazaId, false))
        evento("SENSOR", "Plaza $plazaId marcada ${nuevo.etiqueta.uppercase()} (manual)")
        difundirEstado()
    }

    fun filtrarEventos(tipo: String?) {
        _filtroEventos.value = tipo
        scope.launch { refrescarEventos() }
    }

    // ===================== Reglas de negocio =====================

    private suspend fun aplicarReserva(plazaId: Int, patente: String, usuario: String): String {
        val plazas = _plazas.value
        if (patente.isBlank()) return "Tu cuenta no tiene patente registrada"
        if (plazas.any { it.estado != EstadoPlaza.LIBRE && it.patente == patente })
            return "Ya tienes una reserva o un vehículo estacionado"
        val p = plazas.firstOrNull { it.id == plazaId } ?: return "Plaza inexistente"
        if (p.estado != EstadoPlaza.LIBRE) return "La plaza $plazaId ya no está disponible"
        actualizarPlaza(p.copy(estado = EstadoPlaza.RESERVADA, patente = patente, reservaHasta = System.currentTimeMillis() + DURACION_RESERVA_MS))
        enviarArduino(ProtocoloArduino.reservada(plazaId, true))
        evento("RESERVA", "Vehículo $patente ($usuario) reservó Plaza $plazaId")
        difundirEstado()
        return "Plaza $plazaId reservada por 15 minutos"
    }

    private suspend fun aplicarCancelacion(plazaId: Int, patente: String, usuario: String): String {
        val p = _plazas.value.firstOrNull { it.id == plazaId } ?: return "Plaza inexistente"
        if (p.estado != EstadoPlaza.RESERVADA || p.patente != patente) return "No tienes reserva en esa plaza"
        actualizarPlaza(p.copy(estado = EstadoPlaza.LIBRE, patente = "", reservaHasta = 0))
        enviarArduino(ProtocoloArduino.reservada(plazaId, false))
        evento("RESERVA", "Vehículo $patente ($usuario) canceló reserva de Plaza $plazaId")
        difundirEstado()
        return "Reserva cancelada"
    }

    private suspend fun aplicarAperturaConductor(barrera: Barrera, patente: String): String {
        // Control de acceso: solo abre quien tiene reserva vigente (entrada) o vehículo estacionado (salida)
        val estadoRequerido = if (barrera == Barrera.ENTRADA) EstadoPlaza.RESERVADA else EstadoPlaza.OCUPADA
        val p = _plazas.value.firstOrNull { it.estado == estadoRequerido && it.patente == patente && patente.isNotBlank() }
        if (p == null) {
            evento("SEGURIDAD", "Apertura de ${barrera.etiqueta} DENEGADA a $patente")
            return if (barrera == Barrera.ENTRADA) "Acceso denegado: necesitas una reserva activa"
            else "Acceso denegado: no hay un vehículo tuyo estacionado"
        }
        cambiarBarrera(barrera, abrir = true, autoCerrar = true, motivo = "acceso vehículo $patente")
        // El acceso validado cierra el ciclo de la reserva (el sensor de la plaza lo confirmará si hay Arduino)
        if (barrera == Barrera.ENTRADA) {
            actualizarPlaza(p.copy(estado = EstadoPlaza.OCUPADA, reservaHasta = 0))
            enviarArduino(ProtocoloArduino.reservada(p.id, false))
            evento("RESERVA", "Vehículo $patente ingresó a Plaza ${p.id}")
        } else {
            actualizarPlaza(p.copy(estado = EstadoPlaza.LIBRE, patente = "", reservaHasta = 0))
            evento("RESERVA", "Vehículo $patente salió y liberó Plaza ${p.id}")
        }
        difundirEstado()
        return "Barrera de ${barrera.etiqueta.lowercase()} abierta"
    }

    private suspend fun cambiarBarrera(barrera: Barrera, abrir: Boolean, autoCerrar: Boolean, motivo: String) {
        cierresAutomaticos.remove(barrera)?.cancel()
        _barreras.update { it + (barrera to abrir) }
        enviarArduino(if (abrir) ProtocoloArduino.abrir(barrera) else ProtocoloArduino.cerrar(barrera))
        evento("BARRERA", "Barrera ${barrera.etiqueta} ${if (abrir) "ABIERTA" else "CERRADA"} - $motivo")
        difundirEstado()
        if (abrir && autoCerrar) {
            cierresAutomaticos[barrera] = scope.launch {
                delay(CIERRE_AUTOMATICO_MS)
                cambiarBarrera(barrera, abrir = false, autoCerrar = false, motivo = "cierre automático")
            }
        }
    }

    private suspend fun aplicarSensor(plazaId: Int, ocupada: Boolean) {
        val p = _plazas.value.firstOrNull { it.id == plazaId } ?: return
        when {
            ocupada && p.estado == EstadoPlaza.RESERVADA -> {
                actualizarPlaza(p.copy(estado = EstadoPlaza.OCUPADA, reservaHasta = 0))
                enviarArduino(ProtocoloArduino.reservada(plazaId, false))
                evento("SENSOR", "Vehículo ${p.patente} ingresó a Plaza $plazaId")
            }
            ocupada && p.estado == EstadoPlaza.LIBRE -> {
                actualizarPlaza(p.copy(estado = EstadoPlaza.OCUPADA))
                evento("SENSOR", "Nodo sensor $plazaId: OCUPADO")
            }
            !ocupada && p.estado == EstadoPlaza.OCUPADA -> {
                actualizarPlaza(p.copy(estado = EstadoPlaza.LIBRE, patente = ""))
                evento("SENSOR", "Nodo sensor $plazaId: LIBRE")
            }
            else -> return
        }
        difundirEstado()
    }

    private suspend fun liberarReservasVencidas() {
        val ahora = System.currentTimeMillis()
        val vencidas = _plazas.value.filter { it.estado == EstadoPlaza.RESERVADA && it.reservaHasta in 1 until ahora }
        if (vencidas.isEmpty()) return
        vencidas.forEach { p ->
            actualizarPlaza(p.copy(estado = EstadoPlaza.LIBRE, patente = "", reservaHasta = 0))
            enviarArduino(ProtocoloArduino.reservada(p.id, false))
            evento("RESERVA", "Reserva de Plaza ${p.id} (${p.patente}) expiró y se liberó")
        }
        difundirEstado()
    }

    private fun actualizarPlaza(nueva: Plaza) {
        _plazas.update { lista -> lista.map { if (it.id == nueva.id) nueva else it } }
        db.guardarPlazas(listOf(nueva))
    }

    private fun evento(tipo: String, descripcion: String) {
        scope.launch {
            db.registrarEvento(tipo, descripcion)
            refrescarEventos()
        }
    }

    private fun refrescarEventos() {
        _eventos.value = db.leerEventos(_filtroEventos.value)
    }

    companion object {
        const val DURACION_RESERVA_MS = 15 * 60 * 1000L
        const val CIERRE_AUTOMATICO_MS = 5_000L
        val TIPOS_EVENTO = listOf("SENSOR", "RESERVA", "BARRERA", "SEGURIDAD", "CONEXION", "ACCESO")
    }
}
