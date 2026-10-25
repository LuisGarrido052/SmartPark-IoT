package cl.inacap.estacionamiento

import android.content.Context
import cl.inacap.estacionamiento.data.local.SmartParkDb
import cl.inacap.estacionamiento.data.repository.AuthRepository
import cl.inacap.estacionamiento.data.repository.ParkingRepository
import cl.inacap.estacionamiento.security.SessionManager

/** Inyección de dependencias manual (sin Hilt/Dagger para mantener el proyecto simple y compatible). */
object ServiceLocator {
    @Volatile private var iniciado = false
    lateinit var auth: AuthRepository
        private set
    lateinit var parking: ParkingRepository
        private set

    fun iniciar(context: Context) {
        if (iniciado) return
        synchronized(this) {
            if (iniciado) return
            val app = context.applicationContext
            val db = SmartParkDb(app)
            auth = AuthRepository(db, SessionManager(app))
            parking = ParkingRepository(db)
            iniciado = true
        }
    }
}
