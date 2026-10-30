package dev.xt9y.mm;

import net.minecraft.core.BlockPos;

/** Shared paste-space rotations for server placement and the client preview. */
public final class PasteTransform {
    private PasteTransform() {}

    public static BlockPos rotatedMax(BlockPos max, int rotationY, int rotationX) {
        int x = max.getX(), y = max.getY(), z = max.getZ();
        for (int i = 0; i < rotationY % 4; i++) {
            int previousX = x;
            x = z;
            z = previousX;
        }
        for (int i = 0; i < rotationX % 4; i++) {
            int previousY = y;
            y = z;
            z = previousY;
        }
        return new BlockPos(x, y, z);
    }

    public static BlockPos transform(BlockPos offset, BlockPos max,
                                     boolean flipX, boolean flipY, boolean flipZ,
                                     int rotationY, int rotationX) {
        int x = flipX ? max.getX() - offset.getX() : offset.getX();
        int y = flipY ? max.getY() - offset.getY() : offset.getY();
        int z = flipZ ? max.getZ() - offset.getZ() : offset.getZ();
        int maxX = max.getX(), maxZ = max.getZ();
        for (int i = 0; i < rotationY % 4; i++) {
            int previousX = x;
            x = maxZ - z;
            z = previousX;
            int previousMaxX = maxX;
            maxX = maxZ;
            maxZ = previousMaxX;
        }
        int maxY = max.getY();
        for (int i = 0; i < rotationX % 4; i++) {
            int previousY = y;
            y = maxZ - z;
            z = previousY;
            int previousMaxY = maxY;
            maxY = maxZ;
            maxZ = previousMaxY;
        }
        return new BlockPos(x, y, z);
    }
}
