# Matter Manipulator 26.3 — v1.0.1

## Changes
- **Cut now moves:** Ctrl+X automatically captures the selection without erasing it. After pasting all blocks and NBT, the source is cleared in batches. Overlapping paste destinations are protected from deletion.
- **Geometry controls:** M selects Fill Cube; holding M and scrolling stays within the geometry wheel (currently only Fill Cube) without moving the hotbar. Ctrl+G opens the same Geometry/Fill Cube mode.
- **Copy/Paste/Cut shortcuts:** Ctrl+C, Ctrl+V and Ctrl+X remain available independently of Geometry mode.
- The cut clipboard is cleared only when paste and source cleanup both finish.

## Install
Install `matter-manipulator-26.3-1.0.1.jar` on **both** Fabric client and server with Fabric API.
Requires Minecraft 26.3 and Java 25. Remove older Matter Manipulator JARs.

## Limits
Creative-only; maximum 32,768 blocks. Cut source and paste destination chunks must remain loaded.
Moves are resumable while Shift+Use is held again, but changing modes cancels partially
completed work. Test destructive edits on a backup world. Builds are compile-checked,
not comprehensively tested in-game.

See the [README](https://github.com/xt9y-org/26.3-MatterManipulator#readme) for controls.
