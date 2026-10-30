package `in`.billwise.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtMoney(v: Double): String {
    return "\u20B9" + String.format(Locale.ENGLISH, "%,.2f", v)
}

fun fmtDate(millis: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(millis))
}

data class Totals(
    val subtotal: Double,
    val discount: Double,
    val taxable: Double,
    val gstAmount: Double,
    val cgst: Double,
    val sgst: Double,
    val total: Double
)

fun computeTotals(subtotal: Double, discount: Double, gstEnabled: Boolean, gstRate: Double): Totals {
    val safeDiscount = discount.coerceIn(0.0, subtotal)
    val taxable = (subtotal - safeDiscount).coerceAtLeast(0.0)
    val gstAmount = if (gstEnabled) taxable * gstRate / 100.0 else 0.0
    return Totals(
        subtotal = subtotal,
        discount = safeDiscount,
        taxable = taxable,
        gstAmount = gstAmount,
        cgst = gstAmount / 2.0,
        sgst = gstAmount / 2.0,
        total = taxable + gstAmount
    )
}
