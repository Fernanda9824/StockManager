package com.example.stockmanager.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "movimientos",
    indices = [
        // Facilita las búsquedas por código de producto.
        Index(value = ["codigo_producto"]),

        // Facilita la consulta de movimientos pendientes.
        Index(value = ["sincronizado", "fecha_hora"]),

        // Facilita la consulta de movimientos activos por fecha.
        Index(value = ["eliminado", "fecha_hora"]),

        // Evita duplicar el identificador único del cliente.
        Index(value = ["client_id"], unique = true),

        // Optimiza las consultas por almacén, producto y estado.
        Index(
            value = [
                "almacen_nombre",
                "codigo_producto",
                "eliminado"
            ]
        )
    ]
)
data class Movimiento(

    // Identificador único generado por la base de datos local.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Identificador estable para una futura sincronización.
    @ColumnInfo(name = "client_id")
    val clientId: String = UUID.randomUUID().toString(),

    // Código que identifica el producto.
    @ColumnInfo(name = "codigo_producto")
    val codigoProducto: String,

    // Almacén donde se registra el movimiento.
    // Se establece un valor predeterminado para los registros anteriores.
    @ColumnInfo(
        name = "almacen_nombre",
        defaultValue = "'Almacén principal'"
    )
    val almacenNombre: String = "Almacén principal",

    // Cantidad de unidades registradas.
    val cantidad: Int,

    // Tipo de operación: ENTRADA o SALIDA.
    @ColumnInfo(name = "tipo_movimiento")
    val tipoMovimiento: String,

    // Fecha y hora del registro en milisegundos.
    @ColumnInfo(name = "fecha_hora")
    val fechaHora: Long = System.currentTimeMillis(),

    // Indica si el movimiento ya se sincronizó con el servidor.
    val sincronizado: Boolean = false,

    // Indica si el registro fue eliminado lógicamente.
    val eliminado: Boolean = false,

    // Operación pendiente: CREATE, UPDATE o DELETE.
    val operacion: String = "CREATE"
)