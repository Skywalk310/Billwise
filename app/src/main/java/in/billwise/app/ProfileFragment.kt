package `in`.billwise.app

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File

class ProfileFragment : Fragment() {

    private var pendingLogoPath: String? = null // null = unchanged, "" = removed
    private var savedLogoPath: String = "" // logo path currently in the database
    private var pendingSigPath: String? = null // null = unchanged, "" = removed
    private var pendingSigBitmap: Bitmap? = null // finger-drawn signature, replaces upload
    private var savedSigPath: String = "" // company signature path currently in the database

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = ImageFiles.copyPickedImage(ctx, uri, "logos", "logo.png")) {
                is ImageFiles.PickResult.Ok -> {
                    pendingLogoPath = result.file.absolutePath
                    showLogo(requireView(), result.file.absolutePath)
                    Toast.makeText(ctx, "Logo picked — tap Save to keep it", Toast.LENGTH_SHORT).show()
                }
                ImageFiles.PickResult.StreamFailed ->
                    Toast.makeText(ctx, "Photo khol nahi paye — pehle use phone me download karo", Toast.LENGTH_LONG).show()
                ImageFiles.PickResult.DecodeFailed ->
                    Toast.makeText(ctx, "Ye photo ka format support nahi hota — JPG ya PNG image try karo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val pickSignature = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = ImageFiles.copyPickedImage(ctx, uri, "signatures", "business_signature.png")) {
                is ImageFiles.PickResult.Ok -> {
                    pendingSigPath = result.file.absolutePath
                    pendingSigBitmap = null
                    showSignature(requireView(), result.file.absolutePath)
                    Toast.makeText(ctx, "Signature picked — tap Save to keep it", Toast.LENGTH_SHORT).show()
                }
                ImageFiles.PickResult.StreamFailed ->
                    Toast.makeText(ctx, "Photo khol nahi paye — pehle use phone me download karo", Toast.LENGTH_LONG).show()
                ImageFiles.PickResult.DecodeFailed ->
                    Toast.makeText(ctx, "Ye photo ka format support nahi hota — JPG ya PNG image try karo", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nameEt = view.findViewById<EditText>(R.id.et_biz_name)
        val phoneEt = view.findViewById<EditText>(R.id.et_biz_phone)
        val addrEt = view.findViewById<EditText>(R.id.et_biz_address)
        val gstinEt = view.findViewById<EditText>(R.id.et_biz_gstin)
        val upiEt = view.findViewById<EditText>(R.id.et_biz_upi)
        val dao = AppDatabase.get(requireContext()).dao()

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = dao.getProfile()
            if (profile != null) {
                nameEt.setText(profile.name)
                phoneEt.setText(profile.phone)
                addrEt.setText(profile.address)
                gstinEt.setText(profile.gstin)
                upiEt.setText(profile.upiId)
                savedLogoPath = profile.logoPath
                showLogo(view, profile.logoPath)
                savedSigPath = profile.signaturePath
                showSignature(view, profile.signaturePath)
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_logo).setOnClickListener {
            pickLogo.launch("image/*")
        }
        view.findViewById<MaterialButton>(R.id.btn_remove_logo).setOnClickListener {
            pendingLogoPath = ""
            view.findViewById<ImageView>(R.id.iv_biz_logo).setImageDrawable(null)
            Toast.makeText(requireContext(), "Logo removed — tap Save to keep it", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.btn_pick_signature).setOnClickListener {
            pickSignature.launch("image/*")
        }
        view.findViewById<MaterialButton>(R.id.btn_remove_signature).setOnClickListener {
            pendingSigPath = ""
            pendingSigBitmap = null
            view.findViewById<ImageView>(R.id.iv_biz_signature).setImageDrawable(null)
            Toast.makeText(requireContext(), "Signature removed — tap Save to keep it", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.btn_draw_signature).setOnClickListener {
            showSignaturePad()
        }

        view.findViewById<MaterialButton>(R.id.btn_save_profile).setOnClickListener {
            val logoPath = when (pendingLogoPath) {
                null -> savedLogoPath
                else -> pendingLogoPath!!
            }
            if (pendingLogoPath == "") {
                ImageFiles.deleteIfExists(savedLogoPath)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val sigPath = when {
                    pendingSigPath != null -> {
                        if (pendingSigPath == "") {
                            ImageFiles.deleteIfExists(savedSigPath)
                            ""
                        } else pendingSigPath!!
                    }
                    pendingSigBitmap != null -> {
                        ImageFiles.deleteIfExists(savedSigPath)
                        ImageFiles.saveBitmap(requireContext(), pendingSigBitmap!!, "signatures", "business_signature.png")?.absolutePath ?: savedSigPath
                    }
                    else -> savedSigPath
                }
                val profile = BusinessProfile(
                    name = nameEt.text.toString().trim(),
                    phone = phoneEt.text.toString().trim(),
                    address = addrEt.text.toString().trim(),
                    gstin = gstinEt.text.toString().trim(),
                    logoPath = logoPath,
                    upiId = upiEt.text.toString().trim(),
                    signaturePath = sigPath
                )
                val existing = dao.getProfile()
                if (existing == null) dao.insertProfile(profile) else dao.updateProfile(profile)
                savedLogoPath = logoPath
                pendingLogoPath = null
                savedSigPath = sigPath
                pendingSigPath = null
                pendingSigBitmap = null
                Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLogo(view: View, path: String) {
        val iv = view.findViewById<ImageView>(R.id.iv_biz_logo)
        if (path.isNotBlank() && File(path).exists()) {
            iv.setImageBitmap(BitmapFactory.decodeFile(path))
        } else {
            iv.setImageDrawable(null)
        }
    }

    private fun showSignature(view: View, path: String) {
        val iv = view.findViewById<ImageView>(R.id.iv_biz_signature)
        if (path.isNotBlank() && File(path).exists()) {
            iv.setImageBitmap(BitmapFactory.decodeFile(path))
        } else {
            iv.setImageDrawable(null)
        }
    }

    private fun showSignaturePad() {
        val ctx = requireContext()
        val pad = SignaturePadView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 480
            )
            setBackgroundResource(R.drawable.bg_input)
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Draw signature")
            .setView(pad)
            .setPositiveButton("Save") { _, _ ->
                if (pad.hasSignature) {
                    pendingSigBitmap = pad.toBitmap()
                    pendingSigPath = null
                    view?.findViewById<ImageView>(R.id.iv_biz_signature)?.setImageBitmap(pendingSigBitmap)
                    Toast.makeText(ctx, "Signature drawn — tap Save to keep it", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Draw something first", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ -> pad.clear() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { pad.clear() }
    }
}
