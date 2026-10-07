package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectTickLevelUseCaseTest {
    private val ticks = SelectTickLevelUseCase()

    @Test
    fun metric_dropsLevelsWhoseTicksWouldBeCloserThanAFifthOfADegree() {
        assertEquals(1, ticks.finestStep(0.5f, UnitSystem.METRIC)) // 1 cm ticks
        assertEquals(1, ticks.finestStep(2.8f, UnitSystem.METRIC))
        assertEquals(5, ticks.finestStep(3.0f, UnitSystem.METRIC)) // 1 cm at 3 m is 0.19 degrees
        assertEquals(5, ticks.finestStep(14f, UnitSystem.METRIC))
        assertEquals(10, ticks.finestStep(20f, UnitSystem.METRIC))
        assertEquals(50, ticks.finestStep(40f, UnitSystem.METRIC))
        assertEquals(100, ticks.finestStep(200f, UnitSystem.METRIC))
        assertEquals(100, ticks.finestStep(5000f, UnitSystem.METRIC)) // coarsest level is always drawn
    }

    @Test
    fun imperial_usesInchHalfFootFootAndYard() {
        assertEquals(1, ticks.finestStep(1f, UnitSystem.IMPERIAL))
        assertEquals(1, ticks.finestStep(7f, UnitSystem.IMPERIAL))
        assertEquals(6, ticks.finestStep(8f, UnitSystem.IMPERIAL))
        assertEquals(12, ticks.finestStep(60f, UnitSystem.IMPERIAL))
        assertEquals(36, ticks.finestStep(120f, UnitSystem.IMPERIAL))
    }

    @Test
    fun hysteresis_keepsTheCurrentLevelNearTheSwitchOver() {
        // the switch from 1 cm to 5 cm ticks is at about 2.86 m
        assertEquals(5, ticks.finestStep(2.9f, UnitSystem.METRIC))
        assertEquals(1, ticks.finestStepWithHysteresis(2.9f, UnitSystem.METRIC, current = 1))
        assertEquals(5, ticks.finestStepWithHysteresis(2.8f, UnitSystem.METRIC, current = 5))
        // clearly past the band: the level changes
        assertEquals(5, ticks.finestStepWithHysteresis(3.5f, UnitSystem.METRIC, current = 1))
        assertEquals(1, ticks.finestStepWithHysteresis(2.0f, UnitSystem.METRIC, current = 5))
    }

    @Test
    fun hysteresis_ignoresALevelOfTheOtherUnitSystem() {
        assertEquals(1, ticks.finestStepWithHysteresis(1f, UnitSystem.IMPERIAL, current = 5))
        assertEquals(1, ticks.finestStepWithHysteresis(1f, UnitSystem.METRIC, current = null))
    }

    @Test
    fun graduationConstants() {
        assertEquals(0.01f, SelectTickLevelUseCase.baseOf(UnitSystem.METRIC), 0f)
        assertEquals(0.0254f, SelectTickLevelUseCase.baseOf(UnitSystem.IMPERIAL), 0f)
        assertEquals(100, SelectTickLevelUseCase.wholeOf(UnitSystem.METRIC))
        assertEquals(12, SelectTickLevelUseCase.wholeOf(UnitSystem.IMPERIAL))
    }
}
