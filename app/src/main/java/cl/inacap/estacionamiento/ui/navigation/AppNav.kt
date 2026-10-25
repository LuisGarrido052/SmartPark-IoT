package cl.inacap.estacionamiento.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.ui.screens.ConductorScreen
import cl.inacap.estacionamiento.ui.screens.ConexionScreen
import cl.inacap.estacionamiento.ui.screens.LoginScreen
import cl.inacap.estacionamiento.ui.screens.OperadorScreen
import cl.inacap.estacionamiento.ui.screens.RegistroScreen
import cl.inacap.estacionamiento.ui.viewmodel.AuthViewModel
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel

object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val CONDUCTOR = "conductor"
    const val OPERADOR = "operador"
    const val CONEXION = "conexion"
}

@Composable
fun SmartParkNavHost() {
    val auth: AuthViewModel = viewModel()
    val parking: ParkingViewModel = viewModel()
    val estado by auth.ui.collectAsState()
    val nav = rememberNavController()

    if (estado.verificandoSesion) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Al iniciar o cerrar sesión se navega según el rol (RBAC): cada rol ve solo su panel
    LaunchedEffect(estado.sesion) {
        val destino = when (estado.sesion?.rol) {
            Rol.OPERADOR -> Rutas.OPERADOR
            Rol.CONDUCTOR -> Rutas.CONDUCTOR
            null -> Rutas.LOGIN
        }
        if (nav.currentDestination?.route != destino) {
            nav.navigate(destino) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = Rutas.LOGIN) {
        composable(Rutas.LOGIN) {
            LoginScreen(
                estado = estado,
                onLogin = auth::login,
                onIrARegistro = { auth.limpiarError(); nav.navigate(Rutas.REGISTRO) }
            )
        }
        composable(Rutas.REGISTRO) {
            RegistroScreen(
                estado = estado,
                onRegistrar = auth::registrar,
                onVolver = { auth.limpiarError(); nav.popBackStack() }
            )
        }
        composable(Rutas.CONDUCTOR) {
            val s = estado.sesion ?: return@composable
            if (s.rol != Rol.CONDUCTOR) return@composable
            ConductorScreen(s, parking, onConexion = { nav.navigate(Rutas.CONEXION) }, onCerrarSesion = auth::cerrarSesion)
        }
        composable(Rutas.OPERADOR) {
            val s = estado.sesion ?: return@composable
            if (s.rol != Rol.OPERADOR) return@composable
            OperadorScreen(s, parking, onConexion = { nav.navigate(Rutas.CONEXION) }, onCerrarSesion = auth::cerrarSesion)
        }
        composable(Rutas.CONEXION) {
            val s = estado.sesion ?: return@composable
            ConexionScreen(s.rol, parking, onVolver = { nav.popBackStack() })
        }
    }
}
