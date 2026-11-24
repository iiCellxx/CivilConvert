package com.cicil.convert

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Animated Canvas View that draws 2D and 3D shape projections returned by the Python engine:
 * polygon, extrude (box/prism), circle, ellipse, sector, ring, cylinder, sphere, hemisphere, cone, pyramid.
 */
class ShapeView(context: Context) : View(context) {

    private var spec: JSONObject? = null
    private val dp = resources.displayMetrics.density

    // Animation scale & alpha for shape reveal
    private var animProgress = 1.0f
    private var animator: ValueAnimator? = null

    // Preallocated drawing objects to prevent memory allocation & GC churn inside onDraw
    private val drawBox = RectF()
    private val tempRect1 = RectF()
    private val tempRect2 = RectF()
    private val tempPath1 = Path()
    private val tempPath2 = Path()

    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = 0xFFE65100.toInt()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val hidden = Paint(edge).apply {
        strokeWidth = 1.5f * dp
        pathEffect = DashPathEffect(floatArrayOf(6f * dp, 5f * dp), 0f)
        color = 0x99A1887F.toInt()
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33E65100.toInt()
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3E2723.toInt()
        textSize = 13f * dp
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8D6E63.toInt()
        textSize = 14f * dp
        textAlign = Paint.Align.CENTER
    }

    init {
        // Modern card background
        setBackgroundColor(0xFFFFF3E0.toInt())
    }

    fun show(s: JSONObject?) {
        spec = s
        animator?.cancel()
        if (s != null) {
            animProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1.0f).apply {
                duration = 350
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animProgress = 1.0f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.70f).toInt())
    }

    /** Maps model coordinates (y up) into the drawing box (y down), keeping aspect ratio. */
    private class Fit(minX: Float, minY: Float, maxX: Float, maxY: Float, box: RectF) {
        val sc: Float
        private val ox: Float
        private val oy: Float

        init {
            val bw = max(maxX - minX, 1e-6f)
            val bh = max(maxY - minY, 1e-6f)
            sc = min(box.width() / bw, box.height() / bh)
            ox = box.left + ((box.width() - bw * sc) / 2f) - (minX * sc)
            oy = box.bottom - ((box.height() - bh * sc) / 2f) + (minY * sc)
        }

        fun x(v: Float) = ox + v * sc
        fun y(v: Float) = oy - v * sc
    }

    private fun pts(a: JSONArray): List<FloatArray> = List(a.length()) {
        val p = a.getJSONArray(it)
        floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec
        if (s == null) {
            canvas.drawText("📐 Shape preview appears here", width / 2f, height / 2f, hintPaint)
            return
        }

        canvas.save()
        // Apply entrance scale animation centered on the canvas
        val scale = 0.85f + 0.15f * animProgress
        canvas.scale(scale, scale, width / 2f, height / 2f)
        val alphaInt = (animProgress * 255).toInt().coerceIn(0, 255)
        edge.alpha = alphaInt
        body.alpha = (alphaInt * 0.2f).toInt()
        ink.alpha = alphaInt

        drawBox.set(48f * dp, 30f * dp, width - 48f * dp, height - 40f * dp)
        when (s.optString("type")) {
            "polygon" -> polygon(canvas, s, drawBox)
            "extrude" -> extrude(canvas, s, drawBox)
            "circle", "ellipse", "sector", "ring" -> circleLike(canvas, s, drawBox)
            else -> solid(canvas, s, drawBox)
        }
        val label = s.optString("label")
        if (label.isNotEmpty()) canvas.drawText(label, width / 2f, height - 12f * dp, ink)

        canvas.restore()
    }

    private fun labels(c: Canvas, s: JSONObject, f: Fit) {
        val arr = s.optJSONArray("labels") ?: return
        for (i in 0 until arr.length()) {
            val l = arr.getJSONObject(i)
            val x = f.x(l.getDouble("x").toFloat()) + l.optDouble("ox", 0.0).toFloat() * 26f * dp
            val y = f.y(l.getDouble("y").toFloat()) + l.optDouble("oy", 0.0).toFloat() * 16f * dp + 5f * dp
            c.drawText(l.getString("t"), x, y, ink)
        }
    }

    private fun polygon(c: Canvas, s: JSONObject, box: RectF) {
        val p = pts(s.getJSONArray("points"))
        val f = Fit(p.minOf { it[0] }, p.minOf { it[1] }, p.maxOf { it[0] }, p.maxOf { it[1] }, box)
        tempPath1.reset()
        p.forEachIndexed { i, v ->
            if (i == 0) tempPath1.moveTo(f.x(v[0]), f.y(v[1])) else tempPath1.lineTo(f.x(v[0]), f.y(v[1]))
        }
        tempPath1.close()
        c.drawPath(tempPath1, body)
        c.drawPath(tempPath1, edge)
        labels(c, s, f)
    }

    private fun extrude(c: Canvas, s: JSONObject, box: RectF) {
        val p = pts(s.getJSONArray("points"))
        val d = s.getDouble("depth").toFloat() * 0.5f
        val f = Fit(
            p.minOf { it[0] }, p.minOf { it[1] },
            p.maxOf { it[0] } + d, p.maxOf { it[1] } + d,
            box,
        )

        fun fillOutline(path: Path, dx: Float, dy: Float) {
            path.reset()
            p.forEachIndexed { i, v ->
                val x = f.x(v[0] + dx)
                val y = f.y(v[1] + dy)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }

        fillOutline(tempPath1, d, d)
        c.drawPath(tempPath1, body)
        c.drawPath(tempPath1, hidden)
        p.forEach { v -> c.drawLine(f.x(v[0]), f.y(v[1]), f.x(v[0] + d), f.y(v[1] + d), hidden) }

        fillOutline(tempPath2, 0f, 0f)
        c.drawPath(tempPath2, body)
        c.drawPath(tempPath2, edge)
        labels(c, s, f)
    }

    private fun circleLike(c: Canvas, s: JSONObject, box: RectF) {
        val type = s.getString("type")
        val rx: Float
        val ry: Float
        when (type) {
            "ellipse" -> {
                rx = s.getDouble("a").toFloat()
                ry = s.getDouble("b").toFloat()
            }
            "ring" -> {
                rx = s.getDouble("R").toFloat()
                ry = rx
            }
            else -> {
                rx = s.getDouble("r").toFloat()
                ry = rx
            }
        }
        val f = Fit(-rx, -ry, rx, ry, box)
        tempRect1.set(f.x(-rx), f.y(ry), f.x(rx), f.y(-ry))
        when (type) {
            "sector" -> {
                val deg = s.getDouble("deg").toFloat()
                c.drawArc(tempRect1, -deg / 2f, deg, true, body)
                c.drawArc(tempRect1, -deg / 2f, deg, true, edge)
            }
            "ring" -> {
                val r = s.getDouble("r").toFloat()
                tempRect2.set(f.x(-r), f.y(r), f.x(r), f.y(-r))
                tempPath1.reset()
                tempPath1.fillType = Path.FillType.EVEN_ODD
                tempPath1.addOval(tempRect1, Path.Direction.CW)
                tempPath1.addOval(tempRect2, Path.Direction.CW)
                c.drawPath(tempPath1, body)
                c.drawOval(tempRect1, edge)
                c.drawOval(tempRect2, edge)
            }
            else -> {
                c.drawOval(tempRect1, body)
                c.drawOval(tempRect1, edge)
                if (type == "circle") {
                    c.drawLine(f.x(0f), f.y(0f), f.x(rx), f.y(0f), edge)
                    c.drawCircle(f.x(0f), f.y(0f), 3f * dp, ink)
                }
            }
        }
    }

    private fun solid(c: Canvas, s: JSONObject, box: RectF) {
        val r = s.optDouble("r", 0.0).toFloat()
        val h = s.optDouble("h", 0.0).toFloat()
        when (s.optString("type")) {
            "sphere" -> {
                val ry = r * 0.3f
                val f = Fit(-r, -r, r, r, box)
                tempRect1.set(f.x(-r), f.y(r), f.x(r), f.y(-r))
                c.drawOval(tempRect1, body)
                c.drawOval(tempRect1, edge)
                tempRect2.set(f.x(-r), f.y(ry), f.x(r), f.y(-ry))
                c.drawArc(tempRect2, 0f, 180f, false, edge)
                c.drawArc(tempRect2, 180f, 180f, false, hidden)
            }
            "hemisphere" -> {
                val ry = r * 0.3f
                val f = Fit(-r, -ry, r, r, box)
                tempRect1.set(f.x(-r), f.y(r), f.x(r), f.y(-r))
                tempRect2.set(f.x(-r), f.y(ry), f.x(r), f.y(-ry))
                c.drawArc(tempRect1, 180f, 180f, true, body)
                c.drawArc(tempRect1, 180f, 180f, false, edge)
                c.drawOval(tempRect2, body)
                c.drawOval(tempRect2, edge)
            }
            "cylinder" -> {
                val ry = r * 0.3f
                val f = Fit(-r, -ry, r, h + ry, box)
                val l = f.x(-r)
                val rt = f.x(r)
                val top = f.y(h)
                val bot = f.y(0f)
                val rs = ry * f.sc
                c.drawRect(l, top, rt, bot, body)
                c.drawLine(l, top, l, bot, edge)
                c.drawLine(rt, top, rt, bot, edge)
                tempRect1.set(l, bot - rs, rt, bot + rs)
                c.drawArc(tempRect1, 0f, 180f, false, edge)
                c.drawArc(tempRect1, 180f, 180f, false, hidden)
                tempRect2.set(l, top - rs, rt, top + rs)
                c.drawOval(tempRect2, body)
                c.drawOval(tempRect2, edge)
            }
            "cone" -> {
                val ry = r * 0.3f
                val f = Fit(-r, -ry, r, h, box)
                val ax = f.x(0f)
                val ay = f.y(h)
                val l = f.x(-r)
                val rt = f.x(r)
                val by = f.y(0f)
                val rs = ry * f.sc
                tempPath1.reset()
                tempPath1.moveTo(ax, ay)
                tempPath1.lineTo(l, by)
                tempPath1.lineTo(rt, by)
                tempPath1.close()
                c.drawPath(tempPath1, body)
                tempRect1.set(l, by - rs, rt, by + rs)
                c.drawOval(tempRect1, body)
                c.drawLine(ax, ay, l, by, edge)
                c.drawLine(ax, ay, rt, by, edge)
                c.drawArc(tempRect1, 0f, 180f, false, edge)
                c.drawArc(tempRect1, 180f, 180f, false, hidden)
            }
            "pyramid" -> {
                val l = s.getDouble("l").toFloat()
                val w = s.getDouble("w").toFloat()
                val sx = w * 0.5f
                val sy = w * 0.35f
                val apexX = (l + sx) / 2f
                val apexY = sy / 2f + h
                val f = Fit(0f, 0f, l + sx, max(sy, apexY), box)
                val ax = f.x(0f); val ay = f.y(0f)
                val bx = f.x(l); val by = f.y(0f)
                val cx = f.x(l + sx); val cy = f.y(sy)
                val dx = f.x(sx); val dy = f.y(sy)
                val px = f.x(apexX); val py = f.y(apexY)

                tempPath1.reset()
                tempPath1.moveTo(ax, ay); tempPath1.lineTo(bx, by); tempPath1.lineTo(cx, cy); tempPath1.lineTo(dx, dy); tempPath1.close()
                c.drawPath(tempPath1, body)

                tempPath2.reset()
                tempPath2.moveTo(px, py); tempPath2.lineTo(ax, ay); tempPath2.lineTo(bx, by); tempPath2.close()
                c.drawPath(tempPath2, body)

                c.drawLine(dx, dy, ax, ay, hidden)
                c.drawLine(dx, dy, cx, cy, hidden)
                c.drawLine(dx, dy, px, py, hidden)
                c.drawLine(ax, ay, bx, by, edge)
                c.drawLine(bx, by, cx, cy, edge)
                c.drawLine(px, py, ax, ay, edge)
                c.drawLine(px, py, bx, by, edge)
                c.drawLine(px, py, cx, cy, edge)
            }
        }
    }
}
