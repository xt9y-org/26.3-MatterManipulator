# Matter Manipulator 26.3 — v1.0.3

## Changes
- Copy and Cut now capture their entire selected structures immediately, in the same server action, with full block states and NBT.
- Paste now places the whole clipboard in one server operation, including any Cut-source cleanup, rather than 48 blocks per tick.
- Shift+right-click in Paste mode applies immediately, without a 0.25-second hold. Ctrl+Shift+right-click applies air-only paste immediately.
- Fill Cube remains progressive and resumable at 48 blocks per tick.
- Existing 90° X/Y rotations, flips, paste previews, and air-only Cut safety remain.

## Install
Install `matter-manipulator-26.3-1.0.3.jar` on both Fabric client and server with Fabric API.
Requires Minecraft 26.3 and Java 25. Remove previous Matter Manipulator versions.

## Limits
Creative-only; 32,768 block positions maximum; all affected chunks must remain loaded.
Instant edits run synchronously on the server, so large structures can cause a temporary
tick spike or noticeable pause. No in-game tests are automated. Back up worlds before
destructive world edits.

[Documentation](https://github.com/xt9y-org/26.3-MatterManipulator#readme).
