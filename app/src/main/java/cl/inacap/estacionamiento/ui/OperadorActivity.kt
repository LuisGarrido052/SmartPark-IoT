package cl.inacap.estacionamiento.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Evento
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Terminal Android 2: Operador (vista: res/layout/activity_operador.xml).
 * Telemetría de sensores, control de barreras y log de eventos.
 */
class OperadorActivity : BaseActivity() {

    private lateinit var vm: ParkingViewModel

    private lateinit var tvEnlaceTelefono: TextView
    private lateinit var tvEnlaceTelefonoDetalle: TextView
    private lateinit var tvEnlaceArduino: TextView
    private lateinit var tvKpiAforo: TextView
    private lateinit var tvKpiAforoSub: TextView
    private lateinit var tvKpiLibres: TextView
    private lateinit var tvKpiLibresSub: TextView
    private lateinit var tvKpiReservas: TextView
    private lateinit var tvKpiReservasSub: TextView
    private lateinit var interruptores: Map<Barrera, Switch>
    private lateinit var estadosBarrera: Map<Barrera, TextView>
    private lateinit var grid: GridLayout
    private lateinit var spFiltro: Spinner
    private lateinit var contenedorEventos: LinearLayout
    private lateinit var tvSinEventos: TextView

    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    /** Evita reenviar órdenes a la barrera cuando el switch cambia por código. */
    private var pintandoBarreras = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sesion = exigirSesion(Rol.OPERADOR) ?: return
        setContentView(R.layout.activity_operador)
        vm = ParkingViewModel(this)

        findViewById<TextView>(R.id.tvTitulo).setText(R.string.panel_operador)
        findViewById<TextView>(R.id.tvSubtitulo).apply {
            text = getString(R.string.formato_operador, sesion.nombreUsuario, vm.pin)
            setTextColor(color(R.color.cian_conexion))
        }
        findViewById<Button>(R.id.btnConexion).setOnClickListener {
            startActivity(Intent(this, ConexionActivity::class.java))
        }
        findViewById<Button>(R.id.btnSalir).setOnClickListener { cerrarSesion() }

        tvEnlaceTelefono = findViewById(R.id.tvEnlaceTelefono)
        tvEnlaceTelefonoDetalle = findViewById(R.id.tvEnlaceTelefonoDetalle)
        tvEnlaceArduino = findViewById(R.id.tvEnlaceArduino)
        tvKpiAforo = findViewById(R.id.tvKpiAforo)
        tvKpiAforoSub = findViewById(R.id.tvKpiAforoSub)
        tvKpiLibres = findViewById(R.id.tvKpiLibres)
        tvKpiLibresSub = findViewById(R.id.tvKpiLibresSub)
        tvKpiReservas = findViewById(R.id.tvKpiReservas)
        tvKpiReservasSub = findViewById(R.id.tvKpiReservasSub)
        grid = findViewById(R.id.gridPlazasOperador)
        spFiltro = findViewById(R.id.spFiltroEventos)
        contenedorEventos = findViewById(R.id.contenedorEventos)
        tvSinEventos = findViewById(R.id.tvSinEventos)

        interruptores = mapOf(
            Barrera.ENTRADA to findViewById(R.id.swEntrada),
            Barrera.SALIDA to findViewById(R.id.swSalida)
        )
        estadosBarrera = mapOf(
            Barrera.ENTRADA to findViewById(R.id.tvEstadoEntrada),
            Barrera.SALIDA to findViewById(R.id.tvEstadoSalida)
        )
        interruptores.forEach { (barrera, sw) ->
            sw.setOnCheckedChangeListener { _, abrir ->
                if (!pintandoBarreras) vm.operarBarrera(barrera, abrir)
            }
        }

        // Filtro del log: "Todos" + tipos de evento (SENSOR, RESERVA, BARRERA...)
        val opciones = listOf(getString(R.string.todos)) +
            vm.tiposEvento.map { t -> t.lowercase().replaceFirstChar { it.uppercase() } }
        val adaptador = ArrayAdapter(this, R.layout.item_spinner, opciones)
        adaptador.setDropDownViewResource(R.layout.item_spinner)
        spFiltro.adapter = adaptador
        val actual = vm.filtroEventos.value
        spFiltro.setSelection(if (actual == null) 0 else vm.tiposEvento.indexOf(actual) + 1)
        spFiltro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, posicion: Int, id: Long) {
                vm.filtrarEventos(if (posicion == 0) null else vm.tiposEvento[posicion - 1])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun alMostrar(scope: CoroutineScope) {
        if (!::vm.isInitialized) return
        scope.launch { vm.plazas.collect { pintarPlazas(it) } }
        scope.launch { vm.barreras.collect { pintarBarreras(it) } }
        scope.launch { vm.eventos.collect { pintarEventos(it) } }
        scope.launch { vm.avisos.collect { aviso(it) } }
        observarEnlace(scope, vm.enlaceTelefono) { e ->
            pintarEnlace(tvEnlaceTelefono, getString(R.string.enlace_conductor), e, tvEnlaceTelefonoDetalle)
        }
        observarEnlace(scope, vm.enlaceArduino) { e ->
            pintarEnlace(tvEnlaceArduino, getString(R.string.enlace_arduino), e)
        }
    }

    private fun pintarPlazas(plazas: List<Plaza>) {
        val libres = plazas.count { it.estado == EstadoPlaza.LIBRE }
        val reservadas = plazas.count { it.estado == EstadoPlaza.RESERVADA }
        val ocupacion = if (plazas.isEmpty()) 0 else (plazas.size - libres) * 100 / plazas.size

        tvKpiAforo.text = "$ocupacion%"
        tvKpiAforo.setTextColor(color(if (ocupacion > 80) R.color.rojo_ocupado else R.color.cian_conexion))
        tvKpiAforoSub.setText(R.string.ocupacion)
        tvKpiLibres.text = libres.toString()
        tvKpiLibresSub.text = getString(R.string.formato_de_plazas, plazas.size)
        tvKpiReservas.text = reservadas.toString()
        tvKpiReservasSub.setText(R.string.activas)

        grid.removeAllViews()
        val d = resources.displayMetrics.density
        plazas.forEach { plaza ->
            val celda = layoutInflater.inflate(R.layout.item_plaza, grid, false)
            celda.setBackgroundResource(fondoPlaza(plaza.estado, false))
            celda.findViewById<TextView>(R.id.tvPlazaNumero).text = plaza.id.toString()
            celda.findViewById<TextView>(R.id.tvPlazaEstado).apply {
                text = plaza.estado.etiqueta
                setTextColor(colorPlaza(plaza.estado))
            }
            celda.contentDescription = "Plaza ${plaza.id}, ${plaza.estado.etiqueta}"
            // Corrección manual en contingencia (sensor dañado, etc.)
            celda.setOnClickListener { vm.cambiarEstadoManual(plaza.id) }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = (70 * d).toInt()
                val m = (4 * d).toInt()
                setMargins(m, m, m, m)
            }
            grid.addView(celda, params)
        }
    }

    private fun pintarBarreras(estado: Map<Barrera, Boolean>) {
        pintandoBarreras = true
        Barrera.entries.forEach { b ->
            val abierta = estado[b] == true
            interruptores[b]?.isChecked = abierta
            estadosBarrera[b]?.apply {
                setText(if (abierta) R.string.abierta else R.string.cerrada)
                setTextColor(color(if (abierta) R.color.verde_libre else R.color.texto_secundario))
            }
        }
        pintandoBarreras = false
    }

    private fun pintarEventos(eventos: List<Evento>) {
        tvSinEventos.visibility = if (eventos.isEmpty()) View.VISIBLE else View.GONE
        contenedorEventos.removeAllViews()
        eventos.take(MAX_EVENTOS).forEach { e ->
            val fila = layoutInflater.inflate(R.layout.item_evento, contenedorEventos, false)
            fila.findViewById<TextView>(R.id.tvEventoHora).text = formatoHora.format(Date(e.fecha))
            fila.findViewById<TextView>(R.id.tvEventoTexto).apply {
                text = e.descripcion
                setTextColor(color(if (e.tipo == "SEGURIDAD") R.color.rojo_ocupado else R.color.texto))
            }
            contenedorEventos.addView(fila)
        }
    }

    private companion object {
        const val MAX_EVENTOS = 60
    }
}
