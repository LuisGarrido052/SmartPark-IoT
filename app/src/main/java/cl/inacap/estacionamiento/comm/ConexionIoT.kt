package cl.inacap.estacionamiento.comm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/** Con quién se habla por el enlace: con el otro teléfono Android o con la placa Arduino. */
enum class TipoEnlace { TELEFONO, ARDUINO }

enum class FaseConexion(val etiqueta: String) {
    DESCONECTADO("Desconectado"),
    ESPERANDO("Esperando conexión"),
    CONECTANDO("Conectando"),
    CONECTADO("Conectado"),
    RECONECTANDO("Reconectando");
}

data class EstadoConexion(val fase: FaseConexion, val detalle: String = "")

/**
 * Interfaz común para cualquier medio inalámbrico. La lógica de la app no sabe si
 * el mensaje viaja por Wi-Fi o Bluetooth, ni si al otro lado hay un teléfono o un Arduino.
 * Así se puede cambiar de tecnología sin tocar las pantallas.
 */
interface ConexionIoT {
    val tipo: TipoEnlace
    val medio: String
    val estado: StateFlow<EstadoConexion>
    /** Cada línea de texto recibida (protocolo orientado a líneas terminadas en '\n'). */
    val lineas: SharedFlow<String>

    fun iniciar(scope: CoroutineScope)
    suspend fun enviar(linea: String): Boolean
    fun cerrar()
}

/**
 * Implementación base para enlaces basados en streams (socket TCP o socket RFCOMM Bluetooth).
 * Se encarga de leer línea a línea, escribir de forma segura entre hilos y reconectar
 * automáticamente si se cae la conexión (resiliencia OT).
 */
abstract class ConexionPorStream(
    final override val tipo: TipoEnlace,
    final override val medio: String,
    private val esServidor: Boolean
) : ConexionIoT {

    private val _estado = MutableStateFlow(EstadoConexion(FaseConexion.DESCONECTADO))
    override val estado: StateFlow<EstadoConexion> = _estado

    private val _lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lineas: SharedFlow<String> = _lineas

    private var trabajo: Job? = null
    private val mutexEscritura = Mutex()
    @Volatile private var salida: OutputStream? = null
    @Volatile private var activo = false

    /** Abre (o acepta) la conexión. Debe bloquear hasta que exista un canal listo. */
    protected abstract fun abrir(): Pair<InputStream, OutputStream>

    /** Libera el socket actual (no el servidor). */
    protected abstract fun cerrarCanal()

    /** Libera todo, incluido el socket servidor si existe. */
    protected open fun cerrarTodo() = cerrarCanal()

    /** Texto para mostrar mientras se espera, ej. "IP 192.168.43.1:5050". */
    protected open fun detalleEspera(): String = ""

    protected fun publicar(fase: FaseConexion, detalle: String = "") {
        _estado.value = EstadoConexion(fase, detalle)
    }

    override fun iniciar(scope: CoroutineScope) {
        if (trabajo?.isActive == true) return
        activo = true
        trabajo = scope.launch {
            var intentos = 0
            while (activo && isActive) {
                try {
                    publicar(
                        if (esServidor) FaseConexion.ESPERANDO else if (intentos == 0) FaseConexion.CONECTANDO else FaseConexion.RECONECTANDO,
                        detalleEspera()
                    )
                    val (entrada, out) = abrir()
                    salida = out
                    intentos = 0
                    publicar(FaseConexion.CONECTADO, medio)
                    val lector = BufferedReader(InputStreamReader(entrada, Charsets.UTF_8))
                    while (activo) {
                        val linea = lector.readLine() ?: break
                        val limpia = linea.trim()
                        if (limpia.isNotEmpty()) _lineas.emit(limpia)
                    }
                } catch (e: Exception) {
                    if (activo) publicar(FaseConexion.RECONECTANDO, e.message ?: "Error de conexión")
                } finally {
                    salida = null
                    cerrarCanal()
                }
                if (!activo) break
                intentos++
                delay(if (esServidor) 500 else 3_000)
            }
            publicar(FaseConexion.DESCONECTADO)
        }
    }

    override suspend fun enviar(linea: String): Boolean = mutexEscritura.withLock {
        val out = salida ?: return false
        return try {
            out.write((linea + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: Exception) {
            cerrarCanal()
            false
        }
    }

    override fun cerrar() {
        activo = false
        cerrarTodo()
        trabajo?.cancel()
        publicar(FaseConexion.DESCONECTADO)
    }
}
