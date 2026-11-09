package cl.inacap.estacionamiento

import android.app.Application

/** Punto de arranque de la app: crea la base de datos SQLite y los repositorios. */
class SmartParkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.iniciar(this)
    }
}
