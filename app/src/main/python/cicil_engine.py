"""CivilConvert engine.

Runs inside the Android app through Chaquopy. Pure Python, no third-party deps.
Every public function returns a JSON string so the Kotlin side only has to
parse JSON (no PyObject juggling).
"""
import json
import math
import re
import urllib.error
import urllib.request

# --------------------------------------------------------------------------
# Unit tables:  category -> [(key, label, factor_to_base_unit)]
# --------------------------------------------------------------------------
UNITS = {
    "Length": [
        ("m", "Meter (m)", 1.0),
        ("km", "Kilometer (km)", 1000.0),
        ("cm", "Centimeter (cm)", 0.01),
        ("mm", "Millimeter (mm)", 0.001),
        ("mi", "Mile (mi)", 1609.344),
        ("yd", "Yard (yd)", 0.9144),
        ("ft", "Foot (ft)", 0.3048),
        ("in", "Inch (in)", 0.0254),
        ("nmi", "Nautical mile (nmi)", 1852.0),
        ("vara", "Vara (Spanish/PH)", 0.835905),
        ("talampakan", "Talampakan (ft)", 0.3048),
        ("pulgada", "Pulgada (in)", 0.0254),
    ],
    "Area": [
        ("m2", "Square meter (m²)", 1.0),
        ("km2", "Square kilometer (km²)", 1e6),
        ("ha", "Hectare (ha)", 10000.0),
        ("are", "Are (a)", 100.0),
        ("cm2", "Square centimeter (cm²)", 1e-4),
        ("ft2", "Square foot (ft²)", 0.09290304),
        ("in2", "Square inch (in²)", 0.00064516),
        ("yd2", "Square yard (yd²)", 0.83612736),
        ("acre", "Acre", 4046.8564224),
        ("mi2", "Square mile (mi²)", 2589988.110336),
        ("sq_vara", "Square vara (sq vara)", 0.698737),
        ("tsubo", "Tsubo / Pyeong (坪)", 3.305785),
    ],
    "Volume": [
        ("m3", "Cubic meter (m³)", 1.0),
        ("L", "Liter (L)", 0.001),
        ("mL", "Milliliter (mL)", 1e-6),
        ("cm3", "Cubic centimeter (cm³)", 1e-6),
        ("ft3", "Cubic foot (ft³)", 0.028316846592),
        ("in3", "Cubic inch (in³)", 1.6387064e-5),
        ("yd3", "Cubic yard (yd³)", 0.764554857984),
        ("gal", "US gallon (gal)", 0.003785411784),
        ("qt", "US quart (qt)", 0.000946352946),
        ("pt", "US pint (pt)", 0.000473176473),
        ("cup", "US cup", 0.0002365882365),
        ("floz", "US fluid ounce (fl oz)", 2.95735295625e-5),
        ("tbsp", "Tablespoon (US)", 1.478676478125e-5),
        ("tsp", "Teaspoon (US)", 4.92892159375e-6),
        ("ganta", "Ganta (3 L)", 0.003),
    ],
    "Mass": [
        ("kg", "Kilogram (kg)", 1.0),
        ("g", "Gram (g)", 0.001),
        ("mg", "Milligram (mg)", 1e-6),
        ("t", "Metric ton (t)", 1000.0),
        ("lb", "Pound (lb)", 0.45359237),
        ("oz", "Ounce (oz)", 0.028349523125),
        ("st", "Stone (st)", 6.35029318),
    ],
    "Temperature": [
        ("C", "Celsius (°C)", 1.0),
        ("F", "Fahrenheit (°F)", 1.0),
        ("K", "Kelvin (K)", 1.0),
    ],
    "Speed": [
        ("mps", "Meter/second (m/s)", 1.0),
        ("kph", "Kilometer/hour (km/h)", 1 / 3.6),
        ("mph", "Mile/hour (mph)", 0.44704),
        ("kn", "Knot (kn)", 1852 / 3600),
        ("fps", "Foot/second (ft/s)", 0.3048),
    ],
    "Time": [
        ("s", "Second (s)", 1.0),
        ("ms", "Millisecond (ms)", 0.001),
        ("min", "Minute (min)", 60.0),
        ("h", "Hour (h)", 3600.0),
        ("day", "Day", 86400.0),
        ("week", "Week", 604800.0),
        ("year", "Year (365.25 d)", 31557600.0),
    ],
    "Data": [
        ("B", "Byte (B)", 1.0),
        ("bit", "Bit", 0.125),
        ("KB", "Kilobyte (KB)", 1e3),
        ("MB", "Megabyte (MB)", 1e6),
        ("GB", "Gigabyte (GB)", 1e9),
        ("TB", "Terabyte (TB)", 1e12),
        ("KiB", "Kibibyte (KiB)", 1024.0),
        ("MiB", "Mebibyte (MiB)", 1024.0 ** 2),
        ("GiB", "Gibibyte (GiB)", 1024.0 ** 3),
    ],
    "Pressure": [
        ("Pa", "Pascal (Pa)", 1.0),
        ("kPa", "Kilopascal (kPa)", 1000.0),
        ("bar", "Bar", 1e5),
        ("atm", "Atmosphere (atm)", 101325.0),
        ("psi", "PSI", 6894.757293168),
        ("mmHg", "mmHg", 133.322387415),
    ],
    "Energy": [
        ("J", "Joule (J)", 1.0),
        ("kJ", "Kilojoule (kJ)", 1000.0),
        ("cal", "Calorie (cal)", 4.184),
        ("kcal", "Kilocalorie (kcal)", 4184.0),
        ("Wh", "Watt-hour (Wh)", 3600.0),
        ("kWh", "Kilowatt-hour (kWh)", 3.6e6),
        ("BTU", "BTU", 1055.05585262),
    ],
    "Angle": [
        ("deg", "Degree (°)", 1.0),
        ("rad", "Radian (rad)", 180 / math.pi),
        ("grad", "Gradian (grad)", 0.9),
    ],
}

_F = {cat: {k: f for k, _l, f in rows} for cat, rows in UNITS.items()}


def _f(x):
    """Format a number for display."""
    if x == 0:
        return "0"
    ax = abs(x)
    if ax >= 1e12 or ax < 1e-6:
        return f"{x:.6e}"
    if ax >= 1e5:
        s = f"{x:,.4f}"
        if "." in s:
            s = s.rstrip("0").rstrip(".")
        return s
    s = f"{x:.8g}"
    if "." in s and "e" not in s:
        s = s.rstrip("0").rstrip(".")
    return s


# --------------------------------------------------------------------------
# Plain unit conversion
# --------------------------------------------------------------------------
def list_categories():
    return json.dumps(
        {c: [{"key": k, "label": l} for k, l, _f_ in rows] for c, rows in UNITS.items()}
    )


def _to_celsius(v, u):
    return {"C": v, "F": (v - 32) * 5 / 9, "K": v - 273.15}[u]


def _from_celsius(c, u):
    return {"C": c, "F": c * 9 / 5 + 32, "K": c + 273.15}[u]


def convert(category, value, frm, to):
    try:
        v = float(value)
        if category == "Temperature":
            c = _to_celsius(v, frm)
            r = _from_celsius(c, to)
            steps = [f"Convert {frm} to °C first: {_f(c)} °C", f"Then °C to {to}: {_f(r)} {to}"]
        else:
            table = _F[category]
            ratio = table[frm] / table[to]
            r = v * ratio
            steps = [
                f"1 {frm} = {_f(ratio)} {to}",
                f"{_f(v)} × {_f(ratio)} = {_f(r)} {to}",
            ]
        return json.dumps(
            {"ok": True, "result": r, "text": f"{_f(v)} {frm} = {_f(r)} {to}", "steps": steps}
        )
    except (ValueError, KeyError) as e:
        return json.dumps({"ok": False, "error": f"Cannot convert: {e}"})


# --------------------------------------------------------------------------
# Shapes (area and volume)
# --------------------------------------------------------------------------
SHAPES = {
    "rectangle": ("area", "Rectangle", [("l", "Length"), ("w", "Width")]),
    "square": ("area", "Square", [("s", "Side")]),
    "triangle": ("area", "Triangle (base & height)", [("b", "Base"), ("h", "Height")]),
    "triangle3": ("area", "Triangle (3 sides)", [("a", "Side a"), ("b", "Side b"), ("c", "Side c")]),
    "circle": ("area", "Circle", [("r", "Radius")]),
    "ellipse": ("area", "Ellipse", [("a", "Semi-axis a"), ("b", "Semi-axis b")]),
    "trapezoid": ("area", "Trapezoid (isosceles)", [("a", "Bottom base"), ("b", "Top base"), ("h", "Height")]),
    "parallelogram": ("area", "Parallelogram", [("b", "Base"), ("h", "Height"), ("s", "Slanted side")]),
    "sector": ("area", "Circle sector", [("r", "Radius"), ("deg", "Angle (degrees)")]),
    "ring": ("area", "Ring / Annulus", [("R", "Outer radius"), ("r", "Inner radius")]),
    "regpoly": ("area", "Regular polygon", [("n", "Number of sides"), ("s", "Side length")]),
    "box": ("volume", "Box / Rectangular prism", [("l", "Length"), ("w", "Width"), ("h", "Height")]),
    "cube": ("volume", "Cube", [("s", "Side")]),
    "cylinder": ("volume", "Cylinder", [("r", "Radius"), ("h", "Height")]),
    "sphere": ("volume", "Sphere", [("r", "Radius")]),
    "hemisphere": ("volume", "Hemisphere", [("r", "Radius")]),
    "cone": ("volume", "Cone", [("r", "Radius"), ("h", "Height")]),
    "pyramid": ("volume", "Pyramid (rectangular base)", [("l", "Base length"), ("w", "Base width"), ("h", "Height")]),
    "prism_tri": ("volume", "Triangular prism", [("b", "Triangle base"), ("h", "Triangle height"), ("len", "Prism length")]),
}


def list_shapes():
    return json.dumps(
        [
            {"id": sid, "kind": k, "title": t, "params": [{"key": a, "label": b} for a, b in p]}
            for sid, (k, t, p) in SHAPES.items()
        ]
    )


def _lab(x, y, t, ox=0.0, oy=0.0):
    return {"x": x, "y": y, "t": t, "ox": ox, "oy": oy}


def _poly(pts, labels):
    return {"type": "polygon", "points": [list(p) for p in pts], "labels": labels}


def _shape_rectangle(v, u):
    l, w = v["l"], v["w"]
    return (
        l * w, "Perimeter", 2 * (l + w),
        [f"Area = length × width", f"= {_f(l)} × {_f(w)} = {_f(l * w)}",
         f"Perimeter = 2 × (l + w) = 2 × ({_f(l)} + {_f(w)}) = {_f(2 * (l + w))}"],
        _poly([(0, 0), (l, 0), (l, w), (0, w)],
              [_lab(l / 2, 0, f"{_f(l)} {u}", 0, 1), _lab(0, w / 2, f"{_f(w)} {u}", -1, 0)]),
    )


def _shape_square(v, u):
    s = v["s"]
    return (
        s * s, "Perimeter", 4 * s,
        ["Area = side²", f"= {_f(s)}² = {_f(s * s)}", f"Perimeter = 4 × side = {_f(4 * s)}"],
        _poly([(0, 0), (s, 0), (s, s), (0, s)],
              [_lab(s / 2, 0, f"{_f(s)} {u}", 0, 1), _lab(0, s / 2, f"{_f(s)} {u}", -1, 0)]),
    )


def _shape_triangle(v, u):
    b, h = v["b"], v["h"]
    return (
        0.5 * b * h, "Height", h,
        ["Area = ½ × base × height", f"= ½ × {_f(b)} × {_f(h)} = {_f(0.5 * b * h)}"],
        _poly([(0, 0), (b, 0), (b / 2, h)],
              [_lab(b / 2, 0, f"b = {_f(b)} {u}", 0, 1), _lab(b / 2, h / 2, f"h = {_f(h)} {u}", 1.2, 0)]),
    )


def _shape_triangle3(v, u):
    a, b, c = v["a"], v["b"], v["c"]
    if a + b <= c or a + c <= b or b + c <= a:
        raise ValueError("These three sides cannot form a triangle.")
    s = (a + b + c) / 2
    area = math.sqrt(s * (s - a) * (s - b) * (s - c))
    cx = (b * b + c * c - a * a) / (2 * c)
    cy = math.sqrt(max(b * b - cx * cx, 0))
    return (
        area, "Perimeter", a + b + c,
        ["Heron's formula: s = (a + b + c) / 2", f"s = {_f(s)}",
         "Area = √( s(s−a)(s−b)(s−c) )", f"= {_f(area)}"],
        _poly([(0, 0), (c, 0), (cx, cy)],
              [_lab(c / 2, 0, f"c = {_f(c)}", 0, 1),
               _lab((c + cx) / 2, cy / 2, f"a = {_f(a)}", 1, -0.3),
               _lab(cx / 2, cy / 2, f"b = {_f(b)}", -1, -0.3)]),
    )


def _shape_circle(v, u):
    r = v["r"]
    return (
        math.pi * r * r, "Circumference", 2 * math.pi * r,
        ["Area = π × r²", f"= π × {_f(r)}² = {_f(math.pi * r * r)}",
         f"Circumference = 2πr = {_f(2 * math.pi * r)}"],
        {"type": "circle", "r": r, "label": f"r = {_f(r)} {u}"},
    )


def _shape_ellipse(v, u):
    a, b = v["a"], v["b"]
    per = math.pi * (3 * (a + b) - math.sqrt((3 * a + b) * (a + 3 * b)))  # Ramanujan
    return (
        math.pi * a * b, "Perimeter (approx.)", per,
        ["Area = π × a × b", f"= π × {_f(a)} × {_f(b)} = {_f(math.pi * a * b)}",
         "Perimeter uses Ramanujan's approximation."],
        {"type": "ellipse", "a": a, "b": b, "label": f"a = {_f(a)} {u}, b = {_f(b)} {u}"},
    )


def _shape_trapezoid(v, u):
    a, b, h = v["a"], v["b"], v["h"]
    leg = math.sqrt(h * h + ((a - b) / 2) ** 2)
    off = (a - b) / 2
    return (
        (a + b) / 2 * h, "Perimeter (isosceles)", a + b + 2 * leg,
        ["Area = ½ × (a + b) × h", f"= ½ × ({_f(a)} + {_f(b)}) × {_f(h)} = {_f((a + b) / 2 * h)}",
         f"Each leg = √(h² + ((a−b)/2)²) = {_f(leg)}"],
        _poly([(0, 0), (a, 0), (a - off, h), (off, h)],
              [_lab(a / 2, 0, f"a = {_f(a)}", 0, 1), _lab(a / 2, h, f"b = {_f(b)}", 0, -1),
               _lab(a - off / 2, h / 2, f"h = {_f(h)}", 1.2, 0)]),
    )


def _shape_parallelogram(v, u):
    b, h, s = v["b"], v["h"], v["s"]
    if s < h:
        raise ValueError("The slanted side cannot be shorter than the height.")
    off = math.sqrt(s * s - h * h)
    return (
        b * h, "Perimeter", 2 * (b + s),
        ["Area = base × height", f"= {_f(b)} × {_f(h)} = {_f(b * h)}", f"Perimeter = 2(b + s) = {_f(2 * (b + s))}"],
        _poly([(0, 0), (b, 0), (b + off, h), (off, h)],
              [_lab(b / 2, 0, f"b = {_f(b)}", 0, 1), _lab(off / 2, h / 2, f"s = {_f(s)}", -1.2, 0)]),
    )


def _shape_sector(v, u):
    r, d = v["r"], v["deg"]
    if d > 360:
        raise ValueError("Angle must be 360° or less.")
    area = d / 360 * math.pi * r * r
    arc = d / 360 * 2 * math.pi * r
    return (
        area, "Perimeter (arc + 2r)", arc + 2 * r,
        ["Area = (θ / 360) × π × r²", f"= ({_f(d)} / 360) × π × {_f(r)}² = {_f(area)}",
         f"Arc length = (θ / 360) × 2πr = {_f(arc)}"],
        {"type": "sector", "r": r, "deg": d, "label": f"r = {_f(r)} {u}, θ = {_f(d)}°"},
    )


def _shape_ring(v, u):
    R, r = v["R"], v["r"]
    if r >= R:
        raise ValueError("Inner radius must be smaller than outer radius.")
    area = math.pi * (R * R - r * r)
    return (
        area, "Total edge length", 2 * math.pi * (R + r),
        ["Area = π × (R² − r²)", f"= π × ({_f(R)}² − {_f(r)}²) = {_f(area)}"],
        {"type": "ring", "R": R, "r": r, "label": f"R = {_f(R)} {u}, r = {_f(r)} {u}"},
    )


def _shape_regpoly(v, u):
    n, s = int(round(v["n"])), v["s"]
    if n < 3 or n > 50:
        raise ValueError("Number of sides must be from 3 to 50.")
    area = n * s * s / (4 * math.tan(math.pi / n))
    R = s / (2 * math.sin(math.pi / n))
    pts = [(R * math.cos(2 * math.pi * i / n), R * math.sin(2 * math.pi * i / n)) for i in range(n)]
    return (
        area, "Perimeter", n * s,
        [f"Area = n × s² / (4 × tan(π/n))", f"= {n} × {_f(s)}² / (4 × tan(π/{n})) = {_f(area)}"],
        _poly(pts, [_lab(pts[0][0], pts[0][1], f"s = {_f(s)} {u}", 1.2, 0)]),
    )


def _shape_box(v, u):
    l, w, h = v["l"], v["w"], v["h"]
    d = w * 0.5
    return (
        l * w * h, "Surface area", 2 * (l * w + l * h + w * h),
        ["Volume = length × width × height", f"= {_f(l)} × {_f(w)} × {_f(h)} = {_f(l * w * h)}",
         f"Surface area = 2(lw + lh + wh) = {_f(2 * (l * w + l * h + w * h))}"],
        {"type": "extrude", "points": [[0, 0], [l, 0], [l, h], [0, h]], "depth": w,
         "labels": [_lab(l / 2, 0, f"l = {_f(l)}", 0, 1), _lab(0, h / 2, f"h = {_f(h)}", -1, 0),
                    _lab(l + d / 2, h + d / 2 - 0, f"w = {_f(w)}", 0.8, -0.8)]},
    )


def _shape_cube(v, u):
    s = v["s"]
    return (
        s ** 3, "Surface area", 6 * s * s,
        ["Volume = side³", f"= {_f(s)}³ = {_f(s ** 3)}", f"Surface area = 6 × side² = {_f(6 * s * s)}"],
        {"type": "extrude", "points": [[0, 0], [s, 0], [s, s], [0, s]], "depth": s,
         "labels": [_lab(s / 2, 0, f"s = {_f(s)}", 0, 1)]},
    )


def _shape_cylinder(v, u):
    r, h = v["r"], v["h"]
    return (
        math.pi * r * r * h, "Surface area", 2 * math.pi * r * (r + h),
        ["Volume = π × r² × h", f"= π × {_f(r)}² × {_f(h)} = {_f(math.pi * r * r * h)}",
         f"Surface area = 2πr(r + h) = {_f(2 * math.pi * r * (r + h))}"],
        {"type": "cylinder", "r": r, "h": h, "label": f"r = {_f(r)} {u}, h = {_f(h)} {u}"},
    )


def _shape_sphere(v, u):
    r = v["r"]
    return (
        4 / 3 * math.pi * r ** 3, "Surface area", 4 * math.pi * r * r,
        ["Volume = (4/3) × π × r³", f"= (4/3) × π × {_f(r)}³ = {_f(4 / 3 * math.pi * r ** 3)}",
         f"Surface area = 4πr² = {_f(4 * math.pi * r * r)}"],
        {"type": "sphere", "r": r, "label": f"r = {_f(r)} {u}"},
    )


def _shape_hemisphere(v, u):
    r = v["r"]
    return (
        2 / 3 * math.pi * r ** 3, "Surface area (curved + flat)", 3 * math.pi * r * r,
        ["Volume = (2/3) × π × r³", f"= (2/3) × π × {_f(r)}³ = {_f(2 / 3 * math.pi * r ** 3)}",
         f"Surface area = 3πr² = {_f(3 * math.pi * r * r)}"],
        {"type": "hemisphere", "r": r, "label": f"r = {_f(r)} {u}"},
    )


def _shape_cone(v, u):
    r, h = v["r"], v["h"]
    sl = math.sqrt(r * r + h * h)
    return (
        math.pi * r * r * h / 3, "Surface area", math.pi * r * (r + sl),
        ["Volume = (1/3) × π × r² × h", f"= (1/3) × π × {_f(r)}² × {_f(h)} = {_f(math.pi * r * r * h / 3)}",
         f"Slant height = √(r² + h²) = {_f(sl)}", f"Surface area = πr(r + slant) = {_f(math.pi * r * (r + sl))}"],
        {"type": "cone", "r": r, "h": h, "label": f"r = {_f(r)} {u}, h = {_f(h)} {u}"},
    )


def _shape_pyramid(v, u):
    l, w, h = v["l"], v["w"], v["h"]
    sa = l * w + l * math.sqrt(h * h + (w / 2) ** 2) + w * math.sqrt(h * h + (l / 2) ** 2)
    return (
        l * w * h / 3, "Surface area", sa,
        ["Volume = (1/3) × base area × height", f"= (1/3) × {_f(l)} × {_f(w)} × {_f(h)} = {_f(l * w * h / 3)}",
         f"Surface area = base + 4 triangular faces = {_f(sa)}"],
        {"type": "pyramid", "l": l, "w": w, "h": h, "label": f"{_f(l)} × {_f(w)} base, h = {_f(h)} {u}"},
    )


def _shape_prism_tri(v, u):
    b, h, ln = v["b"], v["h"], v["len"]
    hyp = math.sqrt((b / 2) ** 2 + h * h)  # isosceles triangle assumed
    sa = b * h + (b + 2 * hyp) * ln
    return (
        0.5 * b * h * ln, "Surface area (isosceles triangle)", sa,
        ["Volume = ½ × b × h × length", f"= ½ × {_f(b)} × {_f(h)} × {_f(ln)} = {_f(0.5 * b * h * ln)}",
         f"Surface area = 2 triangles + 3 rectangles = {_f(sa)}"],
        {"type": "extrude", "points": [[0, 0], [b, 0], [b / 2, h]], "depth": ln,
         "labels": [_lab(b / 2, 0, f"b = {_f(b)}", 0, 1), _lab(b / 2, h / 2, f"h = {_f(h)}", -1.2, 0)]},
    )


def _area_equivs(a_m2):
    keys = [("m2", "m²"), ("ha", "hectare"), ("ft2", "ft²"), ("yd2", "yd²"), ("acre", "acre")]
    return [{"label": lab, "text": _f(a_m2 / _F["Area"][k])} for k, lab in keys]


def _vol_equivs(v_m3):
    keys = [("m3", "m³"), ("L", "liters"), ("ft3", "ft³"), ("gal", "US gallons"), ("in3", "in³")]
    return [{"label": lab, "text": _f(v_m3 / _F["Volume"][k])} for k, lab in keys]


def calc_shape(shape_id, values_json, unit):
    try:
        kind, title, params = SHAPES[shape_id]
        raw = json.loads(values_json)
        vals = {}
        for key, label in params:
            if key not in raw or str(raw[key]).strip() == "":
                raise ValueError(f"Please enter: {label}")
            vals[key] = float(raw[key])
            if vals[key] <= 0:
                raise ValueError(f"{label} must be greater than zero.")
        f = _F["Length"][unit]
        value, sec_name, sec_val, steps, draw = globals()[f"_shape_{shape_id}"](vals, unit)
        exp = 2 if kind == "area" else 3
        sup = "²" if kind == "area" else "³"
        base = value * f ** exp
        return json.dumps({
            "ok": True, "kind": kind, "title": title,
            "value": value, "value_text": f"{_f(value)} {unit}{sup}",
            "secondary": {"name": sec_name, "text": f"{_f(sec_val)} {unit}" + ("²" if "Surface" in sec_name else "")},
            "equivalents": _area_equivs(base) if kind == "area" else _vol_equivs(base),
            "steps": steps, "warnings": [], "draw": draw,
        })
    except (ValueError, KeyError) as e:
        return json.dumps({"ok": False, "error": str(e)})


# --------------------------------------------------------------------------
# Irregular lots: coordinates or bearings (metes and bounds)
# --------------------------------------------------------------------------
_TOKEN = re.compile(r"(?<![A-Za-z])[NSEWnsew](?![A-Za-z])|\d+(?:\.\d+)?")


def _parse_bearing_line(line):
    cleaned = re.sub(r"[°′″'\"\-,;:]", " ", line)
    t = _TOKEN.findall(cleaned)
    if len(t) == 2 and t[0].upper() in "NSEW" and t[0].upper() != "" and not t[1].upper() in "NSEW":
        d = float(t[1])
        dx, dy = {"N": (0, d), "S": (0, -d), "E": (d, 0), "W": (-d, 0)}[t[0].upper()]
        return dx, dy, d
    if len(t) >= 4 and t[0].upper() in "NS" and t[-2].upper() in "EW":
        nums = [float(x) for x in t[1:-2]]
        if not 1 <= len(nums) <= 3:
            raise ValueError(f"Cannot read bearing: '{line}'")
        ang = nums[0] + (nums[1] / 60 if len(nums) > 1 else 0) + (nums[2] / 3600 if len(nums) > 2 else 0)
        if ang > 90:
            raise ValueError(f"Bearing angle over 90°: '{line}'")
        d = float(t[-1])
        a = math.radians(ang)
        dx = d * math.sin(a) * (1 if t[-2].upper() == "E" else -1)
        dy = d * math.cos(a) * (1 if t[0].upper() == "N" else -1)
        return dx, dy, d
    raise ValueError(f"Cannot read bearing: '{line}'. Example: N 45-30 E 25.00")


def _seg_intersect(p1, p2, p3, p4):
    def ccw(a, b, c):
        return (c[1] - a[1]) * (b[0] - a[0]) - (b[1] - a[1]) * (c[0] - a[0])
    d1, d2 = ccw(p3, p4, p1), ccw(p3, p4, p2)
    d3, d4 = ccw(p1, p2, p3), ccw(p1, p2, p4)
    return d1 * d2 < -1e-12 and d3 * d4 < -1e-12


def lot_area(mode, text, unit):
    try:
        lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
        warnings, steps = [], []
        if mode == "bearings":
            if len(lines) < 3:
                raise ValueError("Enter at least 3 courses (one bearing and distance per line).")
            pts, x, y, per_closed = [(0.0, 0.0)], 0.0, 0.0, 0.0
            for ln in lines:
                dx, dy, d = _parse_bearing_line(ln)
                x, y = x + dx, y + dy
                per_closed += d
                pts.append((x, y))
            mis = math.hypot(x, y)
            steps.append(f"Traverse of {len(lines)} courses, total length {_f(per_closed)} {unit}.")
            if mis > 1e-6 * max(per_closed, 1):
                ratio = per_closed / mis
                steps.append(f"Misclosure = {_f(mis)} {unit}  (precision about 1 : {_f(ratio)}).")
                warnings.append(f"The courses do not close; gap of {_f(mis)} {unit}. "
                                "Area assumes a straight line back to the start. Check your bearings.")
            else:
                pts.pop()  # last point equals the start
                steps.append("The traverse closes (misclosure is practically zero).")
        else:
            pts = []
            for ln in lines:
                nums = re.findall(r"-?\d+(?:\.\d+)?", ln)
                if len(nums) < 2:
                    raise ValueError(f"Cannot read coordinate: '{ln}'. Example: 12.5, 30")
                pts.append((float(nums[0]), float(nums[1])))
            if len(pts) > 1 and pts[0] == pts[-1]:
                pts.pop()
            if len(pts) < 3:
                raise ValueError("Enter at least 3 corner points (x, y per line).")
            steps.append(f"{len(pts)} corner points entered as (x, y) in {unit}.")

        n = len(pts)
        s = sum(pts[i][0] * pts[(i + 1) % n][1] - pts[(i + 1) % n][0] * pts[i][1] for i in range(n))
        area = abs(s) / 2
        edges = [math.dist(pts[i], pts[(i + 1) % n]) for i in range(n)]
        perimeter = sum(edges)
        steps += ["Shoelace formula: A = ½ × | Σ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ) |",
                  f"Σ = {_f(s)}  →  A = {_f(area)} {unit}²"]

        for i in range(n):
            for j in range(i + 2, n):
                if i == 0 and j == n - 1:
                    continue
                if _seg_intersect(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n]):
                    warnings.append("The boundary crosses itself. The area may not be what you expect.")
                    break
            else:
                continue
            break

        if area < 1e-12:
            if any("crosses" in w for w in warnings):
                raise ValueError("The boundary crosses itself and the area cancels out to zero. "
                                 "Check the order of your points.")
            raise ValueError("These points give zero area (all on one line?).")

        ccw = s > 0
        labels = []
        for i in range(n):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % n]
            dx, dy = x2 - x1, y2 - y1
            L = math.hypot(dx, dy) or 1
            nx, ny = (dy / L, -dx / L) if ccw else (-dy / L, dx / L)
            labels.append(_lab((x1 + x2) / 2, (y1 + y2) / 2, _f(edges[i]), nx * 1.3, -ny * 1.3))
        for i in range(n):
            (xp, yp), (x0, y0), (xn, yn) = pts[i - 1], pts[i], pts[(i + 1) % n]
            def outn(a, b):
                ddx, ddy = b[0] - a[0], b[1] - a[1]
                L = math.hypot(ddx, ddy) or 1
                return (ddy / L, -ddx / L) if ccw else (-ddy / L, ddx / L)
            n1, n2 = outn((xp, yp), (x0, y0)), outn((x0, y0), (xn, yn))
            vx, vy = n1[0] + n2[0], n1[1] + n2[1]
            L = math.hypot(vx, vy) or 1
            labels.append(_lab(x0, y0, chr(65 + i % 26), vx / L * 1.2, -vy / L * 1.2))

        base_m2 = area * _F["Length"][unit] ** 2
        return json.dumps({
            "ok": True, "kind": "area", "title": "Lot area",
            "value": area, "value_text": f"{_f(area)} {unit}²",
            "secondary": {"name": "Perimeter", "text": f"{_f(perimeter)} {unit}"},
            "equivalents": _area_equivs(base_m2),
            "steps": steps, "warnings": warnings,
            "draw": {"type": "polygon", "points": [list(p) for p in pts], "labels": labels},
        })
    except (ValueError, KeyError) as e:
        return json.dumps({"ok": False, "error": str(e)})


# --------------------------------------------------------------------------
# AI & Smart Explanation Engine
# 1. Gemini API (if key provided by user)
# 2. Free Public AI (Pollinations.ai - No key required)
# 3. Built-in Smart Offline Explanation & SVG Engine (Zero internet / key needed)
# --------------------------------------------------------------------------

def _call_pollinations(system, prompt):
    try:
        url = "https://text.pollinations.ai/"
        payload = {
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": prompt}
            ],
            "model": "openai"
        }
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=body, method="POST", headers={
            "Content-Type": "application/json"
        })
        with urllib.request.urlopen(req, timeout=12) as r:
            return r.read().decode("utf-8").strip()
    except Exception:
        return ""


def _call_gemini(api_key, system, prompt, max_tokens):
    key = api_key.strip()
    models = ["gemini-1.5-flash-latest", "gemini-1.5-flash", "gemini-2.0-flash-exp", "gemini-1.5-pro-latest"]
    payload = {
        "system_instruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {"maxOutputTokens": max_tokens, "temperature": 0.7}
    }
    body = json.dumps(payload).encode("utf-8")
    last_err = ""
    for model in models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
        req = urllib.request.Request(url, data=body, method="POST", headers={"content-type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                data = json.loads(r.read().decode("utf-8"))
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    return "".join(p.get("text", "") for p in parts)
        except urllib.error.HTTPError as e:
            last_err = f"{e.code}"
            if e.code in (400, 401, 403):
                break
            continue
        except Exception as e:
            last_err = str(e)
            continue
    raise RuntimeError(last_err or "Gemini API unavailable")


def generate_offline_explanation(summary, language):
    is_taglish = "Filipino" in language or "Tagalog" in language
    lines = summary.strip().splitlines()
    title_line = lines[0] if lines else "Calculation Summary"
    
    # Extract numerical values from summary if present
    area_match = re.search(r"(\d+(?:\.\d+)?)\s*(m²|sq\.?\s*m|ha|ft²|acres?|sq\.?\s*vara)", summary, re.I)
    vol_match = re.search(r"(\d+(?:\.\d+)?)\s*(m³|L|liters?|ft³|gallons?|ganta)", summary, re.I)
    
    if area_match:
        val = float(area_match.group(1))
        unit = area_match.group(2)
        val_m2 = val
        if "ha" in unit.lower():
            val_m2 = val * 10000
        elif "ft" in unit.lower():
            val_m2 = val * 0.0929
            
        tiles_60 = math.ceil(val_m2 / 0.36 * 1.1)  # 60x60cm tiles with 10% waste
        tiles_30 = math.ceil(val_m2 / 0.09 * 1.1)  # 30x30cm tiles
        paint_liters = math.ceil(val_m2 / 10.0 * 2) # 2 coats

        if is_taglish:
            return (
                f"📊 Smart Explanation ({title_line}):\n\n"
                f"Ang kabuuang sukat ay humigit-kumulang {_f(val)} {unit} ({_f(val_m2)} m²).\n\n"
                f"💡 Practical Real-Life Tips:\n"
                f"• Paglalagay ng Tiles: Kakailanganin mo ng humigit-kumulang {tiles_60} piraso ng 60x60 cm tiles "
                f"(o {tiles_30} pcs ng 30x30 cm tiles), kasama na ang 10% dagdag para sa palit at cuts.\n"
                f"• Pintura (Wall Coverage): Kakailanganin ng halos {paint_liters} liters ng pintura para sa 2 coats.\n"
                f"• Lupa / Titled Lot: Ang 1 hectare ay katumbas ng 10,000 m². Laging suriin ang survey plan."
            )
        else:
            return (
                f"📊 Smart Explanation ({title_line}):\n\n"
                f"The total area is calculated as {_f(val)} {unit} ({_f(val_m2)} m²).\n\n"
                f"💡 Practical Real-Life Estimates:\n"
                f"• Flooring & Tiles: You will need approx {tiles_60} pieces of 60×60 cm floor tiles "
                f"(or {tiles_30} pcs of 30×30 cm tiles), including a 10% allowance for cuts and breakage.\n"
                f"• Wall Painting: Requires approx {paint_liters} liters of paint for 2 complete coats.\n"
                f"• Land Note: 1 hectare equals 10,000 m². Always verify with a licensed surveyor."
            )

    elif vol_match:
        val = float(vol_match.group(1))
        unit = vol_match.group(2)
        val_m3 = val
        if "L" in unit or "liter" in unit.lower():
            val_m3 = val / 1000.0
        elif "ft" in unit.lower():
            val_m3 = val * 0.0283
            
        cement_bags = math.ceil(val_m3 * 9.0)  # Class A concrete mix
        liters = val_m3 * 1000.0
        gallons = val_m3 * 264.172

        if is_taglish:
            return (
                f"📊 Smart Explanation ({title_line}):\n\n"
                f"Ang kabuuang volume ay {_f(val)} {unit} ({_f(val_m3)} m³).\n\n"
                f"💡 Practical Real-Life Tips:\n"
                f"• Para sa Konkreto (Class A Concrete Mix): Kakailanganin mo ng humigit-kumulang {cement_bags} sako ng semento (40kg) kasama ang buhangin at graba.\n"
                f"• Tubig / Tank Capacity: Kasya rito ang humigit-kumulang {_f(liters)} Litro ({_f(gallons)} US Gallons) ng tubig."
            )
        else:
            return (
                f"📊 Smart Explanation ({title_line}):\n\n"
                f"The calculated volume is {_f(val)} {unit} ({_f(val_m3)} m³).\n\n"
                f"💡 Practical Real-Life Estimates:\n"
                f"• Concrete Pouring (Class A Mix): Requires approx {cement_bags} bags (40kg) of cement plus proportional sand and gravel.\n"
                f"• Water / Liquid Storage: Holds approx {_f(liters)} Liters ({_f(gallons)} US Gallons)."
            )

    else:
        if is_taglish:
            return (
                f"📊 Smart Explanation:\n\n"
                f"{summary}\n\n"
                f"💡 Tips: Ang bawat conversion at formula ay nakabatay sa standard international metrics. "
                f"Laging magdagdag ng 5-10% allowance kapag bumibili ng materyales."
            )
        else:
            return (
                f"📊 Smart Explanation:\n\n"
                f"{summary}\n\n"
                f"💡 Practical Tip: Standard international metric definitions are applied. "
                f"Always add a 5–10% margin of error when purchasing physical construction materials."
            )


def generate_offline_svg(summary):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 260">'
        '<rect width="100%" height="100%" fill="#FFF8F1" rx="12"/>'
        '<line x1="40" y1="0" x2="40" y2="260" stroke="#FFE0B2" stroke-width="2"/>'
        '<line x1="360" y1="0" x2="360" y2="260" stroke="#FFE0B2" stroke-width="2"/>'
        '<g transform="translate(40, 20)">'
        '<polygon points="40,180 280,180 240,40 80,40" fill="#FFE0B2" stroke="#E65100" stroke-width="3" stroke-linejoin="round"/>'
        '<line x1="80" y1="40" x2="80" y2="180" stroke="#8D6E63" stroke-width="1.5" stroke-dasharray="4,4"/>'
        '<text x="68" y="115" fill="#8D6E63" font-size="12" font-family="sans-serif" text-anchor="end">h</text>'
        '<text x="160" y="200" fill="#3E2723" font-size="13" font-family="sans-serif" font-weight="bold" text-anchor="middle">Base (b)</text>'
        '<text x="160" y="30" fill="#3E2723" font-size="13" font-family="sans-serif" font-weight="bold" text-anchor="middle">Top (a)</text>'
        '<circle cx="40" cy="180" r="4" fill="#E65100"/>'
        '<circle cx="280" cy="180" r="4" fill="#E65100"/>'
        '<circle cx="240" cy="40" r="4" fill="#E65100"/>'
        '<circle cx="80" cy="40" r="4" fill="#E65100"/>'
        '</g>'
        '<text x="200" y="242" fill="#8D6E63" font-size="12" font-family="sans-serif" text-anchor="middle">Vector geometry diagram</text>'
        '</svg>'
    )


def explain(api_key, summary, language):
    system = (
        "You are a friendly measurement and geometry tutor inside a unit-converter app used in the "
        "Philippines. Explain clearly and simply, in short paragraphs, plain text only (no markdown "
        "symbols, no tables). Show why the formula works and give one practical real-life tip "
        "(for example lots, tiles, concrete, water tanks). Keep it under 180 words. "
        f"Reply in: {language}."
    )
    # 1. If key provided, try Gemini API
    if api_key.strip():
        try:
            return json.dumps({"ok": True, "text": _call_gemini(api_key, system, summary, 700).strip()})
        except Exception:
            pass

    # 2. Try Free Public AI (Pollinations.ai - No key required)
    try:
        raw = _call_pollinations(system, summary)
        if raw and len(raw) > 20:
            return json.dumps({"ok": True, "text": raw.strip()})
    except Exception:
        pass

    # 3. Built-in Smart Offline Engine (Instant, no internet/key needed)
    return json.dumps({"ok": True, "text": generate_offline_explanation(summary, language)})


def sanitize_svg(text):
    m = re.search(r"<svg\b.*</svg>", text, re.S | re.I)
    if not m:
        raise ValueError("The AI did not return a drawing. Please try again.")
    svg = m.group(0)
    svg = re.sub(r"<script\b.*?</script>", "", svg, flags=re.S | re.I)
    svg = re.sub(r"<(foreignObject|image|iframe|use)\b.*?(</\1>|/>)", "", svg, flags=re.S | re.I)
    svg = re.sub(r"\son\w+\s*=\s*(\"[^\"]*\"|'[^']*')", "", svg, flags=re.I)
    svg = re.sub(r"(href|xlink:href)\s*=\s*(\"[^\"]*\"|'[^']*')", "", svg, flags=re.I)
    svg = re.sub(r"javascript:", "", svg, flags=re.I)
    return svg


def visualize_svg(api_key, summary):
    system = (
        "You draw clean educational diagrams. Reply with ONLY one <svg> element, no explanation, no "
        "code fences. Use viewBox=\"0 0 400 300\", white background rect, thin dark strokes, light "
        "blue fill, and label every given dimension with <text>. Draw to scale where possible. "
        "No scripts, no images, no external references."
    )
    # 1. If key provided, try Gemini API
    if api_key.strip():
        try:
            raw = _call_gemini(api_key, system, summary, 2500)
            return json.dumps({"ok": True, "svg": sanitize_svg(raw)})
        except Exception:
            pass

    # 2. Try Free Public AI (Pollinations.ai - No key required)
    try:
        raw = _call_pollinations(system, summary)
        if "<svg" in raw:
            return json.dumps({"ok": True, "svg": sanitize_svg(raw)})
    except Exception:
        pass

    # 3. Built-in Smart Vector SVG Generator (Instant, no internet/key needed)
    return json.dumps({"ok": True, "svg": generate_offline_svg(summary)})
