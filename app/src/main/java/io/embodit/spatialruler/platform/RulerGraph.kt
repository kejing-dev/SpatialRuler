package io.embodit.spatialruler.platform

import android.content.Context
import io.embodit.spatialruler.data.repository.PrefsSessionRepository
import io.embodit.spatialruler.data.repository.SessionRepository
import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.port.MeasureEnvironment
import io.embodit.spatialruler.domain.usecase.DefaultSettingsUseCase
import io.embodit.spatialruler.platform.anchor.WorldAnchorGateway
import io.embodit.spatialruler.platform.input.SyntheticPointerSource
import io.embodit.spatialruler.platform.sense.PlaneTracker
import io.embodit.spatialruler.platform.tracking.HeadTracker
import java.util.Locale

/**
 * Process-wide wiring of the platform services. The Stage starts and stops the sensing services
 * (every start has a stop: see `RulerScene.attach` / `detach`); the ViewModel only sees interfaces.
 */
object RulerGraph {
    val anchors = WorldAnchorGateway()
    val planes = PlaneTracker()
    val head = HeadTracker()
    val syntheticPointer = SyntheticPointerSource()

    val environment: MeasureEnvironment =
        object : MeasureEnvironment {
            override fun viewOrigin(): Vec3? = head.pose()?.position

            override fun surfaces(): Collection<PlaneSurface> = planes.all

            override fun surface(id: String): PlaneSurface? = planes.get(id)

            override fun availability(): BlockReason? = head.blockReason()
        }

    @Volatile private var repository: SessionRepository? = null

    fun repository(context: Context): SessionRepository =
        repository ?: synchronized(this) { repository ?: PrefsSessionRepository(context).also { repository = it } }

    /** First-launch settings: unit by system language and region, language follows the system. */
    fun defaultSettings(): RulerSettings {
        val locale = Locale.getDefault()
        return DefaultSettingsUseCase().forLocale(locale.language, locale.country)
    }
}
