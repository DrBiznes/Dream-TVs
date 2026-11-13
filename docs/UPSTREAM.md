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

## 5. A per-display option for how the video fits the screen (fill / crop)

**Problem.** The client always fits the whole video inside the display's block box and pads the rest with black
bars. The video texture has the shape of the box (square for a 1×1 display), so a 16:9 video on a 1×1 display is
letterboxed inside a square. An addon whose visible screen isn't the shape of the box can't change that. Our CRT TV
is one block with a 4:3 tube: stretching the square picture onto the tube makes the video about 1.4 times too
wide, with bars on top of that.

**Dream TVs workaround.** `ScreenRendererVideoMixin` injects into the lambda that emits the video quad
(`ScreenRenderer.renderGpuTexture$lambda$0`, a compiler-generated name) and rewrites the texture-coordinate
arguments of the private `ScreenRenderer.addVertex`, so only the centre of the texture is drawn. The crop window is
worked out from `DisplayScreen.videoContentAspect` and the texture size. This is the most fragile hook we have: a
new lambda in that function or a reordered argument breaks it, and it assumes the video is centred in the texture.

**Ask (any one of these):**
- A per-display fit mode, e.g. `ServerDisplay.fit = CONTAIN | COVER | STRETCH` (`CONTAIN` being today's behaviour),
  synced to clients like the other display settings. `COVER` fills the display and cuts off the overflow.
- Or a per-display content aspect, e.g. `ServerDisplay.contentAspect = 4.0 / 3.0`, meaning "the visible screen has
  this shape, fit the video to that instead of to the block box". With `COVER` this is exactly what the CRT needs.
- Or, most general, a per-display source rectangle in texture coordinates (`u0, v0, u1, v1`) that the client
  applies when it draws the video quad.

Any of these would let us delete `ScreenRendererVideoMixin`.
