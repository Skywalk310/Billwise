package `in`.billwise.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InvoicesFragment : Fragment() {

    private lateinit var adapter: InvoiceAdapter
    private var allInvoices: List<Invoice> = emptyList()
    private var statusFilter: String? = null
    private var query: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_invoice_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.invoice_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = InvoiceAdapter { invoice ->
            val intent = Intent(requireContext(), InvoiceDetailActivity::class.java)
            intent.putExtra(InvoiceDetailActivity.EXTRA_INVOICE_ID, invoice.id)
            startActivity(intent)
        }
        recycler.adapter = adapter

        val emptyText = view.findViewById<TextView>(R.id.empty_text)
        val dao = AppDatabase.get(requireContext()).dao()
        viewLifecycleOwner.lifecycleScope.launch {
            dao.getInvoices().collectLatest { list ->
                allInvoices = list
                applyFilter(emptyText)
            }
        }

        view.findViewById<EditText>(R.id.search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim() ?: ""
                applyFilter(emptyText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<ChipGroup>(R.id.filter_group).setOnCheckedStateChangeListener { _, checkedIds ->
            statusFilter = when {
                checkedIds.contains(R.id.chip_paid) -> "PAID"
                checkedIds.contains(R.id.chip_unpaid) -> "UNPAID"
                else -> null
            }
            applyFilter(emptyText)
        }

        view.findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(Intent(requireContext(), InvoiceEditActivity::class.java))
        }
    }

    private fun applyFilter(emptyText: TextView) {
        var list = allInvoices
        if (statusFilter != null) {
            list = list.filter { it.status == statusFilter }
        }
        if (query.isNotEmpty()) {
            list = list.filter {
                it.number.contains(query, ignoreCase = true) ||
                    it.clientName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(list)
        emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private class InvoiceAdapter(private val onClick: (Invoice) -> Unit) :
        ListAdapter<Invoice, InvoiceAdapter.Holder>(DIFF) {

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<Invoice>() {
                override fun areItemsTheSame(a: Invoice, b: Invoice) = a.id == b.id
                override fun areContentsTheSame(a: Invoice, b: Invoice) = a == b
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val number: TextView = v.findViewById(R.id.tv_number)
            val client: TextView = v.findViewById(R.id.tv_client)
            val date: TextView = v.findViewById(R.id.tv_date)
            val total: TextView = v.findViewById(R.id.tv_total)
            val status: TextView = v.findViewById(R.id.tv_status)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_invoice, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val inv = getItem(position)
            holder.number.text = inv.number
            holder.client.text = inv.clientName
            holder.date.text = fmtDate(inv.dateMillis)
            holder.total.text = fmtMoney(inv.total)
            if (inv.status == "PAID") {
                holder.status.text = "PAID"
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.paid_green))
            } else {
                holder.status.text = "UNPAID"
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.unpaid_red))
            }
            holder.itemView.setOnClickListener { onClick(inv) }
        }
    }
}
