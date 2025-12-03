/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mechanics.compose.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.animation.scene.mechanics.rememberGestureContext
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.mechanics.debug.LocalMotionValueDebugController
import com.android.mechanics.debug.MotionValueDebugController
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.ComposeFeatureCaptures.height
import platform.test.motion.compose.ComposeFeatureCaptures.y
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.ComposeToolkit
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.on
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.asDataPoint
import platform.test.motion.testing.createGoldenPathManager

@RunWith(Parameterized::class)
class VerticalTactileSurfaceRevealModifierTest(val useOverlays: Boolean) :
    MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @get:Rule
    val motionRule: MotionTestRule<ComposeToolkit> =
        createFixedConfigurationComposeMotionTestRule(
            createGoldenPathManager("frameworks/libs/systemui/mechanics/compose/tests/goldens")
        )

    private val debugger = MotionValueDebugController()

    private fun assertVerticalTactileSurfaceRevealMotion(
        goldenName: String,
        gestureControl: GestureRevealMotion,
    ) =
        motionRule.runTest {
            lateinit var state: MutableSceneTransitionLayoutState
            val isTransitioning =
                FeatureCapture<SemanticsNodeInteractionsProvider, Int>("") {
                    (if (state.isTransitioning()) 1 else 0).asDataPoint()
                }

            val boxes = 8
            @Composable
            fun ContentScope.TestContent(modifier: Modifier = Modifier) {
                Box(modifier = modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier.element(ContainerElement)
                                .motionDriver(rememberGestureContext())
                                .verticalScroll(rememberScrollState())
                                .background(Color.LightGray)
                                .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(boxes) {
                            Box(
                                Modifier.testTag("box$it")
                                    .border(
                                        2.dp,
                                        when (it) {
                                            0 -> Color.Green
                                            boxes - 1 -> Color.Red
                                            else -> Color.Blue
                                        },
                                    )
                                    .verticalTactileSurfaceReveal(label = "box$it")
                                    .size(50.dp)
                            )
                        }
                    }
                }
            }

            val motion =
                recordMotion(
                    content = {
                        CompositionLocalProvider(
                            LocalMotionValueDebugController provides debugger
                        ) {
                            state =
                                rememberMutableSceneTransitionLayoutState(
                                    initialScene = gestureControl.startScene,
                                    initialOverlays = gestureControl.startOverlays,
                                    transitions =
                                        transitions {
                                            from(CollapsedScene, to = ExpandedOverlay) {
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                            from(CollapsedScene, to = ExpandedScene) {
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                        },
                                )
                            SceneTransitionLayout(
                                state = state,
                                modifier =
                                    Modifier.background(Color.Yellow)
                                        .size(ContainerSize)
                                        .testTag(STL_TAG),
                                implicitTestTags = true,
                            ) {
                                scene(
                                    key = CollapsedScene,
                                    userActions =
                                        mapOf(
                                            if (useOverlays) {
                                                Swipe.Down to ExpandedOverlay
                                            } else {
                                                Swipe.Down to ExpandedScene
                                            }
                                        ),
                                    content = { Box(modifier = Modifier.fillMaxSize()) },
                                )
                                if (useOverlays) {
                                    overlay(
                                        ExpandedOverlay,
                                        userActions =
                                            mapOf(
                                                Swipe.Up to
                                                    UserActionResult.HideOverlay(ExpandedOverlay)
                                            ),
                                        content = {
                                            TestContent(Modifier.border(2.dp, Color.Magenta))
                                        },
                                    )
                                } else {
                                    scene(
                                        key = ExpandedScene,
                                        userActions = mapOf(Swipe.Up to CollapsedScene),
                                        content = { TestContent(Modifier.border(2.dp, Color.Cyan)) },
                                    )
                                }
                            }
                        }
                    },
                    ComposeRecordingSpec(
                        recording = {
                            performTouchInputAsync(
                                onNodeWithTag(STL_TAG),
                                gestureControl.gestureControl,
                            )

                            awaitCondition {
                                !state.isTransitioning() && debugger.observed.all { it.isStable }
                            }
                        },
                        timeSeriesCapture = {
                            feature(isTransitioning, "isTransitioning")
                            featureOfElement(ContainerElement, height)
                            repeat(boxes) {
                                val testTag = "box$it"
                                on(hasTestTag(testTag)) {
                                    feature(y, name = "${testTag}_${y.name}")
                                    feature(height, name = "${testTag}_${height.name}")
                                }
                            }
                        },
                    ),
                )

            assertThat(motion).timeSeriesMatchesGolden(goldenName)
        }

    @Test
    fun verticalTactileSurfaceReveal_gesture_dragOpen() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_gesture_dragOpen",
            gestureControl =
                GestureRevealMotion(startScene = CollapsedScene) {
                    swipeDown(endY = 200.dp.toPx(), durationMillis = 500)
                },
        )
    }

    @Test
    fun verticalTactileSurfaceReveal_gesture_flingOpen() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_gesture_flingOpen",
            gestureControl =
                GestureRevealMotion(startScene = CollapsedScene) {
                    val end = Offset(centerX, 80.dp.toPx())
                    swipeWithVelocity(
                        start = topCenter,
                        end = end,
                        endVelocity = FlingVelocity.toPx(),
                    )
                },
        )
    }

    private fun startExpanded(gestureControl: TouchInjectionScope.() -> Unit): GestureRevealMotion {
        return if (useOverlays) {
            GestureRevealMotion(
                startScene = CollapsedScene,
                startOverlays = setOf(ExpandedOverlay),
                gestureControl = gestureControl,
            )
        } else {
            GestureRevealMotion(startScene = ExpandedScene, gestureControl = gestureControl)
        }
    }

    @Test
    fun verticalTactileSurfaceReveal_gesture_dragClose() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_gesture_dragClose",
            gestureControl =
                startExpanded { swipeUp(200.dp.toPx(), 0.dp.toPx(), durationMillis = 500) },
        )
    }

    @Test
    fun verticalTactileSurfaceReveal_gesture_flingClose() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_gesture_flingClose",
            gestureControl =
                startExpanded {
                    val start = Offset(centerX, 260.dp.toPx())
                    val end = Offset(centerX, 200.dp.toPx())
                    swipeWithVelocity(start, end, FlingVelocity.toPx())
                },
        )
    }

    private class GestureRevealMotion(
        val startScene: SceneKey,
        val startOverlays: Set<OverlayKey> = emptySet(),
        val gestureControl: TouchInjectionScope.() -> Unit,
    )

    private companion object {
        const val STL_TAG = "stl"

        val CollapsedScene = SceneKey("CollapsedScene")
        val ExpandedScene = SceneKey("ExpandedScene")
        val ExpandedOverlay = OverlayKey("ExpandedOverlay")
        val ContainerElement = ElementKey("ContainerElement")

        val ContainerSize = DpSize(150.dp, 300.dp)
        val FlingVelocity = 1000.dp // dp/sec

        @Parameterized.Parameters(name = "useOverlays={0}")
        @JvmStatic
        fun useOverlays() = listOf(false, true)
    }
}
