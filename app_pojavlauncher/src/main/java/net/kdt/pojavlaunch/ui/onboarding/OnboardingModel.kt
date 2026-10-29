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
 * leave the on-screen buttons out of the picture. It is no longer what the fork is *about*,
 * which is what the welcome page now says, but it is still the clearest single answer to "why
 * this one" and so it keeps the slot.
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
    ),
    // Last of the pages and immediately before the comparison table, which is where it belongs:
    // everything above it is a claim, and this is the sentence that decides whether the claims
    // are worth believing. A launcher that lists twenty features and says nothing about what they
    // cost is an advert, and the table two screens later only works because it loses rows.
    //
    // It is deliberately SHORT. The mechanism is four screens of prose and it lives in Settings,
    // reached from Performance and from About, because somebody meeting the launcher for the
    // first time needs the warning and somebody chasing a frame rate needs the explanation, and
    // those are different people at different moments.
    OnboardingPage(
        titleRes = R.string.onboarding_honest_title,
        bodyRes = R.string.onboarding_honest_body,
        iconRes = R.drawable.ic_x_diagnosis,
        noteRes = R.string.onboarding_honest_note
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
    // "No" for upstream, which is the strongest mark the table hands out and is earned here the
    // way auto-walk earned it: this is not the same capability presented better, it is one that
    // has no equivalent at all. Upstream writes clips and screenshots into a folder under
    // Android/data and that is the end of its involvement, so trimming one means finding a file
    // the system file manager cannot browse to and taking it to another app.
    ComparisonRow(R.string.onboarding_row_editor, Support.NO, Support.YES),
    // Upstream has gyro aiming; what it does not have is one that moves smoothly. "Part" is the
    // honest mark for a thing that exists and steps, and the footnote says what it is measured
    // against — marking it absent would be the kind of overclaim that makes a table worthless.
    ComparisonRow(R.string.onboarding_row_gyro, Support.PARTIAL, Support.YES),
    ComparisonRow(R.string.onboarding_row_voice, Support.NO, Support.YES),
    // "No", on auto-walk's reasoning: upstream has one layout at a time and no way to express
    // a second set of controls, so this is a capability it has no equivalent of rather than
    // the same one presented better.
    ComparisonRow(R.string.onboarding_row_pages, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_keyboard, Support.NO, Support.YES),
    // Its own row rather than folded into the keyboard's, because it is not part of the keyboard:
    // it answers the system one as well, which is the board most people actually meet.
    ComparisonRow(R.string.onboarding_row_typing, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_files, Support.NO, Support.YES),
    // "Part" for upstream because it does have a Modrinth search: what that search finds is
    // modpacks, and installing one builds a whole new profile. Adding one mod to the profile you
    // have is the thing neither had until now.
    ComparisonRow(R.string.onboarding_row_mods, Support.PARTIAL, Support.YES),
    // A capability upstream has no equivalent of, which is what earns a row rather than taste: it
    // ships the same Modrinth search and the same mods folder, and nothing anywhere reads what a
    // jar declares. The pre-launch memory check deliberately gets no row beside this one, because
    // upstream does make that check, in the wrong place, and where a question is asked is not
    // something an honest tick can say.
    ComparisonRow(R.string.onboarding_row_modcheck, Support.NO, Support.YES),
    // "Part" for upstream, and getting this mark right matters more than most: upstream can run
    // Sodium. Its warning has a way past it, a maths question in the experimental settings, which
    // is upstream's own code and works. Marking it "No" would be the overclaim this table exists
    // to avoid. What it does not do is check whether the renderer can reach OpenGL 3.3 before
    // launching, say nothing at all when it can, or offer the renderer that would fix it; it tells
    // you instead that you are on your own. This fork was briefly worse than upstream here, having
    // deleted the screen that held the way past, which is the other reason the row cannot say "No".
    ComparisonRow(R.string.onboarding_row_shaders, Support.PARTIAL, Support.YES),
    ComparisonRow(R.string.onboarding_row_crash, Support.NO, Support.YES),
    // "Part" for upstream and it has to be: it shows the log live over the running game, which
    // this build kept. What it cannot do is read the log afterwards, which is when a crash is
    // actually looked into, or search it. Marking it absent would be the overclaim that makes a
    // table worthless.
    ComparisonRow(R.string.onboarding_row_log, Support.PARTIAL, Support.YES),
    ComparisonRow(R.string.onboarding_row_search, Support.NO, Support.YES),
    ComparisonRow(R.string.onboarding_row_controls, Support.PARTIAL, Support.YES),
    // Not a tie, unlike the screenshot: upstream has per-button colours, which is a different
    // thing from wearing a picture, so the row says something.
    ComparisonRow(R.string.onboarding_row_textures, Support.NO, Support.YES),
    // "Part" because upstream has the joystick control; what it does not have is the finished
    // layout, which you assemble yourself in the editor. Shipping it ready-made is the feature.
    ComparisonRow(R.string.onboarding_row_bedrock, Support.PARTIAL, Support.YES),
    // "Part" for upstream because a button there does hold four keys at once; what it cannot do
    // is press them one after another, which is the half a combo move actually needs. Upstream
    // said no to macros in as many words, so this row is a real difference, not a tie.
    ComparisonRow(R.string.onboarding_row_sequence, Support.PARTIAL, Support.YES),
    // Beside the sequence row rather than folded into it: one is several keys on a clock, this is
    // one key on a clock, and the useful half is the gesture that starts it. "No" for upstream and
    // it survives the fairest reading available: holding a stay-pressed button there does make
    // Minecraft place blocks over and over, so the repeating is not the difference. Doing it from
    // a button that is still an ordinary button when tapped is, and there is no way to get that.
    ComparisonRow(R.string.onboarding_row_repeat, Support.NO, Support.YES),
    // Beside the repeat row for the same reason that one sits beside sequences: a different
    // control on the same stick. "No" is the honest mark on the fairest reading available too,
    // since holding a directional key already walks in that direction upstream just as here;
    // what upstream cannot do is keep it held once the thumb lifts off the stick.
    ComparisonRow(R.string.onboarding_row_autowalk, Support.NO, Support.YES),
    // "Part" for upstream because it ships one Turnip build and loads it automatically on
    // Adreno; what it declined (their issue 224) is letting you import a newer one. On any
    // other GPU neither side offers anything, which is the row's honest scope.
    ComparisonRow(R.string.onboarding_row_turnip, Support.PARTIAL, Support.YES),
    // Was NO/NO for as long as this table has existed, which is what made it worth building.
    ComparisonRow(R.string.onboarding_row_skin, Support.NO, Support.YES),
    // Its own row rather than folded into the editor's, because it is a different thing: the
    // editor makes a skin, this finds one that already exists. Both are NO for upstream, which
    // is only worth two rows because they answer different questions.
    ComparisonRow(R.string.onboarding_row_skinfind, Support.NO, Support.YES),
    // "Part" for upstream, and it has to be: it installs all four loaders perfectly well. What it
    // does not do is let you see which ones support a version before you have picked one, or find
    // that version without scrolling a spinner of seven hundred entries.
    ComparisonRow(R.string.onboarding_row_loaders, Support.PARTIAL, Support.YES),
    // The other place this fork is behind, and it is here for the same reason the download size
    // is: a table where one side wins every line is an advert. Upstream rewrote SDL's controller
    // detection; this build has not taken that, so a mod that initialises SDL's gamepad subsystem
    // still takes the launcher's own pad emulation away for the session. The fix cannot be
    // exercised without a controller and a device, and claiming it either way would be worse
    // than saying so.
    ComparisonRow(R.string.onboarding_row_controller, Support.YES, Support.PARTIAL),
    ComparisonRow(R.string.onboarding_row_size, Support.YES, Support.PARTIAL)
)
