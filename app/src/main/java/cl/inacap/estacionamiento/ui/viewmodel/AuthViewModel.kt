package cl.inacap.estacionamiento.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.inacap.estacionamiento.ServiceLocator
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.data.repository.ResultadoAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val verificandoSesion: Boolean = true,
    val cargando: Boolean = false,
    val error: String? = null,
    val sesion: Sesion? = null
)

class AuthViewModel : ViewModel() {
    private val repo = ServiceLocator.auth
    private val parking = ServiceLocator.parking

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        // Si hay una sesión cifrada vigente se entra directo
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { repo.sesionActual() }
            s?.let { parking.iniciarSesion(it) }
            _ui.value = AuthUiState(verificandoSesion = false, sesion = s)
        }
    }

    fun login(usuario: String, password: String) {
        if (_ui.value.cargando) return
        _ui.value = _ui.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            procesar(repo.login(usuario, password.toCharArray()))
        }
    }

    fun registrar(usuario: String, password: String, rol: Rol, patente: String, codigoOperador: String) {
        if (_ui.value.cargando) return
        _ui.value = _ui.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            procesar(repo.registrar(usuario, password.toCharArray(), rol, patente, codigoOperador))
        }
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

    fun limpiarError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun cerrarSesion() {
        repo.cerrarSesion()
        parking.cerrarSesion()
        _ui.value = AuthUiState(verificandoSesion = false)
    }
}
