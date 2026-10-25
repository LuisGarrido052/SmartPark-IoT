package cl.inacap.estacionamiento.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.inacap.estacionamiento.comm.EstadoConexion
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.ui.theme.AmbarReservado
import cl.inacap.estacionamiento.ui.theme.CianConexion
import cl.inacap.estacionamiento.ui.theme.RojoOcupado
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.theme.VerdeLibre

fun colorPlaza(estado: EstadoPlaza): Color = when (estado) {
    EstadoPlaza.LIBRE -> VerdeLibre
    EstadoPlaza.OCUPADA -> RojoOcupado
    EstadoPlaza.RESERVADA -> AmbarReservado
}

/** Tarjeta de una plaza del mapa con su color según estado. */
@Composable
fun TarjetaPlaza(plaza: Plaza, modifier: Modifier = Modifier, resaltada: Boolean = false, onClick: () -> Unit) {
    val color = colorPlaza(plaza.estado)
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(if (resaltada) 3.dp else 1.dp, color.copy(alpha = if (resaltada) 1f else 0.7f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${plaza.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(plaza.estado.etiqueta, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Indicador redondo de estado de un enlace inalámbrico. */
@Composable
fun IndicadorEnlace(nombre: String, estado: EstadoConexion?) {
    val fase = estado?.fase ?: FaseConexion.DESCONECTADO
    val color = when (fase) {
        FaseConexion.CONECTADO -> VerdeLibre
        FaseConexion.ESPERANDO, FaseConexion.CONECTANDO, FaseConexion.RECONECTANDO -> AmbarReservado
        FaseConexion.DESCONECTADO -> TextoSecundario
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$nombre: ${fase.etiqueta}", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Tarjeta de indicador (KPI). */
@Composable
fun TarjetaKpi(titulo: String, valor: String, subtitulo: String, color: Color = CianConexion, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = TextoSecundario, textAlign = TextAlign.Center)
            Text(valor, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            Text(subtitulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario, textAlign = TextAlign.Center)
        }
    }
}

/** Leyenda de colores del mapa. */
@Composable
fun LeyendaColores() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        EstadoPlaza.entries.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorPlaza(e)))
                Spacer(Modifier.width(4.dp))
                Text(e.etiqueta, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
            }
        }
    }
}

fun formatoMinSeg(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(total / 60, total % 60)
}
