package io.embodit.spatialruler.data.local

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.StoredPin
import io.embodit.spatialruler.domain.model.StoredSession
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Tape
import io.embodit.spatialruler.domain.model.Vec3
import org.json.JSONArray
import org.json.JSONObject

/** JSON form of [StoredSession]. Unknown or missing fields fall back to defaults. */
object SessionJsonCodec {
    private const val VERSION = 1

    fun encode(session: StoredSession): String {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("pinSeq", session.pinSeq)
        root.put("tapeSeq", session.tapeSeq)
        root.put("unit", session.settings.unit.tag)
        root.put("language", session.settings.language.tag)
        root.put("snap", session.settings.snap)
        root.put("coachSeen", session.settings.coachSeen)
        val pins = JSONArray()
        for (p in session.pins) {
            pins.put(
                JSONObject()
                    .put("id", p.id)
                    .put("anchor", p.anchorId)
                    .put("n", vec(p.normalLocal))
                    .put("u", vec(p.uLocal))
                    .put("w", vec(p.vLocal))
                    .put("du", p.offsetUMm)
                    .put("dv", p.offsetVMm)
                    .put("surface", p.surface.name)
                    .put("plane", p.planeLabel)
                    .put("grade", p.grade.tag)
                    .put("snapped", p.snapped.tag)
                    .put("far", p.far)
                    .put("pinch", p.pinch)
                    .put("range", p.rangeM.toDouble())
            )
        }
        root.put("pins", pins)
        val tapes = JSONArray()
        for (t in session.tapes) tapes.put(JSONObject().put("id", t.id).put("a", t.a).put("b", t.b))
        root.put("tapes", tapes)
        return root.toString()
    }

    fun decode(text: String): StoredSession {
        val root = JSONObject(text)
        val pins = ArrayList<StoredPin>()
        val pinArray = root.optJSONArray("pins") ?: JSONArray()
        for (i in 0 until pinArray.length()) {
            val o = pinArray.getJSONObject(i)
            val anchor = o.optString("anchor", "")
            if (anchor.isEmpty()) continue
            pins +=
                StoredPin(
                    id = o.getString("id"),
                    anchorId = anchor,
                    normalLocal = vec(o.optJSONArray("n"), Vec3.UP),
                    uLocal = vec(o.optJSONArray("u"), Vec3.RIGHT),
                    vLocal = vec(o.optJSONArray("w"), Vec3.AHEAD),
                    offsetUMm = o.optInt("du", 0),
                    offsetVMm = o.optInt("dv", 0),
                    surface = SurfaceKind.fromName(o.optString("surface", "")),
                    planeLabel = o.optString("plane", ""),
                    grade = Grade.fromTag(o.optString("grade", "")) ?: Grade.COARSE,
                    snapped = SnapKind.fromTag(o.optString("snapped", "")) ?: SnapKind.NONE,
                    far = o.optBoolean("far", false),
                    rangeM = o.optDouble("range", 0.0).toFloat(),
                    pinch = o.optBoolean("pinch", false),
                )
        }
        val tapes = ArrayList<Tape>()
        val tapeArray = root.optJSONArray("tapes") ?: JSONArray()
        for (i in 0 until tapeArray.length()) {
            val o = tapeArray.getJSONObject(i)
            tapes += Tape(o.getString("id"), o.getString("a"), o.getString("b"))
        }
        return StoredSession(
            pins = pins,
            tapes = tapes,
            pinSeq = root.optInt("pinSeq", pins.size),
            tapeSeq = root.optInt("tapeSeq", tapes.size),
            settings =
                RulerSettings(
                    unit = RulerUnit.fromTag(root.optString("unit", "")) ?: RulerUnit.M,
                    language = LanguagePref.fromTag(root.optString("language", "")) ?: LanguagePref.SYSTEM,
                    snap = root.optBoolean("snap", true),
                    coachSeen = root.optBoolean("coachSeen", false),
                ),
        )
    }

    private fun vec(v: Vec3): JSONArray = JSONArray().put(v.x.toDouble()).put(v.y.toDouble()).put(v.z.toDouble())

    private fun vec(a: JSONArray?, fallback: Vec3): Vec3 =
        if (a == null || a.length() < 3) fallback
        else Vec3(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())
}
