package com.cicil.convert

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var content: FrameLayout
    private val screens = arrayOfNulls<View>(5)

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    // Orange palette
    private val COLOR_PRIMARY = 0xFFE65100.toInt()
    private val COLOR_PRIMARY_LIGHT = 0xFFFFF3E0.toInt()
    private val COLOR_BG = 0xFFFFF8F1.toInt()
    private val COLOR_SURFACE = 0xFFFFFFFF.toInt()
    private val COLOR_BORDER = 0xFFFFCC80.toInt()
    private val COLOR_TEXT_PRIMARY = 0xFF3E2723.toInt()
    private val COLOR_TEXT_SECONDARY = 0xFF8D6E63.toInt()

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        prefs = getSharedPreferences("civila", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // --- Polished App Header ---
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 16.dp(), 20.dp(), 8.dp())
        }

        val titleContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleContainer.addView(TextView(this).apply {
            text = "CivilConvert"
            textSize = 22f
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
        })
        titleContainer.addView(TextView(this).apply {
            text = "Units, scales, geometry, and calculator"
            textSize = 12f
            setTextColor(COLOR_TEXT_SECONDARY)
        })

        val aiChip = TextView(this).apply {
            text = "✨ ⚙ AI"
            textSize = 13f
            setTextColor(COLOR_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            background = GradientDrawable().apply {
                setColor(COLOR_PRIMARY_LIGHT)
                cornerRadius = 20.dp().toFloat()
                setStroke(1.dp(), COLOR_BORDER)
            }
            setOnClickListener { v ->
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).withEndAction {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    showAiSettings()
                }.start()
            }
        }

        header.addView(titleContainer, LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(aiChip)

        // --- Styled Tab Layout ---
        val tabs = TabLayout(this).apply {
            setSelectedTabIndicatorColor(COLOR_PRIMARY)
            setTabTextColors(COLOR_TEXT_SECONDARY, COLOR_PRIMARY)
            setBackgroundColor(COLOR_SURFACE)
            elevation = 2f * resources.displayMetrics.density
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
        }
        listOf("📐 Convert", "📦 Shapes", "🗺️ Lot", "🧮 SciCal", "📏 Scale").forEach {
            tabs.addTab(tabs.newTab().setText(it))
        }

        content = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
        }

        root.addView(header)
        root.addView(tabs)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = showScreen(tab?.position ?: 0)
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        showScreen(0)
    }

    /** Smooth tab transition animation */
    private fun showScreen(i: Int) {
        val v = screens[i] ?: when (i) {
            0 -> buildConvert()
            1 -> buildShapes()
            2 -> buildLot()
            3 -> buildSciCal()
            else -> ScalePage().build()
        }.also { screens[i] = it }

        v.alpha = 0f
        v.translationY = 24.dp().toFloat()
        content.removeAllViews()
        content.addView(v)

        v.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // --------------------------------------------------------- Modern UI Kit

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp(), 12.dp(), 16.dp(), 28.dp())
    }

    private fun card(inside: View) = MaterialCardView(this).apply {
        radius = 16.dp().toFloat()
        cardElevation = 2f * resources.displayMetrics.density
        setCardBackgroundColor(COLOR_SURFACE)
        strokeWidth = 1.dp()
        strokeColor = COLOR_BORDER
        useCompatPadding = true
        addView(inside)
    }

    private fun scroll(col: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(col)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(COLOR_TEXT_SECONDARY)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(4.dp(), 12.dp(), 0, 4.dp())
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
        setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
        background = GradientDrawable().apply {
            setColor(COLOR_SURFACE)
            cornerRadius = 10.dp().toFloat()
            setStroke(1.dp(), COLOR_BORDER)
        }
    }

    private fun Spinner.onPick(f: (Int) -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = f(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun input(hint: String, signed: Boolean = false) = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setTextColor(COLOR_TEXT_PRIMARY)
        setHintTextColor(COLOR_TEXT_SECONDARY)
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
        background = GradientDrawable().apply {
            setColor(COLOR_SURFACE)
            cornerRadius = 10.dp().toFloat()
            setStroke(1.dp(), COLOR_BORDER)
        }
    }

    private fun button(t: String, onClick: () -> Unit) = MaterialButton(this).apply {
        text = t
        textSize = 14f
        isAllCaps = false
        cornerRadius = 12.dp()
        elevation = 2f * resources.displayMetrics.density
        setBackgroundColor(COLOR_PRIMARY)
        setTextColor(Color.WHITE)
        setPadding(0, 12.dp(), 0, 12.dp())
        setOnClickListener { v ->
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start()
                onClick()
            }.start()
        }
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun lengthUnits(): Pair<List<String>, List<String>> {
        val a = Engine.obj("list_categories").getJSONArray("Length")
        return Pair(
            List(a.length()) { a.getJSONObject(it).getString("key") },
            List(a.length()) { a.getJSONObject(it).getString("label") }
        )
    }

    // ------------------------------------------------------------------- AI

    private val noKeyMessage =
        "To use AI features, tap “✨ ⚙ AI” at the top right and paste your free Google Gemini API key from aistudio.google.com.\n\nAll math and offline calculations work completely without internet."

    private fun apiKey() = prefs.getString("api_key", "") ?: ""

    private fun copyToClipboard(text: String, label: String = "CivilConvert Result") {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun aiLanguage() = "English"

    private fun animatePulse(target: View): ValueAnimator {
        return ObjectAnimator.ofFloat(target, "alpha", 1.0f, 0.4f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun askAi(summary: String, out: TextView) {
        if (summary.isBlank()) { out.text = "Compute something first."; return }
        if (apiKey().isBlank()) { out.text = noKeyMessage; return }
        out.text = "✨ Thinking with Gemini AI…"
        val pulse = animatePulse(out)
        val key = apiKey()
        val lang = aiLanguage()
        lifecycleScope.launch(Dispatchers.IO) {
            val r = try {
                Engine.obj("explain", key, summary, lang)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: "Unknown error")
            }
            withContext(Dispatchers.Main) {
                pulse.cancel()
                out.alpha = 1.0f
                out.text = if (r.optBoolean("ok")) r.getString("text") else "⚠ " + r.optString("error")
            }
        }
    }

    private fun askSvg(summary: String, web: WebView, out: TextView) {
        if (summary.isBlank()) { out.text = "Compute something first."; return }
        if (apiKey().isBlank()) { out.text = noKeyMessage; return }
        out.text = "🎨 Generating SVG diagram with AI…"
        val pulse = animatePulse(out)
        val key = apiKey()
        lifecycleScope.launch(Dispatchers.IO) {
            val r = try {
                Engine.obj("visualize_svg", key, summary)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: "Unknown error")
            }
            withContext(Dispatchers.Main) {
                pulse.cancel()
                out.alpha = 1.0f
                if (r.optBoolean("ok")) {
                    out.text = "✨ AI SVG Diagram generated below:"
                    web.visibility = View.VISIBLE
                    val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<style>body{margin:0;background:#fff;display:flex;justify-content:center;align-items:center}svg{width:100%;height:auto;max-height:260px}</style></head><body>" +
                        r.getString("svg") + "</body></html>"
                    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                } else {
                    out.text = "⚠ " + r.optString("error")
                }
            }
        }
    }

    private fun showAiSettings() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 12.dp(), 20.dp(), 12.dp())
        }
        val keyIn = EditText(this).apply {
            hint = "Google Gemini API key (AIzaSy…)"
            setText(apiKey())
            textSize = 15f
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = 8.dp().toFloat()
                setStroke(1.dp(), COLOR_BORDER)
            }
        }
        col.addView(TextView(this).apply {
            text = "AI explanations and drawings use Google Gemini (get a free API key at aistudio.google.com). " +
                "Your key is stored securely on your phone only."
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
        })
        col.addView(label("API Key"))
        col.addView(keyIn)

        AlertDialog.Builder(this)
            .setTitle("✨ AI Settings")
            .setView(col)
            .setPositiveButton("Save Key") { _, _ ->
                prefs.edit()
                    .putString("api_key", keyIn.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ----------------------------------------------- Shared Animated Result Panel

    /** Preview + numbers + working + AI buttons, used by Shapes and Lot screens. */
    private inner class Panel(parent: LinearLayout) {
        val panelCard = MaterialCardView(this@MainActivity).apply {
            radius = 16.dp().toFloat()
            cardElevation = 2f * resources.displayMetrics.density
            setCardBackgroundColor(COLOR_SURFACE)
            strokeWidth = 1.dp()
            strokeColor = COLOR_BORDER
            useCompatPadding = true
            visibility = View.GONE
        }
        private val cardCol = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }
        val shape = ShapeView(this@MainActivity)
        val title = TextView(this@MainActivity).apply {
            textSize = 22f
            setTextColor(COLOR_PRIMARY)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            setPadding(0, 12.dp(), 0, 4.dp())
        }
        val details = TextView(this@MainActivity).apply {
            textSize = 15f
            setTextColor(COLOR_TEXT_PRIMARY)
            setLineSpacing(4f * resources.displayMetrics.density, 1.0f)
        }
        val steps = TextView(this@MainActivity).apply {
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = GradientDrawable().apply {
                setColor(COLOR_PRIMARY_LIGHT)
                cornerRadius = 10.dp().toFloat()
            }
        }
        val aiOut = TextView(this@MainActivity).apply {
            textSize = 14f
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 10.dp(), 0, 0)
            setTextIsSelectable(true)
        }
        val web = WebView(this@MainActivity).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = false
        }
        var summary = ""

        init {
            cardCol.addView(shape, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = 4.dp()
                bottomMargin = 8.dp()
            })
            cardCol.addView(title)
            cardCol.addView(details)
            cardCol.addView(steps, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = 12.dp()
                bottomMargin = 12.dp()
            })

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("✨ Explain AI") { askAi(summary, aiOut) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = 4.dp() })
            row.addView(button("🎨 Draw SVG") { askSvg(summary, web, aiOut) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 4.dp(); marginEnd = 4.dp() })
            row.addView(button("📋 Copy") {
                if (summary.isNotBlank()) copyToClipboard(summary, "Geometry Result")
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 4.dp() })
            cardCol.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 8.dp() })
            cardCol.addView(web, LinearLayout.LayoutParams(MATCH, 240.dp()).apply { topMargin = 8.dp() })
            cardCol.addView(aiOut)

            panelCard.addView(cardCol)
            parent.addView(panelCard)
        }

        fun show(r: JSONObject, inputs: String) {
            aiOut.text = ""
            web.visibility = View.GONE
            if (!r.optBoolean("ok")) {
                panelCard.visibility = View.VISIBLE
                shape.show(null)
                title.text = "⚠ " + r.optString("error")
                title.setTextColor(0xFFDC2626.toInt()) // Red error
                details.text = ""
                steps.text = ""
                summary = ""
                return
            }
            title.setTextColor(COLOR_PRIMARY)
            val draw = r.getJSONObject("draw")
            shape.show(draw)
            title.text = "${r.getString("title")}: ${r.getString("value_text")}"

            val sec = r.getJSONObject("secondary")
            val sb = StringBuilder("${sec.getString("name")}: ${sec.getString("text")}\n\nEquivalents:\n")
            val eq = r.getJSONArray("equivalents")
            for (i in 0 until eq.length()) {
                val e = eq.getJSONObject(i)
                sb.append("  • ${e.getString("label")}: ${e.getString("text")}\n")
            }
            val warn = r.optJSONArray("warnings")
            if (warn != null && warn.length() > 0) {
                sb.append("\n")
                for (i in 0 until warn.length()) sb.append("⚠ ${warn.getString(i)}\n")
            }
            details.text = sb.toString().trim()

            val st = r.getJSONArray("steps")
            val working = (0 until st.length()).joinToString("\n") { st.getString(it) }
            steps.text = "📌 Step-by-Step Working:\n$working"

            var vertices = ""
            if (draw.optString("type") == "polygon") {
                val p = draw.getJSONArray("points")
                vertices = "\nCorner coordinates (x, y): " + (0 until p.length()).joinToString("; ") {
                    val q = p.getJSONArray(it)
                    "%c(%.2f, %.2f)".format('A' + it % 26, q.getDouble(0), q.getDouble(1))
                }
            }
            summary = "$inputs\nResult: ${title.text}\n${sec.getString("name")}: ${sec.getString("text")}" +
                "\nWorking:\n$working$vertices"

            // Reveal Animation for Panel
            if (panelCard.visibility != View.VISIBLE) {
                panelCard.visibility = View.VISIBLE
                panelCard.alpha = 0f
                panelCard.translationY = 30.dp().toFloat()
                panelCard.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(OvershootInterpolator(1.05f))
                    .start()
            }
        }
    }

    // -------------------------------------------------------------- Screens

    private fun buildConvert(): View {
        val cats = Engine.obj("list_categories")
        val catNames = mutableListOf<String>().also { l -> cats.keys().forEach { l.add(it) } }

        val col = column()
        val formCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        val catSp = spinner(catNames)
        val fromSp = spinner(emptyList())
        val toSp = spinner(emptyList())
        val valIn = input("Enter value (e.g. 100)", signed = true)
        val swapBtn = button("⇅  Swap Units") {}

        val resultCard = MaterialCardView(this).apply {
            radius = 16.dp().toFloat()
            cardElevation = 2f * resources.displayMetrics.density
            setCardBackgroundColor(COLOR_PRIMARY_LIGHT)
            strokeWidth = 1.dp()
            strokeColor = COLOR_BORDER
            useCompatPadding = true
        }

        val resCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
        }

        val result = TextView(this).apply {
            textSize = 28f
            setTextColor(COLOR_PRIMARY)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            text = "—"
        }
        val working = TextView(this).apply {
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, 8.dp(), 0, 0)
        }
        val aiOut = TextView(this).apply {
            textSize = 14f
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 10.dp(), 0, 0)
            setTextIsSelectable(true)
        }

        var keys = listOf<String>()
        var summary = ""

        fun recalc() {
            val t = valIn.text.toString().trim()
            val fi = fromSp.selectedItemPosition
            val ti = toSp.selectedItemPosition
            if (t.toDoubleOrNull() == null || keys.isEmpty() || fi < 0 || ti < 0) {
                result.text = "—"
                working.text = ""
                summary = ""
                return
            }
            val cat = catNames[catSp.selectedItemPosition.coerceAtLeast(0)]
            val r = Engine.obj("convert", cat, t, keys[fi], keys[ti])
            if (r.optBoolean("ok")) {
                result.text = r.getString("text")
                val st = r.getJSONArray("steps")
                working.text = (0 until st.length()).joinToString("\n") { st.getString(it) }
                summary = "Unit conversion ($cat): ${r.getString("text")}\nWorking:\n${working.text}"
            } else {
                result.text = "⚠ " + r.optString("error")
                working.text = ""
                summary = ""
            }
        }

        fun loadUnits() {
            val arr = cats.getJSONArray(catNames[catSp.selectedItemPosition.coerceAtLeast(0)])
            keys = List(arr.length()) { arr.getJSONObject(it).getString("key") }
            val labels = List(arr.length()) { arr.getJSONObject(it).getString("label") }
            fromSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            toSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            toSp.setSelection(if (labels.size > 1) 1 else 0)
            recalc()
        }

        var isSwapping = false

        catSp.onPick { loadUnits() }
        fromSp.onPick { if (!isSwapping) recalc() }
        toSp.onPick { if (!isSwapping) recalc() }
        valIn.doAfterTextChanged { recalc() }

        swapBtn.setOnClickListener {
            val fromPos = fromSp.selectedItemPosition
            val toPos = toSp.selectedItemPosition
            if (fromPos < 0 || toPos < 0 || keys.isEmpty()) return@setOnClickListener
            isSwapping = true
            fromSp.setSelection(toPos, false)
            toSp.setSelection(fromPos, false)
            isSwapping = false
            recalc()
        }

        formCol.addView(label("Category"))
        formCol.addView(catSp)
        formCol.addView(label("Value"))
        formCol.addView(valIn)
        formCol.addView(label("From Unit"))
        formCol.addView(fromSp)
        formCol.addView(swapBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = 12.dp()
            bottomMargin = 4.dp()
        })
        formCol.addView(label("To Unit"))
        formCol.addView(toSp)
        col.addView(card(formCol))

        resCol.addView(result)
        resCol.addView(working)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(button("✨ Explain with AI") { askAi(summary, aiOut) },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = 4.dp() })
        btnRow.addView(button("📋 Copy") {
            if (summary.isNotBlank()) copyToClipboard(summary, "Conversion Result")
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 4.dp() })

        resCol.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 12.dp() })
        resCol.addView(aiOut)
        resultCard.addView(resCol)

        col.addView(resultCard)
        loadUnits()
        return scroll(col)
    }

    private fun buildShapes(): View {
        val shapes = Engine.arr("list_shapes")
        val titles = List(shapes.length()) {
            val o = shapes.getJSONObject(it)
            (if (o.getString("kind") == "area") "Area · " else "Volume · ") + o.getString("title")
        }
        val (unitKeys, unitLabels) = lengthUnits()

        val col = column()
        val formCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        val shapeSp = spinner(titles)
        val unitSp = spinner(unitLabels)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var edits = listOf<Pair<String, EditText>>()
        lateinit var panel: Panel

        fun rebuildFields() {
            fields.removeAllViews()
            val params = shapes.getJSONObject(shapeSp.selectedItemPosition.coerceAtLeast(0)).getJSONArray("params")
            edits = List(params.length()) {
                val p = params.getJSONObject(it)
                val e = input("Enter ${p.getString("label")}")
                fields.addView(label(p.getString("label")))
                fields.addView(e)
                p.getString("key") to e
            }
        }
        shapeSp.onPick { rebuildFields() }

        formCol.addView(label("Select Shape"))
        formCol.addView(shapeSp)
        formCol.addView(label("Measured In"))
        formCol.addView(unitSp)
        formCol.addView(fields)
        formCol.addView(button("📐 Calculate Shape") {
            hideKeyboard()
            val sel = shapes.getJSONObject(shapeSp.selectedItemPosition.coerceAtLeast(0))
            val unit = unitKeys[unitSp.selectedItemPosition.coerceAtLeast(0)]
            val vals = JSONObject()
            val shown = ArrayList<String>()
            edits.forEach { (k, e) ->
                vals.put(k, e.text.toString().trim())
                shown.add("$k = ${e.text.toString().trim()}")
            }
            val r = Engine.obj("calc_shape", sel.getString("id"), vals.toString(), unit)
            panel.show(r, "Shape: ${sel.getString("title")}; measured in $unit; inputs: ${shown.joinToString(", ")}")
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 16.dp() })

        col.addView(card(formCol))
        panel = Panel(col)

        rebuildFields()
        return scroll(col)
    }

    private fun buildLot(): View {
        val (unitKeys, unitLabels) = lengthUnits()
        val col = column()
        val formCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        var mode = "bearings"

        val bearingHint = "One course per line, e.g.:\nN 45-30 E 25.00\nS 10-15 W 12.40\nE 30   (due east)"
        val coordHint = "One corner per line as x, y, e.g.:\n0, 0\n30, 0\n30, 10"

        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rbBearing = RadioButton(this).apply { text = "Bearings & distances"; id = View.generateViewId() }
        val rbCoord = RadioButton(this).apply { text = "X, Y coordinates"; id = View.generateViewId() }
        modeGroup.addView(rbBearing)
        modeGroup.addView(rbCoord)

        val text = EditText(this).apply {
            hint = bearingHint
            minLines = 5
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = 10.dp().toFloat()
                setStroke(1.dp(), COLOR_BORDER)
            }
        }
        val unitSp = spinner(unitLabels)
        lateinit var panel: Panel

        modeGroup.setOnCheckedChangeListener { _, id ->
            mode = if (id == rbCoord.id) "coords" else "bearings"
            text.hint = if (mode == "coords") coordHint else bearingHint
        }

        formCol.addView(label("Lot Description Method"))
        formCol.addView(modeGroup)
        formCol.addView(label("Survey Courses / Coordinates"))
        formCol.addView(text, LinearLayout.LayoutParams(MATCH, WRAP))
        formCol.addView(label("Distances Unit"))
        formCol.addView(unitSp)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("📋 Load Example") {
            if (mode == "coords") text.setText("0, 0\n30, 0\n30, 10\n20, 10\n20, 25\n0, 25")
            else text.setText("E 30\nN 10\nW 10\nN 15\nW 20\nS 25")
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = 4.dp() })
        row.addView(button("🗺️ Compute Lot") {
            hideKeyboard()
            val unit = unitKeys[unitSp.selectedItemPosition.coerceAtLeast(0)]
            val r = Engine.obj("lot_area", mode, text.text.toString(), unit)
            val what = if (mode == "coords") "corner coordinates (x, y)" else "bearings and distances (metes and bounds)"
            panel.show(r, "Irregular lot described by $what, in $unit:\n${text.text}")
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 4.dp() })

        formCol.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 14.dp() })
        col.addView(card(formCol))
        panel = Panel(col)

        rbBearing.isChecked = true
        return scroll(col)
    }

    private fun buildSciCal(): View {
        var degrees = true
        var expr = ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp(), 8.dp(), 10.dp(), 10.dp())
            setBackgroundColor(COLOR_BG)
        }

        val expressionView = TextView(this).apply {
            text = "0"
            textSize = 28f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.END
            setTextIsSelectable(true)
        }
        val resultView = TextView(this).apply {
            textSize = 16f
            setTextColor(COLOR_PRIMARY)
            gravity = Gravity.END
            setPadding(0, 4.dp(), 0, 0)
        }

        fun render(showError: Boolean = false) {
            expressionView.text = SciCal.display(expr)
            expressionView.setTextColor(if (expr.isEmpty()) COLOR_TEXT_SECONDARY else COLOR_TEXT_PRIMARY)
            if (expr.isEmpty()) {
                resultView.text = ""
                return
            }
            try {
                resultView.setTextColor(COLOR_PRIMARY)
                resultView.text = SciCal.format(SciCal.evaluate(expr, degrees))
            } catch (e: SciCal.Error) {
                resultView.setTextColor(0xFFC62828.toInt())
                resultView.text = if (showError) e.message ?: "Invalid expression." else ""
            }
        }

        val displayCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }
        displayCol.addView(TextView(this).apply {
            text = "Scientific calculator"
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        displayCol.addView(expressionView)
        displayCol.addView(resultView)
        root.addView(card(displayCol))

        fun styleMode(b: MaterialButton, selected: Boolean) {
            if (selected) {
                b.setBackgroundColor(COLOR_PRIMARY)
                b.setTextColor(Color.WHITE)
                b.strokeWidth = 0
            } else {
                b.setBackgroundColor(COLOR_SURFACE)
                b.setTextColor(COLOR_PRIMARY)
                b.strokeWidth = 1.dp()
                b.strokeColor = ColorStateList.valueOf(COLOR_BORDER)
            }
        }

        val degBtn = MaterialButton(this).apply {
            text = "Degrees"
            textSize = 13f
            isAllCaps = false
            cornerRadius = 10.dp()
            insetTop = 0
            insetBottom = 0
        }
        val radBtn = MaterialButton(this).apply {
            text = "Radians"
            textSize = 13f
            isAllCaps = false
            cornerRadius = 10.dp()
            insetTop = 0
            insetBottom = 0
        }
        fun paintMode() {
            styleMode(degBtn, degrees)
            styleMode(radBtn, !degrees)
        }
        degBtn.setOnClickListener {
            degrees = true
            paintMode()
            render()
        }
        radBtn.setOnClickListener {
            degrees = false
            paintMode()
            render()
        }
        paintMode()

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(degBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = 4.dp() })
        modeRow.addView(radBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 4.dp() })
        root.addView(modeRow, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = 4.dp()
            bottomMargin = 4.dp()
        })

        fun press(block: () -> Unit) {
            block()
            render()
        }

        fun key(label: String, kind: Int, onClick: () -> Unit) = MaterialButton(this).apply {
            text = label
            textSize = if (label.length > 3) 13f else 16f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 40.dp()
            minimumHeight = 40.dp()
            cornerRadius = 10.dp()
            elevation = 0f
            when (kind) {
                1 -> {
                    setBackgroundColor(COLOR_PRIMARY)
                    setTextColor(Color.WHITE)
                }
                2 -> {
                    setBackgroundColor(COLOR_PRIMARY_LIGHT)
                    setTextColor(COLOR_PRIMARY)
                }
                else -> {
                    setBackgroundColor(COLOR_SURFACE)
                    setTextColor(COLOR_TEXT_PRIMARY)
                    strokeWidth = 1.dp()
                    strokeColor = ColorStateList.valueOf(COLOR_BORDER)
                }
            }
            setOnClickListener { onClick() }
        }

        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(cells: List<Triple<String, Int, () -> Unit>>, weight: Float = 1f) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((label, kind, action) in cells) {
                line.addView(key(label, kind, action), LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                    marginStart = 3.dp()
                    marginEnd = 3.dp()
                    topMargin = 3.dp()
                    bottomMargin = 3.dp()
                })
            }
            pad.addView(line, LinearLayout.LayoutParams(MATCH, 0, weight))
        }

        row(listOf(
            Triple("sin", 2) { press { expr = SciCal.token(expr, "sin(") } },
            Triple("cos", 2) { press { expr = SciCal.token(expr, "cos(") } },
            Triple("tan", 2) { press { expr = SciCal.token(expr, "tan(") } },
            Triple("ln", 2) { press { expr = SciCal.token(expr, "ln(") } },
            Triple("log", 2) { press { expr = SciCal.token(expr, "log(") } },
        ))
        row(listOf(
            Triple("asin", 2) { press { expr = SciCal.token(expr, "asin(") } },
            Triple("acos", 2) { press { expr = SciCal.token(expr, "acos(") } },
            Triple("atan", 2) { press { expr = SciCal.token(expr, "atan(") } },
            Triple("√", 2) { press { expr = SciCal.token(expr, "sqrt(") } },
            Triple("^", 2) { press { expr = SciCal.operator(expr, '^') } },
        ))
        row(listOf(
            Triple("π", 2) { press { expr = SciCal.token(expr, "pi") } },
            Triple("e", 2) { press { expr = SciCal.token(expr, "e") } },
            Triple("x²", 2) { press { expr = SciCal.square(expr) } },
            Triple("n!", 2) { press { expr = SciCal.factorialMark(expr) } },
            Triple("1/x", 2) { press { expr = SciCal.reciprocal(expr) } },
        ))
        row(listOf(
            Triple("7", 0) { press { expr = SciCal.digit(expr, '7') } },
            Triple("8", 0) { press { expr = SciCal.digit(expr, '8') } },
            Triple("9", 0) { press { expr = SciCal.digit(expr, '9') } },
            Triple("÷", 1) { press { expr = SciCal.operator(expr, '/') } },
            Triple("(", 2) { press { expr = SciCal.token(expr, "(") } },
        ))
        row(listOf(
            Triple("4", 0) { press { expr = SciCal.digit(expr, '4') } },
            Triple("5", 0) { press { expr = SciCal.digit(expr, '5') } },
            Triple("6", 0) { press { expr = SciCal.digit(expr, '6') } },
            Triple("×", 1) { press { expr = SciCal.operator(expr, '*') } },
            Triple(")", 2) { press { expr = SciCal.token(expr, ")") } },
        ))
        row(listOf(
            Triple("1", 0) { press { expr = SciCal.digit(expr, '1') } },
            Triple("2", 0) { press { expr = SciCal.digit(expr, '2') } },
            Triple("3", 0) { press { expr = SciCal.digit(expr, '3') } },
            Triple("−", 1) { press { expr = SciCal.operator(expr, '-') } },
            Triple("⌫", 2) { press { expr = SciCal.backspace(expr) } },
        ))
        row(listOf(
            Triple("±", 2) { press { expr = SciCal.sign(expr) } },
            Triple("0", 0) { press { expr = SciCal.digit(expr, '0') } },
            Triple(".", 0) { press { expr = SciCal.dot(expr) } },
            Triple("+", 1) { press { expr = SciCal.operator(expr, '+') } },
            Triple("C", 2) { expr = ""; render() },
        ))
        row(listOf(
            Triple("=", 1) {
                if (expr.isEmpty()) return@Triple
                try {
                    expr = SciCal.format(SciCal.evaluate(expr, degrees))
                    render()
                } catch (_: SciCal.Error) {
                    render(showError = true)
                }
            },
        ))

        root.addView(pad, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private data class ScaleHistoryEntry(
        val modeIndex: Int,
        val measurement: String,
        val fromIndex: Int,
        val fromCustom: String,
        val toIndex: Int,
        val toCustom: String,
        val unitIndex: Int,
        val decimals: Int,
        val label: String,
        val key: String,
    )

    /**
     * Scale tab. Drawing ↔ actual uses 1:1 as the actual side, so every direction
     * is D₂ = D₁ × (S₁ / S₂). State stays on this page while the tab is cached.
     */
    private inner class ScalePage {
        private var decimals = 2
        private var suppress = true
        private var copyText = ""
        private val history = ArrayList<ScaleHistoryEntry>()

        private lateinit var valueIn: EditText
        private lateinit var modeSp: Spinner
        private lateinit var fromSp: Spinner
        private lateinit var toSp: Spinner
        private lateinit var unitSp: Spinner
        private lateinit var fromCustom: EditText
        private lateinit var toCustom: EditText
        private lateinit var fromNote: TextView
        private lateinit var toNote: TextView
        private lateinit var resultView: TextView
        private lateinit var metaView: TextView
        private lateinit var stepsView: TextView
        private lateinit var decLabel: TextView
        private lateinit var historyList: LinearLayout
        private lateinit var clearHistory: TextView
        private lateinit var aiOut: TextView
        private var summary = ""

        private val presetDenoms = ScaleCal.PRESET_DENOMINATORS
        private val customIndex = presetDenoms.size
        private val modes = listOf(
            ScaleCal.Mode.DRAWING_TO_ACTUAL,
            ScaleCal.Mode.ACTUAL_TO_DRAWING,
            ScaleCal.Mode.SCALE_TO_SCALE,
        )
        private val unitKeys = listOf("mm", "cm", "m", "in")

        fun build(): View {
            val scaleLabels = presetDenoms.map { ScaleCal.formatScale(it) } + "Custom"
            val col = column()
            val formCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            }

            modeSp = spinner(listOf("Drawing → actual", "Actual → drawing", "Scale → scale"))
            valueIn = input("Drawing measurement (e.g. 25)")
            unitSp = spinner(listOf("Millimeters (mm)", "Centimeters (cm)", "Meters (m)", "Inches (in)"))
            fromSp = spinner(scaleLabels)
            toSp = spinner(scaleLabels)
            fromCustom = scaleField("Custom scale, e.g. 1:30 or 30")
            toCustom = scaleField("Custom scale, e.g. 1:30 or 30")
            fromNote = note("Original scale: actual size (1:1)")
            toNote = note("Target scale: actual size (1:1)")

            val example = TextView(this@MainActivity).apply {
                text = "Example: 25 mm from 1:50 to 1:100 is 12.50 mm."
                textSize = 12f
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(4.dp(), 2.dp(), 4.dp(), 0)
            }
            decLabel = TextView(this@MainActivity).apply {
                textSize = 15f
                setTextColor(COLOR_TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val decRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            decRow.addView(smallKey("−") { changeDecimals(decimals - 1) }, LinearLayout.LayoutParams(48.dp(), 44.dp()))
            decRow.addView(decLabel, LinearLayout.LayoutParams(0, WRAP, 1f))
            decRow.addView(smallKey("+") { changeDecimals(decimals + 1) }, LinearLayout.LayoutParams(48.dp(), 44.dp()))

            val swapBtn = button("⇅  Swap scales") {
                hideKeyboard()
                swapScales()
            }
            val clearBtn = button("Clear / Reset") {
                hideKeyboard()
                resetForm()
            }

            formCol.addView(label("Direction"))
            formCol.addView(modeSp)
            formCol.addView(example)
            formCol.addView(label("Measurement"))
            formCol.addView(valueIn)
            formCol.addView(label("Unit"))
            formCol.addView(unitSp)
            formCol.addView(label("Original scale"))
            formCol.addView(fromNote)
            formCol.addView(fromSp)
            formCol.addView(fromCustom, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 8.dp() })
            formCol.addView(swapBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = 12.dp()
                bottomMargin = 4.dp()
            })
            formCol.addView(label("Target scale"))
            formCol.addView(toNote)
            formCol.addView(toSp)
            formCol.addView(toCustom, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 8.dp() })
            formCol.addView(label("Decimal places"))
            formCol.addView(decRow)
            formCol.addView(clearBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 16.dp() })
            col.addView(card(formCol))

            val resultCard = MaterialCardView(this@MainActivity).apply {
                radius = 16.dp().toFloat()
                cardElevation = 2f * resources.displayMetrics.density
                setCardBackgroundColor(COLOR_PRIMARY_LIGHT)
                strokeWidth = 1.dp()
                strokeColor = COLOR_BORDER
                useCompatPadding = true
            }
            val resCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            }
            resultView = TextView(this@MainActivity).apply {
                textSize = 28f
                setTextColor(COLOR_PRIMARY)
                typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                setTextIsSelectable(true)
            }
            metaView = TextView(this@MainActivity).apply {
                textSize = 14f
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, 6.dp(), 0, 0)
            }
            stepsView = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, 8.dp(), 0, 0)
                setTextIsSelectable(true)
            }
            val copyBtn = button("📋 Copy") {
                if (copyText.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter a valid conversion first.", Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard(copyText, "Scale conversion")
                    recalc(commit = true)
                }
            }
            resCol.addView(resultView)
            resCol.addView(metaView)
            resCol.addView(stepsView)
            val aiBtn = button("✨ Explain AI") {
                hideKeyboard()
                if (summary.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter a valid conversion first.", Toast.LENGTH_SHORT).show()
                } else {
                    askAi(summary, aiOut)
                }
            }
            resCol.addView(aiBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 12.dp() })
            aiOut = TextView(this@MainActivity).apply {
                textSize = 14f
                setTextColor(COLOR_TEXT_PRIMARY)
                setPadding(0, 8.dp(), 0, 0)
                setTextIsSelectable(true)
            }
            resCol.addView(aiOut)
            resCol.addView(copyBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 12.dp() })
            resultCard.addView(resCol)
            col.addView(resultCard)

            val historyCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            }
            val historyHeader = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            historyHeader.addView(TextView(this@MainActivity).apply {
                text = "Recent conversions"
                textSize = 16f
                setTextColor(COLOR_TEXT_PRIMARY)
                typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            clearHistory = TextView(this@MainActivity).apply {
                text = "Clear history"
                textSize = 13f
                setTextColor(COLOR_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
                setPadding(12.dp(), 8.dp(), 4.dp(), 8.dp())
                setOnClickListener {
                    history.clear()
                    renderHistory()
                }
            }
            historyHeader.addView(clearHistory)
            historyList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            historyCol.addView(historyHeader)
            historyCol.addView(historyList)
            col.addView(card(historyCol))

            modeSp.onPick {
                syncMode()
                recalc(commit = true)
            }
            fromSp.onPick {
                syncMode()
                recalc(commit = true)
            }
            toSp.onPick {
                syncMode()
                recalc(commit = true)
            }
            unitSp.onPick { recalc(commit = true) }
            valueIn.doAfterTextChanged { recalc(commit = false) }
            valueIn.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) recalc(commit = true)
            }
            fromCustom.doAfterTextChanged { recalc(commit = false) }
            toCustom.doAfterTextChanged { recalc(commit = false) }
            fromCustom.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) recalc(commit = true)
            }
            toCustom.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) recalc(commit = true)
            }

            resetForm()
            renderHistory()
            return scroll(col)
        }

        private fun scaleField(hint: String) = EditText(this@MainActivity).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            maxLines = 1
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = 10.dp().toFloat()
                setStroke(1.dp(), COLOR_BORDER)
            }
        }

        private fun note(text: String) = TextView(this@MainActivity).apply {
            this.text = text
            textSize = 15f
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(4.dp(), 4.dp(), 4.dp(), 8.dp())
            visibility = View.GONE
        }

        private fun smallKey(t: String, onClick: () -> Unit) = MaterialButton(this@MainActivity).apply {
            text = t
            textSize = 18f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 44.dp()
            minimumHeight = 44.dp()
            cornerRadius = 10.dp()
            elevation = 0f
            setBackgroundColor(COLOR_PRIMARY_LIGHT)
            setTextColor(COLOR_PRIMARY)
            setOnClickListener { onClick() }
        }

        private fun currentMode(): ScaleCal.Mode {
            val index = modeSp.selectedItemPosition
            return modes[if (index in modes.indices) index else modes.lastIndex]
        }

        private fun enteredScale(sp: Spinner, custom: EditText, actual: Boolean): Double {
            if (actual) return 1.0
            val index = sp.selectedItemPosition
            if (index in presetDenoms.indices) return presetDenoms[index]
            return ScaleCal.parseScale(custom.text.toString())
        }

        private fun syncMode() {
            val mode = currentMode()
            val fromActual = mode == ScaleCal.Mode.ACTUAL_TO_DRAWING
            val toActual = mode == ScaleCal.Mode.DRAWING_TO_ACTUAL
            fromNote.visibility = if (fromActual) View.VISIBLE else View.GONE
            fromSp.visibility = if (fromActual) View.GONE else View.VISIBLE
            fromCustom.visibility =
                if (!fromActual && fromSp.selectedItemPosition == customIndex) View.VISIBLE else View.GONE
            toNote.visibility = if (toActual) View.VISIBLE else View.GONE
            toSp.visibility = if (toActual) View.GONE else View.VISIBLE
            toCustom.visibility =
                if (!toActual && toSp.selectedItemPosition == customIndex) View.VISIBLE else View.GONE
            valueIn.hint = if (fromActual) {
                "Actual measurement (e.g. 1250)"
            } else {
                "Drawing measurement (e.g. 25)"
            }
        }

        private fun paintDecimals() {
            decLabel.text = if (decimals == 1) "1 decimal place" else "$decimals decimal places"
        }

        private fun changeDecimals(next: Int) {
            if (next !in 0..6 || next == decimals) return
            decimals = next
            paintDecimals()
            recalc(commit = true)
        }

        private fun showEmpty() {
            copyText = ""
            summary = ""
            resultView.setTextColor(COLOR_PRIMARY)
            resultView.textSize = 28f
            resultView.text = "—"
            metaView.setTextColor(COLOR_TEXT_SECONDARY)
            metaView.text = "Enter a measurement. The result updates as you type."
            stepsView.visibility = View.GONE
            stepsView.text = ""
            aiOut.text = ""
        }

        private fun showError(message: String) {
            copyText = ""
            summary = ""
            resultView.setTextColor(0xFFC62828.toInt())
            resultView.textSize = 16f
            resultView.text = message
            metaView.text = ""
            stepsView.visibility = View.GONE
            stepsView.text = ""
            aiOut.text = ""
        }

        private fun showResult(result: ScaleCal.Result) {
            copyText = result.copyText
            resultView.setTextColor(COLOR_PRIMARY)
            resultView.textSize = 28f
            resultView.text = result.valueText
            metaView.setTextColor(COLOR_TEXT_PRIMARY)
            metaView.text = result.scaleLine
            stepsView.visibility = View.VISIBLE
            stepsView.text = result.steps.joinToString("\n")
            summary = "Scale conversion: ${result.valueText} · ${result.scaleLine}\nWorking:\n${result.steps.joinToString("\n")}"
        }

        private fun renderHistory() {
            historyList.removeAllViews()
            clearHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
            if (history.isEmpty()) {
                historyList.addView(TextView(this@MainActivity).apply {
                    text = "A result is saved when you leave the measurement field, change a scale, or copy it."
                    textSize = 13f
                    setTextColor(COLOR_TEXT_SECONDARY)
                    setPadding(0, 8.dp(), 0, 0)
                })
                return
            }
            for (entry in history.toList()) {
                historyList.addView(TextView(this@MainActivity).apply {
                    text = entry.label
                    textSize = 14f
                    setTextColor(COLOR_TEXT_PRIMARY)
                    minHeight = 44.dp()
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
                    background = GradientDrawable().apply {
                        setColor(COLOR_PRIMARY_LIGHT)
                        cornerRadius = 10.dp().toFloat()
                    }
                    setOnClickListener {
                        hideKeyboard()
                        restore(entry)
                    }
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 8.dp() })
            }
        }

        private fun commitHistory(result: ScaleCal.Result) {
            val entry = ScaleHistoryEntry(
                modeIndex = modeSp.selectedItemPosition,
                measurement = valueIn.text.toString().trim(),
                fromIndex = fromSp.selectedItemPosition,
                fromCustom = fromCustom.text.toString(),
                toIndex = toSp.selectedItemPosition,
                toCustom = toCustom.text.toString(),
                unitIndex = unitSp.selectedItemPosition,
                decimals = decimals,
                label = result.historyLabel,
                key = listOf(
                    modeSp.selectedItemPosition,
                    valueIn.text.toString().trim(),
                    fromSp.selectedItemPosition,
                    fromCustom.text.toString().trim(),
                    toSp.selectedItemPosition,
                    toCustom.text.toString().trim(),
                    unitSp.selectedItemPosition,
                    decimals,
                ).joinToString("|"),
            )
            if (history.firstOrNull()?.key == entry.key) return
            history.add(0, entry)
            while (history.size > 12) history.removeAt(history.lastIndex)
            renderHistory()
        }

        private fun recalc(commit: Boolean) {
            if (suppress) return
            val typed = valueIn.text.toString()
            if (typed.isBlank()) {
                showEmpty()
                return
            }
            try {
                val mode = currentMode()
                val fromActual = mode == ScaleCal.Mode.ACTUAL_TO_DRAWING
                val toActual = mode == ScaleCal.Mode.DRAWING_TO_ACTUAL
                val result = ScaleCal.convert(
                    measurementText = typed,
                    fromDenominator = enteredScale(fromSp, fromCustom, fromActual),
                    toDenominator = enteredScale(toSp, toCustom, toActual),
                    unit = unitKeys[unitSp.selectedItemPosition.coerceIn(0, unitKeys.lastIndex)],
                    decimals = decimals,
                    mode = mode,
                    fromTyped = if (!fromActual && fromSp.selectedItemPosition == customIndex) {
                        fromCustom.text.toString()
                    } else {
                        null
                    },
                    toTyped = if (!toActual && toSp.selectedItemPosition == customIndex) {
                        toCustom.text.toString()
                    } else {
                        null
                    },
                )
                showResult(result)
                val trimmed = typed.trim()
                val incomplete = trimmed.endsWith('.') ||
                    trimmed.endsWith("e", ignoreCase = true) ||
                    trimmed.endsWith('+') ||
                    trimmed.endsWith('-')
                if (commit && !incomplete) commitHistory(result)
            } catch (e: ScaleCal.Error) {
                showError(e.message ?: "Check the values and try again.")
            }
        }

        private fun restore(entry: ScaleHistoryEntry) {
            suppress = true
            modeSp.setSelection(entry.modeIndex)
            fromSp.setSelection(entry.fromIndex)
            toSp.setSelection(entry.toIndex)
            fromCustom.setText(entry.fromCustom)
            toCustom.setText(entry.toCustom)
            unitSp.setSelection(entry.unitIndex)
            decimals = entry.decimals
            valueIn.setText(entry.measurement)
            syncMode()
            paintDecimals()
            suppress = false
            recalc(commit = false)
        }

        private fun resetForm() {
            suppress = true
            valueIn.setText("")
            fromCustom.setText("")
            toCustom.setText("")
            modeSp.setSelection(modes.indexOf(ScaleCal.Mode.SCALE_TO_SCALE))
            fromSp.setSelection(3)
            toSp.setSelection(5)
            unitSp.setSelection(0)
            decimals = 2
            valueIn.clearFocus()
            fromCustom.clearFocus()
            toCustom.clearFocus()
            syncMode()
            paintDecimals()
            suppress = false
            recalc(commit = false)
        }

        private fun swapScales() {
            val mode = currentMode()
            suppress = true
            when (mode) {
                ScaleCal.Mode.SCALE_TO_SCALE -> {
                    val fromIndex = fromSp.selectedItemPosition
                    val fromText = fromCustom.text.toString()
                    fromSp.setSelection(toSp.selectedItemPosition)
                    fromCustom.setText(toCustom.text.toString())
                    toSp.setSelection(fromIndex)
                    toCustom.setText(fromText)
                }
                ScaleCal.Mode.DRAWING_TO_ACTUAL -> {
                    toSp.setSelection(fromSp.selectedItemPosition)
                    toCustom.setText(fromCustom.text.toString())
                    modeSp.setSelection(modes.indexOf(ScaleCal.Mode.ACTUAL_TO_DRAWING))
                }
                ScaleCal.Mode.ACTUAL_TO_DRAWING -> {
                    fromSp.setSelection(toSp.selectedItemPosition)
                    fromCustom.setText(toCustom.text.toString())
                    modeSp.setSelection(modes.indexOf(ScaleCal.Mode.DRAWING_TO_ACTUAL))
                }
            }
            syncMode()
            suppress = false
            recalc(commit = true)
        }
    }
}
