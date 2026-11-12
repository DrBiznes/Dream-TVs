# Dream TVs — Project Plan

> A survival-friendly addon for [Dream Displays](https://github.com/arnodoelinger/dreamdisplays) that adds a single
> craftable **TV block**. Place TV blocks next to each other in a flat rectangle and they merge into one bigger screen.
> This replaces the `/display create` wand-and-command flow.

| | |
|---|---|
| **Mod name** | Dream TVs |
| **Mod ID** | `dreamtvs` |
| **Loaders** | Fabric, NeoForge (no Paper, Forge, Quilt-specific or proxy support) |
| **Requires** | Dream Displays (on client **and** server), Fabric API on Fabric |
| **First target** | Minecraft **1.21.1** (Fabric + NeoForge); 1.21.11 and 26.x later |
| **Language** | Java 21 (see [Why Java](#why-java-not-kotlin)) |
| **Reference** | Dream Displays `1.10.9` (commit `47e44d1`, 2026-11-08) |

---

## 1. Goals and non-goals

### Goals
- One block: `dreamtvs:tv`. You get it from a normal crafting recipe, mine it with a pickaxe, and it drops itself.
- **Multiblock screens.** Any filled rectangle of same-facing TV blocks in one plane becomes a single Dream Displays
  display that fits the rectangle exactly.
- **Live resizing.** Adding a row grows the screen and breaking a block shrinks or splits it, and the video link and
  settings are kept whenever possible.
- Players can set it up without commands, permissions or operator help.
- Works in singleplayer (integrated server) and on dedicated Fabric/NeoForge servers.

### Non-goals (v1)
- Any changes to video playback, the display menu or networking. Dream Displays already handles all of that.
- Floor or ceiling screens (`UP`/`DOWN` facing). Possible later, see [Stretch goals](#9-stretch-goals).
- Paper/Bukkit support. Remote controls, redstone control or channel presets.
- Removing `/display create`. Both flows will exist side by side.

---

## 2. What we learned from the Dream Displays repo

| Area | Finding | Impact on us |
|---|---|---|
| **Addon API** | `@Addon(id, side = SERVER)` + `DreamDisplaysServer.register(addon)`; optional `AddonLifecycle.onEnable/onDisable`. Addons are marked *experimental until 1.11.0*. | We register a server-side addon. Pin a DD version range and expect API churn. |
| **Server API runs on mod loaders** | `VanillaServerApi` (in `platform/server/.../api/VanillaServerApi.kt`) backs `DreamDisplaysServer` on Fabric/NeoForge and starts in `VanillaBootstrap`. | We can use it everywhere we ship. Use `DreamDisplaysServer.onReady { }` because start order isn't guaranteed. |
| **Creating displays** | `DreamDisplaysServer.displays.create(owner, world, x1,y1,z1, x2,y2,z2, facing)` returns `ServerDisplay?` (`null` if it overlaps or the world is unknown). It **skips** the base-material (black concrete) structure check: *"the caller is responsible for the blocks being a suitable wall."* | This is our main hook. The TV blocks themselves are the "wall". |
| **Resizing** | `ServerDisplay.moveTo(x1..z2, facing)` relocates and resizes in place, keeping ID, URL and settings. Returns `REFUSED` on overlap. | Grow and shrink with `moveTo`, so the link isn't lost. |
| **Removing** | `ServerDisplay.delete()` (goes through the `DisplayVeto.Delete` check). | Used when a screen is fully broken or splits. |
| **Addon data** | `ServerDisplay.customData` (`MutableDisplayCustomData`) is saved with the display and synced to clients. | Tag our displays: `dreamtvs:managed`, plus corners and facing (`ServerDisplay` does **not** expose facing or min/max corners). |
| **Events / vetoes** | `events.on<ServerDisplayEvent.Created/Removed/Moved/...>`, `vetoes.on<DisplayVeto.Create/Delete/SetUrl>`. | Detect `/display delete` on our screens. Optionally veto `/display` resizing of TV-managed displays. |
| **World key** | Vanilla displays use `worldKey` = dimension id (e.g. `minecraft:overworld`). | Pass `level.dimension().location().toString()`. |
| **Block protection** ⚠️ | `VanillaProtectionListener.handleBreak` **cancels every block break inside a display's box** (plus explosions and pistons via mixins). | **Biggest risk.** Without handling, players can't break a formed TV. See [§5.4](#54-breaking-blocks-vs-dd-protection). |
| **Limits** | DD config defaults: `min 1×1`, `max 32×24`, `max_displays_per_player`. The API `create()` doesn't enforce the per-player limit. | Clamp to our own config (default 32×24) and enforce our own per-player cap. |
| **Interaction** | The DD client raycasts to displays to open its menu or focus a display (`MinecraftDisplayInteractionService`). | Our block shouldn't consume clicks on the front face (`InteractionResult.PASS`). Needs a check in the spike. |
| **Packaging** | DD shades Kotlin, kotlinx, etc. and renames them to `com.dreamdisplays.libs.*`. The API (`dreamdisplays-api`) is published from Gradle but only jars go to GitHub Releases, Modrinth and CurseForge, with **no public Maven**. | Compile against the API classes from the **released mod jar** (Kotlin-free calls only), or ask upstream to publish the API. |
| **Versions** | DD builds with Stonecutter for 1.18.2 → 26.3. NeoForge is only offered for 1.21.1+. | Our version range: **1.21.1** and **1.21.11** first, 26.x later. |

### Why Java, not Kotlin
The shipped DD jar renames its bundled Kotlin to `com.dreamdisplays.libs.kotlin`. A Kotlin addon compiled against the
API source would reference `kotlin.*` types that DD doesn't provide, so we'd also need Fabric Language Kotlin or Kotlin
for Forge. Plain Java that calls only the Java-facing API (`@JvmStatic` objects, `Consumer`, `Runnable`, `UUID`,
`String`, enums) avoids the whole problem. Skip Kotlin-only API helpers: the `inline reified` `on<T>{}` extensions and
default parameters such as `setUrl(url, lang = "")`. Call `on(Class, Consumer)` and `setUrl(url, "")` instead.

---

## 3. Player experience

1. Craft TV blocks (recipe in [§6](#6-content)).
2. Place one. It faces you, like a furnace, and a single block already forms a **1×1 screen**.
3. Keep placing TVs on the same wall plane with the same facing. When the group is a **filled rectangle**, it becomes
   one screen of that size. An action-bar message confirms it: *"Screen formed: 4×3"*.
4. Look at the screen and use Dream Displays as normal (menu, paste link, sync modes, etc.).
5. Add a row and the screen grows with the same video still playing. Break a block and the screen shrinks, splits or
   turns off.
6. If the group isn't a rectangle (L-shape, hole, mixed facing), the blocks show an "unformed" static face and the
   action bar explains why.

---

## 4. Architecture

```
dreamtvs/
├── common/        # Loader-agnostic: block, multiblock solver, DD bridge, config model
│   ├── TvBlock                     # Block + FACING + connection properties
│   ├── ScreenSolver               # PURE: Set<Cell2D> → rectangles / verdict (unit-tested)
│   ├── ScreenScanner              # World → coplanar same-facing TV cells (flood fill, capped)
│   ├── ScreenReconciler           # Desired screens vs existing DD displays → create/moveTo/delete
│   ├── DreamDisplaysBridge        # Only class that touches com.dreamdisplays.api.*
│   └── DreamTvsAddon              # @Addon(id="dreamtvs", side=SERVER), event/veto subscriptions
├── fabric/        # Registration, events (PlayerBlockBreakEvents, etc.), fabric.mod.json
└── neoforge/      # DeferredRegister, BlockEvent.BreakEvent @HIGHEST, neoforge.mods.toml
```

- **Build:** a MultiLoader-style Gradle layout (common + fabric + neoforge), using Fabric Loom and NeoForge
  ModDevGradle. DD uses the same tools.
- **DD dependency:** `compileOnly` on the DD API. Runtime dependency on the DD mod jar in dev runs (`modLocalRuntime` /
  `localRuntime`), taken from a pinned GitHub release.
- **Required on both sides.** Fabric `environment: "*"`. NeoForge `side = "BOTH"`, plus a `dreamdisplays` dependency
  with `ordering = "AFTER"`.
- **The world is the source of truth.** Screens are always *derived* from the blocks in the world. Every change runs
  the same idempotent `reconcile(group)`, so bugs and crashes heal on the next block update.

---

## 5. Core design

### 5.1 Block
- `dreamtvs:tv`, `HorizontalDirectionalBlock`, `FACING` set from the player's look direction (front faces the player).
- Blockstate connection flags `UP/DOWN/LEFT/RIGHT` (relative to facing) for connected-bezel models, plus `FORMED`
  (front shows black when formed, "no signal" static when not).
- **No BlockEntity in v1.** Display identity lives in DD's `customData`, which avoids sync and persistence work.
- `PushReaction.BLOCK` (pistons can't split screens). Hardness about 3.5, needs a pickaxe, drops itself.

### 5.2 Multiblock solving (`ScreenSolver`, pure)
- Input: the set of 2D cells (u, v) in a plane for one facing. Output: either `Rectangle(minU, minV, w, h)` or
  `Invalid(reason)`.
- v1 rule: **the connected group must be a completely filled axis-aligned rectangle**, within `1 ≤ w ≤ maxWidth` and
  `1 ≤ h ≤ maxHeight`. Otherwise it's `Invalid` (`NOT_RECTANGLE`, `TOO_WIDE`, `TOO_TALL`).
- Flood fill uses 4-neighbourhood, same facing, same plane. Cap the scan at `maxWidth × maxHeight + 1` cells so huge
  walls stay cheap.
- Pure Java, no Minecraft imports, so it's fully unit-testable.

### 5.3 Reconciling with Dream Displays (`ScreenReconciler`)
Triggered on place, break, and neighbor-shape change (debounced to **end of tick**, so `/fill` and bulk changes cost one
pass per group):

1. Scan the group and solve it.
2. Find the existing DD displays tagged `dreamtvs:managed` whose stored corners intersect this group, using a small
   server-side index keyed by world and chunk that's rebuilt from `customData` on start.
3. Decide:
   - **Valid rectangle, no display:** `create(owner, world, corners, facing)` and tag it in `customData`.
   - **Valid rectangle, one display:** `moveTo(corners, facing)` if the corners changed. The URL survives.
   - **Valid rectangle, several displays (two screens merged):** keep the **largest** (tie-break: oldest), delete the
     others, then `moveTo`.
   - **Invalid or empty:** `delete()` the managed displays and set `FORMED=false` on the blocks.
4. Update `FORMED` and the connection blockstates. Send the action-bar message to the acting player.

**Splitting:** breaking a middle block can leave two valid rectangles. The larger one keeps the original display
(`moveTo`) and the other gets a fresh display with no link.

**Owner:** the player who placed the block that caused the create. On merge, the owner of the kept display stays owner.
Who may grow, shrink or merge a screen is covered by the permission rules in [§5.7](#57-permissions).

### 5.4 Breaking blocks vs DD protection ⚠️
DD cancels breaks inside display boxes on Fabric/NeoForge. Options, in order of preference:

1. **Upstream API (best).** Propose a small DD API addition, for example `ServerDisplay.protection = NONE`, or a
   `DisplayVeto.Protect`/`isProtected` hook, so addon-managed displays opt out of block protection. It's a small
   change; open an issue or PR early.
2. **Run first and free the area (fallback).** Hook the break *before* DD:
   - NeoForge: `BlockEvent.BreakEvent` at `EventPriority.HIGHEST`.
   - Fabric: `PlayerBlockBreakEvents.BEFORE`. Ordering isn't guaranteed, so we may need a mixin into
     `ServerPlayerGameMode#destroyBlock` that runs ahead of DD's hook.

   When the target is a TV block inside a managed display, shrink or delete the display right away so DD sees nothing
   to protect, then reconcile at end of tick. If another mod cancels the break afterwards, the end-of-tick reconcile
   re-forms the screen from the blocks still there.
3. **Mixin bypass.** Wrap `VanillaProtectionListener.handleBreak` to return `false` for TV blocks. This is brittle
   because it targets internal code, so use it only as a last resort.

Explosions and pistons: DD protecting formed TVs from creepers is a *feature* in survival, so we keep it. `PushReaction`
already stops pistons.

### 5.5 Keeping in sync with external changes
- **`/display delete` on our screen:** `ServerDisplayEvent.Removed` triggers. Mark the group `FORMED=false` and don't
  auto-recreate. The next block placed or broken in the group re-forms it.
- **Orphans** (managed display with no TV blocks, e.g. world edited offline): on chunk load, or lazily when a player is
  near, check the stored corners, and `delete()` if no TV blocks remain.
- **Optional:** veto `/display` moves or resizes on managed displays so the commands don't fight the blocks.

### 5.6 Rendering check (spike)
The command flow makes the black concrete wall itself the display region, and DD draws on the facing side. We do the
same with the TV blocks as the region. **Verify** in M0 that the video sits flush on the front face without z-fighting
against our model, and that DD's conforming mesh (`ConformingWorldMesh`) handles our block's shape. If not, make the
front face recessed or transparent (e.g. a 15/16-deep model).

### 5.7 Permissions
By default we copy Dream Displays' permission rules, using the same node names and the same fallbacks:

| Action | DD equivalent | Node (LuckPerms) | Fallback without LuckPerms |
|---|---|---|---|
| Form a new screen (place the first TV of a group) | `/display create` | `dreamdisplays.create` | everyone |
| Grow, shrink or break **your own** screen | delete own | `dreamdisplays.delete` | everyone |
| Grow, shrink, break or merge **someone else's** screen | delete others | `dreamdisplays.delete.others` | op level 2 |
| Exceed the per-player screen limit | create bypass | `dreamdisplays.create.bypass` | op level 2 |

LuckPerms is checked the same way DD does it: through its API when the mod is present, and the fallback applies when
LuckPerms is absent or leaves the node undefined. LuckPerms is an optional dependency.

**Config `extendPolicy`:**
- `OWNER` (default, DD behavior): only the owner, or an admin holding `dreamdisplays.delete.others`, can change a screen.
- `ANYONE`: any player can extend, shrink or merge any TV screen. Forming new screens and the per-player limit still
  follow the table above.

How it's enforced:
- **Placing** a TV that would join or merge a screen you can't change is cancelled, and the block goes back to your
  inventory with a message such as *"This screen belongs to Steve"*. A non-owner can't attach blocks to someone else's
  screen and leave them unmerged.
- **Breaking** a TV in a screen you can't change stays blocked by DD's own protection, and we show the same message.
  Allowed breaks go through the break hook in [§5.4](#54-breaking-blocks-vs-dd-protection).
- **Merging** two screens needs permission on *both* of them.
- The screen's `DisplayAccess` (who can change the **video**) is left to DD. New screens start at DD's default,
  `LOCKED`, and owners change it in DD's menu.

---

## 6. Content

| Thing | Details |
|---|---|
| **Block** | `dreamtvs:tv`. Item in the *Functional Blocks* creative tab. |
| **Recipe** (shaped, tweakable via datapack) | `I G I / G R G / I C I` where I = iron ingot, G = black stained glass pane, R = redstone, C = copper ingot. Yields **4**, so big screens stay affordable. |
| **Models** | Bezel frame using connection flags (multipart, 16 variants). Front: black (formed) or animated static (unformed). Side and back: dark grey plastic. |
| **Sounds** | Metal place and break. Optional soft "power on" click when a screen forms. |
| **Lang** | `en_us.json`: block name, action-bar messages (`formed`, `not_rectangle`, `too_wide`, `too_tall`, `limit_reached`). |
| **Loot table / tags** | Drops itself. `mineable/pickaxe`. `needs_stone_tool`. |
| **Config** (`config/dreamtvs.json`, server) | `maxWidth` (32), `maxHeight` (24), `maxScreensPerPlayer` (0 = unlimited), `extendPolicy` (`OWNER` \| `ANYONE`, default `OWNER`), `showFormMessages` (true). |

---

## 7. Milestones

### M0: Setup and spikes (reduce risk first)
- [x] MultiLoader Gradle project (common/fabric/neoforge), MC **1.21.1**, Java 21, Gradle 9.8 wrapper. *(CI not set up yet.)*
- [x] DD 1.10.9 from Modrinth Maven (`maven.modrinth:dreamdisplays`, version IDs in `gradle.properties`) on the compile
      classpath and in both dev runtimes. Both loaders build against it.
- [ ] Register `DreamTvsAddon` through `DreamDisplaysServer.onReady` and log on enable (Fabric + NeoForge, SP + dedicated).
      *Confirmed on a NeoForge dedicated (GameTest) server, 2026-11-12. Fabric and singleplayer not yet checked.*
- [ ] **Spike A:** from a debug command, call `displays.create(...)` on a 3×2 wall of placeholder blocks and check that
      rendering, the menu and playback work.
- [ ] **Spike B:** confirm the break-protection behavior and prototype fallback 2 on both loaders.
- [ ] **Spike C:** check that `customData` round-trips across a server restart.
- [ ] Open an upstream issue or PR for a protection opt-out (and for publishing `dreamdisplays-api` to Maven).

#### Spikes, folded into the minimal TV block (2026-11-12)
We dropped the temporary `/dreamtvs spike` commands. A minimal TV block (one TV = one 1×1 screen) checks the
same three risks in a more natural way. Launch with `.\gradlew.bat :neoforge:runClient` (or `:fabric:runClient`)
and test it in a singleplayer world:

1. **A, create + render:** place a TV (*Functional Blocks* tab). The action bar should show *"Screen formed: 1×1"*.
   Open the DD menu on it, paste a link, and check the video sits flush on the front face.
2. **C, saved data:** save and quit, reload the world, then break the TV. The screen must disappear with it, which
   proves our stored bounds came back after the reload.
3. **B, breaking vs protection:** in survival, break a TV that has a screen. It should break and drop, and the screen
   should be gone. Then place a plain DD `/display` screen and confirm *its* blocks are still protected.

### M1: Block and assets
- [x] Minimal `TvBlock` (facing only) registered on both loaders. Placing one makes a 1×1 screen, and breaking or
      removing it deletes the screen. Breaks run before DD's protection, with an owner/op check.
- [x] `FORMED` blockstate (set by the M2 reconciler, placeholder "no signal" front when unformed).
- [ ] Connection flags `UP/DOWN/LEFT/RIGHT` for connected bezels (with the M4 visuals).
- [x] Placeholder textures, model, recipe (`IGI/GRG/ICI` → 4), loot table, pickaxe tag, lang. *(Final art TBD.)*

### M2: Multiblock core
- [x] `ScreenSolver` + unit tests: 1×1, filled rectangles, L-shapes, holes, oversized, splits.
- [x] `ScreenScanner` (capped flood fill) and end-of-tick debounce queue.
- [x] `ScreenReconciler` create / moveTo / merge / delete through `DreamDisplaysBridge`.
- [x] Owner tracking, per-player screen limit, and the §5.7 permission checks (`extendPolicy`, LuckPerms optional).

#### M2 notes (2026-11-12)
- **Code:** pure rules in `screen/solve/` (`ScreenSolver`, `ScreenPlanner`, no Minecraft imports). World side in
  `screen/`: `ScreenPlane` (block pos ↔ 2D cell), `ScreenScanner`, `ScreenQueue` (end-of-tick flush on both
  loaders), `ScreenReconciler`, `ScreenPermissions` + `LuckPermsCheck` (LuckPerms API is `compileOnly`).
  Config model `config/DreamTvsConfig` is loaded from `config/dreamtvs.json` on server start (M4 still owns docs
  and validation polish).
- **Keeping the link while building:** a group that isn't a rectangle (half-built row, broken corner) doesn't
  delete its screen. The screen stays where it is if its blocks are all still there, or shrinks to the largest
  whole rectangle left inside its old bounds. It's deleted only when none of its TVs are left. Merges keep the
  largest screen (tie: oldest, using a `createdAt` now stored in the `customData` tag).
- **Owners:** the placing player owns a new screen. A split-off part keeps the original owner. A screen formed
  with no player (`/fill`, `/setblock`) is owned by the nil UUID ("the server"), so only admins can change it.
- **`FORMED` blockstate** is set by the reconciler. Unformed TVs show a grey "no signal" front (placeholder art).
- **Chunks:** if a wall reaches into an unloaded chunk the reconcile for that wall is skipped (no half-picture
  shrinking). The next change to the wall retries it.
- **Tests:** `gradlew :common:test` runs 34 solver/planner unit tests. `gradlew :neoforge:runGameTestServer`
  runs 8 GameTests against the real DD 1.10.9 server API (form, L-shape, grow, half-built row, split, merge,
  owner break, stranger break/attach blocked, per-player limit). All pass. The GameTests live in the
  `neoforge/src/gametest` source set and aren't shipped. Fabric builds from the same code but has no GameTests yet.

### M3: Robustness
- [ ] Break handling, final version (upstream API or fallback).
- [ ] `/display delete` sync, orphan cleanup, managed-display index rebuilt on start.
- [ ] Optional veto of `/display` resizes on managed screens.
- [ ] Multiplayer test pass: two players building one wall, chunk borders, server restart mid-build.
- [ ] Test TV screen on sable sub level support when using https://github.com/apehum/dreamdisplays-sable ensure support for dreamdisplays-sable.

### M4: Polish
- [ ] Connected-bezel visuals, "no signal" animation, form and unform sounds and messages.
- [ ] Config file and validation, clamped to sensible bounds.
- [ ] README, Modrinth/CurseForge description, screenshots/GIF.

### M5: Release and versions
- [ ] 1.21.1 release (Fabric + NeoForge jars), declared dependency on `dreamdisplays`.
- [ ] Port to 1.21.11 (DD's current `active` version), then consider Stonecutter for 26.x.

---

## 8. Testing

- **Unit (JUnit, `common`):** `ScreenSolver` covering every shape case, and reconcile decisions using a fake
  `DreamDisplaysBridge`.
- **GameTests** (NeoForge GameTest / Fabric GameTest API): place a rectangle and expect one managed display of the
  right size; break the middle and expect a split; merge two screens and expect one survivor that keeps its URL.
- **Manual matrix:** {Fabric, NeoForge} × {singleplayer, dedicated} × {fresh world, restart}. Also check
  `/display delete`, creeper next to a screen, piston against a screen, `/fill` of 32×24 TVs.

---

## 9. Stretch goals
- Floor and ceiling screens (`UP`/`DOWN`). DD supports them, but they need a 6-direction block and an extra solver plane.
- Item "remote" to power on/off or open the DD menu from a distance.
- Redstone signal to pause/play, if DD exposes playback control on the server.
- Bezel colour variants (dye the TV).

---

## 10. Risks and open questions

| # | Item | Mitigation / decision needed |
|---|---|---|
| R1 | DD block protection blocks breaking TVs | §5.4. Push upstream API, keep the fallback ready. |
| R2 | Addon API is experimental until DD 1.11.0 | Pin a DD version range. Keep every API call in `DreamDisplaysBridge`. Track DD releases. |
| R3 | No public Maven for `dreamdisplays-api`, plus renamed bundled Kotlin | Java-only usage, compile against the released jar. Ask upstream to publish. |
| R4 | Render z-fighting or offset on our block model | Spike A. Recessed or transparent front face. |
| R5 | Fabric event ordering vs DD's break hook | Mixin-based hook if `BEFORE` ordering isn't reliable. |

### Decisions (2026-11-12)
- **Name:** "Dream TVs" / `dreamtvs`. The "Dream" prefix is OK.
- **First target:** Minecraft 1.21.1.
- **Shapes:** filled rectangles only. Anything else stays unformed, and there are no plans to change that.
- **Permissions:** DD-style owner rules by default, `extendPolicy = ANYONE` as a config option ([§5.7](#57-permissions)).
