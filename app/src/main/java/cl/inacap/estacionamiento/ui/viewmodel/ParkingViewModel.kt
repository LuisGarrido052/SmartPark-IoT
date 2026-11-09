package cl.inacap.estacionamiento.ui.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import cl.inacap.estacionamiento.ServiceLocator
import cl.inacap.estacionamiento.comm.BluetoothCliente
import cl.inacap.estacionamiento.comm.BluetoothServidor
import cl.inacap.estacionamiento.comm.TipoEnlace
import cl.inacap.estacionamiento.comm.WifiCliente
import cl.inacap.estacionamiento.comm.WifiServidor
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.repository.ParkingRepository

/**
 * ViewModel (MVVM) compartido por las pantallas del Conductor, Operador y Conexión.
 * Expone los flujos del repositorio para que cada Activity actualice sus vistas XML.
 */
class ParkingViewModel(context: Context) {
    private val repo = ServiceLocator.parking

    val plazas = repo.plazas
    val barreras = repo.barreras
    val eventos = repo.eventos
    val filtroEventos = repo.filtroEventos
    val enlaceTelefono = repo.enlaceTelefono
    val enlaceArduino = repo.enlaceArduino
    val avisos = repo.avisos
    val pin: String get() = repo.pinOperador
    val tiposEvento = ParkingRepository.TIPOS_EVENTO

    private val bluetooth =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ---- Conductor ----
    fun reservar(id: Int) = repo.reservar(id)
    fun cancelarReserva(id: Int) = repo.cancelarReserva(id)
    fun solicitarApertura(b: Barrera) = repo.solicitarApertura(b)

    /** "Plaza más cercana": la libre de menor número en el nivel (más próxima al acceso). */
    fun plazaMasCercana(piso: Int): Plaza? =
        plazas.value.filter { it.piso == piso && it.estado == EstadoPlaza.LIBRE }.minByOrNull { it.id }

    // ---- Operador ----
    fun operarBarrera(b: Barrera, abrir: Boolean) = repo.operarBarrera(b, abrir)
    fun cambiarEstadoManual(id: Int) = repo.cambiarEstadoManual(id)
    fun filtrarEventos(tipo: String?) = repo.filtrarEventos(tipo)

    // ---- Conexiones ----
    val bluetoothDisponible: Boolean get() = bluetooth != null
    val bluetoothActivo: Boolean get() = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun dispositivosVinculados(): List<BluetoothDevice> =
        runCatching { bluetooth?.bondedDevices?.toList().orEmpty().sortedBy { it.name ?: "" } }.getOrDefault(emptyList())

    fun iniciarServidorWifi() = repo.conectar(WifiServidor())

    fun conectarWifi(ip: String, pin: String) = repo.conectar(WifiCliente(ip.trim()), pin.trim())

    fun iniciarServidorBluetooth() {
        val bt = bluetooth ?: return
        repo.conectar(BluetoothServidor(bt))
    }

    fun conectarBluetoothTelefono(d: BluetoothDevice, pin: String) {
        val bt = bluetooth ?: return
        repo.conectar(BluetoothCliente(bt, d, TipoEnlace.TELEFONO), pin.trim())
    }

    fun conectarArduino(d: BluetoothDevice) {
        val bt = bluetooth ?: return
        repo.conectar(BluetoothCliente(bt, d, TipoEnlace.ARDUINO))
    }

    fun desconectar(tipo: TipoEnlace) = repo.desconectar(tipo)
}
