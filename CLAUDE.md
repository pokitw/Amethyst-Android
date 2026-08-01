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
| Launcher home | **Compose** | `ui/home/`, hosted by `MainMenuFragment.kt` |
| Recordings gallery | **Compose** | `ui/recordings/`, `RecordingsActivity.kt` |
| Version picker | **Compose** | `ModalBottomSheet` in `ui/home/HomeSheets.kt` |
| Account picker | **Compose** | Same file |
| Settings | **Compose** | `ui/settings/`, hosted by `SettingsFragment.kt` |
| Runtime manager · gamepad remapper · MobileGlues tuning | XML, stays for now | Reached from the new Settings; see §17 |
| In-game control center | **Compose** | `ui/game/`, hosted by `MainActivity` **and** `CustomControlsActivity` |
| Control layout editor menu | **Compose** | The control center in editor mode; the buttons it edits stay custom views |
| Profile editor | XML | **Next.** Not yet designed |
| Auth / login flow | XML | Not yet designed |
| Control buttons themselves | XML custom views | Deep custom view work; skinned rather than rewritten, see §14 |
| Game surface | XML, stays | See §12.4 |

The launcher's chrome (`activity_pojav_launcher.xml`: account bar, settings button, progress bar)
is **hidden while the home screen or Settings is showing**, because both draw their own header.
Settings keeps the progress bar — a download started elsewhere has nowhere else to report from
while it is open — so `setChromeHidden` takes the two decisions separately.
`ProgressLayout.setSuppressed(boolean)` exists for this.

---

## 14. Current focus

### Settings — done

It was 48 preferences across 8 screens grouped by where the code lived: "Use system Vulkan driver"
under **Miscellaneous**, the renderer not in Settings *at all*, memory third in a screen called
"Java Tweaks", and a category named "Experimental fuckury".

It is now five destinations grouped by intent — **Performance, Controls, Recording, Game files,
About** — each carrying a live summary of its own state, so "MobileGlues · 4 GB · 100%" answers the
common question without opening anything. The renderer moved in, tagged `THIS PROFILE`; it is still
stored per profile and must stay that way. The nine touch-once graphics settings sit behind an
`AdvancedSection` expander that names how many are hiding.

`ui/settings/` is four files on purpose:

- **`SettingsStore.kt`** — typed reads and writes. Every write goes back through
  `LauncherPreferences.loadPreferences`, because most preferences are mirrored into statics the
  launcher reads rather than being consulted at the point of use. It also carries a `revision`
  counter that reads touch, since SharedPreferences is not snapshot state and cannot notify
  Compose on its own.
- **`SettingsComponents.kt`** — `SectionLabel`, `SettingsCard`, `SwitchRow`, `SliderRow`,
  `ChoiceRow`, `TextRow`, `NavRow`, `InfoRow`, `SearchEntry`, `AdvancedSection`, and
  `SettingsHighlight`.
- **`SettingsIndex.kt`** — the flat table of contents search reads. One `SettingEntry` per
  setting: title, description, which of the five screens it lives on, its section, and the words
  someone would type who does not know what it is called ("fps", "lag", "ram"). **Adding a setting
  to a screen means adding a line here**; the two are checked against each other by eye, which is
  the same contract the screens already had with the preference XML they replaced.
- **`SettingsScreen.kt`** — the five screens plus search, written out as the lists of settings they
  are rather than as a data-driven spec, so they can be diffed against the preference XML they
  replaced.

### Settings — search, and the top of the screen

Two things were left. Search was on the roadmap and is the thing fifty settings across five
screens most needs: you know the word, you do not know which of the five owns it. And the top of
Settings was still the launcher's *old* chrome — the `mcAccountSpinner` bar from
`activity_pojav_launcher.xml`, wearing a different background from everything under it, with the
settings button floating over its right-hand end. It said one thing, the username, and it was the
first thing anyone opening Settings saw.

- **The header is now Settings' own.** Skin face, username, account type, and the version that
  account is about to launch — the one wash on the screen, so the top has somewhere for the eye to
  land. The launcher chrome is hidden here the way it already was on home.
- **Search results carry their address.** Each result names its screen and section
  ("Performance · MEMORY AND RUNTIME"), so the answer is readable before the tap.
- **Tapping a result finishes the job.** It opens that screen, scrolls to the row and washes it in
  accent for two seconds. Rows are matched by their **title text**, not by an added key — every row
  already has a title, and threading an identifier through fifty call sites would have bought
  nothing. `AdvancedSection` opens itself when the row it hides is the target, and **latches**: the
  wash fades, and a section that closed itself again would take the answer with it.
- **Detail screens keep their title.** The large title fades into a compact one in the bar as you
  scroll, because twenty near-identical rows give you nothing to tell you where you are.
- `AdvancedSection` now takes the list of titles it hides rather than a hand-written count, so the
  count cannot drift from the list again — it had already drifted, saying 9 for 10 settings.

### In-game control center — done

It was a 200dp right-edge `DrawerLayout` holding a `ListView` of
`android.R.layout.simple_list_item_1`: plain text rows, no icons, no hierarchy, force close
**first** and recording **last**, its whole state carried by a label flipping between "Start" and
"Stop".

It is now `ui/game/ControlCenter.kt`, a sheet from the bottom, because in landscape that is where
thumbs are and the right edge is not. Recording is a card at the top — idle it names the
resolution, frame rate and audio a recording would use; live it drops the gradient for a timer,
the size against the cap, and a stop button in `RecordingRed`. A pill beside the pull tab carries
the same timer without opening anything, and cannot appear in the footage because the recorder
captures the GL surface rather than the screen. Force close is last, quiet, in the error colour.

`ControlCenterHost` is the Java-facing seam: `open`, `close`, `isOpen`, `setEditorMode`,
`setRecordingSummary`, `onRecordingStarted`, `onRecordingStopped`, `release`. `MainActivity`
implements `ControlCenterCallbacks`; every action it exposes already existed, only the way in
changed. The control-layout editor keeps its own mode — six tiles under a banner carrying Share
and Exit — because the drawer used to swap its adapter for exactly that.

### On-screen controls — the Pocket Edition pass

The controls are the part of this launcher that is played rather than looked at, and they were the
part that had never been designed: grey-black rounded rectangles carrying wrapped text labels
("Third\nPerson"), laid out by a default file whose position expressions had been generated by a
tool and ran to four hundred characters each.

Three changes, in order of how much they matter:

1. **`ControlSkin`** — one place that decides how a control is drawn, consulted at draw time and
   never written into the layout. Pocket style is a light translucent fill, a dark keyline so the
   button still reads against snow, and a corner radius between a square and a circle. It is a
   preference (`controlPocketSkin`, on by default), so a decade of shared layouts get the look
   without being touched and turning it off gives the author's colours straight back.
2. **`ControlGlyphs`** — the icon a button shows instead of its name, matched on **the key it
   sends**, not on what it is called. That is what lets an old layout pick up icons with no
   migration and no new field in the format. A button bound to two keys keeps its name: no icon
   honestly means "sneak and jump", and a wrong icon is worse than a word. Also a preference
   (`controlGlyphs`).
3. **A new `assets/default.json`** — a Pocket Edition shape. D-pad bottom left, swipeable so a
   thumb can slide from forward into a turn without lifting; jump owning the bottom-right corner
   at 68dp because it is pressed more than everything else there put together; sneak, sprint, use,
   attack and inventory around it; the rest along the top. Its positions are written in the simple
   vocabulary (`${screen_width}`, `${width}`, `px(n) / 100.0 * ${preferred_scale}`) rather than
   generated, so they can be read — and checked. See §19.

`CustomControlsActivity` hosts the same control center in editor mode, so the editor opened from
Settings and the editor opened mid-game are one screen with two ways in rather than two screens
doing the same seven things.

**Existing users keep their layout.** `AsyncAssetManager` writes a changed default asset to
`controlmap/new_default.json` and leaves `default.json` alone, which is exactly right: the skin and
the icons reach them anyway, and nobody's arrangement is thrown away. New installs get the new one.

---

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
9. **A private Kotlin property still emits its JVM accessors.** `private var editorMode` and a
   public `fun setEditorMode(Boolean)` on the same class are a "platform declaration clash". When
   a Kotlin class is called from Java, name its state and its methods apart.
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
15. **A generated layout file cannot be reviewed.** The old `default.json` had 400-character
    position expressions with `10^-13` coefficients in them; nobody could tell whether a button
    was in the right place without running it. The replacement is written in the simple
    vocabulary and evaluated by a script against a grid of screen sizes and button scales, which
    is a real check rather than a hope.

---

## 17. Known limitations

- `vulkan_zink` cannot be recorded — it renders through OSMesa, which has no EGL surface to hook.
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
- No automated tests. There is no test harness in the project and no device in CI.
- Release builds do not run R8, so every dependency ships whole — which is why only
  `material-icons-core` is used, not the extended set.

---

## 18. Roadmap

**Now**
1. Profile editor — currently a long form, and the last screen on the launch path that has not
   been designed. It also owns the renderer, which Settings now edits a copy of.

**Next**
2. Auth/login flow — the first thing a new user sees.
3. Bring the runtime manager and gamepad remapper onto the new components (see §17), which also
   gets their settings into the search index.
4. The control editor's *editing* surfaces: `EditControlSideDialog` is still a side panel of raw
   fields (stroke width in dp, corner radius in per cent) and `ActionRow` is still a strip of
   bare icons. The menu around them is designed now; what you actually touch to edit a button is
   not.
5. A layout picker worth the name — the editor's Load is still a file list. Layouts should be a
   gallery with a preview, since a control layout is a picture, not a filename.
6. Recording segmentation for multi-hour sessions.

**Later**
7. Shared-element transition from the version card into the version sheet.
8. Recordings: in-app playback and trimming rather than handing off to an external player.
9. Retire `activity_pojav_launcher.xml` chrome entirely once every fragment is Compose.
10. A joystick variant of the Pocket default, offered as a choice the way Bedrock offers it,
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
- Read the whole diff.

CI builds Debug **before** Release, so a missing signing key never hides a compile error. Release
steps are skipped when `GPLAY_KEYSTORE_PASSWORD` is unset.
