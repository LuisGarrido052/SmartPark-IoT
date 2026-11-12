package cl.inacap.estacionamiento.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Autenticación del enlace App -> Arduino UNO R3 (desafío-respuesta con HMAC-SHA256).
 *
 * El UNO R3 no tiene potencia para AES ni TLS, pero sí puede calcular SHA-256. Por eso:
 *  1. Al conectarse, la app envía "H" (hola).
 *  2. El Arduino responde "N,<desafío>": 8 caracteres hexadecimales aleatorios, nuevos en cada conexión.
 *  3. Cada comando que mueve algo (abrir/cerrar barrera, LED de reserva) viaja firmado:
 *         <comando>|<contador>|<firma>
 *     firma = primeros 8 bytes (16 hex) de HMAC-SHA256(clave, "<desafío>|<contador>|<comando>")
 *  4. El Arduino recalcula la firma con la misma clave y exige que el contador sea mayor que el último
 *     aceptado. Así un comando capturado no sirve en otra conexión (cambia el desafío) ni dentro de la
 *     misma (el contador ya se usó): es la misma idea del nonce que usa [CanalSeguro] entre teléfonos.
 *
 * La clave nunca viaja por Bluetooth: solo viajan el desafío y las firmas.
 */
class AutenticadorArduino(clave: String) {

    private val llave: ByteArray = clave.toByteArray(Charsets.UTF_8)
    private var desafio: String? = null
    private var contador = 0L

    /** true cuando el Arduino ya entregó su desafío y se pueden firmar comandos. */
    val listo: Boolean @Synchronized get() = desafio != null

    /** Se llama al recibir "N,<desafío>" del Arduino: empieza una sesión nueva. */
    @Synchronized
    fun nuevoDesafio(nonce: String) {
        desafio = nonce
        contador = 0
    }

    /** Se llama al conectar/reconectar: el desafío anterior ya no vale. */
    @Synchronized
    fun reiniciar() {
        desafio = null
        contador = 0
    }

    /** Devuelve la línea firmada lista para enviar, o null si todavía no hay desafío. */
    @Synchronized
    fun firmar(comando: String): String? {
        val nonce = desafio ?: return null
        contador++
        return "$comando|$contador|${firma(llave, nonce, contador, comando)}"
    }

    companion object {
        /** Largo mínimo de la clave compartida con el Arduino. */
        const val LARGO_MINIMO_CLAVE = 8

        /** Formato del desafío del Arduino: 8 hexadecimales. */
        private val REGEX_DESAFIO = Regex("^[0-9a-f]{8}$")

        fun desafioValido(nonce: String) = REGEX_DESAFIO.matches(nonce)

        fun firma(llave: ByteArray, desafio: String, contador: Long, comando: String): String {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(llave, "HmacSHA256")) }
            val completo = mac.doFinal("$desafio|$contador|$comando".toByteArray(Charsets.UTF_8))
            return completo.copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
        }
    }
}
