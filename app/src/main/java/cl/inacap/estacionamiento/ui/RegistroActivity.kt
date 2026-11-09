package cl.inacap.estacionamiento.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.security.Validaciones
import cl.inacap.estacionamiento.ui.viewmodel.AuthUiState
import cl.inacap.estacionamiento.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Registro de cuentas nuevas (vista: res/layout/activity_registro.xml). */
class RegistroActivity : BaseActivity() {

    private val vm by lazy { AuthViewModel() }

    private lateinit var rgRol: RadioGroup
    private lateinit var etUsuario: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmacion: EditText
    private lateinit var etPatente: EditText
    private lateinit var etCodigo: EditText
    private lateinit var grupoPatente: LinearLayout
    private lateinit var grupoCodigo: LinearLayout
    private lateinit var tvError: TextView
    private lateinit var btnRegistrar: Button
    private lateinit var pbRegistro: ProgressBar

    private val rolElegido: Rol
        get() = if (rgRol.checkedRadioButtonId == R.id.rbOperador) Rol.OPERADOR else Rol.CONDUCTOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        rgRol = findViewById(R.id.rgRol)
        etUsuario = findViewById(R.id.etRegUsuario)
        etPassword = findViewById(R.id.etRegPassword)
        etConfirmacion = findViewById(R.id.etRegConfirmacion)
        etPatente = findViewById(R.id.etPatente)
        etCodigo = findViewById(R.id.etCodigo)
        grupoPatente = findViewById(R.id.grupoPatente)
        grupoCodigo = findViewById(R.id.grupoCodigo)
        tvError = findViewById(R.id.tvErrorRegistro)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        pbRegistro = findViewById(R.id.pbRegistro)

        rgRol.setOnCheckedChangeListener { _, _ -> mostrarCamposDelRol() }
        mostrarCamposDelRol()

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        btnRegistrar.setOnClickListener { registrar() }
    }

    override fun alMostrar(scope: CoroutineScope) {
        scope.launch { vm.ui.collect { pintar(it) } }
    }

    /** Conductor: pide patente. Operador: pide código de autorización. */
    private fun mostrarCamposDelRol() {
        val conductor = rolElegido == Rol.CONDUCTOR
        grupoPatente.visibility = if (conductor) View.VISIBLE else View.GONE
        grupoCodigo.visibility = if (conductor) View.GONE else View.VISIBLE
    }

    private fun registrar() {
        val rol = rolElegido
        val usuario = etUsuario.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmacion = etConfirmacion.text.toString()
        val patente = etPatente.text.toString()
        val codigo = etCodigo.text.toString()

        val eUsuario = Validaciones.errorUsuario(usuario)
        val ePassword = Validaciones.errorPasswordNueva(password)
        val eConfirmacion = Validaciones.errorConfirmacion(password, confirmacion)
        val ePatente = if (rol == Rol.CONDUCTOR) Validaciones.errorPatente(patente) else null
        val eCodigo = if (rol == Rol.OPERADOR && codigo.isBlank()) getString(R.string.error_codigo_vacio) else null

        etUsuario.error = eUsuario
        etPassword.error = ePassword
        etConfirmacion.error = eConfirmacion
        etPatente.error = ePatente
        etCodigo.error = eCodigo
        if (listOf(eUsuario, ePassword, eConfirmacion, ePatente, eCodigo).any { it != null }) return

        vm.registrar(usuario, password.toCharArray(), rol, patente, codigo)
    }

    private fun pintar(estado: AuthUiState) {
        btnRegistrar.isEnabled = !estado.cargando
        pbRegistro.visibility = if (estado.cargando) View.VISIBLE else View.GONE
        tvError.text = estado.error.orEmpty()
        tvError.visibility = if (estado.error == null) View.GONE else View.VISIBLE
        val s = estado.sesion ?: return
        val destino = if (s.rol == Rol.OPERADOR) OperadorActivity::class.java else ConductorActivity::class.java
        startActivity(
            Intent(this, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        vm.limpiar()
        super.onDestroy()
    }
}
