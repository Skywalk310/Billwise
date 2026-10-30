package `in`.billwise.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ItemsFragment : Fragment() {

    private lateinit var adapter: ItemAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_items, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.item_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val emptyText = view.findViewById<TextView>(R.id.empty_text)
        val dao = AppDatabase.get(requireContext()).dao()
        adapter = ItemAdapter(
            onEdit = { item -> showItemDialog(item) },
            onDelete = { item ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete item?")
                    .setMessage(item.name)
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch { dao.deleteItem(item) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recycler.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            dao.getItems().collectLatest { list ->
                adapter.submitList(list)
                emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        view.findViewById<FloatingActionButton>(R.id.fab_add_item).setOnClickListener {
            showItemDialog(null)
        }
    }

    private fun showItemDialog(existing: Item?) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameEt = EditText(ctx).apply { hint = "Item name"; setText(existing?.name ?: "") }
        val rateEt = EditText(ctx).apply {
            hint = "Rate (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (existing == null) "" else existing.rate.toString())
        }
        layout.addView(nameEt)
        layout.addView(rateEt)
        AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "Add item" else "Edit item")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEt.text.toString().trim()
                val rate = rateEt.text.toString().toDoubleOrNull()
                if (name.isEmpty() || rate == null) {
                    Toast.makeText(ctx, "Name and a valid rate are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dao = AppDatabase.get(ctx).dao()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (existing == null) {
                        dao.insertItem(Item(name = name, rate = rate))
                    } else {
                        dao.updateItem(existing.copy(name = name, rate = rate))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ItemAdapter(
        private val onEdit: (Item) -> Unit,
        private val onDelete: (Item) -> Unit
    ) : ListAdapter<Item, ItemAdapter.Holder>(DIFF) {

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<Item>() {
                override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
                override fun areContentsTheSame(a: Item, b: Item) = a == b
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tv_name)
            val rate: TextView = v.findViewById(R.id.tv_rate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_item, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            holder.name.text = item.name
            holder.rate.text = fmtMoney(item.rate)
            holder.itemView.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(item) }
            holder.itemView.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(item) }
        }
    }
}
