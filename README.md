# Dream TVs

An addon for [Dream Displays](https://github.com/arnodoelinger/dreamdisplays) that adds craftable TV blocks.
Place a TV, open the Dream Displays menu on it, and paste a video link. You don't need commands or operator
permissions.

## Requirements

- Minecraft **1.21.1**, **1.21.11** or **26.3**
- **Fabric** (with Fabric API) or **NeoForge**
- **Dream Displays 1.10.9** or newer, built for the same Minecraft version

Install Dream TVs on both the server and every client.

## The TVs

All TVs are in the **Dream TVs** creative tab. Hover over one and hold **Shift** to read what it does.

| TV | Size | Merges with neighbours |
|---|---|---|
| **LCD Panel** | 1×1 per block | Yes, into any filled rectangle |
| **Jumbotron** | 1×1 per block | Yes, into any filled rectangle, with no bezel |
| **CRT TV** | 1×1 | No, each CRT is its own screen (4:3 picture) |
| **Flat Screen (2x1)** | 2 wide, 1 tall | No |
| **Flat Screen (3x2)** | 3 wide, 2 tall | No |

## Building a screen

1. Place a TV. It faces you, and a single block is already a screen.
2. To make a bigger screen, place more LCD Panels (or Jumbotrons) on the same wall, facing the same way. Once
   they fill a rectangle, they become one screen.
3. Right-click the screen with an empty hand to open the Dream Displays menu and set a video.

Adding a row grows the screen and breaking a block shrinks it. The video link stays in both cases. If a wall
splits in two, the bigger part keeps the video.

A few rules:

- Only filled rectangles count. While a row is half built, the screen keeps its old size.
- LCD Panels and Jumbotrons never merge with each other.
- Flat Screens are placed whole from one item, and breaking any part removes the whole TV.

## Recipes

| TV | Recipe | Makes |
|---|---|---|
| LCD Panel | iron, black glass pane, iron / pane, redstone, pane / iron, copper, iron | 4 |
| Jumbotron | iron, glowstone dust, iron / dust, redstone, dust / iron, dust, iron | 4 |
| CRT TV | 3 gray concrete / glass pane, redstone, iron / 3 gray concrete | 1 |
| Flat Screen (2x1) | 3 black glass panes / iron, redstone, iron | 1 |
| Flat Screen (3x2) | 3 black glass panes / 3 black glass panes / iron, redstone, iron | 1 |

Each line between slashes is one row of the crafting grid. Recipes can be changed with a datapack.

## Mod compatibility

Dream TVs fits into tech modpacks. When one of these mods is installed, the TV recipes use its parts instead of
the vanilla ones:

- **Create**: TVs are made in a Create sequenced assembly
- **Create: Power Grid**, **Create: Electro Energetics** and **Create Crafts & Additions**
- **Mekanism**
- **Immersive Engineering**
- **Modern Industrialization**
- **Applied Energistics 2**
- **Thermal Series** (on Minecraft versions where it's available)

With Create and another tech mod installed, the sequenced assembly uses that mod's parts. If several are installed,
one is picked by priority. All of them are optional, and you can pick a different one or switch back to the vanilla
recipes in the config.

## Permissions

Dream TVs uses Dream Displays' permission nodes. Without LuckPerms, the fallback applies.

| Action | Node | Fallback |
|---|---|---|
| Build a new screen | `dreamdisplays.create` | everyone |
| Grow, shrink or break your own screen | `dreamdisplays.delete` | everyone |
| Grow, shrink, break or merge someone else's screen | `dreamdisplays.delete.others` | op level 2 |
| Go over the per-player screen limit | `dreamdisplays.create.bypass` | op level 2 |

Who can change the video on a screen is set by its owner in the Dream Displays menu.

## Config

The server config is `config/dreamtvs.json`. It's created on first start, and each option has a comment
explaining it. Changes apply when the server restarts, except the `compat` options, which also apply on `/reload`.

| Option | Default | Meaning |
|---|---|---|
| `maxWidth`, `maxHeight` | `32`, `24` | Largest LCD Panel or Jumbotron screen, in blocks (1 to 128) |
| `maxScreensPerPlayer` | `0` | Screens each player may own, `0` for unlimited |
| `extendPolicy` | `"OWNER"` | `"OWNER"`: only the owner or an admin can change a screen. `"ANYONE"`: every player can. |
| `showFormMessages` | `true` | Show "Screen formed" and shape hints on the action bar |
| `compat.recipeSource` | `"auto"` | Which mod's parts the recipes use: `"auto"`, `"vanilla"`, or a mod listed in the file |
| `compat.createSequencedAssembly` | `true` | With Create installed, make TVs in a sequenced assembly instead of a crafting table |

If a value is invalid, Dream TVs fixes it and writes a warning to the server log. If the file can't be read at
all, it's left alone and the defaults are used.

In game, the same options are in a settings screen: the Config button in NeoForge's Mods list, or the Configure
button in [Mod Menu](https://modrinth.com/mod/modmenu) on Fabric. The screen edits the file in your own game folder,
so it's for singleplayer and LAN worlds. Saving while you're in a world applies the changes immediately; recipe
changes need a `/reload`. A dedicated server still uses its own file.

## Building from source

```
gradlew build
```

One source tree builds every Minecraft version and loader with [Stonecutter](https://stonecutter.kikugie.dev/).
Each build has its own Gradle project, named `<minecraft>-<loader>` (`1.21.1-fabric`, `26.3-neoforge`, ...), and
writes its jar to `versions/<minecraft>-<loader>/build/libs`. Gradle needs JDK 25 (it is downloaded if missing).

- `gradlew :1.21.11-neoforge:runClient` (or `runServer`) starts a dev game for one version.
- `gradlew test` runs the unit tests, and `gradlew :26.3-neoforge:runGameTestServer` runs the in-game tests.
- Code that differs between versions sits in `//? if >=1.21.11 { ... //?}` comment blocks. The sources are
  committed for `1.21.1-neoforge`; to work on another version, run the `Set active project to <version>` task,
  and run `Reset active project` before committing. Small API differences live in `McCompat` and `ClientCompat`.
- After editing the compat recipe sources in `src/compat`, run `gradlew :tools:generateCompatRecipes`.

## License

LGPL-3.0
