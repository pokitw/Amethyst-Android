package net.kdt.pojavlaunch.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.optimiser.PerformancePlan

/**
 * The seam between performance mode's work and the screen showing it.
 *
 * <b>The work does not belong to the composition.</b> Applying the plan writes preferences, edits
 * Minecraft's options file and downloads six mods, which takes as long as it takes; a
 * `rememberCoroutineScope` is cancelled the moment somebody leaves the screen, and half an install
 * is the one outcome worse than none. So the fragment owns the work on its own lifecycle and this
 * holds what the screen draws, in the same shape as `ControlCenterHost`.
 *
 * Mutators are named apart from the properties on purpose: a `var stage by mutableStateOf(...)`
 * with `private set` still emits a JVM `setStage`, and a method of that name beside it is a
 * platform declaration clash rather than an override. That has cost two build cycles already
 * (handbook 16.9).
 */
@Stable
class PerformanceHost {

    enum class Stage {
        /** Nothing on screen. */
        CLOSED,

        /** The plan, before a single thing has been written. */
        PREVIEW,

        /** It is already on: what it did, and the way back off. */
        ACTIVE,

        /** Applying, with a line saying what is happening. */
        WORKING,

        /** What happened, which is the only place the mods that had no build are named. */
        RESULT
    }

    var stage by mutableStateOf(Stage.CLOSED)
        private set

    var plan by mutableStateOf<PerformancePlan.Plan?>(null)
        private set

    /** The phone in one line: GPU, panel and refresh rate. */
    var deviceLine by mutableStateOf("")
        private set

    /** Which profile this will land on, because it is per profile and that has to be said. */
    var profileLine by mutableStateOf("")
        private set

    var progress by mutableStateOf("")
        private set

    /** The outcome, one sentence per fact. Empty until there is one. */
    var result by mutableStateOf<List<String>>(emptyList())
        private set

    /** Set by the fragment. Tapping the sheet's one button calls this. */
    var onApply: () -> Unit = {}

    fun showPreview(plan: PerformancePlan.Plan, device: String, profile: String) {
        this.plan = plan
        deviceLine = device
        profileLine = profile
        progress = ""
        result = emptyList()
        stage = Stage.PREVIEW
    }

    /** Opened on a mode that is already on, so the one button offered is the way off. */
    fun showActive(lines: List<String>, device: String, profile: String) {
        plan = null
        deviceLine = device
        profileLine = profile
        progress = ""
        result = lines
        stage = Stage.ACTIVE
    }

    fun showWorking(text: String) {
        progress = text
        stage = Stage.WORKING
    }

    fun showResult(lines: List<String>) {
        result = lines
        stage = Stage.RESULT
    }

    fun close() {
        stage = Stage.CLOSED
        plan = null
        progress = ""
        result = emptyList()
    }
}
