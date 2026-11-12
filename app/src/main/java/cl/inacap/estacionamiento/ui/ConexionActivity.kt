package cl.inacap.estacionamiento.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.comm.EstadoConexion
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.comm.PUERTO_SMARTPARK
import cl.inacap.estacionamiento.comm.TipoEnlace
import cl.inacap.estacionamiento.comm.ipLocal
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.security.AutenticadorArduino
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Configuración de enlaces inalámbricos (vista: res/layout/activity_conexion.xml):
 *  - Otro teléfono (Conductor <-> Operador) por Wi-Fi o Bluetooth.
 *  - Placa Arduino UNO R3 con módulo HC-05 por Bluetooth.
 */
class ConexionActivity : BaseActivity() {

    private lateinit var vm: ParkingViewModel
    private lateinit var rol: Rol

    private lateinit var tvEstadoTelefono: TextView
    private lateinit var tvDetalleTelefono: TextView
    private lateinit var rgMedio: RadioGroup
    private lateinit var grupoServidor: LinearLayout
    private lateinit var tvIpLocal: TextView
    private lateinit var btnIniciarServidor: Button
    private lateinit var grupoCliente: LinearLayout
    private lateinit var etPin: EditText
    private lateinit var grupoIp: LinearLayout
    private lateinit var etIp: EditText
    private lateinit var tvAyudaBtTelefono: TextView
    private lateinit var btnPermisoBtTelefono: Button
    private lateinit var listaTelefono: LinearLayout
    private lateinit var btnDesconectarTelefono: Button

    private lateinit var tvEstadoArduino: TextView
    private lateinit var tvAvisoArduino: TextView
    private lateinit var etClaveArduino: EditText
    private lateinit var btnPermisoBtArduino: Button
    private lateinit var listaArduino: LinearLayout
    private lateinit var btnDesconectarArduino: Button

    private var estadoTelefono: EstadoConexion? = null
    private var estadoArduino: EstadoConexion? = null

    private val usaWifi: Boolean get() = rgMedio.checkedRadioButtonId != R.id.rbBluetooth
    private val esOperador: Boolean get() = rol == Rol.OPERADOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rol = (exigirSesion() ?: return).rol
        setContentView(R.layout.activity_conexion)
        vm = ParkingViewModel(this)

        tvEstadoTelefono = findViewById(R.id.tvEstadoTelefono)
        tvDetalleTelefono = findViewById(R.id.tvDetalleTelefono)
        rgMedio = findViewById(R.id.rgMedio)
        grupoServidor = findViewById(R.id.grupoServidor)
        tvIpLocal = findViewById(R.id.tvIpLocal)
        btnIniciarServidor = findViewById(R.id.btnIniciarServidor)
        grupoCliente = findViewById(R.id.grupoCliente)
        etPin = findViewById(R.id.etPin)
        grupoIp = findViewById(R.id.grupoIp)
        etIp = findViewById(R.id.etIp)
        tvAyudaBtTelefono = findViewById(R.id.tvAyudaBtTelefono)
        btnPermisoBtTelefono = findViewById(R.id.btnPermisoBtTelefono)
        listaTelefono = findViewById(R.id.listaDispositivosTelefono)
        btnDesconectarTelefono = findViewById(R.id.btnDesconectarTelefono)
        tvEstadoArduino = findViewById(R.id.tvEstadoArduino)
        tvAvisoArduino = findViewById(R.id.tvAvisoArduino)
        etClaveArduino = findViewById(R.id.etClaveArduino)
        btnPermisoBtArduino = findViewById(R.id.btnPermisoBtArduino)
        listaArduino = findViewById(R.id.listaDispositivosArduino)
        btnDesconectarArduino = findViewById(R.id.btnDesconectarArduino)

        findViewById<Button>(R.id.btnVolverConexion).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTituloTelefono).setText(
            if (esOperador) R.string.titulo_tel_operador else R.string.titulo_tel_conductor
        )
        findViewById<TextView>(R.id.tvPinOperador).text = getString(R.string.formato_pin, vm.pin)
        grupoServidor.visibility = if (esOperador) View.VISIBLE else View.GONE
        grupoCliente.visibility = if (esOperador) View.GONE else View.VISIBLE

        rgMedio.setOnCheckedChangeListener { _, _ -> pintarTelefono() }

        btnIniciarServidor.setOnClickListener {
            if (usaWifi) vm.iniciarServidorWifi() else vm.iniciarServidorBluetooth()
        }
        findViewById<Button>(R.id.btnConectarWifi).setOnClickListener { conectarWifi() }
        btnDesconectarTelefono.setOnClickListener { vm.desconectar(TipoEnlace.TELEFONO) }
        btnDesconectarArduino.setOnClickListener { vm.desconectar(TipoEnlace.ARDUINO) }
        btnPermisoBtTelefono.setOnClickListener { pedirPermisoBluetooth() }
        btnPermisoBtArduino.setOnClickListener { pedirPermisoBluetooth() }
    }

    override fun alMostrar(scope: CoroutineScope) {
        if (!::vm.isInitialized) return
        observarEnlace(scope, vm.enlaceTelefono) { e ->
            estadoTelefono = e
            pintarEnlace(tvEstadoTelefono, getString(R.string.enlace_estado), e, tvDetalleTelefono)
            pintarTelefono()
        }
        observarEnlace(scope, vm.enlaceArduino) { e ->
            estadoArduino = e
            pintarEnlace(tvEstadoArduino, getString(R.string.enlace_estado), e)
            pintarArduino()
        }
    }

    private fun activo(e: EstadoConexion?) = e != null && e.fase != FaseConexion.DESCONECTADO

    // ---------------- Teléfono a teléfono ----------------

    private fun pintarTelefono() {
        val conectado = activo(estadoTelefono)
        val wifi = usaWifi
        btnDesconectarTelefono.visibility = if (conectado) View.VISIBLE else View.GONE
        for (i in 0 until rgMedio.childCount) rgMedio.getChildAt(i).isEnabled = !conectado
        tvAyudaBtTelefono.visibility = if (!wifi) View.VISIBLE else View.GONE

        if (esOperador) {
            tvIpLocal.text = getString(R.string.formato_ip, ipLocal() ?: getString(R.string.sin_red_wifi), PUERTO_SMARTPARK)
            tvIpLocal.visibility = if (wifi) View.VISIBLE else View.GONE
            btnIniciarServidor.setText(if (wifi) R.string.btn_servidor_wifi else R.string.btn_servidor_bt)
            val puedeIniciar = wifi || tienePermisoBluetooth()
            btnIniciarServidor.visibility = if (!conectado && puedeIniciar) View.VISIBLE else View.GONE
            btnPermisoBtTelefono.visibility = if (!conectado && !puedeIniciar) View.VISIBLE else View.GONE
            listaTelefono.removeAllViews()
        } else {
            grupoIp.visibility = if (wifi && !conectado) View.VISIBLE else View.GONE
            etPin.isEnabled = !conectado
            if (wifi || conectado) {
                btnPermisoBtTelefono.visibility = View.GONE
                listaTelefono.removeAllViews()
            } else if (!tienePermisoBluetooth()) {
                btnPermisoBtTelefono.visibility = View.VISIBLE
                listaTelefono.removeAllViews()
            } else {
                btnPermisoBtTelefono.visibility = View.GONE
                llenarDispositivos(listaTelefono) { d ->
                    if (pinValido()) {
                        aviso(getString(R.string.conectando_a, d.name ?: d.address))
                        vm.conectarBluetoothTelefono(d, etPin.text.toString())
                    }
                }
            }
        }
    }

    private fun pinValido(): Boolean {
        val ok = etPin.text.length == 6 && etPin.text.all { it.isDigit() }
        etPin.error = if (ok) null else getString(R.string.error_pin)
        return ok
    }

    private fun conectarWifi() {
        val ip = etIp.text.toString().trim()
        val ipOk = ip.split(".").let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }
        etIp.error = if (ipOk) null else getString(R.string.error_ip)
        if (!pinValido() || !ipOk) return
        vm.conectarWifi(ip, etPin.text.toString())
    }

    // ---------------- Arduino ----------------

    private fun pintarArduino() {
        val conectado = activo(estadoArduino)
        btnDesconectarArduino.visibility = if (conectado) View.VISIBLE else View.GONE
        listaArduino.removeAllViews()
        btnPermisoBtArduino.visibility = View.GONE
        tvAvisoArduino.visibility = View.GONE
        etClaveArduino.isEnabled = !conectado
        if (conectado) return
        when {
            !vm.bluetoothDisponible -> mostrarAvisoArduino(R.string.sin_bluetooth)
            !tienePermisoBluetooth() -> btnPermisoBtArduino.visibility = View.VISIBLE
            !vm.bluetoothActivo -> mostrarAvisoArduino(R.string.bt_apagado)
            else -> llenarDispositivos(listaArduino) { d ->
                if (claveArduinoValida()) {
                    aviso(getString(R.string.conectando_a, d.name ?: d.address))
                    vm.conectarArduino(d, etClaveArduino.text.toString())
                }
            }
        }
    }

    private fun claveArduinoValida(): Boolean {
        val ok = etClaveArduino.text.length >= AutenticadorArduino.LARGO_MINIMO_CLAVE
        etClaveArduino.error = if (ok) null else getString(R.string.error_clave_arduino)
        return ok
    }

    private fun mostrarAvisoArduino(texto: Int) {
        tvAvisoArduino.setText(texto)
        tvAvisoArduino.visibility = View.VISIBLE
    }

    // ---------------- Bluetooth ----------------

    /** Agrega un botón (res/layout/item_dispositivo.xml) por cada dispositivo vinculado. */
    @SuppressLint("MissingPermission")
    private fun llenarDispositivos(contenedor: LinearLayout, alElegir: (BluetoothDevice) -> Unit) {
        contenedor.removeAllViews()
        val dispositivos = vm.dispositivosVinculados()
        val titulo = TextView(this).apply {
            setText(if (dispositivos.isEmpty()) R.string.sin_vinculados else R.string.dispositivos_vinculados)
            setTextColor(color(if (dispositivos.isEmpty()) R.color.ambar_reservado else R.color.texto_secundario))
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        contenedor.addView(titulo)
        dispositivos.forEach { d ->
            val boton = layoutInflater.inflate(R.layout.item_dispositivo, contenedor, false) as Button
            boton.text = "${d.name ?: "Sin nombre"} · ${d.address}"
            boton.setOnClickListener { alElegir(d) }
            contenedor.addView(boton)
        }
    }

    private fun tienePermisoBluetooth(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun pedirPermisoBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), CODIGO_PERMISO_BT)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CODIGO_PERMISO_BT) return
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            aviso(getString(R.string.permiso_bt_denegado))
        }
        pintarTelefono()
        pintarArduino()
    }

    private companion object {
        const val CODIGO_PERMISO_BT = 7
    }
}
