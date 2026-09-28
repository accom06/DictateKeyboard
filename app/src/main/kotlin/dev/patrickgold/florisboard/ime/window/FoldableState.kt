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

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks foldable device state across the IME service lifetime.
 *
 * An IME does not own a top-level window, so `WindowInfoTracker.windowLayoutInfo()`
 * is wired against the *application* context — this gives us the same FoldingFeature
 * events an Activity would see, which is what we want: when the user unfolds the
 * device (e.g. Pixel Fold, Galaxy Z Fold, Mate X), we observe the FoldingFeature
 * state change and toggle the split-keyboard mode accordingly.
 *
 * The tracker is process-wide: a single Application context covers the IME service
 * for the whole app process, regardless of which Activity is currently in the foreground.
 *
 * @property isUnfolded True when the device is in the unfolded (flat) state. False
 *  when fully folded (closed) or when no foldable hardware is present. Folded but
 *  half-opened (tabletop / book modes) is treated as unfolded for keyboard purposes —
 *  the available surface area is large either way.
 */
object FoldableState {
    private val _isUnfolded = MutableStateFlow(false)
    val isUnfolded: StateFlow<Boolean> = _isUnfolded.asStateFlow()

    private var installed = false

    /**
     * Begin observing folding state. Idempotent — a second call is a no-op so the IME
     * service can safely re-invoke this on every onCreate without spawning duplicate
     * coroutines.
     *
     * @param context Any context (Application preferred). Only retained as long as the
     *  collection is active.
     * @param lifecycleOwner The lifecycle that bounds the collection. The IME service
     *  passes itself here so the subscription is torn down with the service.
     */
    fun install(context: Context, lifecycleOwner: LifecycleOwner) {
        if (installed) return
        installed = true
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(context)
                    .windowLayoutInfo(context)
                    .collect { info ->
                        val folded = info.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .any { feature ->
                                // HALF_OPENED covers the "book" / "tabletop" postures where the
                                // hinge is in the middle but the two halves are still usable as
                                // a flat-ish surface. FLAT means fully unfolded. Anything else
                                // (mostly just CLOSED) is treated as folded for keyboard purposes.
                                feature.state == FoldingFeature.State.FLAT ||
                                    feature.state == FoldingFeature.State.HALF_OPENED
                            }
                        _isUnfolded.value = folded
                    }
            }
        }
    }
}
