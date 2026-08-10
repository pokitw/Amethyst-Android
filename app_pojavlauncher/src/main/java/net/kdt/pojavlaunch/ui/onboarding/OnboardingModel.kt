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
 *
 * **One page per feature, and a feature has to be one you would not otherwise find.** The controls
 * editor is not here even though it was rewritten, because you meet it the moment you long press a
 * button and it explains itself; the comparison table carries it instead. Text input is one page
 * and not two for the opposite reason — the keyboard and dictation are two answers to the same
 * problem, which is that typing in landscape with both thumbs occupied is miserable.
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
        titleRes = R.string.onboarding_gyro_title,
        bodyRes = R.string.onboarding_gyro_body,
        iconRes = R.drawable.ic_x_motion,
        noteRes = R.string.onboarding_gyro_note
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_chat_title,
        bodyRes = R.string.onboarding_chat_body,
        iconRes = R.drawable.ic_x_voice,
        noteRes = R.string.onboarding_chat_note
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_files_title,
        bodyRes = R.string.onboarding_files_body,
        iconRes = R.drawable.ic_x_files,
        noteRes = R.string.onboarding_files_note
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_crash_title,
        bodyRes = R.string.onboarding_crash_body,
        iconRes = R.drawable.ic_x_diagnosis
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_design_title,
        bodyRes = R.string.onboarding_design_body,
        iconRes = R.drawable.ic_x_sparkle
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
    // Upstream has gyro aiming; what it does not have is one that moves smoothly. "Part" is the
    // honest mark for a thing that exists and steps, and the footnote says what it is measured
    // against — marking it absent would be the kind of overclaim that makes a table worthless.
    ComparisonRow(R.string.onboarding_row_gyro, Support.PARTIAL, Support.YES),
    ComparisonRow(R.string.onboarding_row_voice, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_keyboard, Support.NO, Support.YES),
    // Its own row rather than folded into the keyboard's, because it is not part of the keyboard:
    // it answers the system one as well, which is the board most people actually meet.
    ComparisonRow(R.string.onboarding_row_typing, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_files, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_crash, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_search, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_controls, Support.PARTIAL, Support.YES),
    // Not a tie, unlike the screenshot: upstream has per-button colours, which is a different
    // thing from wearing a picture, so the row says something.
    ComparisonRow(R.string.onboarding_row_textures, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_skin, Support.NO, Support.NO),
    ComparisonRow(R.string.onboarding_row_size, Support.YES, Support.PARTIAL)
)
