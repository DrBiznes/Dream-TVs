# Dream TVs — Tech Mod Compatibility Update (Recipes)

> A future compatibility update for [Dream TVs](PROJECT_PLAN.md). When a supported technology mod is installed, the
> TV block gets a **crafting recipe built from that mod's items**. If several supported mods are loaded, a fixed
> **priority list** picks the recipe from the highest-priority one. A **config option** lets players override that and
> use the recipe from a different mod (or the vanilla one).

| | |
|---|---|
| **Status** | Planned. Not started. Depends on M2/M4 of [PROJECT_PLAN.md](PROJECT_PLAN.md) (config file exists, block is stable). |
| **Target version** | Dream TVs `0.x` compat release, after the 1.21.1 release (M5) |
| **Scope** | Recipes only. No new blocks, items or Dream Displays changes. |
| **Loaders** | Fabric and NeoForge (not every supported mod exists on both, see [§2](#2-supported-mods-and-priority-order)) |

---

## 1. Goals and non-goals

### Goals
- If a supported tech mod is present, the TV is craftable with that mod's materials, so it fits modpack progression.
- Several mods loaded at once: exactly **one** TV recipe is active, chosen by the priority list.
- A server-side config option picks a different source, so a pack author can choose "use the Mekanism recipe even
  though Power Grid is installed".
- No supported mod installed: the existing vanilla recipe ([PROJECT_PLAN.md §6](PROJECT_PLAN.md#6-content)) still works.
- No hard dependency on any of these mods. Dream TVs must load fine without all of them.

### Non-goals
- Compat *behavior* (powering the TV with FE, Create stress, energy use). Possible later, see [§10](#10-stretch-goals).
- Changing the vanilla recipe's yield or layout.
- Per-mod item/block content from our side (no new textures or models).
- Supporting every tech mod. The list is fixed per release and datapack authors can add their own ([§6.3](#63-adding-your-own-variant-datapacks)).

---

## 2. Supported mods and priority order

Higher in the table = higher priority. The first loaded mod wins in `auto` mode.

| # | Variant id | Mod | Mod ID (for `mod_loaded`) | Loaders (1.21.1) | Notes |
|---|---|---|---|---|---|
| 1 | `power_grid` | **Create: Power Grid** | *TBD, read from the jar* | Fabric, NeoForge | Physics-based electricity. Has "Modular Displays" and circuit parts we may reuse. |
| 2 | `electro_energetics` | **Create: Electro Energetics** | *TBD, read from the jar* | NeoForge | Realistic-ish electricity addon (capacitors, diodes, relays, transformers). |
| 3 | `mekanism` | **Mekanism** | `mekanism` | NeoForge | Most popular tech mod. See the naming note below. |
| 4 | `createaddition` | **Create: Crafts & Additions** | `createaddition` | NeoForge | Electricity for Create (capacitors, copper wire, motors). |
| 5 | `immersiveengineering` | **Immersive Engineering** | `immersiveengineering` | NeoForge | Wires, electronic components, steel plates. |
| 6 | `modern_industrialization` | **Modern Industrialization** | `modern_industrialization` | Fabric, NeoForge | Gives Fabric players a tech option. |
| 7 | `thermal` | **Thermal Series** | `thermal` | NeoForge | RF coils, electrum. |
| 8 | `ae2` | **Applied Energistics 2** | `ae2` | Fabric, NeoForge | Logic processors, vibrant glass. Good "screen" ingredients. |
| fallback | `vanilla` | (none) | | | The current recipe. Used when no supported mod is loaded. |

> ⚠️ **Naming note.** The request listed "Mechanism" as #3. The only exact match found on Modrinth is a *Paper
> plugin* (not a mod), so this plan assumes **Mekanism** was meant. If a different mod was intended, swap row 3. The
> rest of the plan doesn't change.
>
> **Mod IDs for rows 1 and 2** weren't published on their Modrinth/GitHub pages. Read them from each jar's
> `neoforge.mods.toml` / `fabric.mod.json` before writing the conditions (task C0).
>
> **Why these 5 extra mods:** popularity and a spread of play styles: Create-ecosystem electricity (Crafts &
> Additions), classic tech (Immersive Engineering, Thermal), a Fabric-friendly tech mod (Modern Industrialization) and
> the most-installed storage/logic mod (AE2).

Priority is **hard-coded in order** (the table above) and lives in one place in code (`CompatVariants`), so reordering is
a one-line change. A user-editable order is a [stretch goal](#10-stretch-goals).

---

## 3. Player experience

1. Install Dream TVs with no tech mods: the vanilla recipe works as today.
2. Install Mekanism: the TV recipe changes to the Mekanism one. Recipe book, JEI/REI/EMI show only that recipe.
3. Install Mekanism **and** Create: Power Grid: Power Grid wins (higher priority). Only one recipe is shown.
4. Not happy? Set `compat.recipeSource` to `"mekanism"` in `config/dreamtvs.json`, then run `/reload` (or restart).
   Now the Mekanism recipe is the active one.
5. A log line at startup always says what happened, for example:
   `Dream TVs recipe source: mekanism (auto: highest priority of [power_grid, mekanism])`.

---

## 4. Config

Added to `config/dreamtvs.json` (server-side, same file as in [PROJECT_PLAN.md §6](PROJECT_PLAN.md#6-content)):

```json
{
  "compat": {
    "recipeSource": "auto"
  }
}
```

| Key | Values | Default | Meaning |
|---|---|---|---|
| `compat.recipeSource` | `"auto"` | `"auto"` | Pick the highest-priority **loaded** mod from [§2](#2-supported-mods-and-priority-order). `vanilla` if none. |
| | `"vanilla"` | | Always use the vanilla recipe, even if tech mods are installed. |
| | a variant id, e.g. `"mekanism"` | | Use that mod's recipe. Must be loaded, otherwise see below. |

**Invalid values.**
- Unknown id (typo) → log a warning listing the valid ids, behave as `auto`.
- Valid id but that mod isn't loaded → log a warning, behave as `auto`. We never enable a recipe whose ingredients
  don't exist.
- The resolved choice is always logged once per reload.

**When it applies.** Recipes are datapack data, so the config is read when data loads: **world load, `/reload`, or
server restart**. Changing the file while running does nothing until a reload. Validation of the value belongs with the
rest of the config validation (M4).

**Multiplayer.** The server's config decides. Clients get the recipe through normal recipe sync and need no config
(clients still need the tech mods that add the items, as with any modded recipe).

---

## 5. How the recipe is chosen

### 5.1 Resolver (pure, in `common`)

```
CompatResolver.resolve(configValue, loadedModIds) -> Variant
  1. if configValue == "vanilla"                      -> VANILLA
  2. if configValue names a known, loaded variant     -> that variant
  3. otherwise (auto, unknown, or not loaded)         -> first variant in PRIORITY whose mod is loaded
  4. none loaded                                      -> VANILLA
```

- No Minecraft imports. Takes a `Set<String>` of loaded mod ids, so it is **unit-testable** without a game.
- The result is computed once per data load and cached. Both loaders ask it the same question, so behavior is identical.

### 5.2 Making the choice reach the recipe system

The challenge is that recipe JSON can't read our config by itself. Options:

| Option | How | Pros | Cons |
|---|---|---|---|
| **A. Conditional JSON + custom condition** *(recommended)* | One recipe file per variant in the datapack. Each has a load condition: `mod_loaded(X)` **and** `dreamtvs:variant_selected(X)`. Our custom condition asks `CompatResolver`. | Uses each loader's supported mechanism. Datapack-friendly, so players can read and override recipes. Disabled recipes are never parsed, so missing items don't error. Viewers (JEI/EMI) just see one recipe. | The condition key differs per loader (`neoforge:conditions` vs `fabric:load_conditions`), so recipe JSON can't be shared as is. Needs a custom condition registered on each loader. |
| B. Mixin on `RecipeManager` | Build or filter variant recipes in code after load. | One code path, no per-loader JSON. | Brittle across MC versions, hidden from datapack authors. |
| C. One recipe type that checks at craft time | Custom serializer; `matches()` returns false when inactive. | Loader-neutral. | Inactive recipes still show in recipe book and JEI/EMI. Bad UX. |

**Decision: Option A.** To avoid keeping two copies of every recipe by hand:

- Keep the loader-neutral source of truth in `common/src/compat/variants/<variant>.json`, containing only the shaped
  recipe (pattern, keys, result).
- A small Gradle task (`generateCompatRecipes`) writes the per-loader files, adding the right condition block:
  - NeoForge → `data/dreamtvs/recipe/compat/<variant>.json` with `neoforge:conditions`.
  - Fabric → same path with `fabric:load_conditions` (Fabric API *resource conditions*).
- Custom condition `dreamtvs:variant_selected { "variant": "<id>" }`, registered once per loader, both calling
  `CompatResolver`.
- The vanilla recipe stays at `data/dreamtvs/recipe/tv.json` (same id) and gets the same treatment
  (`variant_selected: vanilla`), so exactly one of the nine recipes is ever active.

> The config must be loaded **before** datapack recipes are read. Our config is a plain JSON file read at mod init
> (not a `ModConfigSpec`), so load order isn't a problem. Verify this in the spike (C1).

### 5.3 Only one TV recipe ever exists
All variants produce `dreamtvs:tv`. Because non-selected variants are skipped at load time, the recipe book, JEI, REI
and EMI never show duplicates, and `/recipe give` only grants the active one. Compat recipe ids are
`dreamtvs:compat/<variant>`. The vanilla recipe **keeps its original id** `dreamtvs:tv`, so existing datapack
overrides, scripts and recipe-book progress keep working (see [Risks](#9-risks-and-open-questions) K2/K3).

---

## 6. Recipe design

### 6.1 Rules for every variant
- **Same shape and yield as vanilla** (`IGI / GRG / ICI`, makes 4), so screens cost about the same everywhere and
  docs/screenshots stay valid. Only the ingredients change.
- Ingredients play the same **roles**:

  | Slot | Role | Typical source |
  |---|---|---|
  | `I` | Frame / casing | The mod's plate or casing material |
  | `G` | Screen | Glass or display part (black glass pane if the mod has no equivalent) |
  | `R` | Brain | The mod's circuit / logic component |
  | `C` | Power / wiring | The mod's wire, capacitor or conductor |

- **Prefer common tags** (`c:ingots/steel`, `c:plates/steel`, etc.) over mod-specific items when a tag exists, so
  small item renames and other mods adding the same material don't break the recipe.
- Early-to-mid-game cost: reachable once the player has the mod's first circuit tier. Not end-game.
- Every variant recipe is a normal datapack file. Players can override it with their own datapack.

### 6.2 Draft ingredients (to be verified in-game)

> All item ids below are **drafts** from memory of each mod's content. Each one must be checked against the real mod
> version during task C2, before it ships. Rows 1 and 2 need the jars opened first.

| Variant | `I` frame | `G` screen | `R` brain | `C` power | Notes |
|---|---|---|---|---|---|
| `power_grid` | TBD | TBD (look at *Modular Displays*) | TBD (circuit board / transistor) | TBD (conductive part) | Needs jar inspection |
| `electro_energetics` | TBD | black glass pane | TBD (capacitor / diode / relay) | TBD (conductor) | Needs jar inspection |
| `mekanism` | `mekanism:ingot_steel` | black glass pane | `mekanism:basic_control_circuit` | `mekanism:alloy_infused` | |
| `createaddition` | `create:iron_sheet` | black glass pane | `createaddition:capacitor` | `createaddition:copper_wire` | |
| `immersiveengineering` | `immersiveengineering:plate_steel` | black glass pane | `immersiveengineering:component_electronic` | `immersiveengineering:wire_copper` | |
| `modern_industrialization` | steel plate (use `c:` tag) | black glass pane | electronic circuit (use `c:` tag) | copper cable (use `c:` tag) | Check tag availability first |
| `thermal` | `thermal:steel_ingot` | black glass pane | `thermal:rf_coil` | `thermal:electrum_ingot` | |
| `ae2` | `minecraft:iron_ingot` | `ae2:quartz_vibrant_glass` | `ae2:logic_processor` | `ae2:fluix_crystal` | |
| `vanilla` | `minecraft:iron_ingot` | `minecraft:black_stained_glass_pane` | `minecraft:redstone` | `minecraft:copper_ingot` | Existing recipe |

### 6.3 Adding your own variant (datapacks)
Pack authors can add a recipe for a mod we don't support by shipping a normal recipe that produces `dreamtvs:tv` with
their own conditions. Document in the README that if they add one, they should also disable the matching built-in
variant with `compat.recipeSource` or by overriding its file, otherwise two recipes can be active. A clean version of
this (custom variants ids accepted by the config) is a [stretch goal](#10-stretch-goals).

---

## 7. Architecture

```
dreamtvs/
├── common/
│   ├── compat/CompatVariants        # ordered list: id, mod id, display name (the priority list lives here)
│   ├── compat/CompatResolver        # PURE: (config value, loaded mods) -> Variant (unit-tested)
│   ├── compat/CompatConfig          # reads/validates compat.recipeSource, logs warnings
│   └── src/compat/variants/*.json   # loader-neutral recipe sources, one per variant
├── fabric/
│   └── VariantSelectedCondition     # registered with Fabric API ResourceConditions
│                                    # + generated data/dreamtvs/recipe/compat/*.json (fabric:load_conditions)
└── neoforge/
    └── VariantSelectedCondition     # registered as an ICondition codec
                                     # + generated data/dreamtvs/recipe/compat/*.json (neoforge:conditions)
```

- **Fabric** needs Fabric API's resource-conditions module (already a dependency of the Fabric build).
- **Mod loaded check** comes from `FabricLoader.isModLoaded` / `ModList.isLoaded`, passed into the resolver as a
  `Set<String>` so `common` never touches loader APIs.
- **No compile dependency** on any supported mod. Recipes reference items by id in JSON only, so nothing in code
  imports their classes.

---

## 8. Milestones

### C0: Research
- [ ] Open the Power Grid and Electro Energetics jars and record their mod ids and relevant items.
- [ ] Confirm the interpretation of #3 (Mekanism) with the project owner.
- [ ] Confirm each supported mod's loader availability for our target Minecraft version.

### C1: Spike (reduce risk first)
- [ ] Register `dreamtvs:variant_selected` on NeoForge and Fabric and make one conditional recipe appear or disappear
      based on a hard-coded value.
- [ ] Confirm the config is readable by the time recipes load, in singleplayer and on a dedicated server.
- [ ] Confirm that `/reload` after a config edit switches the recipe.

### C2: Core
- [ ] `CompatVariants`, `CompatResolver` and `CompatConfig` with unit tests ([§11](#11-testing)).
- [ ] `generateCompatRecipes` Gradle task producing both loaders' recipe JSON.
- [ ] Put the vanilla recipe (id unchanged) under the variant system.
- [ ] Draft and verify ingredients for all eight variants in-game ([§6.2](#62-draft-ingredients-to-be-verified-in-game)).

### C3: Polish
- [ ] Startup log line, warnings for bad config values, README section with the priority table and config example.
- [ ] Lang/tooltip: nothing needed unless we decide to show the active source somewhere (optional).
- [ ] Modrinth/CurseForge description: add a "Compatibility" section; list the mods as *optional* integrations.

### C4: Release
- [ ] Test matrix ([§11](#11-testing)), then ship with the next Dream TVs version.

---

## 9. Risks and open questions

| # | Item | Mitigation / decision needed |
|---|---|---|
| K1 | Item ids and tags in tech mods change between versions | Prefer `c:` tags. Conditions only check that the mod is *loaded*, so a renamed item would produce a recipe parse error. Consider also guarding with `item_exists`-style conditions where the loader offers them, and test against each mod's latest 1.21.1 build. |
| K2 | Changing the vanilla recipe id would break datapacks or KubeJS scripts that reference it | Decided: vanilla keeps its original id `dreamtvs:tv`. Only the compat recipes are namespaced under `compat/`. |
| K3 | Recipe book progress: players who unlocked the old recipe | Unchanged ids keep progress (follows from K2). Compat recipes are new ids and unlock normally. |
| K4 | Config isn't loaded before data on some loader/path | C1 spike. Fall back to reading the file directly in the condition. |
| K5 | Fabric only has some of the supported mods | Accepted: the list is per-loader in practice. On Fabric the usable variants are Power Grid, Modern Industrialization, AE2 and `vanilla`. |
| K6 | "Which mod did #3 mean?" | Needs a yes/no from the project owner. Assumed Mekanism. |
| K7 | Balance: some mods' items are far cheaper or more expensive than others | Playtest each variant for approximate parity with vanilla's cost. Tune in the recipe JSON only. |
| K8 | Two supported mods in a pack where the pack author wants *neither* | `recipeSource: "vanilla"` covers it. |

---

## 10. Stretch goals
- **User-editable priority order** in config (`compat.priority: ["mekanism", "power_grid", ...]`) instead of the fixed
  list.
- **Custom variants through config or datapack** that the resolver recognises by id, without code changes.
- **Optional hard override** `compat.disableVariants: [...]` to take specific mods out of `auto` selection.
- **Energy/power integration** (TV draws FE or stress, needs power to display). That is real compat, not just recipes,
  and would need its own plan.
- Show the active recipe source in a TV item tooltip or a `/dreamtvs recipe-source` command.

---

## 11. Testing

- **Unit (`common`):** `CompatResolver` for every path:
  - no mods loaded → `vanilla`
  - one mod loaded → that mod
  - several loaded → highest priority wins, regardless of load order
  - config names a loaded mod → that mod, even if it is lower priority
  - config names a mod that isn't loaded → falls back to `auto` + warning
  - config has an unknown id or wrong case → falls back to `auto` + warning
  - config `vanilla` with mods loaded → `vanilla`
- **Datagen/Gradle check:** the generated files for both loaders exist, parse, and contain exactly one condition block.
- **Manual matrix:** {Fabric, NeoForge} × {singleplayer, dedicated} × {no mods, 1 mod, 2+ mods} × {`auto`, forced
  other mod, forced `vanilla`, bad value}. In each: check the active recipe in the recipe book / JEI, craft a TV, edit
  the config, `/reload`, check again.
- **Pack sanity:** a pack containing all eight mods must still log one clear "recipe source" line and show exactly one
  TV recipe.
