# Amethyst X

Project handbook. This is the single source of truth for what this project is, why it is built the
way it is, and what must not be broken. Read it before changing anything; update it whenever a
major design decision is made or a major feature lands.

---

## 1. Project overview

Amethyst X is an Android launcher for **Minecraft: Java Edition**. It is a fork of
[Amethyst](https://github.com/AngelAuraMC/Amethyst-Android), which is itself a fork of
PojavLauncher. It runs a real JVM on the device and translates the game's OpenGL calls to OpenGL ES,
so it is a launcher *and* a compatibility layer.

Two things separate this fork from upstream:

1. **A built-in gameplay recorder** that captures frames inside the GL pipeline rather than through
   the screen, so it records the game and nothing else.
2. **A ground-up UI/UX redesign** in Jetpack Compose, aiming at flagship Android app quality.

The recorder work was offered upstream and declined. That is the origin of the fork's direction:
rather than maintain a patch, make it a better tool.

- Repository: `pokitw/Amethyst-Android`
- Working branch: `claude/amethyst-settings-controls-redesign-m29j5h`
- Application ID: `org.angelauramc.amethyst` (**never change this** — see §12)

---

## 2. Vision and long-term goals

> Amethyst X should feel like a flagship Android application that just happens to launch Minecraft.

Not "good for a launcher". Not "modern enough". The bar is: someone installs it and thinks *this
looks like something Google would ship*. Concretely that means:

- The launcher is a **product**, not a settings front-end for a JVM.
- Every screen is designed from first principles against what people actually do, not converted
  from the layout that happened to exist.
- Recording is a **flagship feature**, presented as one, everywhere it appears.
- Quality over throughput. One exceptional screen beats five average ones.

---

## 3. Design philosophy

**Rank by what people actually do, not by what the code contains.** The clearest example is the old
launcher home: the account bar — set once, then ignored for months — was the largest element on the
screen, while Play sat at the bottom sharing weight with a version dropdown. The redesign inverted
that. Apply the same test to every screen: how often is each element *used*, and does its visual
weight match?

**Merge decisions that are really one decision.** Nobody thinks "select 1.20.1" and then separately
"launch". They think *play this*. So version and Play became one object. Look for other pairs like
this before adding controls.

**Show state before the action, not after the failure.** The launch card shows renderer, memory,
mod loader and whether the version is downloaded. Before, the only way to discover a profile was on
the wrong renderer was to launch it and watch it fail. Settings summaries follow the same rule.

**Spend boldness in one place.** Exactly one element per screen carries a gradient. Everything
around it stays quiet. That is what makes the focal point read without any glow or neon.

**Destructive actions do not get prime real estate.** "Force close" was the *first* row of the
in-game menu. Removal of an account was a delete icon on every row. Both are wrong: rare and
irreversible actions go last, or behind a long press, always behind a confirmation.

**If something does not improve usability, remove it.** Six equal-weight buttons became four tiles
and three text links, because two of them were external links and one was a diagnostic.

---

## 4. UI/UX principles

- **Dark only.** The system setting is not consulted. The launcher sits beside a game that is
  itself dark; a light mode would be jarring rather than useful.
- **No dynamic colour.** The amethyst *is* the brand and must not be replaced by the wallpaper.
- **Tonal elevation, not borders.** Surfaces are separated by the neutral ramp. Reach for a border
  only when tone genuinely cannot do the job.
- **One accent, used sparingly.** Violet marks the primary action and the current selection. It is
  not decoration.
- **Large touch targets.** Minimum 48dp for anything tappable; in-game targets are larger still,
  because they are hit with a thumb while the other hand holds the device.
- **Every list row earns its subtitle.** A row that only repeats its title in smaller text should
  not have one. A row that can carry live state (current value, current selection) should.
- **Text is design material.** Name things the way a player would: "Game files", not
  "Storage provider directory". Controls say what will happen.
- **Empty states are designed**, not an afterthought — they are the first thing a new user sees.

### Minecraft identity — Material 3 × Minecraft

The app should be recognisably *Minecraft's* without being a costume. The rule is: **Google-quality
design with Minecraft personality; the reference enhances, never overpowers.**

Do:
- **Inventory-slot geometry** for icon wells — a squarish rounded tile with a subtle inner
  highlight, echoing the bevelled slots of the inventory, rendered in Material tonal surfaces.
- **Hotbar composition** where a row of equal-weight actions is genuinely the right control.
- **Slightly chunkier proportions** than stock Material: a little more padding, slightly larger
  icon wells, corner radii on the generous side.
- **Reward the players who know.** Small, quiet details that land only if you have played.

Do **not**:
- Pixel-art UI, blocky buttons, dirt/stone textures, Minecraft fonts in body copy.
- Neon, glow, "gaming" chrome of any kind.
- Anything that would look out of place next to Google Photos.

---

## 5. Branding

- Name: **Amethyst X**. In the header it is set as "Amethyst" plus an accent-coloured "X"
  (`home_brand_name` + `home_brand_mark`), so the mark reads without a logo lockup.
- Mark: a cut amethyst gem — `res/drawable/ic_x_gem.xml` at 24dp, `ic_launcher_x_foreground.xml`
  for the adaptive icon. Four flat facets, no gradients, so it survives every launcher mask.
- The gem is the one place colour is the subject rather than an accent; it does **not** take a tint.

---

## 6. Colour palette

Defined once in `ui/theme/Color.kt` and mirrored into `res/values/colors.xml` for the screens still
built from XML, so both halves of the app sit on the same ground.

**Accent ramp**

| Token | Hex | Use |
| --- | --- | --- |
| `Amethyst20` | `#3B1B52` | Text/icons *on* the accent |
| `Amethyst30` | `#542A73` | Primary container |
| `Amethyst40` | `#6E3D94` | Inverse primary |
| `Amethyst50` | `#9649B8` | Brand amethyst; deep end of gradients |
| `Amethyst70` | `#C08CE8` | **Primary.** Accent text, icons, selection |
| `Amethyst80` | `#D6B4F2` | Secondary |
| `Amethyst90` | `#EEDCFA` | On primary container |

**Neutrals** — violet-leaning on purpose, so large dark areas do not read as flat grey next to the
accent.

| Token | Hex | Use |
| --- | --- | --- |
| `Neutral04` | `#0E0B12` | Background, scrim, status bar |
| `Neutral08` | `#16121C` | `surfaceContainerLow` — tiles |
| `Neutral12` | `#1D1826` | `surfaceContainer` — cards, sheets |
| `Neutral16` | `#251F30` | `surfaceContainerHigh` — icon wells |
| `Neutral22` | `#2F2839` | `surfaceContainerHighest` — inset wells, muted chips |
| `Neutral30` | `#3A3245` | Outline, dividers |
| `Neutral70` | `#A79DB4` | `onSurfaceVariant` — secondary text |
| `Neutral80` | `#CBC2D4` | On surface variant, brighter |
| `Neutral95` | `#E9E2EF` | `onSurface` — primary text |

**Status**

| Token | Hex | Use |
| --- | --- | --- |
| `Success70` | `#7FD69A` | Confirmed / healthy state |
| `Danger70` | `#FFB4AB` | Error text |
| `RecordingRed` | `#E5484D` | **Live recording only.** Deliberately outside the accent ramp so it can never be mistaken for chrome. |

Reach for a token, never a raw hex, in new code.

---

## 7. Typography

`ui/theme/Type.kt`. Two families on purpose:

- **Display/headings** — Noto Sans Bold (`R.font.noto_sans_bold`), already bundled, so nothing new
  ships. Used for `displaySmall` → `titleLarge`.
- **Body/labels** — the system font. Better hinted at small sizes and it follows the reader's own
  font settings.

Tracking is tightened on large sizes (down to `-0.5sp`) — that is what stops big text looking loose
— and opened slightly on small labels (up to `+0.5sp`) so they stay legible.

Scale: `displaySmall` 30 / `headlineMedium` 24 / `headlineSmall` 20 / `titleLarge` 18 /
`titleMedium` 15 / `titleSmall` 13 / `bodyLarge` 15 / `bodyMedium` 13 / `bodySmall` 12 /
`labelLarge` 13 / `labelMedium` 11 / `labelSmall` 10.

Stay on the scale. If a size is missing, it is probably the wrong size.

---

## 8. Shape, spacing, motion

**Shape** (`ui/theme/Shape.kt`) — steps far enough apart that an element's role is legible from its
silhouette before any text is read: `extraSmall` 6dp (badges) · `small` 10dp (thumbnails) ·
`medium` 16dp (cards, tiles) · `large` 22dp (hero card) · `extraLarge` 30dp (sheets).

Nested shapes follow **outer radius − padding**: the hero card is 22dp with 6dp padding, so its
inner elements are 16dp. Keep that relationship.

**Spacing** — 20dp screen gutters, 10dp between tiles in a grid, 24dp between major sections,
12–16dp inside cards. Use `Arrangement.spacedBy`, not per-child margins.

**Motion philosophy**

- Animate to explain a change, never because it is possible.
- **The thing you touched is the thing that responds.** Progress renders inside the Play button,
  not in a bar somewhere else. Press states squish the element pressed.
- Durations: 140ms for press feedback, ~300ms for entrances, 500ms for value changes such as a
  progress fill. Slow, continuous motion (an indeterminate sweep) runs at ~1500ms.
- Easing: `FastOutSlowInEasing` for anything with a start and an end; `LinearEasing` only for
  continuous loops.
- Every `animate*AsState` gets a `label` — it costs one argument and makes the animation
  inspectable.
- Respect reduced-motion where the platform reports it.

---

## 9. Component guidelines

- **Cards** — `surfaceContainer`, `shapes.medium`, no border. Only the hero card gets a gradient
  wash, and it is a radial wash from the top-left corner that has faded out before it reaches the
  primary button.
- **Tiles** — `surfaceContainerLow`, a 34dp icon well in `surfaceContainerHigh`, accent-tinted
  icon, title in `titleSmall`, one subtitle line in `labelSmall`. Both text lines are
  `maxLines = 1` so a row of tiles never goes ragged.
- **Chips** — `CircleShape`, `surfaceContainer`. Used for identity, not for actions.
- **Badges** — `extraSmall`, uppercase `labelSmall`; accent at 13% alpha when meaningful, plain
  `surfaceContainerHighest` when merely informational.
- **Sheets** — `ModalBottomSheet`, `surfaceContainer`, `skipPartiallyExpanded = true`. Every sheet
  opens with a heading and a one-line hint that names any non-obvious gesture.
- **Rows in sheets** — 14dp radius, accent at 11% alpha when selected, trailing check when
  selected. Long press for the secondary action; the heading says so.
- **Settings rows** — `ui/settings/SettingsComponents.kt`. Grouped into a `SettingsCard` with no
  dividers: the shared surface groups them and the space between text blocks separates them. Title
  in `titleSmall`, description in `bodySmall`, and the *current value* in `labelLarge` **accent**
  underneath — that accent line is what makes a screen of settings scannable. A slider's value
  goes above the track, never beside it, because a thumb would cover it mid-drag. Every row goes
  through `RowShell`/`HighlightBox`, which is what lets search light one up (see §14).
- **Icon wells** — `SlotWell` in `ui/theme/Slot.kt`. A squarish tile with a light inset along the
  top-left and a dark one along the bottom-right: the bevel of an inventory slot, drawn entirely
  in Material tonal surfaces. **This is the whole of the Minecraft reference** — reach for it
  rather than inventing a second one.
- **Icons** — 24dp grid, 1.8 stroke, round joins, `#FFFFFF` so the caller tints. The set lives in
  `res/drawable/ic_x_*.xml`. Draw new ones to match rather than importing a mismatched Material
  glyph. Core Material icons are acceptable for universal glyphs (play, check, add, chevron).
- **Control glyphs** — `res/drawable/ic_ctrl_*.xml` is a **second** icon family and the only one
  allowed to break the rule above: solid filled shapes, no strokes. They are drawn thumb-sized
  over live gameplay, where a 1.8dp hairline disappears against a bright sky and a filled shape
  does not. Keep them geometric and chunky; they are read at a glance, mid-fight, in a hurry.

---

## 10. Technical architecture

### Processes

Two. The launcher UI runs in the main process; **the game runs in `:game`**, which is where the JVM
is loaded in-process via JNI. They share nothing but files and intents.

### The launch path — do not disturb

```
MainMenuFragment  →  ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
                  →  LauncherActivity.mLaunchGameListener
                       ├─ refuses if ProgressLayout.hasProcesses()
                       ├─ reads the profile from LauncherPreferences.PREF_KEY_CURRENT_PROFILE
                       ├─ reads the account from mcAccountSpinner.getSelectedAccount()
                       └─ MinecraftDownloader → ContextAwareDoneListener → MainActivity (:game)
```

`ExtraCore` is a tiny global event bus (`extra/ExtraCore.java`). It is how loosely-coupled parts of
the launcher talk. New UI should raise the existing events rather than reimplement what they do.

**`mcAccountSpinner` is the source of truth for who is logged in**, because the launch listener
reads it. It is now permanently hidden while the home screen is showing, but it still runs: it owns
the Microsoft/Mojang login listeners and the token refresh. Compose account switching routes
*through* it (`LauncherActivity.selectAccount` → `mcAccountSpinner.selectAccountByName`), never
around it.

### Profiles

`launcher_profiles.json` in the game directory, read through `LauncherProfiles` (a static holder)
and `MinecraftProfile`. The **selected profile is a string key in SharedPreferences**
(`PREF_KEY_CURRENT_PROFILE`), and the profile editor reads that key rather than taking an argument
— so anything opening the editor must set the selection first.

`LauncherProfiles.mainProfileJson` is unsynchronised shared mutable state that the launch path also
reads. **Read it on the main thread.** See §12.

### Progress

`ProgressKeeper` is a static registry of named tasks (`ProgressLayout.DOWNLOAD_MINECRAFT`,
`UNPACK_RUNTIME`, `INSTALL_MODPACK`, `AUTHENTICATE_MICROSOFT`, …). Anything can submit progress from
any thread; listeners are called on the submitting thread. The task **count** is the same condition
the launch path refuses on, which is why the Play button's busy state follows the count rather than
any individual key.

### Storage

`getExternalFilesDir()` → `/sdcard/Android/data/org.angelauramc.amethyst/files`. Worlds, versions,
runtimes and recordings all live under it.

---

## 11. Recording pipeline

The distinguishing feature. It records **inside the GL pipeline**, not via the screen, so overlays,
notifications and the system UI never appear in the output.

### Native side (`jni/ctxbridges/`)

```
pojavSwapBuffers (LWJGL3 GLFW stub)
  → br_swap_buffers → gl_swap_buffers()          [gl_bridge.c]
      → gl_recorder_frame(display, bundle)        ← the hook, before eglSwapBuffers
      → eglSwapBuffers
```

- `gl_recorder.c` owns a second ES3 context that **shares** with the game's, an encoder surface from
  MediaCodec, and a `glBlitFramebuffer` that letterboxes the game's framebuffer into the target
  resolution.
- The game's work is fenced with `EGL_KHR_fence_sync` before the blit. **Without this you get white
  flashes** — that was a real, shipped bug.
- Frames are stamped with **raw `CLOCK_MONOTONIC`** via `eglPresentationTimeANDROID` where it
  exists. Where it does not, the buffer queue stamps with exactly that clock anyway, so both paths
  land in the same domain and the extension stays optional. Making it mandatory broke MobileGlues
  entirely — see §16.
- `gl_overlay.c` composites the virtual mouse cursor, which is an Android view above the surface and
  therefore absent from the captured frames. It uses its own GL entry points and degrades to
  no-overlay rather than failing. `gl_overlay_release()` deliberately issues **no** GL calls,
  because it runs while the game's context is current.
- **Only the EGL bridge can be recorded.** `osm_bridge.c` (OSMesa, used by `vulkan_zink`) has no EGL
  surface to hook, so recording refuses on that renderer with a clear message.

### Java side (`recorder/`)

- `GameRecorder` owns MediaCodec (surface input), the audio encoder and MediaMuxer. It rebases both
  tracks against the recording-start instant at mux time, which is what keeps A/V in sync.
- Audio is `AudioPlaybackCaptureConfiguration` (API 29+, requires a MediaProjection consent),
  optionally mixed with the microphone. **PCM is little-endian and `ByteBuffer` defaults to
  big-endian** — getting that wrong produced continuous static. See §16.
- `RecorderService` is a foreground service with FGS type `mediaProjection`, required from
  Android 14. It is separate from `GameService` on purpose.
- `RecordingInfo` writes a JSON sidecar next to each clip (version, resolution, frame rate, audio),
  because MediaMuxer cannot store arbitrary metadata.
- Output is capped below the **MP4 4 GB 32-bit offset limit** and stops with free space remaining.

### Cost

Roughly one extra full-screen blit and a hardware encode per frame — a few percent of GPU, far
cheaper than a screen recorder, which composites the whole display and re-encodes it.

---

## 12. Things that must not change without a very good reason

1. **`applicationId` stays `org.angelauramc.amethyst`.** It determines
   `getExternalFilesDir()`. Changing it orphans every world, version and runtime the user has.
2. **The launch seam stays `ExtraCore.setValue(LAUNCH_GAME, true)`.** Everything downstream —
   version resolution, LWJGL3ify handling, demo-account guards, the downloader — hangs off that one
   listener.
3. **`mcAccountSpinner` stays the account source of truth** while the launch listener reads it.
4. **`MinecraftGLSurface` is never reimplemented in Compose.** Compose has no native GL-surface
   host; wrap it in `AndroidView` if it must live inside a composition. The touch and GL lifecycle
   is exactly where the game path would break.
5. **`initializeViewTreeOwners()` in `BaseActivity.onCreate` stays.** AppCompat resolves to an old
   transitive version that overrides `setContentView` without installing the view-tree owners, and
   a `ComposeView` inside an XML layout resolves its recomposer from the *activity's content view*.
   Without it the launcher crashes on start. See §16.
6. **The recorder's timestamps stay in the raw `CLOCK_MONOTONIC` domain**, and
   `eglPresentationTimeANDROID` stays optional.
7. **`LauncherProfiles` and `ProfileIconCache` are read on the main thread.** They are
   unsynchronised statics shared with the launch path.
8. **Compose BOM stays `2024.10.01`** while `compileSdk` is 34 — later BOMs require 35. Same for
   `activity-compose:1.9.3` (1.10+ needs compileSdk 35).
9. **A `ComposeView` over the game surface stays `GONE` unless it is showing something.** Visible,
   it sits in front of every touch `MinecraftGLSurface` is waiting for. `ControlCenterHost` toggles
   visibility and delays the `GONE` by the exit animation's length.
10. **In-game surfaces are not `ModalBottomSheet`.** That creates a real dialog window, which risks
    dropping the game out of immersive fullscreen. The control center draws its own scrim and
    slide inline instead.
11. **The control skin is applied at draw time and never written into a layout.** `ControlSkin` is
    read by `ControlInterface.setBackground()` and `ControlButton.onDraw`; the fill, stroke and
    radius the user saved stay in the file untouched. Baking the skin into the data would take
    someone's colours away permanently, which is the one thing a look-and-feel preference must
    never do.
12. **`assets/default.json` is replaced, never merged.** Changing it hands existing users a
    `controlmap/new_default.json` and leaves their own default alone — that is `AsyncAssetManager`
    working as intended, not a bug to fix. Note `Tools.compareSHA1` "fake matches" on a read
    error, which is what makes a fresh install take the `else` branch and write `default.json`.

---

## 13. Compose migration status

| Surface | State | Notes |
| --- | --- | --- |
| Onboarding | **Compose** | `ui/onboarding/`, `OnboardingActivity.kt`; runs once, before the launcher is built |
| Launcher home | **Compose** | `ui/home/`, hosted by `MainMenuFragment.kt` |
| Recordings gallery | **Compose** | `ui/recordings/`, `RecordingsActivity.kt` |
| Game files (worlds, mods, packs, shaders, screenshots) | **Compose** | `ui/content/`, `ContentActivity.kt` |
| Profile / account pickers on home | **Compose** | `ModalBottomSheet` in `ui/home/HomeSheets.kt` |
| Settings | **Compose** | `ui/settings/`, hosted by `SettingsFragment.kt` |
| Runtime manager · gamepad remapper · MobileGlues tuning | XML, stays for now | Reached from the new Settings; see §17 |
| In-game control center | **Compose** | `ui/game/`, hosted by `MainActivity` **and** `CustomControlsActivity` |
| On-screen keyboard · voice overlay | **Compose** | `ui/game/`, each with its own bottom-anchored `ComposeView` |
| Control layout editor menu | **Compose** | The control center in editor mode; the buttons it edits stay custom views |
| Control editor panel · key picker | **Compose** | `ui/controls/`, driven by `ControlLayout.setEditorHost` |
| Sign-in chooser | **Compose** | `ui/auth/`, hosted by `SelectAuthFragment.kt` |
| Profile editor · type picker · MC version picker | **Compose** | `ui/profile/`, hosted by `ProfileEditorFragment.kt` and `ProfileTypeSelectFragment.kt` |
| Crash screen | **Compose** | `diagnosis/`, hosted by `ExitActivity.kt` |
| Skin editor | Not built | Designed, §18.1 |
| Mod search's version dialog | XML | The one remaining `VersionSelectorDialog` caller; see §17 |
| Control buttons themselves | XML custom views | Deep custom view work; skinned rather than rewritten, see §14 |
| Game surface | XML, stays | See §12.4 |

Full screens share `AppScaffold` (`ui/common/`) for the back bar, the title that collapses into it
and the 20dp gutters. Settings hoists its scroll state through it, because search scrolls to a row.

The launcher's chrome (`activity_pojav_launcher.xml`: account bar, settings button, progress bar)
is **hidden while the home screen or Settings is showing**, because both draw their own header.
Settings keeps the progress bar — a download started elsewhere has nowhere else to report from
while it is open — so `setChromeHidden` takes the two decisions separately.
`ProgressLayout.setSuppressed(boolean)` exists for this.

---

## 14. What has been redesigned, and the decision that survived it

This section is a ledger, not a diary: each entry keeps only the decision that would otherwise be
re-litigated. The reasoning lives in the commit that made the change.

- **Onboarding** (`ui/onboarding/`) — story pages, then an honest side-by-side against upstream.
  Two rules decide what goes in it, and both exist to stop it growing a page per commit.
  **A page is one feature you would not otherwise find.** The control editor was rewritten and has
  no page, because you meet it the first time you long press a button; the comparison table carries
  it. **Two answers to one problem are one page** — the on-screen keyboard and dictation share
  "Say it instead of typing it", because they are both about typing in landscape with both thumbs
  busy.
  The comparison table is the reason the flow exists: someone arriving from Amethyst is owed an
  answer to "why this one". It is **only trustworthy because it loses rows** — the skin editor and
  the download size are there on purpose, and gyro aiming is marked `Part` for upstream rather than
  absent, because upstream *has* one, it just steps. A table where the fork wins every line is an
  advert, and nobody believes an advert.
  **Adding a feature means adding its row**, whether or not it earns a page.
- **Settings** — five destinations grouped by intent, each carrying a live summary of its own
  state. The renderer moved in, tagged `THIS PROFILE`, and **stays stored per profile**. Screens
  are written out as the lists of settings they are, not generated from a spec, so they can be
  diffed against what they replaced.
- **Settings search** — `SettingsIndex.kt` is the flat table of contents. **Adding a setting to a
  screen means adding a line there.** Results carry their screen and section; tapping one scrolls
  to the row and washes it. Rows are matched by **title text**, not an added key.
- **Settings header** — Settings draws its own account header, so the launcher chrome is hidden
  here as it already was on home. It keeps the progress bar; home does not.
- **In-game control center** — a sheet from the bottom, because in landscape that is where thumbs
  are. Recording is the card at the top. Force close is last, quiet, in the error colour.
  `ControlCenterHost` is the Java-facing seam.
- **On-screen keyboard** (`ui/game/GameKeyboard.kt` + `KeyboardPanel.kt` + `GameKeyboardHost.kt`)
  — replaces the keycode `AlertDialog`. It has **its own bottom-anchored `ComposeView`**, not the
  control center's full-screen one, so the game stays visible *and touchable* above it and it
  needs no scrim at all; a keyboard you poke mid-fight is not a menu. Caps send a **GLFW keycode
  directly**, not an index into `EfficientAndroidLWJGLKeycode`, and they carry their
  **character**, which is what lets the keyboard type in chat and not only fire keybinds. **Any
  cap can be latched** by long press, which is the only way F3 + G was ever reachable; latched
  keys are real key-downs inside the game, so `GameKeyboardHost.close()` and `release()` must go
  on releasing them. Every row's weights add up to `ROW_UNITS`; that is what
  `scripts/check_keyboard.py` checks, along with the keycode range and that no key the old dialog
  offered was lost.
- **In-game screenshots** (`jni/ctxbridges/gl_screenshot.c` + `screenshot/GameScreenshot.java` +
  `ui/game/ScreenshotHost.kt`) — a picture of the frame the renderer is about to present, taken at
  the same seam as the recorder, so the on-screen controls are not in it.
  Four decisions hold it up. It reads through **a context of its own that shares the game's**,
  never the game's own: `glReadPixels` implicitly reads the pixel pack buffer, the pack alignment
  and row length and the read framebuffer binding, and restoring those is *not* symmetric between
  ES 2 and ES 3 — `GL_FRAMEBUFFER_BINDING` and `GL_DRAW_FRAMEBUFFER_BINDING` are the same number
  while `GL_READ_FRAMEBUFFER_BINDING` is a different one, and getting that wrong spoils the game's
  rendering from then on rather than merely the picture. A fresh context has all of it at the
  defaults by construction. **Both bridges are covered**, unlike recording: OSMesa has no EGL
  surface but it does have a finished CPU frame between `ANativeWindow_lock` and
  `unlockAndPost`, which is the whole of the zink path. The wait is a plain **`glFinish`**, not the
  recorder's fence — the recorder runs every frame and cannot stall the CPU, this runs once and
  the simpler, stronger guarantee is the right trade. And the **native side hands over one shape
  of buffer** — tightly packed, top-down, opaque RGBA — so `Bitmap.copyPixelsFromBuffer` takes it
  with nothing said about strides, row order or alpha, and there is no second place for those to
  be got wrong.
  It gets **no comparison-table row**, which is the one deliberate exception to the rule above.
  Upstream can bind F2 exactly as this can, so every honest mark would be a tie, and a table row
  that says nothing is worse than no row — the table is only worth reading because it is edited.
- **Control-center actions straight from a button** — `SPECIALBTN_GAMEKEYBOARD` (-12) opens the
  keyboard without the sheet. Special keycodes are **appended, never inserted**: the editor's
  spinner converts position ↔ keycode by arithmetic over the *reversed* name list, and the
  keycode itself is what is written into saved layout JSON, so an insertion silently re-points
  every layout anyone has ever saved.
- **Voice typing** (`ui/game/VoiceInputHost.kt` + `VoiceOverlay.kt`, `customcontrols/keyboard/`)
  — `SPECIALBTN_VOICE` (-13), plus an opt-in hold on a button bound to T or `/`. Four decisions
  hold it together. It uses the **bound-service `SpeechRecognizer`**, never
  `ACTION_RECOGNIZE_SPEECH` as an activity: that pauses the game, and `onPause` sends ESCAPE
  while the cursor is grabbed, so every dictation would open the pause menu. Words go out as
  **characters** (`CallbackBridge.sendChar`), never key events, or a dictated "quick" would drop
  the held item and open the inventory. `LiveTyper` **models the chat box it cannot read**,
  typing each new guess as a diff against the last, which is why anything that could desync the
  two calls `forget()` rather than keeping on correcting. And **only the back key undoes** a
  dictation — the overlay's stop control keeps the words, because deleting text someone watched
  appear is the more startling of the two. The recogniser watchdog is a **silence** timer that
  every callback pushes back; a fixed session limit would cut off exactly the long sentence the
  feature exists for.
- **On-screen controls** — `ControlSkin` decides how a control is drawn **at draw time and never
  writes to the layout**, so turning it off gives the author's colours back. `ControlGlyphs` picks
  an icon from **the key a button sends**, not its name, so old layouts gain icons with no
  migration; a button bound to two keys keeps its text. `assets/default.json` is a Pocket Edition
  shape written in the simple expression vocabulary so it can be read and checked (§19).
- **Control layout editor** — hosts the same control center in editor mode, so the editor from
  Settings and the one from inside a game are one screen with two ways in.
- **Editing a button** (`ui/controls/`) — the keycode spinners are gone: **you bind a key by
  pressing it on a keyboard**, the same board the on-screen keyboard uses, from the same `Key`
  tables. Everything a control can do that is not a key sits behind an Actions tab with real
  names — "Left click", not `SPECIAL_PRI`. Slots appear one at a time rather than four at once,
  binding the first one **names an unnamed button after its key**, and every change is live on
  the button behind the panel, which is why the panel is narrow and hugs the edge the button is
  not on. `ControlEditorState` is the only thing that writes `ControlData`, because each field
  needs a different refresh call and getting one wrong is invisible until a slider does nothing.
  This took `EditControlSideDialog`, `ActionRow` and its three icon buttons, and the whole
  `colorselector` package with it.
- **Game files** (`ui/content/`) — worlds, mods, resource packs, shader packs and screenshots as
  **one screen**, reached from the home tile and from Settings. Both used to hand the player to a
  file manager and a path under `Android/data`.
  The reason it is one screen and not five is **the add button**: nobody thinks "place a file in
  the resourcepacks directory", so the picker takes anything and **what it is decides where it
  goes** — `.jar` is a mod, a zip with `pack.mcmeta` a resource pack, one with `shaders/` a shader
  pack, one with `level.dat` a world, which gets unpacked. The categories are a **filter over one
  list**, which is what lets search cross them. Rows are deliberately the same shape whatever they
  hold; only mods carry a switch, because only a mod can be turned off in place.
  Three things that must not be undone: **the four mod metadata formats are tried most-specific
  first** (a Quilt jar also ships `fabric.mod.json`, a NeoForge jar also ships `mods.toml`, so the
  obvious order misreports both); **a world's folder name is not its name**, so `level.dat` is
  read through `NbtReader` for the real one; and **world extraction canonicalises every entry
  path** before writing, because it is the only place in the launcher that unpacks something a
  stranger sent. Nothing here touches the network: this is the folder, not a store.
  The list is lazy — `LazyAppScaffold` exists because `AppScaffold` puts its content in a
  scrolling `Column`, and a folder of four hundred screenshots cannot live in one.
  It is **drawn with the settings components** (§9), not with a vocabulary of its own. It first
  shipped with a gradient storage meter, a row of filter chips and every item on its own floating
  card, and next to Settings — the screen most people arrive from — it read as a different
  application. Same grouped card, same section labels, same row metrics, same choice row. The
  category filter is a `ChoiceRow` rather than chips because chips are for identity, not actions,
  and delete moved to a **long press with a named hint** rather than a red link on every row.
  **Every row is its own lazy item**, and the card around a section is rebuilt from the rows'
  corners — first rounded above, last below, the rest square. Putting a section inside one
  `SettingsCard` reads identically and composes all four hundred rows the moment the section
  scrolls into view, which is a `LazyColumn` that is not lazy.
  Three rules keep it fast, and each of them was a visible stall: **pictures are read when a row
  asks**, never during the scan; **a resume keeps every item whose file has not changed** (matched
  on `level.dat` for a world, because a folder's own timestamp does not move when a region file
  inside it does), so coming back from a screenshot viewer costs a directory listing rather than
  two hundred jars reopened; and **the background passes merge rather than replace** — the switch,
  the rename behind it and the picture that just arrived are taken from the row as it stands, or a
  mod turned off while its jar was being read turns itself back on.
- **Gyro aiming** (`customcontrols/mouse/GyroControl.java` + `GyroSmoother.java`) — rewritten
  because it stepped. The old one **held movement back behind a 1.13–1.3 unit threshold and then
  flushed the whole accumulator**, which at a slow aiming speed meant freezing for up to 80ms and
  then jumping two pixels; the threshold was there to hide drift. It now reads **raw angular
  velocity** at the fastest rate the device offers, integrates against the **measured** interval
  between samples so the feel is rate-independent, and **calibrates the bias away** instead of
  hiding it. Sub-pixel movement survives because `CallbackBridge.mouseX` is a float and the
  bridge floors only at `GLFW_invoke_CursorPos`. Yaw is taken **around gravity, not around the
  screen** (Jibb Smart's player space, relax factor 2), so aiming still works with the phone
  tilted back or flat — the case local space, and every mobile shooter that uses it, gets wrong.
  Smoothing is **tiered**: only movements below ~1.5°/s are averaged, so shake is removed and a
  flick is not delayed. 100% sensitivity is **1:1** with the view.
- **Sign-in** — one screen, not two. Microsoft carries the gradient and offline is quieter beneath
  it, because they are not equal choices: offline cannot join a server. The username is asked for
  in place, validated as it is typed. Microsoft still hands off to `MicrosoftLoginFragment`, and an
  offline account is still created by raising `MOJANG_LOGIN_TODO` for the account spinner.
- **Version picker** — searchable and filterable, each row saying whether it is on disk. It is a
  **route inside the profile editor**, not its own fragment, so Back always lands on the editor.
- **Profile editor / creator** — version and icon are the header, since together they are how a
  profile is recognised everywhere else. Delete stops sitting beside Save. Profile type tiles say
  what each loader *is* rather than repeating "Create X profile".
- **Crash diagnosis** — `ExitActivity` (still resolved from JNI by name, so its package, class
  and `showExitMessage(Landroid/content/Context;IZ)V` are **frozen**) now scans the tail of
  `latestlog.txt` through `diagnosis/CrashDiagnosis.kt` — an ordered, first-match rule table —
  and names the failure in words, quoting the log line it concluded from. Every stage is wrapped
  and the old dialog remains the fallback; the composition renders only precomputed strings, so
  the crash screen has nothing left to crash on. Rules live in the Kotlin/Python-shared regex
  subset because `scripts/check_crash_rules.py` re-runs the shipped patterns against fixture
  logs before every push (§19). New rule = new `Rule(...)` + strings + a fixture.

## 15. Coding conventions

**Match the file you are in.** This codebase is old, mixed-style, and largely upstream. Do not
reformat around your change.

- **Java**: `mCamelCase` fields, upstream brace style. Source/target **Java 8** — no `var`, no
  diamond with anonymous classes, no API 24+ methods (`minSdk` is 21).
- **Kotlin**: official style, 4 spaces, ~100 column limit. Prefer top-level functions over objects
  full of statics.
- **Comments explain *why*, never *what*.** No commented-out code, no "TODO" without a reason, no
  restating the line below. If a line looks wrong but is deliberate, say why — those comments are
  the valuable ones (`gl_overlay_release()` issuing no GL calls; not using `setSelection()` on a
  hidden spinner).
- **KDoc/Javadoc on anything non-obvious**, especially where a subtlety would otherwise be
  "corrected" later.
- **Resources**: `home_*`, `recordings_*`, `preference_recorder_*` prefixes. New strings go in
  `values/strings.xml` only; translations are upstream's.
- **Commits**: imperative subject under ~72 chars, then *why*, not *what*. Never mention the model.

---

## 16. Lessons learned

Each of these cost a build cycle or a user-visible bug. They are here so they are not repeated.

1. **White flashes in recordings** — no GPU sync between the game's context and the recorder's.
   Fixed with `EGL_KHR_fence_sync`. A comment claiming "MediaCodec timestamps frames as they arrive,
   which is good enough" was simply wrong.
2. **59-day video durations** — video started at device uptime, audio at zero. The first fix made
   `eglPresentationTimeANDROID` mandatory, which **broke MobileGlues entirely**. That was the wrong
   *shape* of fix: the right one keeps both tracks in one clock domain and rebases at mux time.
   When a fix removes support for something, it is probably wrong.
3. **Continuous static when mixing game audio and microphone** — `ByteBuffer` defaults to
   `BIG_ENDIAN`; PCM is little-endian. Only the mixed path used `getShort`/`putShort`, which matched
   the symptom exactly.
4. **The launcher crashed on start with `ViewTreeLifecycleOwner not found`** — `AppCompatActivity`
   overrides `setContentView` without calling through to `ComponentActivity`'s, and this project has
   no explicit AppCompat dependency, so it inherits an old transitive one. `activity-compose`'s
   `setContent` patches the owners itself, which is why the recordings gallery worked and the home
   screen did not. **Two Compose screens reaching Compose by different routes will not fail the same
   way.**
5. **`if (busy) Color else Brush` inside `background()`** unifies to `Any` and matches neither
   overload. Branch the whole modifier instead.
6. **Kotlin and Compose cannot be verified locally** — there is no Android SDK, no Gradle cache and
   no `kotlinc` in the dev container. CI is the only compiler. Java *can* be checked with stub
   classes and `javac -source 8`; resource references can be verified by script; vector drawables
   can be rendered to SVG and screenshotted with the bundled headless Chromium. Do all three before
   pushing.
7. **A green build is not a working app.** Runs 16 and 17 passed while the launcher crashed on
   start. Compilation proves nothing about attach-time contracts.
8. **Design first, build once.** Publishing an interactive mockup and getting a reaction before
   writing Kotlin has been worth far more than the time it costs.
9. **A private Kotlin property still emits its JVM accessors**, and `private set` does not stop
   it. `var opacity by mutableStateOf(...) private set` and `fun setOpacity(Float)` on the same
   class are a "platform declaration clash" — nothing to do with Java calling it, the two just
   compile to the same JVM signature. A state holder that mirrors fields and writes them back
   needs its mutators named apart from its properties: `applyOpacity`, not `setOpacity`. This
   has now cost two build cycles, in `ControlCenterHost` and again in `ControlEditorState`.
10. **A wrapper composable must pass the scope on.** A helper taking
    `content: @Composable () -> Unit` and placing it inside a `Row` gives its callers no
    `RowScope`, so every `Modifier.weight(1f)` inside them fails to resolve. Take
    `@Composable RowScope.() -> Unit` and hand it to `Row(content = content)`.
11. **Do not read the device from inside a composition.** Free space, package info and permission
    checks are not snapshot state; called in a composable they re-run on every recomposition, so a
    slider drag would stat the filesystem per frame. Read them in `onResume` into state.
12. **`BaseActivity.setFullscreen()` defaults to true.** That is right for the game and wrong for
    everything else — the recordings gallery lost its status bar and navigation buttons simply by
    not overriding it. Any new launcher-side activity must.
13. **A section that opens itself has to latch.** The advanced expander opens when search sends
    someone to a row inside it. Deriving "open" from the highlight meant it closed again the
    moment the highlight faded, taking the answer with it. Anything driven by a transient signal
    needs to be copied into state, not read from it.
14. **Vector path data is worth rendering before trusting.** The "drop item" glyph had its arrow
    pointing up: the apex was the first point in the path, and the first point is where the pen
    starts, not where the arrow points. It took ten seconds to spot in a contact sheet and would
    have taken a build cycle and a screenshot from a user otherwise.
15. **A sampling rate is a permission.** `registerListener` throws `SecurityException` on
    Android 12+ for any period under 5000µs unless the app declares
    `HIGH_SAMPLING_RATE_SENSORS` — and `GyroControl.enable()` is called from `onResume`, so
    asking for 2500µs was not a gyroscope that failed, it was a game that would not launch.
    200Hz is the fastest unpermitted rate and costs nothing here: the game reads the cursor once
    per `pojavPumpEvents`, which is once a frame. **Anything on the resume or launch path that
    touches a system service has to be unable to throw**, not merely unlikely to.
16. **A stub that always succeeds tests nothing about the call it stands in for.** The gyro
    harness verified the maths thoroughly and shipped a crash, because its fake `SensorManager`
    returned `true` for every rate — so the one contract that actually broke was the one the
    tests could not see. It now throws exactly as the platform does, and there are checks for
    the real limit, the fallback, and a sensor that will not start at all. When a harness stands
    in for a platform, **model the platform's refusals, not just its successes**.
17. **A generated layout file cannot be reviewed.** The old `default.json` had 400-character
    position expressions with `10^-13` coefficients in them; nobody could tell whether a button
    was in the right place without running it. The replacement is written in the simple
    vocabulary and evaluated by a script against a grid of screen sizes and button scales, which
    is a real check rather than a hope.

---

## 17. Known limitations

- `vulkan_zink` cannot be recorded — it renders through OSMesa, which has no EGL surface to hook.
  It **can** be screenshotted: OSMesa draws into a CPU buffer that is sitting in memory at the
  moment the frame is presented, so there is something to copy even though there is nothing to
  blit.
- **The launcher's screenshot is not the only one.** Minecraft's own F2 writes into the same
  folder, from its own framebuffer, and so has never had the controls in it either. What this adds
  is that it does not go through the game: no keybind to know or to lose to a modpack, one tap on a
  button or in the control center, and it says so on screen instead of in the chat log. Anyone
  weighing up whether it earns its place should weigh it against that, not against nothing.
- A screenshot **costs a `glFinish` and a full readback** on the frame it is taken, so the game
  hitches for one frame. That is the price of not touching any of the game's GL state, and it is
  paid once per picture rather than every frame like the recorder.
- Recordings are capped below 4 GB (MP4 32-bit offsets). At 1080p60/12 Mbps that is roughly 40
  minutes. Automatic segmentation into `part1.mp4`, `part2.mp4` … is designed but not built.
- Echo cancellation on the microphone path is requested but unverified on speakers.
- Cursor hotspot alignment in recordings is unverified against the on-screen cursor.
- Three settings leaves are still `PreferenceScreen` and will look plainer than the rest: the
  **runtime manager** (its own install flow), the **gamepad remapper** (a capture UI), and the
  **MobileGlues tuning** (10 options that are meaningless unless that renderer is selected). They
  are reached by a row from the new Settings and sit on the same ground, so they should read as
  "deeper settings" rather than as broken.
- **Search does not index those three leaves.** Their contents live in `pref_renderer.xml` and in
  the remapper's own capture UI, so search gets you as far as the row that opens them.
- The new default control layout is checked from 80% to 175% button scale. Above that the top row
  runs out of screen on a small display — inherent to nineteen buttons, and the layout is editable.
- **Control glyphs cover the actions a player recognises**, not the whole keyboard. A button bound
  to F7, or to two keys at once, keeps its text label on purpose.
- Voice typing **cannot open chat for you**. The chat key is rebindable and nothing on the
  launcher side can read the player's keybinds, so a voice button pressed with no text field open
  types into nothing. That is also why the hold shortcut is hardcoded to T and `/` — the vanilla
  defaults — and says so in Settings rather than pretending to be general.
- Voice typing **refuses while the recorder holds the microphone**, and says so. One device, one
  microphone; both trying gives both a broken stream.
- Dictation quality, latency and offline support are **the device's recogniser**, not ours.
  `EXTRA_PREFER_OFFLINE` is a request, ignored where unsupported.
- The on-screen keyboard is **US layout**. The shift pairs are baked into the table because that is
  the layout the game's own keybind names assume; a player on another physical layout gets US
  symbols. It is landscape-only, which is safe because `MainActivity` is `sensorLandscape`.
- **`CallbackBridge`'s five modifier booleans are one global with several writers**, and the
  keyboard is the first surface that holds a modifier *across* other input, so it makes an old
  problem reachable: a physical key rewrites all five from its event's meta state
  (`EfficientAndroidLWJGLKeycode.execKey`), and a toggled control button bound to Shift shares the
  same flag and the same key-down. Latching Shift on both, then releasing one, desynchronises them.
  Properly fixing it needs global key-state tracking that does not exist; the keyboard confines the
  damage by only ever touching the flag belonging to the key that changed.
- **`VersionSelectorDialog` still exists** for the mod-search flow, which is the only caller left.
  The profile editor uses the Compose picker; the two should converge when mod search is redesigned.
- A skin can only be **applied** to a Microsoft account — Mojang's API is the only thing a server
  reads a skin from, and an offline account has no profile to attach one to. Any skin editor has
  to say so rather than appearing to work and silently doing nothing.
- Gyro aiming is **verified by simulation, not on hardware** (`scripts/gyrosim/`). The maths and
  the axis mapping are checked; what a real MEMS gyroscope's noise floor feels like in the hand is
  not, and neither is the cost of 400Hz sensor callbacks on a weak device.
- Gyro aiming has **no acceleration curve**. Deliberate: the goal is to feel like a mouse, and a
  mouse has none. A "quick turn" boost for large movements is a reasonable thing to want and is
  not built.
- The gyro's 100% is now **1:1**, which is roughly 38% of what 100% used to mean. Anyone who had
  tuned the slider has to raise it once; the range goes to 400% so the old feel is still reachable.
- No automated tests beyond the scripted checks in `scripts/`. There is no device in CI.
- Release builds do not run R8, so every dependency ships whole — which is why only
  `material-icons-core` is used, not the extended set.

---

## 18. Roadmap

**Now**
1. **Skin editor.** Designed, not built. The shape it should take:
   - `ui/skin/SkinModel.kt` — the 64×64 texture plus the **UV table** saying which rect is which
     face of which body part. Everything else reads that one table, so the atlas layout (including
     the 1.8+ second layer and the 3px slim-arm variant) is stated once.
   - `SkinCanvasScreen.kt` — paint one *face* at a time zoomed with a grid, not the raw atlas;
     pencil / eraser / fill / eyedropper, and an undo stack of whole-pixel diffs.
   - `SkinPreview.kt` — a software-projected cube model, drawn on a Compose `Canvas`. No GL: the
     game owns the only GL surface (§12.4) and a launcher-side preview must not need one.
   - `SkinStore.kt` — skins as PNGs under the game directory, listed as a gallery.
   - Applying goes through `PUT api.minecraftservices.com/minecraft/profile/skins` with the
     account's existing `accessToken`, the same `Bearer` pattern `MicrosoftBackgroundLogin` uses.
     Offline accounts save locally and the screen says why that is all it can do (§17).

**Next**
2. Bring the runtime manager and gamepad remapper onto the new components (see §17), which also
   gets their settings into the search index.
4. A layout picker worth the name — the editor's Load is still a file list. Layouts should be a
   gallery with a preview, since a control layout is a picture, not a filename.
5. Recording segmentation for multi-hour sessions.
6. Mod search — `SearchModFragment` and its CurseForge/Modrinth flow are still the old XML, and
   the one remaining `VersionSelectorDialog` caller. Installing *from* the internet and managing
   what is installed should meet in `ui/mods/`.

**Later**
6. Shared-element transition from the version card into the version sheet.
7. Recordings: in-app playback and trimming rather than handing off to an external player.
8. Retire `activity_pojav_launcher.xml` chrome entirely once every fragment is Compose.
9. A joystick variant of the Pocket default, offered as a choice the way Bedrock offers it,
   rather than something you assemble yourself in the editor.

---

## 19. Build and verification

```bash
# There is no Android SDK in the dev container. CI is the compiler.
git push -u origin claude/amethyst-settings-controls-redesign-m29j5h
# → GitHub Actions "Android CI" → artifact "app-debug (recommended)"
```

Before pushing:
- Verify every `R.*` reference resolves (script it against `res/values*/*.xml` and `res/drawable*`).
  Ignore the noise from `android.R.*` and the `sdp`/`ssp` libraries; only new names matter.
- Parse every file under `res/` as XML.
- Render new vector drawables to SVG and screenshot them with
  `/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell --screenshot`.
  A contact sheet of the whole family catches shapes that are merely upside down.
- Check balanced braces in new Kotlin and Java files.
- **Evaluate any changed control layout.** `${...}` substitution is a plain string replace
  (`JSONUtils.insertSingleJSONValue`) and the result goes to exp4j, so a short Python script can
  reproduce it exactly: substitute, map `px(n)` to `n * density`, `^` to `**`, and check every
  button lands on screen across a grid of resolutions and button scales.
- **Run `python3 scripts/check_crash_rules.py`** if the crash rule table changed — it tests the
  shipped patterns, parsed out of the Kotlin source, against fixture crash logs.
- **Run `scripts/nbtsim/run.sh`** if `NbtReader` changed — it writes a `level.dat` shaped like a
  real one, nested compounds and arrays and all, and reads it back through the shipped source.
  Binary format parsing is wrong in ways reading cannot catch.
- **Run `scripts/gyrosim/run.sh`** if gyro aiming changed — it stubs the four framework types
  `GyroControl` touches, compiles the real shipped source, and drives synthetic motion through it:
  1:1 scaling, no drift under a hardware bias, one-pixel steps on a slow turn, no added latency on
  a flick, player space vs local space, and two sample rates agreeing. There is no device in CI, so
  this is the only thing that can catch a sign error or a broken integration before a user does.
- **Run `python3 scripts/check_keyboard.py`** if the on-screen keyboard changed — it parses the cap
  tables out of `GameKeyboard.kt` and checks the row weights, the keycode range, and that every key
  the old dialog could send is still reachable. A board is also worth *looking* at: the same parser
  can emit HTML and be screenshotted, which is how a row that does not line up gets caught.
- Read the whole diff.

CI builds Debug **before** Release, so a missing signing key never hides a compile error. Release
steps are skipped when `GPLAY_KEYSTORE_PASSWORD` is unset.
