# Developing Dream TVs

How the mod works, how one source tree builds every Minecraft version and loader, and how to check it. This
replaces the project plan and the compat recipes plan, which were written while the mod was being built; what
is still true or still open from them is here. What players need (recipes, permissions, config options) is in
the [reference](REFERENCE.md). Requests for Dream Displays itself are in [UPSTREAM.md](UPSTREAM.md).

Dream Displays is "DD" below. Everything was checked against DD **1.10.9**.

**Contents**

1. [How the mod works](#1-how-the-mod-works)
2. [Working with Dream Displays](#2-working-with-dream-displays)
3. [Minecraft versions and loaders (Stonecutter)](#3-minecraft-versions-and-loaders-stonecutter)
4. [What each Minecraft version needed](#4-what-each-minecraft-version-needed)
5. [Compat recipes](#5-compat-recipes)
6. [Testing](#6-testing)
7. [Open items](#7-open-items)

---

## 1. How the mod works

### Layout

```
src/main/java/com/dreamtvs/
├── block/        The TV blocks. Each extends ScreenBlock and has a ScreenKind.
├── screen/       World side: ScreenPlane, ScreenScanner, ScreenQueue, ScreenReconciler, ScreenSync, permissions.
│   └── solve/    Pure rules, no Minecraft imports: ScreenSolver, ScreenPlanner.
├── dd/           Everything that talks to Dream Displays: the bridge, the addon, the index, DD internals.
├── client/       Picture placement, the CRT overlay, tooltips, the settings screen, the remote.
├── mixin/        Mixins into DD (server and client).
├── compat/       Which recipes are active (section 5).
├── config/       config/dreamtvs.json (server) and config/dreamtvs-client.json.
├── item/         The TV Remote.
├── net/          The one payload: the server's remote range, sent on join.
├── visualtest/   The visual tests (section 6). Not in the mod jar.
├── fabric/       Fabric only: registration, events, Mod Menu.
└── neoforge/     NeoForge only: registration, events.
src/gametest/     NeoForge GameTests. Not in the mod jar.
src/compat/       Hand-written recipe sources (section 5).
src/generated/    Generated recipes, item models and the compat index. Committed.
tools/            The recipe generator: plain Java, no Minecraft.
```

Art and the scripts that make it live in the git-ignored `blockbench/` folder; only finished assets are committed.

### The world is the source of truth

A screen is always derived from the TV blocks in the world. There are no block entities: a screen's identity
and bounds are stored on the DD display itself (`customData`, tag `dreamtvs:screen`, see `ManagedTag`). Every
change to a wall runs the same idempotent reconcile, so a bug or a crash heals at the next change.

**Solving (`ScreenSolver`, pure).** TVs group by plane: same wall, same facing, same kind, joined on their four
sides. A group is a screen only if it is a completely filled rectangle within its kind's `maxWidth` by `maxHeight`
(`lcdPanel` or `jumbotron` in the config). The flood fill stops at `maxWidth * maxHeight + 1` cells, so huge
walls stay cheap.

**Reconciling (`ScreenQueue`, `ScreenReconciler`, `ScreenPlanner`).** Placing, breaking and neighbour changes only
mark the wall dirty. Once per server tick each dirty wall is reconciled, so `/fill` costs one pass. For each
rectangle on the wall:

- no display there: create one, and tag it;
- one display: move it to the rectangle if its corners changed. The id, link and settings survive;
- several displays (two screens were joined): keep the largest (tie: the oldest, by the `createdAt` in the tag),
  delete the others, then move.

Details that matter:

- **The link is kept while building.** A group that isn't a rectangle (a half-built row, a broken corner) doesn't
  delete its screen. The screen stays where it is if all its blocks are still there, or shrinks to the largest
  whole rectangle left inside its old bounds. It is deleted only when none of its TVs are left.
- **Splitting.** When a break leaves two rectangles, the bigger one keeps the display and the other gets a new one
  with no link, and the same owner.
- **Owners.** The player who placed the block owns a new screen. A screen formed with no player (`/fill`,
  `/setblock`) is owned by the nil UUID, "the server", so only admins can change it.
- **Unloaded chunks.** If a wall reaches into an unloaded chunk, its reconcile is skipped (no shrinking to the
  loaded half). The next change, or the chunk loading, retries it.
- **`FORMED`** is set by the reconciler. It doesn't change the model: a formed wall is covered by DD's picture, and
  an unformed one shows dark glass.

### Screen kinds

| Kind | Block | Merges | Notes |
|---|---|---|---|
| `LCD_PANEL` (0) | `lcd_panel` | Yes | A 10-pixel slab, glass 4 pixels back behind a 1-pixel bezel. The `up/down/left/right` blockstate flags only draw the connected bezel and never trigger a reconcile. |
| `CRT` (1) | `crt_tv` | No | One block, one 1x1 screen, also beside other CRTs. A 14x10 tube above a control bar. |
| `JUMBOTRON` (2) | `jumbotron` | Yes | A full cube, the whole front is screen, no bezel. |
| `FLAT_SCREEN_2X1` (3), `FLAT_SCREEN_3X2` (4) | `flat_screen_2x1`, `flat_screen_3x2` | No | Fixed size, placed in one go. |

The kind is part of the plane, so kinds on one wall never see each other, and it is one byte of the tag (tags
without it read as LCD panels). The ids are stored in worlds: never renumber them.

**Flat Screens work like beds.** One item places the whole TV. The clicked block becomes the bottom of the middle
column (the left block of a 2x1), and the rest goes to the sides and up; if any of that space is taken, nothing is
placed. Each block has a `part`, `row * width + column`, row 0 at the bottom and column 0 on the viewer's left. A
TV missing a part falls apart, so breaking any block removes all of it. Only part 0 drops the item, so one TV
gives back one item; in creative, part 0 is removed first without a drop, as doors do. A TV counts as a screen
only while all its blocks are there, and `maxWidth`/`maxHeight` don't apply to it.

### The picture on the glass (client)

DD draws a flat picture over the whole front face of a display's block box. Our glass is set back and smaller
than the face, so for displays carrying our tag the client moves the picture onto the glass and trims it so its
edges end under the bezel (`PanelPicture`, driven by `ScreenRendererMixin` and `DisplayPoseStackMixin`). The
picture isn't drawn when the camera is edge-on to the glass or behind it, where it bled through the bezel.

**The CRT look (`CrtOverlay`)** is one translucent texture drawn over DD's picture: phosphor glow, aperture
grille, scanlines, darkened edges, glass glare, the curved edge of the tube, and under it a faint bar of light
that rolls down the tube every 9 seconds. Players can turn it off (`crtOverlay` in the client config). It is
only an overlay; the video isn't warped or blurred, which would
need our own shader in place of DD's drawing. No sounds or static (decided against).

**4:3 on the CRT.** DD fits the whole video into a texture the shape of the block box, with black bars.
`ScreenRendererVideoMixin` narrows the video quad's texture coordinates (`PanelPicture.setVideo`), so the video
fills the tube at its true proportions with its sides cut off.

### Permissions

The rules and nodes are in the [reference](REFERENCE.md). How they are enforced:

- LuckPerms is checked through its API when it is installed; the fallback applies when it is absent or leaves a
  node undefined. It is an optional, compile-only dependency.
- **Placing** a TV that would join a screen the player may not change is refused, with whose screen it is. A
  stranger can't attach blocks to someone else's screen and leave them unmerged.
- **Breaking** a TV in a screen the player may not change stays blocked by DD's own protection.
- **Merging** two screens needs permission on both.
- Who can change the video (`DisplayAccess`) is left to DD. New screens start at DD's default, locked.

### Other pieces

- **Config** (`DreamTvsConfig` for the server, `DreamTvsClientConfig` for the client, sharing `ConfigJson`).
  Each file is written with a `//` comment per option and read leniently. Each option is validated on its own: a
  wrong type falls back to that option's default, numbers are clamped, unknown keys are reported, and every fix is
  logged. After a good read the file is rewritten, so it picks up new options. A file that isn't valid JSON is
  left alone and the defaults are used. No config library, so nothing to port per version.
- **Tooltips** (`ItemDescription`) are a port of Create 6's, so they look like Create's: "Hold [Shift] for
  Summary", then conditions and behaviours. Lang keys follow Create's layout: `<item key>.tooltip.summary`,
  `.condition1`, `.behaviour1`, and so on. An item with no `.summary` key gets no tooltip.
- **The TV Remote** binds to a TV block's position rather than a screen id, so it keeps working when a wall grows,
  shrinks or forms again. It works on the client (DD's client API), so the client checks `remoteRange`: the
  server sends its value when a player joins (`RemoteRangePayload`), and the distance is from the player's eyes
  to the nearest edge of the screen's blocks.
- **Art rules.** A small fixed palette per material, light from the top left, flat areas stay flat (no speckle);
  detail comes from edges, seams, vents and fittings.

---

## 2. Working with Dream Displays

### What we use

- **The server API.** `DreamDisplaysServer` works on Fabric and NeoForge. We register an addon
  (`DreamTvsAddon`) once it is ready (`onReady`, because start order isn't guaranteed). All API calls go through
  `DreamDisplaysBridge`. Addons are marked experimental until DD 1.11.0, so expect changes.
- `displays.create(owner, world, corners, facing)` skips DD's black-concrete check: the caller answers for the
  wall. It returns `null` on overlap. `moveTo` resizes in place and keeps the id, link and settings. `delete()`
  goes through `DisplayVeto.Delete`.
- `customData` is saved with the display and synced to clients. `ServerDisplay` doesn't expose its corners or
  facing, so the tag stores them.
- DD's world key is the dimension id (`minecraft:overworld`).
- `create()` doesn't enforce DD's per-player limit; we enforce our own.

**Why Java, not Kotlin.** DD's jar bundles Kotlin renamed to `com.dreamdisplays.libs.kotlin`. An addon written in
Kotlin would reference `kotlin.*` types DD doesn't provide. Plain Java that calls only the Java-facing API avoids
that. Kotlin-only helpers can't be used: call `on(Class, Consumer)` and `setUrl(url, "")` instead of the reified
and default-parameter forms. There is no public Maven for DD's API, so we compile against the released mod jar
from Modrinth's Maven.

### Where DD gets in the way, and what we do

| Problem | What we do | If a DD update breaks it |
|---|---|---|
| 40 ticks after any player joins, DD deletes every display with no black concrete in its box, including API-made ones. | `DisplayManagerMixin` keeps tagged displays out of the removal list. | The mixin doesn't apply; the walls are reconciled and get new screens, without their links. |
| DD cancels every block break inside a display's box. | Our break hook runs before DD's and shrinks the display out of the way first. Fabric: a `PlayerBlockBreakEvents.BEFORE` phase ordered before the default phase DD registers in. NeoForge: the break event at `HIGHEST`. | Formed TVs can't be broken. |
| DD draws on the block's front face, not on our glass. | The client mixins above. | The picture floats in front of the panel; nothing else changes. |
| DD letterboxes video into the block-box shape. | `ScreenRendererVideoMixin` (kept separate: it leans on a compiler-generated lambda name, so it is the likeliest to break). | CRT video is stretched with black bars. |
| Another mod moves a display (dreamdisplays-sable). | We read its new box and facing from DD internals (`DdInternals`, guarded) and follow it. | Moved screens stop being managed instead of being deleted. |

Every mixin into DD is `require = 0`, and every use of DD internals fails soft, with a warning in the log.
Explosions and pistons: DD protecting formed screens from creepers is kept on purpose, and the blocks can't be
pushed. The first two rows would go away with the API additions asked for in [UPSTREAM.md](UPSTREAM.md).

### Changes made by others (`ScreenSync`)

The bridge wraps its own create, move, delete and tag calls in a "self" flag. Events outside it:

- **Deleted on purpose** (a `DisplayVeto.Delete` was asked first: `/display delete`, the DD menu, an addon's
  `delete()`): the TVs go dark and stay dark until the wall next changes, which forms it again without the link.
- **Removed silently** (no delete request): the wall is reconciled, so a whole rectangle gets a new screen.
- **Moved by another mod:** the display is re-tagged at its new box and both places are re-checked. If the new box
  can't be read, or the screen now faces up or down, we stop managing it.

**The index** (`ManagedIndex`) maps id to world and bounds, and world to chunk to ids. It is rebuilt from
`customData` the first time it is needed after a server start, then kept current by the bridge and DD's events,
so block hooks and chunk loads never walk every display.

**Orphans.** After each server start every managed screen is re-checked once DD is ready, and again whenever one
of its chunks loads. A re-check is a normal reconcile, so a screen with no TVs left is deleted and a partly
broken one shrinks.

**Sable sub-levels.** Before the blocks move, dreamdisplays-sable calls `moveTo` on every display inside them. We
follow that move, so the screen keeps its id and link. Removing a sub-level deletes the display through
`delete()`, which counts as deleted on purpose.

---

## 3. Minecraft versions and loaders (Stonecutter)

Six builds from one source tree:

| | Fabric | NeoForge |
|---|---|---|
| 1.21.1 | `1.21.1-fabric` | `1.21.1-neoforge` |
| 1.21.11 | `1.21.11-fabric` | `1.21.11-neoforge` |
| 26.3 | `26.3-fabric` | `26.3-neoforge` |

[Stonecutter](https://stonecutter.kikugie.dev/) gives every build its own Gradle project (a "node"), named
`<minecraft>-<loader>`. All nodes compile the same `src/` tree; comments in the sources say which lines belong
to which node. DD is built the same way.

| File | What it does |
|---|---|
| `settings.gradle.kts` | Lists the nodes: one `match("<minecraft>", "fabric", "neoforge")` line per Minecraft version. Each node is built by `build.<loader>.gradle.kts`. `vcsVersion` is the node the committed sources are written for (`1.21.1-neoforge`). |
| `stonecutter.gradle.kts` | The active node, and the names usable in conditions: the Minecraft version, and the constants `fabric` and `neoforge`. Also renames `ResourceLocation` to `Identifier` from 1.21.11 on. |
| `stonecutter.properties.toml` | Every version number: the mod's own, and per node the Java version, loader and dependency versions, and the resource pack format. A node reads the top level, then `["<minecraft>"]`, then `[<loader>."<minecraft>"]`; later tables win. `gradle.properties` holds only Gradle options. |
| `build.fabric.gradle.kts` | Every Fabric node (Loom; `loom-back-compat` picks the remapping Loom on 1.21.x and the plain one on 26.x, which is unobfuscated). |
| `build.neoforge.gradle.kts` | Every NeoForge node (ModDevGradle), including the GameTest source set and its run. |
| `buildSrc/` | `neoforge-mutex`: stops several NeoForge nodes from decompiling Minecraft at the same time. |
| `versions/<node>/` | Build output only. Nothing in it is committed. |

Gradle itself runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`; downloaded if missing): Stonecutter needs
it, and 26.x compiles for Java 25. The 1.21.x nodes compile for Java 21. The DD jar for each node comes from
Modrinth's Maven, by version id (`deps.dreamdisplays_id`).

### Conditions in the sources

```java
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else
import net.minecraft.client.renderer.RenderType;
```

- `//? if <condition> {` ... `//?}` wraps a block; `//?} else {` and `//?} else if <condition> {` continue it.
  Without braces (`//? if >=26.1` or `//?} else`) the condition covers the next line only.
- The lines for nodes other than the committed one sit in `/* ... */`. A block comment inside one is written
  `/^ ... ^/`.
- Conditions are a Minecraft version comparison (`>=1.21.2`, `<1.21.9`, `>=26.1`), `fabric` or `neoforge`.
- Whole files can be loader-only: `com.dreamtvs.fabric` and `com.dreamtvs.neoforge` wrap their contents in
  `//? if fabric {` / `//? if neoforge {`, and compile to empty classes on the other loader.

The sources are committed as the `1.21.1-neoforge` node sees them. To work on another node in the IDE, run its
`Set active project to <node>` Gradle task, and run `Reset active project` before committing. Building or running
a node from the command line needs neither: each node compiles its own generated copy of the sources.

### Where differences go

- Small API differences go in `McCompat` (common) and `ClientCompat` (client), so the rest of the code has no
  conditions.
- Bigger ones (registration, block hooks, rendering, the test harnesses) stay where they are, behind conditions.
- Resources whose format changed are separate folders that the build scripts pick from:
  - `src/main/resources-1.21` or `src/main/resources-26`: loot tables.
  - `src/generated/recipes-1.21.1` or `src/generated/recipes-1.21.2`: recipes.
- `pack.mcmeta` of the built-in resource pack, the mixin config's Java level and the loader metadata are filled
  in from `stonecutter.properties.toml` when resources are processed.

### Commands

| Command | What it does |
|---|---|
| `gradlew build` | Builds every node and runs the unit tests. Jars: `versions/<node>/build/libs`. |
| `gradlew :<node>:runClient`, `:<node>:runServer` | A dev game. Run folders are per node: `run/<loader>-<minecraft>-client`, and so on. |
| `gradlew :<minecraft>-neoforge:runClient2` | A second player (`Dev2`, not an op), for multiplayer checks. |
| `gradlew test` | The unit tests. |
| `gradlew :<minecraft>-neoforge:runGameTestServer` | The GameTests. |
| `gradlew :<node>:runVisualTest` | The visual tests. |
| `gradlew :tools:generateCompatRecipes` | Regenerates `src/generated` from `src/compat`. |
| `gradlew :1.21.1-neoforge:installTestMods` / `removeTestMods` | Copies Sable, Create, Create Aeronautics and dreamdisplays-sable into the 1.21.1 NeoForge run folders, or removes them. |

### Adding a Minecraft version

1. Add a `match("<minecraft>", "fabric", "neoforge")` line in `settings.gradle.kts`.
2. Add its tables to `stonecutter.properties.toml`: `["<minecraft>"]` (`mod.mc_releases`, `mod.resource_format`,
   `deps.java`) and one per loader (`mod.mc_compat`, loader and API versions, `deps.dreamdisplays_id`).
3. Run `gradlew :<minecraft>-neoforge:compileJava` and fix what doesn't compile, with conditions or in
   `McCompat` / `ClientCompat`. Then the Fabric node.
4. If a data format changed, add a resource folder and pick it in both build scripts. For recipes, add a `Format`
   to the generator and regenerate (section 5).
5. Run everything in section 6 on both new nodes, and look at the visual test screenshots: the GameTests don't
   cover the client.

---

## 4. What each Minecraft version needed

DD 1.10.9 has the same `ScreenRenderer`, `DisplayPoseStack` and `DisplayManager` signatures on all three
versions, so the mixins are the same everywhere.

### 1.21.11

- `ResourceLocation` is `Identifier` (a Stonecutter replacement).
- Blocks and items need their registry id before they are built (since 1.21.2).
- `useItemOn` returns `InteractionResult`, and `updateShape` has a new signature.
- `onRemove` is gone: a replaced TV is seen in `affectNeighborsAfterRemoval`, and a turned one in `onPlace`.
- Permission levels, the user cache, render types and a few GUI widgets moved.
- Since 1.21.2 recipes write ingredients as plain ids, so the generator writes a second recipe folder.
- Items need `assets/dreamtvs/items/*.json` (ignored by 1.21.1).
- Minecraft 1.21.5 replaced `@GameTest`. Tests are marked with our own `@TvTest`, and `TvGameTests` registers
  them: a `@GameTestGenerator` on 1.21.1, test functions plus test instances from 1.21.11 on (each batch is its
  own test environment). Test ids are snake case, such as `dreamtvs:rectangle_forms_one_screen`.

### 26.3

- Blocks have no codecs. `PushReaction.BLOCK` is `IMMOVEABLE`. Overlay messages, chunk positions, the toast
  manager, NeoForge's break event (`BreakBlockEvent`) and Fabric's creative tab builder were renamed.
- **Loot tables** have a new format (one `condition` object with a `type`). The old format still loads on 26.3,
  but its conditions are silently ignored, which made every Flat Screen part drop an item. The GameTests caught
  it. That is why loot tables exist twice.
- NeoForge deprecated `logoFile`; our square icon is an `iconFile` there.
- **Rendering works differently**, below.

### The CRT overlay on 26.x

Up to 1.21.x, DD draws each screen straight away, and the CRT overlay is drawn through the game's buffer source
once DD's render pass has ended. 26.x has no buffer source. DD hands every quad to the game's draw list
(`SubmitNodeCollector.submitCustomGeometry`), and the game emits and draws them later in the frame.

On 26.3 the overlay was missing on both loaders, with nothing in the log. The visual tests found it. Four things
were wrong, and each one alone was enough to hide it:

1. **The end-of-pass hook doesn't fire.** The overlay was drawn from an inject at the end of DD's
   `ScreenRenderer.render`, which never ran on 26.3. On 26.x `PanelPicture.end` now hands the overlay over
   straight after each CRT's picture.
2. **The quads were emitted too late.** They read the queue of CRT positions when they are emitted, which on 26.x
   is after the queue has been cleared, so nothing was emitted. `CrtOverlay.flush` now copies the queue first.
3. **The game's transparency pass flattened it.** 26.x draws blended render types through order-independent
   transparency, where the overlay came out as an even grey tint. DD keeps its own render types out of that pass
   with `DisplaySolidPhase.mark`; the overlay's render type is marked the same way (`CrtOverlay.type`).
4. **The draw order was random.** Within one phase the game groups draws by render type in a hash map, so the
   overlay came out before or after DD's picture depending on the launch. Drawn before it, the picture covers it.
   The overlay now goes into the draw list's next order (`order(1)`), which the game draws after everything in
   the default one. The level's draw list has no getter, so `CrtOverlay.levelSubmits` reads the
   `LevelRenderer.submitNodeStorage` field by reflection (26.x isn't obfuscated, so the name is the same on both
   loaders).

DD can also draw its pictures itself at the end of the frame (its `ScreenRenderer` callback). There draws keep
the order they are given, and the overlay goes through that callback as before. That path didn't run in the
visual tests, so it is untested.

With these, a CRT on 26.3 matches 1.21.11 to within a level or two per colour channel. `DisplaySolidPhase` and
the callback are DD client internals: like the rest of `CrtOverlay`, any failure switches the overlay off with
a warning, and CRTs show a plain picture.

---

## 5. Compat recipes

When a supported tech mod is installed, every TV's recipe is built from that mod's parts. With Create installed
the TVs are made in a sequenced assembly, and if another tech mod is installed too, the assembly deploys that
mod's parts. Exactly one recipe per TV is ever active. There is no compile dependency on any of these mods,
Create included: recipes name items by id in JSON only.

### Sources and priority

In `auto` mode the first usable source wins: its mods are loaded and every item it uses exists.

| # | Source id | Mod ids needed | Parts used |
|---|---|---|---|
| 1 | `power_grid` | `create`, `powergrid` | display module, circuit board, copper wire, light bulb, cathode ray tube |
| 2 | `electro_energetics` | `create`, `electroenergetics` | relay, copper wire, bulb |
| 3 | `mekanism` | `mekanism` | steel ingot, basic control circuit, infused alloy |
| 4 | `createaddition` | `create`, `createaddition` | capacitor, copper wire |
| 5 | `immersiveengineering` | `immersiveengineering` | steel plate, electronic component, copper wire, light bulb |
| 6 | `modern_industrialization` | `modern_industrialization` | steel plate, analog circuit, copper cable |
| 7 | `thermal` | `thermal` | steel ingot, RF coil, electrum ingot |
| 8 | `ae2` | `ae2` | quartz vibrant glass, logic processor, fluix crystal |
| 9 | `create` | `create` | iron sheet, electron tube, copper sheet |
| fallback | `vanilla` | | the normal recipes |

- The order, mod ids and materials all live in `src/compat/sources.json`. Reordering is moving one entry.
- Sources that are Create addons (1, 2, 4) take Create's parts for roles they don't set.
- Mod and item ids were read from each mod's 1.21.1 jar, except Thermal's: **Thermal has no 1.21.1 release**, and
  its ids are from memory. If they are wrong the source is skipped with a warning.
- On 1.21.1 every one of these mods is NeoForge-only, so Fabric always uses vanilla there. The Fabric side is
  fully wired for versions where Fabric builds exist.
- The compat recipes were only tried with the mods on 1.21.1. On newer versions they load only if the mods exist
  and still accept the same recipe format; otherwise the resolver falls back.

### How one recipe is chosen

`CompatResolver` is pure Java and unit-tested. It takes the config, the sources in order, a "mod loaded" check and
an "item exists" check:

```
methods = createSequencedAssembly ? [sequenced_assembly, crafting] : [crafting]
1. recipeSource is "vanilla"                         -> (vanilla, crafting)
2. recipeSource is a source id, usable for a method  -> (that source, first such method); else warn and go on
3. for each method, for each source in order         -> the first usable (source, method)
4. nothing usable                                    -> (vanilla, crafting)
```

- **Method first, then source**: with Create installed, "a sequenced assembly with the best parts available" comes
  before falling back to crafting.
- **Checking that items exist is what makes ports safe.** A mod that is installed but renamed an item is skipped
  with a warning naming the missing items. Tags can't be checked this early, so sources use plain item ids.
- Sequenced assemblies need our `dreamtvs:incomplete_<tv>` items, which are only registered with Create installed.
  Without them the resolver falls back to crafting.
- Bad config values: an unknown id, or one whose mods or items are missing, gives a warning and then `auto`. Ids
  ignore case.
- **When it applies.** Recipes are data, so the choice is made at world load and `/reload`. Datapacks load before
  the server-start config load, so the resolver reads the config file itself. `CompatRecipes` caches the choice
  for one data load and logs it, for example
  `Dream TVs recipes: mekanism (sequenced_assembly) [auto, installed: mekanism, create]`.

**Reaching the recipe system.** Every TV recipe, vanilla included, carries one custom condition:

```json
{ "type": "dreamtvs:recipe_source", "source": "mekanism", "method": "sequenced_assembly" }
```

Recipes that aren't selected are never parsed, so missing items or a missing `create:sequenced_assembly` recipe
type cause no errors. One file serves both loaders: each recipe has a `neoforge:conditions` block and a
`fabric:load_conditions` block, and each loader ignores the other's key (`RecipeSourceCondition` in each loader
package).

| Recipe | Id |
|---|---|
| Vanilla | `dreamtvs:<tv>` (unchanged, so datapack overrides and recipe-book progress keep working) |
| Crafting with a source's parts | `dreamtvs:compat/<source>/crafting/<tv>` |
| Create sequenced assembly | `dreamtvs:compat/<source>/sequenced_assembly/<tv>` |

### Recipe design

Each source maps **roles** to items, and each TV's templates use roles (`src/compat/tvs.json`). Roles a source
doesn't set come from its parent, and finally from vanilla.

| Role | Meaning | Vanilla |
|---|---|---|
| `frame` | Casing | iron ingot |
| `screen` | Display surface | black stained glass pane |
| `circuit` | The brain | redstone |
| `wire` | Wiring | copper ingot |
| `light` | Backlight (Jumbotron) | glowstone dust |
| `tube` | Picture tube (CRT) | glass pane |
| `housing` | CRT body | gray concrete |

| TV | Crafting (makes) | Sequenced assembly: input, then steps (makes) |
|---|---|---|
| LCD Panel | `IGI / GRG / ICI` frame, screen, circuit, wire (4) | frame; deploy screen, screen, circuit, wire; press (4) |
| Jumbotron | `IDI / DRD / IDI` frame, light, circuit (4) | frame; deploy light, circuit, light; press (4) |
| CRT TV | `CCC / GRI / CCC` housing, tube, circuit, frame (1) | housing; deploy tube, circuit, frame (1) |
| Flat Screen (2x1) | `PPP / IRI` screen, frame, circuit (1) | frame; deploy circuit, screen, screen, screen; press (1) |
| Flat Screen (3x2) | `PPP / PPP / IRI` screen, frame, circuit (1) | a Flat Screen (2x1); deploy screen x3, circuit; press (1) |

- Create picks a sequenced assembly by its input and first step, so no two TVs may share both. The generator
  checks this for every source and fails with a clear message. Another mod's assembly with the same start could
  still clash; unlikely, but possible.
- The in-progress items use the finished TV's model, and have no progress bar (that needs Create's item class).
- Balance is a first pass: assemblies use fewer frames than crafting, as a reward for automating.

### Generation

```
src/compat/sources.json, tvs.json
        |  gradlew :tools:generateCompatRecipes   (CompatRecipeGenerator)
        v
src/generated/
  common/                  every Minecraft version: dreamtvs.compat.json (the runtime index), item models
  recipes-1.21.1/          1.21.1: ingredients as {"item": ...}
  recipes-1.21.2/          1.21.2 and later: ingredients as "id"
```

The output is committed, and `CompatRecipesUpToDateTest` fails the build if it doesn't match the sources. The
recipe format is confined to the generator's `Format` and two methods (`crafting`, `sequencedAssembly`); a
version with another format adds a `Format` and regenerates. Mods or items missing on a version need no code
change.

**Datapacks.** Any generated recipe can be overridden by id; keep the `dreamtvs:recipe_source` condition if it
should still follow the config. To add a source for an unsupported mod, ship your own recipe and set
`compat.recipeSource` to `"vanilla"`, so two recipes aren't active.

---

## 6. Testing

| Command | What it checks |
|---|---|
| `gradlew test` | Unit tests, in every node: the solver and planner on pictures of walls, the compat resolver, the config parser, and that the generated recipes are up to date. |
| `gradlew :<minecraft>-neoforge:runGameTestServer` | GameTests against the real DD server API: forming, growing, splitting, merging, permissions, the per-player limit, `/display delete`, the join cleanup, orphans, outside moves, Flat Screens, and which recipes load. |
| `gradlew :<node>:runVisualTest` | Visual tests: every kind of TV, in the real client. |

Fabric has no GameTests; it builds from the same code, and the visual tests are what runs it.

**GameTest notes.** Mock players always report `isCreative()`, so drops from a survival player's break are
covered by a test with no player. To check a compat source, copy its jars into
`run/neoforge-<minecraft>-gametest/mods` and run the compat test on its own: Create's networking doesn't accept
GameTest fake players, so the screen tests crash with Create there.

### Visual tests

`runVisualTest` opens the game, creates a flat world, builds each case in front of a grey wall with the camera
straight in front of it, takes a screenshot, and exits. The cases are LCD Panel and Jumbotron walls from 1x1 to
7x4, a CRT, a row of CRTs, both Flat Screens, and two kinds side by side.

- Screenshots: `run/<loader>-<minecraft>-visualtest/screenshots/<case>.png`
- Results: `run/<loader>-<minecraft>-visualtest/visualtest-results.txt`, one `PASS` or `FAIL` line per case. The
  run fails if any case fails.

A case passes when the server has formed the screens it expects, at the right sizes. **Whether the picture is
drawn is only in the screenshots, so look at them.** With no video set, every screen should show DD's loading
picture, black with a blue bar at the bottom, and CRTs should show the glass overlay over it.

- Run one node at a time: four clients at once is heavy, and the first run in a new game folder also downloads
  DD's media libraries.
- Run a node more than once before trusting a rendering fix. The 26.x draw order problem showed on some launches
  and not on others.
- The Fabric 26.3 client sometimes dies with a native crash (exit code `0xC0000005`) while the game is still
  loading its resources, before the tests start. It happened in about half the launches, on the first launch
  after a rebuild each time, and the next launch passed. The cause isn't known, and no other node has done it.
  Rerun it.
- The task clears the last run's screenshots and results before the game starts, so a crashed run leaves none.
- The game window doesn't need the focus, and the mouse is left free.
- Only north-facing screens are built.
- The harness is `com.dreamtvs.visualtest.VisualTests`. It is left out of the mod jars and only runs when
  `runVisualTest` sets the `dreamtvs.visualTest` system property.
- The screenshot waits for the loading bar to be mid-screen, using the bar's timing in DD 1.10.9. If DD changes
  it, the bar may be elsewhere or out of sight in some screenshots.

### Manual checks

On a dedicated server (`runServer`) with two clients:

1. **Join cleanup.** Build a screen, set a link, then have the second player join. Two seconds later the screen
   and link are still there. Check both loaders.
2. **`/display delete`** on a TV screen: the TVs go dark and stay dark. Place one more TV on the wall, and the
   screen forms again with no link.
3. **Two builders.** A starts a wall and B adds to it: B is refused with "This screen belongs to A". Set
   `extendPolicy` to `ANYONE`, restart, and B can grow it.
4. **Chunk borders.** Build a wall across a chunk border, fly away until it unloads, come back: the screen and
   link are unchanged.
5. **Restart mid-build.** Leave a row half-built and restart. The screen keeps its old size, and finishing the row
   grows it.
6. **Offline edit.** Stop the server, remove a screen's TVs with a world editor, start again: the orphaned screen
   is gone (`/display list`).
7. **Sable** (1.21.1 NeoForge, after `installTestMods`). Put a TV screen on a contraption, assemble it and fly it
   around. The video follows the screen, keeps playing and survives disassembly. Breaking a TV on the moving
   sub-level shrinks the screen.

Also worth a look by hand: a creeper next to a screen, a piston against one, `/fill` of 32x24 TVs, tooltips, the
settings screen and item icons on each version. For compat recipes: with no mods, Create, Create plus one mod,
one mod without Create, and several mods, under `auto`, a forced source, `vanilla`, assembly off and a bad value,
check the recipe in JEI or EMI, make a TV, edit the config, `/reload`, and check again.

---

## 7. Open items

**Not done**

- A release: jars for all six builds, with the declared dependency on `dreamdisplays`.
- File the requests in [UPSTREAM.md](UPSTREAM.md): a protection opt-out, skipping the join cleanup for API-made
  displays, exposing a display's box and facing, and publishing DD's API to a Maven.
- Find out why the Fabric 26.3 dev client sometimes crashes while loading (section 6), and whether it happens
  outside the visual tests.
- Look at tooltips, the settings screen and item icons in a client on 1.21.11 and 26.3. The visual tests cover
  the picture on every kind of TV, and nothing else.
- Compat recipes: playtest each source for cost (Power Grid's display modules and circuit boards especially);
  try each mod in a client (one recipe per TV in JEI or EMI, and the assembly runs on a belt); check that
  `/reload` after a config edit switches the recipe; art for the incomplete items.
- Source 3 was asked for as "Mechanism" and taken to mean **Mekanism**. Not confirmed. Swapping it is one entry
  in `sources.json`.
- Verify Thermal's item ids when porting to a version that has Thermal.

**Known risks**

- DD's addon API is experimental until 1.11.0, and we lean on DD internals in several places (section 2). Keep
  API calls in `DreamDisplaysBridge` and internals in `DdInternals` and the mixins, and recheck all of them on
  every DD update. The visual tests and GameTests are how.
- On 26.x the CRT overlay also depends on a private Minecraft field and on how the game orders its draw list.

**Decided**

- Filled rectangles only. Anything else stays unformed, and there are no plans to change that.
- Wall screens only; no floor or ceiling screens (see below).
- DD-style owner rules by default, with `extendPolicy = ANYONE` as an option.
- `/display create` stays; both ways of making a screen exist side by side.
- No Paper, Forge or proxy support.

**Ideas for later**

- A projector, with floor and ceiling screens. DD supports `UP` and `DOWN` displays, but it needs a 6-direction
  block and another solver plane.
- A redstone signal to pause and play, if DD exposes playback control on the server.
- Bezel colour variants (dye the TV).
- A soft "power on" sound when a screen forms.
- Compat: a user-editable priority order (`compat.priority`), `compat.disabledSources`, custom sources by config
  or datapack, showing the active source in a tooltip or command, a progress bar on the incomplete items, and
  real power integration (a TV that draws FE or stress), which needs its own plan.
