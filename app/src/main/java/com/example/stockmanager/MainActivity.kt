package com.example.stockmanager

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockmanager.data.local.Movimiento
import com.example.stockmanager.data.local.StockDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ==========================================
        // 1. COMPONENTES DE LA INTERFAZ
        // ==========================================

        val etAlmacen = findViewById<EditText>(R.id.etAlmacen)
        val etCodigo = findViewById<EditText>(R.id.etCodigo)
        val etCantidad = findViewById<EditText>(R.id.etCantidad)

        val btnEntrada = findViewById<Button>(R.id.btnEntrada)
        val btnSalida = findViewById<Button>(R.id.btnSalida)
        val btnActualizar = findViewById<Button>(R.id.btnActualizar)
        val btnEliminar = findViewById<Button>(R.id.btnEliminar)

        val btnConsultarExistencias =
            findViewById<Button>(R.id.btnConsultarExistencias)

        val tvExistencia = findViewById<TextView>(R.id.tvExistencia)
        val tvEstado = findViewById<TextView>(R.id.tvEstado)
        val tvPendientes = findViewById<TextView>(R.id.tvPendientes)
        val tvMovimientos = findViewById<TextView>(R.id.tvMovimientos)

        // ==========================================
        // 2. BASE DE DATOS
        // ==========================================

        val dao = StockDatabase
            .getInstance(applicationContext)
            .movimientoDao()

        // Normalizar el texto del almacén.
        fun normalizarAlmacen(texto: String): String {
            return texto.trim().replace(Regex("\\s+"), " ")
        }

        // Mostrar las existencias de un producto.
        suspend fun actualizarVistaExistencias(
            codigo: String,
            almacen: String
        ) {
            val existencia = dao.obtenerExistencia(
                codigo = codigo,
                almacen = almacen
            )

            tvExistencia.text =
                "EXISTENCIAS DISPONIBLES\n" +
                        "Producto: $codigo\n" +
                        "Almacén: $almacen\n" +
                        "Unidades disponibles: $existencia"
        }

        // ==========================================
        // 3. HISTORIAL AUTOMÁTICO
        // ==========================================

        lifecycleScope.launch {
            dao.observarActivos().collect { movimientos ->

                if (movimientos.isEmpty()) {
                    tvMovimientos.text =
                        "Todavía no hay movimientos registrados."
                } else {
                    val formatoFecha = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    )

                    tvMovimientos.text = movimientos.joinToString(
                        "\n\n────────────────\n\n"
                    ) { movimiento ->

                        val fecha = formatoFecha.format(
                            Date(movimiento.fechaHora)
                        )

                        val titulo = when (movimiento.tipoMovimiento) {
                            "ENTRADA" -> "+ ENTRADA"
                            "SALIDA" -> "− SALIDA"
                            else -> movimiento.tipoMovimiento
                        }

                        "$titulo\n" +
                                "ID: ${movimiento.id}\n" +
                                "Almacén: ${movimiento.almacenNombre}\n" +
                                "Producto: ${movimiento.codigoProducto}\n" +
                                "Cantidad: ${movimiento.cantidad} unidades\n" +
                                "Fecha: $fecha"
                    }
                }
            }
        }

        // ==========================================
        // 4. CONTADOR DE MOVIMIENTOS PENDIENTES
        // ==========================================

        lifecycleScope.launch {
            dao.contarPendientes().collect { cantidad ->
                tvPendientes.text =
                    "Movimientos pendientes de sincronización: $cantidad"
            }
        }

        // ==========================================
        // 5. REGISTRAR ENTRADAS Y SALIDAS
        // ==========================================

        fun registrarMovimiento(tipo: String) {

            val almacen = normalizarAlmacen(
                etAlmacen.text.toString()
            )

            val codigo = etCodigo.text
                .toString()
                .trim()
                .uppercase(Locale.ROOT)

            val cantidad = etCantidad.text
                .toString()
                .toIntOrNull()

            if (almacen.isBlank()) {
                tvEstado.text = "Escribe el nombre del almacén."
                etAlmacen.requestFocus()
                return
            }

            if (codigo.isBlank()) {
                tvEstado.text = "Escribe el código del producto."
                etCodigo.requestFocus()
                return
            }

            if (cantidad == null || cantidad <= 0) {
                tvEstado.text =
                    "La cantidad debe ser mayor que cero."
                etCantidad.requestFocus()
                return
            }

            lifecycleScope.launch {
                try {
                    // Validar existencias antes de una salida.
                    if (tipo == "SALIDA") {
                        val disponibles = dao.obtenerExistencia(
                            codigo = codigo,
                            almacen = almacen
                        )

                        if (cantidad > disponibles) {

                            // Mostrar el error en la pantalla principal.
                            tvEstado.text =
                                "Stock insuficiente. Solo hay " +
                                        "$disponibles unidades disponibles."

                            // Mostrar una advertencia emergente.
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("⚠️ Existencias insuficientes")
                                .setMessage(
                                    "No puedes retirar $cantidad unidades del producto $codigo.\n\n" +
                                            "Almacén: $almacen\n" +
                                            "Cantidad solicitada: $cantidad\n" +
                                            "Cantidad disponible: $disponibles\n\n" +
                                            "Reduce la cantidad solicitada para continuar."
                                )
                                .setPositiveButton("Entendido", null)
                                .show()

                            // Cancelar el registro de la salida.
                            return@launch
                        }
                    }

                    // Crear el movimiento en Room.
                    val movimiento = Movimiento(
                        almacenNombre = almacen,
                        codigoProducto = codigo,
                        cantidad = cantidad,
                        tipoMovimiento = tipo
                    )

                    val id = dao.insertar(movimiento)

                    // Consultar el saldo después del registro.
                    actualizarVistaExistencias(codigo, almacen)

                    tvEstado.text =
                        "$tipo registrada correctamente.\n" +
                                "Producto: $codigo\n" +
                                "Almacén: $almacen\n" +
                                "Cantidad: $cantidad\n" +
                                "ID local: $id"

                    etCodigo.text.clear()
                    etCantidad.text.clear()

                } catch (e: Exception) {
                    tvEstado.text =
                        "Error al registrar: " +
                                (e.localizedMessage ?: "Error desconocido")
                }
            }
        }

        btnEntrada.setOnClickListener {
            registrarMovimiento("ENTRADA")
        }

        btnSalida.setOnClickListener {
            registrarMovimiento("SALIDA")
        }

        // ==========================================
        // 6. CONSULTAR EXISTENCIAS
        // ==========================================

        btnConsultarExistencias.setOnClickListener {

            val almacen = normalizarAlmacen(
                etAlmacen.text.toString()
            )

            val codigo = etCodigo.text
                .toString()
                .trim()
                .uppercase(Locale.ROOT)

            if (almacen.isBlank()) {
                tvEstado.text = "Escribe el nombre del almacén."
                etAlmacen.requestFocus()
                return@setOnClickListener
            }

            if (codigo.isBlank()) {
                tvEstado.text = "Escribe el código del producto."
                etCodigo.requestFocus()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    actualizarVistaExistencias(codigo, almacen)
                    tvEstado.text = "Consulta realizada correctamente."
                } catch (e: Exception) {
                    tvEstado.text =
                        "Error al consultar: " +
                                (e.localizedMessage ?: "Error desconocido")
                }
            }
        }

        // ==========================================
        // 7. ACTUALIZAR UN MOVIMIENTO SELECCIONADO
        // ==========================================

        btnActualizar.setOnClickListener {

            lifecycleScope.launch {
                try {
                    val movimientos = dao.observarActivos().first()

                    if (movimientos.isEmpty()) {
                        tvEstado.text =
                            "No hay movimientos disponibles para editar."
                        return@launch
                    }

                    val formatoFecha = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    )

                    val opciones = movimientos.map { movimiento ->
                        val fecha = formatoFecha.format(
                            Date(movimiento.fechaHora)
                        )

                        "${movimiento.tipoMovimiento} | " +
                                "${movimiento.codigoProducto} | " +
                                "${movimiento.cantidad} unidades | " +
                                "${movimiento.almacenNombre} | " +
                                "$fecha | ID: ${movimiento.id}"
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Seleccionar movimiento")
                        .setItems(opciones) { _, posicion ->

                            val seleccionado = movimientos[posicion]

                            val contenedor =
                                LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL

                                    val margen = (
                                            20 * resources.displayMetrics.density
                                            ).toInt()

                                    setPadding(margen, 0, margen, 0)
                                }

                            fun agregarCampo(
                                etiqueta: String,
                                valor: String,
                                tipoEntrada: Int
                            ): EditText {
                                val campo = EditText(this@MainActivity).apply {
                                    hint = etiqueta
                                    inputType = tipoEntrada
                                    setText(valor)

                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                }

                                contenedor.addView(campo)
                                return campo
                            }

                            val etAlmacenEditar = agregarCampo(
                                "Nombre del almacén",
                                seleccionado.almacenNombre,
                                InputType.TYPE_CLASS_TEXT or
                                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
                            )

                            val etCodigoEditar = agregarCampo(
                                "Código del producto",
                                seleccionado.codigoProducto,
                                InputType.TYPE_CLASS_TEXT or
                                        InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                            )

                            val etCantidadEditar = agregarCampo(
                                "Cantidad",
                                seleccionado.cantidad.toString(),
                                InputType.TYPE_CLASS_NUMBER
                            )

                            val grupoTipo =
                                RadioGroup(this@MainActivity).apply {
                                    orientation = RadioGroup.HORIZONTAL
                                }

                            val rbEntrada =
                                RadioButton(this@MainActivity).apply {
                                    id = View.generateViewId()
                                    text = "Entrada"
                                    isChecked =
                                        seleccionado.tipoMovimiento == "ENTRADA"
                                }

                            val rbSalida =
                                RadioButton(this@MainActivity).apply {
                                    id = View.generateViewId()
                                    text = "Salida"
                                    isChecked =
                                        seleccionado.tipoMovimiento == "SALIDA"
                                }

                            grupoTipo.addView(rbEntrada)
                            grupoTipo.addView(rbSalida)
                            contenedor.addView(grupoTipo)

                            val dialogo =
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Editar movimiento")
                                    .setMessage(
                                        "Modifica los datos del registro."
                                    )
                                    .setView(contenedor)
                                    .setNegativeButton("Cancelar", null)
                                    .setPositiveButton("Guardar", null)
                                    .create()

                            dialogo.setOnShowListener {
                                dialogo.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                                ).setOnClickListener guardarClick@{

                                    val almacenNuevo = normalizarAlmacen(
                                        etAlmacenEditar.text.toString()
                                    )

                                    val codigoNuevo = etCodigoEditar.text
                                        .toString()
                                        .trim()
                                        .uppercase(Locale.ROOT)

                                    val cantidadNueva =
                                        etCantidadEditar.text
                                            .toString()
                                            .toIntOrNull()

                                    val tipoNuevo = when {
                                        rbEntrada.isChecked -> "ENTRADA"
                                        rbSalida.isChecked -> "SALIDA"
                                        else -> ""
                                    }

                                    if (almacenNuevo.isBlank()) {
                                        etAlmacenEditar.error =
                                            "Escribe el nombre del almacén."
                                        return@guardarClick
                                    }

                                    if (codigoNuevo.isBlank()) {
                                        etCodigoEditar.error =
                                            "Escribe el código del producto."
                                        return@guardarClick
                                    }

                                    if (
                                        cantidadNueva == null ||
                                        cantidadNueva <= 0
                                    ) {
                                        etCantidadEditar.error =
                                            "La cantidad debe ser mayor que cero."
                                        return@guardarClick
                                    }

                                    lifecycleScope.launch {
                                        try {
                                            val actual = dao.obtenerPorId(
                                                seleccionado.id
                                            )

                                            if (actual == null) {
                                                tvEstado.text =
                                                    "El movimiento ya no está disponible."
                                                dialogo.dismiss()
                                                return@launch
                                            }

                                            // Calcular el saldo sin el registro
                                            // que estamos editando.
                                            val saldoOriginal =
                                                dao.obtenerExistencia(
                                                    actual.codigoProducto,
                                                    actual.almacenNombre
                                                ).toLong()

                                            val aporteOriginal =
                                                if (actual.tipoMovimiento == "ENTRADA") {
                                                    actual.cantidad.toLong()
                                                } else {
                                                    -actual.cantidad.toLong()
                                                }

                                            val saldoSinMovimiento =
                                                saldoOriginal - aporteOriginal

                                            val mismaUbicacion =
                                                actual.codigoProducto.equals(
                                                    codigoNuevo,
                                                    ignoreCase = true
                                                ) &&
                                                        actual.almacenNombre.equals(
                                                            almacenNuevo,
                                                            ignoreCase = true
                                                        )

                                            val saldoDestino =
                                                if (mismaUbicacion) {
                                                    saldoSinMovimiento
                                                } else {
                                                    dao.obtenerExistencia(
                                                        codigoNuevo,
                                                        almacenNuevo
                                                    ).toLong()
                                                }

                                            val nuevoAporte =
                                                if (tipoNuevo == "ENTRADA") {
                                                    cantidadNueva.toLong()
                                                } else {
                                                    -cantidadNueva.toLong()
                                                }

                                            val saldoFinal =
                                                saldoDestino + nuevoAporte

                                            val origenQuedariaNegativo =
                                                !mismaUbicacion &&
                                                        saldoSinMovimiento < 0L

                                            if (
                                                origenQuedariaNegativo ||
                                                saldoFinal < 0L
                                            ) {
                                                etCantidadEditar.error =
                                                    "La modificación deja existencias negativas."

                                                tvEstado.text =
                                                    "Revisa las existencias del producto y almacén."
                                                return@launch
                                            }

                                            val filas = dao.actualizarDatos(
                                                id = actual.id,
                                                almacen = almacenNuevo,
                                                codigo = codigoNuevo,
                                                cantidad = cantidadNueva,
                                                tipo = tipoNuevo
                                            )

                                            if (filas > 0) {
                                                actualizarVistaExistencias(
                                                    codigoNuevo,
                                                    almacenNuevo
                                                )

                                                tvEstado.text =
                                                    "Movimiento actualizado.\n" +
                                                            "ID: ${actual.id}\n" +
                                                            "Producto: $codigoNuevo\n" +
                                                            "Almacén: $almacenNuevo\n" +
                                                            "Cantidad: $cantidadNueva\n" +
                                                            "Tipo: $tipoNuevo"

                                                dialogo.dismiss()
                                            } else {
                                                tvEstado.text =
                                                    "No se encontró el movimiento."
                                            }

                                        } catch (e: Exception) {
                                            tvEstado.text =
                                                "Error al actualizar: " +
                                                        (e.localizedMessage
                                                            ?: "Error desconocido")
                                        }
                                    }
                                }
                            }

                            dialogo.show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()

                } catch (e: Exception) {
                    tvEstado.text =
                        "Error al consultar movimientos: " +
                                (e.localizedMessage ?: "Error desconocido")
                }
            }
        }

        // ==========================================
        // 8. ELIMINAR UN MOVIMIENTO SELECCIONADO
        // ==========================================

        btnEliminar.setOnClickListener {

            lifecycleScope.launch {
                try {
                    val movimientos = dao.observarActivos().first()

                    if (movimientos.isEmpty()) {
                        tvEstado.text =
                            "No hay movimientos disponibles para eliminar."
                        return@launch
                    }

                    val formatoFecha = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    )

                    val opciones = movimientos.map { movimiento ->
                        val fecha = formatoFecha.format(
                            Date(movimiento.fechaHora)
                        )

                        "${movimiento.tipoMovimiento} | " +
                                "${movimiento.codigoProducto} | " +
                                "${movimiento.cantidad} unidades | " +
                                "${movimiento.almacenNombre} | " +
                                "$fecha | ID: ${movimiento.id}"
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Seleccionar movimiento")
                        .setItems(opciones) { _, posicion ->

                            val seleccionado = movimientos[posicion]

                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Confirmar eliminación")
                                .setMessage(
                                    "¿Deseas eliminar este movimiento?\n\n" +
                                            "Tipo: ${seleccionado.tipoMovimiento}\n" +
                                            "Producto: ${seleccionado.codigoProducto}\n" +
                                            "Cantidad: ${seleccionado.cantidad}\n" +
                                            "Almacén: ${seleccionado.almacenNombre}\n" +
                                            "ID: ${seleccionado.id}"
                                )
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Eliminar") { _, _ ->

                                    lifecycleScope.launch {
                                        try {
                                            if (
                                                seleccionado.tipoMovimiento ==
                                                "ENTRADA"
                                            ) {
                                                val existencia =
                                                    dao.obtenerExistencia(
                                                        seleccionado.codigoProducto,
                                                        seleccionado.almacenNombre
                                                    )

                                                if (
                                                    existencia <
                                                    seleccionado.cantidad
                                                ) {
                                                    tvEstado.text =
                                                        "No se puede eliminar esta entrada: " +
                                                                "hay salidas que dependen de ella."
                                                    return@launch
                                                }
                                            }

                                            val filas = dao.eliminarLogico(
                                                seleccionado.id
                                            )

                                            if (filas > 0) {
                                                actualizarVistaExistencias(
                                                    seleccionado.codigoProducto,
                                                    seleccionado.almacenNombre
                                                )

                                                tvEstado.text =
                                                    "Movimiento eliminado de la lista.\n" +
                                                            "ID: ${seleccionado.id}\n" +
                                                            "Pendiente de sincronización."
                                            } else {
                                                tvEstado.text =
                                                    "El movimiento ya no está disponible."
                                            }

                                        } catch (e: Exception) {
                                            tvEstado.text =
                                                "Error al eliminar: " +
                                                        (e.localizedMessage
                                                            ?: "Error desconocido")
                                        }
                                    }
                                }
                                .show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()

                } catch (e: Exception) {
                    tvEstado.text =
                        "Error al consultar movimientos: " +
                                (e.localizedMessage ?: "Error desconocido")
                }
            }
        }
    }
}