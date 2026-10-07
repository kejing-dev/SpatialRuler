package io.embodit.spatialruler.data

import io.embodit.spatialruler.data.local.SessionJsonCodec
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionJsonCodecTest {

    private val session =
        StoredSession(
            pins =
                listOf(
                    StoredPin("p1", "6f1c0a52-0001", Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), 2, -3, SurfaceKind.FLOOR, "FLOOR:934405ca", Grade.FINE, SnapKind.CORNER, false, 1.25f),
                    StoredPin("p4", "6f1c0a52-0004", Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), 0, 0, SurfaceKind.WALL, "WALL:334b445c", Grade.COARSE, SnapKind.NONE, true, 3.5f),
                ),
            tapes = listOf(Tape("t2", "p1", "p4")),
            pinSeq = 4,
            tapeSeq = 2,
            settings = RulerSettings(unit = RulerUnit.FT_IN, language = LanguagePref.ZH, snap = false, coachSeen = true),
        )

    @Test
    fun session_survivesARoundTrip() {
        assertEquals(session, SessionJsonCodec.decode(SessionJsonCodec.encode(session)))
    }

    @Test
    fun missingFields_fallBackToDefaults_andPinsWithoutAnAnchorAreDropped() {
        val decoded = SessionJsonCodec.decode("""{"pins":[{"id":"p1","anchor":"A"},{"id":"p2"}],"tapes":[{"id":"t1","a":"p1","b":"p2"}]}""")
        assertEquals(listOf("p1"), decoded.pins.map { it.id })
        val pin = decoded.pins.single()
        assertEquals(Vec3.UP, pin.normalLocal)
        assertEquals(Grade.COARSE, pin.grade)
        assertEquals(SurfaceKind.OTHER, pin.surface)
        assertEquals(1, decoded.pinSeq)
        assertEquals(RulerSettings(), decoded.settings)
        assertEquals(1, decoded.tapes.size)
    }

    @Test
    fun unknownEnumValues_doNotBreakDecoding() {
        val decoded = SessionJsonCodec.decode("""{"unit":"furlong","language":"xx","pins":[{"id":"p1","anchor":"A","grade":"psychic","surface":"MOON","snapped":"??"}]}""")
        assertEquals(RulerUnit.M, decoded.settings.unit)
        assertEquals(LanguagePref.SYSTEM, decoded.settings.language)
        assertEquals(Grade.COARSE, decoded.pins.single().grade)
        assertEquals(SnapKind.NONE, decoded.pins.single().snapped)
        assertTrue(decoded.tapes.isEmpty())
    }
}
