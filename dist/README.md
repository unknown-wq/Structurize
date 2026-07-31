# dist

Compiled build of the Fabric / Minecraft 26.2 port (source in `../26.2/`).

The jar below is a **ready-to-install build** — nothing needs compiling to play, just drop it into
`mods/`. The sources it was built from live in `../26.2/`; the untouched NeoForge 1.21.1 original
it was ported from is in `../1.21.1/`.

| File | `structurize-26.2-1.0.0.jar` |
|---|---|
| Minecraft | 26.2 |
| Loader | Fabric, loader ≥ 0.19.3 |
| Java | 25 |
| Requires | Fabric API 0.154.2+26.2, **Domum Ornamentum**, **BlockUI** |
| Environment | client **and** dedicated server |
| sha256 | `109cf41c15b59d3f1fd249ebe9506009070f3b78d1648b1fbfc1d3ffe8260e37` |

Unlike the other two ports in the set, Structurize has **hard mod dependencies** — it will not load
without them. Take all three jars from the same place:

- [Domum Ornamentum](https://github.com/unknown-wq/Domum-Ornamentum) → `dist/domum_ornamentum-26.2-1.0.0.jar`
- [BlockUI](https://github.com/unknown-wq/BlockUI) → `dist/blockui-26.2-0.0.1.jar`

Install: drop all three jars plus Fabric API into the `mods/` folder of a Fabric 26.2 profile or
server.

## Try it

Craft a **Build Tool**, place a blueprint preview, move and rotate it, then paste it. The **Scan
Tool** saves a region back out to a `.blueprint` file. Structure packs from `structurize/` load as
before — the format did not change in the port.

Note that the rotate keybinds moved: `Shift`+`←`/`→` became **`X`** / **`Z`**, because 26.2 has no
keybind modifiers.

## What changed in the port

This is the only one of the three ports that had to cross **both** axes at once: Minecraft 1.21.1 →
26.2 *and* NeoForge → Fabric.

- **The GUI came last.** 13 files were parked until the BlockUI port was ready, then landed
  unchanged — the XML layouts needed no edits at all.
- **A compat layer reconstructed from call sites matched the real library exactly.** While BlockUI
  was still being ported, `com.ldtteam.common` was rebuilt from how Structurize called it; swapping
  in the real library later took three `sed` substitutions across 36 files and four compile errors.
- **AccessTransformer → AccessWidener** — 8 of the 13 upstream lines survived; the dead ones are
  documented in a comment inside the file.
- **Two real bugs found and fixed on the way**: the data version enum stopped at 1.21.1 and made
  the server refuse to start, and all six hand-written recipes used the pre-26.2 object form for
  ingredients, so the mod had no recipes in game at all.

## Deliberately dropped

Read these before filing a bug — they are decisions, not regressions. The full log is
`../26.2/PORT-GAPS.md`, one row per cut with a repair plan; each row has a matching
`TODO(port-26.2)` marker in the source (37 in total).

- **Blueprint preview is opaque** and **does not render fluids**. Blend state is now a property of
  an immutable `RenderPipeline`, and the fluid renderer is unreachable from a fake level.
- **Preview lighting is flat, and very large blueprints may cost FPS** — the bake-into-`VertexBuffer`
  path is gone, so the preview submits one model per block per frame.
- **Config does not persist or sync.** This is a BlockUI-level limitation (`ModConfigSpec` has no
  counterpart), so preview settings reset on restart. Fixing it in BlockUI fixes it here.
- **The tag substitution anchor renders as a flat model in hand and in inventory** —
  `BlockEntityWithoutLevelRenderer` was removed.
- **Inventories published only through a mod capability are invisible** to the "required items"
  scan; every vanilla container is counted normally.
- **Keybind conflict contexts are gone** — the behaviour is restored in code, but the controls
  screen still shows blueprint-window keys as conflicting.

## Verification status

- `build` green, including `test` and `validateAccessWidener`.
- Dedicated 26.2 server boots on a fresh world, zero `ERROR`/`FATAL` lines.
- Client rendering and GUI compile and load but have **not** been visually play-tested end to end —
  the check list for that is at the end of `../26.2/PORT-GAPS.md`. Please
  [open an issue](https://github.com/unknown-wq/Structurize/issues) if something looks wrong.

Rebuild with:

```sh
../gradle-dist/install.sh     # vendored Gradle 9.6.1 + Java 25
mkdir -p ../26.2/libs         # then drop domum_ornamentum-26.2-1.0.0.jar and blockui-0.0.1.jar in
cd ../26.2
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 /opt/gradle-9.6.1/bin/gradle build --no-daemon
```
