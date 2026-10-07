package io.embodit.spatialruler.domain.model

/** Display unit of every reading. [tag] is the stable name used in storage, logs and debug commands. */
enum class RulerUnit(val tag: String) {
    M("m"),
    CM("cm"),
    MM("mm"),
    FT_IN("ftin");

    val system: UnitSystem
        get() = if (this == FT_IN) UnitSystem.IMPERIAL else UnitSystem.METRIC

    companion object {
        fun fromTag(tag: String?): RulerUnit? = entries.firstOrNull { it.tag == tag }
    }
}

/** Which graduation is printed on the tape. */
enum class UnitSystem { METRIC, IMPERIAL }

enum class LanguagePref(val tag: String) {
    SYSTEM("system"),
    ZH("zh"),
    EN("en");

    companion object {
        fun fromTag(tag: String?): LanguagePref? = entries.firstOrNull { it.tag == tag }
    }
}

/** How a pin was placed: decides its share of the tolerance. */
enum class Grade(val tag: String, val glyph: String) {
    CONTACT("contact", "◆"),
    FINE("fine", "●"),
    COARSE("coarse", "○");

    companion object {
        fun fromTag(tag: String?): Grade? = entries.firstOrNull { it.tag == tag }
    }
}

/** What the placement point was snapped to. Only CORNER and EDGE stay on the pin after it is set. */
enum class SnapKind(val tag: String) {
    NONE("none"),
    PIN("pin"),
    CORNER("corner"),
    EDGE("edge"),
    PLUMB("plumb"),
    LEVEL("level");

    companion object {
        fun fromTag(tag: String?): SnapKind? = entries.firstOrNull { it.tag == tag }
    }
}

/** A pin is its spatial anchor: creation is asynchronous, so a pin is first PENDING. */
enum class SaveState(val tag: String) {
    PENDING("pending"),
    SAVED("saved"),
    UNSAVED("unsaved");

    companion object {
        fun fromTag(tag: String?): SaveState? = entries.firstOrNull { it.tag == tag }
    }
}

/** Semantic class of the surface a pin sits on (subset of the platform's plane semantics). */
enum class SurfaceKind {
    FLOOR,
    WALL,
    CEILING,
    TABLE,
    BED,
    CABINET,
    CHAIR,
    CURTAIN,
    WINDOW,
    DOOR,
    OTHER;

    companion object {
        /** Maps the platform's semantic label name; anything without its own copy string is OTHER. */
        fun fromSemanticName(name: String?): SurfaceKind =
            when (name) {
                "FLOOR" -> FLOOR
                "WALL", "VIRTUAL_WALL" -> WALL
                "CEILING" -> CEILING
                "TABLE" -> TABLE
                "BED" -> BED
                "CABINET" -> CABINET
                "CHAIR", "SOFA" -> CHAIR
                "CURTAIN" -> CURTAIN
                "WINDOW" -> WINDOW
                "DOOR" -> DOOR
                else -> OTHER
            }

        fun fromName(name: String?): SurfaceKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** The two nudge directions of a pin on its surface. */
enum class NudgeAxis { U, V }

/**
 * The seat a pin was snapped to when it landed, for the mark that is drawn for a moment at that time:
 * the two plane edges that leave a corner, the edge, or the direction towards the tape head (plumb /
 * level). Not stored: a restored pin has none.
 */
data class SeatMark(val kind: SnapKind, val a: Vec3?, val b: Vec3?)

/**
 * A measuring pin.
 *
 * Position model (design: "pin = anchor pose + local offset"): [base] / [baseRotation] are the current
 * pose of the pin's spatial anchor in Stage metres (or the tap point with identity rotation while the
 * anchor does not exist). The surface frame and the fine-tune offset are stored in the anchor's own
 * frame, so that they follow a rigid re-localisation of the anchor.
 */
data class Pin(
    val id: String,
    val anchorId: String?,
    val base: Vec3,
    val baseRotation: Quat4 = Quat4.IDENTITY,
    /** Surface normal, pointing away from the surface towards the side it was pinned from. */
    val normalLocal: Vec3,
    /** Nudge axis "left / right" along the surface. */
    val uLocal: Vec3,
    /** Nudge axis "near / far" (horizontal surface) or "up / down" (vertical surface). */
    val vLocal: Vec3,
    val offsetUMm: Int = 0,
    val offsetVMm: Int = 0,
    val surface: SurfaceKind = SurfaceKind.OTHER,
    /** `SEMANTIC:uuid8` of the plane it was set on; informational (logs, dump). */
    val planeLabel: String = "",
    val grade: Grade = Grade.COARSE,
    val snapped: SnapKind = SnapKind.NONE,
    val far: Boolean = false,
    /** Distance from the viewer to the pin when it was set or last trued, metres. */
    val rangeM: Float = 0f,
    /** A contact pin that was set with the pinch point of a hand (8 mm instead of 2 mm). */
    val pinch: Boolean = false,
    val saveState: SaveState = SaveState.PENDING,
    /** Stored pin whose anchor was not found in this space: hidden, kept in storage. */
    val elsewhere: Boolean = false,
    /** A retry of a failed save is under way (decides whether "Saved" is announced). */
    val retrying: Boolean = false,
    /** Its anchor is being re-created at its trued position (shown as "pinning"; the old anchor still holds it). */
    val rebasing: Boolean = false,
    /** Seat it landed on, shown once as a mark (see [SeatMark]). */
    val seat: SeatMark? = null,
) {
    val position: Vec3
        get() =
            base +
                baseRotation.rotate(
                    uLocal * (offsetUMm / 1000f) + vLocal * (offsetVMm / 1000f)
                )

    val normal: Vec3
        get() = baseRotation.rotate(normalLocal)

    val uAxis: Vec3
        get() = baseRotation.rotate(uLocal)

    val vAxis: Vec3
        get() = baseRotation.rotate(vLocal)

    /** A horizontal surface nudges "near / far", a vertical one "up / down". */
    val onHorizontalSurface: Boolean
        get() = kotlin.math.abs(normal.y) > 0.5f

    /** Readings that involve this pin carry a warning mark. */
    val warns: Boolean
        get() = far || saveState == SaveState.UNSAVED
}

/** A tape between two pins. The tape head is [a]; the reading is always computed from current positions. */
data class Tape(val id: String, val a: String, val b: String) {
    fun joins(p: String, q: String): Boolean = (a == p && b == q) || (a == q && b == p)

    fun touches(pinId: String): Boolean = a == pinId || b == pinId
}

/** Persisted user settings. */
data class RulerSettings(
    val unit: RulerUnit = RulerUnit.M,
    val language: LanguagePref = LanguagePref.SYSTEM,
    val snap: Boolean = true,
    val coachSeen: Boolean = false,
)

/** Pose of a spatial anchor in Stage space. */
data class AnchorPose(val position: Vec3, val rotation: Quat4 = Quat4.IDENTITY)

/** What survives a relaunch. */
data class StoredSession(
    val pins: List<StoredPin> = emptyList(),
    val tapes: List<Tape> = emptyList(),
    val pinSeq: Int = 0,
    val tapeSeq: Int = 0,
    val settings: RulerSettings = RulerSettings(),
)

data class StoredPin(
    val id: String,
    val anchorId: String,
    val normalLocal: Vec3,
    val uLocal: Vec3,
    val vLocal: Vec3,
    val offsetUMm: Int,
    val offsetVMm: Int,
    val surface: SurfaceKind,
    val planeLabel: String,
    val grade: Grade,
    val snapped: SnapKind,
    val far: Boolean,
    val rangeM: Float,
    val pinch: Boolean = false,
)
