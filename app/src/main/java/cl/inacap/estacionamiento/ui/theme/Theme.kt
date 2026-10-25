package cl.inacap.estacionamiento.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semiótica de colores definida en el informe (Material Design 3, modo oscuro de alto contraste)
val VerdeLibre = Color(0xFF00E676)
val RojoOcupado = Color(0xFFFF5252)
val AmbarReservado = Color(0xFFFFD700)
val CianConexion = Color(0xFF00B8D4)
val Fondo = Color(0xFF0E1116)
val Superficie = Color(0xFF171B22)
val SuperficieAlta = Color(0xFF212733)
val TextoSecundario = Color(0xFF9AA4B2)

private val Esquema = darkColorScheme(
    primary = VerdeLibre,
    onPrimary = Color(0xFF00210E),
    secondary = CianConexion,
    onSecondary = Color(0xFF001F25),
    tertiary = AmbarReservado,
    onTertiary = Color(0xFF231B00),
    error = RojoOcupado,
    onError = Color(0xFF2D0000),
    background = Fondo,
    onBackground = Color(0xFFE6EAF0),
    surface = Superficie,
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoSecundario,
    outline = Color(0xFF3A4352)
)

@Composable
fun SmartParkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, content = content)
}
