package `in`.billwise.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/**
 * Premium invoice PDF: charcoal ink, champagne-gold rules, serif totals.
 * v1.0.1: company logo in header, tabular bill, remarks, QR code, party
 * signature/stamp block. Creates invoices only — never claims GST filing,
 * e-invoicing or compliance.
 */
object PdfGenerator {

    private val ink = Color.parseColor("#101418")
    private val gold = Color.parseColor("#C6A664")
    private val grey = Color.parseColor("#5A5F66")
    private val lightBorder = Color.parseColor("#E3DDD2")

    fun generate(context: Context, profile: BusinessProfile, invoice: Invoice, lines: List<InvoiceLine>): File {
        return generate(context, profile, invoice, lines, null)
    }

    fun generate(context: Context, profile: BusinessProfile, invoice: Invoice, lines: List<InvoiceLine>, client: Client?): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44
        val right = pageWidth - margin
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas

        val titlePaint = Paint().apply {
            color = ink
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val businessPaint = Paint().apply {
            color = ink
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val normalPaint = Paint().apply {
            color = ink
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = ink
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val smallPaint = Paint().apply {
            color = grey
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val goldLine = Paint().apply {
            color = gold
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val thinLine = Paint().apply {
            color = lightBorder
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val moneyPaint = Paint().apply {
            color = ink
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val totalPaint = Paint().apply {
            color = ink
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        // ---- Business header (logo optional; no logo = v1.0.0 layout) ----
        var y = 58f
        val logoBmp = if (profile.logoPath.isNotBlank()) ImageFiles.loadScaled(profile.logoPath, 512) else null
        var textX = margin.toFloat()
        var logoBottom = 0f
        if (logoBmp != null) {
            val maxW = 64f
            val maxH = 64f
            val scale = minOf(maxW / logoBmp.width, maxH / logoBmp.height, 1f)
            val w = logoBmp.width * scale
            val h = logoBmp.height * scale
            val top = 38f
            c.drawBitmap(logoBmp, null, Rect(margin, top.toInt(), (margin + w).toInt(), (top + h).toInt()), null)
            textX = margin + w + 12f
            logoBottom = top + h
        }
        c.drawText(if (profile.name.isBlank()) "BillWise" else profile.name, textX, y, businessPaint)
        y += 18f
        if (profile.phone.isNotBlank()) {
            c.drawText(profile.phone, textX, y, smallPaint)
            y += 14f
        }
        if (profile.address.isNotBlank()) {
            c.drawText(profile.address, textX, y, smallPaint)
            y += 14f
        }
        if (profile.gstin.isNotBlank()) {
            c.drawText("GSTIN: " + profile.gstin, textX, y, smallPaint)
            y += 14f
        }
        if (logoBottom > 0f) y = maxOf(y, logoBottom + 10f) else y += 10f
        c.drawLine(margin.toFloat(), y, right.toFloat(), y, goldLine)
        y += 30f

        // ---- Title + meta ----
        c.drawText("INVOICE", margin.toFloat(), y, titlePaint)
        val metaRight = right.toFloat()
        c.drawText(invoice.number, metaRight - normalPaint.measureText(invoice.number), y, normalPaint)
        y += 18f
        val dateText = "Date: " + fmtDate(invoice.dateMillis)
        c.drawText(dateText, metaRight - smallPaint.measureText(dateText), y, smallPaint)
        y += 14f
        if (invoice.dueDateMillis != null) {
            val dueText = "Due: " + fmtDate(invoice.dueDateMillis)
            c.drawText(dueText, metaRight - smallPaint.measureText(dueText), y, smallPaint)
            y += 14f
        }
        val statusText = "Status: " + invoice.status
        c.drawText(statusText, metaRight - smallPaint.measureText(statusText), y, smallPaint)
        y += 26f

        // ---- Bill to ----
        c.drawText("Bill To", margin.toFloat(), y, boldPaint)
        y += 16f
        c.drawText(invoice.clientName, margin.toFloat(), y, normalPaint)
        y += 15f
        if (invoice.clientPhone.isNotBlank()) {
            c.drawText(invoice.clientPhone, margin.toFloat(), y, smallPaint)
            y += 14f
        }
        if (invoice.clientAddress.isNotBlank()) {
            c.drawText(invoice.clientAddress, margin.toFloat(), y, smallPaint)
            y += 14f
        }
        y += 18f
        c.drawLine(margin.toFloat(), y, right.toFloat(), y, goldLine)
        y += 22f

        // ---- Tabular bill ----
        // Geometry (content width 507pt): # | Item (up to 274pt) | Qty | Rate | Amount (all right-aligned).
        val colHash = margin.toFloat()
        val colItem = margin + 24f
        val colQty = 350f
        val colRate = 432f
        val colAmt = right.toFloat()
        val itemMaxWidth = colQty - colItem - 10f
        val headerPaint = Paint().apply {
            color = grey
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        c.drawText("#", colHash, y, headerPaint)
        c.drawText("ITEM", colItem, y, headerPaint)
        c.drawText("QTY", colQty - headerPaint.measureText("QTY"), y, headerPaint)
        c.drawText("RATE", colRate - headerPaint.measureText("RATE"), y, headerPaint)
        c.drawText("AMOUNT", colAmt - headerPaint.measureText("AMOUNT"), y, headerPaint)
        y += 8f
        c.drawLine(margin.toFloat(), y, right.toFloat(), y, thinLine)
        y += 20f

        for ((idx, line) in lines.withIndex()) {
            c.drawText((idx + 1).toString(), colHash, y, normalPaint)
            c.drawText(ellipsize(line.itemName, normalPaint, itemMaxWidth), colItem, y, normalPaint)
            val qtyText = trimQty(line.qty)
            c.drawText(qtyText, colQty - normalPaint.measureText(qtyText), y, normalPaint)
            val rateText = fmtMoney(line.rate)
            c.drawText(rateText, colRate - moneyPaint.measureText(rateText), y, moneyPaint)
            val amtText = fmtMoney(line.amount)
            c.drawText(amtText, colAmt - moneyPaint.measureText(amtText), y, moneyPaint)
            y += 20f
        }
        c.drawLine(margin.toFloat(), y, right.toFloat(), y, thinLine)
        y += 22f

        // ---- Totals (labels right-aligned to 421, values right-aligned to right edge) ----
        val totalsLabelRight = 421f
        val totals = computeTotals(invoice.subtotal, invoice.discount, invoice.gstEnabled, invoice.gstRate)
        fun totalRow(label: String, value: String, paint: Paint) {
            c.drawText(label, totalsLabelRight - smallPaint.measureText(label), y, smallPaint)
            c.drawText(value, colAmt - paint.measureText(value), y, paint)
            y += 18f
        }
        totalRow("Subtotal", fmtMoney(totals.subtotal), moneyPaint)
        if (totals.discount > 0) {
            totalRow("Discount", "-" + fmtMoney(totals.discount), moneyPaint)
        }
        if (invoice.gstEnabled && totals.gstAmount > 0) {
            val half = invoice.gstRate / 2.0
            totalRow("CGST (" + trimQty(half) + "%)", fmtMoney(totals.cgst), moneyPaint)
            totalRow("SGST (" + trimQty(half) + "%)", fmtMoney(totals.sgst), moneyPaint)
        }
        y += 4f
        c.drawLine(totalsLabelRight - 60f, y, right.toFloat(), y, goldLine)
        y += 22f
        c.drawText("Total", totalsLabelRight - totalPaint.measureText("Total"), y, totalPaint)
        val totalText = fmtMoney(totals.total)
        c.drawText(totalText, colAmt - totalPaint.measureText(totalText), y, totalPaint)
        y += 30f

        // ---- Remarks (only when present) ----
        if (invoice.remarks.isNotBlank()) {
            c.drawText("Remarks:", margin.toFloat(), y, boldPaint)
            y += 16f
            for (wrapped in wrapText(invoice.remarks, normalPaint, (right - margin).toFloat())) {
                c.drawText(wrapped, margin.toFloat(), y, normalPaint)
                y += 15f
            }
            y += 10f
        }

        // ---- Bottom block: QR (left) + party signature/stamp (right) ----
        val blockTop = y + 8f
        var blockBottom = blockTop
        val qrContent = buildQrContent(profile, invoice, totals.total)
        val qrBmp = makeQrBitmap(qrContent, 400)
        if (qrBmp != null) {
            val qrSize = 140f
            c.drawBitmap(qrBmp, null, Rect(margin, blockTop.toInt(), (margin + qrSize).toInt(), (blockTop + qrSize).toInt()), null)
            val qrLabel = if (profile.upiId.isNotBlank()) "Scan to Pay" else "Invoice QR"
            c.drawText(qrLabel, margin.toFloat(), blockTop + qrSize + 16f, smallPaint)
            blockBottom = maxOf(blockBottom, blockTop + qrSize + 16f)
        }
        val sigX = 340f
        var sigY = blockTop
        // The invoice signature is the SELLING company's own signature (business
        // profile), not the party/client's — v1.0.6.
        val sigBmp = if (profile.signaturePath.isNotBlank()) ImageFiles.loadScaled(profile.signaturePath, 512) else null
        val stampBmp = if (client != null && client.stampPath.isNotBlank()) ImageFiles.loadScaled(client.stampPath, 512) else null
        if (sigBmp != null) {
            // v1.0.8: trim the empty margins first — a finger-drawn signature
            // is a full-pad transparent bitmap, so without trimming the ink
            // renders tiny inside the box — then centre the trimmed image
            // over the line (the "Signature" label below is centred too).
            val trimmed = trimEmptyBorders(sigBmp)
            val fit = fitRect(trimmed.width, trimmed.height, 160f, 64f)
            val drawX = sigX + (160f - fit.first) / 2f
            c.drawBitmap(trimmed, null, Rect(drawX.toInt(), sigY.toInt(), (drawX + fit.first).toInt(), (sigY + fit.second).toInt()), null)
            sigY += fit.second + 8f
            c.drawLine(sigX, sigY, sigX + 160f, sigY, thinLine)
            sigY += 16f
            val sigLabelX = sigX + (160f - smallPaint.measureText("Signature")) / 2f
            c.drawText("Signature", sigLabelX, sigY, smallPaint)
            sigY += 10f
        }
        if (stampBmp != null) {
            c.drawText("Stamp", sigX, sigY + 12f, smallPaint)
            sigY += 18f
            val fit = fitRect(stampBmp.width, stampBmp.height, 120f, 120f)
            c.drawBitmap(stampBmp, null, Rect(sigX.toInt(), sigY.toInt(), (sigX + fit.first).toInt(), (sigY + fit.second).toInt()), null)
            sigY += fit.second + 10f
        }
        if (sigBmp == null && stampBmp == null) {
            c.drawLine(sigX, sigY + 30f, sigX + 160f, sigY + 30f, thinLine)
            val sigLabelX = sigX + (160f - smallPaint.measureText("Signature")) / 2f
            c.drawText("Signature", sigLabelX, sigY + 46f, smallPaint)
            sigY += 56f
        }
        blockBottom = maxOf(blockBottom, sigY)
        y = blockBottom + 28f

        // ---- Footer (unchanged from v1.0.0) ----
        c.drawText("This is a computer-generated invoice.", margin.toFloat(), y, smallPaint)

        doc.finishPage(page)
        val dir = File(context.cacheDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, invoice.number + ".pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun trimQty(q: Double): String {
        return if (q == q.toLong().toDouble()) q.toLong().toString()
        else String.format(java.util.Locale.ENGLISH, "%.2f", q)
    }

    /** Truncate with an ellipsis so the text fits [maxWidth] in [paint]. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > maxWidth) {
            cut = cut.dropLast(1)
        }
        return if (cut.isEmpty()) "…" else "$cut…"
    }

    /** Word-wrap [text] to lines fitting [maxWidth] in [paint]. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = if (paint.measureText(word) <= maxWidth) word else ellipsize(word, paint, maxWidth)
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.ifEmpty { listOf("") }
    }

    /** Scale (w,h) to fit inside (maxW,maxH) preserving aspect. Never upscales. */
    private fun fitRect(w: Int, h: Int, maxW: Float, maxH: Float): Pair<Float, Float> {
        val scale = minOf(maxW / w, maxH / h, 1f)
        return Pair(w * scale, h * scale)
    }

    /** Crop transparent / near-white margins off a signature image so the ink
     *  itself fills the box it is drawn in. A pixel counts as empty when it
     *  is mostly transparent or near-white — this covers both finger-drawn
     *  pads (transparent background) and uploaded photos (white background).
     *  Returns the original bitmap when there is nothing to trim. */
    private fun trimEmptyBorders(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val a = (p ushr 24) and 0xFF
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val empty = a < 128 || (r > 240 && g > 240 && b > 240)
                if (!empty) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return bmp // fully empty — nothing to trim
        val pad = 8
        left = maxOf(0, left - pad)
        top = maxOf(0, top - pad)
        right = minOf(w - 1, right + pad)
        bottom = minOf(h - 1, bottom + pad)
        return Bitmap.createBitmap(bmp, left, top, right - left + 1, bottom - top + 1)
    }

    private fun buildQrContent(profile: BusinessProfile, invoice: Invoice, total: Double): String {
        val upi = profile.upiId.trim()
        return if (upi.isNotBlank()) {
            val biz = if (profile.name.isBlank()) "BillWise" else profile.name
            "upi://pay?pa=" + URLEncoder.encode(upi, "UTF-8") +
                "&pn=" + URLEncoder.encode(biz, "UTF-8") +
                "&am=" + String.format(java.util.Locale.ENGLISH, "%.2f", total) +
                "&tn=" + URLEncoder.encode(invoice.number, "UTF-8")
        } else {
            val biz = if (profile.name.isBlank()) "BillWise" else profile.name
            "Invoice " + invoice.number + " | " + biz + " | Total: " + fmtMoney(total)
        }
    }

    /** On-device QR bitmap (black modules on white). Null on any failure — caller skips the QR. */
    private fun makeQrBitmap(content: String, sizePx: Int): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
