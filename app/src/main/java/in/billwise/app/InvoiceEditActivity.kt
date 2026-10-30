package `in`.billwise.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.util.Calendar

class InvoiceEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INVOICE_ID = "invoice_id"
        const val EXTRA_DUPLICATE_ID = "duplicate_id"
    }

    private data class EditLine(var name: String, var qty: Double, var rate: Double) {
        val amount: Double get() = qty * rate
    }

    private val lines = mutableListOf<EditLine>()
    private lateinit var linesAdapter: EditLineAdapter
    private var clients: List<Client> = emptyList()
    private var items: List<Item> = emptyList()
    private var editInvoiceId: Long = -1
    private var dateMillis: Long = System.currentTimeMillis()
    private var dueDateMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_edit)

        val dao = AppDatabase.get(this).dao()
        val editId = intent.getLongExtra(EXTRA_INVOICE_ID, -1)
        val dupId = intent.getLongExtra(EXTRA_DUPLICATE_ID, -1)

        val clientSpinner = findViewById<Spinner>(R.id.spinner_client)
        val onetimeLayout = findViewById<LinearLayout>(R.id.layout_onetime)
        val itemSpinner = findViewById<Spinner>(R.id.spinner_item_pick)
        val linesRecycler = findViewById<RecyclerView>(R.id.lines_recycler)
        linesRecycler.layoutManager = LinearLayoutManager(this)
        linesAdapter = EditLineAdapter(lines) { recalc() }
        linesRecycler.adapter = linesAdapter

        clientSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                onetimeLayout.visibility = if (pos == 0) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<MaterialButton>(R.id.btn_date).setOnClickListener { pickDate(false) }
        findViewById<CheckBox>(R.id.cb_due).setOnCheckedChangeListener { _, checked ->
            findViewById<LinearLayout>(R.id.layout_due).visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) dueDateMillis = null
        }
        findViewById<MaterialButton>(R.id.btn_due_date).setOnClickListener { pickDate(true) }

        findViewById<MaterialButton>(R.id.btn_add_saved_item).setOnClickListener {
            val pos = itemSpinner.selectedItemPosition
            if (pos >= 0 && pos < items.size) {
                val item = items[pos]
                lines.add(EditLine(item.name, 1.0, item.rate))
                linesAdapter.notifyItemInserted(lines.size - 1)
                recalc()
            } else {
                Toast.makeText(this, "No saved items yet", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btn_add_custom_item).setOnClickListener { showCustomItemDialog() }

        val gstSwitch = findViewById<SwitchMaterial>(R.id.switch_gst)
        val gstGroup = findViewById<RadioGroup>(R.id.radio_gst_rate)
        gstSwitch.setOnCheckedChangeListener { _, checked ->
            gstGroup.visibility = if (checked) View.VISIBLE else View.GONE
            recalc()
        }
        gstGroup.setOnCheckedChangeListener { _, checkedId ->
            findViewById<LinearLayout>(R.id.layout_custom_rate).visibility =
                if (checkedId == R.id.radio_custom) View.VISIBLE else View.GONE
            recalc()
        }
        findViewById<EditText>(R.id.et_gst_custom).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalc() }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<EditText>(R.id.et_discount).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalc() }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<MaterialButton>(R.id.btn_save_invoice).setOnClickListener { saveInvoice() }

        updateDateLabels()

        lifecycleScope.launch {
            clients = dao.getClientsOnce()
            items = dao.getItemsOnce()
            val clientNames = mutableListOf("One-time customer")
            clientNames.addAll(clients.map { it.name })
            clientSpinner.adapter = ArrayAdapter(this@InvoiceEditActivity, android.R.layout.simple_spinner_dropdown_item, clientNames)
            itemSpinner.adapter = ArrayAdapter(this@InvoiceEditActivity, android.R.layout.simple_spinner_dropdown_item, items.map { it.name + " — " + fmtMoney(it.rate) })

            val sourceId = if (editId > 0) editId else if (dupId > 0) dupId else -1
            if (sourceId > 0) {
                val inv = dao.getInvoice(sourceId)
                if (inv != null) {
                    val srcLines = dao.getLines(sourceId)
                    if (editId > 0) {
                        editInvoiceId = editId
                        findViewById<TextView>(R.id.tv_form_title).text = "Edit Invoice"
                    }
                    dateMillis = inv.dateMillis
                    dueDateMillis = inv.dueDateMillis
                    if (dueDateMillis != null) {
                        findViewById<CheckBox>(R.id.cb_due).isChecked = true
                        findViewById<LinearLayout>(R.id.layout_due).visibility = View.VISIBLE
                    }
                    val idx = clients.indexOfFirst { it.id == inv.clientId }
                    if (idx >= 0) {
                        clientSpinner.setSelection(idx + 1)
                    } else {
                        clientSpinner.setSelection(0)
                        findViewById<EditText>(R.id.et_client_name).setText(inv.clientName)
                        findViewById<EditText>(R.id.et_client_phone).setText(inv.clientPhone)
                        findViewById<EditText>(R.id.et_client_address).setText(inv.clientAddress)
                    }
                    lines.clear()
                    lines.addAll(srcLines.map { EditLine(it.itemName, it.qty, it.rate) })
                    linesAdapter.notifyDataSetChanged()
                    findViewById<EditText>(R.id.et_discount).setText(if (inv.discount > 0) inv.discount.toString() else "")
                    gstSwitch.isChecked = inv.gstEnabled
                    gstGroup.visibility = if (inv.gstEnabled) View.VISIBLE else View.GONE
                    val radioId = when (inv.gstRate) {
                        0.0 -> R.id.radio_0
                        5.0 -> R.id.radio_5
                        12.0 -> R.id.radio_12
                        28.0 -> R.id.radio_28
                        18.0 -> R.id.radio_18
                        else -> R.id.radio_custom
                    }
                    gstGroup.check(radioId)
                    if (radioId == R.id.radio_custom) {
                        findViewById<LinearLayout>(R.id.layout_custom_rate).visibility = View.VISIBLE
                        findViewById<EditText>(R.id.et_gst_custom).setText(trimRate(inv.gstRate))
                    }
                    findViewById<EditText>(R.id.et_remarks).setText(inv.remarks)
                    findViewById<CheckBox>(R.id.cb_paid).isChecked = inv.status == "PAID"
                    updateDateLabels()
                    recalc()
                }
            }
        }
    }

    private fun pickDate(forDue: Boolean) {
        val cal = Calendar.getInstance()
        val base = if (forDue) (dueDateMillis ?: System.currentTimeMillis()) else dateMillis
        cal.timeInMillis = base
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val c = Calendar.getInstance()
                c.set(year, month, day, 0, 0, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (forDue) dueDateMillis = c.timeInMillis else dateMillis = c.timeInMillis
                updateDateLabels()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabels() {
        findViewById<TextView>(R.id.tv_date).text = fmtDate(dateMillis)
        findViewById<TextView>(R.id.tv_due_date).text = dueDateMillis?.let { fmtDate(it) } ?: ""
    }

    private fun showCustomItemDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameEt = EditText(this).apply { hint = "Item name" }
        val rateEt = EditText(this).apply {
            hint = "Rate (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(nameEt)
        layout.addView(rateEt)
        AlertDialog.Builder(this)
            .setTitle("Add custom item")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEt.text.toString().trim()
                val rate = rateEt.text.toString().toDoubleOrNull()
                if (name.isEmpty() || rate == null) {
                    Toast.makeText(this, "Name and a valid rate are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lines.add(EditLine(name, 1.0, rate))
                linesAdapter.notifyItemInserted(lines.size - 1)
                recalc()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun gstRate(): Double {
        if (!findViewById<SwitchMaterial>(R.id.switch_gst).isChecked) return 0.0
        return when (findViewById<RadioGroup>(R.id.radio_gst_rate).checkedRadioButtonId) {
            R.id.radio_0 -> 0.0
            R.id.radio_5 -> 5.0
            R.id.radio_12 -> 12.0
            R.id.radio_28 -> 28.0
            R.id.radio_custom -> {
                val raw = findViewById<EditText>(R.id.et_gst_custom).text.toString().toDoubleOrNull() ?: 0.0
                val clamped = raw.coerceIn(0.0, 100.0)
                kotlin.math.round(clamped * 100.0) / 100.0
            }
            else -> 18.0
        }
    }

    private fun recalc() {
        val subtotal = lines.sumOf { it.amount }
        val discount = findViewById<EditText>(R.id.et_discount).text.toString().toDoubleOrNull() ?: 0.0
        val gstEnabled = findViewById<SwitchMaterial>(R.id.switch_gst).isChecked
        val totals = computeTotals(subtotal, discount, gstEnabled, gstRate())
        findViewById<TextView>(R.id.tv_subtotal).text = "Subtotal: " + fmtMoney(totals.subtotal)
        if (totals.discount > 0) {
            findViewById<TextView>(R.id.tv_subtotal).text =
                "Subtotal: " + fmtMoney(totals.subtotal) + "  •  Discount: -" + fmtMoney(totals.discount)
        }
        findViewById<TextView>(R.id.tv_gst).text = if (gstEnabled) {
            "GST (" + trimRate(gstRate()) + "%): " + fmtMoney(totals.gstAmount) +
                "  (CGST " + fmtMoney(totals.cgst) + " + SGST " + fmtMoney(totals.sgst) + ")"
        } else {
            "GST: —"
        }
        findViewById<TextView>(R.id.tv_total).text = "Total: " + fmtMoney(totals.total)
    }

    private fun trimRate(r: Double): String {
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    private fun saveInvoice() {
        val clientSpinner = findViewById<Spinner>(R.id.spinner_client)
        val pos = clientSpinner.selectedItemPosition
        var clientId: Long? = null
        var cName = ""
        var cPhone = ""
        var cAddress = ""
        if (pos == 0) {
            cName = findViewById<EditText>(R.id.et_client_name).text.toString().trim()
            cPhone = findViewById<EditText>(R.id.et_client_phone).text.toString().trim()
            cAddress = findViewById<EditText>(R.id.et_client_address).text.toString().trim()
        } else {
            val client = clients[pos - 1]
            clientId = client.id
            cName = client.name
            cPhone = client.phone
            cAddress = client.address
        }
        if (cName.isEmpty()) {
            Toast.makeText(this, "Client name is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Add at least one item", Toast.LENGTH_SHORT).show()
            return
        }
        val subtotal = lines.sumOf { it.amount }
        val discount = findViewById<EditText>(R.id.et_discount).text.toString().toDoubleOrNull() ?: 0.0
        val gstEnabled = findViewById<SwitchMaterial>(R.id.switch_gst).isChecked
        val totals = computeTotals(subtotal, discount, gstEnabled, gstRate())
        val status = if (findViewById<CheckBox>(R.id.cb_paid).isChecked) "PAID" else "UNPAID"
        val due = if (findViewById<CheckBox>(R.id.cb_due).isChecked) dueDateMillis else null
        val remarks = findViewById<EditText>(R.id.et_remarks).text.toString().trim()

        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InvoiceEditActivity).dao()
            if (editInvoiceId > 0) {
                val inv = Invoice(
                    id = editInvoiceId,
                    number = dao.getInvoice(editInvoiceId)?.number ?: "INV-001",
                    clientId = clientId,
                    clientName = cName,
                    clientPhone = cPhone,
                    clientAddress = cAddress,
                    dateMillis = dateMillis,
                    dueDateMillis = due,
                    subtotal = totals.subtotal,
                    discount = totals.discount,
                    gstEnabled = gstEnabled,
                    gstRate = gstRate(),
                    total = totals.total,
                    status = status,
                    remarks = remarks
                )
                dao.updateInvoice(inv)
                dao.deleteLines(editInvoiceId)
                for (line in lines) {
                    dao.insertLine(InvoiceLine(invoiceId = editInvoiceId, itemName = line.name, qty = line.qty, rate = line.rate, amount = line.amount))
                }
                Toast.makeText(this@InvoiceEditActivity, "Invoice updated", Toast.LENGTH_SHORT).show()
                // Edit path: return to the detail screen, which reloads in onResume.
                finish()
            } else {
                val maxId = dao.getMaxInvoiceId() ?: 0
                val number = "INV-" + String.format("%03d", maxId + 1)
                val inv = Invoice(
                    number = number,
                    clientId = clientId,
                    clientName = cName,
                    clientPhone = cPhone,
                    clientAddress = cAddress,
                    dateMillis = dateMillis,
                    dueDateMillis = due,
                    subtotal = totals.subtotal,
                    discount = totals.discount,
                    gstEnabled = gstEnabled,
                    gstRate = gstRate(),
                    total = totals.total,
                    status = status,
                    remarks = remarks
                )
                val newId = dao.insertInvoice(inv)
                for (line in lines) {
                    dao.insertLine(InvoiceLine(invoiceId = newId, itemName = line.name, qty = line.qty, rate = line.rate, amount = line.amount))
                }
                Toast.makeText(this@InvoiceEditActivity, "Invoice " + number + " saved", Toast.LENGTH_SHORT).show()
                // New-invoice path: open the detail screen directly so Share PDF,
                // Save to Downloads, Edit, Duplicate and Delete appear immediately
                // instead of dropping the user on the list.
                val detail = Intent(this@InvoiceEditActivity, InvoiceDetailActivity::class.java)
                detail.putExtra(InvoiceDetailActivity.EXTRA_INVOICE_ID, newId)
                startActivity(detail)
                finish()
            }
        }
    }

    private inner class EditLineAdapter(
        private val data: MutableList<EditLine>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<EditLineAdapter.Holder>() {

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tv_line_name)
            val qtyEt: EditText = v.findViewById(R.id.et_qty)
            val rateEt: EditText = v.findViewById(R.id.et_rate)
            val amountTv: TextView = v.findViewById(R.id.tv_amount)
            var qtyWatcher: TextWatcher? = null
            var rateWatcher: TextWatcher? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_edit_line, parent, false))
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = data[position]
            holder.name.text = line.name
            holder.qtyWatcher?.let { holder.qtyEt.removeTextChangedListener(it) }
            holder.rateWatcher?.let { holder.rateEt.removeTextChangedListener(it) }
            holder.qtyEt.setText(if (line.qty == line.qty.toLong().toDouble()) line.qty.toLong().toString() else line.qty.toString())
            holder.rateEt.setText(line.rate.toString())
            holder.amountTv.text = fmtMoney(line.amount)
            holder.qtyWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val p = holder.bindingAdapterPosition
                    if (p == RecyclerView.NO_POSITION) return
                    data[p].qty = s?.toString()?.toDoubleOrNull() ?: 0.0
                    holder.amountTv.text = fmtMoney(data[p].amount)
                    onChanged()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            holder.rateWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val p = holder.bindingAdapterPosition
                    if (p == RecyclerView.NO_POSITION) return
                    data[p].rate = s?.toString()?.toDoubleOrNull() ?: 0.0
                    holder.amountTv.text = fmtMoney(data[p].amount)
                    onChanged()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            holder.qtyEt.addTextChangedListener(holder.qtyWatcher)
            holder.rateEt.addTextChangedListener(holder.rateWatcher)
            holder.itemView.findViewById<View>(R.id.btn_remove_line).setOnClickListener {
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) return@setOnClickListener
                data.removeAt(p)
                notifyItemRemoved(p)
                onChanged()
            }
        }
    }
}
