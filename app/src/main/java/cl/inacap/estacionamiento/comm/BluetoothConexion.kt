package cl.inacap.estacionamiento.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** UUID estándar SPP (puerto serie). Lo usan el módulo HC-05/HC-06 y también los teléfonos entre sí. */
val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * Cliente Bluetooth clásico. Sirve para:
 *  - conectarse al módulo HC-05 del Arduino UNO R3 (tipo ARDUINO), o
 *  - conectarse al teléfono del Operador (tipo TELEFONO).
 * El dispositivo debe estar vinculado previamente desde Ajustes > Bluetooth.
 */
@SuppressLint("MissingPermission") // El permiso BLUETOOTH_CONNECT se solicita en la pantalla de conexión
class BluetoothCliente(
    private val adaptador: BluetoothAdapter,
    private val dispositivo: BluetoothDevice,
    tipo: TipoEnlace
) : ConexionPorStream(tipo, "Bluetooth (${dispositivo.name ?: dispositivo.address})", esServidor = false) {

    @Volatile private var socket: BluetoothSocket? = null

    override fun detalleEspera() = dispositivo.name ?: dispositivo.address

    override fun abrir(): Pair<InputStream, OutputStream> {
        adaptador.cancelDiscovery()
        val s = dispositivo.createRfcommSocketToServiceRecord(UUID_SPP)
        s.connect()
        socket = s
        return s.inputStream to s.outputStream
    }

    override fun cerrarCanal() {
        runCatching { socket?.close() }
        socket = null
    }
}

/** Servidor Bluetooth: el teléfono del Operador espera que el Conductor se conecte. */
@SuppressLint("MissingPermission")
class BluetoothServidor(private val adaptador: BluetoothAdapter) :
    ConexionPorStream(TipoEnlace.TELEFONO, "Bluetooth (servidor)", esServidor = true) {

    @Volatile private var servidor: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null

    override fun detalleEspera() = "Visible como \"${adaptador.name ?: "este teléfono"}\""

    override fun abrir(): Pair<InputStream, OutputStream> {
        val srv = servidor ?: adaptador.listenUsingRfcommWithServiceRecord("SmartPark", UUID_SPP).also { servidor = it }
        val s = srv.accept()
        socket = s
        return s.inputStream to s.outputStream
    }

    override fun cerrarCanal() {
        runCatching { socket?.close() }
        socket = null
    }

    override fun cerrarTodo() {
        cerrarCanal()
        runCatching { servidor?.close() }
        servidor = null
    }
}
