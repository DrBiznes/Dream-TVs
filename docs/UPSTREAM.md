# Upstream requests for Dream Displays

Drafts for issues or PRs on [arnodoelinger/dreamdisplays](https://github.com/arnodoelinger/dreamdisplays). Nothing
here has been filed yet. Each one would let Dream TVs drop a workaround that relies on DD internals.

Checked against Dream Displays **1.10.9**.

---

## 1. Let addon-created displays skip the join-time base-material cleanup

**Problem.** `VanillaPlayerListener.onJoin` runs `DisplayManager.validateDisplaysAndCleanup` 40 ticks after a
player joins. It deletes every display whose box holds no base-material block (black concrete and its shaped
variants). `ServerDisplays.create(...)` deliberately skips the base-material check (*"the caller is responsible for
the blocks being a suitable wall"*), but this cleanup applies it anyway. So any addon whose screens sit on other
blocks loses them, links included, the next time anyone joins.

**Dream TVs workaround.** A `@Redirect` into `validateDisplaysAndCleanup` (`DisplayManagerMixin`) that keeps
displays tagged `dreamtvs:screen` out of the removal list.

**Ask (any one of these):**
- Skip displays created through the API in that cleanup, or
- a per-display flag, e.g. `ServerDisplay.surfaceCheck = false`, or
- a veto that runs for it, e.g. `DisplayVeto.Cleanup(display)` or making the cleanup go through `DisplayVeto.Delete`.

## 2. Opt out of block protection for addon-managed displays

**Problem.** `VanillaProtectionListener.handleBreak` cancels every block break inside a display box. Addons whose
blocks *are* the display need those blocks to stay breakable, with their own permission rules.

**Dream TVs workaround.** A break hook that runs before DD's (Fabric: an earlier `PlayerBlockBreakEvents.BEFORE`
phase; NeoForge: `BlockEvent.BreakEvent` at `HIGHEST`) and shrinks the display out of the way first.

**Ask:** a per-display protection setting (e.g. `ServerDisplay.protection = NONE | BREAK_ONLY | FULL`), or a
`DisplayVeto.Protect(display, pos, actor)` hook that addons can allow.

## 3. Expose a display's box and facing on `ServerDisplay`

**Problem.** `ServerDisplay` gives `x/y/z`, `width` and `height` but not its corners or facing. After another addon
moves a display (e.g. dreamdisplays-sable carrying it onto a sub-level), there's no API way to tell where it went.

**Dream TVs workaround.** We store our own copy in `customData`, and after outside moves we read
`VanillaDisplayData` (`minX..maxZ`, `facing`, `worldKey`) through `DisplayManager.getDisplayData` (`DdInternals`).

**Ask:** `ServerDisplay.getMin()/getMax()` (or `getBounds()`) and `getFacing()`. A `ServerDisplayEvent.Moved` that
carries the old and new bounds would be even better.

## 4. Publish `dreamdisplays-api` to a Maven repository

**Problem.** Only the full mod jars are published (GitHub, Modrinth, CurseForge). Addons compile against the
Modrinth Maven mod jar, which works but pulls in every internal class.

**Ask:** publish the `dreamdisplays-api` artifact (Maven Central, or a GitHub Packages / Modrinth Maven entry).
