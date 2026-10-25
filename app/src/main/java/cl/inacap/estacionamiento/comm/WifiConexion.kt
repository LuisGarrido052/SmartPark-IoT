package cl.inacap.estacionamiento.comm

import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/** Puerto TCP usado por SmartPark en la red Wi-Fi local. */
const val PUERTO_SMARTPARK = 5050

/** Teléfono del Operador: abre un servidor TCP y espera al teléfono del Conductor. */
class WifiServidor(private val puerto: Int = PUERTO_SMARTPARK) :
    ConexionPorStream(TipoEnlace.TELEFONO, "Wi-Fi (servidor)", esServidor = true) {

    @Volatile private var servidor: ServerSocket? = null
    @Volatile private var cliente: Socket? = null

    override fun detalleEspera() = "IP ${ipLocal() ?: "sin red"} · puerto $puerto"

    override fun abrir(): Pair<InputStream, OutputStream> {
        val srv = servidor ?: ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(puerto))
        }.also { servidor = it }
        val s = srv.accept()
        s.tcpNoDelay = true
        cliente = s
        return s.getInputStream() to s.getOutputStream()
    }

    override fun cerrarCanal() {
        runCatching { cliente?.close() }
        cliente = null
    }

    override fun cerrarTodo() {
        cerrarCanal()
        runCatching { servidor?.close() }
        servidor = null
    }
}

/** Teléfono del Conductor: se conecta por TCP a la IP del Operador. */
class WifiCliente(private val ip: String, private val puerto: Int = PUERTO_SMARTPARK) :
    ConexionPorStream(TipoEnlace.TELEFONO, "Wi-Fi ($ip)", esServidor = false) {

    @Volatile private var socket: Socket? = null

    override fun detalleEspera() = "$ip:$puerto"

    override fun abrir(): Pair<InputStream, OutputStream> {
        val s = Socket()
        s.connect(InetSocketAddress(ip, puerto), 5_000)
        s.tcpNoDelay = true
        socket = s
        return s.getInputStream() to s.getOutputStream()
    }

    override fun cerrarCanal() {
        runCatching { socket?.close() }
        socket = null
    }
}

/** Busca la IPv4 local del teléfono (Wi-Fi o zona Wi-Fi/hotspot). */
fun ipLocal(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()
