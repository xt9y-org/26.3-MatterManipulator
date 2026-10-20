# Matter Manipulator 26.3 — v1.0.0

First stable-tagged release of the focused Fabric port for **Minecraft 26.3**.

## Included
- **Fill Cube / Geometry:** select a cuboid, then gradually fill it with the offhand block. Empty offhand fills with air.
- **Copy:** automatically capture the complete selection, including exact block states and supported block-entity NBT/inventories.
- **Cut:** capture blocks and NBT before gradually clearing the original region.
- **Paste:** move the orange preview to the desired location, **Use once to lock**, flip X/Y/Z while fixed, and **hold Shift+Use for 0.25 seconds** to build. **Ctrl+V** unlocks the preview for another location.
- Persistent shared selection, blue selection/geometry previews and orange paste previews.
- Uses the player's configured Minecraft **Use Item/Place Block** control.
- No energy system.

## Install
Install the attached `matter-manipulator-26.3-1.0.0.jar` into the `mods` folder **on both the Fabric client and server** alongside Fabric API. Requires **Minecraft 26.3** and **Java 25**. Remove any previous `matter-manipulator` alpha JAR to avoid duplicates.

## Scope and limitations
- This is a stable **versioned release of the four implemented modes**, not complete GTNH Matter Manipulator feature parity.
- Creative-only; clipboard copies inventory data and can duplicate items. Maximum selected region: **32,768 blocks**.
- Chunk loading is required. Clipboard contents are held in server memory and are not saved across server restarts.
- Independent entities (mobs, armor stands, etc.) are not part of the block-entity clipboard.
- Release validation includes Gradle compilation and packaging, but **not comprehensive in-game/world-edit tests**. Test destructive tools in a backup world.

Source, setup, and controls: [README](https://github.com/xt9y-org/26.3-MatterManipulator#readme).
