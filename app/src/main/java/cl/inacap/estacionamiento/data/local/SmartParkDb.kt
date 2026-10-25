package cl.inacap.estacionamiento.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cl.inacap.estacionamiento.data.model.EstadoPlaza
import cl.inacap.estacionamiento.data.model.Evento
import cl.inacap.estacionamiento.data.model.Plaza
import cl.inacap.estacionamiento.data.model.Rol
import cl.inacap.estacionamiento.security.PasswordHasher

/**
 * Base de datos local SQLite (sin Room, para máxima compatibilidad).
 * Tablas:
 *  - usuarios: credenciales (hash + sal), rol, patente e intentos fallidos.
 *  - plazas:   último estado conocido de cada plaza.
 *  - eventos:  log de auditoría (sensores, reservas, barreras, seguridad).
 */
class SmartParkDb(context: Context) : SQLiteOpenHelper(context, NOMBRE, null, VERSION) {

    data class FilaUsuario(
        val id: Long,
        val usuario: String,
        val hash: String,
        val sal: String,
        val rol: Rol,
        val patente: String,
        val intentosFallidos: Int,
        val bloqueadoHasta: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE usuarios(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario TEXT NOT NULL UNIQUE COLLATE NOCASE,
                hash TEXT NOT NULL,
                sal TEXT NOT NULL,
                rol TEXT NOT NULL,
                patente TEXT NOT NULL DEFAULT '',
                intentos_fallidos INTEGER NOT NULL DEFAULT 0,
                bloqueado_hasta INTEGER NOT NULL DEFAULT 0,
                creado INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE plazas(
                id INTEGER PRIMARY KEY,
                piso INTEGER NOT NULL,
                estado TEXT NOT NULL,
                patente TEXT NOT NULL DEFAULT '',
                reserva_hasta INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE eventos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha INTEGER NOT NULL,
                tipo TEXT NOT NULL,
                descripcion TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_eventos_fecha ON eventos(fecha)")

        // Plazas iniciales: 2 niveles x 12 plazas (101-112 y 201-212)
        for (piso in 1..2) for (n in 1..12) {
            db.insert("plazas", null, ContentValues().apply {
                put("id", piso * 100 + n)
                put("piso", piso)
                put("estado", EstadoPlaza.LIBRE.name)
            })
        }

        // Cuentas de demostración (las contraseñas se guardan con hash, nunca en texto plano)
        insertarUsuario(db, "operador", "Operador2026", Rol.OPERADOR, "")
        insertarUsuario(db, "conductor", "Conductor2026", Rol.CONDUCTOR, "ABCD12")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS plazas")
        db.execSQL("DROP TABLE IF EXISTS eventos")
        onCreate(db)
    }

    // ---------- Usuarios ----------

    private fun insertarUsuario(db: SQLiteDatabase, usuario: String, password: String, rol: Rol, patente: String): Long {
        val h = PasswordHasher.generarHash(password.toCharArray())
        return db.insert("usuarios", null, ContentValues().apply {
            put("usuario", usuario)
            put("hash", h.hash)
            put("sal", h.sal)
            put("rol", rol.name)
            put("patente", patente)
            put("creado", System.currentTimeMillis())
        })
    }

    fun crearUsuario(usuario: String, password: CharArray, rol: Rol, patente: String): Long {
        val h = PasswordHasher.generarHash(password)
        return writableDatabase.insert("usuarios", null, ContentValues().apply {
            put("usuario", usuario)
            put("hash", h.hash)
            put("sal", h.sal)
            put("rol", rol.name)
            put("patente", patente)
            put("creado", System.currentTimeMillis())
        })
    }

    fun existeUsuario(usuario: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM usuarios WHERE usuario = ?", arrayOf(usuario)).use { it.moveToFirst() }

    fun buscarUsuario(usuario: String): FilaUsuario? =
        readableDatabase.rawQuery(
            "SELECT id, usuario, hash, sal, rol, patente, intentos_fallidos, bloqueado_hasta FROM usuarios WHERE usuario = ?",
            arrayOf(usuario)
        ).use { c ->
            if (!c.moveToFirst()) null else FilaUsuario(
                id = c.getLong(0),
                usuario = c.getString(1),
                hash = c.getString(2),
                sal = c.getString(3),
                rol = Rol.valueOf(c.getString(4)),
                patente = c.getString(5),
                intentosFallidos = c.getInt(6),
                bloqueadoHasta = c.getLong(7)
            )
        }

    fun actualizarIntentos(id: Long, intentos: Int, bloqueadoHasta: Long) {
        writableDatabase.update("usuarios", ContentValues().apply {
            put("intentos_fallidos", intentos)
            put("bloqueado_hasta", bloqueadoHasta)
        }, "id = ?", arrayOf(id.toString()))
    }

    // ---------- Plazas ----------

    fun leerPlazas(): List<Plaza> =
        readableDatabase.rawQuery("SELECT id, piso, estado, patente, reserva_hasta FROM plazas ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Plaza(
                        id = c.getInt(0),
                        piso = c.getInt(1),
                        estado = EstadoPlaza.valueOf(c.getString(2)),
                        patente = c.getString(3),
                        reservaHasta = c.getLong(4)
                    )
                )
            }
        }

    fun guardarPlazas(plazas: List<Plaza>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            plazas.forEach { p ->
                db.insertWithOnConflict("plazas", null, ContentValues().apply {
                    put("id", p.id)
                    put("piso", p.piso)
                    put("estado", p.estado.name)
                    put("patente", p.patente)
                    put("reserva_hasta", p.reservaHasta)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- Eventos ----------

    fun registrarEvento(tipo: String, descripcion: String, fecha: Long = System.currentTimeMillis()) {
        writableDatabase.insert("eventos", null, ContentValues().apply {
            put("fecha", fecha)
            put("tipo", tipo)
            put("descripcion", descripcion)
        })
        // Se conservan solo los últimos 500 eventos
        writableDatabase.execSQL(
            "DELETE FROM eventos WHERE id NOT IN (SELECT id FROM eventos ORDER BY id DESC LIMIT 500)"
        )
    }

    /** Consulta de eventos, opcionalmente filtrados por tipo. */
    fun leerEventos(tipo: String? = null, limite: Int = 100): List<Evento> {
        val sql = if (tipo == null)
            "SELECT id, fecha, tipo, descripcion FROM eventos ORDER BY id DESC LIMIT $limite"
        else
            "SELECT id, fecha, tipo, descripcion FROM eventos WHERE tipo = ? ORDER BY id DESC LIMIT $limite"
        val args = if (tipo == null) null else arrayOf(tipo)
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(Evento(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)))
            }
        }
    }

    companion object {
        const val NOMBRE = "smartpark.db"
        const val VERSION = 1
    }
}
