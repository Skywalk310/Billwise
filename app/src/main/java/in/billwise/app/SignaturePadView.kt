package `in`.billwise.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Finger-drawn signature pad. Transparent background so it overlays cleanly on the PDF. */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val drawPath = Path()
    private val drawPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private var lastX = 0f
    private var lastY = 0f
    var hasSignature = false
        private set

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.moveTo(x, y)
                lastX = x
                lastY = y
                hasSignature = true
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                drawPath.lineTo(x, y)
            }
        }
        invalidate()
        return true
    }

    fun clear() {
        drawPath.reset()
        hasSignature = false
        invalidate()
    }

    /** Render the drawn strokes to a transparent bitmap (unscaled view size). */
    fun toBitmap(): Bitmap {
        val w = if (width > 0) width else 600
        val h = if (height > 0) height else 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawPath(drawPath, drawPaint)
        return bmp
    }
}
