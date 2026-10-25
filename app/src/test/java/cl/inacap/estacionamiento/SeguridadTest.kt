package cl.inacap.estacionamiento

import cl.inacap.estacionamiento.comm.ProtocoloArduino
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.security.CanalSeguro
import cl.inacap.estacionamiento.security.PasswordHasher
import cl.inacap.estacionamiento.security.Validaciones
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeguridadTest {

    @Test
    fun hashDeContrasena_verificaSoloLaCorrecta() {
        val r = PasswordHasher.generarHash("Clave2026".toCharArray())
        assertTrue(PasswordHasher.verificar("Clave2026".toCharArray(), r.hash, r.sal))
        assertFalse(PasswordHasher.verificar("clave2026".toCharArray(), r.hash, r.sal))
        assertFalse(r.hash.contains("Clave2026"))
    }

    @Test
    fun hashDeContrasena_usaSalDistintaCadaVez() {
        val a = PasswordHasher.generarHash("Clave2026".toCharArray())
        val b = PasswordHasher.generarHash("Clave2026".toCharArray())
        assertNotEquals(a.sal, b.sal)
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun validaciones_deFormulario() {
        assertNull(Validaciones.errorUsuario("luis_g"))
        assertNotNull(Validaciones.errorUsuario("ab"))
        assertNotNull(Validaciones.errorPasswordNueva("corta1"))
        assertNotNull(Validaciones.errorPasswordNueva("sinnumeros"))
        assertNull(Validaciones.errorPasswordNueva("Segura2026"))
        assertNull(Validaciones.errorPatente("abcd12"))
        assertNull(Validaciones.errorPatente("AB-1234"))
        assertNotNull(Validaciones.errorPatente("12ABCD"))
    }

    @Test
    fun canalSeguro_mensajeIdaYVuelta() {
        val emisor = CanalSeguro("123456")
        val receptor = CanalSeguro("123456")
        val linea = emisor.proteger(JSONObject().put("tipo", "RESERVAR").put("plaza", 105))
        assertFalse("el contenido debe viajar cifrado", linea.contains("RESERVAR"))
        val json = receptor.abrir(linea)
        assertEquals(105, json.getInt("plaza"))
    }

    @Test
    fun canalSeguro_rechazaRepeticion() {
        val emisor = CanalSeguro("123456")
        val receptor = CanalSeguro("123456")
        val linea = emisor.proteger(JSONObject().put("tipo", "ABRIR_BARRERA"))
        receptor.abrir(linea)
        esperarError("NONCE_REPETIDO") { receptor.abrir(linea) }
    }

    @Test
    fun canalSeguro_rechazaPinDistintoYMensajeAntiguo() {
        val linea = CanalSeguro("111111").proteger(JSONObject().put("tipo", "X"))
        esperarError("FIRMA_INVALIDA") { CanalSeguro("222222").abrir(linea) }

        var reloj = 1_000_000L
        val emisor = CanalSeguro("123456") { reloj }
        val receptor = CanalSeguro("123456") { reloj }
        val vieja = emisor.proteger(JSONObject().put("tipo", "X"))
        reloj += 60_000
        esperarError("TOKEN_EXPIRADO") { receptor.abrir(vieja) }
    }

    @Test
    fun canalSeguro_rechazaMensajeAlterado() {
        val linea = CanalSeguro("123456").proteger(JSONObject().put("tipo", "X"))
        val alterada = (if (linea[5] == 'A') 'B' else 'A').let { linea.substring(0, 5) + it + linea.substring(6) }
        esperarError("FIRMA_INVALIDA") { CanalSeguro("123456").abrir(alterada) }
    }

    @Test
    fun protocoloArduino_interpretaLineas() {
        assertEquals(ProtocoloArduino.Mensaje.Sensor(101, true), ProtocoloArduino.interpretar("P,1,1"))
        assertEquals(ProtocoloArduino.Mensaje.EstadoBarrera(Barrera.ENTRADA, false), ProtocoloArduino.interpretar("B,E,0"))
        assertNull(ProtocoloArduino.interpretar("hola"))
        assertEquals("A,S", ProtocoloArduino.abrir(Barrera.SALIDA))
        assertEquals("R,2,1", ProtocoloArduino.reservada(102, true))
    }

    private fun esperarError(codigo: String, bloque: () -> Unit) {
        try {
            bloque(); fail("Se esperaba $codigo")
        } catch (e: SecurityException) {
            assertEquals(codigo, e.message)
        }
    }
}
