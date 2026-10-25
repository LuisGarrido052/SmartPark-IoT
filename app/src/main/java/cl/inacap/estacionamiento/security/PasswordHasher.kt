package cl.inacap.estacionamiento.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 + sal aleatoria (ISO 27400: credenciales cifradas).
 * La contraseña nunca se guarda en texto plano: solo la sal y el hash.
 */
object PasswordHasher {
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"
    private const val ITERACIONES = 60_000
    private const val LARGO_BITS = 256
    private const val LARGO_SAL = 16

    data class Resultado(val hash: String, val sal: String)

    fun generarHash(password: CharArray): Resultado {
        val sal = ByteArray(LARGO_SAL).also { SecureRandom().nextBytes(it) }
        val hash = derivar(password, sal)
        return Resultado(b64(hash), b64(sal))
    }

    fun verificar(password: CharArray, hashGuardado: String, salGuardada: String): Boolean {
        val sal = Base64.getDecoder().decode(salGuardada)
        val esperado = Base64.getDecoder().decode(hashGuardado)
        val calculado = derivar(password, sal)
        // Comparación en tiempo constante para evitar ataques de tiempo
        return MessageDigest.isEqual(esperado, calculado)
    }

    private fun derivar(password: CharArray, sal: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, sal, ITERACIONES, LARGO_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
