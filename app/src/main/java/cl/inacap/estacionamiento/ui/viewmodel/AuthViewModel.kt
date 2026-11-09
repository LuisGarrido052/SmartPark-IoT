package cl.inacap.estacionamiento.ui.viewmodel

import cl.inacap.estacionamiento.ServiceLocator
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.data.repository.ResultadoAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val sesion: Sesion? = null
)

/**
 * ViewModel (MVVM) del Login y el Registro. Las Activities observan [ui] y pintan las vistas XML.
 * Es una clase Kotlin simple (sin librerías AndroidX) para que el proyecto abra en cualquier Android Studio.
 */
class AuthViewModel {
    private val repo = ServiceLocator.auth
    private val parking = ServiceLocator.parking
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    /** Si existe una sesión cifrada vigente (máx. 8 h) se entra directo sin pedir credenciales. */
    fun verificarSesionGuardada() {
        scope.launch {
            val s = withContext(Dispatchers.IO) { repo.sesionActual() }
            if (s != null) {
                parking.iniciarSesion(s)
                _ui.value = _ui.value.copy(sesion = s)
            }
        }
    }

    fun login(usuario: String, password: CharArray) {
        if (_ui.value.cargando) return
        _ui.value = _ui.value.copy(cargando = true, error = null)
        scope.launch { procesar(repo.login(usuario, password)) }
    }

    fun registrar(usuario: String, password: CharArray, rol: Rol, patente: String, codigoOperador: String) {
        if (_ui.value.cargando) return
        _ui.value = _ui.value.copy(cargando = true, error = null)
        scope.launch { procesar(repo.registrar(usuario, password, rol, patente, codigoOperador)) }
    }

    private fun procesar(r: ResultadoAuth) {
        _ui.value = when (r) {
            is ResultadoAuth.Exito -> {
                parking.iniciarSesion(r.sesion)
                _ui.value.copy(cargando = false, sesion = r.sesion, error = null)
            }
            is ResultadoAuth.Error -> _ui.value.copy(cargando = false, error = r.mensaje)
        }
    }

    fun limpiar() = scope.cancel()
}
