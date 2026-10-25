package cl.inacap.estacionamiento.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.data.model.Sesion
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la sesión cifrada con AES-256-GCM. La llave vive en el Android Keystore
 * (hardware seguro del teléfono), por lo que no puede extraerse del dispositivo.
 * Es el mismo mecanismo que usa EncryptedSharedPreferences, sin agregar dependencias.
 */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("sesion_segura", Context.MODE_PRIVATE)

    fun guardar(sesion: Sesion) {
        val json = JSONObject()
            .put("usuario", sesion.nombreUsuario)
            .put("rol", sesion.rol.name)
            .put("patente", sesion.patente)
            .put("expira", sesion.expiraEn)
            .toString()
        prefs.edit().putString(CLAVE, cifrar(json)).apply()
    }

    /** Devuelve la sesión si existe y no ha expirado. */
    fun obtener(): Sesion? {
        val cifrado = prefs.getString(CLAVE, null) ?: return null
        return try {
            val json = JSONObject(descifrar(cifrado))
            val sesion = Sesion(
                nombreUsuario = json.getString("usuario"),
                rol = Rol.valueOf(json.getString("rol")),
                patente = json.optString("patente"),
                expiraEn = json.getLong("expira")
            )
            if (sesion.expiraEn < System.currentTimeMillis()) {
                cerrar(); null
            } else sesion
        } catch (e: Exception) {
            cerrar(); null
        }
    }

    fun cerrar() {
        prefs.edit().remove(CLAVE).apply()
    }

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    private fun cifrar(texto: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, llave())
        val datos = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + datos)
    }

    private fun descifrar(base64: String): String {
        val bytes = Base64.getDecoder().decode(base64)
        val iv = bytes.copyOfRange(0, 12)
        val datos = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(datos), Charsets.UTF_8)
    }

    companion object {
        private const val ALIAS = "smartpark_sesion"
        private const val CLAVE = "sesion"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        /** La sesión expira a las 8 horas (un turno). */
        const val DURACION_MS = 8 * 60 * 60 * 1000L
    }
}
