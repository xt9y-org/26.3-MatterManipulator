# Matter Manipulator — Minecraft 26.3

A focused Fabric 26.3 implementation of **Fill Cube, Copy, Paste and Cut**.
There is no GUI, radial menu, energy system or other building modes.

**Requires Minecraft 26.3, Java 25 and Fabric API.**
Install the JAR on both the client and dedicated server.

## Controls

| Control | Action |
|---|---|
| **M** | Cycle Fill Cube → Copy → Paste → Cut |
| **Ctrl+C** | Select Copy mode |
| **Ctrl+X** | Select Cut mode |
| **Ctrl+V** | Select Paste mode |
| **Ctrl+Z** | Clear the region selection |
| **Right-click** | Mark the first and second corners (Fill, Copy or Cut mode) |
| **Hold Shift+RMB for at least 1 second** | Begin the current operation; keep holding to process blocks incrementally |
| **Release Shift or RMB** | Pause the operation; hold again for another second to resume it |

**One selection is shared across modes.** Changing modes with M or Ctrl shortcuts
does not reset the selected corners. Ordinary right-clicking in Paste mode does
not replace the selection; paste uses your crosshair position when you begin holding.
The entire selected volume is inclusive of both corners.

Use `/mm status` to inspect the region or `/mm clear` to reset it manually.

## Modes

**Fill Cube:** Select two corners, then hold Shift+RMB to fill the cube
at 48 positions per server tick. A block in the offhand supplies its default
block state. **An empty offhand means Air**, so it gradually clears the cube.
A nonempty offhand containing a non-block item does not erase anything.

**Copy:** Select two corners in any selection mode, switch to Copy, then hold
Shift+RMB. The source is progressively scanned without editing the world.
All states, air blocks, and complete supported block-entity NBT (including
chest and other inventories) are captured.

**Cut:** Select a region, switch to Cut, then hold Shift+RMB.
The tool first captures every source block with its NBT and only then begins
erasing source blocks progressively. The saved clipboard is retained until
you paste it; once pasted, it is cleared to avoid accidentally repeating the move.

**Paste:** Switch to Paste and look at your destination. Hold Shift+RMB
for at least one second to place the clipboard in batches of 48 positions
per server tick. Looking at a block places next to the targeted face.
Looking into air places five blocks ahead; the paste may overwrite existing blocks.

## Chat and progress

Chat feedback is limited to mode changes, selection changes, errors
and completed operations, for example:

```text
[MM] Copy mode | Region selected
[MM] Cube filled (64 blocks)
[MM] Copied (64 blocks incl. NBT)
```

A transient action-bar message reports the number of blocks processed while
you hold Shift+RMB. The server stops work on release or shortly after heartbeats
stop. Re-holding resumes an unfinished operation in the same mode/selection.

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
- The GitHub workflow checks compilation and publishes prerelease JARs; it does
  not perform an in-game render, block-entity or multiplayer test.

## Install and build

Download the latest successful
[GitHub Release](https://github.com/xt9y/26.3-MatterManipulator/releases).
Give yourself the Matter Manipulator:

```mcfunction
/give @s mattermanipulator:matter_manipulator
```

Build from source with `./gradlew build`.

## Attribution

Original mod: [RecursivePineapple/MatterManipulator](https://github.com/RecursivePineapple/MatterManipulator).
GTNH fork: [GTNewHorizons/MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator).
See [UPSTREAM.md](UPSTREAM.md) and [LICENSE](LICENSE) for item artwork and LGPL-3.0 attribution.
