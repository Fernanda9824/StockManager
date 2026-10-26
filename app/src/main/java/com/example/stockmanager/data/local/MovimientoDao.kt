package com.example.stockmanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoDao {

    // ==========================================
    // CREATE: registrar una entrada o salida
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(movimiento: Movimiento): Long


    // ==========================================
    // READ: consultar el historial
    // ==========================================

    @Query("""
        SELECT * FROM movimientos
        WHERE eliminado = 0
        ORDER BY fecha_hora DESC, id DESC
    """)
    fun observarActivos(): Flow<List<Movimiento>>


    // READ: buscar un movimiento por su ID.
    @Query("""
        SELECT * FROM movimientos
        WHERE id = :id
        AND eliminado = 0
        LIMIT 1
    """)
    suspend fun obtenerPorId(id: Long): Movimiento?


    // READ: buscar movimientos por código de producto.
    @Query("""
        SELECT * FROM movimientos
        WHERE codigo_producto COLLATE NOCASE = :codigo
        AND eliminado = 0
        ORDER BY fecha_hora DESC, id DESC
    """)
    fun buscarPorCodigo(codigo: String): Flow<List<Movimiento>>


    // READ: obtener el movimiento activo más reciente.
    @Query("""
        SELECT * FROM movimientos
        WHERE eliminado = 0
        ORDER BY fecha_hora DESC, id DESC
        LIMIT 1
    """)
    suspend fun obtenerUltimoActivo(): Movimiento?


    // ==========================================
    // READ: calcular las existencias por producto
    // y almacén
    // ==========================================

    // Las entradas suman unidades.
    // Las salidas restan unidades.
    // Los movimientos eliminados no cuentan.
    @Query("""
        SELECT COALESCE(
            SUM(
                CASE
                    WHEN tipo_movimiento = 'ENTRADA'
                        THEN cantidad
                    WHEN tipo_movimiento = 'SALIDA'
                        THEN -cantidad
                    ELSE 0
                END
            ),
            0
        )
        FROM movimientos
        WHERE codigo_producto COLLATE NOCASE = :codigo
        AND almacen_nombre COLLATE NOCASE = :almacen
        AND eliminado = 0
    """)
    suspend fun obtenerExistencia(
        codigo: String,
        almacen: String
    ): Int


    // ==========================================
    // UPDATE: modificar todos los datos editables
    // ==========================================

    @Query("""
        UPDATE movimientos
        SET almacen_nombre = :almacen,
            codigo_producto = :codigo,
            cantidad = :cantidad,
            tipo_movimiento = :tipo,
            operacion = CASE
                WHEN sincronizado = 0 AND operacion = 'CREATE'
                    THEN 'CREATE'
                ELSE 'UPDATE'
            END,
            sincronizado = 0
        WHERE id = :id
        AND eliminado = 0
    """)
    suspend fun actualizarDatos(
        id: Long,
        almacen: String,
        codigo: String,
        cantidad: Int,
        tipo: String
    ): Int


    // ==========================================
    // DELETE lógico: ocultar un movimiento
    // ==========================================

    @Query("""
        UPDATE movimientos
        SET eliminado = 1,
            sincronizado = 0,
            operacion = 'DELETE'
        WHERE id = :id
        AND eliminado = 0
    """)
    suspend fun eliminarLogico(id: Long): Int


    // ==========================================
    // READ: consultar movimientos pendientes
    // ==========================================

    @Query("""
        SELECT * FROM movimientos
        WHERE sincronizado = 0
        ORDER BY fecha_hora ASC, id ASC
    """)
    suspend fun obtenerPendientes(): List<Movimiento>


    // UPDATE: marcar un movimiento como sincronizado.
    @Query("""
        UPDATE movimientos
        SET sincronizado = 1
        WHERE id = :id
    """)
    suspend fun marcarSincronizado(id: Long): Int


    // READ: contar movimientos pendientes.
    @Query("""
        SELECT COUNT(*) FROM movimientos
        WHERE sincronizado = 0
    """)
    fun contarPendientes(): Flow<Int>


    // DELETE físico: eliminar definitivamente un registro.
    @Delete
    suspend fun eliminarFisicamente(
        movimiento: Movimiento
    ): Int
}