package cl.inacap.estacionamiento

import cl.inacap.estacionamiento.comm.ProtocoloArduino
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.security.AutenticadorArduino
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

    @Test
    fun protocoloArduino_interpretaDesafioYRechazo() {
        assertEquals(ProtocoloArduino.Mensaje.Desafio("0adf2ff0"), ProtocoloArduino.interpretar("N,0ADF2FF0"))
        assertEquals(ProtocoloArduino.Mensaje.Rechazo("AUTH"), ProtocoloArduino.interpretar("E,AUTH"))
        assertTrue(AutenticadorArduino.desafioValido("0adf2ff0"))
        assertFalse(AutenticadorArduino.desafioValido("xyz"))
    }

    @Test
    fun autenticadorArduino_firmaIgualQueElSketch() {
        // Vector calculado por el sketch smartpark_r3.ino (SHA-256/HMAC propios) y aceptado por él
        val firma = AutenticadorArduino.firma("SmartPark2026".toByteArray(), "0adf2ff0", 1, "A,E")
        assertEquals("2dd4bb0be6a02c04", firma)
    }

    @Test
    fun autenticadorArduino_sinDesafioNoFirmaYContadorCrece() {
        val aut = AutenticadorArduino("SmartPark2026")
        assertNull("sin desafío no se puede enviar nada", aut.firmar("A,E"))
        aut.nuevoDesafio("0adf2ff0")
        assertEquals("A,E|1|2dd4bb0be6a02c04", aut.firmar("A,E"))
        assertTrue(aut.firmar("A,E")!!.startsWith("A,E|2|"))
        aut.reiniciar()
        assertNull(aut.firmar("A,E"))
    }

    @Test
    fun autenticadorArduino_claveDistintaDaFirmaDistinta() {
        val buena = AutenticadorArduino.firma("SmartPark2026".toByteArray(), "0adf2ff0", 1, "A,E")
        val mala = AutenticadorArduino.firma("OtraClave99".toByteArray(), "0adf2ff0", 1, "A,E")
        val otraSesion = AutenticadorArduino.firma("SmartPark2026".toByteArray(), "5e8e6b8d", 1, "A,E")
        assertNotEquals(buena, mala)
        assertNotEquals(buena, otraSesion)
    }

    private fun esperarError(codigo: String, bloque: () -> Unit) {
        try {
            bloque(); fail("Se esperaba $codigo")
        } catch (e: SecurityException) {
            assertEquals(codigo, e.message)
        }
    }
}
