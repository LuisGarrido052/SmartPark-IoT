package cl.inacap.estacionamiento.data.model

/** Roles del sistema (control de acceso basado en roles, RBAC). */
enum class Rol(val etiqueta: String) {
    CONDUCTOR("Conductor"),
    OPERADOR("Operador");
}

enum class EstadoPlaza(val etiqueta: String) {
    LIBRE("Libre"),
    OCUPADA("Ocupada"),
    RESERVADA("Reservada");
}

data class Plaza(
    val id: Int,
    val piso: Int,
    val estado: EstadoPlaza = EstadoPlaza.LIBRE,
    val patente: String = "",
    /** Momento (epoch ms) en que vence la reserva. 0 si no está reservada. */
    val reservaHasta: Long = 0L
)

data class Usuario(
    val id: Long,
    val nombreUsuario: String,
    val rol: Rol,
    val patente: String
)

data class Evento(
    val id: Long,
    val fecha: Long,
    val tipo: String,
    val descripcion: String
)

enum class Barrera(val etiqueta: String, val codigo: String) {
    ENTRADA("Entrada", "E"),
    SALIDA("Salida", "S");

    companion object {
        fun desdeCodigo(codigo: String): Barrera? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Sesión activa del usuario autenticado. */
data class Sesion(
    val nombreUsuario: String,
    val rol: Rol,
    val patente: String,
    val expiraEn: Long
)
