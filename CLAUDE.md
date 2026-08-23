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
- **Run `sh scripts/logsim/run.sh`** if the log parser changed. It compiles the shipped
  `LogParser` at source 8 and drives the real class, which is the point: the first draft of this
  check re-implemented the algorithm in Python and passed while six of eight mutations to the
  shipped code went unnoticed. It reads the log4j configs out of assets for the format, and the
  mutations it is known to catch are listed at the top of `run.sh` so a weakening of it shows.
- **Run `python3 scripts/check_settings_calls.py`** if anything in `ui/settings/SettingsComponents.kt`
  changed its parameters. It reproduces Kotlin's "No value passed for parameter" against every
  call site of every shared row, which is the one way an optional parameter added in the middle
  of one of them fails: a trailing lambda still binds to the last parameter, and a callback
  passed positionally does not.
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
- Read the whole diff.

CI builds Debug **before** Release, so a missing signing key never hides a compile error. Release
steps are skipped when `GPLAY_KEYSTORE_PASSWORD` is unset.
