package cl.inacap.estacionamiento.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.inacap.estacionamiento.security.Validaciones
import cl.inacap.estacionamiento.ui.theme.TextoSecundario
import cl.inacap.estacionamiento.ui.theme.VerdeLibre
import cl.inacap.estacionamiento.ui.viewmodel.AuthUiState

/**
 * Pantalla de Login (rúbrica 2.1.1.1): ingreso mediante credenciales usuario + contraseña.
 */
@Composable
fun LoginScreen(
    estado: AuthUiState,
    onLogin: (usuario: String, password: String) -> Unit,
    onIrARegistro: () -> Unit
) {
    var usuario by rememberSaveable { mutableStateOf("") }
    // La contraseña no se guarda en el estado persistente (rememberSaveable) por seguridad
    var password by remember { mutableStateOf("") }
    var verPassword by remember { mutableStateOf(false) }
    var intentoEnviar by rememberSaveable { mutableStateOf(false) }
    val foco = LocalFocusManager.current

    val errorUsuario = if (intentoEnviar) Validaciones.errorUsuario(usuario) else null
    val errorPassword = if (intentoEnviar) Validaciones.errorPasswordLogin(password) else null

    fun enviar() {
        intentoEnviar = true
        if (Validaciones.errorUsuario(usuario) == null && Validaciones.errorPasswordLogin(password) == null) {
            foco.clearFocus()
            onLogin(usuario.trim(), password)
            password = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(VerdeLibre.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("P", style = MaterialTheme.typography.displayMedium, color = VerdeLibre, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text("SmartPark IoT", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Sistema de Estacionamiento Inteligente",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it.take(20) },
                    label = { Text("Usuario") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    isError = errorUsuario != null,
                    supportingText = if (errorUsuario != null) { { Text(errorUsuario) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { foco.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { verPassword = !verPassword }) {
                            Text(if (verPassword) "Ocultar" else "Mostrar")
                        }
                    },
                    singleLine = true,
                    isError = errorPassword != null,
                    supportingText = if (errorPassword != null) { { Text(errorPassword) } } else null,
                    visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrect = false),
                    keyboardActions = KeyboardActions(onDone = { enviar() }),
                    modifier = Modifier.fillMaxWidth()
                )

                if (estado.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(estado.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { enviar() },
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (estado.cargando) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Verificando...")
                    } else {
                        Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿No tienes cuenta?", color = TextoSecundario)
            TextButton(onClick = onIrARegistro) { Text("Crear cuenta") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Credenciales protegidas con hash PBKDF2 · Bloqueo tras 5 intentos fallidos · ISO/IEC 27400",
            style = MaterialTheme.typography.labelSmall,
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )
    }
}
