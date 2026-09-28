/*
 * Copyright (C) 2026 Aaron (accom06)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the IME is currently rendering in a "wide" enough window to benefit from a split
 * (THUMBS) keyboard layout. This is the practical signal we use to auto-toggle the split mode for
 * foldables — see [updateFromInsets] for why we don't go through `androidx.window.layout.WindowInfoTracker`.
 *
 * **Why not WindowInfoTracker?** The `FoldingFeature` API from `androidx.window.layout` only reports
 * fold posture for windows registered with the framework's window hierarchy (Activities). An IME
 * (`InputMethodService`) does not own such a window, so passing `applicationContext` to
 * `WindowInfoTracker.windowLayoutInfo()` returns an empty `displayFeatures` list in practice — no
 * FoldingFeature is ever delivered. The original install using WindowInfoTracker never fired, the
 * keyboard never auto-split.
 *
 * **What works instead.** We watch the root window bounds (the host app's window, which the IME
 * DOES get via `ImeInsets.Root.boundsDp`). When the user unfolds a foldable, the root window width
 * jumps from portrait (≤480dp on a Fold's cover screen) to landscape/tabletop (~600–840dp on the
 * inner screen). We compare the current width against a threshold ([UNFOLDED_MIN_WIDTH_DP]); above
 * that, treat the surface as "wide enough to split". This also catches regular tablets and large
 * landscape phones, which are exactly the same scenario from a keyboard-ergonomics standpoint — two
 * thumbs need a split layout, period.
 *
 * The state is a [StateFlow] so the controller can collect it alongside its other configuration
 * flows without polling.
 */
object FoldableState {
    /**
     * The smallest root window width (in dp) at which we consider the keyboard surface wide enough
     * to benefit from the THUMBS layout. 600dp matches the Android WindowSizeClass `WIDTH_DP_MEDIUM`
     * breakpoint, which is the same threshold that triggers adaptive two-pane layouts in Compose —
     * it's a well-known "this is no longer a phone" boundary.
     */
    const val UNFOLDED_MIN_WIDTH_DP = 600

    private val _isUnfolded = MutableStateFlow(false)
    val isUnfolded: StateFlow<Boolean> = _isUnfolded.asStateFlow()

    /**
     * Recompute the unfolded flag from a fresh set of root insets. Called from
     * [ImeWindowController] each time the root window changes (orientation flip, foldable opening
     * or closing, entering split-screen, etc.). Idempotent — repeated calls with the same width
     * leave the flag alone.
     *
     * @param rootInsets The latest root insets; we only read the width.
     */
    fun updateFromInsets(rootInsets: ImeInsets.Root) {
        val wide = rootInsets.boundsDp.width.value >= UNFOLDED_MIN_WIDTH_DP
        _isUnfolded.value = wide
    }
}
