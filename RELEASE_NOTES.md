# Matter Manipulator 26.3 — v1.0.2

## Changes
- **R in Paste mode:** clockwise 90° Y-axis rotation; **Shift+R:** 90° X-axis rotation. Repeated presses produce 180°, 270° and 360°.
- Rotations are applied to actual block offsets, rendered orange preview bounds and supported block states.
- **Ctrl+Shift+right-click held** in Paste mode: air-only placement. Clipboard air is ignored; existing destination blocks are never overwritten.
- Cut moves with air-only pasting clear only successfully moved original blocks. Skipped source blocks stay intact and remain in the cut clipboard.
- Rotation commands: `/mm rotate x` and `/mm rotate y`.
- All v1.0.1 cut/geometry-mode changes remain.

## Install
Install `matter-manipulator-26.3-1.0.2.jar` on both Fabric client and server with Fabric API.
Requires Minecraft 26.3 and Java 25. Remove older Matter Manipulator JARs.

## Limits
Creative-only; maximum 32,768 block positions. Loaded chunks required.
Y rotation uses vanilla block-state rotation; X rotation handles facing/axis properties
when the block supports the resulting orientation. Horizontally constrained blocks
may not support fully vertical rotation.
Gradle compilation is checked in CI; in-game tests are not automated.
Back up worlds before using destructive edits.

[Documentation](https://github.com/xt9y-org/26.3-MatterManipulator#readme).
