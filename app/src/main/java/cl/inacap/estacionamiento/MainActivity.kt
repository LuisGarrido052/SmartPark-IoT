package cl.inacap.estacionamiento

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import cl.inacap.estacionamiento.ui.navigation.SmartParkNavHost
import cl.inacap.estacionamiento.ui.theme.SmartParkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.iniciar(this)
        setContent {
            SmartParkTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SmartParkNavHost()
                }
            }
        }
    }
}
