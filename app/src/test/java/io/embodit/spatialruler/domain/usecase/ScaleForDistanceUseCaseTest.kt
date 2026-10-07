package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.assertNear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleForDistanceUseCaseTest {
    private val scales = ScaleForDistanceUseCase()

    @Test
    fun panels_keepAConstantVisualAngleBetween45CentimetresAndSixMetres() {
        assertNear(1f, scales.panel(0.8f))
        assertNear(2f, scales.panel(1.6f))
        assertNear(0.5625f, scales.panel(0.2f)) // clamped at 0.45 m
        assertNear(6.25f, scales.panel(5f)) // still growing at 5 m
        assertNear(7.5f, scales.panel(6f))
        assertNear(7.5f, scales.panel(9f)) // clamped at 6 m
    }

    @Test
    fun beyondSixMetres_aPlateOnlyShowsItsValueLine() {
        assertFalse(scales.plateIsFar(5.9f))
        assertFalse(scales.plateIsFar(6f))
        assertTrue(scales.plateIsFar(6.1f))
    }

    @Test
    fun pins_areLifeSizeUpToTwoAndAHalfMetres_andTwiceAtFiveMetresAndBeyond() {
        assertNear(1f, scales.pin(0.5f))
        assertNear(1f, scales.pin(2.5f))
        assertNear(1.2f, scales.pin(3f))
        assertNear(2f, scales.pin(5f))
        assertNear(2f, scales.pin(10f))
    }

    @Test
    fun touchSphereOfAPin_growsFromOnePointThreeMetres_upToFourTimes() {
        assertNear(1f, scales.pinTouch(0.5f))
        assertNear(1f, scales.pinTouch(1.3f))
        assertNear(2f, scales.pinTouch(2.6f))
        assertNear(4f, scales.pinTouch(10f))
    }

    @Test
    fun blade_isWidenedSoThatItNeverLooksThinnerThanHalfADegree() {
        assertNear(1f, scales.tapeWidth(1f))
        assertNear(2f, scales.tapeWidth(3.667f), 1e-3f)
        assertNear(4f, scales.tapeWidth(20f))
    }

    @Test
    fun guides_areTwoMillimetresWide_andWidenWithTheDistance() {
        assertNear(0.0020f, scales.guideWidthM(0.5f))
        assertNear(0.0020f, scales.guideWidthM(1.25f), 1e-6f)
        assertNear(0.008f, scales.guideWidthM(5f), 1e-6f)
    }
}
