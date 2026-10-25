package cl.inacap.estacionamiento.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.inacap.estacionamiento.comm.EstadoConexion
import cl.inacap.estacionamiento.comm.FaseConexion
import cl.inacap.estacionamiento.comm.PUERTO_SMARTPARK
import cl.inacap.estacionamiento.comm.TipoEnlace
import cl.inacap.estacionamiento.comm.ipLocal
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.ui.components.IndicadorEnlace
import cl.inacap.estacionamiento.ui.theme.AmbarReservado
import cl.inacap.estacionamiento.ui.theme.CianConexion
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.viewmodel.ParkingViewModel

/**
 * Configuración de enlaces inalámbricos:
 *  - Otro teléfono (Conductor <-> Operador) por Wi-Fi o Bluetooth.
 *  - Placa Arduino UNO R3 con módulo HC-05 por Bluetooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ConexionScreen(rol: Rol, vm: ParkingViewModel, onVolver: () -> Unit) {
    val contexto = LocalContext.current
    val enlaceTel by vm.enlaceTelefono.collectAsState()
    val enlaceArd by vm.enlaceArduino.collectAsState()
    val estadoTel = enlaceTel?.estado?.collectAsState()?.value
    val estadoArd = enlaceArd?.estado?.collectAsState()?.value

    var medio by rememberSaveable { mutableStateOf("WIFI") }
    var ip by rememberSaveable { mutableStateOf("192.168.43.1") }
    var pin by rememberSaveable { mutableStateOf("") }

    fun tienePermisoBt() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    var permisoBt by remember { mutableStateOf(tienePermisoBt()) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permisoBt = it }
    val vinculados: List<BluetoothDevice> = remember(permisoBt) { if (permisoBt) vm.dispositivosVinculados() else emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conexión inalámbrica", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------- Teléfono a teléfono ----------------
            item {
                Seccion(if (rol == Rol.OPERADOR) "Recibir al teléfono del Conductor" else "Conectar con el teléfono del Operador") {
                    IndicadorEnlace("Estado", estadoTel)
                    estadoTel?.detalle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextoSecundario) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = medio == "WIFI", onClick = { medio = "WIFI" }, label = { Text("Wi-Fi") })
                        FilterChip(selected = medio == "BT", onClick = { medio = "BT" }, label = { Text("Bluetooth") })
                    }
                    Spacer(Modifier.height(8.dp))

                    if (rol == Rol.OPERADOR) {
                        Text("PIN de vinculación: ${vm.pin}", style = MaterialTheme.typography.titleLarge, color = CianConexion, fontWeight = FontWeight.Bold)
                        Text("El conductor debe ingresar este PIN. Con él se cifran y firman los mensajes.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        Spacer(Modifier.height(8.dp))
                        if (medio == "WIFI") {
                            Text("IP de este teléfono: ${ipLocal() ?: "sin red Wi-Fi"} · puerto $PUERTO_SMARTPARK", color = TextoSecundario)
                            BotonEnlace(estadoTel, "Iniciar servidor Wi-Fi", onIniciar = { vm.iniciarServidorWifi() }, onDetener = { vm.desconectar(TipoEnlace.TELEFONO) })
                        } else {
                            if (!permisoBt) BotonPermiso { pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                            else BotonEnlace(estadoTel, "Esperar conexión Bluetooth", onIniciar = { vm.iniciarServidorBluetooth() }, onDetener = { vm.desconectar(TipoEnlace.TELEFONO) })
                            Text("Vincula ambos teléfonos antes en Ajustes > Bluetooth.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        }
                    } else {
                        OutlinedTextField(
                            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                            label = { Text("PIN del operador") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (medio == "WIFI") {
                            OutlinedTextField(
                                value = ip, onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                                label = { Text("IP del teléfono del operador") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            BotonEnlace(
                                estadoTel, "Conectar por Wi-Fi",
                                habilitado = pin.length == 6 && ip.count { it == '.' } == 3,
                                onIniciar = { vm.conectarWifi(ip, pin) },
                                onDetener = { vm.desconectar(TipoEnlace.TELEFONO) }
                            )
                        } else {
                            if (!permisoBt) BotonPermiso { pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                            else ListaDispositivos(vinculados, estadoTel, habilitado = pin.length == 6,
                                onElegir = { vm.conectarBluetoothTelefono(it, pin) },
                                onDetener = { vm.desconectar(TipoEnlace.TELEFONO) })
                        }
                    }
                }
            }

            // ---------------- Arduino ----------------
            item {
                Seccion("Placa Arduino UNO R3 (módulo HC-05)") {
                    IndicadorEnlace("Estado", estadoArd)
                    Text(
                        "Sensores de plaza (HC-SR04) y barrera (servo). Vincula el HC-05 en Ajustes > Bluetooth (PIN habitual 1234).",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        !vm.bluetoothDisponible -> Text("Este teléfono no tiene Bluetooth", color = AmbarReservado)
                        !permisoBt -> BotonPermiso { pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        !vm.bluetoothActivo -> Text("Activa el Bluetooth del teléfono", color = AmbarReservado)
                        else -> ListaDispositivos(vinculados, estadoArd, habilitado = true,
                            onElegir = { vm.conectarArduino(it) },
                            onDetener = { vm.desconectar(TipoEnlace.ARDUINO) })
                    }
                }
            }

            item {
                Text(
                    "Consejo: si la red Wi-Fi de la sede bloquea la comunicación entre equipos, activa la zona Wi-Fi (hotspot) en el teléfono del operador y conecta el otro teléfono a ella. La IP del operador suele ser 192.168.43.1.",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            contenido()
        }
    }
}

private fun activo(e: EstadoConexion?) = e != null && e.fase != FaseConexion.DESCONECTADO

@Composable
private fun BotonEnlace(estado: EstadoConexion?, texto: String, habilitado: Boolean = true, onIniciar: () -> Unit, onDetener: () -> Unit) {
    if (activo(estado)) {
        OutlinedButton(onClick = onDetener, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Desconectar") }
    } else {
        Button(onClick = onIniciar, enabled = habilitado, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(texto) }
    }
}

@Composable
private fun BotonPermiso(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Permitir acceso a Bluetooth") }
}

@SuppressLint("MissingPermission")
@Composable
private fun ListaDispositivos(
    dispositivos: List<BluetoothDevice>,
    estado: EstadoConexion?,
    habilitado: Boolean,
    onElegir: (BluetoothDevice) -> Unit,
    onDetener: () -> Unit
) {
    if (activo(estado)) {
        OutlinedButton(onClick = onDetener, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Desconectar") }
        return
    }
    if (dispositivos.isEmpty()) {
        Text("No hay dispositivos vinculados.", color = AmbarReservado)
        return
    }
    Text("Dispositivos vinculados:", style = MaterialTheme.typography.labelLarge, color = TextoSecundario)
    dispositivos.forEach { d ->
        OutlinedButton(
            onClick = { onElegir(d) },
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("${d.name ?: "Sin nombre"} (${d.address})") }
    }
}
