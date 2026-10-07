package io.embodit.spatialruler.ui.ruler

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.data.repository.AnchorGateway
import io.embodit.spatialruler.data.repository.AnchorOutcome
import io.embodit.spatialruler.data.repository.AnchorRecord
import io.embodit.spatialruler.data.repository.SessionRepository
import io.embodit.spatialruler.domain.model.AnchorPose
import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.StoredSession
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.port.MeasureEnvironment
import io.embodit.spatialruler.domain.port.RulerLogger
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory storage. */
class FakeSessionRepository(var stored: StoredSession? = null) : SessionRepository {
    var saves = 0

    override suspend fun load(): StoredSession? = stored

    override suspend fun save(session: StoredSession) {
        stored = session
        saves++
    }
}

/** In-memory anchor store that survives a "relaunch" (a second ViewModel over the same instance). */
class FakeAnchorGateway : AnchorGateway {
    val store = LinkedHashMap<String, AnchorRecord>()
    var failNextCreates = 0
    var failNextLoads = 0
    var loads = 0
    private var seq = 0

    override val moves = MutableSharedFlow<AnchorRecord>(extraBufferCapacity = 8)

    override suspend fun create(position: Vec3, name: String): AnchorOutcome<AnchorRecord> {
        if (failNextCreates > 0) {
            failNextCreates--
            return AnchorOutcome.Failed(-6, "provider not ready")
        }
        val record = AnchorRecord("uuid-${++seq}", name, AnchorPose(position))
        store[record.id] = record
        return AnchorOutcome.Ok(record)
    }

    override suspend fun loadAll(): AnchorOutcome<List<AnchorRecord>> {
        loads++
        if (failNextLoads > 0) {
            failNextLoads--
            return AnchorOutcome.Failed(-8, "load too frequent")
        }
        return AnchorOutcome.Ok(store.values.toList())
    }

    override suspend fun remove(anchorId: String): AnchorOutcome<Unit> {
        store.remove(anchorId)
        return AnchorOutcome.Ok(Unit)
    }
}

/** The test room with a viewer standing at the Stage origin. */
class FakeEnvironment(var origin: Vec3? = Room.HEAD, planes: List<PlaneSurface> = Room.all) : MeasureEnvironment {
    private val byId = planes.associateBy { it.id }.toMutableMap()

    /** A plane the room has just recognised. */
    fun add(plane: PlaneSurface) {
        byId[plane.id] = plane
    }

    /** Set to make the spatial features unavailable. */
    var blocked: BlockReason? = null

    /** How many more times [availability] answers [blocked] before the features become available by themselves (-1: for good). */
    var blockedAnswers = -1

    override fun availability(): BlockReason? {
        if (blocked != null && blockedAnswers >= 0) {
            if (blockedAnswers == 0) blocked = null else blockedAnswers--
        }
        return blocked
    }

    override fun viewOrigin(): Vec3? = origin

    override fun surfaces(): Collection<PlaneSurface> = byId.values

    override fun surface(id: String): PlaneSurface? = byId[id]
}

class RecordingLogger : RulerLogger {
    val lines = ArrayList<String>()

    override fun log(line: String) {
        lines += line
    }
}
