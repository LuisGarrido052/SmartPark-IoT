package cl.inacap.estacionamiento.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.ui.components.IndicadorEnlace
import cl.inacap.estacionamiento.ui.components.LeyendaColores
import cl.inacap.estacionamiento.ui.components.TarjetaKpi
import cl.inacap.estacionamiento.ui.components.TarjetaPlaza
import cl.inacap.estacionamiento.ui.theme.AmbarReservado
import cl.inacap.estacionamiento.ui.theme.CianConexion
import cl.inacap.estacionamiento.ui.theme.RojoOcupado
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.theme.VerdeLibre
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Terminal Android 2: Operador. Telemetría, control de barreras y log de eventos (OT). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperadorScreen(
    sesion: Sesion,
    vm: ParkingViewModel,
    onConexion: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    val plazas by vm.plazas.collectAsState()
    val barreras by vm.barreras.collectAsState()
    val eventos by vm.eventos.collectAsState()
    val filtro by vm.filtroEventos.collectAsState()
    val enlaceTel by vm.enlaceTelefono.collectAsState()
    val enlaceArd by vm.enlaceArduino.collectAsState()
    val estadoTel = enlaceTel?.estado?.collectAsState()?.value
    val estadoArd = enlaceArd?.estado?.collectAsState()?.value
    val snackbar = remember { SnackbarHostState() }
    val formato = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(Unit) { vm.avisos.collect { snackbar.showSnackbar(it) } }

    val libres = plazas.count { it.estado == EstadoPlaza.LIBRE }
    val reservadas = plazas.count { it.estado == EstadoPlaza.RESERVADA }
    val ocupacion = if (plazas.isEmpty()) 0 else ((plazas.size - libres) * 100 / plazas.size)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel de Operador", fontWeight = FontWeight.Bold)
                        Text("${sesion.nombreUsuario} · PIN de vinculación ${vm.pin}", style = MaterialTheme.typography.labelMedium, color = CianConexion)
                    }
                },
                actions = {
                    IconButton(onClick = onConexion) { Icon(Icons.Filled.Settings, contentDescription = "Conexión") }
                    IconButton(onClick = onCerrarSesion) { Icon(Icons.Filled.ExitToApp, contentDescription = "Cerrar sesión") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        IndicadorEnlace("Conductor", estadoTel)
                        estadoTel?.detalle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                        }
                        Spacer(Modifier.height(6.dp))
                        IndicadorEnlace("Arduino", estadoArd)
                        Spacer(Modifier.height(6.dp))
                        Text("Red IoT cifrada AES-256-GCM + HMAC-SHA256 · ISO/IEC 27400", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TarjetaKpi("Aforo", "$ocupacion%", "ocupación", if (ocupacion > 80) RojoOcupado else CianConexion, Modifier.weight(1f))
                    TarjetaKpi("Libres", "$libres", "de ${plazas.size} plazas", VerdeLibre, Modifier.weight(1f))
                    TarjetaKpi("Reservas", "$reservadas", "activas", AmbarReservado, Modifier.weight(1f))
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Control de barreras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Barrera.entries.forEach { b ->
                            val abierta = barreras[b] == true
                            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Barrera ${b.etiqueta}")
                                    Text(
                                        if (abierta) "ABIERTA" else "CERRADA",
                                        color = if (abierta) VerdeLibre else TextoSecundario,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Switch(checked = abierta, onCheckedChange = { vm.operarBarrera(b, it) })
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    Text("Plazas (toca para corregir en contingencia)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    LeyendaColores()
                }
            }
            // Grilla de 4 columnas armada con filas (evita anidar listas desplazables)
            items(plazas.chunked(4), key = { fila -> fila.first().id }) { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { p ->
                        TarjetaPlaza(p, modifier = Modifier.weight(1f)) { vm.cambiarEstadoManual(p.id) }
                    }
                    repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            item {
                Column {
                Text("Log de eventos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filtro == null, onClick = { vm.filtrarEventos(null) }, label = { Text("Todos") })
                    vm.tiposEvento.forEach { t ->
                        FilterChip(selected = filtro == t, onClick = { vm.filtrarEventos(t) }, label = { Text(t.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                }
            }
            if (eventos.isEmpty()) {
                item { Text("Sin eventos registrados", color = TextoSecundario) }
            }
            items(eventos, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth()) {
                    Text(formato.format(Date(e.fecha)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = CianConexion)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        e.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (e.tipo == "SEGURIDAD") RojoOcupado else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
