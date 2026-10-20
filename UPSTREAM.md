# Source attribution and scope

Original author: **RecursivePineapple**. Original project:
https://github.com/RecursivePineapple/MatterManipulator

GTNH-maintained fork:
https://github.com/GTNewHorizons/MatterManipulator

The upstream project is licensed under **LGPL-3.0**. LICENSE contains the corresponding license.
The original four Matter Manipulator item textures (itemMatterManipulator0.png
through itemMatterManipulator3.png) are taken from the GTNewHorizons fork
`src/main/resources/assets/matter-manipulator/textures/items` and
renamed to `textures/item/matter_manipulator_0.png` through `_3.png`.
These images retain their upstream LGPL-3.0 license and copyright.
They are not original artwork of this port.

## Compatibility boundaries

The GTNH 1.7.10 Forge mod includes 133 Java source files and implements additional
AE2/GT tile configuration, multiblock uplinks, upgrades, EU charging, item/fluid
providers, microblock integrations, and ModularUI menus.

This port is not a binary/source drop-in replacement and is not feature-complete.
It currently implements server-authoritative block-state geometry and basic
copy/paste operations. The original Java source files have not been copied
into this port. All GTNH-specific integrations still require explicit migration,
their relevant modern APIs, and regression testing.

Do not install the 1.7.10 JAR on 26.3 or use this 26.3 JAR on GTNH 1.7.10.
