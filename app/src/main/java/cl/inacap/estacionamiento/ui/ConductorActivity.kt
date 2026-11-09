package cl.inacap.estacionamiento.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import cl.inacap.estacionamiento.R
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.data.repository.ParkingRepository
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal Android 1: Conductor (vista: res/layout/activity_conductor.xml).
 * Mapa de plazas, reserva con temporizador de 15 minutos y apertura de barreras.
 */
class ConductorActivity : BaseActivity() {

    private lateinit var vm: ParkingViewModel
    private lateinit var sesion: Sesion

    private lateinit var tvEnlaceTelefono: TextView
    private lateinit var tvEnlaceArduino: TextView
    private lateinit var tvModoLocal: TextView
    private lateinit var cardReserva: LinearLayout
    private lateinit var tvReservaTitulo: TextView
    private lateinit var tvReservaPatente: TextView
    private lateinit var grupoTemporizador: LinearLayout
    private lateinit var tvTiempoRestante: TextView
    private lateinit var pbReserva: ProgressBar
    private lateinit var btnAbrirSalida: Button
    private lateinit var tvLibres: TextView
    private lateinit var rgNivel: RadioGroup
    private lateinit var btnBuscar: Button
    private lateinit var tvAyudaMapa: TextView
    private lateinit var pbCargando: ProgressBar
    private lateinit var grid: GridLayout

    private var piso = 1
    private var sugerida: Int? = null
    private var miPlaza: Plaza? = null
    private var dialogo: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = exigirSesion(Rol.CONDUCTOR) ?: return
        setContentView(R.layout.activity_conductor)
        vm = ParkingViewModel(this)
        piso = savedInstanceState?.getInt(CLAVE_PISO, 1) ?: 1

        findViewById<TextView>(R.id.tvTitulo).setText(R.string.marca)
        findViewById<TextView>(R.id.tvSubtitulo).text =
            getString(R.string.formato_hola, sesion.nombreUsuario, sesion.patente)
        findViewById<Button>(R.id.btnConexion).setOnClickListener {
            startActivity(Intent(this, ConexionActivity::class.java))
        }
        findViewById<Button>(R.id.btnSalir).setOnClickListener { cerrarSesion() }

        tvEnlaceTelefono = findViewById(R.id.tvEnlaceTelefono)
        tvEnlaceArduino = findViewById(R.id.tvEnlaceArduino)
        tvModoLocal = findViewById(R.id.tvModoLocal)
        cardReserva = findViewById(R.id.cardReserva)
        tvReservaTitulo = findViewById(R.id.tvReservaTitulo)
        tvReservaPatente = findViewById(R.id.tvReservaPatente)
        grupoTemporizador = findViewById(R.id.grupoTemporizador)
        tvTiempoRestante = findViewById(R.id.tvTiempoRestante)
        pbReserva = findViewById(R.id.pbReserva)
        btnAbrirSalida = findViewById(R.id.btnAbrirSalida)
        tvLibres = findViewById(R.id.tvLibres)
        rgNivel = findViewById(R.id.rgNivel)
        btnBuscar = findViewById(R.id.btnBuscarPlaza)
        tvAyudaMapa = findViewById(R.id.tvAyudaMapa)
        pbCargando = findViewById(R.id.pbCargandoPlazas)
        grid = findViewById(R.id.gridPlazas)

        pbReserva.max = (ParkingRepository.DURACION_RESERVA_MS / 1000).toInt()

        findViewById<Button>(R.id.btnAbrirEntrada).setOnClickListener { vm.solicitarApertura(Barrera.ENTRADA) }
        btnAbrirSalida.setOnClickListener { vm.solicitarApertura(Barrera.SALIDA) }
        findViewById<Button>(R.id.btnCancelarReserva).setOnClickListener {
            miPlaza?.let { vm.cancelarReserva(it.id) }
        }

        rgNivel.check(if (piso == 2) R.id.rbNivel2 else R.id.rbNivel1)
        rgNivel.setOnCheckedChangeListener { _, id ->
            piso = if (id == R.id.rbNivel2) 2 else 1
            sugerida = null
            pintarPlazas(vm.plazas.value)
        }

        btnBuscar.setOnClickListener {
            val p = vm.plazaMasCercana(piso)
            sugerida = p?.id
            pintarPlazas(vm.plazas.value)
            if (p == null) aviso(getString(R.string.sin_plazas_libres))
            else if (miPlaza == null) confirmarReserva(p)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CLAVE_PISO, piso)
    }

    override fun alMostrar(scope: CoroutineScope) {
        if (!::vm.isInitialized) return
        scope.launch { vm.plazas.collect { pintarPlazas(it) } }
        scope.launch { vm.avisos.collect { aviso(it) } }
        observarEnlace(scope, vm.enlaceTelefono) { e ->
            pintarEnlace(tvEnlaceTelefono, getString(R.string.enlace_operador), e)
            tvModoLocal.visibility = if (e?.fase == FaseConexion.CONECTADO) View.GONE else View.VISIBLE
        }
        observarEnlace(scope, vm.enlaceArduino) { e ->
            pintarEnlace(tvEnlaceArduino, getString(R.string.enlace_arduino), e)
        }
        // Temporizador de la reserva: se refresca cada segundo
        scope.launch {
            while (true) {
                pintarTemporizador()
                delay(1_000)
            }
        }
    }

    private fun pintarPlazas(plazas: List<Plaza>) {
        pbCargando.visibility = if (plazas.isEmpty()) View.VISIBLE else View.GONE
        miPlaza = plazas.firstOrNull { it.patente == sesion.patente && it.estado != EstadoPlaza.LIBRE }
        val libres = plazas.count { it.estado == EstadoPlaza.LIBRE }
        tvLibres.text = getString(R.string.formato_libres, libres, plazas.size)
        btnBuscar.isEnabled = miPlaza == null
        tvAyudaMapa.visibility = if (miPlaza == null) View.VISIBLE else View.GONE

        pintarReserva()

        grid.removeAllViews()
        val inflador = layoutInflater
        plazas.filter { it.piso == piso }.forEach { plaza ->
            val celda = inflador.inflate(R.layout.item_plaza, grid, false)
            val marcada = plaza.id == sugerida || plaza.id == miPlaza?.id
            celda.setBackgroundResource(fondoPlaza(plaza.estado, marcada))
            celda.findViewById<TextView>(R.id.tvPlazaNumero).text = plaza.id.toString()
            celda.findViewById<TextView>(R.id.tvPlazaEstado).apply {
                text = plaza.estado.etiqueta
                setTextColor(colorPlaza(plaza.estado))
            }
            celda.contentDescription = "Plaza ${plaza.id}, ${plaza.estado.etiqueta}"
            celda.setOnClickListener {
                if (plaza.estado == EstadoPlaza.LIBRE && miPlaza == null) confirmarReserva(plaza)
            }
            grid.addView(celda, celdaParams())
        }
    }

    /** Celdas de igual ancho en la grilla (columnWeight). */
    private fun celdaParams(): GridLayout.LayoutParams {
        val m = (5 * resources.displayMetrics.density).toInt()
        return GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        ).apply {
            width = 0
            height = (76 * resources.displayMetrics.density).toInt()
            setMargins(m, m, m, m)
        }
    }

    private fun pintarReserva() {
        val p = miPlaza
        if (p == null) {
            cardReserva.visibility = View.GONE
            return
        }
        cardReserva.visibility = View.VISIBLE
        val reservada = p.estado == EstadoPlaza.RESERVADA
        cardReserva.setBackgroundResource(if (reservada) R.drawable.bg_reserva else R.drawable.bg_plaza_libre)
        tvReservaTitulo.text =
            if (reservada) getString(R.string.formato_reserva, p.id, p.piso)
            else getString(R.string.formato_estacionado, p.id)
        tvReservaPatente.text = getString(R.string.formato_patente, p.patente)
        grupoTemporizador.visibility = if (reservada) View.VISIBLE else View.GONE
        btnAbrirSalida.visibility = if (reservada) View.GONE else View.VISIBLE
        pintarTemporizador()
    }

    private fun pintarTemporizador() {
        val p = miPlaza ?: return
        if (p.estado != EstadoPlaza.RESERVADA) return
        val restante = p.reservaHasta - System.currentTimeMillis()
        tvTiempoRestante.text = formatoMinSeg(restante)
        pbReserva.progress = (restante.coerceAtLeast(0) / 1000).toInt()
    }

    private fun confirmarReserva(p: Plaza) {
        dialogo?.dismiss()
        dialogo = AlertDialog.Builder(this)
            .setTitle(getString(R.string.formato_dialogo_titulo, p.id))
            .setMessage(getString(R.string.formato_dialogo, p.piso, sesion.patente))
            .setPositiveButton(R.string.btn_reservar) { _, _ -> vm.reservar(p.id) }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    override fun onDestroy() {
        dialogo?.dismiss()
        super.onDestroy()
    }

    private companion object {
        const val CLAVE_PISO = "piso"
    }
}
