package `in`.billwise.app

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClientsFragment : Fragment() {

    private lateinit var adapter: ClientAdapter

    // Pending per-dialog state (reset every time the client dialog opens).
    // NOTE (v1.0.6): the invoice signature belongs to the selling company, so
    // it lives in the business profile now — the client form has no signature
    // section anymore, only the stamp.
    private var pendingStampUri: Uri? = null
    private var pendingStampCleared: Boolean = false
    private var stampPreview: ImageView? = null

    private val pickStamp = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        pendingStampUri = uri
        pendingStampCleared = false
        stampPreview?.setImageURI(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_clients, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.client_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val emptyText = view.findViewById<TextView>(R.id.empty_text)
        val dao = AppDatabase.get(requireContext()).dao()
        adapter = ClientAdapter(
            onEdit = { client -> showClientDialog(client) },
            onDelete = { client ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete client?")
                    .setMessage(client.name)
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            dao.deleteClient(client)
                            ImageFiles.deleteIfExists(client.signaturePath)
                            ImageFiles.deleteIfExists(client.stampPath)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recycler.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            dao.getClients().collectLatest { list ->
                adapter.submitList(list)
                emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        view.findViewById<FloatingActionButton>(R.id.fab_add_client).setOnClickListener {
            showClientDialog(null)
        }
    }

    private fun showClientDialog(existing: Client?) {
        pendingStampUri = null
        pendingStampCleared = false
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameEt = EditText(ctx).apply { hint = "Name"; setText(existing?.name ?: "") }
        val phoneEt = EditText(ctx).apply { hint = "Phone"; setText(existing?.phone ?: "") }
        val addrEt = EditText(ctx).apply { hint = "Address"; setText(existing?.address ?: "") }
        layout.addView(nameEt)
        layout.addView(phoneEt)
        layout.addView(addrEt)

        val stampLabel = TextView(ctx).apply { text = "Party stamp (optional)"; setPadding(0, 32, 0, 8) }
        layout.addView(stampLabel)
        stampPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(240, 240)
            setBackgroundResource(R.drawable.bg_input)
            setImageBitmap(existing?.stampPath?.let { ImageFiles.loadScaled(it, 512) })
        }
        layout.addView(stampPreview)
        val stampRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val pickStampBtn = MaterialButton(ctx).apply { text = "Pick stamp image" }
        val clearStampBtn = MaterialButton(ctx).apply { text = "Clear stamp" }
        stampRow.addView(pickStampBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stampRow.addView(clearStampBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(stampRow)

        pickStampBtn.setOnClickListener { pickStamp.launch("image/*") }
        clearStampBtn.setOnClickListener {
            pendingStampUri = null
            pendingStampCleared = true
            stampPreview?.setImageDrawable(null)
        }

        AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "Add client" else "Edit client")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEt.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dao = AppDatabase.get(ctx).dao()
                viewLifecycleOwner.lifecycleScope.launch {
                    val phone = phoneEt.text.toString().trim()
                    val address = addrEt.text.toString().trim()
                    if (existing == null) {
                        val id = dao.insertClient(Client(name = name, phone = phone, address = address))
                        val stampPath = writePendingStamp(id)
                        dao.updateClient(Client(id = id, name = name, phone = phone, address = address, stampPath = stampPath))
                    } else {
                        var stampPath = existing.stampPath
                        if (pendingStampCleared) {
                            ImageFiles.deleteIfExists(stampPath)
                            stampPath = ""
                        } else if (pendingStampUri != null) {
                            ImageFiles.deleteIfExists(stampPath)
                            stampPath = writePendingStamp(existing.id) ?: stampPath
                        }
                        dao.updateClient(existing.copy(name = name, phone = phone, address = address, stampPath = stampPath))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun writePendingStamp(clientId: Long): String {
        val uri = pendingStampUri ?: return ""
        return when (val result = ImageFiles.copyPickedImage(requireContext(), uri, "stamps", "stamp_$clientId.png")) {
            is ImageFiles.PickResult.Ok -> result.file.absolutePath
            ImageFiles.PickResult.StreamFailed -> {
                Toast.makeText(requireContext(), "Photo khol nahi paye — pehle use phone me download karo", Toast.LENGTH_LONG).show()
                ""
            }
            ImageFiles.PickResult.DecodeFailed -> {
                Toast.makeText(requireContext(), "Ye photo ka format support nahi hota — JPG ya PNG image try karo", Toast.LENGTH_LONG).show()
                ""
            }
        }
    }

    private class ClientAdapter(
        private val onEdit: (Client) -> Unit,
        private val onDelete: (Client) -> Unit
    ) : ListAdapter<Client, ClientAdapter.Holder>(DIFF) {

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<Client>() {
                override fun areItemsTheSame(a: Client, b: Client) = a.id == b.id
                override fun areContentsTheSame(a: Client, b: Client) = a == b
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tv_name)
            val phone: TextView = v.findViewById(R.id.tv_phone)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_client, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = getItem(position)
            holder.name.text = c.name
            holder.phone.text = if (c.phone.isBlank()) c.address else c.phone
            holder.itemView.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(c) }
            holder.itemView.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(c) }
        }
    }
}
