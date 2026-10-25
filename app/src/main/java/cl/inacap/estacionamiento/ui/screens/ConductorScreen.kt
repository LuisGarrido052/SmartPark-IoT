package cl.inacap.estacionamiento.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.data.model.Barrera
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.model.Sesion
import cl.inacap.estacionamiento.data.repository.ParkingRepository
import cl.inacap.estacionamiento.ui.components.IndicadorEnlace
import cl.inacap.estacionamiento.ui.components.LeyendaColores
import cl.inacap.estacionamiento.ui.components.TarjetaPlaza
import cl.inacap.estacionamiento.ui.components.formatoMinSeg
import cl.inacap.estacionamiento.ui.theme.AmbarReservado
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.theme.VerdeLibre
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel

/** Terminal Android 1: Conductor. Mapa de plazas, reserva con temporizador y apertura de barrera. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConductorScreen(
    sesion: Sesion,
    vm: ParkingViewModel,
    onConexion: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    val plazas by vm.plazas.collectAsState()
    val ahora by vm.ahora.collectAsState()
    val enlaceTel by vm.enlaceTelefono.collectAsState()
    val enlaceArd by vm.enlaceArduino.collectAsState()
    val estadoTel = enlaceTel?.estado?.collectAsState()?.value
    val estadoArd = enlaceArd?.estado?.collectAsState()?.value
    var piso by rememberSaveable { mutableStateOf(1) }
    var plazaPorReservar by remember { mutableStateOf<Plaza?>(null) }
    var sugerida by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.avisos.collect { snackbar.showSnackbar(it) } }

    val miPlaza = plazas.firstOrNull { it.patente == sesion.patente && it.estado != EstadoPlaza.LIBRE }
    val libres = plazas.count { it.estado == EstadoPlaza.LIBRE }
    val delPiso = plazas.filter { it.piso == piso }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartPark IoT", fontWeight = FontWeight.Bold)
                        Text("Hola, ${sesion.nombreUsuario} · ${sesion.patente}", style = MaterialTheme.typography.labelMedium, color = TextoSecundario)
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
        if (plazas.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IndicadorEnlace("Operador", estadoTel)
                    IndicadorEnlace("Arduino", estadoArd)
                }
            }
            if (estadoTel?.fase != FaseConexion.CONECTADO) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Modo local: conéctate al teléfono del operador para sincronizar las plazas.",
                        style = MaterialTheme.typography.bodySmall, color = AmbarReservado
                    )
                }
            }

            if (miPlaza != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TarjetaReserva(
                        plaza = miPlaza,
                        restanteMs = miPlaza.reservaHasta - ahora,
                        onAbrirEntrada = { vm.solicitarApertura(Barrera.ENTRADA) },
                        onAbrirSalida = { vm.solicitarApertura(Barrera.SALIDA) },
                        onCancelar = { vm.cancelarReserva(miPlaza.id) }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Plazas libres", color = TextoSecundario, style = MaterialTheme.typography.labelLarge)
                            Text("$libres / ${plazas.size}", style = MaterialTheme.typography.headlineMedium, color = VerdeLibre, fontWeight = FontWeight.Bold)
                        }
                        LeyendaColores()
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2).forEach { n ->
                            FilterChip(selected = piso == n, onClick = { piso = n; sugerida = null }, label = { Text("Nivel $n") })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val p = vm.plazaMasCercana(piso)
                            sugerida = p?.id
                            if (p != null && miPlaza == null) plazaPorReservar = p
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = miPlaza == null
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Buscar plaza más cercana")
                    }
                }
            }

            items(delPiso, key = { it.id }) { plaza ->
                TarjetaPlaza(plaza, resaltada = plaza.id == sugerida || plaza.id == miPlaza?.id) {
                    if (plaza.estado == EstadoPlaza.LIBRE && miPlaza == null) plazaPorReservar = plaza
                }
            }
        }
    }

    plazaPorReservar?.let { p ->
        AlertDialog(
            onDismissRequest = { plazaPorReservar = null },
            title = { Text("Reservar plaza ${p.id}") },
            text = { Text("Nivel ${p.piso}. La reserva dura 15 minutos y se libera automáticamente si no llegas.\nPatente: ${sesion.patente}") },
            confirmButton = {
                Button(onClick = { vm.reservar(p.id); plazaPorReservar = null }) { Text("Reservar") }
            },
            dismissButton = { TextButton(onClick = { plazaPorReservar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TarjetaReserva(
    plaza: Plaza,
    restanteMs: Long,
    onAbrirEntrada: () -> Unit,
    onAbrirSalida: () -> Unit,
    onCancelar: () -> Unit
) {
    val reservada = plaza.estado == EstadoPlaza.RESERVADA
    val color = if (reservada) AmbarReservado else VerdeLibre
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (reservada) "Plaza #${plaza.id} - Nivel ${plaza.piso} reservada" else "Estás en la plaza #${plaza.id}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Text("Patente: ${plaza.patente}", color = TextoSecundario)
            if (reservada) {
                Spacer(Modifier.height(8.dp))
                Text("Tiempo restante de reserva", style = MaterialTheme.typography.labelLarge, color = TextoSecundario)
                Text(formatoMinSeg(restanteMs), style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.Bold)
                val fraccion = (restanteMs.toFloat() / ParkingRepository.DURACION_RESERVA_MS).coerceIn(0f, 1f)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = fraccion,
                    color = color,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Button(
                    onClick = onAbrirEntrada,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir barrera de acceso")
                }
                TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar reserva", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAbrirSalida,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Abrir barrera de salida") }
            }
        }
    }
}
