package cl.inacap.estacionamiento.security

/** Reglas de validación de los formularios de Login y Registro. */
object Validaciones {
    private val REGEX_USUARIO = Regex("^[a-zA-Z0-9_.]{4,20}$")
    // Patentes chilenas: antigua AB1234 o nueva BCDF12
    private val REGEX_PATENTE = Regex("^([A-Z]{2}[0-9]{4}|[A-Z]{4}[0-9]{2})$")

    fun errorUsuario(usuario: String): String? = when {
        usuario.isBlank() -> "Ingresa tu nombre de usuario"
        !REGEX_USUARIO.matches(usuario) -> "Usa de 4 a 20 caracteres: letras, números, punto o guion bajo"
        else -> null
    }

    fun errorPasswordLogin(password: String): String? =
        if (password.isEmpty()) "Ingresa tu contraseña" else null

    /** Política de contraseñas: mínimo 8 caracteres, con al menos una letra y un número. */
    fun errorPasswordNueva(password: String): String? = when {
        password.length < 8 -> "Mínimo 8 caracteres"
        password.none { it.isLetter() } -> "Debe incluir al menos una letra"
        password.none { it.isDigit() } -> "Debe incluir al menos un número"
        else -> null
    }

    fun errorConfirmacion(password: String, confirmacion: String): String? =
        if (password != confirmacion) "Las contraseñas no coinciden" else null

    fun normalizarPatente(patente: String): String =
        patente.uppercase().filter { it.isLetterOrDigit() }

    fun errorPatente(patente: String): String? =
        if (!REGEX_PATENTE.matches(normalizarPatente(patente))) "Formato de patente inválido (ej: AB1234 o BCDF12)" else null
}
