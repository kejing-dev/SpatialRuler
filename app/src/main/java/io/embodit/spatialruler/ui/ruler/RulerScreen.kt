package io.embodit.spatialruler.ui.ruler

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.ui.ruler.components.LocalRulerResources
import io.embodit.spatialruler.ui.ruler.components.OverlayHosting
import io.embodit.spatialruler.ui.ruler.components.RulerStage
import io.embodit.spatialruler.ui.ruler.components.localizedResources
import io.embodit.spatialruler.ui.ruler.debug.RulerProbe
import io.embodit.spatialruler.ui.ruler.scene.RulerFrameSystem
import io.embodit.spatialruler.ui.ruler.scene.RulerScene
import com.pico.spatial.ui.platform.HandController
import com.pico.spatial.ui.platform.HandControllerHapticType
import com.pico.spatial.ui.platform.LocalAudioEffectPlayer
import com.pico.spatial.ui.platform.LocalHandControllerHaptic
import com.pico.spatial.ui.platform.SpatialSoundEffect
import io.embodit.spatialruler.domain.model.FeedbackKind

/**
 * The measuring screen: the one screen of the app, living in a Mixed Stage.
 *
 * It wires the three parts together and owns nothing else: the ViewModel holds the state, the
 * [RulerScene] turns state into entities and reports taps, and [RulerStage] declares the panels.
 */
@Composable
fun RulerScreen() {
    val context = LocalContext.current
    val viewModel: RulerViewModel = viewModel(factory = RulerViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current

    // test-harness seams only exist in debuggable builds; set before any panel is composed
    remember(context) {
        RulerProbe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember(viewModel) { RulerScene(viewModel::onEvent, viewModel) }

    DisposableEffect(scene) {
        // the one per-frame system; every start below has its stop in onDispose
        registerSystem<RulerFrameSystem>()
        RulerProbe.host =
            object : RulerProbe.Host {
                override fun dispatch(event: RulerEvent) = viewModel.onEvent(event)

                override fun uiState(): RulerUiState = viewModel.state.value

                override fun dumpJson(): String = viewModel.dumpJson()

                override fun scene(): RulerProbe.SceneProbe = scene
            }
        RulerLog.i("STAGE enter")
        onDispose {
            RulerProbe.host = null
            unregisterSystem<RulerFrameSystem>()
            scene.detach()
            RulerLog.i("STAGE disposed")
        }
    }

    // model -> entities; also when a floating surface changes its host
    val dialogHost = OverlayHosting.dialog.value
    val snackHost = OverlayHosting.snackbar.value
    LaunchedEffect(scene, state, dialogHost, snackHost) { scene.render(state) }

    // sound and touch: each goes with a change that is also visible (design: 声音与触感)
    val audio = LocalAudioEffectPlayer.current
    val haptic = LocalHandControllerHaptic.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RulerUiEffect.ExitApp -> {
                    RulerLog.i("STAGE close requested")
                    runCatching { navigator.closeStage() }.onFailure { RulerLog.e("STAGE close failed: $it", it) }
                }
                is RulerUiEffect.Feedback -> {
                    val hand = if (effect.right) HandController.Right else HandController.Left
                    runCatching {
                        when (effect.kind) {
                            FeedbackKind.PIN_DROP -> {
                                audio.playSystem(SpatialSoundEffect.OpClick)
                                haptic.feedback(HandControllerHapticType.Press, hand)
                            }
                            FeedbackKind.SNAP -> {
                                audio.playSystem(SpatialSoundEffect.StateSelected)
                                haptic.feedback(HandControllerHapticType.Step, hand)
                            }
                            FeedbackKind.TAPE_LOCK -> audio.playSystem(SpatialSoundEffect.OpDragEnd)
                            FeedbackKind.RETRACT -> audio.playSystem(SpatialSoundEffect.OpClose)
                            FeedbackKind.FAIL -> audio.playSystem(SpatialSoundEffect.StateFailure)
                        }
                    }.onFailure { RulerLog.e("FEEDBACK failed: $it", it) }
                    if (RulerProbe.enabled) RulerLog.i("FEEDBACK ${effect.kind.name.lowercase()} hand=${if (effect.right) "right" else "left"}")
                }
            }
        }
    }

    // the language chosen in the app switches at run time: panels read their copy from this local
    val resources = remember(state.language, context) { localizedResources(context, state.language) }
    CompositionLocalProvider(LocalRulerResources provides resources) {
        RulerStage(state = state, scene = scene, onEvent = viewModel::onEvent)
    }
}
