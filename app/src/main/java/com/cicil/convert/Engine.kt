package com.cicil.convert

import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject

/** Thin bridge to app/src/main/python/cicil_engine.py (all functions return JSON strings). */
object Engine {
    private val module by lazy { Python.getInstance().getModule("cicil_engine") }

    fun obj(fn: String, vararg args: Any?): JSONObject =
        JSONObject(module.callAttr(fn, *args).toString())

    fun arr(fn: String, vararg args: Any?): JSONArray =
        JSONArray(module.callAttr(fn, *args).toString())
}
