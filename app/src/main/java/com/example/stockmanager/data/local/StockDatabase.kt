package com.example.stockmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Movimiento::class],
    version = 3,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {

    abstract fun movimientoDao(): MovimientoDao

    companion object {

        @Volatile
        private var INSTANCIA: StockDatabase? = null

        // Migración de la versión 1 a la 2.
        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_movimientos_eliminado_fecha_hora`
                    ON `movimientos` (`eliminado`, `fecha_hora`)
                    """.trimIndent()
                )
            }
        }

        // Migración de la versión 2 a la 3.
        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL(
                    """
                    ALTER TABLE `movimientos`
                    ADD COLUMN `almacen_nombre` TEXT NOT NULL
                    DEFAULT 'Almacén principal'
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_movimientos_almacen_nombre_codigo_producto_eliminado`
                    ON `movimientos`
                    (`almacen_nombre`, `codigo_producto`, `eliminado`)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): StockDatabase {

            return INSTANCIA ?: synchronized(this) {

                INSTANCIA ?: Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stockmanager.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3
                    )
                    .build()
                    .also { baseDatos ->
                        INSTANCIA = baseDatos
                    }
            }
        }
    }
}