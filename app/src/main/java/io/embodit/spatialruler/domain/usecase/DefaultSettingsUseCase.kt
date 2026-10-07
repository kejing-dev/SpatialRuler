package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.RulerUnit

/**
 * Settings of a first launch. The unit follows the system language and region: Chinese -> m,
 * en-US -> ft·in, everything else -> m. The language follows the system until the user picks one.
 */
class DefaultSettingsUseCase {
    fun forLocale(language: String, country: String): RulerSettings {
        val unit = if (language.equals("en", ignoreCase = true) && country.equals("US", ignoreCase = true)) RulerUnit.FT_IN else RulerUnit.M
        return RulerSettings(unit = unit)
    }
}
