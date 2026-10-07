package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.RulerUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSettingsUseCaseTest {
    private val defaults = DefaultSettingsUseCase()

    @Test
    fun americanEnglish_startsInFeetAndInches() {
        assertEquals(RulerUnit.FT_IN, defaults.forLocale("en", "US").unit)
        assertEquals(RulerUnit.FT_IN, defaults.forLocale("EN", "us").unit)
    }

    @Test
    fun everythingElse_startsInMetres() {
        assertEquals(RulerUnit.M, defaults.forLocale("zh", "CN").unit)
        assertEquals(RulerUnit.M, defaults.forLocale("en", "GB").unit)
        assertEquals(RulerUnit.M, defaults.forLocale("", "").unit)
    }

    @Test
    fun firstLaunch_followsTheSystemLanguage_snapsAndHasNotSeenTheHint() {
        val settings = defaults.forLocale("zh", "CN")
        assertEquals(LanguagePref.SYSTEM, settings.language)
        assertTrue(settings.snap)
        assertFalse(settings.coachSeen)
    }
}
