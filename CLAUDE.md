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
    never do. **A texture pack obeys the same contract** — it draws instead of the fill and the
    keyline and writes nothing — and `setBackground()` is where the three styles are chosen
    between, because it re-runs on every layout pass and anything decided elsewhere is overwritten
    by it a moment later.
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
| Typing preview | **Compose** | `ui/game/TypingPreviewHost.kt`, its own top-anchored `ComposeView` |
| Control layout editor menu | **Compose** | The control center in editor mode; the buttons it edits stay custom views |
| Control editor panel · key picker | **Compose** | `ui/controls/`, driven by `ControlLayout.setEditorHost` |
| Sign-in chooser | **Compose** | `ui/auth/`, hosted by `SelectAuthFragment.kt` |
| Profile editor · type picker · MC version picker | **Compose** | `ui/profile/`, hosted by `ProfileEditorFragment.kt` and `ProfileTypeSelectFragment.kt` |
| Crash screen | **Compose** | `diagnosis/`, hosted by `ExitActivity.kt` |
| Skin editor | **Compose** | `ui/skin/`, `SkinActivity.kt` |
| Mod browser (Modrinth) | **Compose** | `ui/mods/`, `ModBrowserActivity.kt` |
| Modpack search's version dialog | XML | The one remaining `VersionSelectorDialog` caller; see §17 |
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
  **It lays out in two columns past 600dp of width**, and that is not a tablet affordance: the
  game is landscape, so the sheet has width to spare and almost no height, and stacked it grew
  past what a phone is tall and covered the whole screen — the one thing a sheet over a running
  game must not do. Anything added to it has to be measured in the height it costs, which is also
  why capture is **one card with two targets** (the row takes the picture, the button on the end
  pins the shutter) rather than a row each. There is a `heightIn` cap and a scroll behind all of
  it as a backstop, so a screen too short even for two columns loses nothing.
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
  **The control center offers both, because they answer different questions.** Taking one from
  the sheet is right when what is worth keeping is already on screen; a shutter floating over the
  running game is right when you need to see the shot first, and no arrangement of a bottom sheet
  will ever fix that it covers the thing being photographed.
  **The shutter is dragged with a long press, and remembers where it was left.** A long press
  rather than a plain drag because it is jabbed at mid-fight and a drag threshold would send it
  wandering; it is also what everything else here uses for a second meaning. Three things make it
  work. The view stays `wrap_content` and is moved by **translation** (§12.9 — a `match_parent`
  one would swallow every touch the game is waiting for), which is why the *view*, not the
  composition, owns the position. The offset applied is **the pointer's position minus where it
  took hold**, added to the current translation, never a delta between consecutive positions:
  moving a view moves its own coordinate space, so the naive delta reads zero from the second
  event on and the button sticks. And the release is **guarded by a timestamp**, because
  `clickable` fires on the release whatever came before it and a long press to move would
  otherwise also take a picture on the way out.
  Position is stored as **fractions in a preferences file of its own**, written only by the game
  process: the two processes each cache the default preferences and rewrite the whole file on
  apply, so a position saved in game could be thrown away by the launcher saving something else.
  Fractions rather than pixels because the surface changes size. The drag is clamped to the
  parent, so it can never be put somewhere it cannot be grabbed back from — which is why there is
  no "reset position" anywhere, and why the "which side" setting it briefly had was deleted rather
  than kept.
  A bound control button remains the better answer for anyone who wants one permanently; the
  shutter is that without a trip to the editor.
  Its settings sit on the **recording** screen, now called "Recording and screenshots", because
  the two are the same thing at two lengths and a sixth destination holding two rows would have
  been worse than a section. Format is PNG by default — it is what the game's own F2 writes, and a
  folder mixing the two sources should not also mix quality unless someone asked for that — and
  the JPEG quality slider only appears once JPEG is chosen, because a slider that does nothing is
  the one thing a settings screen must never show.
  It gets **no comparison-table row**, which is the one deliberate exception to the rule above.
  Upstream can bind F2 exactly as this can, so every honest mark would be a tie, and a table row
  that says nothing is worse than no row — the table is only worth reading because it is edited.
- **Gallery export** (`media/GalleryExport.java`) — everything the launcher writes lives under
  `Android/data`, which the media scanner does not index and, from Android 10, cannot be made to:
  that directory is outside the media store's scope. So a screenshot or a clip that is only there
  is one no gallery, no chat app and no share sheet will ever see. A copy goes to an "Amethyst X"
  album in Pictures or Movies, through the media store on Android 10 and up (no permission, no
  scanner) and through the public directory plus a scan below it, which is what the
  `maxSdkVersion="28"` storage permission in the manifest is for.
  It is a **copy, not a move**, and that is the decision: the game's screenshots folder is where
  Minecraft's own F2 writes too, and both Game files and the recordings gallery read those folders
  directly, so moving the file out would empty two screens the player already uses. The cost is a
  second copy, which the setting says in as many words rather than leaving to be discovered.
  A video is written **pending** and only revealed once every byte is there, so a half-copied clip
  is never offered up for playing, and the copy runs on its own thread after the player has been
  told the recording is saved — gigabytes must not be what stands between them and the game.
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
- **Typing preview** (`ui/game/TypingPreviewHost.kt`) — a strip at the top of the game holding
  what is being typed, because the pan that lifts a text field clear of the keyboard pushes it off
  the top of a short landscape screen instead, and the only way to check a typo was to close the
  keyboard, look, and open it again.
  It **mirrors what was sent and never claims to be the field**, which is the same wall `LiveTyper`
  lives behind: nothing on the launcher side can read a pixel the game drew. Everything else
  follows from taking that seriously. Backspacing past the first character it saw means the game
  deleted something it never had, so it puts an **ellipsis on the front and carries on** rather
  than dropping the keystroke or hiding — everything after the mark is still true, and hiding would
  take the feature away at the exact moment it was in use. Anything else that loses its place, a
  caret key on the launcher's own board or a dictation starting, lands in the same state through
  `forget()`. It **watches a session rather than a lifetime**, a session being one spell with a
  keyboard open, because with no keyboard up there is no pan and the field can simply be read.
  Three things make it work. It is fed by **wrapping the sender** rather than editing one: the
  system keyboard's characters all pass through `CharacterSenderStrategy`, and `watch()` returns
  the sender untouched when the preference is off, so two upstream files on the input path needed
  no change at all. It is the **one overlay that cancels the pan** instead of riding it, since the
  frame every overlay lives in is the thing being translated and a strip that moved with it would
  leave the screen exactly when wanted. And it **shares the top band with the screenshot toast by
  z-order alone** — declared first, so the toast covers it for its two seconds and it is still
  there afterwards, with neither needing to know the other exists.
  Its settings sit with keyboard panning in a **Typing** section of their own, which also takes
  panning out of Buttons, where it was the one row that had nothing to do with a button. The two
  are halves of one answer to one problem, which is why they are now next to each other.
- **On-screen controls** — `ControlSkin` decides how a control is drawn **at draw time and never
  writes to the layout**, so turning it off gives the author's colours back. `ControlGlyphs` picks
  an icon from **the key a button sends**, not its name, so old layouts gain icons with no
  migration; a button bound to two keys keeps its text. `assets/default.json` is a Pocket Edition
  shape written in the simple expression vocabulary so it can be read and checked (§19).
- **Control textures** (`customcontrols/textures/`) — a folder of PNGs the buttons wear instead of
  a fill and a keyline, so a Bedrock-style face is something a player can make rather than
  something the launcher has to ship.
  **One setting, not two.** The Pocket switch became a `Button style` choice — Layout colours,
  Pocket, then the installed packs — because nobody thinks "Pocket on, and separately which
  texture". Two controls would have needed a documented rule about which wins and would have left
  the switch doing nothing whenever a pack was chosen. The old boolean is read once to migrate.
  **The format is three files and stops there**: `button.png`, an optional `button_pressed.png`,
  an optional `pack.json` carrying `slice`, `smooth` and `label`. Per-button artwork was designed
  and cut. The obvious key for it is the button's *name*, and the name is the weakest identity in
  the system — free text that can be empty, duplicated, translated, or literally `..` — and
  freezing it into a format players share would be permanent. Icons are keyed on **the key a
  button sends** for exactly this reason; if per-button art ever earns its place it goes there.
  **`label` is not decoration.** The label and the glyph are white, which works over the
  translucent dark fills the flat skins draw and disappears over the pale stone the headline use
  case is made of. A pack that needs dark content says so and gets it, defaulting to today's
  white. A glyph that cannot be read at a glance mid-fight is the one failure those icons exist to
  prevent.
  **A press and a latch wear the artwork's silhouette**, by redrawing the texture through a colour
  filter rather than by the rounded rectangle the flat skins use — that rectangle's radius is the
  layout's corner percentage, which is precisely the number a texture has stopped drawing, so it
  would bleed past a rounded face or cut across a square one.
  Packs live in `controltextures/`, **not** under `controlmap/`: the two surviving layout dialogs
  list every directory there, so a folder inside it would appear in the layout picker forever.
  There is an importer because there has to be one — `Android/data` is unbrowsable from Android 11
  — and it takes a zip and looks inside it the way Game files does, canonicalising every entry
  path because this is the launcher's second archive from a stranger.
  The nine-slice geometry is checked by `scripts/check_textures.py` against the shipped Java: 8400
  button sizes, that the cells tile exactly and that none is ever empty or inverted.
  **Eleven packs ship with it**, generated by `scripts/gen_control_textures.py` rather than
  hand-painted, because a face in this format is a rule and not a picture: the middle cell is
  *stretched*, so the interior has to be flat, an edge may only vary along the axis it is not
  stretched on, and every bit of character lives in the four corners. A bevelled frame around a
  plain middle is not a style choice, it is the shape the format draws, and it is also exactly what
  a Bedrock button looks like. Written as a generator the rule can be argued with and re-run; as
  eleven PNGs it could only be replaced. They are **read from assets and never copied out**, which
  is §12.12's lesson taken the other way: a copy in the game folder can be deleted, edited, or left
  at an old version, and each of those is a state to reason about.
  The picker is a **rail of previews, not a list of names**, for the same reason the roadmap wants
  one for layouts: a texture pack is entirely a picture and choosing one by folder name is absurd.
  The tiles are drawn by `ControlTextureDrawable` itself, given the bounds a real button would give
  it, so a preview cannot drift from what a button does; and they are drawn **over sky and grass**,
  because most faces are translucent and against a settings surface every one of them would read
  as the same dark rectangle, hiding the single thing worth knowing, which is whether the pack is
  legible over a bright world.
  **Making one starts from a pack that already works.** `TexturePackExport` writes the selected
  style out to Downloads as a zip the importer takes straight back, with the only documentation
  this format has inside it. Downloads rather than beside the packs for the same reason the
  importer exists at all: `Android/data` is unbrowsable from Android 11, so a README shipped next
  to the artwork would be a file nobody could open.
- **The Bedrock layout** (`assets/Bedrock.json`) — a second shipped layout: joystick on the
  left, the staggered Bedrock action cluster on the right, chat / keyboard / pause / menu
  top-centre the way the Pocket UI arranges its trio, and the two Java leftovers (drop,
  perspective) small in the top-left where the Java HUD draws nothing. Pair it with a texture
  pack and the controls are the Bedrock screenshot it was drawn from.
  Five decisions hold it up. **The cluster is two rows, not Bedrock's three**, because 56dp
  buttons at 175% scale on a 360dp-tall phone leave no height for a third; the budget decides the
  shape, down to the upper row sitting at 126 rather than the stagger's exact 130 so it clears a
  320dp-tall screen. **The third column rides the zigzag rows** rather than sitting at the
  bottom, because at 640dp the game's own hotbar reaches that far across and a button at hotbar
  height there steals slot taps. **The top row can be centred** only because the drawer pull tab
  hides itself whenever a layout carries a menu button, which this one does — and that same fact
  is why **keyboard, pause and menu stay visible while a GUI is open** (`displayInMenu`): every
  GUI ungrabs the cursor, ungrabbed hides the in-game controls, and with the pull tab gone a
  layout that kept nothing visible would strand the player in the chat box it had just opened.
  The adversarial review caught exactly that in the first draft, and `check_layouts.py` now
  asserts it for every shipped layout.
  **The ungrabbed state needs a pointer as much as it needs a way out**, which the first draft
  missed and a user found: three buttons is what a GUI shows, and none of the three was a cursor.
  `SPECIALBTN_VIRTUALMOUSE` on a control button is the only route to `MainActivity.toggleMouse`
  anywhere in the launcher, so a layout without one cannot summon the touchpad, and with "virtual
  mouse at start" on it cannot dismiss the one that came up at surface-ready either. That is the
  worse half: while the touchpad is displayed `InGUIEventProcessor` skips `sendTouchCoordinates`,
  so it also takes away the tap-to-position path that had been making menus work without it. The
  Mouse button is top-right, and being a virtual-mouse button it is `isHideable = false`, so it is
  the one control this layout always draws. `check_layouts.py` asserts the third clause now.
  And it is **copied out once and never refreshed**: a
  layout is a user file the moment it lands (the editor writes back to that exact path), so a
  correction arrives as `new_Bedrock.json` rather than stamping over rearranged buttons, on the
  same terms `default.json` ships `new_default.json`. It has to arrive somehow, because
  `Android/data` is unbrowsable from Android 11 and a shipped layout that is wrong on disk is one
  nobody can delete by hand.
  The joystick is `absolute` — fixed in place with the knob tracking the finger, which is how the
  Bedrock stick behaves; the floating stick that re-centres under every touch is the other value.
  Selected from the editor's Select-default dialog, which lists `controlmap/` and now finds it.
- **Timed key sequences** (`ControlData.sequence` + `ControlButton.startSequence`) — a button's
  four keycodes can fire in order, a configurable gap apart, instead of together. Built because
  the community asked for exactly this move: pearl slot, use, wind charge slot, use, on one
  button; the game samples the hotbar once a tick, so "together" is the one way it can never
  work and the gap's floor is one tick. Four decisions hold it. **One press is one run** and
  the release edge is ignored, so a finger lifting mid-move cannot cut a pearl throw in half.
  **A press during a run is dropped**, never queued: queued repeats are how a nervous double
  tap becomes four pearls, and this staying a combo and not an autoclicker is the safety line.
  **The whole run is one self-advancing runnable**, because a queue of anonymous lambdas
  cannot be taken back and `onDetachedFromWindow` must be able to cancel it and release
  whatever a completed step left held (the keyboard's latch lesson). And **stay-pressed and
  in-order are mutually exclusive in the editor**: one holds keys until the next tap, the other
  releases them on a clock, and a written combination has no meaning a switch label could
  predict. The fields are additive, so old layouts deserialise untouched.
- **Turnip driver manager** (`utils/TurnipDrivers.java` + `egl_bridge.c` + the Performance
  screen) — import adrenotools driver zips and pick which Vulkan driver Zink renders through.
  Upstream declined this (their issue 224, "PR it"); the loader machinery was already here,
  hardcoded to the one bundled `libvulkan_freedreno.so`. The feature is the choice, not the
  loading: `POJAV_TURNIP_DIR`/`POJAV_TURNIP_SONAME` point the existing namespace loader at an
  imported folder, and every failure still lands on the system driver, so the worst a bad
  import does is not get used. Drivers live under **internal** `getFilesDir()`, non-negotiably:
  `Android/data` is mounted noexec and a library there can never be dlopened. The import
  canonicalises entry paths (the launcher's fourth stranger archive), requires `meta.json`
  naming the library, and refuses anything that does not parse as an arm64 ELF, so an x86
  driver fails at import and not in game. The rows exist **only on Adreno GPUs**: a control
  that can only make the game worse is not a control, and the thread that asked said the same
  about other chips. The old "prefer system driver" boolean folded into the picker, read once.
  Importing selects, like the texture packs; deleting an import falls the choice back to the
  bundled Turnip, and only the launcher process ever rewrites the preference.
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
- **Mod browser** (`ui/mods/` + `modloaders/modpacks/api/ModrinthMods.java` +
  `ModInstall.java`) — searching Modrinth and putting the jar in the profile, without leaving the
  launcher.
  **The mod search that already existed searched modpacks.** `SearchModFragment` sets
  `isModpack = true` and never unsets it, and `ModrinthApi.installMod` carries a TODO saying it
  only handles modpacks: what it does is download an `.mrpack` and build a whole new profile. So
  the launcher has never had a way to add one mod to a profile you already have, and the way
  people actually do it is a browser, a download, and the Game files importer.
  **The filter is the feature.** The profile knows its Minecraft version and its loader, so the
  search is narrowed to what will run before a character is typed, and installing is one tap
  because there is then only one sensible version to pick. Re-presenting the mod page's version
  table as a dialog would be putting back the step this exists to remove. `ModTarget` recovers
  the two facts differently on purpose: the loader is in the version id and nowhere else, and the
  Minecraft version is in the id for three loaders out of four, with NeoForge falling back to
  `inheritsFrom` in the installed manifest.
  **It is a second Modrinth client, not a widened one.** `ModrinthApi` reads `game_versions[0]`
  as the version and `files[0]` as the download, and for a modpack both are right because there
  is only ever one of each. For a mod the first game version is the *oldest* of a range and the
  first file is as likely to be a sources jar, so bending that class would have broken the
  modpack path to fix the mod one. Neither calls the other.
  **Only required dependencies are installed.** Optional is a suggestion, and a launcher that
  acted on suggestions would put jars in someone's folder that they did not choose and cannot
  attribute later. The walk is breadth-first with a seen-set and a depth cap, because dependency
  graphs have cycles and the cost of being wrong is a phone downloading until it is full.
  Reached from Game files' add button, which now asks which of the two kinds of adding you meant.
  **A row opens a page** (`ModProjectScreen.kt`), a route inside the activity so Back lands on
  the results with the query and scroll intact, the same call the version picker made. The page
  draws in arrival order: the header comes from the search hit before any request returns, and
  the body, gallery and version list fill their sections as their own fetches land. Sorting and
  a category filter ride the same facet machinery as the profile filter; category labels are the
  index's own tags tidied rather than translated, because they are what the Modrinth site shows
  and a picker that agrees with the site can be followed from a mod's install instructions.
  **The description renders a documented subset of Markdown** (`ModMarkdown.kt`): headings,
  paragraphs, lists, code, emphasis, links. A full renderer is a library this launcher does not
  ship (no R8, so every dependency ships whole); everything outside the subset degrades to its
  text, and "Open on Modrinth" is the honest way to the whole page. Gallery pictures are fetched
  bounded and downsampled to the strip they sit in, and a page fetch that outlives its page is
  dropped by id rather than landing on whichever mod was opened next.
- **Skin editor** (`ui/skin/` + `skin/SkinUpload.java`) — make a skin, look at it on a model,
  put it on your account. Asked for by someone who could not reach their skin folder, which is
  not laziness: everything the launcher writes is under `Android/data`, and from Android 11 that
  cannot be browsed at all, so a skin there is a skin nothing can pick up. The launcher is the
  only thing that can see that folder, so it has to be the thing that offers them.
  **The UV table is stated once** (`SkinModel.kt`) and everything reads it. The atlas is a layout
  Mojang chose, not a derivable one: top and bottom faces sit above the sides, the four sides run
  right, front, left, back, the arms and legs moved in 1.8, and there is a whole second set of
  rectangles for the outer layer at two arm widths. Encoded twice, the two copies drift and a leg
  ends up wearing a sleeve. `scripts/check_skin_uv.py` checks it against independently written
  ground truth, plus bounds, overlap and the slim probe.
  **The preview is software, and that is not a compromise.** The game owns the only GL surface
  (§12.4), so a launcher-side model must not need one. Under an orthographic projection every
  face lands as a parallelogram, which is exactly what a 2x3 affine matrix draws, so each face is
  one `drawImage` through a matrix and there is no per-pixel work in Kotlin at all. Faces are
  culled by the sign of the projected basis and painter-sorted by box depth, which is correct
  because the parts are convex and do not interpenetrate. Verified by reimplementing the same
  maths in Python and looking at the render: a mirrored limb or an inside-out head is invisible
  in source and obvious in a picture.
  **You paint a face, never the atlas.** The raw sheet has the head's top wedged above its sides
  and the left arm in a bottom corner; nobody can paint while looking at it. So the canvas is one
  rectangle blown up with a grid and a checkerboard behind it, because a skin is full of
  deliberate transparency and a flat backdrop makes "erased" and "painted the backdrop colour"
  identical. The model beside it updates on every stroke, which is the thing that makes a phone
  editor usable at all.
  **Undo is a diff, not a snapshot.** Twenty snapshots of a 64 by 64 image is a third of a
  megabyte held live; a stroke touches tens of pixels. The whole drag is one step, because
  undoing a stroke a pixel at a time is not undo. **The fill is bounded to the face**, since the
  atlas is one image and an unbounded fill of a transparent area runs straight out of the head
  and repaints the entire skin.
  **The palette is fixed and there is no colour wheel.** A wheel on a phone is a fiddly control
  that gets you a colour you did not mean, and skins are made of skin, cloth and hair, which is a
  small knowable set. The eyedropper covers the rest: any colour already in the skin, including
  one that arrived in an imported picture, is one tap away.
  **Slim is stored in the pixels**, not in a preference, because it has to survive being handed
  to Mojang and read back on another device, and the pixels are the only thing that travels.
  **Applying is Mojang's business and the screen says so.** A server asks Mojang what a player
  looks like, so an offline account has nothing to ask about, which is exactly what the community
  thread concluded before this was built. The editor still works for them and the screen explains
  why that is all it can do, rather than appearing to work.
- **Installing a mod loader** (`modloaders/LoaderIndex.java` + `ui/loaders/` +
  `fragments/LoaderInstallFragment.kt`) — one screen for Fabric, Quilt, Forge and NeoForge, asked
  from the Minecraft version rather than the loader.
  **The old flow has the question inside out.** It makes you choose a loader before it can tell
  you what that loader supports, then find your Minecraft version in a spinner of seven hundred
  entries, then a build in a second list, on one of four near-identical screens. Nobody thinks "I
  want Forge, and separately, for what version?" They think "I want to play 1.20.1 with mods", and
  which loaders can do that is a single fact that was never shown anywhere. So a row is a Minecraft
  version and the loaders sit on it, each already naming the build it would install.
  **Everything is fetched once.** Six requests: a game list and a loader list each for Fabric and
  Quilt, and one maven-metadata.xml each for Forge and NeoForge, which is every version they have
  ever released. After that, typing filters an in-memory list. The flow this replaces fetched
  loader versions *per selected game version*, so changing the version cost a round trip every
  time.
  **Fabric's matrix is not a matrix**, which is what makes that possible: its loader is independent
  of the game version, which is why the meta API serves the loader list with no game version in the
  URL. Asking per version was always answering a question with one answer.
  **The install machinery is untouched.** The same `FabriclikeDownloadTask`, `ForgeDownloadTask`
  and `NeoForgeDownloadTask`, the same listener proxy, the same handoff of the Forge and NeoForge
  installer jars to `JavaGUILauncherActivity`. Only the choosing changed. **The four old fragments
  stay wired up**, deliberately: none of this can be tested on a device from the build container,
  and a new way in should not be the only way in.
  Two version rules are stated because they cannot be derived. Forge's maven id splits on its
  **first** hyphen, since `1.7.10-10.13.4.1614-1.7.10` splits on its last into a Minecraft version
  of "1614". NeoForge states its Minecraft version nowhere and encodes it in the build number,
  `21.1.66` meaning 1.21.1 and `21.0.167` meaning 1.21 with the trailing zero dropped, except for
  their first line which kept Forge's numbering and is `47.x` for 1.20.1. `scripts/loadersim`
  pins both.

- **Finding skins** (`skin/MojangSkins.java` + `ui/skin/SkinBrowseScreen.kt` +
  `ui/skin/SkinHistory.kt`) — any player's skin by name, what Mojang says is on the account, and
  a record of what has been worn.
  **Mojang keeps no skin history, and that is the fact the whole thing is built around.** There is
  no endpoint for it and there never has been; the profile response carries a `skins` array with a
  `state` of ACTIVE or INACTIVE which looks exactly like one and in practice holds the skin being
  worn now. The name history endpoint that did exist was withdrawn in 2022. So a history has to be
  kept by whatever applies the skins, the screen says so in as many words rather than implying the
  list is complete, and the two things that genuinely can be read are read: the live skin on the
  account, and any inactive entries where Mojang does return them.
  **A history entry owns its pixels.** The PNG is copied into the history folder when it is
  recorded, never referenced where it already sits, because the library file is one the editor
  writes to in place: a reference would turn "the skin I wore in March" into "whatever that file
  says today". It is **recorded only on a successful apply**, that being the single point at which
  a skin actually goes onto an account and therefore the only place that can honestly claim one
  was worn.
  **Any player's skin is the catalogue, and it is Mojang's own public API.** A name gives a UUID
  (`api.mojang.com`), a UUID gives a profile whose `textures` property is a base64 blob holding the
  skin URL (`sessionserver.mojang.com`). No key, no account, and every account that has ever
  uploaded a skin is in it, which makes it the largest skin database there is and the one every
  skin site is built on top of. It is a **lookup and not a browsable catalogue**, which is a real
  difference and is stated rather than dressed up: see the limitations for why there is no third
  option.
  **The parse is split from the fetch** (`parseOwnSkins`, `parsePlayer`), because Mojang is not
  reachable from the build container and the parse is both the only checkable part and the part
  that fails silently. The slim flag is at `textures.SKIN.metadata.model` and is **absent rather
  than false** for a classic skin; the variant on the account path is `SLIM` in upper case and
  `slim` in lower on the public one; and `textures` is not promised to be the first property in
  the array. `scripts/skinapisim` drives all three, and every one of them was made to fail on
  purpose before being trusted.
  **One definition of slim on disk.** `writeArmWidth` is shared by the gallery's model switch and
  by saving a skin found on another player, so a slim skin cannot arrive wearing classic arms
  through the second door.

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
- **Performance mode** (`optimiser/` + `ui/settings/PerformanceSheet.kt`) — one switch that reads
  what the phone actually is and sets the renderer, the resolution, the heap, Minecraft's own
  graphics settings and a mod set to match. The single most asked-for thing a Minecraft launcher
  on Android can do, and the launcher already knew everything the answer needs.
  **The plan is a pure function**, and that is what the whole feature hangs off: `PerformancePlan`
  takes primitives and returns a list of changes rather than performing them, so the same object
  can be shown before anything is written, captured for the undo, and driven by
  `scripts/plansim` with no device in the room. A version that wrote settings as it worked them
  out could be none of those three things.
  **The resolution is solved against a pixel budget, not set to a percentage.** A 1440 by 3168
  panel is four and a half million pixels and the game pays for every one every frame, so the tier
  names a number of pixels it can afford and the scale is `sqrt(budget / panel)`. That is why the
  same tier gives 70% on a OnePlus 12 and 95% on a 1080p phone; a fixed percentage would blur the
  second to fix the first.
  **It never undoes somebody's own tuning.** Every graphics option carries a bound, and the
  direction that means "faster" is stated per key rather than inferred, because it genuinely
  differs: `graphicsMode` counts *up* from fast to fabulous and `particles` counts *up* from all to
  minimal. A player already on minimal particles is not raised to decreased because a flagship tier
  says so. On a flagship, where the tier asks for very little, that guard is most of what the plan
  does.
  **The distances are the exception, and the mods are what buy them back.** Render distance is set
  outright: eight chunks on a flagship without mods, twelve with Sodium. A preset that only ever
  takes things away is the one everybody turns off after an evening, and "not everything at zero"
  is the difference. Particles land on decreased rather than minimal for the same reason: minimal
  hides crit sparks and potion effects, which is feedback the game is played on.
  **`vulkan_zink` is never chosen**, and that is a design decision rather than a performance one.
  It renders through OSMesa, which has no EGL surface, so selecting it would silently take
  recording away (§11) as the price of a plan the player was told was about frame rate. Under
  1.17 the plan picks GL4ES and from 1.17 MobileGlues, because that boundary is the game asking
  for OpenGL 3.2 core, which GL4ES cannot serve at all.
  **Nothing writes JVM flags.** They are the one lever where being wrong does not cost frames, it
  costs a game that will not start, and CI has no device to find that out on.
  **The undo is the thing that makes the switch acceptable.** Every value is captured before
  anything is written, including the profile's renderer, which rides in the same capture under a
  reserved `@profileRenderer` key because it is part of the same undo even though it lives in
  `launcher_profiles.json`. A key that was never set comes back as never set (`PerformanceBackup`),
  which matters because several of these are computed from the device on first run and writing a
  number into them would freeze the answer to whatever phone was in hand.
  **The mods do not come back**, and the sheet says so in as many words. They are jars in a folder
  the player owns, listed on Game files, and some keep configuration beside them; deleting
  somebody's files because a switch was turned off is a much larger promise than restoring a
  setting. Quilt profiles ask Modrinth for a Quilt build and fall back to the Fabric one, which is
  Quilt's own advice and the difference between five of six mods missing and none.
  It gets an **onboarding page**, and the rule about pages (§14, "one feature you would not
  otherwise find") had to be argued for rather than assumed: the switch is the first row of
  Settings and perfectly findable. What somebody would not find is the reason to look. Its
  comparison row is **`Part` for upstream, not `No`**: upstream does pick a resolution and a heap
  size from the device on first run, which is device-specific automatic configuration by any
  honest reading, and a table that claimed otherwise would be worth less than one row.

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
- **No em dashes in anything a user reads.** They are the single clearest tell that a sentence was
  machine-written, and a launcher that says "A bit short — names are at least 3 characters" reads
  like a generated one. A full stop, a comma or a colon says the same thing and sounds like a
  person. This applies to `values/strings.xml` and to any literal that reaches the screen; the
  prose in this file and in KDoc is not user-facing and keeps them.
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

18. **Adding a parameter to a shared composable re-points every positional call site.**
    `SwitchRow` gained `value`, `iconRes` and `inert` before its `onCheckedChange`, which is
    harmless for the callers that pass the callback as a trailing lambda, because a trailing
    lambda always binds to the last parameter whatever comes before it. It is not harmless for
    the ones that pass it as the fourth positional argument, and `ControlEditorPanel.kt` had
    eight of those. The check that missed it was mine: I grepped for the `) { ... }` shape,
    confirmed every hit was a trailing lambda, and concluded the change was safe, having never
    looked for the other shape at all. **A grep that only matches the form you expect is not a
    survey, it is a confirmation.** `scripts/check_settings_calls.py` now reproduces Kotlin's own
    "No value passed for parameter" against every call site of every shared settings row.

19. **Skin upload is a POST; the cape endpoint next to it is the PUT.** Every attempt came
    back 405 Method Not Allowed, which is a precise thing to be told: the path exists and the
    method is wrong, or it would have been a 404. `POST /minecraft/profile/skins` uploads a skin,
    `PUT /minecraft/profile/capes/active` selects a cape, and the two sit beside each other in
    every description of that API. **What actually found it was the error surfacing added an hour
    earlier**: the first version printed "Mojang would not accept that skin" and threw the
    response away, and no amount of staring at that sentence would ever have produced the answer.
    A remote failure that cannot be reproduced locally is worth the code that makes it explain
    itself, and that code pays for itself the first time it runs.

20. **A fixture that only carries the shape you expect agrees with your bug.** Mojang hands out
    texture URLs as `http://textures.minecraft.net/...`, in plain HTTP, inside a response fetched
    over HTTPS. `MojangSkins.texture` required `https://` and rejected every real one, so no skin
    ever loaded. Every fixture in `skinapisim` used `https://`, which is why 57 checks passed on
    a client that could not fetch a single picture: the harness was written from the same
    assumption as the code, so it confirmed the assumption instead of testing it. **When a
    harness and the code it checks are written by the same hand in the same hour, the fixtures
    have to come from what the API sends, not from what the code expects.**
    It also shipped with the symptom disguised. The picture was missing, and the screen said
    "this player is wearing a default skin", which is a real answer to a different question:
    a default skin and a failed download look identical and are not the same fact. That is the
    second time in this feature that a specific failure hid behind a generic sentence (see 19),
    and the two are now told apart on the card as well as in the message.

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
- The floating shutter **starts** where the default control layout has room, so a custom layout
  can have one on top of a button until it is dragged off it. It is dragged with a long press,
  which is a gesture nothing announces except the hint on the row that turned it on.
- The gallery copy **doubles the space** a screenshot or a clip takes, and a 40-minute recording
  is gigabytes. It is skipped, with a log line and nothing else, when the volume does not have
  room for it — a gallery copy is a convenience and is not worth filling a phone for.
- Below Android 10 the gallery copy needs `WRITE_EXTERNAL_STORAGE`, which nothing in the launcher
  asks for at runtime. Where it has not been granted the copy is skipped silently; the file is
  still written to the game folder, which is where both in-app galleries read it from.
- Screenshot settings are read **when the game process starts**, like every other in-game setting
  here, so changing the shutter's size takes effect at the next launch rather than at once. The
  format is read per picture and the dragged position is written as it happens, since neither
  costs anything.
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
- Both shipped layouts stay on screen from 80% to 175% button scale on every screen the check
  sweeps, down to a 320dp-tall hdpi phone, and overlap-free through 125% on ordinary screens.
  Past that on a phone, buttons start to cover each other: the top rows meet in the middle and
  the action cluster can reach the joystick. Inherent to this many controls at that size, and
  both layouts are editable. The 250% end of the scale slider is outside what any shipped layout
  survives on a phone.
- `Bedrock.json` is never stamped over in `controlmap/`, because the editor writes back to that
  exact path and an update would take away rearranged buttons. A corrected copy lands beside it as
  `new_Bedrock.json`, so anyone who launched the game before the fix has **two Bedrock entries in
  the layout picker** and has to pick the new one. That is the same deal `default.json` makes, and
  the alternative is either overwriting a user's file or shipping a fix that never reaches them.
- **A layout with nothing on screen looks broken even when it is working.** Every GUI ungrabs the
  cursor, and the ungrabbed state draws only the `displayInMenu` controls, so a sparse layout at
  the title screen shows a handful of buttons and nothing else. The pull tab, which would normally
  hint at more, hides itself whenever a layout carries its own menu button. Nothing is wrong and it
  reads as a failed load; `check_layouts.py` now enforces a floor of menu, keyboard and mouse, but
  the floor is a minimum and not a design.
- A pack that ships with the launcher **cannot be removed**, only switched away from. It lives in
  assets, so it costs nothing and is always intact, but the picker will always list all eleven.
- The shipped packs' interiors are **flat by necessity**, not by taste. Ore speckle, plank grain
  and stone noise all sit in the middle cell, which is stretched across the button, so they would
  smear. What tells the packs apart is the frame.
- **Exporting needs Downloads to be writable.** Below Android 10 that is `WRITE_EXTERNAL_STORAGE`,
  which nothing asks for at runtime, so on those devices the copy can fail and says so.
- A texture pack draws **every button the same**, because the format has no per-button artwork
  (see §14 for why the button's name is the wrong key for it). A layout is told apart by its
  glyphs and its labels, not by nineteen different faces.
- The **joystick is not textured**. It is an external library view with its own three colour
  setters and no drawable to replace; a pack applies to buttons, drawers and sub-buttons.
- A pack is read **when the layout is loaded**, so a new one takes effect at the next launch or
  the next return from Settings, not while a game is running.
- A pack whose folder has been deleted leaves its name selected in Settings and draws flat. The
  game process deliberately does not rewrite the preference to correct it: both processes cache
  the whole preference file, and the launcher would be the one to lose the change.
- Packs are capped at 1024px a side and **refused rather than downscaled** past it, because the
  nine-slice inset is written in source pixels and a quietly halved bitmap would be sliced in the
  wrong place.
- **Control glyphs cover the actions a player recognises**, not the whole keyboard. A button bound
  to F7, or to two keys at once, keeps its text label on purpose.
- The typing preview shows **what you typed, not what the field holds**. A field that already had
  text in it, or one edited with the arrow keys, is text it never saw; it marks that with a leading
  ellipsis rather than pretending otherwise. It also cannot move the caret, and deliberately draws
  no caret, because drawing one would promise exactly that.
- The preview is **cleared whenever every keyboard closes**, so reopening one starts it empty. That
  is the honest boundary: with no keyboard up the field is not covered and can be read directly.
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
- A key sequence is **at most the four keys a button holds**, one press each, no loops and no
  repeats. That is deliberate: it is a combo, and the line between a combo and an autoclicker is
  the line between a control scheme and a cheat. A sequence also cannot wait on the game, only
  on the clock; a step that lands while a GUI is open types into it, exactly as the same key
  from a finger would.
- A sequence button held down does **not** repeat, and a toggle cannot be a sequence. Both are
  the same decision from two sides: one press, one run.
- The Turnip driver picker is **Adreno only, by presence**: on any other GPU the rows are not
  shown, search does not find them, and nothing is disabled because nothing is there. An
  imported driver is validated as an arm64 ELF in an adrenotools-shaped zip, and nothing more:
  whether a given Mesa build actually works on a given Adreno is between the driver and the
  phone, and the fallback to the system driver is what makes trying one safe.
- A driver import is a **copy into internal storage**, so it spends real megabytes, and an
  import with the same name replaces the previous one rather than piling up beside it.
- **There is no skin history to read.** Mojang does not keep one and never has, so "recently worn"
  is the launcher's own record and starts from the first skin applied through it. A skin set from
  the Minecraft website or another device shows up as the account's current skin and not in the
  record, because the launcher was not there when it happened.
- **There is no browsable skin catalogue, because there is no licensed API for one.** NameMC, the
  Skindex and Planet Minecraft have no public API and their terms forbid automated access, and
  shipping a library of other people's artwork is the licensing question the editor already
  declined. What is offered instead is Mojang's own public lookup, which is a name at a time.
- The session server rate limits **per profile per minute**, so looking the same player up twice in
  a row can come back as a pause request rather than a skin. It is reported as one.
- A player who has never uploaded a skin has **nothing to save**: Mojang serves them a default
  rather than storing one, so the profile is real and the textures are empty. The screen says that
  rather than showing a failure.
- Capes are **read and not used**. The lookup carries the cape URL because the profile does, and
  nothing in the launcher wears one; a cape is Mojang's to grant and not something a launcher can
  apply.
- The wire format is coded from the **documented** contract. None of `api.mojang.com`,
  `sessionserver.mojang.com` or `api.minecraftservices.com` is reachable from the build container,
  so `scripts/skinapisim` drives the shipped parsers against fixtures rather than against a
  captured response. It checks the things that break silently, not that the endpoints still answer
  in that shape.
- The history is capped at **40 entries**, oldest dropped first, and dropping one deletes its
  picture. A record that grows forever on a phone is a bug with a nice name.
- A skin can only be **applied** to a Microsoft account. Mojang's profile is the only thing a
  server reads a skin from, so an offline account has nowhere to put one. It can still be made,
  kept and previewed here, and the gallery says why in a sentence rather than failing at the
  moment of applying.
- The editor paints **one face at a time** and has no cross-face tools: no gradient, no
  selection, no copy between parts beyond mirroring a face. That is the trade for a canvas big
  enough to hit a pixel on a phone.
- Slimness is **guessed from the pixels**, because the PNG has no flag for it: a slim arm leaves
  the last two columns of its strip empty. A hand-made skin that paints there anyway reads as
  classic, and switching the setting rewrites those columns to make the guess true.
- The preview is **orthographic and unlit beyond flat face shading**. It is a good likeness of
  the inventory model and not of the game: no cape, no held item, no animation.
- **Nothing is preloaded.** The thread asked for skins to come with it, and shipping a library
  of them is a licensing question about other people's artwork rather than an engineering one.
  Importing a PNG is one tap, which is the honest version of the same thing.
- The loader installer covers **Fabric, Quilt, Forge and NeoForge**. OptiFine, BTA and LWJGL3ify
  keep their own screens: OptiFine's list is scraped from a download page rather than fetched from
  an index, and the other two are their own shapes. The four old per-loader fragments also stay,
  so nothing that worked before stops working.
- Whether a loader is **already installed is a guess**, read out of the profile's version id the
  same way the mod browser guesses at installed mods. Being wrong only means a pill does not say
  so, which costs a duplicate profile rather than a crash.
- The index is fetched **once per visit to the screen** and not cached across them. Six requests
  is a second or two on a phone connection, and a stale list of loader builds is worse than a
  short wait.
- **Forge and NeoForge still hand off to the Java installer.** They ship an installer jar rather
  than a profile, so the last step is `JavaGUILauncherActivity` running it, exactly as before. That
  screen is untouched and is still the old one.
- The list is capped at **400 Minecraft versions** after filtering, which is every release Fabric
  has ever supported and then some. Snapshots are behind a switch because there are thousands of
  them and they are not what anybody is looking for by default.
- The mod browser is **Modrinth only**. CurseForge needs an API key, and the one this repo has is
  a build config value for the modpack search; adding a second index is a bigger question than
  making the first one work.
- It installs **mods**, not resource packs or shaders, though the client takes a project type and
  the folders already exist. One kind at a time, and mods are the kind people ask for.
- **A mod's own compatibility is Modrinth's word for it.** The filter is the index's version and
  loader tags, so a mod tagged wrongly installs and does not work, and a mod that would work but
  is not tagged for your version is hidden until the filter is turned off.
- **Already-installed detection is a guess.** The search response has no file name in it, so a row
  is ticked when the project's slug appears in a file name in the folder. A false tick means
  installing over the top, a missed one means a duplicate jar; neither is worth a request per row
  to avoid.
- **The mod page's Markdown is a subset**, and says so in its own KDoc: headings, paragraphs,
  lists, code, emphasis and links render; tables collapse to their cell text, HTML is stripped
  to its words, and images inside the body become their alt text. The gallery is fetched; body
  images are not. A page that leans hard on the exotic reads plainer in the launcher than on the
  site, and "Open on Modrinth" exists for exactly that page.
- **The version list has no dates.** Modrinth sends `date_published` and the parser does not
  carry it yet; rows are told apart by version number, channel and file size, and the list is
  already newest-first because the server sends it so.
- The wire format is coded from the **documented** Modrinth v2 contract: `api.modrinth.com` is not
  reachable from the build container, so `scripts/modrinthsim/` drives the shipped parser against
  fixtures rather than against a captured response. It checks the things that break (primary file
  selection, null tolerance, version choice, facet syntax, name safety), not that the endpoint
  still answers in that shape.
- **`VersionSelectorDialog` still exists** for the modpack-search flow, which is the only caller
  left. The profile editor uses the Compose picker, and the mod browser needs no version dialog at
  all; the two should converge when modpack search is redesigned.
- A skin can only be **applied** to a Microsoft account — Mojang's API is the only thing a server
  reads a skin from, and an offline account has no profile to attach one to. Any skin editor has
  to say so rather than appearing to work and silently doing nothing.
- Performance mode **cannot be applied while the game is running**. Minecraft reads options.txt
  once at startup and writes its whole in-memory copy back on exit, so an edit made in between is
  overwritten a moment later with no error anywhere. That is why it lives in Settings and not in
  the in-game control center, and why the sheet says the changes land at the next launch.
- The plan is **verified by simulation, not on hardware** (`scripts/plansim`, plus the apply round
  trip in `scripts/optionssim`). What is checked is the decision: the pixel budget, the version
  gating, the bounds, the value formats against Minecraft's own option readers. Whether MobileGlues
  is actually faster than Zink on a given phone is between that phone and that translator, and
  there is no device in CI to ask.
- **Changing the renderer can stop a game that used to start.** The plan only ever moves to the
  translator the Minecraft version needs, and the preview names the change before it happens, but
  a device whose driver cannot serve MobileGlues will find out at launch. Turning the mode off puts
  the old renderer back, including putting back "follow the global default" when that is what it
  was.
- Sustained performance is turned **on** for the two top tiers, which deliberately **lowers peak
  clocks**. It is the throttling and heat answer, not the peak frame rate one: it costs the first
  two minutes and pays for the next thirty. The lower tiers do not get it, because a device with
  no headroom has none to give up.
- The mod set is **Modrinth only and keyed on slugs**. A project that renames its slug is a mod
  reported as having no build for this version, which is visible on the result screen and fixable
  from the mod browser. Whether a mod is already installed is **the same guess the mod browser
  makes** (the slug appearing in a file name), deliberately erring towards skipping rather than
  towards two jars of one mod, which is a crash.
- **Turning it off does not remove the mods.** They stay in the mods folder and can be deleted from
  Game files. The settings all come back.
- Minecraft's own settings are only written for a version the launcher could work out. A snapshot
  id, or a profile whose version cannot be read, gets the launcher settings and the keys whose
  spelling has never changed, and nothing that moved: `graphicsMode` replaced `fancyGraphics` in
  1.16, and `simulationDistance` and `prioritizeChunkUpdates` arrived in 1.18. Writing a modern key
  into an older file does not fail loudly, it fails silently.
- The heap is capped at **half the device's memory** on every rung of the ladder. Android does not
  swap, so a heap the JVM may fill is memory the system cannot take back.
- The switch on the row is **inert**: it reports and never moves on its own, because the honest
  answer to "is it on" takes a download to arrive at. Tapping the row opens the preview.
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
1. Nothing outstanding from the previous list; see the ledger in §14.

**Next**
2. Bring the runtime manager and gamepad remapper onto the new components (see §17), which also
   gets their settings into the search index.
4. A layout picker worth the name — the editor's Load is still a file list. Layouts should be a
   gallery with a preview, since a control layout is a picture, not a filename.
5. Recording segmentation for multi-hour sessions.
6. Modpack search — `SearchModFragment` and its CurseForge/Modrinth flow are still the old XML,
   and the one remaining `VersionSelectorDialog` caller. Single mods now live in `ui/mods/`;
   modpacks, which build a whole profile, have not moved.

**Later**
6. Shared-element transition from the version card into the version sheet.
7. Recordings: in-app playback and trimming rather than handing off to an external player.
8. Retire `activity_pojav_launcher.xml` chrome entirely once every fragment is Compose.

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
- **Run `python3 scripts/check_layouts.py`** if any shipped layout changed. `${...}`
  substitution is a plain string replace (`JSONUtils.insertSingleJSONValue`) and the result goes
  to exp4j, so the script reproduces it exactly: substitute, map `px(n)` to `n * density`, and
  evaluate every control across a grid of screens, densities and button scales. It asserts
  everything stays on screen, nothing overlaps through 125% (175% where there is room), and every
  keycode exists.
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
- **Run `python3 scripts/check_textures.py`** if the texture format or the shipped packs changed.
  Beyond the geometry sweep it checks the packs in the APK: that each has a decodable `button.png`
  under the size limit, a pressed face of matching dimensions, and a `slice` that leaves a middle
  band. Regenerate them with `scripts/gen_control_textures.py`, and **look at them** afterwards:
  the nine-slice can be reproduced in Python and rendered as a contact sheet at real button sizes,
  which is how a face that vanished over a bright sky and a corner radius that turned a small
  button into a circle were both caught before they shipped.
- **Run `sh scripts/modrinthsim/run.sh`** if the Modrinth client changed. It compiles the shipped
  `ModrinthMods` and `ModInstall` against stubs at source 8 and drives them with fixtures, which
  is the only check available: the API is not reachable from the build container.
- **Run `sh scripts/loadersim/run.sh`** if the loader index changed. It drives the shipped
  parsers against the shapes those four APIs send, including the historical Forge id that splits
  wrongly on its last hyphen and NeoForge's undeclared Minecraft version. None of those hosts is
  reachable from the build container, so the parse is the only checkable part.
- **Run `sh scripts/skinapisim/run.sh`** if the Mojang skin client changed. It compiles the
  shipped `MojangSkins` at source 8 and drives its parsers with fixtures: the base64 textures blob,
  a slim skin, a classic one with no metadata at all, `textures` not being the first property, a
  player with no textures, and a handful of responses designed to make a parser throw.
- **Run `python3 scripts/check_skin_uv.py`** if the skin atlas table changed. It checks the
  UV rectangles against independently written ground truth, at both arm widths, plus bounds,
  overlap and the columns the slim guess reads. A wrong rectangle is a leg wearing a sleeve
  and is invisible until someone opens the editor on a real skin.
- **Run `sh scripts/optionssim/run.sh`** if the options.txt merge or performance mode's apply
  changed. It drives both: the merge against an awkward hand-edited file, and the shipped
  `PerformanceMode.applyOptions` end to end, checking that a declined bound really is declined,
  that a second apply does not overwrite the backup, and that turning the mode off gives the file
  back byte for byte.
- **Run `sh scripts/devicesim/run.sh`** if the device tiering changed, and
  **`sh scripts/plansim/run.sh`** if the plan did. The plan harness is the one that matters most:
  every way it can be wrong is silent. It checks the pixel budget against real panels, the frame
  cap against Minecraft's own 10 to 260 range, the version gating against the game's option
  readers version by version, and that no bound can ever move a setting to the slower side of
  where the player left it.
- **Run `python3 scripts/check_settings_calls.py`** if anything in `ui/settings/SettingsComponents.kt`
  changed its parameters. It reproduces Kotlin's "No value passed for parameter" against every
  call site of every shared row, which is the one way an optional parameter added in the middle
  of one of them fails: a trailing lambda still binds to the last parameter, and a callback
  passed positionally does not.
- **Run `python3 scripts/check_keyboard.py`** if the on-screen keyboard changed — it parses the cap
  tables out of `GameKeyboard.kt` and checks the row weights, the keycode range, and that every key
  the old dialog could send is still reachable. A board is also worth *looking* at: the same parser
  can emit HTML and be screenshotted, which is how a row that does not line up gets caught.
- Read the whole diff.

CI builds Debug **before** Release, so a missing signing key never hides a compile error. Release
steps are skipped when `GPLAY_KEYSTORE_PASSWORD` is unset.
