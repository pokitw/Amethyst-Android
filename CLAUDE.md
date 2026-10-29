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
| `Warning70` | `#E8C07D` | A warning that is not a failure. Warm rather than yellow: on a log screen where a third of the lines can be warnings, a true amber reads as an alarm and makes the errors beside it count for less. |
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
- **The thing you touched is the thing that responds.** Pressing Play turns the launch card itself
  into the launch console, rather than reporting in a bar somewhere else. Press states squish the
  element pressed.
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
the launch path refuses on, which is why the launch card shows its console whenever the count is
non-zero rather than following any individual key.

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
13. **The polled key state is written where the event is dispatched, never where it is sent.**
    `keyDownBuffer` is the whole of what `glfwGetKey` reads, and the callbacks are queued, so
    stamping it at send time puts the poll a frame ahead of the callbacks and makes the order keys
    were sent in unobservable. That is not a micro-optimisation to restore: it is the Shift+F3 bug.
    `mouseDownBuffer` still has the same shape, deliberately left alone, since no chord the
    launcher can express is decided by a `glfwGetMouseButton` poll.

14. **A control's `pages` mask of 0 means every page, and that is load bearing.** Every layout
    anyone has ever saved has no such field, so Gson gives them all zero, and zero has to be the
    value that behaves exactly as the launcher did before pages existed. It is also why the mask
    canonicalises back to zero whenever it covers every page: two spellings of "all" would drift
    the first time somebody added a page. `ControlPages` owns both rules and `scripts/pagesim`
    sweeps them.

---

## 13. Compose migration status

| Surface | State | Notes |
| --- | --- | --- |
| Onboarding | **Compose** | `ui/onboarding/`, `OnboardingActivity.kt`; runs once, before the launcher is built |
| Launcher home | **Compose** | `ui/home/`, hosted by `MainMenuFragment.kt` |
| Recordings gallery | **Compose** | `ui/recordings/`, `RecordingsActivity.kt` |
| Clip editor · screenshot editor | **Compose** | `ui/mediaedit/`, `MediaEditorActivity.kt`; reached from both galleries. The clip preview is a `TextureView` in an `AndroidView`, driven by `ClipPlayer` |
| Game files (worlds, mods, packs, shaders, screenshots) | **Compose** | `ui/content/`, `ContentActivity.kt` |
| Profile / account pickers on home | **Compose** | `ModalBottomSheet` in `ui/home/HomeSheets.kt` |
| Settings | **Compose** | `ui/settings/`, hosted by `SettingsFragment.kt` |
| Runtime manager · gamepad remapper · MobileGlues tuning | XML, stays for now | Reached from the new Settings; see §17 |
| In-game control center | **Compose** | `ui/game/`, hosted by `MainActivity` **and** `CustomControlsActivity` |
| On-screen keyboard · voice overlay | **Compose** | `ui/game/`, each with its own bottom-anchored `ComposeView` |
| Typing preview | **Compose** | `ui/game/TypingPreviewHost.kt`, its own top-anchored `ComposeView` |
| Game loading screen | **Compose** | `ui/game/GameLoadingHost.kt`, hosted by `MainActivity`; up only until the first frame |
| Control layout editor menu | **Compose** | The control center in editor mode; the buttons it edits stay custom views |
| Control editor panel · key picker | **Compose** | `ui/controls/`, driven by `ControlLayout.setEditorHost` |
| Sign-in chooser | **Compose** | `ui/auth/`, hosted by `SelectAuthFragment.kt` |
| Profile editor · type picker · MC version picker | **Compose** | `ui/profile/`, hosted by `ProfileEditorFragment.kt` and `ProfileTypeSelectFragment.kt` |
| Crash screen | **Compose** | `diagnosis/`, hosted by `ExitActivity.kt` |
| Skin editor | **Compose** | `ui/skin/`, `SkinActivity.kt` |
| Mod browser (Modrinth) | **Compose** | `ui/mods/`, `ModBrowserActivity.kt` |
| Log viewer | **Compose** | `ui/logs/`, `LogActivity.kt`; parsing is Java in `logs/` so it can be driven (§19) |
| Modpack search's version dialog | XML | The one remaining `VersionSelectorDialog` caller; see §17 |
| Control buttons themselves | XML custom views | Deep custom view work; skinned rather than rewritten, see §14 |
| Game surface | XML, stays | See §12.4 |

Full screens share `AppScaffold` (`ui/common/`) for the back bar, the title that collapses into it
and the 20dp gutters. Settings hoists its scroll state through it, because search scrolls to a row.

The launcher's chrome (`activity_pojav_launcher.xml`: account bar, settings button, progress bar)
is **hidden by the fragment asking for it, through `ui/common/ChromeOwner`**, because a screen
that draws its own header must not have the old one above it. Settings keeps the progress bar — a
download started elsewhere has nowhere else to report from while it is open — so the interface
takes the two decisions separately, and `ProgressLayout.setSuppressed(boolean)` exists for that.

**It used to be a list of class names in `LauncherActivity`, and the list drifted.** It named home
and Settings, which was every Compose screen this activity hosted when it was written; the profile
editor, the profile type picker, the sign-in chooser and the loader installer were all added
afterwards and all drew an `AppScaffold` back bar and large title *underneath* the account bar.
Nobody saw it, because a class name in an activity is not somewhere anybody looks while writing a
screen. Declaring it on the fragment puts the decision next to the thing that makes it true.

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
  It gets **no comparison-table row**, which was the first deliberate exception to the rule
  above; the opening choreography is the second, on the same reasoning.
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
- **Slide to repeat** (`ControlData.slideRepeat` + `ControlButton.maybeArmRepeat`) — a button that
  is an ordinary button when tapped and keeps firing while you hold it and slide. Asked for in
  those words: place a single block with a tap, clutch by spam clicking with the same button.
  **It is one control, not two, and that is the whole feature.** The repeating on its own is
  nothing new; holding a stay-pressed button already makes the game place blocks over and over.
  What has never existed is getting it without giving up the tap: every other route is a second
  button somewhere else on the screen, or a toggle that has to be turned off again afterwards,
  and both mean the thumb leaves the place button at the exact moment it must not. So the second
  job hangs off a gesture on the first button.
  **The gesture is a slide because a slide is the one thing a button is otherwise deaf to.** A
  long press is taken twice over (it opens the editor, and it holds a dictation), and a double
  tap would cost every ordinary tap a delay before it could be sure it was not the first of two,
  which is a real cost paid by everyone to give a feature to some.
  Four decisions hold it up. **Release stops it**, always, with no latch: an autoclicker you
  cannot turn off is a bug however good it feels, and a clutch is held anyway. **The threshold is
  radial** (`pastSlideThreshold`, squared on both sides so nothing takes a square root on the
  touch path), because comparing the axes separately arms at 1.41 times the distance along a
  diagonal, which the player experiences as a gesture that works when they slide down and not
  when they slide diagonally. **The repeat leaves the pressed state alone** and wears a steady
  accent wash instead: `sendKeyPresses` sets it, and driven at twenty edges a second that is a
  strobe. And **four other behaviours are mutually exclusive with it**, enforced in the editor
  where the switches visibly move and again in `canRepeat` where a hand-edited file lands: a
  toggle has no press for a slide to modify, a sequence is already a clock on the same keys, and
  swipe and pass-through have both already spent the slide.
  The gap floors at one game tick for the same reason the sequence's does: Minecraft samples
  input once a tick, so a faster setting would be a number that does nothing. The fields are
  additive, so old layouts deserialise untouched.
  **The distance is drawn, not just numbered.** "20 dp" is a figure nobody has an intuition for,
  and the question actually being asked of that slider is whether the gesture fits inside this
  button or runs off it, which is a distance next to a size and therefore a picture. The editor
  rings the selected control at the real radius while the mode is on. It is centred, which is the
  honest average rather than the truth: the gesture is measured from wherever the thumb landed,
  so a press near an edge arms sooner on one side, and drawing every possible circle would say
  less than drawing one.
- **Joystick auto-walk** (`ControlJoystickData.autoWalk` + `ControlJoystick.engageAutoWalk`) —
  double-tap a direction on the movement stick to keep walking that way without a thumb sat on
  it, touch the stick again to take manual control back. The same idea as slide to repeat, aimed
  at the other thumb: a long tunnel or a walk to a village is the one thing a phone does worse
  than a keyboard, because a keyboard lets go of the key and a touchscreen does not.
  **Double-tap, which slide to repeat's own reasoning ruled out for a button** ("a double tap
  would cost every ordinary tap a delay before it could be sure it was not the first of two").
  That cost is real for a button, whose ordinary use is a single discrete tap that the gesture
  detector would have to sit on for a moment deciding whether a second one is coming. It is not a
  cost here: a joystick's ordinary use is one continuous drag, which is not built out of taps at
  all, so `GestureDetector` never has anything to disambiguate during normal play and the
  double-tap check for a single ordinary press-and-hold literally never fires. The same gesture
  that would tax every button press costs a moving stick nothing.
  **The direction is read from where the tap landed, not from the stick's own reporting.** The
  default tracking mode recentres the stick to wherever a touch begins, so a stationary tap
  always measures zero distance from its own centre and would never resolve to a direction at
  all if read the way a drag is. Measured instead against the view's own fixed centre, a tap
  works the same regardless of the absolute/relative setting, and it is what a screen tap already
  means: point where you want to walk.
  **Touching the stick always takes manual control back**, on the first down of any new touch,
  before that touch has any chance to become anything else. That is what lets a second double-tap
  redirect a lock already in place without a dead step in between: the first tap of the new pair
  cancels the old lock, the second engages the new one, exactly as if there had never been a gap.
  It is also the only cancel gesture that exists, deliberately: a player who wants to stop just
  touches the stick, which is the one thing every player already knows to do with it.
  **The locked keys are held independently of the stick's own state**, because the knob
  recentres after every tap whether the tap is starting a lock or ending one, and reading that
  recentre as "the stick let go" would release the very keys the lock exists to hold down. The
  ring wears the accent while locked, the one visible sign the stick is now driving itself; `onMove`
  is skipped entirely while locked and `onDetachedFromWindow` releases the held keys if the
  control is deleted or the layout torn down with the lock still on, the same "nothing may
  outlive the view that was holding it" rule slide to repeat and the sequence runner both follow.
  It gets a **comparison row**, `No` for upstream: unlike the launch console and the opening,
  which stay off the table because upstream has the same capability presented differently, this
  is a control upstream has no equivalent of at all, which is exactly what the table is for. It
  gets **no onboarding page**, the same as slide to repeat and the sequence runner before it:
  which stick has it is answered in the editor, where it was turned on.
- **Clicking with a second finger** (`InGUIEventProcessor` + `PREF_GUI_SECOND_FINGER_CLICK`) —
  with the virtual mouse up, a tap anywhere by a second finger clicks where the pointer already
  is, so the finger steering it never has to be lifted. Reported as a plain gap: the click simply
  did not register.
  **The tap was already being detected**, which is the whole story. `TapDetector` watches
  `ACTION_POINTER_DOWN` and `ACTION_POINTER_UP` as carefully as it watches the first finger's,
  and it was returning true for exactly this gesture; `processTouchEvent` only ever read its
  verdict inside the `ACTION_UP` branch, so a tap made while another finger was still down was
  worked out and then dropped on the floor. The fix is a case in a switch, not a gesture
  recogniser, and reusing the tuned detector rather than writing a second one is what keeps the
  two kinds of tap agreeing about what a tap is.
  **A scroll is told from a tap by what happens after the finger lands, not before it.** Two
  fingers moving together already scrolls, and at the instant a second finger touches down the
  two gestures are identical, so the click waits for the lift and refuses if the fingers have
  travelled since: `mMultiTouchDrift` against `FINGER_STILL_THRESHOLD`. Without it a short flick
  would click, and a spurious click in an inventory moves somebody's items.
  **Only while the touchpad is showing.** Without it a touch in a menu already puts the cursor
  under the finger and taps there, so a second finger would have nothing to add and could only
  surprise.
  The cursor deliberately **holds still during the tap**, because two pointers down is the
  existing scroll branch rather than the move branch. That reads as the right behaviour rather
  than a compromise: it is a click, and a click that dragged the pointer as it landed would be
  worse.
- **The reachable game area** (`customcontrols/GameViewport.java` + `MainActivity.applyGameViewport`)
  — the whole game, its HUD and every control drawn into a smaller rectangle anchored where the
  player can actually see and reach it. Asked for by somebody with a muscular dystrophy who plays
  lying on their side: a 6.8 inch panel held that close puts its own bottom edge outside what they
  can see without turning their head, and what lives there is Minecraft's hotbar, health and
  hunger.
  **The HUD cannot be moved, so the frame it is drawn in is moved instead.** That HUD belongs to
  the game, not the launcher, and nothing here can reposition it; but it is part of the picture
  rather than something drawn over it, so shrinking the picture brings it in.
  **The game moves and the controls do not**, which is the correction that matters most here.
  The first version inset `ControlLayout`, which was elegant (its `dimension_tracker` child is
  what `Tools.updateWindowSize` reads for `physicalWidth/Height`, so every coordinate followed for
  free) and wrong, as the owner reported within a day: it took the buttons with it. **The rule the
  first version missed is that insetting exists to move what the player cannot move themselves.**
  Minecraft's HUD is exactly that. Every control button is the opposite: draggable and resizable
  in the editor already, so moving them solves nothing and silently rearranges somebody's layout,
  crowding buttons because their positions are fractions of the box while their sizes are in dp.
  So the inset lands on the three views that *are* the game: `MinecraftGLSurface` (which is only
  a touch view), the rendering surface it adds beside itself in the parent, and the `Touchpad`
  that draws the cursor over them. `MinecraftGLSurface` sizes its framebuffer from its own bounds
  and reads touches in its own coordinates, so picture, aspect ratio and touch mapping stay in
  step with no offset arithmetic. `ControlLayout` keeps the whole panel, so `physicalWidth/Height`
  keep meaning the panel and every control stays where its author put it.
  **Two views are laid out in the panel but belong to the picture**, and only those two need
  telling: the hotbar-tap strip sits at the bottom of the game, and the gamepad pointer at the
  middle of it. `GameViewport` holds the live box for them, defaulting to zero so a reader that
  has not been told falls back to the panel.
  **The cursor is bounded by the picture, not the panel**, and that is not a restriction to fix:
  its coordinates are sent onward as game window coordinates, so a pointer outside the window
  would be pointing at nothing.
  It reuses the resize path rotation already exercises (`requestLayout`, then re-derive window
  size, control positions and the controller input area in the post), so the risky part is a path
  the app runs every time the phone turns.
  **Uniform on both axes.** The game adapts to any aspect ratio it is handed, so an uneven inset
  would not distort anything, but it would change the field of view as a side effect of a setting
  about reach. "The game, smaller" is a promise a player can predict.
  **It is also a real speed-up**, which is not a side note for the person who asked: 80% of each
  axis is 64% of the pixels, and they described the game as feeling heavy to play. The two
  complaints have one fix.
  The one thing that does not scale with the box is **button size**, which is set in dp. Positions
  are stored as fractions and so compress correctly, but the buttons themselves stay the size they
  were, which is why the floor is 50% and why the button-size slider is the companion control. Not
  scaled automatically on purpose: it is the player's own saved preference, and silently rewriting
  one of those is what the performance-mode entry above is a warning about.
  Behind the Controls screen's **advanced expander**, at the owner's request. Almost nobody needs
  the game smaller than their screen and the row would read as a mistake to everyone who does not;
  the people it is for will go looking, and the search index carries "accessibility", "one handed"
  and "disability" so it can be found by what it is rather than by what it is called.
  **Editor excepted**: `CustomControlsActivity` has no dimension tracker, so it keeps the full
  screen and arranges at full size. Positions being fractional, the arrangement is proportionally
  identical in the smaller box; only the relative size of the buttons differs.
- **Turnip driver manager** (`utils/TurnipDrivers.java` + `egl_bridge.c` + the Performance
  screen) — import adrenotools driver zips and pick which Vulkan driver Zink renders through.
  Upstream has declined to build it, and the accurate version of that is worth keeping because
  the handbook previously carried a quote ("PR it") that **is not on the page**: issue 224 is
  still open, and what the maintainer wrote on 28 September 2026, after rewriting how Vulkan
  natives are loaded, is "Implementing this should now be easy but am not willing to do it". That
  is a stronger fact for this fork than a closure would have been. The loader machinery was
  already here,
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
- **Testing a layout** (`ControlTestBridge` + `ui/game/ControlTest*.kt` + `ControlDebugHost.kt`)
  — press the controls you have just arranged, in the editor, and be told exactly what they send.
  **A real launch was built and removed**, which is the entry's most useful half. It worked the
  way it was asked to: a reserved profile, a superflat world shipped as a level.dat, Minecraft's
  settings floored, `--quickPlaySingleplayer`, a download bubble over the editor and a covered
  handoff. It never opened the world. Three attempts went into the level.dat, the last one
  reading Mojang's actual codecs, and it still landed on the title screen; the world could not
  even be opened by hand. **The cost of being wrong was invisible and the loop to find out was
  a few hundred megabytes long**, which is what made it a bad bet however good the reasoning got:
  every guess cost a download and returned one bit. Reverted whole, including the launch-path
  hook, so nothing of it is left to maintain.
  What replaced it is the thing it was competing with, made better. A control in the launcher
  process **cannot simply be pressed**: every send goes through `CallbackBridge`, whose senders
  are `@CriticalNative` calls into a JVM that only exists in `:game`, which is why the editor has
  always intercepted touches before a button saw them. The seam sits one level above the bridge,
  at `ControlButton.sendSingleKey` and at the joystick's own `sendInput`, which needs it
  separately because `sendKeyPresses` is stubbed out there.
  **The same seam does both jobs, on one flag.** The editor's session swallows the press because
  there is no game to send it to; the in-game debug strip lets every press through and only
  watches. A debug overlay that ate the input it was reporting would be the worst bug in the file.
  **Three panels behind one header**, because they answer three questions and only one is being
  asked at a time. *Keys* is the plain answer, the name and the raw keycode of everything held.
  *Log* is the same with history and timings, which exists because a slide-to-repeat at twenty
  presses a second and a four-step sequence are both over before the live line can be read.
  *Layout* is the half pressing cannot do at all: `inspectLayout` reads the live views and names
  keys bound to two controls, controls bound to nothing, controls off the screen, controls too
  small to hit, and controls hidden in both visibility states. Those are facts about the file
  that were previously invisible until the moment they mattered, and the header carries a dot
  when there is something to see so the tab is worth its tap.
  It is measured on the **live views, not the file**, because positions are expressions evaluated
  against the screen: a report built from the JSON would be answering a different question.
  The panel is **`wrap_content` and pinned by gravity** (12.9) and moves between top and bottom
  rather than growing a drag, because it is only ever in the way at one end and a thing you can
  drop anywhere is a thing you have to put back. The game's own hotbar and crosshair are drawn
  behind the layout, which is the one thing a bare editor could never answer: a button at the
  bottom middle looks fine on an empty screen and steals hotbar taps in play.
  Two things bite anyone extending it. `mControlVisible` starts **false** and only a game ever
  turns it on, so applying the visibility rules without setting it first hides every control and
  stages the exact failure the session exists to find. And nothing may release the keys on the
  way out: the bridge is detached first, so a release afterwards would take the real path into a
  native symbol that is not in this process.
- **The editor's ground and its grip** (`ControlLayout.dispatchDraw` +
  `handleview/ControlHandleView` + `ic_ctrl_resize_grip.xml`) — what arranging a layout actually
  feels like, which until now was: near-black, silent, and a resize that could destroy a button.
  **The backdrop is sky and grass, and it is not decoration.** Controls are translucent, so the
  only question worth answering while placing one is whether it will still be legible over a
  bright world, and a dark editor is the single background that flatters every button and tells
  you nothing. The texture pack picker settled this argument already (previews there are drawn
  over a world for the same reason), so these are that picker's exact colours and the two places
  the launcher previews a control now agree. It is **painted, not a background drawable, and
  gated on `mModifiable`**: the same class sits over the GL surface in a running game, where it
  must stay completely transparent, and a backdrop set unconditionally would cover Minecraft
  with a picture of a hill.
  **The editor decorations draw rather than being views.** A layout is a thing you drag buttons
  around on, and every view added over it is a view that can swallow a drag (12.9); an overlay
  that only ever draws cannot take a touch from anything. That is also why the size readout is
  drawn by the layout and not inside the grip: the grip is a 34dp square pinned to a corner, and a
  readout in it would either be illegible or need the view grown into something that starts
  blocking its neighbours. The price is that **anything drawn about a child has to be invalidated
  when that child moves** (16.21), which is what `mSelectionWatcher` is for: a drawn outline does
  not follow a view the way a view does.
  **The resize handle had no weight to it at all.** No press state, no haptic, no readout and no
  floor: the size followed the finger exactly, printed both dimensions to stdout on every move
  event, and would take a button to zero by zero, at which point it is still in the layout and
  can never be grabbed again. It now has a floor, a 4dp step, a tick when it is taken hold of and
  one per step crossed, and the size on screen while it changes. The step is what makes a drag
  feel like it is moving through something rather than sliding on glass.
  Two things about it are load-bearing. The drag is measured in **raw screen coordinates against
  the size at grab time**, never as a delta from where the grip currently sits: the grip is placed
  at whatever corner the button actually ended up with, so once a floor exists it stops being
  under the finger, and deriving the next size from its position rubber-bands. And the size is
  **snapped then floored**, which at the shipped constants is indistinguishable from the reverse
  because the floor is a whole number of steps; `scripts/resizesim` sweeps off-grid floors
  precisely so that the day either constant changes, the order is still checked.
- **Game files** (`ui/content/`) — worlds, mods, resource packs, shader packs and screenshots as
  **one screen**, reached from the home tile and from Settings. Both used to hand the player to a
  file manager and a path under `Android/data`.
  The reason it is one screen and not five is **the add button**: nobody thinks "place a file in
  the resourcepacks directory", so the picker takes anything and **what it is decides where it
  goes** — `.jar` is a mod, a zip with `pack.mcmeta` a resource pack, one with `shaders/` a shader
  pack, one with `level.dat` a world, which gets unpacked. The categories are a **filter over one
  list**, which is what lets search cross them. Rows are deliberately the same shape whatever they
  hold; only mods carry a switch, because only a mod can be turned off in place.
  **What it cannot identify, it asks about rather than refusing.** The deciding covers mods,
  packs, worlds and screenshots, and everything else used to be a toast. That refusal was close
  to a dead end: a config file, a datapack, an options.txt somebody was sent, a jar for a loader
  this does not model, none of them could get into the game folder by any other route either,
  because from Android 11 `Android/data` cannot be browsed to at all. So the refusal opens a
  folder picker rooted at the game directory, a plain walk one folder at a time, with the
  destination and the name the file will be given both shown before anything is written. The
  name is shown because it is not obvious: a folder already holding that name gets the copy
  beside it rather than over it.
  **The destination is guarded twice, and the two guards answer different questions.** Up is
  hidden at the root, which is about what the player can reach. The copy re-checks by canonical
  path and that the resolved file is a direct child, which is about what a symlink inside the
  game folder could point at. And the display name is treated as a stranger's string, because it
  comes from whichever provider served the document and reaches `File(folder, name)` directly:
  "../x" resolves outside the chosen folder and ".." resolves to its parent. The dialog's preview
  runs through the same sanitiser, so the name it promises is the name that gets written.
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
- **The opening** (`AmethystXSplashTheme` + the entrance choreography in `ui/home/HomeScreen.kt`)
  — a cold start now shows the gem centred on the launcher's own ground, and the home screen
  assembles in the order it is used: brand settles from the top, the Play card rises to meet the
  eye, then the tiles, then the links, all inside about half a second.
  **The splash is resources only, and that is the design.** The launch window is drawn by the
  system before any of our code runs, which is what makes it instant and what makes it unable to
  crash; the startup path is where this app has been burned twice (16.4, 16.7), and a splash
  library that throws when the theme is not its own is exactly the attach-time contract class
  those lessons are about. Per API level: 21 and 22 keep the plain dark window, because a
  layer-list cannot centre a drawable there and a stretched gem is worse than none; 23 to 30 get
  the gem in the window background; 31 up hand the job to the system splash and take the
  background gem back out, or the system icon would be followed by a second, different gem for a
  frame.
  **The choreography plays once per process.** MainMenuFragment's composition is rebuilt on every
  return from Settings and every rotation, and an entrance that replayed each time would stop
  meaning "the launcher is opening" and start meaning nothing. A process-level latch, not
  remembered state, because the composition does not live as long as the answer. It also skips
  itself when the platform reports animations off, read once at composition.
  **The hero carries the one scale settle** and the largest rise, which is the motion budget spent
  where the colour budget already is (8). Everything else fades and settles a few dp on the
  handbook's 300ms and FastOutSlowInEasing; the whole sequence is over before it could be waited
  on.
  **The entrance never blocks input.** Compose hit-testing ignores layer alpha, so the controls
  are live while they fade in, and that is kept rather than gated: an opening that ate taps
  would trade a real half second of responsiveness for a theoretical mis-tap in a window shorter
  than a reaction time. Decorative motion must never make the app slower.
  It gets **no onboarding page and no comparison row**, on the screenshot's precedent: an opening
  is not a capability, every honest mark would be taste, and the table is only worth reading
  because it is edited.

- **The launch console** (`ui/home/LaunchConsole.kt` + the stage timeline in `LaunchProgress.kt`
  + the press echo in `MainMenuFragment`) — pressing Play turns the hero card into a staged
  report of the launch: the profile inside a progress ring, the percentage large, a thin track,
  and a timeline of what has actually been done, on a deepened wash. It replaced one line of
  text inside the Play button.
  **The whole card transforms, not the button.** The thing touched is the thing that responds
  (§8), and swapping the version row out with the button is what stops a profile being switched
  under a download that has already decided what it is fetching — previously that row stayed
  live for the whole launch.
  **A stage is a string resource, and the timeline is the downloader's own reports.** The
  launcher was already narrating its work through `ProgressKeeper`; each report's resid is the
  stage's identity, so the counts and speeds that churn several times a second update one line
  in place and a new resid starts a new line, with the previous one taking a check. Nothing is
  invented: no fixed checklist that would tick steps that never ran, and no "boosting" theatre.
  A booster feeling built from fake stages would be the neon rule (§4) broken with words.
  **The press is echoed locally** (`launchRequested`), because the real busy signal is the task
  count and the first task is only submitted once the downloader thread has spun up: the card
  must become the console in the frame the finger lifts, not when the network answers. A refused
  launch (no account, no version) never starts a task, so the echo concedes after four seconds
  of nothing running, and any task count reaching zero clears it too. Busy without a press still
  opens the console, under "Getting ready" rather than "Launching", because home can be returned
  to in the middle of work started elsewhere and a card that claimed to be launching would be
  lying.
  **One animated value drives the ring, the bar and the number**, so the three can never
  disagree; it is an `Animatable` rather than `animateFloatAsState` so the first target is swept
  to from zero, which makes the ring drawing itself in the entrance. Reduced motion swaps every
  continuous piece for a static one: the indeterminate arc rests instead of orbiting, the
  travelling band becomes a quiet wash.
  **There is deliberately no completion state.** `ContextAwareDoneListener` starts the game and
  kills the launcher process in the same breath, so the honest end of the sequence is the game
  window appearing over it; anything designed for "done" would only ever be seen when the launch
  had failed.
  It gets **no onboarding page and no comparison row**, on the opening's precedent: upstream
  launches and reports progress too, this is the same capability presented better, and a table
  row about presentation would be a mark for taste.

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
  **The Install tile on home is what this is for**, and pointing it here was the whole point of
  building it: it was labelled "Forge, Fabric" and opened a file picker asking for an installer
  jar, which is asking somebody to supply the thing they came to fetch. Running a jar is still
  offered, from inside the screen rather than as the only thing the tile could do, because it is
  the only route for anything the index does not carry: an OptiFine build, a modpack's own
  installer, a file from a friend. The tile's long press keeps the custom arguments dialog.
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
  **Player space assumes the player is upright, and hands back when they are not.** It measures
  yaw around the world's vertical axis, which is the axis the player wants to turn about only
  while the player is themselves aligned with it. Lie on your side and the two come apart
  completely: gravity is then along the screen's *right* axis, contributes nothing to the pair
  player space is built from, and the formula returns nearly zero however hard the phone is
  twisted. That shipped, and was reported by somebody who always plays lying down: free up and
  down, dead side to side. So the projection's own length is read as a confidence, since it is
  1 for an upright player whatever the phone is doing and falls as the cosine of how far they
  have rolled, and below it the screen's own up axis is used instead. That axis is right by
  construction in exactly the case player space is wrong in, because a phone held naturally is
  upright with respect to the player whether or not the player is upright with respect to the
  Earth. **Blended rather than switched**, over a band chosen so the response does not dip
  through the crossover: up to 0.5 the relax factor is still restoring player space to full gain,
  so the two agree in the middle and there is nothing to smooth over.
- **Performance mode, built and removed** (`optimiser/`, `ui/settings/PerformanceSheet.kt`,
  `scripts/{plansim,devicesim,perfsim,optionssim}`) — one switch that read the device and set the
  renderer, the resolution, the heap, Minecraft's own graphics settings and a mod set to match,
  with a preview and a full undo. Removed at the owner's request; the entry stays so it is not
  rebuilt from the roadmap by somebody who never saw it.
  What it cost is the part worth keeping. It was the only thing in the launcher that **wrote into
  Minecraft's own options.txt**, and the only thing that **installed mods nobody had chosen one at
  a time**, so it owned an undo, a backup format, a device tiering, a plan, four harnesses and a
  sheet. Every one of those existed to make one switch safe. Anything proposing to set several
  unrelated settings at once inherits that whole tail, and should be costed with it rather than as
  the switch it looks like.
  The specific findings are in the commits, and two are worth having before a second attempt: a
  graphics option's "faster" direction genuinely differs per key (`graphicsMode` counts up from
  fast, `particles` counts up from all), so a bound cannot be inferred from the value alone; and a
  resolution has to be solved against a pixel budget rather than set to a percentage, because the
  same percentage blurs a 1080p phone to fix a 1440p one.
  **Anyone who had it on keeps the settings it applied**, and the undo left with the code. Nothing
  is broken by that: every value it wrote is an ordinary setting that is still editable where it
  always was, the renderer included. No migration was written to restore the captures, because
  restoring them means keeping the backup format, the options.txt writer and the renderer path
  alive to run once, and a half restore that put the preferences back but not Minecraft's own file
  would be worse than none. The three orphaned preference keys (`performanceMode`,
  `performanceBackup`, `performanceChunks`) are left inert rather than swept up on the startup
  path, which is the one path in this app that must not grow work that can throw (16.15).

- **Reading the log** (`logs/LogParser.java` + `ui/logs/` + `LogActivity.kt`) — the log, in the
  launcher, with a search box, a level filter and the line you searched for shown among its
  neighbours. Everything the launcher could say about a failed session was already in
  `latestlog.txt`, and the only thing ever offered was a share sheet: to read your own log you
  had to send it somewhere else first, and from Android 11 the file cannot be browsed to at all
  because it lives under `Android/data`. The launcher is the only thing that can show it.
  **The parsing is Java, and that is the entry's most reusable half.** It was written in Kotlin
  first, which made it unverifiable: there is no Kotlin compiler in the build container, so the
  only check available was a Python re-implementation reading the constants out of the source.
  That harness passed while **six of eight deliberate mutations to the shipped code went
  unnoticed**, because the thing it drove was the copy. Moved into `logs/LogParser.java` with no
  Android imports, `scripts/logsim` compiles the real class at source 8 and now catches all ten.
  Every other parser here that has a harness is Java for exactly this reason (§16.6); this is the
  first time the rule was learned the other way round.
  **The format is read, not remembered.** This launcher hands the game
  `-Dlog4j.configurationFile=` pointing at its own `assets/components/security/*.xml`, and those
  files set `[%d{HH:mm:ss}] [%t/%level]: %msg%n`. The harness reads each config, renders a line
  the way log4j would and feeds it back, so a format that changes there fails a check rather than
  somebody's error filter (16.20).
  **A line with no level of its own inherits the line above it**, which is the one thing a level
  filter over a Java log has to get right: a stack trace carries no level, and without
  inheritance the Errors filter shows a one-line exception with its cause hidden. It inherits for
  *filtering* and not for *colour*, because a single crash would otherwise paint forty lines red
  and nothing on screen would stand out.
  **A level is read from the prefix and never from the message.** Chat quotes the word routinely,
  and a player typing "the ERROR was mine" must not file their own sentence under Errors.
  **The filter is offered only when the log declares levels at all.** The format belongs to
  Minecraft rather than to this launcher, so if a version stops writing them the parse finds
  none, and a filter in that state would hide the whole log while claiming to show its errors.
  **A search result is a route, not a destination**: tapping one clears the search and takes you
  to that line among its neighbours, washed, which is the settings-search idiom (§14) and the
  reason to look at a log rather than at a list of matches. The screen keeps its search box
  pinned rather than taking the collapsing large title, because a log is a working surface and
  searching one is a loop of typing, reading and retyping.
  It gets **no onboarding page** (you meet it on the home footer and on the crash screen) but it
  does get a **comparison row**, marked `Part` for upstream: upstream shows the log live over a
  running game, which this build kept, and cannot read it afterwards or search it.
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
- **The memory question, asked before the download** (`prefs/HeapAdvice.java` + the dialog in
  `MainMenuFragment.play`) — whether the heap will fit is now asked in front of the Play button,
  with the fix attached, instead of after the game has been fetched.
  **The check already existed and was in the only place it could not be acted on.**
  `Tools.launchMinecraft` has always compared the allocation against free memory. On a desktop
  that is a small annoyance. Here it lands after several hundred megabytes have been downloaded,
  frequently over mobile data; it runs in the game process, which is started by killing the
  launcher, so the settings it advises changing are no longer reachable; and it is an OK-only
  dialog that then launches anyway, so being told changes nothing. Moving the same question
  earlier fixes all three at once and costs nothing to act on.
  **Three numbers became one definition.** The ceiling the memory slider enforces, the default a
  fresh install gets and the bound the launch check tests against were three pieces of arithmetic
  in three files with nothing making them agree. A launch screen that refuses a value Settings
  offers is a launcher arguing with itself, and it is not a bug anybody would think to look for.
  **The threshold is deliberately the shipped one and not a better one.** The heap is committed up
  front (`-Xms` equals `-Xmx`), so "you are asking for more than the device says is free" needs no
  invented constant for the game's native overhead. Moving the question and attaching a fix is the
  improvement; changing the threshold in the same breath would be changing two things at once with
  nothing to check the new one against.
  **The address space arm stays in the game process.** `getMaxContinuousAddressSpaceSize` parses
  `/proc/self/maps`, so it describes whichever process asks, and the launcher's map is not the
  game's. An answer from the wrong process is worse than no answer.
  **An unreadable reading is not a reading of zero.** `ActivityManager` returning nothing looks
  exactly like a device with no memory free, and believing it would warn on every launch on every
  device that will not answer. Unknown means check nothing, which is the same rule the snapshot
  version and `Tools.compareSHA1` both follow.
  The fix goes through `loadPreferences` as well as the editor, because `PREF_RAM_ALLOCATION` is a
  static cached at load time and writing the preference alone would launch with the old value.
  It gets **no comparison row**: upstream has this check too, in the same wrong place, and the
  difference is where it is asked rather than whether it exists.
- **What a log opens with** (`Tools.printLauncherInfo`) — the header now carries the chipset, the
  core count and ABIs, free memory and free storage at launch, the Android release, the panel and
  the resolution actually rendered, the renderer **choice** and the Java runtime, alongside what it
  already had. Every bug report the project receives is built from these lines.
  **This is the only place that can write them.** `Logger.begin` opens the file with `O_TRUNC` and
  only the game process and the Java installer ever call it, so anything the launcher wrote first
  would be erased by the launch it was describing.
  **The renderer choice matters more than the driver's own name.** "Adreno" says nothing about
  whether the game was going through gl4es, ANGLE or Zink, which is usually the first thing worth
  knowing, and it is the setting somebody would actually be asked to change.
  Each fact is read inside its own guard and says `unavailable` rather than throwing, because this
  runs on the launch path and a device report is never worth a game that will not start (§16.15).
- **Checking mods against the profile** (`modmeta/` + `ui/content/ContentCompat.kt`) — every jar in
  the folder read against the Minecraft version and loader it will actually run under, and against
  the other jars beside it, so "will not load" is something you find out before the game does.
  **There is no declared Minecraft version to read**, and that is the finding that shapes the
  whole thing. In every modern format the Minecraft requirement *is* a dependency entry, sitting
  beside the mod's other dependencies in the same grammar. So "does this match my version" and "is
  anything it needs missing" are one parser and two questions over it, and building either alone
  means writing both. They shipped together for that reason and should never be split.
  **Anything not fully understood answers UNKNOWN, never CONFLICTS.** A missed warning costs a
  player nothing, because they are exactly where they already were. A false one tells them to turn
  off a mod that works, breaks their game, and the launcher gets the blame. Everything in
  `VersionPredicate` fails towards silence, which is the same asymmetry `Tools.compareSHA1` encodes
  when it fake matches on a read error, and it must survive anybody later tidying the file.
  **Four traps in this ecosystem each produce a screen full of confident, wrong warnings**, and all
  four are checked by name. Fabric API is forty modules that declare `provides`, and mods depend on
  those module ids, so a graph ignoring `provides` reports a missing dependency on nearly every
  Fabric mod installed. Platform ids (`minecraft`, `fabricloader`, `forge`) are not jars in the
  folder. A `.disabled` jar is on disk and absent from the game, so counting it means switching
  Fabric API off silently stops warning about the twenty mods that needed it. And **NeoForge
  renamed Forge's `mandatory` to `type`**, so reading one spelling makes every dependency in the
  other format either always required or never, both of which are silent.
  **Java over Gson, not Kotlin over `org.json`**, and that is a verification decision rather than a
  taste one (§16.25): there is no `org.json` jar in the build container and no Kotlin compiler, so
  a parser in the existing readers' idiom could only ever have been driven by a copy of itself.
  Written in Java it compiles at source 8 and `scripts/modmetasim` builds real jars with
  `ZipOutputStream` and reads them back through the shipped classes.
  **The fix is what makes it worth more than a badge.** Knowing four mods are for the wrong version
  is worth little on a phone if acting on it is four long presses; a desktop launcher gets away
  with a column of ticks because a mouse makes the follow-up cheap. So the count carries "turn them
  off", and a missing dependency carries its own name into a Modrinth search that installs it.
  **Off, never deleted**, because the verdict is the launcher's reading of somebody else's
  metadata and being wrong has to stay undoable.
  **Switching a mod off asks who breaks first.** That failure is otherwise completely silent: a
  mod turned off is not an error anywhere, the game simply fails to start next time complaining
  about a mod nobody touched. Deleting already had a confirmation, so the same fact went into that
  dialog's body rather than into a second dialog after it.
  The verdict replaces the row's **accent line** rather than adding a third one, because that line
  is already where a row reports its state and four hundred rows should not get taller for the two
  that are broken. In `Warning70`, never `Danger70`: nothing has failed, and the mod is still there.
- **Running Sodium, and therefore shaders** (`sodium/SodiumSupport.java` + the dialog in
  `MainMenuFragment.play`) — the renderer is asked about before launching, and on the one renderer
  that is real OpenGL nothing is asked at all. Reported as "we can't run Sodium because it shows a
  popup", which was exactly right.
  **The dead end was this fork's own doing**, and that is the half worth keeping. The dialog had
  always been a warning with a way past it: a `sodium_override` preference, set by a
  `MathQuestionPreference` in `pref_experimental.xml`. The Compose settings rewrite
  (`eec3e6f`) deleted that screen and that class. The *read* of the preference survived; the only
  *write* did not. So the dialog's sole remaining button was "Delete Sodium and Launch", and Sodium
  became unrunnable, and with it Iris, and with Iris every shader pack. **Deleting a settings
  screen can orphan the write half of a preference and leave the read half enforcing it**, which
  nothing in the build can see.
  **The flat refusal was the wrong question anyway.** Sodium needs OpenGL 3.3, which is a fact
  about the renderer and the device rather than about the mod; gl4es tops out two whole versions
  below the floor, so there it cannot work at all. One question, four answers, and the renderer is
  the thing the player can change, so the dialog offers the change.
  **The renderer alone was also the wrong question, and that shipped.** The first version returned
  SUPPORTED for Zink on every device. Zink is Mesa, and Mesa gates the GL version it can advertise
  on specific Vulkan features of the driver underneath: GL 2.1 on `logicOp`, `fillModeNonSolid`,
  `alphaToOne` and `shaderClipDistance`, GL 3.2 on `VK_EXT_depth_clip_enable`, GL 3.3 on
  `VK_EXT_vertex_attribute_divisor`. The bundled Mesa Turnip carries all of them, so Adreno with
  Turnip clears the floor; a stock ARM Mali driver fails several outright and lands around GL 3.1,
  which is *below* it. So the class was silent on exactly the devices that most needed telling, and
  a Mali player got a crash with no dialog where they had previously had one. The verdict now takes
  a `Gpu` read by `sodium/GpuProfile.java`, and **`VERIFIED` is reachable only by Zink on an Adreno
  with a Turnip driver actually loading**, which is the one Vulkan feature set this build knows.
  **The three levels are named for their evidence**: `VERIFIED` has evidence for, `INCOMPATIBLE`
  has evidence against, `UNTESTED` has neither. The previous spelling of the first was `SUPPORTED`,
  which read as a promise about the device when all it ever meant was "this renderer is the real
  OpenGL one". That name is what made the bug easy to write.
  **Mali is UNTESTED and not INCOMPATIBLE**, deliberately. The evidence that stock Mali drivers
  fall short is real but it is about driver versions in general, and the Vulkan feature set cannot
  be queried from the launcher, so condemning a specific device would claim more than this build
  can read. The warning is specific instead: a `Reason` per obstacle, so the dialog names the
  driver rather than shrugging.
  **Turnip must be loading, not merely available.** `JREUtils` only loads it inside an
  `info.isAdreno()` branch and the user can choose the system driver, so `GpuProfile` computes
  `turnipActive` exactly as the launch does. A verdict that assumed Turnip where the launch will
  not load it would be the same bug with a different cause.
  **Nothing is asked on a renderer that can run it.** That is the point of the class rather than a
  nicety: a warning that fires on every launch when the setup is already correct is how a player
  learns to dismiss warnings without reading them.
  **Unknown is UNTESTED, never UNSUPPORTED.** A renderer id this class has never heard of is one it
  has no business condemning, the same asymmetry `VersionPredicate` and `Tools.compareSHA1` encode.
  An unset renderer lands there too, and correctly: the automatic pick is gl4es or MobileGlues
  depending on the version's asset index, never Zink, so warning and offering the switch is right
  whichever it turns out to be, and the launcher cannot know which without the version JSON.
  **`shadersViable` is defined as "the verdict is VERIFIED"**, which makes it exactly as strong as
  the evidence and no stronger. Iris requires Sodium and shader packs lean on compute shaders and
  storage buffers, so anywhere Sodium itself is only UNTESTED a shader pack is certainly not
  something to promise. It was Zink-on-any-GPU first, and that is the second place the
  renderer-only answer was wrong: being true everywhere Zink was selected, it *removed* the shader
  pack warning on precisely the Mali devices that needed it.
  **Sodium's own anti-launcher check does not fire here**, which is worth writing down because it
  looks like it should. `PostLaunchChecks.isUsingPojavLauncher` tests an environment variable this
  fork renamed to `AMETHYST_RENDERER` and a `java.library.path` regex anchored on
  `net.kdt.pojavlaunch`, and the application id is `org.angelauramc.amethyst`. So no Podium shim is
  needed. `ISSUE_2561` is a separate LWJGL version-string check and is still turned off by the
  `-Dsodium.checks.issue2561=false` the launch path already passed.
  **The mixin properties the launcher used to write had never done anything**, and that was found
  by reading rather than guessed: Sodium resolves a mixin option by the mixin's own *package path*,
  and neither `mixin.features.buffer_builder.intrinsics` nor `mixin.features.chunk_rendering` is a
  path in any version (the modern ones are `features/render/immediate/buffer_builder` and
  `features/render/chunk`; the 0.3 era namespace was the singular `mixin.feature.*`). It wrote four
  junk files into the config folder on every launch, through a loop whose `if` tested the whole
  array on each pass. Deleted rather than corrected, because the one spelling that *would* have
  matched is a documented crash on joining a world (Sodium issue 842), so the fix for "we don't
  want more people complaining" would have caused the complaint.
  **Detection and deletion are two lists on purpose.** `SodiumSupport.FAMILY` is six renderer
  replacements, because the question is "is there a Sodium family renderer here".
  `Tools.deleteSodiumMods` keeps its own longer list, because removing a renderer mod and leaving
  Iris and Indium behind is a crash on startup rather than a tidy removal. The matcher is one
  definition in pure Java so `scripts/sodiumsim` drives the real thing rather than a transcription
  of it (16.25).
  **The log line now names the renderer and the verdict.** It used to say Sodium "is unsupported,
  you are on your own" whatever the renderer, which is wrong on Zink and is the first line of every
  bug report this project receives. It is written where `LOCAL_RENDERER` has finished resolving,
  which is after the profile's choice, the automatic pick and the incompatible-renderer fallback.
  **A shader pack row says so too**, through `shadersViable` on the Game files rows
  (`ui/content/ContentCompat.kt`). That is a gap the dialog cannot close: the dialog only speaks
  when a Sodium family mod is present, and a shader pack dropped in before Iris is installed is
  otherwise completely silent, because it appears in the folder, appears in the game's own shader
  list, and does nothing with no error anywhere. It replaces the row's accent line rather than
  adding a third, in `Warning70` not `Danger70` on the mod check's reasoning: nothing has failed
  and the pack is still there. The renderer is **handed in rather than read there**, for the reason
  `modTarget` is: reading it parses the profile JSON and the compat pass runs again on every
  background batch of a folder scan (16.11).
  It gets **no onboarding page** and **a comparison row** marked `Part` for upstream, and that mark
  is the entry's last load-bearing fact: **upstream can run Sodium.** Its warning has a way past
  it, the maths question in the experimental settings, which is upstream's own code and works.
  Marking the row `No` would be the overclaim the table exists to avoid, and this fork was briefly
  *worse* than upstream here, having deleted the screen that held the way past. What upstream does
  not do is check the renderer first, stay silent when it is already right, or offer the renderer
  that would fix it.

- **Editing a clip and a screenshot** (`recorder/edit/` + `screenshot/edit/` + `ui/mediaedit/`)
  — trim a recording, drop parts out of its middle, crop and turn a screenshot, and save a copy
  the gallery can see. Everything the launcher captures previously left it to be edited: both
  live under `Android/data`, which from Android 11 cannot be browsed to at all, so the launcher
  is again the only thing that can offer them (the same argument the skin editor and the log
  viewer are built on).
  **The clip edit is a stream copy, and that one decision shapes the whole feature.** Samples are
  copied from one MP4 into another without being decoded, so an edit is as fast as the storage,
  cannot lose a frame of quality, and needs no transcode pipeline and no media3 dependency this
  app could not afford to ship whole. The price is a single rule, and the rule is narrower than
  it looks: **only a segment's first video sample has to be a sync frame**, because nothing needs
  to decode past a segment's end. So the trim END and the near side of every cut are frame
  accurate, and only the points where playback RESUMES sit on the two second grid. Anybody
  assuming both ends snap will make the editor needlessly coarse, which is why `clipsim` asserts
  the asymmetry rather than merely the behaviour.
  **A resume point snaps forward, never back**, so a cut always removes at least what was asked
  for. Somebody cutting out a death, a mistake or a face on a shared screen is asking for that
  material to be gone, and a cut that quietly left 1.9 seconds of it in would be the one failure
  this must not have. Losing a little extra footage is a disappointment; failing to remove what
  you were told to remove is a breach.
  **A segment's end still snaps, to a frame rather than to a sync frame**, and this is the piece
  nobody would find by looking. A declared segment that ends between two frames makes the frame
  at the join display for a fraction of its time, once per cut. Snapping the end forward to the
  next frame costs nothing and cannot change which frames survive, because the span is half open
  so a frame sitting exactly on the new end is still excluded. That it cannot overrun the next
  segment rests on every sync frame also being a frame, which `build` establishes by **unioning**
  the two tables rather than trusting a file to nest them.
  **The decisions are plain Java and the execution is not, deliberately** (§16.25). `ClipPlan`
  and `ImageEdit` have no Android imports and are driven by harnesses; `ClipEditor` and
  `ImageEditor` hold only the calls that need a device. A wrong plan is a corrupt video that
  compiles perfectly, and there is no phone in CI.
  **`MediaMuxer` fails in three ways that are all worse than an exception**, and all three are
  reachable from an arithmetic slip: a timestamp that goes backwards within a track **hangs the
  thread** rather than throwing (`minSdk` is 21, below where the platform started tolerating it),
  a negative timestamp truncates the track silently and then hangs the next write to it, and a
  segment that starts off a sync frame is not refused at all, the writer just eats the leading
  frames and puts the video up to a GOP behind the sound. Each is checked before the call that
  would trigger it, and `clipeditsim`'s stub throws where the real one would block, so a guard
  that stopped working is a failed check rather than a frozen phone.
  **The sample flags are translated, not cast.** `SAMPLE_FLAG_SYNC` is 1 and so is
  `BUFFER_FLAG_KEY_FRAME`, which makes `info.flags = extractor.getSampleFlags()` look obviously
  right; but `SAMPLE_FLAG_ENCRYPTED` is 2 and so is `BUFFER_FLAG_CODEC_CONFIG`, and
  `SAMPLE_FLAG_PARTIAL_FRAME` is 4 and so is `BUFFER_FLAG_END_OF_STREAM`.
  **B frames are detected and refused rather than mangled.** A stream copy can reorder spans of
  samples, not samples, and MediaMuxer discards the original decode timeline and rebuilds it from
  the presentation one, so a reordered source would not be lossless in the timing even where it
  was accepted. The walk that collects the sync frames already reads every timestamp, so noticing
  costs nothing.
  **The filmstrip is drawn at sync frames, and that is the UI's whole answer to the constraint.**
  `getFrameAtTime` with `OPTION_CLOSEST_SYNC` returns exactly the frames a clip is allowed to
  start from, and it is the cheap option, so the pictures on the strip are the pictures the start
  handle can land on. The grid is furniture rather than a sentence. Ticks are drawn only while
  they are far enough apart to carry information, because at forty minutes there are twelve
  hundred of them and a solid grey line says less than nothing.
  **The screenshot preview is produced by the exporter's own code.** Turning the picture with a
  `graphicsLayer` rotation would be cheaper and would mean the preview and the saved file came
  from two different pieces of arithmetic, which can disagree in exactly the ways that are
  invisible in source: a flip that comes out vertical at ninety degrees, a crop taken from the
  wrong corner. The preview bitmap goes through the same `ImageEdit.plan` and `ImageEditor.apply`
  the export uses, so **what is on screen is the output**. Same rule as `ControlTextureDrawable`
  drawing its own previews.
  **The crop is held as fractions of the displayed image**, because `preview()` is downsampled
  and `export()` works in full resolution source pixels. A plan built against a half size preview
  and applied to a full size bitmap crops the wrong half of the picture and looks entirely
  plausible doing it.
  **The format is the source's, not the capture preference's.** Saving an edited PNG as a JPEG
  would quietly throw away both the quality and the alpha somebody had; the format setting is
  about what a new capture is written as, which is a different question. Only a JPEG's quality
  comes from preferences, there being no other number to use.
  **The gallery copy follows the existing setting, and there is a button for when it does not.**
  An edit is published exactly as a capture is, so anyone who leaves the export preference on
  gets the same behaviour from both and the message says so. The button exists for the case the
  setting cannot cover: somebody who keeps the automatic copy off, because it doubles the space
  every capture takes, but wants this one edit where a chat app can find it. It is shown only
  while there is something to copy, so it is never a control that does nothing, and a picture
  that fails to copy offers it again, since below Android 10 the cause is a storage permission
  nothing asks for at runtime.
  **Neither editor ever writes to the original**, which is why both pick a free `_edit` name
  beside it, and the clip is written to a `.part` file and renamed only once every sample is in
  it. `loadRecordings` filters on `.mp4`, so a half written export is invisible to the gallery
  rather than merely incomplete in it.
  **It is an activity, not a route**, which is the opposite of the mod browser's project page and
  for a reason: this one screen is reached from two different activities, and it finishes by
  writing a new file that whichever one launched it picks up on its own resume. A route would
  mean hosting it twice.
  **Tapping a screenshot now opens it here**, because there was no viewer before: a tap built a
  document URI and handed it to a chooser, so the way to look at a picture the launcher had just
  written was to leave the launcher. The editor is the viewer, and it offers the old behaviour as
  a link rather than taking it away.
  It gets a **comparison row** marked `No` for upstream, the strongest mark the table hands out,
  on auto-walk's reasoning: this is not the same capability presented better, it is one upstream
  has no equivalent of at all. It gets **no onboarding page**, on the precedent of slide to
  repeat and the sequence runner: you meet it on the thing you wanted to edit.

- **The clip editor, rebuilt around playback** (`ui/mediaedit/ClipPlayer.kt` +
  `ClipEditScreen.kt` + `ClipPlan.timelineSegments`/`isRemoved`/`segmentAt`/`retime`) — the
  first version had a still, a scrub bar and a button that cut a fixed two seconds at the
  marker, and no way to watch anything. Reported in those words, with a CapCut screenshot
  attached: there is no playback, and cutting should be as easy as it is there.
  **The two complaints are one complaint.** You cannot find the part you do not want without
  watching, and you cannot state its length in advance; a fixed-length cut is the control you
  design when the screen cannot play, and a screen that can play does not need one. So the
  shape is the one every video editor converged on, for the reason they converged on it: the
  picture is the biggest thing and it plays, the transport is directly under it, the timeline
  is a filmstrip with a playhead, and the tools act on what is selected.
  **Split and delete, not "remove this part".** Put the playhead somewhere, Split divides the
  span, select a span and Delete removes it from the output. Two steps instead of one, and
  better because each step shows its own result. Delete and Restore are **one control with two
  labels** rather than a Delete and a separate Restore, so a mis-tap costs one tap instead of
  the whole edit through Reset.
  **`MediaPlayer` and a `TextureView`, not media3.** There is no ExoPlayer in this build and
  release builds do not run R8, so adding one ships a whole player; what this screen needs is
  the easy half of what a player does. A `TextureView` rather than a `SurfaceView` because a
  surface punches a hole through the window and is composited behind everything, so the
  timeline and toolbar drawn over the preview would be the things that disappeared.
  **The player is told nothing about the edit.** It plays the file on disk and reports a
  position; `MediaEditorActivity` steps it over every deleted span and back to the trim start
  at the end, so pressing play previews the OUTPUT. Putting the skipping inside the player
  would mean two things decided what the edit is.
  **`retime` is what makes the shading honest.** `isRemoved` is exact equality, so the cut list
  is only safe while every cut is one of the current spans, and a split or a trim drag changes
  what the spans are. Every shape change goes through `ClipPlan.retime`, whose result is by
  construction a subset of the spans handed in. **Any overlap deletes the span**, not a
  majority and not a complete cover: all three agree in the ordinary cases and this is the
  conservative one, the same asymmetry that makes a resume point snap forward. Without it a
  deletion stays in the plan and vanishes from the screen, which is the one disagreement this
  feature cannot afford. `clipsim` asserts the invariant rather than any particular mapping.
  **The scrub is debounced and the marker is not.** `MediaPlayer.seekTo` decodes to get there,
  so sixty a second makes the picture lag the finger by more than doing nothing would; the
  playhead follows the touch immediately and the frame catches up 60ms later. While a scrub is
  in flight the playback ticker is ignored, or it would drag the playhead back to where the
  player still is.
  **Two columns on a short screen**, which is the control center's rule in the other
  orientation: stacked, the bar, transport, timeline, tools and summary come to more than a
  phone is tall in landscape, and the thing that gives way is the weighted child, which is the
  picture. An editor whose picture is nothing is not an editor.
  The entry above still holds for everything underneath: the plan, the executor, the snapping
  asymmetry and the harnesses are untouched. Only the controls over them changed.

- **Copying what you already captured** (`GalleryExport.publishFolderOnRequest` + two rows on
  the Recording screen) — the automatic export only ever sees a file at the moment it is made,
  so everything taken before the setting was turned on, or while it was off, is invisible to
  every gallery on the phone and has no route out: the launcher's folders are under
  `Android/data`, which from Android 11 cannot be browsed to at all.
  **Skipping matters more than copying.** A bulk action is one people press twice, and without
  a check the second press fills the gallery with a duplicate of everything. Each name is
  looked for first, through the media store on Android 10 and up and in the public album below
  it, and already-there is counted apart from copied so the row can say "nothing new" rather
  than appearing to do nothing. The lookup **errs towards copying** on any failure, because a
  duplicate is visible and deletable and a refusal is neither.
  The folder is resolved on the **main thread** before the copy moves off it, because
  `recordingsDirectory()` and the screenshots path both go through `LauncherProfiles` (§12.7).

- **Managing a world** (`ui/content/NbtEditor.java` + `WorldModel.kt` + `WorldSheet.kt`) —
  name, game mode, difficulty, cheats, a copy, a backup zip, and turning hardcore off, none of
  which needed the game. Game files could list a world and delete it; tapping one handed its
  folder to an intent, which from Android 11 nothing on the phone can act on, so the one row
  whose tap reliably did nothing was the kind the game is made of.
  **The splice is the decision.** The obvious implementation parses `level.dat` into a tree,
  changes a field and writes the tree back. That would be wrong here, because the only reader
  available is `NbtReader`, which is built for a screen: it caps lists and nesting so a
  malformed file cannot hang the launcher, and it collapses NBT's twelve tag types onto Java's.
  Serialising its output would silently drop whatever those caps cut and would have to guess
  the element type of every empty list. So nothing is rewritten: the decompressed bytes are
  walked once recording where the value sits, and the new bytes are spliced into that range.
  Every other byte is the byte that was there, including the parts this code does not model and
  the parts a future Minecraft adds. **The blast radius is the one field.**
  **Being wrong has to be cheap**, which is the rest of it. `edit` writes a temporary file,
  reads it back **with the shipped reader** rather than a check of its own, and only then
  replaces anything, keeping the original as `.bak`. A splice that corrupted the file fails
  before it lands. That is what makes offering this reasonable at all: a world is somebody's
  hundreds of hours, and 16.23's arithmetic says a feature whose correctness is invisible needs
  the cost of being wrong brought down rather than the odds talked up.
  **The folder name is not the world's name.** Minecraft shows `LevelName`, so renaming writes
  that first and succeeds or fails on it; the folder follows as a courtesy and a failure there
  is not worth undoing the rename that worked, because nothing reads the folder's name.
  **Turning hardcore off also sets survival**, because a hardcore death writes GameType 3 as
  well, and a world that is no longer hardcore but still locked to spectator is half rescued.
  **A copy is a copy and a backup is a zip in Downloads**, for the texture pack exporter's
  reason: a backup written beside the world is one nobody can reach, send or copy off the
  phone.
  Checked by `nbtsim`, which drives the real class; fifteen mutations fail it. Three coverage
  holes were found by mutating rather than by reading, and each is now a fixture: a compound
  that must be stepped **over** rather than entered, a name long enough to need both bytes of
  the length prefix, and a string too long for it at all.

- **Adding a file, with the guess shown before it is acted on** (`ImportPlan` + `ImportDialog`)
  — the add button still works out where a file belongs by looking inside it, and that is still
  right. What was wrong is that the decision was also the action.
  **A guess that is right nearly always is still a guess**, and the two questions most worth
  asking of an import both land in the gap between deciding and writing: is this really a mod,
  and do you know you already have this one. So `planImport` stops at the decision and hands
  back a staged file; one tap accepts it, which is no more work than before, and "choose a
  folder" is one tap away instead of impossible. The folder picker is no longer only for what
  could not be identified.
  **The duplicate check is why this is a dialog and not a toast afterwards.** Two jars
  declaring one mod id is a loader that refuses to start, naming an id the player has never
  seen, and the usual way to get there is the most ordinary thing anybody does: updating a mod
  by adding the new file and forgetting the old. Replace is offered first, in the error colour,
  with Keep both beside it and Cancel spelled out rather than left to a tap outside, because
  the primary action here deletes a file.
  **Matched on the declared mod id, never on the file name**, the same rule the folder's own
  duplicate check follows: "sodium-0.6.jar" beside "sodium-0.5.jar" is the case this exists for
  and no amount of stemming tells it from "reeses-sodium-options.jar". A jar whose metadata
  will not parse has no duplicate, which is `UNKNOWN never CONFLICTS` again.

- **Duplicates, sorting, and a download you can see** (`ContentCompat.duplicateModIds` +
  `ContentSort` + `ModInstall.Progress`) — three complaints about the mods half of Game files,
  with one shape between them: the screen knew things it was not saying.
  **A duplicated mod id is the one failure in the folder that stops the game starting** rather
  than stopping a mod loading, so it sits above a missing dependency in the row's one warning
  line and below the two that say the mod cannot run here at all. "Turn them off" had to learn
  about it: both copies carry the warning and switching both off removes the mod entirely, so
  the newest of each group survives. Told apart by **the sentence the row is showing**, not by
  whether the mod is duplicated, because a second copy that is also built for the wrong version
  should be turned off like any other mod that cannot run.
  **Newest first is the default sort**, and for a mod that is literally last installed: a jar's
  timestamp is when it was written. The old default buried a mod somebody had just added
  alphabetically among two hundred others. Sorting moved out of the scan, because a world's
  real timestamp and a mod's real size only arrive in the slow pass and ordering on
  placeholders makes the list reshuffle under a thumb.
  **The install band replaces an 18dp spinner inside the button that was tapped**, which says
  "something is happening" and nothing else, and is invisible the moment that row scrolls away
  — which during a dependency walk is most of the wait. Progress is reported **per file and
  never for the whole**, because the whole is not known until it is over: dependencies are
  discovered as the graph is walked, so a bar across everything would have to lie about its own
  denominator. Throttled at the source at 128KB (16.30), and `modrinthsim`'s download stub now
  actually drives the monitor so the throttle is a thing checks can see (16.16): removing it,
  dropping the final partial step, or setting the step to one byte each fail it.

- **Saying what it costs** (the onboarding's honesty page + `SettingsRoute.DEMANDS`) — one
  page in the welcome saying this is capability first and not frame rate first, and four
  screens of mechanism in Settings for anybody who wants to know why.
  **Two audiences, two lengths, and that split is the whole design.** Somebody meeting the
  launcher needs the warning before they form an expectation; somebody chasing a frame rate
  needs the explanation, and they are not the same person at the same moment. The page in the
  welcome is four sentences and points at the other one. The other one is reached from
  **Performance**, above the controls rather than below them, because that is where the person
  with the problem already is and what it says is which of those rows can help and which
  cannot, and from **About**, where somebody merely curious looks.
  **It is written about mechanisms, not numbers.** "One extra full screen copy and one
  hardware encode per frame" survives a rewrite of the recorder; a percentage does not. Nothing
  on that page is generated from the code, so it is prose that can go stale, and writing it
  this way is what keeps the staleness harmless.
  **The last section is the one that earns the rest.** It says plainly that upstream's
  optimisation work is all still here, that what gets built on top is features rather than
  frames, and that this is the wrong trade for some phones. It also says why there is no one
  tap optimise, which is the performance mode entry above, told to the player rather than to
  the next person to read this file.
  It is **prose in settings rows**, not a document: same ground, same search index, no second
  vocabulary. It gets **no comparison row**, because honesty is not a capability.

- **Taking what is worth taking from upstream** (`scripts/check_renderer_env.py`,
  `check_log_formats.py`, `check_ported_fixes.py`) — 21 commits landed upstream between
  2026-08-27 and 2026-09-22, all by one maintainer, and nothing since. Five were ported, three
  were deliberately refused, and the refusals are the half worth recording.
  **Ported.** MobileGlues was being handed a GL ES 2 context, and an ES 1 one on rd and indev
  versions (upstream `3ad110090`, one line). IME range clamping, which fixes a real crash from
  swiping an IME's space key to the end of the input box: our file was byte-identical to
  upstream's pre-fix version, so the fix was taken as the whole file rather than as a patch
  (`8da3cf972`, from the TouchController organisation, the one outside contribution in the
  window). The AMD FSR 1 upscaling picker, removed because upstream's own commit body says it
  "does next to nothing visually and has some graphical bugs on some adrenos" (`90668f783`): the
  control *works*, which is worse than a dead row, because its label promises upscaling it does
  not deliver. Android 13 Game Mode (`c11e6f995`). And one bug of our own found while diffing for
  the port, `sdl_hook.c` logging two `%p` with one argument on the hook-init path at every launch,
  which came in on a fork-side commit upstream's default branch never took.
  **Refused, and these must stay refused without a fresh reading.** Upstream's Turnip rewrite
  (`cc4fc5c74`) deletes both readers of `POJAV_TURNIP_DIR` and `POJAV_TURNIP_SONAME`, so merging
  it silently reduces the driver picker from "system, bundled, and N imported" to "system and
  bundled" with no error anywhere; it also reshapes the `nsbypass` API and adds an SSH-only
  submodule as a build dependency. Removing `libcutils.so` from the kopper-zink AAR
  (`b04dfa2ce`) would break Kopper Zink outright here. And the two MobileGlues removals that are
  not FSR are live rows for us, because we ship MobileGlues 1.3.4 and upstream's reasoning
  follows their 2.0.0 bump.
  **The SDL controller-detection rewrite is deferred rather than refused** (`bc0736010`). We do
  carry the bug: `sdlEnabled` is set from SDL subsystem init, so a mod initialising the gamepad
  or joystick subsystem for any reason takes the launcher's own pad emulation away for the
  session, and `onDirectInputEnable` is one-way with no disable anywhere in the tree. But the fix
  is 332 lines of bytehook C and Java across four files, it cannot be exercised without a
  controller and a device, upstream needed a follow-up two hours later for copy-pasted action
  numbers, and their `DEINIT` arm re-enables direct input on the way out. That is 16.23's
  arithmetic: a long loop, an invisible failure, and nothing on this side of the wall that can
  judge it. It wants a device, not another careful reading.
  **Each port got the check it could have.** The MobileGlues fix is the one that generalises, so
  the rule it encodes is per-branch rather than per-renderer: every `opengles*` branch in
  `setJavaEnvironment` must set `LIBGL_ES` itself, because the generic derivation below is guarded
  on `containsKey` and `OPEN_GL_VERSION` has already filled it with a number chosen for a
  different purpose. Writing that check found a **second** `opengles_mobileglues` branch in
  `loadGraphicsLibrary` that the analysis had missed, which turned out to be correct as it stands
  and is why the check pins the call order too: `setJavaEnvironment` applies the map before the
  renderer is dlopened, and swapping them would load MobileGlues before its context version was
  decided. The log-format check generalises the `sdl_hook.c` defect across the whole JNI tree, as
  an arity counter rather than a type checker, and it reports what it skipped so the coverage
  number stays honest. Game Mode and the IME clamping get structural guards and nothing stronger,
  which is said plainly: one is a manifest declaration only the platform reads, the other is
  inside an Android view class.
  **Upstream's Game Mode declaration is very likely inert and ours is not.** They put the
  `<meta-data>` as a direct child of `<manifest>`, where `<meta-data>` is not a valid element and
  the platform does not look; it belongs inside `<application>`. Worth having at all because we
  own both knobs an OEM would reach for, the resolution scaler and the reachable game area, and a
  downscale stacked on somebody's own 80% inset is exactly the silent rewriting of a saved
  preference the performance-mode entry warns about.

- **Pages** (`customcontrols/ControlPages.java` + `ControlData.pages` + `SPECIALBTN_PAGE`) —
  several sets of controls in one layout, one showing at a time, with a button that moves
  between them: building on one, bridging on another, PvP on a third. Asked for in those words,
  and the reason is a real bind: a layout that can do everything covers the screen, and a layout
  that leaves the screen clear cannot do everything.
  **A page is a filter on the controls, not a second layout.** The obvious model is a list of
  whole layouts, and it is wrong twice over: the furniture that belongs on every page (the
  stick, the menu button, the page buttons themselves) gets duplicated per page and then drifts
  apart the first time somebody drags one, and nothing can express "on building and PvP but not
  bridging". So membership is a bitmask on the control, in the same spirit as `displayInMenu`,
  and **0 means every page**, which is what every layout ever saved deserialises to (§12.14).
  **Pages apply while the cursor is grabbed and never while a GUI is open.** The ungrabbed state
  already draws a deliberately minimal set, and `check_layouts.py` enforces a floor of menu,
  keyboard and mouse there precisely so a layout cannot strand somebody in a chat box. Filtering
  that set by page as well would be a second, invisible route to the exact state that floor
  exists to prevent, and it buys nothing: a page is about what your thumbs need while playing.
  **The control center's rail is why no page can be a dead end.** It is the one piece of this
  that is not optional: a page whose only switch button you forgot to place would otherwise be a
  room with no door, and the sheet is reachable from the pull tab or a menu button, which the
  floor already guarantees. That rail sits **above** the recording card, which is the one place
  the launch-console-era rule "recording is the card at the top" gives way, and deliberately: a
  safety net you have to scroll to find is not a safety net. Layouts without pages are untouched
  and recording keeps the top on every one of them. The layout report names a page with no way
  off it, and `check_layouts.py` asserts it for shipped layouts, written while the count was
  zero (§16.35).
  **The visibility rule lived in two places and only one of them was obvious.**
  `ControlLayout.setControlVisible` re-applies it to every control at once;
  `ControlInterface.onGrabState` applies it to one when the grab state changes underneath it.
  A page filter written into only the first would have meant that closing an inventory quietly
  brought back every control from every other page. Both now call `ControlPages.visible`, which
  is the only copy.
  **Deleting a page re-points everything that referenced one.** Masks above the hole shift down
  so each keeps naming the page it always named, a control that lived only on the deleted page
  lands on the page that took its place rather than on none or on all of them, and a button
  pointing at the deleted page becomes a cycle button rather than silently pointing at whatever
  slid into the slot. All three are in `ControlPages` where `pagesim` can drive them, because
  getting any of them wrong moves somebody's buttons and nothing in the build would see it.
  **`targetPage` is one based so that zero can mean "cycle".** A page button somebody has just
  bound and not configured moves to the next page, which is what most layouts want and nobody
  has to set; an absent field deserialises to zero and lands on exactly that.
  **In the editor you arrange one page at a time**, because stacking four pages of buttons on
  top of each other is the clutter this exists to remove, and a control added lands on the page
  being looked at. Unticking a page does **not** hide the control there and then: it would make
  the thing you have selected vanish from under the panel editing it. The filter catches up when
  you switch, and the row says how many pages it is on in the meantime.
  Switching works in a **test session**, which is the second exception beside hide-controls and
  for the same reason: it is pure view code about the controls rather than about the game, and
  trying a layout is exactly when you want to know whether its pages reach each other.
  It gets a **comparison row**, `No` for upstream, on auto-walk's reasoning: upstream has no
  equivalent at all. It gets **no onboarding page**: you meet it in the editor, where it is
  turned on.

- **Everything the sheet can do, from a button** (`SPECIALBTN_RECORD`, `_SHUTTER`,
  `_LOGOUTPUT`, `_QUICKSETTINGS`) — recording, the floating shutter, the live log and quick
  settings are bindable now. Asked for as "screenshot, recording, keyboard and more which is
  not available", and the audit behind it is the useful part: screenshot and both keyboards
  already existed as actions, and **recording did not**, which made the one feature this fork
  exists for the one thing the control center could do and a button could not.
  **Force close is deliberately still not bindable**, and that is the only thing left out. A
  rare, irreversible action does not get a target a thumb can find mid-fight (§3); it stays
  last in the sheet, quiet, behind a confirmation.
  Each routes to the method the sheet already calls, which closes the sheet first and is
  harmless when it was never open, so there is one implementation per action rather than two
  that can drift.
  **The positional invariant now has a check.** `getSpecialButtons()` must stay in
  `-(index + 1)` order, because the raw negative integer is what lands in saved layout JSON and
  the editor converts position to keycode by arithmetic over the list: inserting one in the
  middle silently re-points every button in every layout anybody has ever saved, with no error
  and no way to tell afterwards what was meant. That rule was written in the javadoc and
  enforced by nobody, across nine appended entries. `scripts/check_specials.py` reads the
  declarations and the array and asserts it.

- **Which keyboard a keyboard button opens** (`PREF_SYSTEM_KEYBOARD`) — a switch in the Typing
  section that sends every route to a keyboard to the phone's own IME instead of the launcher's
  board. Asked for directly, and both keyboards already existed: `SPECIALBTN_KEYBOARD` has
  raised the system IME since upstream and `SPECIALBTN_GAMEKEYBOARD` opens ours.
  **The choice is made in one place**, `MainActivity.onClickedGameKeyboard`, which is what a
  control button, the control center and anything added later all pass through. Deciding it per
  call site is how a launcher ends up with two keyboards that disagree about which one is the
  keyboard, and it is the same reasoning as `setBackground()` owning the three control styles.
  **`SPECIALBTN_KEYBOARD` is left alone.** It means the system IME, every layout ever shared
  depends on that, and a setting that redefined it would change what somebody else's layout does.
  The switch is about what "the keyboard" means, not about rewriting a binding.
  **The two keyboard actions were renamed for it.** `SPECIALBTN_GAMEKEYBOARD` follows the
  setting, so it is just "Keyboard" now rather than "On-screen keyboard", and
  `SPECIALBTN_KEYBOARD` is "System keyboard, always" because that is exactly what it is: the
  one binding the setting cannot redefine, which is what every shared layout depends on.
  The description says what the trade is in as many words: your own layout, your own autocorrect
  and your own emoji, against more of the screen covered and no way to hold a key down for a
  keybind. It gets **no comparison row**, because upstream has the system IME too and the
  difference is that this one can be chosen.

- **Something to look at while you wait** (`splash/SplashPicker.java` +
  `ui/common/SplashLine.kt` + `ui/game/GameLoadingHost.kt`) — a rotating line on the launch
  console, and a real loading screen over the black gap between the surface being ready and the
  game's first frame. Asked for as "keep dopamine high" across the three waits this launcher
  has.
  **The three waits are one wait at three lengths**, which is why there is one idiom and not
  three. A player who meets a different device at each of them is being told three times that
  these are three different programs. So the launcher getting ready, a version downloading and
  the JVM starting all carry a line from the same table, drawn the same way.
  **The splash line is the one piece of Minecraft personality that is pure copy.** It is the
  yellow text beside the title screen logo, which has said "Also try Terraria!" since 2010, and
  it breaks none of §4's rules because a line of text is never "gaming" chrome: no glow, no
  neon, nothing that would look out of place next to Google Photos. It also costs nothing from
  the motion budget (§8), which the black screen needs for the one thing that does move.
  **On the launch console it sits below the timeline and visibly outside it.** Every line above
  it is the downloader's own report, and that entry's standing rule is that nothing is invented;
  a decorative line dressed as a stage would be exactly the fake progress the console was built
  to refuse. Below, quieter, one line, no tick: it is plainly not a stage.
  **The black screen's honest half is the log.** `Logger` is already streaming the game's own
  output to whatever asks, so the last line it printed is a free, true answer to the only
  question anybody has while the screen is black, which is whether this is working or dead. It
  is dim and monospace on purpose: evidence that work is happening rather than something to
  read. Nothing on that screen is invented, which is the same bargain the console makes.
  **Three ways out, because being wrong locks somebody out of their game.** A full screen
  `ComposeView` over the surface swallows every touch the game is waiting for (§12.9), so this
  one goes when the first frame is presented, when the player taps it, and when a deadline
  passes. The deadline is **short when there is no signal and long when there is**: with a frame
  counter answering, a cold modpack can legitimately take minutes and the counter will end it
  first; with no counter there is nothing to end it, so the overlay has eight seconds and then
  gets out of the way even if what is underneath is still black. That asymmetry is the whole
  safety argument.
  **The first frame is counted in `pojavSwapBuffers`**, which is the single function both the
  EGL bridge and OSMesa are reached through, so the zink path and the gl4es path answer
  identically with nothing shared between them. `GameFrames` latches off after one failure and
  returns -1 forever, because this is read on the launch path where nothing may throw (§16.15)
  and the overlay has its own way out regardless.
  **The order is a shuffle bag, not a random pick.** Over a thirty second wait with four lines
  shown, random repeats often enough to notice, and a rotating line that looks broken is worse
  than no line. `SplashPicker` is plain Java so `scripts/splashsim` drives the shipped class,
  and the property it exists to check is the **seam**: a bag is fine by construction, and the
  only place a repeat can happen is between the last line of one order and the first of the
  next, about one time in N, which is often enough to be seen and rare enough to survive a
  casual test.
  It gets **no onboarding page and no comparison row**, on the opening's precedent: a loading
  screen is presentation rather than capability, and the table is only worth reading because it
  is edited.

- **Two keys on one button** (`customcontrols/KeyCombo.java` + the dispatch-time key state in
  `jni/input_bridge_v3.c`) — a button bound to Shift and F3 opened the debug overlay and never the
  profiler chart, in either binding order, every time. Reported with the half that solved it: the
  launcher's own on-screen keyboard did the same combination correctly.
  **The state a poll reads was written a frame before the callbacks it belongs to.**
  `critical_send_key` stamped `keyDownBuffer` at send time and queued the callback for
  `pojavPumpEvents`, so every key one touch event produced reached its final state before the first
  of them was delivered. Minecraft decides the chart on F3's *release*
  (`renderDebugCharts = renderDebug && Screen.hasShiftDown()`) and `hasShiftDown` polls
  `glfwGetKey`, which reads that buffer and nothing else. So when F3's release arrived, Shift had
  already been stamped released. The keyboard worked because a latched Shift is held across frames,
  which is the one arrangement that survives the gap.
  **The order the keys were sent in could not be observed at all**, which is why every theory about
  the array being the wrong way round was wrong, and worth remembering: with an eager write in
  front of a queue, no amount of reordering on the sending side is visible to a reader that polls.
  Stamping the buffer where the event is dispatched is what makes send order mean something, and
  puts the write and `glfwGetKey`'s read on the same thread into the bargain.
  **A chord is not a set.** With the poll fixed, the launcher still has to send one: modifiers
  down first, and released last, which is the reverse of the press rather than a second rule that
  could disagree with it. A control button has no truth to report here, since one finger lifts and
  both keys leave together, so it has to choose a model, and the only faithful one is the one a
  hand performs.
  **`isModifier` and `setModifiers` are one list**, checked by `scripts/combosim/check_agreement.py`.
  A key ordered first that no flag follows promises something nothing downstream can honour; a flag
  written for a key not ordered first is set after the key it was meant to modify has gone. Super
  is in neither, because there is no `holdingSuper` and `getCurrentMods` cannot express it.
  Right Shift, Right Control and Right Alt now set their flags, which they never did.
  It gets **no comparison row**: upstream has the same bug, and a row saying so would be about
  upstream rather than about a capability.

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

21. **Moving a child does not re-record its parent's display list.** The editor's selection
    outline is drawn by `ControlLayout.dispatchDraw`, and dragging a button left the outline
    behind at the position it had just been dragged away from. Nothing about the drawing was
    wrong: on the hardware path `setX` transforms the child's own render node and damages the
    parent, but the parent's display list is not re-recorded, so `dispatchDraw` never runs again
    and the last thing it drew is still what is on screen. Anything a `ViewGroup` draws *about* a
    child has to be invalidated when that child moves, and doing it on the drag path is not
    enough: the editor's own sliders and a snap both move a control with no touch event reaching
    the layout at all. The fix is a pre-draw watcher on the layout that compares the selection's
    bounds against what it last drew, which is the one point every mover has to pass through.
    The grip did not have this bug and that is why it went unnoticed: `ControlHandleView` is a
    *view*, so moving it works, and only the drawn decoration was stale.

22. **A frame derived from gravity is a frame that assumes the user is standing up.** Player space
    takes yaw around the world's vertical axis, which is the right axis for a player who is
    upright and no axis at all for one who is lying on their side: gravity is then along the
    screen's *right* axis, contributes nothing to the pair the formula reads, and yaw goes to
    nearly zero while pitch, which never consulted gravity, stays perfect. The report was exactly
    that shape, "vertical is fine, horizontal is dead", and that asymmetry is the signature worth
    remembering, because it points straight at the one axis that was derived rather than read.
    The general form: any quantity computed by projecting onto a measured direction needs the
    length of that projection checked, because it is the confidence, and code that uses the
    direction without it has a silent degenerate case wherever the length goes to zero.
    `scripts/gyrosim` could not see it, and for the reason lesson 20 gives rather than a new one:
    its `gravity()` helper took an up component and a normal component and **pinned the third axis
    to zero**, so every fixture ever written with it put gravity exactly in the plane player space
    reads, where player space is perfectly conditioned. The harness could not express the broken
    pose, so it agreed with the bug. It now takes all three.
    Mutating the fix found a second hole the first round of checks did not: every side-lying
    fixture turned the same way, so a fallback that returned a magnitude instead of a signed rate
    passed all of them. **A direction needs a check that turns both ways**, and reading the test
    was never going to reveal that. Breaking the code on purpose was.

23. **When being wrong is invisible and the loop is long, stop paying for guesses.** The control
    test world shipped three times and never once opened. Each attempt was more careful than the
    last, and the third read Mojang's own 1.20.1 sources rather than reasoning from memory, which
    found something real: `DimensionOptionsRegistryHolder.toConfig` marks a world experimental
    unless the merged dimension count is exactly three, and quick play answers a confirmation it
    cannot show by silently returning to the title screen. That was almost certainly *a* bug. It
    was not the last one, and the shape of the problem is why.
    Everything about the loop was hostile. The failure was **silent**: no crash, no dialog, no log
    line worth the name, and a world that would not open looked exactly like a world that was
    never created. The verifier lived **in the other program**, which is not in this container and
    cannot be run here, so every check that could be written was a check of the format and never
    of the policy. And each iteration cost **a version download on the user's device**, so the
    feedback loop was hours long and returned one bit. Three of those bits bought nothing.
    The lesson is not about NBT. It is that the expected number of attempts, times the cost of an
    attempt, is a number worth estimating *before* starting, and that a feature whose correctness
    can only be judged by software you cannot execute should be scoped so that being wrong is
    cheap. The alternative that shipped instead had been sitting there the whole time, is
    verifiable entirely on this side of the wall, and turned out to answer the question better.

24. **A scoped composable can be resolved from a scope you are not in.** `AnimatedVisibility` has
    `RowScope` and `ColumnScope` overloads but no `BoxScope` one, so writing it inside a `Box`
    that happens to sit inside a `Column` picks the `ColumnScope` extension, reaching past the
    innermost receiver to one that is no longer applicable, and fails with "cannot be called in
    this context with an implicit receiver". The same call compiles directly inside a `Row`,
    which is why the identical line in `LaunchConsole` was fine and the one in `LogScreen` was
    not. Lifting it into its own composable, where no scope is in reach, leaves the plain
    overload as the only candidate. Kotlin resolves extensions by what is *in scope*, not by
    what is nearest, and Compose's layout scopes make that difference visible.

25. **Language choice is a verification decision.** The log viewer's level parsing was written in
    Kotlin, which made it uncheckable: there is no Kotlin compiler in this container, so the only
    harness possible re-implemented the algorithm in Python and read the constants out of the
    source. It passed while **six of eight deliberate mutations to the shipped code went
    unnoticed**, because what it drove was the copy. Rewritten as plain Java it compiles at
    source 8 and the harness drives the real class; all ten mutations now fail it. Every parser
    here that has a harness is Java, and that was not a coincidence anybody had written down.
    **Before writing logic whose failure is silent, ask what can execute it before a user does.**

27. **Deleting a settings screen can orphan the write half of a preference and leave the read
    half enforcing it.** The Sodium warning was always a warning with a way past: a
    `sodium_override` boolean, set by a `MathQuestionPreference` row in `pref_experimental.xml`.
    The Compose settings rewrite deleted that screen and that class, and the only *writer* of the
    preference went with them. The *reader*, in the launch path, was untouched and carried on
    refusing, so the dialog's one remaining button deleted the mod and Sodium became unrunnable,
    taking Iris and every shader pack with it. Nothing in the build can see this: both halves
    compile, the preference key still resolves to `false`, and the screen that is gone is gone from
    a different file. The generalisation is that a preference is a *pair*, and a migration that
    moves settings has to account for every key by both halves: a reader with no writer is a
    constant, and a constant that gates a feature off is a feature deletion nobody wrote.
    Worth grepping for on any future settings move: every `getBoolean`/`getString` key in the app
    against the keys the new screens actually write. That scan was run when this was found, and
    the only other reader with no writer is `disable_autojre_select` in
    `JavaGUILauncherActivity`, which is left alone deliberately: it defaults to `false` and
    `false` is the automatic behaviour, so the orphan makes a rarely wanted override unreachable
    rather than gating a feature off. `controlPocketSkin` and `zinkPreferSystemDriver` also read
    without being written, and both are the documented read-once migrations in §14.
    **That asymmetry is the thing to check first.** An orphaned reader whose default is the
    permissive value is a lost option; one whose default is the restrictive value, which
    `sodium_override` was, is a feature deleted silently.

28. **A renderer is not a capability, and a version you forced is not a measurement.** The Sodium
    verdict asked "which renderer is selected" and answered SUPPORTED for Zink everywhere. Zink is
    Mesa, and Mesa's advertised GL version is a function of the Vulkan driver beneath it: the
    bundled Turnip carries every feature it gates GL 3.3 on, a stock ARM Mali driver fails three of
    the six it gates GL **2.1** on. So the one renderer the launcher was proudest of was silent on
    the devices that most needed a warning, and a Mali player got a crash where the dead-end dialog
    had at least said something. **The general form: an abstraction layer's ceiling is the layer
    under it, so a verdict about a layer has to name what it is standing on.**
    The second half is why it could not be caught from inside. `JREUtils` sets
    `MESA_GL_VERSION_OVERRIDE=4.6COMPAT` for every zink renderer, so the context reports 4.6 on a
    device that cannot do 3.3, and Mesa documents that the override "may not really implement all
    the features of the given version". **Code that forces an override can never use the overridden
    value as a check**, and the reflex that saves you is to ask which of your inputs you wrote
    yourself. `Gpu` therefore carries no reported-GL field, and `sodiumsim` asserts the Zink verdict
    is invariant across every GL ES version rather than testing a case, so adding one back fails.
    It was found by an adversarial pass told to **refute** the claim rather than check it, and the
    evidence it brought was a real Mali-G78 log plus upstream's own renderer docs, neither of which
    this project would have produced on its own. The claim had sounded right for a week.

26. **A harness in another language inherits that language's arithmetic, not the shipped one's.**
    `check_joystick_directions.py` transcribes an eight-way `((angle + 22.5) / 45) % 8` bucket out
    of Java. Java's `%` on doubles keeps the sign of the dividend; Python's keeps the sign of the
    divisor. For every input the shipped code actually produces they agree, so the harness passed
    and looked fine — but the difference is exactly what the angle-wrapping step upstream of it
    exists to guarantee, so with Python's `%` the harness silently repaired an unwrapped negative
    angle into the right bucket and **could not tell a working wrap from a deleted one**. Found by
    deleting the wrap and watching the check still pass. The same trap is waiting in integer
    division, integer overflow, string comparison and date handling. **When a check is a
    translation, the operators are part of what was translated**, and the ones whose edge cases
    differ silently are the ones to look up rather than assume.

29. **A local `val` shadows an implicit receiver's member of the same name.** `DrawScope` exposes
    `density` as a `Float`, and the timeline composable had `val density = LocalDensity.current`
    a few lines above its `Canvas`. Inside the draw lambda `10f * density` therefore resolved to
    `Float * Density` and failed, while the declaration itself looked entirely ordinary. Kotlin
    resolves a local ahead of a receiver's member, so the error appears only where the value is
    used and names a `times` overload rather than the shadowing. The general form is 16.24's the
    other way round: there, a call reached *past* the innermost receiver to an outer scope; here
    a local hid a receiver's member. Both are invisible at the line that causes them. When a
    composable computes something from `LocalDensity.current` and also draws, convert at the call
    (`with(LocalDensity.current) { X.toPx() }`) and keep no `density` local at all.

30. **A callback on a per-item loop is a main-thread flood in disguise.** `ClipEditor` reported
    progress for every sample it copied, which is a quarter of a million times across the two
    tracks of a forty minute clip, and the activity hopped to the main thread on each one to move
    a bar. Nothing about either half looks wrong: the executor reports what it knows when it
    knows it, and the caller does the normal thing with it. The cost only exists where they meet.
    **Throttle at the source**, because a caller cannot cheaply discard what it has already been
    handed on the main thread. And once throttled, **completion has to be said explicitly**: the
    running figure could never reach 1, since the last sample's timestamp is by definition before
    the end of the span, so the bar stopped at 99% and sat there. The check that found it was the
    one that asserted the *end state* rather than the rate, which is worth remembering when
    writing a check for anything that counts.


33. **NBT strings are modified UTF-8, and `getBytes("UTF-8")` is not that.** Renaming a world
    to anything containing an emoji failed every time, silently and permanently. A supplementary
    character is four bytes in standard UTF-8 and six in modified UTF-8, where it is written as
    a surrogate pair, and `DataInput.readUTF` rejects any byte at or above 0xF0 outright. So the
    splice produced a file neither `NbtReader` nor Minecraft could parse, the verify step
    correctly refused it, and the player got "could not save" with no reason and no recourse.
    The fix is to encode through `DataOutputStream.writeUTF`, which is literally the encoder
    paired with the reader, rather than hand-rolling a length prefix over `String.getBytes`. The
    general form: **a wire format's string encoding is part of the format**, and the two places
    this is most often wrong are Java's modified UTF-8 (NBT, class files, `DataOutput`) and the
    assumption that a character is a byte. Where the platform ships the matching encoder, use
    it, because then the only way to be wrong is to pick the wrong one rather than to implement
    it wrong. The bound moves with it: `MAX_STRING_BYTES` counted standard UTF-8 bytes, which is
    the wrong unit for the prefix it was writing.
    It was found by an adversarial pass that **compiled the shipped class against the harness
    stubs and ran it** rather than reading it, which is the part worth copying: the harness had
    every fixture it needed to catch this and not one of them used a character above U+00FF.

32. **"Can not extract resource from ParsedResource" means a bare apostrophe, and the message
    names nothing.** Six of them failed a build at once. aapt prints an object hash per
    offender and no string name, no line and no hint about what is wrong, so the only thing
    the log tells you is how many there are; the way the six were found was by counting them
    and then counting the strings in the diff that held a `'`.
    The cause is the part that generalises. Those strings were written by a Python script
    through a heredoc, and `\'` inside a Python string literal **is** `'` — the outer
    language consumed the escape, so what reached the XML was a bare apostrophe that
    `xml.dom.minidom` parses perfectly happily, because it is well formed XML and an invalid
    Android resource. **When one language's source is written by another language's string
    literal, every escape belongs to the outer one**, and the only thing worth checking is the
    file that landed, never the script that wrote it. That is 16.26's trap on the other axis:
    there a harness inherited Python's arithmetic, here a resource inherited Python's escaping.
    `scripts/check_strings.py` now reads the file that landed. It fails on the apostrophe,
    which is an aapt error, and only warns on several non-positional format conversions in one
    string, which is an aapt warning that thirteen inherited strings already carry: a check
    that is red before anybody touches anything is a check nobody runs.

34. **A key that is deliberately stable is a key that can collide.** A mod row's id has
    `.disabled` stripped off it so that switching a mod does not change the identity of its row,
    which is the right call and is also why `foo.jar` beside `foo.jar.disabled` is one id twice.
    Nothing in either file reads as wrong, and the folder is one the launcher now builds itself:
    "turn them off" renames a jar to `.disabled`, and installing the mod again writes the
    original name back next to it. A `LazyColumn` throws on the duplicate at the moment the
    second row is **composed**, so the symptom is a crash while scrolling and not a screen that
    fails to open, which points the reader at the scroll rather than at the scan. The same id was
    also the dependency graph's lookup key, so both rows silently shared one verdict.
    The general form: **deriving a key by discarding part of the thing it names makes two things
    share a key, and the place it surfaces is wherever the key is required to be unique rather
    than where it was built.** Worth asking of any id built by stripping a suffix, lowercasing, or
    normalising whitespace. The repair is to rename only the rows that actually clash, so the
    stability the stripping was for survives everywhere it was not the problem.

35. **A rule with no check is a rule that is already broken somewhere.** §15 has banned em
    dashes in user-facing copy since it was written, and the only thing enforcing it was
    whoever was reading the diff. Adding fifty two lines of splash and tip copy in one commit
    is exactly the change that slips one through, so the ban went into `check_strings.py` as a
    hard failure. The point worth keeping is what made that possible: the file had **zero**
    em dashes when the check was written, so it was green from the first run. A check added to
    a file that already fails it is a check somebody has to suppress before they can use it,
    which is §16.32's lesson about an aapt warning thirteen inherited strings already carry.
    **Write the check at the moment the count is zero**, which is usually the moment you are
    about to add the thing that would make it non-zero. The en dash is deliberately still
    allowed: "3–16 characters" is a numeric range and correct typography, and it is not the
    tell the rule is about.

36. **A rule applied to all of something and to one of it is a rule written twice.** The
    on-screen controls' visibility is decided in `ControlLayout.setControlVisible`, which
    re-applies it to every control when something changes globally, and again in
    `ControlInterface.onGrabState`, which applies it to one control when the grab state changes
    underneath it. The two were the same boolean expression, copied. Adding a page filter to
    the obvious one would have worked perfectly until the first time somebody opened their
    inventory, at which point every control from every other page would have come back, because
    the second copy does not know pages exist.
    The general shape is worth the lookout: wherever there is a `for` loop applying a rule to a
    collection, there is often a second, per-item copy of the same rule on the path that handles
    one item changing on its own, and it is usually in a different file and sometimes in an
    interface's default method, which is not where anybody looks. **Grep for the predicate, not
    for the method**, and when there are two, make them one function before adding to either.

37. **An experimental Compose API is a CI-only failure that is decidable here.** A call to
    `combinedClickable` or `ModalBottomSheet` looks exactly like a call to a stable API, and
    without it being opted into, the build fails three minutes into CI with "this declaration
    needs opt-in". It has now cost two cycles: `ModalBottomSheet` on the world sheet, and
    `combinedClickable` on the control center's page rail, where every other use site in this
    codebase had the annotation and the new one did not. Nothing about the call site says it is
    different, which is why reading the diff does not catch it and why `scripts/check_compose_optin.py`
    now reproduces the rule. **The fix for the next one is the annotation and a line in
    `MARKERS`**, because a check that only knows the two APIs that have already bitten is a
    check that will be written again.
    The first draft of it could not see the bug it was written for, which is the half worth
    keeping. Its word boundary was `(?<![A-Za-z0-9_.])`, excluding a leading dot to avoid
    matching a qualified name, and `.combinedClickable(` is **exactly** how a `Modifier`
    extension is always called. So it caught `ModalBottomSheet`, which is called bare, and
    passed cleanly on the file that had failed CI an hour earlier. The tell was the count it
    prints: 14 calls before the fix and 24 after, which is `check_settings_calls.py`'s 183 to 43
    all over again. **Print what a check looked at, and read that number**, because a check that
    has gone half blind otherwise reports exactly the same word as one that is working.

38. **An anchored replacement has to take everything grammatically attached to the anchor.**
    Inserting new properties into `ControlEditorState` meant replacing the line
    `var showInMenu by mutableStateOf(data.displayInMenu)`, and that property's `private set`
    is on the line beneath it. The replacement did not consume it, so the accessor attached
    itself to the next declaration, which was a `val`, and the build failed on "A 'val'
    property cannot have a setter" and "Property must be initialized". **The damage was to a
    line nobody had touched and the diff showed nothing wrong**: the new code was correct, and
    what broke was a property three lines above it.
    This is §16.18 in a new costume. A search for `var \w+ by mutableStateOf(` returns the
    declaration and not the accessor glued to it, so an edit anchored on what the search
    returned drops half a declaration. `scripts/check_kotlin_accessors.py` holds the part of
    that a script can: a bare `set` must sit under a `var`, walking up through the continuation
    lines of a multi-line declaration rather than reading the closing bracket as its owner.

39. **A build whose log you cannot read is not a feedback loop.** Three cycles went into two
    one-line Kotlin errors, because this container cannot reach the blob host a job's log is
    served from: `gh run view --log-failed` gets a 403 and so does the REST logs endpoint, and
    the only thing a failure reported was "exit code 1". Annotations come from the API
    directly, so the build steps now pipe through `tee` and re-emit compile errors as
    `::error::` workflow commands. The next failure named its file, line and message on the
    first try.
    Worth generalising: **when a tool cannot tell you why it failed, fixing that comes before
    fixing the thing it failed on.** Guessing at a compile error costs a full cycle per guess
    and the guesses do not converge, because each one is reasoning about the code rather than
    reading what the compiler said.

31. **A control offered only when a setting is off cannot call code that the same setting gates.**
    "Add to gallery" appeared exactly when the automatic export preference was off, and
    `GalleryExport.publish` returns false early when that preference is off, so the button could
    never once have worked. Both halves are individually reasonable and the contradiction lives
    only in their composition, which is why reading either file alone finds nothing: the
    preference means "copy everything I capture", which is the right thing to gate an automatic
    path on and the wrong thing to consult when somebody is looking at one file and asking for
    it. The general form: **when a new entry point reuses an existing action, check what that
    action's preconditions are, not just its signature** - and when the precondition is the very
    condition that made the entry point appear, the feature is dead on arrival with nothing in
    the build able to see it. Found by an adversarial review told to look for behaviour rather
    than compile errors; no harness could have, because the gate is a preference read at runtime.

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
- **The test session is not the game**, and the differences are the ones you would expect.
  Nothing is sent anywhere, so a key bound wrongly is named wrongly rather than doing the wrong
  thing; there is no world, so nothing tells you whether a button is legible over a nether
  ceiling; and the specials that act on a running game are reported rather than performed. The
  one exception is hide-controls, which is pure view code and about the controls rather than the
  game. **Launching a real Minecraft to test in was built and removed** (16.23); the way to try a
  layout against the actual game is still to launch normally and open the editor from the control
  center, which puts the game underneath it.
- The layout report is **advice, not a verdict**. Two controls sending the same key is normal on
  purpose in plenty of layouts, a deliberately tiny button is somebody's choice, and "off the
  screen" is measured at the size and density in front of you rather than at every one. It names
  what is unusual and never refuses to do anything about it.
- The log keeps the **last sixty edges** and is cleared when the session closes. It is for reading
  a combination back, not for a session's worth of history, and nothing writes it to a file.
- The in-game debug strip names **keys, not actions in the world**. It reports what the launcher
  sent, which is the honest limit of what it can know: whether Minecraft did anything with that
  key is between the game and the player's own keybinds.
- The hotbar and crosshair in a test are **a guide at Minecraft's automatic GUI scale**. That is
  what the game ships with and what nearly everyone leaves it on, but it is a setting, and a
  player who has changed it gets furniture of another size.
- A **toggle latched during a test stays lit** when the session ends. Clearing it would mean
  sending a release, and by then the bridge is detached, so the release would take the real path
  into a native symbol this process does not have. Tapping the button again clears it.
- Testing is offered **only in the editor reached from Settings**. From inside a game the editor
  already has the game underneath it, so leaving edit mode is the test, on the world you are
  actually playing.
- The editor's sky and grass is **a flat backdrop, not a screenshot of your world**. It answers
  the one question a dark editor could not, which is whether a translucent button stays legible
  over something bright; it is not a preview of the game, and a layout that reads well on it can
  still land on a nether ceiling.
- The resize grip is **the corner only**, and its floor of 20dp is smaller than the handbook's
  48dp touch target on purpose. That is a floor against loss rather than a recommendation: a
  control shrunk to nothing stays in the layout, draws nothing and can never be selected again,
  and somebody may still genuinely want a small button.
- A resize **steps in 4dp**, so a size between two steps is not reachable by dragging. The
  editor's own width and height sliders still set any value; the step is the grip's feel, not a
  rule about what a button may measure.
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
- A slide-to-repeat button **stops the moment the finger comes off**, and cannot be latched. That
  is deliberate rather than missing: it is the line between a control scheme and an autoclicker,
  and it is also the only arrangement in which the feature cannot be left running by accident.
- The slide ring in the editor is **centred on the control and the real gesture is not**. It
  measures from wherever the thumb went down, so a press near one edge reaches the threshold
  sooner on that side than the ring suggests. The ring answers how far the distance is against
  the size of the button, which is the question the slider is being asked.
- **Nothing on the button says it can do this.** The gesture is invisible until it arms, at which
  point there is a haptic and an accent wash. A permanent corner marker was considered and
  dropped: on a 46dp button it is clutter, and over a texture pack it reads as a defect in the
  artwork. Which buttons have it is answered in the editor, where it was turned on.
- Repeating sends the **whole button**, all four key slots, exactly as an ordinary press does. It
  cannot repeat one key of a button that holds several, and it cannot repeat a sequence.
- A repeat is **twenty presses a second at its fastest**, which is one per game tick and as fast
  as Minecraft can register a click at all. A slider that offered more would be a number that
  does nothing.
- Sliding off a repeating button onto a **swipeable** one presses that button, which is what
  sliding onto a swipeable button has always done. Nothing about the repeat changes it, and the
  repeating button carries on until the finger lifts.
- The arithmetic is **verified by simulation, not on hardware** (`scripts/repeatsim`). The
  threshold and the gap are checked; the touch lifecycle around them lives in a View and cannot
  be lifted out of one, so what a slide feels like under a thumb is not checked by anything.
- Auto-walk's **tap-to-direction geometry is checked by simulation** (`scripts/check_joystick_directions.py`),
  the same limit as the slide gesture above and for the same reason: `ControlJoystick` extends a
  third-party joystick view, and stubbing that whole surface to compile the real class would cost
  more than the one formula it would be checking is worth. What a double-tap feels like under a
  thumb, mid-stride, is not checked by anything.
- Auto-walk is **off by default** and lives on a switch next to the other two joystick-only
  settings, so a layout nobody has opened in the editor behaves exactly as it always did.
- Auto-walk sends **the same keys a held drag would send**, nothing more: it does not sprint on
  its own, does not turn on its own, and a locked walk into lava is exactly as fatal as a held
  one. Locking is not a safety net, it is not holding the stick.
- The second-finger click is **left click only**, and only with the virtual mouse up. Right click
  in a menu is still a bound control button, which is where it has always been.
- It can **misfire on a very short two-finger flick**: a scroll that both starts and ends inside
  the tap detector's window and moves less than the still threshold is, by every measure
  available at the time, a tap. The threshold makes it unlikely rather than impossible, which is
  why the whole thing is a switch.
- The reachable game area **moves the game and nothing else**. The control buttons keep the whole
  panel, which is deliberate: they are already placeable anywhere in the editor, so anyone who
  wants them nearer their thumbs moves them there rather than having it done to them. It does mean
  a strong inset leaves buttons sitting over the black surround, which is inert.
- **Looking around and moving the virtual cursor both happen inside the picture**, because that is
  the only part of the screen the game receives touches from. With a strong inset the area to drag
  in is correspondingly smaller.
- The cursor **cannot leave the picture**, and that is correct rather than a limit: its position is
  sent onward as a game window coordinate, and outside the window there is nothing to point at.
- The area outside the game is **black and inert**. Nothing is drawn there and touches in it reach
  nothing, which is deliberate: it is not screen the game can use, so it must not be screen that
  half-works.
- It is **verified by simulation, not on hardware** (`scripts/viewportsim`). The geometry is
  checked exhaustively; whether a given inset actually brings the hotbar into somebody's field of
  view is a thing only they can answer, which is why it is a slider rather than a switch.
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
- The log viewer shows **the last megabyte**, which is on the order of ten thousand lines. A
  crash writes its reason at the end, so that is the half worth holding as strings on a phone;
  the beginning, which carries the launch command and the mod list, is what a share sends and
  this does not show. It says which it is doing rather than leaving it to be discovered.
- It reads the file **once, when it opens**, and there is no refresh. Nothing writes
  `latestlog.txt` while the launcher is on screen: the game writes it, and starting the game
  kills the launcher process (§12.2). Re-opening the screen is the reload.
- **Lines are not numbered.** A window onto the end of a file cannot number from the file's own
  start without reading all of it, and numbering from the top of the window would be a number
  that means nothing to anything else. What a search result offers instead is the line in place.
- The level of a line is **Minecraft's word for it**, and a mod logging an error at INFO is an
  error the filter will not find. The viewer reports the log; it does not second-guess it.
- Lines **wrap and cannot be unwrapped**. Two-axis scrolling makes a log unreadable on a phone,
  and a wrap toggle would be a control that is wrong nine times out of ten.
- The **in-game** log is unchanged and is still the live one: the control center's log output
  tile over a running game. This screen is the launcher's, and the two do not share code.
- Gyro aiming is **verified by simulation, not on hardware** (`scripts/gyrosim/`). The maths and
  the axis mapping are checked; what a real MEMS gyroscope's noise floor feels like in the hand is
  not, and neither is the cost of 400Hz sensor callbacks on a weak device.
- Gyro aiming has **no acceleration curve**. Deliberate: the goal is to feel like a mouse, and a
  mouse has none. A "quick turn" boost for large movements is a reasonable thing to want and is
  not built.
- The gyro's 100% is now **1:1**, which is roughly 38% of what 100% used to mean. Anyone who had
  tuned the slider has to raise it once; the range goes to 400% so the old feel is still reachable.
- Player space **hands back to the screen's own axis past about 66° of body roll**, and between 53°
  and 66° the two are blended. That is the right answer for a player lying down, and it does mean
  the phone's own tilt stops being compensated for in that band: someone lying on their side who
  also tilts the phone flat is in a pose neither frame describes, and gets the screen's axis.
  There is no way to tell the two rotations apart from a gravity vector alone, and the fallback is
  the one that is never dead.
- Anyone who **raised the sensitivity to compensate** for the dead horizontal axis while lying down
  now has both axes at that setting and will want to lower it again. There is nothing that can
  detect that honestly, so the release notes have to say it.
- The pre-launch memory check is **a heuristic about a moment, not a fact about a limit**. Linux
  overcommits, so a heap larger than free memory still starts and is killed or thrashes later; and
  Android's own available figure moves as other apps come and go, so the same launch can warn once
  and not the next time. Launching anyway is always offered, and is often the right answer.
- It **cannot see the game process's address space**, which is where a 32-bit device actually runs
  out. That arm stays in `Tools.launchMinecraft`, after the download, because it is the only place
  the process being measured is the one about to start a JVM.
- It **says nothing at all** on a device whose `ActivityManager` will not report its memory, which
  is correct and does mean the check is silently absent there rather than degraded.
- The device facts in the log header are **read once, at launch**, so a report describes the
  session it came from and not the phone as it is now. Anything unreadable says `unavailable`
  rather than being omitted, so a gap is never mistaken for a value.
- There is **no device report screen**. The facts go into the log, which the in-app viewer can
  already show and search, and a screen would be a second place for them to drift from.
- The mod check is **the mod's own word for itself**, so a jar tagged wrongly is judged wrongly and
  a jar that declares nothing is not judged at all. It reads what four metadata formats state; it
  cannot run the mod.
- It says nothing about **snapshots and pre-releases**. `ModTarget` only recognises plain releases,
  and `VersionPredicate` refuses to order a version with a suffix, so a snapshot profile checks
  nothing rather than condemning everything. That is the safe direction and it does mean the
  feature is silently absent there.
- A verdict appears **only once the jar has been opened**, which is the second of the screen's
  three passes. A folder that is still being read shows no warnings yet rather than wrong ones.
- **Optional dependencies are ignored entirely.** A launcher that acted on suggestions would put
  jars in somebody's folder that they never chose, which is the same line the mod browser's
  dependency walk already draws.
- **Turning off the broken ones can uncover more.** A mod that depended on one just switched off is
  now genuinely missing a dependency, so the count can go up before it goes down. That is true
  rather than a bug, and each round strictly reduces what is enabled, so it ends.
- Finding a missing dependency opens **a search, not an install**. The id a mod declares is not
  always what the project is called on Modrinth, so the results are offered rather than the first
  hit installed silently.
- Game files runs in the **`:launcher` process**, which caches preferences separately from the
  process that writes them, so the profile it judges against is the one selected when that process
  last read it. Switching profile and coming straight back is right, because the activity re-reads
  on creation; a `:launcher` process left alive from before a switch is the case that can be stale.
- A multi-key button is **a chord typed in slot order, modifiers excepted**. The launcher can
  order GLFW modifiers because "modifier" is a fact about the protocol, present in the event's
  mods, in `setModifiers` and in `hasShiftDown`. It cannot order F3, because "F3 is a chord prefix"
  is a fact about Minecraft's keybinds in one version range. So `[G, F3]` sends G first and does
  not open the chunk-border view, and the fix is to bind them the other way round.
- **Shift+F3 stopped being the pie chart in 23w33a.** From 1.20.2 the profiler chart is F3 then 1,
  so on those versions the combination to bind is a latched F3 and the `1` key, and no launcher
  change makes the old one work.
- Two controls bound to the **same modifier** still clash: `holdingShift` is one boolean with no
  reference count, so releasing either clears it while the other is held. Pre-existing, and the
  layout report already names keys bound to two controls.
- A modifier inside a **sequence** cannot work, because a sequence releases each step before
  pressing the next. That is what a sequence is for, and it is the wrong tool for a chord.
- `keyDownBuffer` now lags the send by up to one frame, which is GLFW's own contract and what makes
  send order observable, but it does mean a mod polling `glfwGetKey` from its own thread reads a
  value written a frame later than it used to.
- **`VERIFIED` means "this build has positive evidence for this configuration", not "tested on
  your phone".** It is reached only by Zink on an Adreno with a Turnip driver loading, and the
  evidence is that the bundled Mesa Turnip carries every Vulkan feature Mesa gates GL 3.3 on. It
  is not a promise about a particular Sodium build, a particular Minecraft version, or a pile of
  other mods. **Nothing here has been run on a device by this project**: there is no phone in CI
  and no Vulkan loader in the build container, so every verdict is a reading of what the device
  reports plus what Mesa documents, and the only real confirmation is somebody launching it.
- **Mali on Zink is UNTESTED, which is weaker than the evidence and deliberately so.** Two
  independent Zink-on-Mali projects and upstream PojavLauncher's own renderer docs put a stock ARM
  driver around GL 3.1, below Sodium's floor, and a real Mali-G78 log shows Mesa refusing three of
  Zink's six GL 2.1 base requirements. That is evidence about driver versions in general, and the
  launcher cannot query a Vulkan feature set, so a specific device is warned about rather than
  condemned. If somebody reports Sodium working on a recent Mali, that is consistent with this
  and the verdict stays as it is.
- **Shader packs are promised only where the verdict is VERIFIED**, which is narrower than
  Sodium's own floor and is why `shadersViable` is a separate question. Iris will install and may
  even start on a translator; a shader pack leans on compute shaders and storage buffers a GL to
  GL ES layer does not carry. On Mali and on Adreno without Turnip the shader pack rows in Game
  files warn, which is the case the first version got backwards.
- **Zink is the slower renderer on most devices**, so the fix the dialog offers is a trade and not
  a free upgrade: Mesa executing OpenGL over Vulkan costs more than gl4es translating to GL ES. A
  player on a weak device may genuinely prefer gl4es without Sodium to Zink with it, which is why
  "Launch anyway" is always present and why nothing is switched without a tap.
- The verdict is **keyed on the renderer id and the GPU family, so anything new is UNTESTED until
  listed**. That is the safe direction and it does mean a future renderer, or a future chip whose
  Vulkan driver is excellent, still produces a warning until somebody adds it to `SodiumSupport`.
- **The GPU read is three facts and not a capability query.** `GpuProfile` has the GL ES driver's
  vendor and renderer strings, the GL ES major version it will actually give a current context,
  whether the package manager reports Vulkan, and whether Turnip will load. It cannot call
  `vkGetPhysicalDeviceFeatures`, which is the thing that would actually answer the question, so
  a Vulkan probe is the obvious future improvement and is not built.
- The reported GL version is **deliberately not an input**. `JREUtils` sets
  `MESA_GL_VERSION_OVERRIDE=4.6COMPAT` for every renderer id containing "zink", so a Zink context
  reports 4.6 whatever the driver can do, and Mesa's own documentation says it "may not really
  implement all the features of the given version". `SodiumSupport.Gpu` carries no reported-GL
  field at all, and `sodiumsim` asserts the Zink verdict is invariant across every GL ES version,
  so wiring one in later cannot pass quietly.
- **The GL ES version bounds the translators only.** It decides MobileGlues and LTW, because a GL
  to GL ES translator cannot expose more than its backend, and it decides nothing about Zink,
  which does not go through GL ES. A reading of 0 means unknown and decides nothing either way.
- **An unset profile renderer cannot be resolved on the launcher side.** The automatic pick depends
  on the version's asset index, which the launcher process does not read, so the dialog names it
  "the automatic renderer" rather than guessing between gl4es and MobileGlues. Both warn and both
  get the same offer, so the player is never misdirected, but the message is vaguer there.
- Detection is **a substring match on jar file names**, so a mod whose name happens to contain one
  of the six family names is taken for Sodium, and a Sodium fork named something else entirely is
  missed. `scripts/sodiumsim` asserts the list does not match a set of popular unrelated mods,
  which is the direction that costs something: a false match sends somebody with no Sodium to
  change their renderer.
- A `.disabled` jar is **not** detected, matching the mod checker's rule: a disabled mod is absent
  from the game, so warning about it would be warning about nothing.
- The shader pack row's line is **about the renderer and nothing else**. It does not check whether
  Iris is installed, whether the pack's own format matches the Iris version, or whether the pack
  works: those are the pack's business and Iris's. It answers the one question that is knowable
  from here and is wrong often enough to be worth saying.
- It is read from the **profile selected when the activity was created**, like the mod check beside
  it, so switching renderer in another process and coming back without the activity being recreated
  shows the old answer.
- **Nothing was done about Sodium's own compatibility checks beyond what was already there.**
  Sodium's anti-launcher detection does not fire on this fork (renamed environment variable,
  different application id), and `ISSUE_2561` was already turned off. If a future Sodium version
  adds a check this fork does trip, that is a new problem and not one this handles in advance.
- **This fork is now behind upstream on the SDL controller path, knowingly.** A mod that
  initialises the SDL gamepad or joystick subsystem still takes the launcher's own pad emulation
  away for the rest of the session, and `onDirectInputEnable` has no counterpart. Upstream's fix
  exists and is not ported, because it cannot be exercised without a controller and a device.
- **MobileGlues is 1.3.4 here and upstream has moved to 2.0.0.** Two of their settings removals
  are correct for 2.0.0 and wrong for us, so the renderer tuning screen deliberately diverges.
  Anyone bumping the AAR has to revisit the multidraw rows in the same change.
- **Game Mode is a declaration, not a behaviour**, and nothing here can confirm an OEM honours
  it. It is inert below Android 13 and on any device whose vendor ignores the manifest.
- The FSR removal leaves `mg_renderer_setting_fsr` in SharedPreferences with **no reader and no
  writer**, so MobileGlues falls back to its own default for anyone who had set it. That is the
  safe direction of §16.27's asymmetry rather than an oversight: a reader with no writer is the
  dangerous one, and there is no reader left.
- The ported fixes are **verified structurally, not run**. `check_renderer_env.py` reads
  `JREUtils` as source, `check_ported_fixes.py` reads the manifest and one Java file as text, and
  `check_log_formats.py` counts conversions. None of them starts a game.
- **A clip can only start where the video can start.** The trim start and the far side of every
  cut land on the next sync frame, roughly every two seconds, because the edit is a stream copy
  and a decoder cannot begin anywhere else. The trim end and the near side of a cut are frame
  accurate. Snapping is always forward, so a cut removes at least what was asked for and may
  remove up to two seconds more.
- **Playback is `MediaPlayer`, with everything that implies.** No frame stepping, no speed
  control, no scrubbing that decodes every frame: seeking is exact only from Android 8, and
  below it the player lands on the nearest sync frame, which is the same two second grid the
  trim start snaps to. The scrub is debounced by 60ms, so the picture follows the finger a
  moment behind.
- **Playback steps over deleted spans rather than hiding them.** The player is playing the
  whole file and being seeked past what will not be in the output, so a skip is a seek, and a
  seek takes as long as it takes. Expect a visible stutter at each cut, more on a slow device
  and more on a long clip. The saved file has no stutter: nothing is skipped in it, the
  material is gone.
- **Nothing in the preview is the output.** It is the source file with parts jumped over; the
  exported clip is written by `ClipEditor` from the plan. The two agree about which material
  survives and they are not the same code, which is deliberate (one needs a device, the other
  is driven by a harness) and does mean the preview cannot prove the export.
- **There is no undo, only Restore and Reset.** A deleted span can be given back by selecting
  it and pressing Restore; a split cannot be removed once made, and the way out is Reset.
  Splits cost nothing in the output, so a stray one is harmless, which is why removing them
  individually was not worth a second gesture.
- **A clip whose frames are stored out of order cannot be edited.** Nothing this launcher records
  is, because the recorder's encoder is a realtime surface encoder, but an imported file can be
  and is refused with a reason rather than mangled.
- **Nothing is re-encoded, so nothing can be added.** No fades, no titles, no music, no speed
  change, no joining two clips. Those all mean a transcode, which means a pipeline and minutes of
  encoding on a phone. This cuts and trims, instantly and losslessly, and stops there.
- **A very short selection can hold no sound.** One AAC frame is 21ms at 48kHz, so a selection
  shorter than that has video and no audio, which MediaMuxer refuses. It is reported rather than
  risked.
- **The screenshot editor turns in quarter turns only**, and that is deliberate rather than
  missing: at a multiple of 90 degrees with an optional flip the transform is a permutation of
  the pixels, so the output is exact, there is no config promotion and no antialiasing. Free
  rotation would resample the picture and produce transparent corners on a bitmap the platform
  still flags opaque.
- **The crop is drawn, not typed.** There are no aspect ratio presets and no numeric fields; a
  crop is a rectangle dragged on the picture, with corners that can be taken hold of afterwards.
- **An edited picture is decoded whole to be saved.** The preview is downsampled, the export is
  not, because the plan's coordinates are source pixels. On a very large imported image that can
  run out of memory, which is reported rather than crashed.
- **Both editors are verified by simulation, not on hardware.** `clipsim` drives the planner,
  `clipeditsim` drives the real executor against a stubbed platform, `imageeditsim` brute forces
  the crop geometry. None of them plays an MP4 or looks at a picture: that a real MediaMuxer
  accepts these timestamps and that the resulting file plays needs a device, and there is none in
  CI.
- **Copying a whole folder to the gallery cannot be cancelled** and has no progress beyond the
  line on the row. A folder of forty minute recordings is gigabytes and the copy runs for as
  long as it runs; it skips what is already in the album, and it skips any file the volume
  does not have room for, counting it as failed.
- **The gallery copy rows count, they do not list.** The row says how many were copied, how
  many were already there and how many failed, and nothing about which. A per-file report
  would be a screen, and the place to see what arrived is the gallery.
- **An edit is a copy, so it costs the space twice**, and with the gallery copy on, three times.
  The original is never touched, which is the point, but a forty minute recording trimmed to five
  still leaves the forty on disk until it is deleted by hand.
- The folder picker for an unidentified file **only copies**. It does not create folders, rename,
  move or delete, and it cannot leave the game directory. It is the escape hatch for getting a
  file in, not a file manager, and anything beyond copying is a second feature with its own
  confirmations to design.
- It **takes the provider's name or falls back to "import"**, with separators cut and the two
  directory names refused. A document whose provider reports no name at all lands as `import`,
  and a second one as `import (2)`, which is ugly and is the honest result of being told nothing.
- Nothing about the copy is **verified by a harness**: `ContentModel.kt` is Kotlin, so the
  containment check and the sanitiser are read rather than executed. They are short and guarded
  twice for that reason, but §16.25 applies and a future change here would be better placed in
  Java where `javac` and a harness can reach it.
- **"What this asks of your phone" is prose, not a measurement.** It names mechanisms rather
  than numbers precisely so it does not go stale, but nothing in the build checks it against
  the code, and a renderer added or a feature's cost changed will not update it. It is also
  the one screen in Settings with no settings on it.
- It cannot tell you **whether your phone will be fast enough**, which is the question being
  asked. Nothing can: the answer depends on the chip, the driver, the Minecraft version, the
  mods and the renderer, and the launcher can read two of those five.
- **A field the world's own file does not carry cannot be added.** The splice replaces a tag
  that exists; adding a key means rewriting the compound around it, which is the tree rewrite
  this deliberately does not do. So a world with no `Difficulty` tag shows "Not set" and no
  picker, rather than a picker whose every option reports a failure.
- **A world is edited one field at a time, and only the fields the sheet names.** `NbtEditor`
  splices a string, a byte or an int into a path that already exists; it cannot add a key that
  is absent, delete one, or touch a list or an array. Anything else about a world is still the
  game's business. A `level.dat` that will not parse is not editable at all, which the sheet
  says rather than offering controls that would refuse.
- Every edit leaves a **`level.dat_old`** beside the world and nothing ever deletes it. That is
  Minecraft's own backup name, so the game falls back to it by itself, which is the only
  recovery route a player has when the folder cannot be browsed to. It does mean the newest
  backup is the only one, that a second edit overwrites the first, and that it displaces the
  game's own previous backup. It is a safety net for the edit you just made, not a history.
- **The seed is shown and cannot be changed.** Changing it on a world that has already
  generated chunks does nothing to the ones that exist and breaks the join between them and
  everything generated afterwards, which looks like a corrupt world rather than a new seed.
- **Turning hardcore off cannot give back what the death took.** It makes the world playable
  again in survival; the inventory, the position and whatever was in the lava are gone, because
  Minecraft wrote that before the launcher ever saw the file.
- A world **copy and a backup zip both cost the world's size again**, and a big world is
  gigabytes. Neither reports progress beyond a line on the row, and neither can be cancelled.
  The copy is removed if it fails part way; the zip is hidden from Downloads until every byte
  is there.
- **A rename changes the folder as well, and the two can still disagree.** The name in
  `level.dat` is what Minecraft shows and is what the rename succeeds or fails on; the folder
  follows unless something is already using that name, in which case it is left as it was.
- The duplicate mod check reads **declared mod ids only**. A jar whose metadata will not parse
  is not compared against anything, two versions of a mod that declares different ids in each
  are not a duplicate, and a `.disabled` copy is not counted, because it is not in the game.
- The install band reports **one file at a time and no overall total**. A mod's dependencies
  are discovered as the graph is walked, so how many files are coming is not known until the
  last one has landed; what it can say is which file, how far through it is and how many have
  finished.
- **Neither the import dialog nor the world sheet is driven by a harness.** Both are Kotlin, so
  §16.25 applies: the splice under the sheet is Java and checked, and the screens over it are
  read rather than executed.
- The loading screen is up **only until the first presented frame**, which is the title screen
  and not a world: loading a save afterwards is Minecraft's own progress bar, behind the game's
  own window, and nothing here can show anything during it.
- It shows **the last line the game logged**, which is not a stage and not a percentage. A mod
  that logs nothing for twenty seconds leaves the same line sitting there, which is honest and
  does look like a stall. There is nothing better available: no part of the JVM's startup
  reports progress to the launcher.
- **Where `GameFrames` cannot read the frame counter the screen lasts eight seconds**, which on
  a slow device is far less than the wait. That is the deliberate direction: an overlay with no
  way of knowing when the game has drawn must not be the thing standing between a player and
  their game.
- The splash table is **not translated**. New strings go in `values/strings.xml` only and the
  translations are upstream's (§15), so every language sees the English lines.
- Opening the launcher shows a splash line **only while something is actually loading**. On an
  ordinary start there is no work to wait for and the home screen is simply there, which is the
  right outcome and does mean the line is mostly seen on a first run or behind a download.
- A layout is capped at **eight pages**, which is a limit on the editor rather than on the
  mask: a row of chips picked from with a thumb runs out long before an int does.
- **Pages do not apply while a GUI is open.** Opening an inventory shows the `displayInMenu`
  set whatever page you were on, and closing it puts you back. That is deliberate and it does
  mean a page cannot have its own menu-state controls.
- **Nothing on a control says which pages it is on** while you are playing. It is answered in
  the editor, where it was set, which is the same deal slide to repeat makes.
- A page button bound to a page that was later deleted **does nothing** rather than going
  somewhere plausible. The layout report names it; silently sending somebody to the wrong page
  would be worse.
- The page a layout opens on is **always the first one** and is not saved. The page you left
  the editor on is not a thing to launch into.
- **Switching a page is not free of its history**: a toggled control that was latched on one
  page is still latched when you come back, because the key is held in the game and nothing
  here releases it. That is the same contract a hidden control already had.
- In the editor, unticking the page you are looking at **does not hide the control until you
  switch pages**. Hiding it immediately would take the selection out from under the panel that
  is editing it.
- The system keyboard switch is read **when the game process starts**, like every other in-game
  setting, so changing it takes effect at the next launch.
- With the phone keyboard chosen, a key **cannot be held down**. The launcher's own board can
  latch a cap, which is the only way F3 + G is reachable; an IME sends characters, so a layout
  that depends on latching wants the launcher's board.
- **Force close has no bindable action** and is not an oversight. Everything else the control
  center offers can go on a button; that one stays behind a confirmation at the bottom of the
  sheet.
- A recording button is **a toggle with no state on it**. The button looks the same whether or
  not a recording is running; what says so is the recording pill over the game, which is where
  it already was.
- No automated tests beyond the scripted checks in `scripts/`. There is no device in CI.
- Release builds do not run R8, so every dependency ships whole — which is why only
  `material-icons-core` is used, not the extended set.

---

## 18. Roadmap

**Now**
1. Nothing outstanding from the previous list; see the ledger in §14.

**Next**
2. **Discord Rich Presence.** Newly possible: the Social SDK 1.10 added unauthenticated Android
   presence on 3 August 2026, verified in `discord/discord-api-docs` commit `364e399`, which
   deleted the "desktop client only" line the whole ecosystem was working from. PojavLauncher
   closed this request three times as `wontfix` on that basis and those closures now predate the
   fact. The route needs no credential from a player: `Client::SetApplicationId` then
   `Client::UpdateRichPresence`, with no `Connect()`, talking to the installed Discord app.
   **Never the account token route**, whatever convenience it offers: it is a self-bot under
   Discord's own developer policy, the token bypasses 2FA and owns the whole account, and this
   codebase has already had a session token reach `latestlog.txt` next to a share button.
   `discord/PresenceCard.java` and `scripts/presencesim` are the half that could be built and
   checked here: what the card says, which version and loader it names, and the standing rule that
   **the account name is never in it**. What is missing is the SDK itself, which downloads from a
   portal this container cannot reach, and an Application ID. Also outstanding: a C++ SDK at API
   24 against `minSdk` 21, `ndkBuild` where Discord documents CMake, and ABI coverage, which is
   how the same feature crashes upstream (PojavLauncher #3409, a mod's `libdiscord-rpc.so` built
   for the wrong architecture). The card will read "Playing Amethyst X", since the title is the
   Discord application's name.
3. Bring the runtime manager and gamepad remapper onto the new components (see §17), which also
   gets their settings into the search index.
4. A layout picker worth the name — the editor's Load is still a file list. Layouts should be a
   gallery with a preview, since a control layout is a picture, not a filename.
5. Recording segmentation for multi-hour sessions.
6. Modpack search — `SearchModFragment` and its CurseForge/Modrinth flow are still the old XML,
   and the one remaining `VersionSelectorDialog` caller. Single mods now live in `ui/mods/`;
   modpacks, which build a whole profile, have not moved.

**Later**
6. Shared-element transition from the version card into the version sheet.
7. Playback in the recordings gallery itself, so a clip can be watched without opening the
   editor. The editor plays one now; the gallery still hands a row to an external player.
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
  the bundled headless Chromium. **Find it rather than hardcoding it**: the path carries a
  build number that moves under you (it was `chromium_headless_shell-1194/chrome-linux/`
  and is now `chromium_headless_shell-1243/chrome-headless-shell-linux64/`), so
  `find /opt/pw-browsers -name 'chrome-headless-shell' -o -name headless_shell` first.
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
- **Run `bash scripts/nbtsim/run.sh`** if `NbtReader` or `NbtEditor` changed — it writes a
  `level.dat` shaped like a real one, nested compounds and arrays and all, and reads it back
  through the shipped source. Binary format parsing is wrong in ways reading cannot catch.
  For the editor it asserts the property the splice exists for, which is that **only the edited
  payload differs**, by comparing the common prefix and suffix of the two files rather than by
  checking the fields the harness happens to look at: a writer that round-tripped those and
  quietly dropped a capped list would pass the weaker check and destroy a world. It also drives
  `edit()` on a real file for the backup, the verify-before-replace step, a change that returns
  null and a change that corrupts, and it keeps an uncompressed file uncompressed.
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
- **Run `sh scripts/modrinthsim/run.sh`** if the Modrinth client or the installer changed. It
  compiles the shipped `ModrinthMods` and `ModInstall` against stubs at source 8 and drives them
  with fixtures, which is the only check available: the API is not reachable from the build
  container. Its download stub **drives the progress monitor** rather than returning silently,
  because the question worth asking is how many reports reach the caller and a stub that
  reports nothing answers "none" however the throttle is written (16.16). Two assertions
  matter: the reports are far fewer than the raw callbacks, and they still **end at the file's
  real size**, which is the half that found the clip editor's bar stopping at 99% (16.30).
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
- **Run `sh scripts/logsim/run.sh`** if the log parser changed. It compiles the shipped
  `LogParser` at source 8 and drives the real class, which is the point: the first draft of this
  check re-implemented the algorithm in Python and passed while six of eight mutations to the
  shipped code went unnoticed. It reads the log4j configs out of assets for the format, and the
  mutations it is known to catch are listed at the top of `run.sh` so a weakening of it shows.
- **Run `python3 scripts/check_strings.py`** if `values/strings.xml` changed, and especially if
  anything wrote it through a script. A bare apostrophe is an aapt compile error whose message
  names no string and no line (§16.32), so this is the difference between finding six of them
  in a second and finding them by counting hashes in a CI log. It warns rather than failing on
  non-positional format conversions, because thirteen inherited strings have them.
- **Run `python3 scripts/check_settings_calls.py`** if anything in `ui/settings/SettingsComponents.kt`
  changed its parameters, **or if a full screen composable gained one**. It reproduces Kotlin's
  "No value passed for parameter" against every call site of every shared row, which is the one
  way an optional parameter added in the middle of one of them fails: a trailing lambda still
  binds to the last parameter, and a callback passed positionally does not.
  It also covers the screens in `SCREENS`, which have a single caller each and so look like the
  safe case. They are not: a parameter added to `ContentScreen` whose argument never reached
  that one call site failed exactly as a shifted position does, three minutes into CI. The
  screens are named rather than taken wholesale from their files, because several of them
  declare a private `Thumbnail` or `Spinner` and merging declarations by name across files
  would check a call against the wrong signature.
  **The skip for a declaring file is per name, not per file.** Adding `SettingsScreen.kt` as a
  declaration source and skipping the whole file took the largest consumer of the shared rows
  out of the scan: the count fell from 183 to 43 and it went on printing ok. The count is
  printed for that reason, and it is worth reading.
- **Run `python3 scripts/check_keyboard.py`** if the on-screen keyboard changed — it parses the cap
  tables out of `GameKeyboard.kt` and checks the row weights, the keycode range, and that every key
  the old dialog could send is still reachable. A board is also worth *looking* at: the same parser
  can emit HTML and be screenshotted, which is how a row that does not line up gets caught.
- **Run `sh scripts/repeatsim/run.sh`** if slide to repeat changed. It lifts the four statics out
  of the shipped `ControlData` and drives them, and the check worth keeping is the one that
  binary-searches the arming radius around a whole circle: an axis-wise threshold is right along
  both axes and wrong only between them, which is exactly where a handful of test points are not.
- **Run `sh scripts/resizesim/run.sh`** if the resize grip's floor, step or snapping changed. It
  pulls `resolveSize` and both constants verbatim out of the shipped file, so the harness cannot
  drift from the code, and sweeps **off-grid floors** as well as the shipped pair: with the
  shipped constants alone, reversing the snap and the floor is invisible, which is exactly the
  mutation that got through the first draft.
- **Run `sh scripts/viewportsim/run.sh`** if the game viewport geometry changed. It compiles the
  shipped `GameViewport` and sweeps every percent and anchor across six real panels. The check
  that matters most is that **100% is byte-for-byte the full screen**, because every player who
  never opens the setting depends on that and a rounding error there would ship to all of them.
- **Run `python3 scripts/check_joystick_directions.py`** if auto-walk's tap-to-direction geometry
  changed. It checks the eight compass points, the dead zone's boundary, and a tolerance band
  around each cardinal rather than only its exact centre, because a formula that drops the
  half-bucket centring offset still gets every point placed exactly on a cardinal right by luck
  and only shows itself on the points 20 degrees either side. It also uses `math.fmod` rather
  than Python's own `%`, because Java's double remainder keeps the sign of the dividend and
  Python's floored one does not; the first draft used `%`, which silently repaired an unwrapped
  negative angle into the right answer and could not tell a working wrap from a removed one.
- **Run `sh scripts/combosim/run.sh` and `sh scripts/chordsim/run.sh`** if anything about how a
  control button sends several keys changed, or if `critical_send_key` or `pojavPumpEvents` did.
  combosim drives the shipped ordering; chordsim lifts the three native functions **verbatim** out
  of `input_bridge_v3.c`, compiles them against a stub environment and asks the one question
  Minecraft asks, which is whether a poll of Shift still reads pressed when F3's release is
  dispatched. Its most important mutation is the one-line revert that restores the eager
  `keyDownBuffer` write, because that mutation *is* the shipped bug and a check that cannot see it
  would have passed on the broken build. Also run
  `python3 scripts/combosim/check_agreement.py`, which compares `KeyCombo.isModifier` against
  `CallbackBridge.setModifiers` as declarations rather than re-implementing either.
- **Run `sh scripts/presencesim/run.sh`** if the Discord presence card changed. It compiles the
  shipped `PresenceCard` at source 8 and checks what it would publish. Two failures matter and
  both are permanent once seen by a stranger: a field naming the wrong Minecraft version, and a
  field naming the player. The identity check is written as a property of the output rather than
  trusted to the call sites, because a username reaching a public profile cannot be taken back.
- **Run `sh scripts/modmetasim/run.sh`** if the mod metadata parser, the version grammars or the
  dependency graph changed. It compiles the shipped `ModRequirements`, `VersionPredicate` and
  `ModGraph` at source 8 and drives them with **real jars written by the harness**, whose metadata
  is copied from what mods actually ship rather than from what the parser expects (§16.20). Two
  assertions matter more than the rest: that a folder of real, mutually consistent mods produces
  **exactly zero** warnings, because over-flagging is the failure mode and only a positive
  assertion on a good set can see it; and that every shape the parser does not model comes out
  UNKNOWN rather than CONFLICTS. One input is guarded in three places and no single removal is
  observable, which `run.sh` states rather than hides.
- **Run `sh scripts/memsim/run.sh`** if the heap ceiling, the default allocation or the pre-launch
  memory check changed. It compiles the shipped `HeapAdvice` and sweeps every device size against
  every allocation the slider can produce. The assertion that matters most is that **the
  launcher's own default never warns**, which is memsim's counterpart to viewportsim's
  "100% is byte-for-byte the full screen": if the check disagreed with the launcher's own choice,
  every fresh install would be warned on its first launch about a value it never made. Two
  mutations are known **not** to be caught and are listed in `run.sh` with the reason, because
  today's constants and the clamps that guard them hide each other's removal, which is the
  resizesim situation and is covered by sweeping every megabyte rather than the realistic sizes.
- **Run `sh scripts/sodiumsim/run.sh`** if the Sodium verdict, the renderer ids, the GPU families
  or the detection list changed. It compiles the shipped `SodiumSupport` at source 8 and sweeps
  every renderer against every GPU family, GL ES version, Vulkan flag and Turnip flag. Three
  checks matter more than the rest. `maliOnZinkStillWarns` fails on the one-line revert that drops
  the GPU dimension, which *is* the shipped bug, so it has chordsim's property of being able to
  see the thing it was written for. `aReportedGlVersionCannotDecideZink` is written as an
  invariance property rather than a case, because `MESA_GL_VERSION_OVERRIDE` makes a reported
  version worthless and the mistake to prevent is somebody wiring one in later.
  `familyAgreesWithTheTurnipPredicate` reads the real `GLInfoUtils.isAdreno()` source rather than
  restating its rule, so the two cannot drift into promising a Turnip driver that will not load.
  The detection checks still catch **over-matching**, with a list of popular unrelated mods a
  widened entry would hit.
- **Run `sh scripts/gpusim/run.sh`** if `GpuProfile` changed. It stubs the two framework types and
  four project classes it touches, compiles the **real** shipped source at source 8, and drives
  it. That compile is the point as much as the checks are: `GpuProfile` is new Java calling six
  signatures and CI is otherwise the only compiler (§16.6). Every stub can be made to refuse, one
  at a time and all at once, because the single thing this class must never do is throw on the
  launch path (§16.15), and a stub that can only succeed tests nothing about the guard around it
  (§16.16). One mutation is known **not** to be caught and `run.sh` says why.
- **Run `python3 scripts/check_renderer_env.py`** if a renderer branch, the launch env order or
  the MobileGlues settings changed. The invariant is per-branch and not per-renderer: every
  `opengles*` branch in `setJavaEnvironment` must set `LIBGL_ES` itself, because the generic
  derivation below is guarded on `containsKey` and `OPEN_GL_VERSION` has already filled it with
  "1" or "2". A renderer added without the line inherits a number chosen for something else,
  which is the MobileGlues bug. It also pins that `setJavaEnvironment` runs before
  `loadGraphicsLibrary`, since that ordering is what makes one line enough, and that the FSR
  picker is gone from all five places it lived: the value array was inverted (0,4,3,2,1), so a
  half removal mis-maps quality levels rather than failing.
- **Run `python3 scripts/check_log_formats.py`** if anything under `jni/` logs. It counts printf
  conversions against arguments in every LOG call, which is the class of bug `sdl_hook.c` shipped:
  two `%p` and one argument, reading an uninitialised vararg slot at every launch. `-Wformat`
  would have caught it, and a warning in an ndkBuild log nobody reads is a warning nobody reads.
  Deliberately an arity counter and not a type checker, and it prints what it skipped so the
  coverage number does not quietly shrink.
- **Run `python3 scripts/check_ported_fixes.py`** if the manifest, the Game Mode config or
  `TouchControllerInputView` changed. Both guards are structural rather than executed, which is
  stated in the file: one is a manifest declaration only the platform reads, the other is inside
  an Android view class. The one that matters is placement, because upstream's own copy of the
  Game Mode declaration sits under `<manifest>` where `<meta-data>` is not valid and the platform
  does not look, so "we match upstream" is the wrong thing to assert.
- **Run `sh scripts/clipsim/run.sh`** if `ClipPlan` changed. It compiles the shipped planner at
  source 8 and drives it: which spans survive a trim and a set of cuts, and what every surviving
  sample's new timestamp is. The one that matters most is `aCutNeverRemovesLessThanAsked`, swept
  rather than sampled, because flipping the snap direction is a one word edit and it is the edit
  somebody will make. The editor's own cutting model is driven there too:
  `theTimelineDividesIntoSelectableSpans` for the spans a split produces, and
  `deletionsFollowTheirSpanWhenTheTimelineChanges` for `retime`, which asserts the invariant
  the screen rests on (every cut is one of the current spans) rather than any particular
  mapping. Break that and the plan removes material the screen says is being kept, which is
  invisible in both halves read alone.
- **Run `sh scripts/clipeditsim/run.sh`** if `ClipEditor` changed. It stubs `MediaExtractor` and
  `MediaMuxer` and drives the **real** shipped executor, which is half the point: that file is
  ~350 lines of new Java calling twenty Android signatures and CI is otherwise the only compiler
  (§16.6). The stubs model the platform's refusals rather than only its successes (§16.16), and
  one of them throws `WouldHaveHung` where the real muxer **blocks forever**, so a guard that
  stopped working shows up as a failed check instead of a frozen phone. It also counts the
  progress callbacks against the samples written, because reporting per sample is a main thread
  flood that looks like ordinary code at both ends (§16.30), and asserts the fraction reaches 1,
  which is the assertion that found it stopping short.
- **Run `sh scripts/imageeditsim/run.sh`** if the screenshot crop or orientation maths changed.
  It brute forces every pixel of several images through `mapPoint`/`unmapPoint` and back, which
  is the only honest check for a transform whose failures (a mirrored limb, a crop from the wrong
  corner) are invisible in source and obvious in a picture.
- **Run `sh scripts/splashsim/run.sh`** if the splash ordering or either copy table changed. It
  compiles the shipped `SplashPicker` at source 8 and drives it, and the check that matters is
  `neverTwiceInARow`, because a bag is correct by construction and the only repeat possible is
  at the seam between two of them. `theFirstLineVariesByLaunch` is its companion: handing the
  bag out before the first shuffle is still a permutation and still never repeats, so every
  other check passes while every cold start opens on the same line. It also bounds the tables,
  since a splash that wraps is not a splash.
- **Run `sh scripts/pagesim/run.sh`** if page membership, page switching or page deletion
  changed. It compiles the shipped `ControlPages` at source 8 and drives it, and every check
  there is a form of one question: can an arrangement of pages take a control off the screen
  that the player did not take off themselves. Two carry most of it.
  `anOldLayoutIsUntouched` sweeps mask 0, which is what every layout ever saved deserialises
  to, across every page count and page. `theLastPageCannotBeUnticked` covers the trap built
  into a scheme where zero means "every page", since a control unticked everywhere would
  otherwise canonicalise straight back onto all of them. `anOrphanLandsOnOnePageAndNotAllOfThem`
  is there because the obvious assertion, that the control is still visible somewhere, passes
  on the broken version too: without the guard the empty mask falls through as ALL_PAGES, so
  the control is drawn everywhere rather than nowhere and only counting the pages tells them
  apart.
- **Run `python3 scripts/check_compose_optin.py`** whenever new Compose is written, and
  always after a build fails on an opt-in. It reproduces Kotlin's "this declaration needs
  opt-in" for the experimental APIs this project uses, which is otherwise a three minute CI
  round trip for a missing annotation (§16.37). Adding the API that just failed to `MARKERS` is
  half the fix; the count it prints is the other half, because that number is what shows the
  check has stopped looking at something.
- **Run `python3 scripts/check_specials.py`** if a special button was added, which is the one
  change in this codebase that can corrupt every layout anybody has saved without producing an
  error anywhere. It asserts `getSpecialButtons()` is in `-(index + 1)` order, that nothing is
  declared and left out of the array, and that nothing is listed twice.
- **Run `python3 scripts/check_kotlin_accessors.py`** after editing a Kotlin class full of
  `var x by mutableStateOf(...)`, which here means the editor state holders. It asserts a bare
  `set` sits under a `var`, which is the damage an anchored edit does when it consumes a
  declaration and leaves the accessor beneath it behind (§16.38).
- Read the whole diff.

CI builds Debug **before** Release, so a missing signing key never hides a compile error. Release
steps are skipped when `GPLAY_KEYSTORE_PASSWORD` is unset.
