package dev.xt9y.mm.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import dev.xt9y.mm.PasteTransform;

/** Only selection corners and the clipboard outline are synchronized. */
public final class Preview {
    public static BlockPos a, b, pasteAnchor;
    public static String mode = "fill";
    public static boolean visible = true;
    public static boolean showGuides = true;
    public static int color = 0x31DDFF;
    public static BlockPos clipboardMax = BlockPos.ZERO;
    public static int clipboardCount;
    public static boolean flipX, flipY, flipZ;
    public static int rotationY, rotationX;
    public static boolean cutting = false;
    public static List<BlockPos> clipboard = List.of();
    private static ClientLevel previous;

    public static void tick(Minecraft mc) {
        if (mc.level != previous) {
            previous = mc.level;
            a = b = pasteAnchor = null;
            clipboard = List.of();
            clipboardMax = BlockPos.ZERO;
            clipboardCount = 0;
            flipX = flipY = flipZ = false;
            rotationY = rotationX = 0;
        }
    }
    private static BlockPos parse(String value) {
        if (value.equals("_")) return null;
        String[] v = value.split(",");
        if (v.length != 3) throw new IllegalArgumentException("coordinates");
        return new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
    }
    public static void receiveState(String input) {
        String[] f = input.split("\\|", -1);
        if (f.length < 5) return;
        try {
            mode = f[0].toLowerCase(Locale.ROOT);
            a = parse(f[1]);
            b = parse(f[2]);
            clipboardCount = Integer.parseInt(f[3]);
            cutting = Boolean.parseBoolean(f[4]);
            if (f.length >= 9) {
                flipX = Boolean.parseBoolean(f[5]);
                flipY = Boolean.parseBoolean(f[6]);
                flipZ = Boolean.parseBoolean(f[7]);
                clipboardMax = parse(f[8]);
            }
            pasteAnchor = f.length >= 10 ? parse(f[9]) : null;
            rotationY = f.length >= 11 ? Integer.parseInt(f[10]) : 0;
            rotationX = f.length >= 12 ? Integer.parseInt(f[11]) : 0;
        } catch (RuntimeException ignored) {}
    }
    public static void receiveClipboard(String input) {
        if (input.isEmpty()) { clipboard = List.of(); return; }
        List<BlockPos> result = new ArrayList<>();
        for (String s : input.split(";")) {
            if (result.size() >= 768) break;
            try { result.add(parse(s)); } catch (RuntimeException ignored) {}
        }
        clipboard = List.copyOf(result);
    }
    private Preview() {}
}
