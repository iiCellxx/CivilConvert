# 📐 CicilConvert — Universal Converter, Geometric & Scientific Engine for Android

**CicilConvert** is a modern, high-performance Android application combining unit conversions, 2D/3D geometry calculations, irregular lot area surveying (metes & bounds / Cartesian coordinates), a full scientific calculator parser, and instant AI explanations with SVG vector diagrams.

---

## 🛠️ Tech Stack & Technologies Used

### 📱 Android Frontend & UI
* **Language**: Kotlin (`1.9.24`)
* **SDK Version**: `compileSdk = 34`, `targetSdk = 34`, `minSdk = 24` (Android 7.0+)
* **UI Architecture**: Programmatic Android Material 3 Views (`com.google.android.material`), custom 2D/3D Canvas rendering (`ShapeView`), and `WebView` for inline SVG rendering.
* **Concurrency**: Kotlin Coroutines (`Dispatchers.IO` / `Dispatchers.Main`) with lifecycle-aware execution via `lifecycleScope`.
* **Edge-to-Edge**: Android 14/15 (`enableEdgeToEdge()` + `WindowInsetsCompat`) for seamless system bar insets.
* **Animations**: `ValueAnimator`, `ObjectAnimator`, `OvershootInterpolator`, `DecelerateInterpolator` for tab transitions, canvas reveals, and tactile button interactions.
* **Dependencies**:
  * `com.google.android.material:material:1.12.0` (Material 3 UI Components)
  * `androidx.appcompat:appcompat:1.7.0` (AppCompat Base Framework)
  * `androidx.core:core-ktx:1.13.1` (Kotlin Android Extensions)
  * `androidx.lifecycle:lifecycle-runtime-ktx:2.8.2` (Lifecycle KTX & `lifecycleScope`)
  * `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1` (Coroutines for Android)
  * `junit:junit:4.13.2` (Unit Testing)

### 🐍 Python Backend & Embedded Runtime
* **Chaquopy**: `com.chaquo.python` version `16.0.0` — Embedded Python 3.11 runtime executing directly inside the Android JVM process.
* **Python Engine**: Pure Python 3.11 (`cicil_engine.py`) with **zero 3rd-party pip dependencies** for maximum performance, security, and low memory footprint.
* **Python Standard Modules Used**:
  * `json`: High-speed serialization and deserialization between Kotlin & Python.
  * `math`: Trigonometry, hypotenuse, square roots, radians/degrees, Ramanujan ellipse perimeter, and geometric formulas.
  * `re`: Regular expressions for parsing survey bearings (`N 45-30-15 E 25.00`) and coordinate pairs.
  * `urllib.request` / `urllib.error`: Lightweight HTTP client for remote AI API calls.

### 🤖 3-Tier AI & Smart Explanation Engine
1. **Google Gemini API**: Support for `gemini-1.5-flash-latest`, `gemini-1.5-flash`, and `gemini-2.0-flash-exp` via Google AI Studio API keys.
2. **Pollinations.ai**: Free public AI API endpoint requiring **no API key**.
3. **Smart Built-in Offline Engine**: Pure Python offline intelligence generating real-world construction estimates (tile counts with 10% cut allowance, paint liters for 2 coats, Class A cement bags, water storage in liters and gallons) and vector SVG diagrams without internet.

---

## 📦 Package Imports Reference

### Kotlin Imports (`app/src/main/java/com/cicil/convert/`)

* **`MainActivity.kt`**:
  ```kotlin
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
  ```

* **`ShapeView.kt`**:
  ```kotlin
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
  ```

* **`SciCal.kt`**:
  ```kotlin
  import java.util.Locale
  import kotlin.math.*
  ```

* **`Engine.kt`**:
  ```kotlin
  import com.chaquo.python.Python
  import org.json.JSONArray
  import org.json.JSONObject
  ```

### Python Imports (`app/src/main/python/cicil_engine.py`)
```python
import json
import math
import re
import urllib.error
import urllib.request
```

---

## 🔄 System Architecture & Data Flow

```
+-----------------------------------------------------------------------------------+
|                            📱 User Input / Tab Switch                             |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                        MainActivity.kt (Material 3 UI)                            |
+-----------------------------------------------------------------------------------+
        |                                                 |
(Pure Kotlin Math Parser)                       (JSON Query via Chaquopy)
        v                                                 v
+------------------------------------+  +-------------------------------------------+
| SciCal.kt (Scientific Calculator)  |  |   Engine.kt (Kotlin <-> Python Bridge)    |
| • Recursive Descent Parser         |  +-------------------------------------------+
| • Implicit Multiplication          |                    |
| • Exponents (Right-Associative)    |                    v
| • Precision Snapping               |  +-------------------------------------------+
+------------------------------------+  |      cicil_engine.py (Python Engine)      |
                                        +-------------------------------------------+
                                                |                           |
                                      (Math & Shoelace)           (AI Request Handler)
                                                |                           |
                                                v                           v
                                        +---------------+  +------------------------+
                                        |  JSON Output  |  | 1. Google Gemini API   |
                                        +---------------+  | 2. Pollinations.ai     |
                                                |          | 3. Smart Offline       |
                                                |          +------------------------+
                                                |                           |
                                                +-------------+-------------+
                                                              |
                                                              v
+-----------------------------------------------------------------------------------+
|             ShapeView.kt (Preallocated Canvas)  &  WebView (SVG Diagram)          |
+-----------------------------------------------------------------------------------+
```

---

## ✨ Features & Functionality

### 1. 📐 Unit Converter
* **11 Categories**: Length, Area, Volume, Mass, Temperature, Speed, Time, Data, Pressure, Energy, Angle.
* **Regional & Philippine Units**:
  * *Length*: Vara (Spanish/PH - 0.835905 m), Talampakan (0.3048 m), Pulgada (0.0254 m).
  * *Area*: Square vara (0.698737 m²), Tsubo / Pyeong (3.305785 m²), Hectare (ha), Acre.
  * *Volume*: Ganta (3 Liters / 0.003 m³).
* **Instant Calculation**: Recalculates dynamically on text change with step-by-step ratio workings.
* **Swap Units Guard**: Safe one-tap unit swapping (`⇅ Swap Units`) with re-entrancy prevention.
* **One-Tap Copy**: "📋 Copy" button copies the formatted conversion directly to the clipboard.

### 2. 📦 2D & 3D Shape Engine
* **11 Area Shapes**: Rectangle, Square, Triangle (Base/Height & 3-Side Heron's), Circle, Ellipse, Isosceles Trapezoid, Parallelogram, Sector, Ring / Annulus, Regular Polygon.
* **8 Volume Solids**: Rectangular Prism / Box, Cube, Cylinder, Sphere, Hemisphere, Cone, Rectangular Pyramid, Triangular Prism.
* **Optimized Canvas Rendering**: `ShapeView` renders shape outlines, dimension labels, and dashed hidden edges. All `RectF` and `Path` objects are preallocated to eliminate GC churn during animations.

### 3. 🗺️ Irregular Lot Area Calculator
* Computes land area from **Survey Bearings & Distances** (`N 45-30 E 25.00`, `E 30`) or **Cartesian Coordinates** (`x, y`).
* Uses the **Shoelace Algorithm** (`Gauss Area Formula`) for closed polygonal area.
* Calculates traverse closure precision ratio ($1 : \text{ratio}$), misclosure gap distance, and automatic boundary self-intersection warnings.
* Provides corner coordinate labeling (A, B, C...) and boundary side lengths.

### 4. 🧮 Scientific Calculator (`SciCal.kt`)
* **Custom Recursive Descent Parser**: Parses and calculates expressions without `eval()`.
* **Smart Tokenizer**: Automatically injects implicit multiplication (`2(3+4)`, `(1+2)(3+4)`, `2pi`).
* **Trigonometry**: Sine, Cosine, Tangent, and inverses with single-tap **Degrees / Radians** switching.
* **Exponents & Factorials**: Right-associative exponentiation ($2^{3^2} = 512$) and bounded factorials ($n! \le 170!$).
* **Precision Snapping**: Eliminates IEEE 754 precision artifacts (e.g. $\sin(180^\circ) = 0$).

### 5. 📏 Scale Converter
* **Three conversion modes**: Drawing → actual, Actual → drawing, Scale → scale using $D₂ = D₁ × (S₁ / S₂)$.
* **Preset scales**: 1:10, 1:20, 1:25, 1:50, 1:75, 1:100, 1:150, 1:200, plus a **Custom** scale option (accepts `1:50`, `50`, `2:100`, `1/50`).
* **Units**: millimeters (mm), centimeters (cm), meters (m), inches (in).
* **Instant calculation**: Result updates automatically whenever input, scale, unit, or decimal places change.
* **Swap scales**: One-tap `⇅ Swap scales` to exchange original and target scales.
* **Decimal places**: Adjustable from 0 to 6 places with `−` / `+` controls.
* **Copy result**: One-tap `📋 Copy` to clipboard.
* **Conversion history**: Recent conversions saved and tappable to restore; history can be cleared.
* **Clear/Reset**: Resets all fields to defaults.
* **Input validation**: Error messages for negative, empty, non-numeric, or invalid scales/units.
* **AI explanation**: `✨ Explain AI` button generates a plain-language explanation of the conversion using Google Gemini API (with free Pollinations.ai and built-in offline fallbacks, 100% offline capable).
* **Example**: Converting 25 mm from scale 1:50 to scale 1:100 produces **12.50 mm**.

### 6. 💡 Smart AI Explanations & SVG Diagrams
* **English Explanations**: Generates clear, plain-text explanations and practical real-life civil construction estimations (tile counts, paint coats, concrete mix, liquid volume).
* **SVG Vector Visualizer**: Generates lightweight SVG diagrams rendered in an embedded, script-sanitized `WebView`.
* **Zero-Setup Offline Fallback**: Works 100% offline without API keys, with optional Google Gemini API support.

---

## 🧪 Automated Testing

CivilConvert includes a JUnit 4 test suite for validating mathematical correctness, operator precedence, implicit multiplication, and edge cases.

### Run Unit Tests via CLI:
```cmd
.\gradlew testDebugUnitTest
```

---

## 🏗️ How to Build the APK

### Method 1: Android Studio (GUI)
1. Open **Android Studio** (Jellyfish or newer with JDK 17).
2. Select **File > Open...** and pick the `CicilConvert` folder.
3. Allow Gradle to finish syncing (Chaquopy automatically sets up the Python 3.11 runtime).
4. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK Location: `app/build/outputs/apk/debug/app-debug.apk`.

### Method 2: Command Line (CLI)
```cmd
.\gradlew assembleDebug
```
Output: `app\build\outputs\apk\debug\app-debug.apk`

---

## 📁 Project Directory Structure

```
CicilConvert/
├── app/
│   ├── build.gradle.kts                # App SDK versions, Chaquopy config & dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # App permissions, theme & launcher activity
│       │   ├── java/com/cicil/convert/
│       │   │   ├── MainActivity.kt     # Programmatic Material 3 UI, coroutines & tabs
│       │   │   ├── ShapeView.kt        # Preallocated 2D/3D Canvas vector drawing view
│       │   │   ├── SciCal.kt           # Scientific calculator parser & tokenizer
│       │   │   └── Engine.kt           # Kotlin <-> Python Chaquopy JSON bridge
│       │   └── python/
│       │       └── cicil_engine.py     # Pure Python math engine, units & AI logic
│       └── test/java/com/cicil/convert/
│           └── SciCalTest.kt           # JUnit test suite for the math parser
├── build.gradle.kts                    # Top-level Gradle plugins
├── settings.gradle.kts                 # Subproject inclusion & repositories
└── README.md                           # Documentation & architecture breakdown
```
