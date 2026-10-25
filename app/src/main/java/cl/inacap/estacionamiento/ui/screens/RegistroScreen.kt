package cl.inacap.estacionamiento.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.security.Validaciones
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.viewmodel.AuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    estado: AuthUiState,
    onRegistrar: (usuario: String, password: String, rol: Rol, patente: String, codigo: String) -> Unit,
    onVolver: () -> Unit
) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var rol by rememberSaveable { mutableStateOf(Rol.CONDUCTOR) }
    var patente by rememberSaveable { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var intento by rememberSaveable { mutableStateOf(false) }

    val eUsuario = Validaciones.errorUsuario(usuario)
    val ePassword = Validaciones.errorPasswordNueva(password)
    val eConfirmacion = Validaciones.errorConfirmacion(password, confirmacion)
    val ePatente = if (rol == Rol.CONDUCTOR) Validaciones.errorPatente(patente) else null
    val eCodigo = if (rol == Rol.OPERADOR && codigo.isBlank()) "Ingresa el código entregado por el administrador" else null
    val valido = listOf(eUsuario, ePassword, eConfirmacion, ePatente, eCodigo).all { it == null }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
            Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        Text("Tipo de cuenta", style = MaterialTheme.typography.labelLarge, color = TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rol.entries.forEach { r ->
                FilterChip(selected = rol == r, onClick = { rol = r }, label = { Text(r.etiqueta) })
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it.take(20) },
            label = { Text("Usuario") }, singleLine = true,
            isError = intento && eUsuario != null,
            supportingText = { Text(if (intento && eUsuario != null) eUsuario else "4 a 20 caracteres") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it.take(64) },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            isError = intento && ePassword != null,
            supportingText = { Text(if (intento && ePassword != null) ePassword else "Mínimo 8 caracteres, con letras y números") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmacion, onValueChange = { confirmacion = it.take(64) },
            label = { Text("Repetir contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            isError = intento && eConfirmacion != null,
            supportingText = if (intento && eConfirmacion != null) { { Text(eConfirmacion) } } else null,
            modifier = Modifier.fillMaxWidth()
        )
        if (rol == Rol.CONDUCTOR) {
            OutlinedTextField(
                value = patente, onValueChange = { patente = it.uppercase().take(8) },
                label = { Text("Patente del vehículo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                isError = intento && ePatente != null,
                supportingText = { Text(if (intento && ePatente != null) ePatente else "Ej: ABCD12") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = codigo, onValueChange = { codigo = it.take(20) },
                label = { Text("Código de autorización de operador") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = intento && eCodigo != null,
                supportingText = { Text(if (intento && eCodigo != null) eCodigo else "Solo personal autorizado del recinto") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (estado.error != null) {
            Text(estado.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                intento = true
                if (valido) onRegistrar(usuario.trim(), password, rol, patente, codigo)
            },
            enabled = !estado.cargando,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (estado.cargando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Crear cuenta", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}
