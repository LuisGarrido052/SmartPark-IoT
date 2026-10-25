package cl.inacap.estacionamiento.security

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Protección de los mensajes entre los dos teléfonos (ISO 27400, datos en tránsito):
 *  1. Cifrado AES-256-GCM (confidencialidad).
 *  2. Firma HMAC-SHA256 sobre el mensaje cifrado (integridad y autenticidad).
 *  3. Marca de tiempo + nonce de un solo uso (protección contra ataques de repetición / replay).
 *
 * Ambos teléfonos derivan las llaves a partir del mismo código de vinculación (PIN)
 * que muestra el Operador y que el Conductor ingresa al conectarse.
 */
class CanalSeguro(pin: String, private val reloj: () -> Long = System::currentTimeMillis) {

    private val llaveCifrado: ByteArray = sha256("SmartPark|cifrado|$pin")
    private val llaveFirma: ByteArray = sha256("SmartPark|firma|$pin")
    private val noncesVistos = LinkedHashMap<String, Long>()
    private val aleatorio = SecureRandom()

    /** Empaqueta un JSON en una línea de texto lista para enviar: base64(iv+cifrado).base64(hmac) */
    fun proteger(contenido: JSONObject): String {
        val sobre = JSONObject(contenido.toString())
            .put("ts", reloj())
            .put("nonce", nuevoNonce())
        val iv = ByteArray(12).also { aleatorio.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(llaveCifrado, "AES"), GCMParameterSpec(128, iv))
        val cifrado = iv + cipher.doFinal(sobre.toString().toByteArray(Charsets.UTF_8))
        return b64(cifrado) + "." + b64(hmac(cifrado))
    }

    /**
     * Verifica firma, descifra y valida vigencia y nonce.
     * Lanza [SecurityException] si el mensaje fue alterado, es antiguo o es una repetición.
     */
    fun abrir(linea: String): JSONObject {
        val partes = linea.trim().split(".")
        if (partes.size != 2) throw SecurityException("FORMATO_INVALIDO")
        val cifrado = Base64.getDecoder().decode(partes[0])
        val firma = Base64.getDecoder().decode(partes[1])
        if (!MessageDigest.isEqual(firma, hmac(cifrado))) throw SecurityException("FIRMA_INVALIDA")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(llaveCifrado, "AES"),
            GCMParameterSpec(128, cifrado, 0, 12)
        )
        val json = JSONObject(String(cipher.doFinal(cifrado, 12, cifrado.size - 12), Charsets.UTF_8))

        val ahora = reloj()
        val ts = json.optLong("ts", 0L)
        if (kotlin.math.abs(ahora - ts) > VIGENCIA_MS) throw SecurityException("TOKEN_EXPIRADO")
        val nonce = json.optString("nonce")
        synchronized(noncesVistos) {
            noncesVistos.entries.removeAll { ahora - it.value > VIGENCIA_MS * 2 }
            if (nonce.isEmpty() || noncesVistos.containsKey(nonce)) throw SecurityException("NONCE_REPETIDO")
            noncesVistos[nonce] = ahora
        }
        return json
    }

    private fun nuevoNonce(): String = ByteArray(12).also { aleatorio.nextBytes(it) }.let(::b64)

    private fun hmac(datos: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(llaveFirma, "HmacSHA256")) }.doFinal(datos)

    private fun sha256(texto: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8))

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    companion object {
        /** Un mensaje solo es válido durante 30 segundos. */
        const val VIGENCIA_MS = 30_000L

        fun generarPin(): String = (SecureRandom().nextInt(900_000) + 100_000).toString()
    }
}
