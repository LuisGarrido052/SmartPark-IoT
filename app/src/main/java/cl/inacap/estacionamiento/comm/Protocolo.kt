package cl.inacap.estacionamiento.comm

import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Plaza
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocolo con el Arduino UNO R3 (texto simple, fácil de leer con Serial.readStringUntil('\n')).
 *
 *  Arduino -> App
 *    P,<n>,<0|1>     Sensor de la plaza n (1 = ocupada). La plaza n corresponde a la 100+n (Nivel 1).
 *    B,<E|S>,<0|1>   Estado de la barrera de Entrada/Salida (1 = abierta).
 *
 *  App -> Arduino
 *    A,<E|S>         Abrir barrera
 *    C,<E|S>         Cerrar barrera
 *    R,<n>,<0|1>     Plaza n reservada (enciende/apaga LED ámbar)
 *    ?               Pedir estado de todos los sensores
 */
object ProtocoloArduino {

    sealed class Mensaje {
        data class Sensor(val plazaId: Int, val ocupada: Boolean) : Mensaje()
        data class EstadoBarrera(val barrera: Barrera, val abierta: Boolean) : Mensaje()
    }

    fun interpretar(linea: String): Mensaje? {
        val p = linea.trim().split(",").map { it.trim() }
        return when {
            p.size == 3 && p[0] == "P" -> {
                val n = p[1].toIntOrNull() ?: return null
                Mensaje.Sensor(100 + n, p[2] == "1")
            }
            p.size == 3 && p[0] == "B" -> {
                val b = Barrera.desdeCodigo(p[1]) ?: return null
                Mensaje.EstadoBarrera(b, p[2] == "1")
            }
            else -> null
        }
    }

    fun abrir(b: Barrera) = "A,${b.codigo}"
    fun cerrar(b: Barrera) = "C,${b.codigo}"
    fun reservada(plazaId: Int, reservada: Boolean) = "R,${plazaId - 100},${if (reservada) 1 else 0}"
    const val PEDIR_ESTADO = "?"
}

/**
 * Protocolo entre los dos teléfonos (JSON, luego cifrado y firmado por CanalSeguro).
 */
object ProtocoloTelefono {
    const val SOLICITAR_ESTADO = "SOLICITAR_ESTADO"
    const val ESTADO = "ESTADO"
    const val RESERVAR = "RESERVAR"
    const val CANCELAR = "CANCELAR"
    const val ABRIR_BARRERA = "ABRIR_BARRERA"
    const val RESPUESTA = "RESPUESTA"

    fun mensaje(tipo: String) = JSONObject().put("tipo", tipo)

    fun estado(plazas: List<Plaza>, barreras: Map<Barrera, Boolean>): JSONObject {
        val arr = JSONArray()
        plazas.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("piso", it.piso)
                    .put("estado", it.estado.name)
                    .put("patente", it.patente)
                    .put("reservaHasta", it.reservaHasta)
            )
        }
        val b = JSONObject()
        barreras.forEach { (k, v) -> b.put(k.name, v) }
        return mensaje(ESTADO).put("plazas", arr).put("barreras", b)
    }

    fun leerPlazas(json: JSONObject): List<Plaza> {
        val arr = json.getJSONArray("plazas")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Plaza(
                id = o.getInt("id"),
                piso = o.getInt("piso"),
                estado = EstadoPlaza.valueOf(o.getString("estado")),
                patente = o.optString("patente"),
                reservaHasta = o.optLong("reservaHasta")
            )
        }
    }

    fun leerBarreras(json: JSONObject): Map<Barrera, Boolean> {
        val o = json.optJSONObject("barreras") ?: return emptyMap()
        return Barrera.entries.associateWith { o.optBoolean(it.name, false) }
    }
}
