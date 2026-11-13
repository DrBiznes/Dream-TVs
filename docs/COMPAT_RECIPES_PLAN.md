# Dream TVs — Tech Mod Compatibility Update (Recipes)

> A compatibility update for [Dream TVs](PROJECT_PLAN.md). When a supported technology mod is installed, every TV
> gets a recipe **built from that mod's parts**. With **Create** installed, the TVs are made in a **Create sequenced
> assembly** instead of at a crafting table, and if other tech mods are installed too, the assembly deploys **their**
> parts. A fixed **priority list** picks one source when several mods are loaded, and **config options** let players
> pick another source, go back to vanilla, or turn sequenced assembly off.

| | |
|---|---|
| **Status** | Implemented (C0 to C2). Recipe balance and the in-game matrix still need playtesting (C3, C4). |
| **Target version** | Dream TVs `0.x` compat release, after the 1.21.1 release (M5) |
| **Scope** | Recipes for all five TVs, plus five hidden "incomplete" items for Create. No Dream Displays changes. |
| **Loaders** | Fabric and NeoForge. On 1.21.1 every supported mod is NeoForge-only, so Fabric only ever uses vanilla. |

---

## 1. Goals and non-goals

### Goals
- If a supported tech mod is present, the TVs are craftable with that mod's materials, so they fit modpack
  progression.
- If **Create** is present, the TVs are made in a Create sequenced assembly (deployers and a press), and the parts it
  deploys come from the highest-priority other tech mod that's installed (Mekanism circuits, Power Grid display
  modules, and so on).
- Several mods loaded at once: exactly **one** recipe per TV is active.
- Server-side config: pick a different source, force vanilla, or turn sequenced assembly off.
- No supported mod installed: the vanilla recipes work as before, with their original ids.
- **No hard or compile dependency** on any of these mods, Create included. Dream TVs must keep loading when it's
  ported to a Minecraft version where Create or some of these mods don't exist, or where an item was renamed.

### Non-goals
- Compat *behavior* (powering the TV with FE, Create stress, energy use). Possible later, see [§10](#10-stretch-goals).
- Changing the vanilla recipes.
- Supporting every tech mod. The list is fixed per release; datapack authors can override any recipe
  ([§6.4](#64-overriding-or-adding-recipes-datapacks)).

---

## 2. Supported sources and priority order

Higher in the table = higher priority. In `auto` mode the first **usable** source wins (its mods are loaded and
every item it uses exists).

| # | Source id | Mod | Mod ids required | 1.21.1 loaders | Parts used |
|---|---|---|---|---|---|
| 1 | `power_grid` | **Create: Power Grid** | `create`, `powergrid` | NeoForge | display module, circuit board, copper wire, light bulb, cathode ray tube |
| 2 | `electro_energetics` | **Create: Electro Energetics** | `create`, `electroenergetics` | NeoForge | relay, copper wire, bulb |
| 3 | `mekanism` | **Mekanism** | `mekanism` | NeoForge | steel ingot, basic control circuit, infused alloy |
| 4 | `createaddition` | **Create Crafts & Additions** | `create`, `createaddition` | NeoForge | capacitor, copper wire |
| 5 | `immersiveengineering` | **Immersive Engineering** | `immersiveengineering` | NeoForge | steel plate, electronic component, copper wire, light bulb |
| 6 | `modern_industrialization` | **Modern Industrialization** | `modern_industrialization` | NeoForge | steel plate, analog circuit, copper cable |
| 7 | `thermal` | **Thermal Series** | `thermal` | *none (no 1.21.1 release)* | steel ingot, RF coil, electrum ingot |
| 8 | `ae2` | **Applied Energistics 2** | `ae2` | NeoForge | quartz vibrant glass, logic processor, fluix crystal |
| 9 | `create` | **Create** (alone) | `create` | NeoForge | iron sheet, electron tube, copper sheet |
| fallback | `vanilla` | (none) | | all | iron, black glass pane, redstone, copper, glowstone, glass pane, gray concrete |

Notes from research (C0, 2026-11-13):
- Mod ids were read from each jar: Power Grid is `powergrid` (0.6.2), Electro Energetics is `electroenergetics`
  (1.1.4). Every item id above was checked against the latest 1.21.1 jar of its mod, except Thermal's.
- Row 3 is **Mekanism** (the request said "Mechanism"; the only exact match is a Paper plugin). Confirm with the
  project owner; swapping it is a one-entry change in `sources.json`.
- **Thermal has no 1.21.1 release.** It stays in the list for ports to versions that have it (e.g. 1.20.1). Its item
  ids are from memory and unverified; if they're wrong the source is skipped with a warning ([§5.1](#51-resolver)).
- **None of these mods have a Fabric build for 1.21.1**, Create included. The Fabric side is fully wired up and
  will pick them up on versions where Fabric builds exist.
- Sources that are Create addons (rows 1, 2, 4) inherit Create's parts for roles they don't override.

The priority order, mod ids and materials all live in one file, [`common/src/compat/sources.json`](../common/src/compat/sources.json).
Reordering is moving one entry. A user-editable order is a [stretch goal](#10-stretch-goals).

---

## 3. Player experience

1. No tech mods: the vanilla crafting recipes, as before.
2. Mekanism only: crafting-table recipes with Mekanism parts.
3. **Create** only: every TV is made in a Create sequenced assembly with Create parts.
4. **Create + Mekanism**: a sequenced assembly that deploys Mekanism parts (steel ingot on the belt, deploy black
   glass panes, a basic control circuit and infused alloy, then press).
5. Create + Power Grid + Mekanism: Power Grid wins (highest priority). One recipe per TV is shown.
6. Not happy? Set `compat.recipeSource` or `compat.createSequencedAssembly` in `config/dreamtvs.json` and run
   `/reload`.
7. The log always says what was picked, for example:
   `Dream TVs recipes: mekanism (sequenced_assembly) [auto, installed: mekanism, create]`.

---

## 4. Config

In `config/dreamtvs.json` (server-side, the same file as [PROJECT_PLAN.md §6](PROJECT_PLAN.md#6-content)):

```json
{
  "compat": {
    "recipeSource": "auto",
    "createSequencedAssembly": true
  }
}
```

| Key | Values | Default | Meaning |
|---|---|---|---|
| `compat.recipeSource` | `"auto"` | `"auto"` | The highest-priority usable source from [§2](#2-supported-sources-and-priority-order), `vanilla` if none. |
| | `"vanilla"` | | Always the vanilla crafting recipes, even with tech mods installed (and no sequenced assembly). |
| | a source id, e.g. `"mekanism"` | | That source's parts. If it can't be used, see below. |
| `compat.createSequencedAssembly` | `true` / `false` | `true` | With Create installed, make TVs in a sequenced assembly. `false` = crafting-table recipes with the same parts. |

**Invalid values.**
- Unknown id (typo) → warning listing the valid ids, then `auto`.
- Valid id but its mods aren't loaded, or its items don't exist → warning, then `auto`. We never enable a recipe whose
  ingredients don't exist.
- Ids are case-insensitive and trimmed. A value that isn't a string, or a non-boolean `createSequencedAssembly`,
  is fixed by the normal config validation (M4) with a warning at server start.
- The config file lists every source id with its mod name in a comment.

**When it applies.** Recipes are data, so the compat options are read when data loads: **world load and `/reload`**.
The rest of the config still applies on server start. The resolver re-reads the file itself (quietly) because
datapacks load before the server-start config load runs.

**Multiplayer.** The server's config decides. Clients get the recipes through normal recipe sync (and need the tech
mods that add the items, as with any modded recipe).

---

## 5. How the recipe is chosen

### 5.1 Resolver

`CompatResolver` (pure Java in `common`, unit-tested) takes the config, the sources in priority order, a
"mod loaded" check and an "item exists" check, and returns one `(source, method)`:

```
resolve(recipeSource, createSequencedAssembly) -> (source, method)
  methods = createSequencedAssembly ? [sequenced_assembly, crafting] : [crafting]
  1. "vanilla"                                         -> (vanilla, crafting)
  2. a known source id: first method it's usable for   -> (that source, method)   else warn, go on
  3. for each method in methods:
       for each source in priority order (not vanilla): if usable -> (source, method)
  4. nothing usable                                    -> (vanilla, crafting)

usable(source, method) = all the method's mods are loaded AND all its items exist in the item registry
```

- **Method first, then source**, so Create installed means "sequenced assembly with the best parts available"
  before falling back to crafting.
- **Item existence is what makes ports safe.** If a mod is installed but an item was renamed or doesn't exist on this
  game version, that source is skipped with a warning naming the missing items, and the next one is used. Tags
  can't be checked this early, so sources use plain item ids.
- Sequenced assemblies also need our `dreamtvs:incomplete_<tv>` items, which only exist with Create installed. If
  they're missing for any reason the resolver falls back to crafting.
- `CompatRecipes` caches the result for one data load and logs it. Each loader clears the cache when a reload starts
  (NeoForge `AddReloadListenerEvent`, Fabric `START_DATA_PACK_RELOAD` and server stop), and editing the config file
  also forces a new choice.

### 5.2 Making the choice reach the recipe system

**Option A from the first draft: conditional JSON with one custom condition**, `dreamtvs:recipe_source`:

```json
{ "type": "dreamtvs:recipe_source", "source": "mekanism", "method": "sequenced_assembly" }
```

- Every TV recipe (vanilla included) carries it, so exactly one recipe per TV loads. Non-selected recipes are never
  parsed, so missing items or a missing `create:sequenced_assembly` recipe type never cause errors.
- **One file serves both loaders.** Each recipe has a `neoforge:conditions` block *and* a `fabric:load_conditions`
  block; each loader ignores the other's key. They also list `mod_loaded` / `all_mods_loaded` for readability.
- Registered as an `ICondition` codec on NeoForge and a `ResourceConditionType` on Fabric, both calling
  `CompatRecipes.isSelected(source, method)`.

### 5.3 Recipe ids

| Recipe | Id |
|---|---|
| Vanilla (unchanged) | `dreamtvs:<tv>` |
| Crafting with a source's parts | `dreamtvs:compat/<source>/crafting/<tv>` |
| Create sequenced assembly | `dreamtvs:compat/<source>/sequenced_assembly/<tv>` |

`<tv>` is `lcd_panel`, `jumbotron`, `crt_tv`, `flat_screen_2x1` or `flat_screen_3x2`. Vanilla ids are unchanged, so
existing datapack overrides, scripts and recipe-book progress keep working.

---

## 6. Recipe design

### 6.1 Roles

Each source maps **roles** to items; each TV's templates use roles. Roles a source doesn't set come from its parent
(Create addons → `create`) and finally from `vanilla`.

| Role | Meaning | Vanilla |
|---|---|---|
| `frame` | Casing / plate material | iron ingot |
| `screen` | Display surface | black stained glass pane |
| `circuit` | The brain | redstone |
| `wire` | Power / wiring | copper ingot |
| `light` | Backlight (Jumbotron) | glowstone dust |
| `tube` | Picture tube (CRT) | glass pane |
| `housing` | CRT body | gray concrete |

### 6.2 Templates

From [`common/src/compat/tvs.json`](../common/src/compat/tvs.json). Crafting keeps the vanilla shapes and yields.

| TV | Crafting (yield) | Sequenced assembly: input → steps (yield) |
|---|---|---|
| LCD Panel | `IGI / GRG / ICI` frame, screen, circuit, wire (4) | frame → deploy screen, screen, circuit, wire → press (4) |
| Jumbotron | `IDI / DRD / IDI` frame, light, circuit (4) | frame → deploy light, circuit, light → press (4) |
| CRT TV | `CCC / GRI / CCC` housing, tube, circuit, frame (1) | housing → deploy tube, circuit, frame (1) |
| Flat Screen (2x1) | `PPP / IRI` screen, frame, circuit (1) | frame → deploy circuit, screen, screen, screen → press (1) |
| Flat Screen (3x2) | `PPP / PPP / IRI` screen, frame, circuit (1) | **a Flat Screen (2x1)** → deploy screen ×3, circuit → press (1) |

- Create picks a sequenced assembly by its input and first step, so no two TVs may share both. The generator checks
  this for every source and fails with a clear message.
- The in-progress item is `dreamtvs:incomplete_<tv>` (registered only with Create installed; its model is the
  finished TV for now, and it has no progress bar since that would need Create's item class).
- Balance (K7) is a first pass: assemblies use fewer frames than crafting, as a reward for automating.

### 6.3 Generation

The recipes are generated, not hand-written:

```
common/src/compat/sources.json   # sources in priority order, mods, materials per role
common/src/compat/tvs.json       # one crafting + one sequenced assembly template per TV
        │  gradlew :common:generateCompatRecipes   (CompatRecipeGenerator, common/src/compatgen)
        ▼
common/src/generated/resources/  # committed, shipped by both loaders
  data/dreamtvs/recipe/<tv>.json                                  (vanilla)
  data/dreamtvs/recipe/compat/<source>/<method>/<tv>.json         (90 compat recipes)
  assets/dreamtvs/models/item/incomplete_<tv>.json
  dreamtvs.compat.json            # runtime index: sources in order, mods and items per method
```

`CompatRecipesUpToDateTest` fails the build if the committed output doesn't match the sources. **Porting:** the
1.21.1 / Create 6 JSON format is confined to two methods in the generator (`crafting`, `sequencedAssembly`); a port
with a different recipe format changes those and regenerates. Mods or items missing on that version need no code
change, the resolver skips them.

### 6.4 Overriding or adding recipes (datapacks)
- Override any generated file by id, like any recipe. Keep the `dreamtvs:recipe_source` condition if it should still
  follow the config.
- To add a source for an unsupported mod, ship a recipe producing the TV with your own conditions, and set
  `compat.recipeSource: "vanilla"` (or override the built-in files) so two recipes aren't active. A cleaner way
  (custom source ids in config) is a [stretch goal](#10-stretch-goals).

---

## 7. Architecture

```
dreamtvs/
├── common/
│   ├── src/compat/{sources,tvs}.json           # hand-written sources (the priority list lives here)
│   ├── src/compatgen/.../CompatRecipeGenerator # Gradle-run generator (Gson only, no Minecraft)
│   ├── src/generated/resources/                # generated recipes, models, index (committed)
│   └── src/main/java/com/dreamtvs/compat/
│       ├── CompatIndex       # reads dreamtvs.compat.json from the jar
│       ├── CompatResolver    # PURE: (config, sources, mods, items) -> (source, method)
│       ├── CompatRecipes     # caches the choice per data load, logs it, answers the condition
│       └── RecipeMethod      # crafting | sequenced_assembly
├── fabric/   RecipeSourceCondition  (ResourceConditionType, registered with ResourceConditions)
└── neoforge/ RecipeSourceCondition  (ICondition codec, DeferredRegister on CONDITION_CODECS)
```

- `DreamTvs.init(configDir, modLoaded)` gets the loader's config folder and `isModLoaded` check, so `common` never
  touches loader APIs.
- **No compile dependency** on any supported mod or on Create. Recipes reference items by id in JSON only. The
  in-progress items are plain `Item`s, registered only when `create` is loaded.

---

## 8. Milestones

### C0: Research
- [x] Read the Power Grid and Electro Energetics mod ids from their jars and check every item id (2026-11-13).
- [ ] Confirm the interpretation of #3 (Mekanism) with the project owner.
- [x] Check loader availability for 1.21.1 (all NeoForge-only; Thermal has no 1.21.1 release).

### C1: Spike
- [x] `dreamtvs:recipe_source` registered on NeoForge and Fabric.
- [x] Config readable when recipes load: the resolver reads the file itself.
- [x] GameTest: without tech mods only the 5 vanilla recipes load; with Create only the 5 Create sequenced
      assemblies load; with `createSequencedAssembly: false` only the 5 Create crafting recipes load.
- [ ] Check in a client that `/reload` after a config edit switches the recipe (code path exists, not yet tried).

### C2: Core
- [x] `CompatIndex`, `CompatResolver`, `CompatRecipes` and config options, with unit tests.
- [x] `generateCompatRecipes` task and up-to-date test.
- [x] Vanilla recipes (ids unchanged) under the same condition.
- [x] Sequenced assembly for every source, with in-progress items.

### C3: Polish
- [ ] Playtest each source for cost parity (K7), especially Power Grid's display modules and circuit boards.
- [ ] Art for the incomplete items (they reuse the finished TV models), and maybe a progress bar.
- [ ] Try each mod in a dev client: JEI/EMI show one recipe per TV and the assembly runs on a belt.
- [ ] Modrinth/CurseForge description: list the mods as *optional* integrations.

### C4: Release
- [ ] Test matrix ([§11](#11-testing)), then ship with the next Dream TVs version.

---

## 9. Risks and open questions

| # | Item | Mitigation / decision |
|---|---|---|
| K1 | Item ids change between mod or game versions | The resolver checks every item exists and skips the source with a warning. No parse errors, no TV without a recipe. |
| K2 | Changing the vanilla recipe ids would break datapacks and scripts | Decided: vanilla keeps `dreamtvs:<tv>`. |
| K3 | Recipe book progress | Unchanged ids keep progress; compat recipes unlock normally. |
| K4 | Config not loaded before data | Solved: the resolver reads the file directly when the first condition is tested. |
| K5 | Fabric has none of these mods on 1.21.1 | Accepted. Fabric is fully wired for versions that do have them. |
| K6 | "Which mod did #3 mean?" | Assumed Mekanism. Needs a yes/no from the project owner. |
| K7 | Balance between sources | First pass only. Tune in `sources.json` / `tvs.json`, regenerate. |
| K8 | Pack author wants none of it | `recipeSource: "vanilla"`. |
| K9 | Another mod's sequenced assembly with the same input and first step | Create would pick one of them. Ours start with distinct steps; cross-mod clashes are unlikely but possible. |
| K10 | Thermal ids are unverified | Harmless: skipped with a warning if wrong. Verify when porting to a version with Thermal. |

---

## 10. Stretch goals
- **User-editable priority order** in config (`compat.priority: [...]`).
- **Custom sources** through config or datapack, recognised by id.
- **`compat.disabledSources: [...]`** to take specific mods out of `auto`.
- **Energy/power integration** (TV draws FE or stress). Real compat, needs its own plan.
- Show the active recipe source in a tooltip or a `/dreamtvs recipe-source` command.
- A progress bar on the incomplete items (Create's own item class would mean a compile dependency; a small
  reflection-based bar would not).

---

## 11. Testing

- **Unit (`common`, `gradlew :common:test`):**
  - `CompatResolverTest`: no mods → vanilla; one tech mod → its crafting; Create → Create assembly; Create + tech mod
    → assembly with its parts; priority regardless of load order; assembly off → crafting; config names a lower
    source; forced source without Create → its crafting; not installed / unknown id / wrong case → auto (+ warning);
    `vanilla` with mods → vanilla; installed mod with missing items → skipped with a warning; missing in-progress
    items → crafting.
  - `CompatRecipesUpToDateTest`: committed files match the generator; one `dreamtvs:recipe_source` per loader block;
    vanilla ids kept; the shipped index parses; clashing assembly starts are rejected.
  - `DreamTvsConfigTest`: the compat section round-trips, defaults, normalisation and bad values.
- **GameTest (`gradlew :neoforge:runGameTestServer`):** `CompatRecipeGameTests` checks that exactly the selected
  source's five recipes load. With no extra mods that's vanilla. To check another source, copy its jars into
  `run/neoforge-gametest/mods` (the screen tests crash with Create there because Create's networking doesn't
  accept GameTest fake players, so run the compat test on its own).
- **Manual matrix:** {NeoForge, Fabric} × {singleplayer, dedicated} × {no mods, Create, Create + 1 mod, 1 mod without
  Create, 2+ mods} × {`auto`, forced other source, `vanilla`, assembly off, bad value}. In each: check the recipe in
  JEI/EMI, make a TV, edit the config, `/reload`, check again.
