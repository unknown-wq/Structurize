<h1 align="center">
  <img src="26.2/src/main/resources/structurize.png" alt="Structurize logo" width="140">
  <br>
  Structurize — Fabric port for Minecraft 26.2
</h1>

<p align="center">
  <b>Copy, paste and place structures in Minecraft 26.2 on the Fabric loader.</b><br>
  Scan any build into a <code>.blueprint</code> file, preview it anywhere, rotate and paste it —
  an unofficial community port of <a href="https://github.com/ldtteam/Structurize">LDTTeam's Structurize</a>
  from NeoForge to Fabric.
</p>

<p align="center">
  <img alt="Minecraft 26.2" src="https://img.shields.io/badge/Minecraft-26.2-brightgreen?style=for-the-badge">
  <img alt="Fabric" src="https://img.shields.io/badge/Loader-Fabric%200.19.3-1976d2?style=for-the-badge">
  <img alt="Fabric API" src="https://img.shields.io/badge/Fabric%20API-0.154.2%2B26.2-1976d2?style=for-the-badge">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-orange?style=for-the-badge">
  <img alt="License GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge">
</p>

<p align="center">
  <a href="#-download">Download</a> ·
  <a href="#-the-other-ports">Other ports</a> ·
  <a href="#-what-this-mod-does">Features</a> ·
  <a href="#-installation">Installation</a> ·
  <a href="#-building-from-source">Build</a> ·
  <a href="#%EF%B8%8F-known-limitations">Limitations</a> ·
  <a href="#-issues-and-bug-reports">Issues</a> ·
  <a href="#-credits-and-license">Credits</a>
</p>

---

## 🧩 The other ports

This repository is one piece of a set. The goal of the whole set is to bring the
**[MineColonies](https://github.com/unknown-wq/minecolonies) mod family to Fabric on Minecraft 26.2**.
Structurize sits in the middle of that stack: it needs the two leaf mods, and MineColonies needs it.

| Mod | Fabric 26.2 port | Original (upstream) | Role in the stack |
|---|---|---|---|
| **BlockUI** | [unknown-wq/BlockUI](https://github.com/unknown-wq/BlockUI) | [ldtteam/BlockUI](https://github.com/ldtteam/BlockUI) | XML-driven GUI framework. A leaf — ported first |
| **Domum Ornamentum** | [unknown-wq/Domum-Ornamentum](https://github.com/unknown-wq/Domum-Ornamentum) | [ldtteam/Domum-Ornamentum](https://github.com/ldtteam/Domum-Ornamentum) | Skinnable decorative blocks. Also a leaf |
| **Structurize** | **you are here** | [ldtteam/Structurize](https://github.com/ldtteam/Structurize) | Blueprint scanning and placement. Needs both leaves |
| **MineColonies** | [unknown-wq/minecolonies](https://github.com/unknown-wq/minecolonies) | [ldtteam/minecolonies](https://github.com/ldtteam/minecolonies) | The colony-building mod itself |

```
BlockUI  ──┐
           ├──> Structurize ──> MineColonies
Domum Ornamentum ──────────────┘
```

> All of these are **ports, not forks with new features**. The gameplay, content and IDs are the
> upstream authors' work; what happens here is the move from **NeoForge to Fabric** and up to
> **Minecraft 26.2**.

The porting notes and tooling behind all of them live in
[unknown-wq/port-kit](https://github.com/unknown-wq/port-kit).

---

## 📦 Download

**The built mod jar lives in [`dist/`](dist/).** Grab it there — no build step required.

```
dist/structurize-26.2-1.0.0.jar
```

Structurize **will not load on its own** — it needs Domum Ornamentum and BlockUI, both of which have
matching 26.2 builds in their own `dist/` folders. See [`dist/README.md`](dist/README.md) for the
checksum and the full list.

---

## 🏗 What this mod does

Structurize is a **builder's toolkit and a library**. It is what MineColonies uses to place its
9,000+ building schematics, and it works standalone as a creative building tool.

- **Scan Tool** — select a region and save it to a `.blueprint` file, entities and block entities
  included
- **Build Tool** — load a blueprint, preview it in the world, move, rotate and mirror it, then paste
- **Shape Tool** — generate spheres, cylinders, domes, pyramids and other shapes from parameters
  instead of placing blocks by hand
- **Structure packs** — blueprints ship in packs, browsable in game and loaded from disk or from
  the server
- **Substitution blocks** — placeholders for "any block", "any solid block" or a tagged material,
  so one blueprint can be built from whatever the player has
- **Undo and redo** for placements, with a scan of the required materials before you build

**Port facts**

| | |
|---|---|
| Minecraft | 26.2 |
| Loader | Fabric Loader 0.19.3+ |
| Requires | Fabric API 0.154.2+26.2, Domum Ornamentum, BlockUI |
| Java | 25 |
| Environment | client **and** dedicated server (both sides) |
| Mod id | `structurize` |
| License | GPL-3.0-only (same as upstream) |

---

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2 into your `mods/` folder.
3. Add the two required mods from their ports:
   [Domum Ornamentum](https://github.com/unknown-wq/Domum-Ornamentum) and
   [BlockUI](https://github.com/unknown-wq/BlockUI) — the jars are in their `dist/` folders.
4. Copy the jar from [`dist/`](dist/) into the same `mods/` folder.
5. Launch. All four mods are needed on both client and server.

---

## 🔨 Building from source

The build targets **Java 25** and uses **Fabric Loom** on **Gradle 9.6.1**.

```sh
./gradle-dist/install.sh                       # installs Gradle 9.6.1 to /opt and OpenJDK 25
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64

mkdir -p 26.2/libs                             # dependency jars are not committed:
                                               #   domum_ornamentum-26.2-1.0.0.jar
                                               #   blockui-0.0.1.jar
cd 26.2
/opt/gradle-9.6.1/bin/gradle build             # jar lands in 26.2/build/libs/
```

Both dependency jars come from the sibling ports' `dist/` folders. Loom 1.17 on 26.2 has no
`modImplementation` — the game is unobfuscated, so there is nothing to remap — and Fabric Loader
finds the mods on the classpath through their `fabric.mod.json`.

Useful tasks: `runClient`, `runServer`, `test`, `validateAccessWidener`, `runDatagen`. Minecraft
26.1+ ships unobfuscated, so the build carries **no mappings line**.

---

## 🧭 How the port was done

Structurize is the largest of the three ports and the only one that had to cross **both axes at
once**: Minecraft 1.21.1 → 26.2 *and* NeoForge → Fabric. The base is upstream's `version/1.21`
branch (NeoForge 21.1.84, Java 21).

- **The GUI was parked and landed last.** 13 files waited for the BlockUI port; when it was ready
  they went in with the XML layouts completely unchanged.
- **A compat layer reconstructed from call sites turned out to be exact.** While BlockUI was still
  in flight, `com.ldtteam.common` was rebuilt from the way Structurize called it. Swapping in the
  real library afterwards took three `sed` substitutions across 36 files and produced four compile
  errors — call sites *are* the contract.
- **AccessTransformer → AccessWidener** — 8 of 13 upstream lines were still live; the dead ones are
  recorded in a comment inside the file, since Loom fails the build on a member that no longer
  exists.
- **Two real bugs fixed on the way**: the blueprint data-version enum stopped at 1.21.1, which made
  the server refuse to start outright, and all six hand-written recipes still used the pre-26.2
  object form for ingredients, so the mod had no working recipes at all.
- **Rendering rebuilt** on 26.2's submit-based pipeline — `VertexBuffer`, `ShaderInstance`,
  `BakedModel` and `ModelData` are all gone.

Status: `build` is green including tests and AccessWidener validation, and a dedicated server boots
a fresh world with no errors. The detailed record is in [`26.2/PORT-STATUS.md`](26.2/PORT-STATUS.md)
and [`26.2/PORT-GAPS.md`](26.2/PORT-GAPS.md).

---

## ⚠️ Known limitations

Some NeoForge-only hooks have no equivalent in Fabric or in vanilla 26.2, and parts of the render
pipeline were rewritten wholesale. Where something was cut, the original was kept in place,
commented and logged rather than deleted.

| Area | What differs from upstream | Impact |
|---|---|---|
| **Preview transparency** | Blend state is a property of an immutable `RenderPipeline` in 26.2 | The blueprint preview renders opaque; the transparency setting does nothing |
| **Fluids in the preview** | The fluid renderer lives inside the section builder, unreachable from a fake level | Water and lava in a blueprint are not drawn |
| **Preview lighting and performance** | The bake-into-`VertexBuffer` path was removed | Flat lighting, and large blueprints may cost FPS — one submit per block per frame |
| **Config** | Inherited from BlockUI: `ModConfigSpec` has no counterpart | Preview settings do not persist or sync; they reset each launch |
| **Tag substitution anchor** | `BlockEntityWithoutLevelRenderer` was removed | The anchor item renders as a flat model in hand and inventory |
| **Capability inventories** | Fabric has no capability API with the same semantics | Containers published only through a mod capability are not counted in "required items"; all vanilla containers are |
| **Rotate keybinds** | 26.2 has no keybind modifiers | `Shift`+`←`/`→` became **`X`** / **`Z`** |
| **Keybind conflict contexts** | NeoForge-only concept | Behaviour is restored in code, but the controls screen still shows blueprint-window keys as conflicting |
| **Client rendering** | Compiles and loads, not visually play-tested end to end | Please report anything that looks wrong |

Each row maps to a `TODO(port-26.2)` marker in the source and to a row in
[`26.2/PORT-GAPS.md`](26.2/PORT-GAPS.md) with a concrete repair plan.

---

## 🐞 Issues and bug reports

**Found a problem? [Open an issue](https://github.com/unknown-wq/Structurize/issues) — please do.**
Bug reports are genuinely welcome; that is how the remaining rough edges get found.

- Report **port bugs here**, not to LDTTeam. Anything caused by the move to Fabric 26.2 is this
  repository's doing, not upstream's.
- Helpful things to include: Minecraft / Fabric Loader / Fabric API versions, the versions of the
  three companion mods, the full log (`logs/latest.log` or the crash report), and the steps that
  reproduce it.
- If the same bug also happens on upstream's NeoForge build, it belongs
  [upstream](https://github.com/ldtteam/Structurize/issues) instead.

---

## 📁 Repository layout

```
.
├── dist/            # ← the built mod jar, ready to drop into mods/
├── 26.2/            # the Fabric 26.2 port — sources, build, port documentation
├── 1.21.1/          # read-only snapshot of upstream version/1.21 (NeoForge 21.1.84) — the port base
├── porting-26.2/    # notes, findings, rename tables and scripts collected while porting
└── gradle-dist/     # vendored Gradle 9.6.1 + toolchain installer
```

The snapshot folder is upstream code, kept verbatim for reference and diffing. It is not edited and
is not part of the build.

---

## 🙏 Credits and license

**Structurize is the work of [LDTTeam (Let's Dev Together)](https://github.com/ldtteam)** — the team
behind MineColonies, BlockUI and Domum Ornamentum. The blueprint format, the tools, the renderer and
every line of game logic in this repository originate with them. All credit for the mod belongs to
its original authors and contributors:

- Upstream source: **[github.com/ldtteam/Structurize](https://github.com/ldtteam/Structurize)**
- CurseForge: [Structurize](https://www.curseforge.com/minecraft/mc-mods/structurize)
- Website: [minecolonies.com](https://minecolonies.com/) · Discord: [LDTTeam](https://discord.gg/Tb3PagMpaG) ·
  support them on [Patreon](https://www.patreon.com/Minecolonies)

This repository is an **unofficial, community-maintained port to the Fabric loader**. It is not
affiliated with, endorsed by or supported by LDTTeam — please do not send them support requests
about this build.

Licensed under **[GPL-3.0-only](26.2/LICENSE)**, the same license as the upstream project, and
distributed under its terms.
