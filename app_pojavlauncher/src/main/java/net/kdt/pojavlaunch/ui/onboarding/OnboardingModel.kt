package net.kdt.pojavlaunch.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import net.kdt.pojavlaunch.R

/**
 * What the onboarding says, kept apart from how it looks.
 *
 * Written out as a list rather than generated, for the same reason the settings screens are: it
 * can be read and argued with. Every claim here is a thing the fork actually does, and every page
 * that has a limitation carries it rather than hiding it a screen away.
 */
class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
    /** The caveat under the copy, or 0 where there is honestly nothing to add. */
    @StringRes val noteRes: Int = 0,
    /** The first page shows the gem itself instead of an icon well. */
    val brand: Boolean = false
)

/**
 * Ordered by how much a player would care, which is why the recorder is first: it is the only
 * thing here another launcher cannot simply add, because a screen recorder physically cannot
 * leave the on-screen buttons out of the picture.
 */
fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_welcome_title,
        bodyRes = R.string.onboarding_welcome_body,
        iconRes = R.drawable.ic_x_gem,
        brand = true
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_record_title,
        bodyRes = R.string.onboarding_record_body,
        iconRes = R.drawable.ic_x_recordings,
        noteRes = R.string.onboarding_record_note
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_design_title,
        bodyRes = R.string.onboarding_design_body,
        iconRes = R.drawable.ic_x_sparkle
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_crash_title,
        bodyRes = R.string.onboarding_crash_body,
        iconRes = R.drawable.ic_x_diagnosis
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_keyboard_title,
        bodyRes = R.string.onboarding_keyboard_body,
        iconRes = R.drawable.ic_x_keyboard,
        noteRes = R.string.onboarding_keyboard_note
    )
)

/** Whether a build has a thing, does not have it, or has part of it. */
enum class Support { YES, NO, PARTIAL }

class ComparisonRow(
    @StringRes val labelRes: Int,
    val upstream: Support,
    val fork: Support
)

/**
 * The side-by-side.
 *
 * The last two rows are the point. A comparison where the fork wins every line is an advert, and
 * nobody believes an advert; these are the two places it is genuinely behind, and leaving them in
 * is what makes the rest of the table worth reading.
 *
 * Drawn from this repository's own history against the fork point rather than from a fetch of
 * upstream, which is what the footnote on the page says.
 */
fun comparisonRows(): List<ComparisonRow> = listOf(
    ComparisonRow(R.string.onboarding_row_play, Support.YES, Support.YES),
    ComparisonRow(R.string.onboarding_row_record, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_crash, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_keyboard, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_search, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_controls, Support.PARTIAL, Support.YES),
    ComparisonRow(R.string.onboarding_row_skin, Support.NO, Support.NO),
    ComparisonRow(R.string.onboarding_row_size, Support.YES, Support.PARTIAL)
)
