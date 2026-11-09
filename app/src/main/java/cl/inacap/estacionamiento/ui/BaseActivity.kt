package cl.inacap.estacionamiento.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.ServiceLocator
import cl.inacap.estacionamiento.comm.ConexionIoT
import cl.inacap.estacionamiento.comm.EstadoConexion
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base de todas las pantallas. Usa la Activity del framework de Android (sin AppCompat) y
 * vistas definidas en XML (res/layout). Mientras la pantalla está visible se escuchan los
 * flujos del ViewModel en [alMostrar]; al ocultarse se cancelan (ahorro de batería).
 */
abstract class BaseActivity : Activity() {

    private var visible: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.iniciar(this)
    }

    override fun onStart() {
        super.onStart()
        val s = MainScope()
        visible = s
        alMostrar(s)
    }

    override fun onStop() {
        visible?.cancel()
        visible = null
        super.onStop()
    }

    /** Aquí cada pantalla lanza la observación de sus datos. */
    protected open fun alMostrar(scope: CoroutineScope) {}

    protected fun aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()

    protected fun color(id: Int): Int = getColor(id)

    /** Pinta un indicador de enlace: punto de color + "Nombre: Fase". */
    protected fun pintarEnlace(tv: TextView, nombre: String, estado: EstadoConexion?, detalle: TextView? = null) {
        val fase = estado?.fase ?: FaseConexion.DESCONECTADO
        val c = color(
            when (fase) {
                FaseConexion.CONECTADO -> R.color.verde_libre
                FaseConexion.DESCONECTADO -> R.color.texto_secundario
                else -> R.color.ambar_reservado
            }
        )
        tv.text = getString(R.string.formato_enlace, nombre, fase.etiqueta)
        tv.setTextColor(c)
        tv.compoundDrawableTintList = ColorStateList.valueOf(c)
        if (detalle != null) {
            val d = estado?.detalle.orEmpty()
            detalle.text = d
            detalle.visibility = if (d.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    /** Observa un enlace (que puede cambiar) y su estado interno. */
    protected fun observarEnlace(
        scope: CoroutineScope,
        enlace: StateFlow<ConexionIoT?>,
        alCambiar: (EstadoConexion?) -> Unit
    ) {
        scope.launch {
            enlace.collectLatest { e ->
                if (e == null) alCambiar(null) else e.estado.collect { alCambiar(it) }
            }
        }
    }

    protected fun fondoPlaza(estado: EstadoPlaza, marcada: Boolean): Int = when (estado) {
        EstadoPlaza.LIBRE -> if (marcada) R.drawable.bg_plaza_libre_marcada else R.drawable.bg_plaza_libre
        EstadoPlaza.OCUPADA -> if (marcada) R.drawable.bg_plaza_ocupada_marcada else R.drawable.bg_plaza_ocupada
        EstadoPlaza.RESERVADA -> if (marcada) R.drawable.bg_plaza_reservada_marcada else R.drawable.bg_plaza_reservada
    }

    protected fun colorPlaza(estado: EstadoPlaza): Int = color(
        when (estado) {
            EstadoPlaza.LIBRE -> R.color.verde_libre
            EstadoPlaza.OCUPADA -> R.color.rojo_ocupado
            EstadoPlaza.RESERVADA -> R.color.ambar_reservado
        }
    )

    /**
     * Control de acceso (RBAC): la pantalla solo abre con una sesión vigente del rol correcto.
     * Si no, vuelve al Login. Con rol = null basta cualquier sesión vigente.
     */
    protected fun exigirSesion(rol: Rol? = null): Sesion? {
        val s = ServiceLocator.auth.sesionActual()
        if (s == null || (rol != null && s.rol != rol)) {
            irALogin()
            return null
        }
        ServiceLocator.parking.iniciarSesion(s)
        return s
    }

    protected fun cerrarSesion() {
        ServiceLocator.auth.cerrarSesion()
        ServiceLocator.parking.cerrarSesion()
        irALogin()
    }

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}

fun formatoMinSeg(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}
