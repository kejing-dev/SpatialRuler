package io.embodit.spatialruler.platform

import android.util.Log
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.port.RulerLogger
import java.util.Locale

/**
 * One line per event under the tag `SpatialRuler`, with stable prefixes the tests grep for:
 * `STATE`, `PLANES`, `TAP`, `PIN`, `ANCHOR`, `TAPE`, `UNIT`, `LANG`, `RESTORE`, `UI`, ...
 * Positions are Stage metres with four decimals.
 */
object RulerLog : RulerLogger {
    const val TAG = "SpatialRuler"

    override fun log(line: String) {
        Log.i(TAG, line)
    }

    fun i(line: String) {
        Log.i(TAG, line)
    }

    fun w(line: String, error: Throwable? = null) {
        Log.w(TAG, line, error)
    }

    fun e(line: String, error: Throwable? = null) {
        Log.e(TAG, line, error)
    }

    /** `(x, y, z)` with four decimals. */
    fun pos(v: Vec3?): String =
        if (v == null) "null" else String.format(Locale.US, "(%.4f, %.4f, %.4f)", v.x, v.y, v.z)

    /** `(x, y, z)` with three decimals (pose lines of the test harness). */
    fun pos3(v: Vec3?): String =
        if (v == null) "null" else String.format(Locale.US, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z)

    /** `(x, y, z)` with five decimals (pose and panel lines of the test harness: 0.01 mm). */
    fun pos5(v: Vec3?): String =
        if (v == null) "null" else String.format(Locale.US, "(%.5f, %.5f, %.5f)", v.x, v.y, v.z)

    /** `q(x, y, z, w)` with five decimals. */
    fun quat5(q: Quat4?): String =
        if (q == null) "null" else String.format(Locale.US, "q(%.5f, %.5f, %.5f, %.5f)", q.x, q.y, q.z, q.w)

    /** `q(x, y, z, w)` with three decimals. */
    fun quat(q: Quat4?): String =
        if (q == null) "null" else String.format(Locale.US, "q(%.3f, %.3f, %.3f, %.3f)", q.x, q.y, q.z, q.w)
}
