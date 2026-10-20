# Matter Manipulator — Minecraft 26.3 (v1.0.0)

The **v1.0.0** release is a focused Fabric 26.3 implementation of **Fill Cube, Copy, Paste and Cut**.
There is no GUI, radial menu, energy system or other building modes.

**Requires Minecraft 26.3, Java 25 and Fabric API.**
Install the JAR on both the client and dedicated server.

## Controls

| Input | Behavior |
|---|---|
| **Use** (click a block) | Select the position **adjacent to the clicked face**, as in GTNH |
| **Short Shift+Use** (release before 0.25 s) | Select the **actual targeted block** instead of the adjacent position |
| **Hold Shift+Use for 0.25 seconds or longer** | Begin the current operation in batches; do not change selection |
| **Release Shift or Use** | Pause; hold again to resume the same queued job |
| **M** | Cycle Fill Cube → Copy → Paste → Cut without resetting the region |
| **Ctrl+C / Ctrl+X / Ctrl+V** | Switch to Copy / Cut / Paste |
| **Ctrl+Z** | Clear the current selection |
| **Use once in Paste mode** | Lock the destination at the current orange preview position |
| **Ctrl+V while already in Paste mode** | Unlock the destination to reposition the orange preview |
| **X / Y / Z** (Paste mode, without Ctrl) | Toggle flips around the fixed destination |

One selection is shared across all modes. The **preview is always
visible when the tool is held**: blue in Fill, Copy and Cut, and orange in Paste.

- Before selecting: highlight the exact block that the next Use click would select
  (adjacent face, or the actual clicked block when holding Shift).
- After the first corner: show the growing blue cube between that corner and the crosshair.
- After the second corner: show the whole blue selected shape, with sampled translucent
  blue block hints.
- In Paste mode: show the destination bounds and the **actual non-air clipboard
  shape** in orange. One Use click locks that orange structure in the world,
  so moving your crosshair no longer moves it. X/Y/Z flip around the locked
  anchor. Ctrl+V releases the lock to choose a new position. Air positions
  are still included in the real paste; only the preview excludes them.

The colored ghosts represent block occupancy rather than textured Minecraft
block models. Supported block-state orientations and full block-entity NBT are
preserved by the actual paste.

Paste may be positioned beside an existing block face or five blocks into
empty air. Holding Shift+Use does not reselect corners. The offhand can be
empty in Fill Cube mode to progressively fill with air.

Use `/mm status` to see the selection, `/mm clear` to reset it, or
`/mm flip x`, `/mm flip y`, `/mm flip z` to toggle the same paste flips
through commands.

## Modes

**Fill Cube:** Select two corners, then hold Shift+Use to fill the cube
at 48 positions per server tick. A block in the offhand supplies its default
block state. **An empty offhand means Air**, so it gradually clears the cube.
A nonempty offhand containing a non-block item does not erase anything.

**Copy:** Select two corners in Copy mode, or switch to Copy with an existing
completed selection. Capturing starts automatically, without Shift+Use, and
progressively scans the region without editing the world.
All states, air blocks, and complete supported block-entity NBT (including
chest and other inventories) are captured.

**Cut:** Select a region, switch to Cut, then hold Shift+Use.
The tool first captures every source block with its NBT and only then begins
erasing source blocks progressively. The saved clipboard is retained until
you paste it; once pasted, it is cleared to avoid accidentally repeating the move.

**Paste:** Switch to Paste and look at your destination. Use once to lock
the orange preview in place (adjacent to a clicked face, or five blocks in
front when aiming into air). Flip using X/Y/Z without moving the anchor.
Hold Shift+Use for at least 0.25 seconds to build at the **locked** location,
in batches of 48 positions per server tick. Press Ctrl+V to unlock and
select a new location, retaining the clipboard and flips. A paste may overwrite
existing blocks.

## Chat and progress

Chat feedback is limited to mode changes, selection changes, errors
and completed operations, for example:

```text
[MM] Copy mode | Region selected
[MM] Cube filled (64 blocks)
[MM] Copied (64 blocks incl. NBT)
```

A transient action-bar message reports the number of blocks processed while
you hold Shift+Use. The server stops work on release or shortly after network heartbeats stop. Re-holding resumes an unfinished operation in the same mode/selection.

## Limits and safety

- Creative-only: copying full chest inventories also duplicates their items.
- Maximum region: **32,768 block positions**.
- Block states and block-entity NBT are copied, **not** independent entities
  such as mobs, armor stands, item frames or dropped items.
- All source and destination chunks must be loaded; the clipboard is in memory
  and disappears when the server restarts.
- Changing mode or selection during a partially finished edit cancels the queued
  operation, **not** blocks already edited. Back up your test world.
- No region claim or anti-grief plugin integration. Do not give the tool to
  untrusted players on shared servers.
- The GitHub workflow checks compilation and publishes tagged stable builds; it does
  not perform an in-game render, block-entity or multiplayer test.

## Install and build

Download the **[v1.0.0 stable release](https://github.com/xt9y-org/26.3-MatterManipulator/releases/tag/v1.0.0)** and install its `matter-manipulator-26.3-1.0.0.jar`. Remove older alpha versions from the mods folder.
Give yourself the Matter Manipulator:

```mcfunction
/give @s mattermanipulator:matter_manipulator
```

Build from source with `./gradlew build`.

## Attribution

Original mod: [RecursivePineapple/MatterManipulator](https://github.com/RecursivePineapple/MatterManipulator).
GTNH fork: [GTNewHorizons/MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator).
See [UPSTREAM.md](UPSTREAM.md) and [LICENSE](LICENSE) for item artwork and LGPL-3.0 attribution.

## Troubleshooting

If an operation cannot start, check the server chat for `[MM] Cannot start:`
messages. Fill/Copy/Cut require two corners; Paste requires a completed Copy or Cut.
Build operations use the configured Minecraft Use key and dedicated Fabric network
packets, so the mod must be installed on **both client and server**. Quick ordinary
Use clicks are handled by Minecraft's server-side item-use event rather than
20 Hz input polling.
