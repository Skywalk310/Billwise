package `in`.billwise.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class InvoiceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INVOICE_ID = "invoice_id"
    }

    private var invoice: Invoice? = null
    private var lines: List<InvoiceLine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_detail)
        val id = intent.getLongExtra(EXTRA_INVOICE_ID, -1)
        if (id <= 0) {
            finish()
            return
        }
        load(id)

        // Single action set (v1.0.4): all actions up top, once only.
        findViewById<MaterialButton>(R.id.btn_top_share).setOnClickListener { sharePdf() }
        findViewById<MaterialButton>(R.id.btn_top_save).setOnClickListener { savePdfToDownloads() }
        findViewById<MaterialButton>(R.id.btn_top_edit).setOnClickListener {
            val intent = Intent(this, InvoiceEditActivity::class.java)
            intent.putExtra(InvoiceEditActivity.EXTRA_INVOICE_ID, id)
            startActivity(intent)
        }
        findViewById<MaterialButton>(R.id.btn_top_duplicate).setOnClickListener {
            val intent = Intent(this, InvoiceEditActivity::class.java)
            intent.putExtra(InvoiceEditActivity.EXTRA_DUPLICATE_ID, id)
            startActivity(intent)
        }
        findViewById<MaterialButton>(R.id.btn_top_toggle_status).setOnClickListener { toggleStatus() }
        findViewById<MaterialButton>(R.id.btn_top_delete).setOnClickListener { confirmDelete() }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete invoice?")
            .setMessage(invoice?.number ?: "")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val dao = AppDatabase.get(this@InvoiceDetailActivity).dao()
                    val inv = invoice ?: return@launch
                    dao.deleteLines(inv.id)
                    dao.deleteInvoice(inv)
                    Toast.makeText(this@InvoiceDetailActivity, "Invoice deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val id = intent.getLongExtra(EXTRA_INVOICE_ID, -1)
        if (id > 0) load(id)
    }

    private fun load(id: Long) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InvoiceDetailActivity).dao()
            val inv = dao.getInvoice(id) ?: return@launch
            invoice = inv
            lines = dao.getLines(id)
            render()
        }
    }

    private fun render() {
        val inv = invoice ?: return
        findViewById<TextView>(R.id.tv_d_number).text = inv.number
        val statusTv = findViewById<TextView>(R.id.tv_d_status)
        statusTv.text = inv.status
        statusTv.setTextColor(
            ContextCompat.getColor(
                this,
                if (inv.status == "PAID") R.color.paid_green else R.color.unpaid_red
            )
        )
        findViewById<TextView>(R.id.tv_d_client).text = "Bill To: " + inv.clientName
        findViewById<TextView>(R.id.tv_d_phone).text = inv.clientPhone
        findViewById<TextView>(R.id.tv_d_address).text = inv.clientAddress
        findViewById<TextView>(R.id.tv_d_date).text = "Date: " + fmtDate(inv.dateMillis)
        findViewById<TextView>(R.id.tv_d_due).text =
            if (inv.dueDateMillis != null) "Due: " + fmtDate(inv.dueDateMillis) else ""
        // Tabular bill: #, Item, Qty, Rate, Amount + totals rows.
        val table = findViewById<TableLayout>(R.id.table_bill)
        table.removeAllViews()
        fun cell(text: String, bold: Boolean, alignRight: Boolean, serif: Boolean, sizeSp: Float): TextView {
            return TextView(this).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(this@InvoiceDetailActivity, R.color.ink))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                if (serif) setTypeface(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                textSize = sizeSp
                gravity = if (alignRight) Gravity.END else Gravity.START
                setPadding(4, 6, 4, 6)
            }
        }
        fun addRow(cells: List<TextView>, topRule: Boolean = false) {
            if (topRule) {
                val rule = View(this).apply {
                    layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(ContextCompat.getColor(this@InvoiceDetailActivity, R.color.gold))
                }
                val ruleRow = TableRow(this)
                ruleRow.addView(rule, TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1).apply { span = 5 })
                table.addView(ruleRow)
            }
            val row = TableRow(this)
            for ((i, c) in cells.withIndex()) {
                val lp = TableRow.LayoutParams(
                    if (i == 1) TableRow.LayoutParams.MATCH_PARENT else TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
                if (i == 1) lp.weight = 1f
                row.addView(c, lp)
            }
            table.addView(row)
        }
        addRow(
            listOf(
                cell("#", true, false, false, 12f),
                cell("Item", true, false, false, 12f),
                cell("Qty", true, true, false, 12f),
                cell("Rate", true, true, false, 12f),
                cell("Amount", true, true, false, 12f)
            ),
            topRule = true
        )
        for ((idx, line) in lines.withIndex()) {
            addRow(
                listOf(
                    cell((idx + 1).toString(), false, false, false, 14f),
                    cell(line.itemName, false, false, false, 14f),
                    cell(if (line.qty == line.qty.toLong().toDouble()) line.qty.toLong().toString() else line.qty.toString(), false, true, false, 14f),
                    cell(fmtMoney(line.rate), false, true, true, 14f),
                    cell(fmtMoney(line.amount), false, true, true, 14f)
                )
            )
        }
        val totals = computeTotals(inv.subtotal, inv.discount, inv.gstEnabled, inv.gstRate)
        fun totalRow(label: String, value: String, bold: Boolean, sizeSp: Float) {
            val row = TableRow(this)
            val labelView = cell(label, bold, true, bold, sizeSp)
            val valueView = cell(value, bold, true, true, sizeSp)
            val spacer = View(this)
            row.addView(spacer, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT).apply { span = 3 })
            row.addView(labelView, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT))
            row.addView(valueView, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT))
            table.addView(row)
        }
        totalRow("Subtotal", fmtMoney(totals.subtotal), false, 14f)
        if (totals.discount > 0) {
            totalRow("Discount", "-" + fmtMoney(totals.discount), false, 14f)
        }
        if (inv.gstEnabled && totals.gstAmount > 0) {
            val half = inv.gstRate / 2.0
            val halfText = if (half == half.toLong().toDouble()) half.toLong().toString() else half.toString()
            totalRow("CGST ($halfText%)", fmtMoney(totals.cgst), false, 14f)
            totalRow("SGST ($halfText%)", fmtMoney(totals.sgst), false, 14f)
        }
        totalRow("Total", fmtMoney(totals.total), true, 18f)

        val remarksTv = findViewById<TextView>(R.id.tv_d_remarks)
        if (inv.remarks.isNotBlank()) {
            remarksTv.visibility = View.VISIBLE
            remarksTv.text = "Remarks: " + inv.remarks
        } else {
            remarksTv.visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.btn_top_toggle_status).text =
            if (inv.status == "PAID") "Mark as Unpaid" else "Mark as Paid"
    }

    private var profileCache: BusinessProfile? = null
    private var clientCache: Client? = null

    private suspend fun loadClientForPdf(inv: Invoice): Client? {
        if (inv.clientId == null) return null
        if (clientCache?.id == inv.clientId) return clientCache
        val all = AppDatabase.get(this@InvoiceDetailActivity).dao().getClientsOnce()
        clientCache = all.firstOrNull { it.id == inv.clientId }
        return clientCache
    }

    private fun sharePdf() {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InvoiceDetailActivity).dao()
            profileCache = dao.getProfile()
            val inv = invoice ?: return@launch
            val client = loadClientForPdf(inv)
            val file = try {
                PdfGenerator.generate(this@InvoiceDetailActivity, profileCache ?: BusinessProfile(), inv, lines, client)
            } catch (e: Exception) {
                Toast.makeText(this@InvoiceDetailActivity, "PDF failed: " + e.message, Toast.LENGTH_LONG).show()
                return@launch
            }
            val uri: Uri = FileProvider.getUriForFile(
                this@InvoiceDetailActivity,
                "in.billwise.app.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice " + inv.number)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share invoice"))
        }
    }

    private fun savePdfToDownloads() {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InvoiceDetailActivity).dao()
            profileCache = dao.getProfile()
            val inv = invoice ?: return@launch
            val client = loadClientForPdf(inv)
            val file = try {
                PdfGenerator.generate(this@InvoiceDetailActivity, profileCache ?: BusinessProfile(), inv, lines, client)
            } catch (e: Exception) {
                Toast.makeText(this@InvoiceDetailActivity, "PDF failed: " + e.message, Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                val fileName = inv.number + ".pdf"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(file).use { input -> input.copyTo(out) }
                        }
                        Toast.makeText(this@InvoiceDetailActivity, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@InvoiceDetailActivity, "Could not save", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloads.exists()) downloads.mkdirs()
                    val dest = File(downloads, fileName)
                    FileInputStream(file).use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    Toast.makeText(this@InvoiceDetailActivity, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InvoiceDetailActivity, "Save failed: " + e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleStatus() {
        val inv = invoice ?: return
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InvoiceDetailActivity).dao()
            val updated = inv.copy(status = if (inv.status == "PAID") "UNPAID" else "PAID")
            dao.updateInvoice(updated)
            invoice = updated
            render()
        }
    }
}
