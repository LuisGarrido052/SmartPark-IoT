package cl.inacap.estacionamiento.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.security.Validaciones
import cl.inacap.estacionamiento.ui.viewmodel.AuthUiState
import cl.inacap.estacionamiento.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pantalla de Login (rúbrica 2.1.1.1).
 * Vista: res/layout/activity_login.xml con un EditText para el usuario y otro para la contraseña.
 * Las credenciales se validan contra la base SQLite (hash PBKDF2) en AuthRepository.
 */
class LoginActivity : BaseActivity() {

    private val vm by lazy { AuthViewModel() }

    private lateinit var etUsuario: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbMostrar: CheckBox
    private lateinit var tvError: TextView
    private lateinit var btnIngresar: Button
    private lateinit var pbLogin: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsuario = findViewById(R.id.etUsuario)
        etPassword = findViewById(R.id.etPassword)
        cbMostrar = findViewById(R.id.cbMostrarPassword)
        tvError = findViewById(R.id.tvErrorLogin)
        btnIngresar = findViewById(R.id.btnIngresar)
        pbLogin = findViewById(R.id.pbLogin)

        btnIngresar.setOnClickListener { ingresar() }
        etPassword.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) { ingresar(); true } else false
        }
        cbMostrar.setOnCheckedChangeListener { _, ver ->
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or
                if (ver) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
        }
        findViewById<Button>(R.id.btnIrRegistro).setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        vm.verificarSesionGuardada()
    }

    override fun alMostrar(scope: CoroutineScope) {
        scope.launch { vm.ui.collect { pintar(it) } }
    }

    private fun ingresar() {
        val usuario = etUsuario.text.toString().trim()
        val password = etPassword.text.toString()

        // Validación de los campos antes de consultar la base de datos
        val errorUsuario = Validaciones.errorUsuario(usuario)
        val errorPassword = Validaciones.errorPasswordLogin(password)
        etUsuario.error = errorUsuario
        etPassword.error = errorPassword
        if (errorUsuario != null || errorPassword != null) return

        vm.login(usuario, password.toCharArray())
        etPassword.text.clear() // la contraseña no queda en pantalla ni en memoria de la vista
    }

    private fun pintar(estado: AuthUiState) {
        btnIngresar.isEnabled = !estado.cargando
        btnIngresar.setText(if (estado.cargando) R.string.verificando else R.string.btn_ingresar)
        pbLogin.visibility = if (estado.cargando) View.VISIBLE else View.GONE
        tvError.text = estado.error.orEmpty()
        tvError.visibility = if (estado.error == null) View.GONE else View.VISIBLE
        estado.sesion?.let { abrirPanel(it) }
    }

    private var navegando = false

    private fun abrirPanel(s: Sesion) {
        if (navegando) return
        navegando = true
        val destino = if (s.rol == Rol.OPERADOR) OperadorActivity::class.java else ConductorActivity::class.java
        startActivity(Intent(this, destino))
        finish()
    }

    override fun onDestroy() {
        vm.limpiar()
        super.onDestroy()
    }
}
