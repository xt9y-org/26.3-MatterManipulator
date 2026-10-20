package dev.xt9y.mm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import dev.xt9y.mm.MatterManipulatorMod;

/**
 * Uses Minecraft's submit-node render pipeline. Never begins a nested
 * render pass or uploads a StagedVertexBuffer during the active level pass.
 */
public final class SelectionRenderer {
    private Selection state;
    private record Selection(BlockPos a, BlockPos b, int rgb, boolean guides,
                             String mode, java.util.List<BlockPos> clipboard,
                             BlockPos target, boolean cutting, boolean eraseFill,
                             BlockPos clipboardMax, boolean flipX, boolean flipY, boolean flipZ) {}

    public void register() {
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (Preview.visible && (Preview.a != null || !Preview.clipboard.isEmpty()) && mc.player != null
                    && mc.player.getMainHandItem().is(MatterManipulatorMod.TOOL)) {
                BlockPos target = mc.hitResult instanceof BlockHitResult hit
                    ? hit.getBlockPos().relative(hit.getDirection())
                    : BlockPos.containing(mc.player.getEyePosition()
                        .add(mc.player.getLookAngle().scale(5.0)));
                state = new Selection(Preview.a, Preview.b, Preview.color, Preview.showGuides,
                    Preview.mode, Preview.clipboard, target, Preview.cutting,
                    mc.player.getOffhandItem().isEmpty(),
                    Preview.clipboardMax, Preview.flipX, Preview.flipY, Preview.flipZ);
            } else {
                state = null;
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext context) {
        Selection selection = state;
        if (selection == null) return;
        PoseStack pose = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        // The engine owns upload and render-pass lifetime for these vertices.
        // This callback only appends geometry through the supplied consumer.
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(),
            (entry, vertices) -> emitGeometry(selection, entry.pose(), vertices));
        pose.popPose();
    }

    private static void emitGeometry(Selection selection, Matrix4fc matrix, VertexConsumer vertices) {
        if (selection.mode().equals("paste")) {
            pasteBoundingBox(matrix, vertices, selection);
            clipboardPreview(matrix, vertices, selection);
            return;
        }
        if (selection.a() == null) {
            // The player has copied blocks but may not have a selection active.
        } else if (selection.b() == null) {
            BlockPos a = selection.a();
            box(matrix, vertices, a.getX(), a.getY(), a.getZ(),
                a.getX()+1, a.getY()+1, a.getZ()+1, 0x55FF77, 0.22f);
        } else {
            BlockPos a = selection.a(), b = selection.b();
            double x0 = Math.min(a.getX(), b.getX());
            double y0 = Math.min(a.getY(), b.getY());
            double z0 = Math.min(a.getZ(), b.getZ());
            double x1 = Math.max(a.getX(), b.getX()) + 1;
            double y1 = Math.max(a.getY(), b.getY()) + 1;
            double z1 = Math.max(a.getZ(), b.getZ()) + 1;
            double w = 0.027;

            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? x0 : x1;
                double z = (i & 2) == 0 ? z0 : z1;
                box(matrix, vertices, x-w, y0, z-w, x+w, y1, z+w, selection.rgb(), 0.8f);
            }
            for (int i = 0; i < 4; i++) {
                double y = (i & 1) == 0 ? y0 : y1;
                double z = (i & 2) == 0 ? z0 : z1;
                box(matrix, vertices, x0, y-w, z-w, x1, y+w, z+w, selection.rgb(), 0.8f);
            }
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? x0 : x1;
                double y = (i & 2) == 0 ? y0 : y1;
                box(matrix, vertices, x-w, y-w, z0, x+w, y+w, z1, selection.rgb(), 0.8f);
            }
            ghostBlocks(matrix, vertices, selection, x0, y0, z0, x1, y1, z1);
            if (selection.guides()) {
                box(matrix, vertices, a.getX(), a.getY(), a.getZ(),
                    a.getX()+1, a.getY()+1, a.getZ()+1, 0x55FF77, 0.13f);
                box(matrix, vertices, b.getX(), b.getY(), b.getZ(),
                    b.getX()+1, b.getY()+1, b.getZ()+1, 0xFF55EE, 0.13f);
            }
        }
        clipboardPreview(matrix, vertices, selection);
    }

    /** Orange world-space paste bounds, using the FULL clipboard extents, not sampled hints. */
    private static void pasteBoundingBox(Matrix4fc m, VertexConsumer v, Selection selection) {
        if (selection.clipboard().isEmpty() || selection.target() == null) return;
        BlockPos target = selection.target();
        BlockPos dimensions = selection.clipboardMax();
        double x0 = target.getX(), y0 = target.getY(), z0 = target.getZ();
        double x1 = x0 + dimensions.getX() + 1;
        double y1 = y0 + dimensions.getY() + 1;
        double z1 = z0 + dimensions.getZ() + 1;
        double w = 0.037;
        int orange = 0xFF992D;
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? x0 : x1;
            double z = (i & 2) == 0 ? z0 : z1;
            box(m, v, x-w, y0, z-w, x+w, y1, z+w, orange, .90f);
        }
        for (int i = 0; i < 4; i++) {
            double y = (i & 1) == 0 ? y0 : y1;
            double z = (i & 2) == 0 ? z0 : z1;
            box(m, v, x0, y-w, z-w, x1, y+w, z+w, orange, .90f);
        }
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? x0 : x1;
            double y = (i & 2) == 0 ? y0 : y1;
            box(m, v, x-w, y-w, z0, x+w, y+w, z1, orange, .90f);
        }
    }

    /** Preview pasted blocks at the crosshair, with transform and origin compensation. */
    private static void clipboardPreview(Matrix4fc matrix, VertexConsumer vertices, Selection selection) {
        if (selection.target() == null || selection.clipboard().isEmpty()) return;
        String mode = selection.mode();
        if (!mode.equals("paste")) return;
        BlockPos dest = selection.target();
        BlockPos size = selection.clipboardMax();
        int drawn = 0;
        for (BlockPos pos : selection.clipboard()) {
            if (++drawn > 768) return;
            int offsetX = selection.flipX() ? size.getX() - pos.getX() : pos.getX();
            int offsetY = selection.flipY() ? size.getY() - pos.getY() : pos.getY();
            int offsetZ = selection.flipZ() ? size.getZ() - pos.getZ() : pos.getZ();
            double x = dest.getX() + offsetX + .5;
            double y = dest.getY() + offsetY + .5;
            double z = dest.getZ() + offsetZ + .5;
            box(matrix, vertices, x-.24,y-.24,z-.24,x+.24,y+.24,z+.24,0xFFA142,.34f);
        }
        if (selection.cutting() && selection.a() != null && selection.b() != null) {
            // Orange voxels at the source indicate blocks scheduled for removal.
            BlockPos lo = new BlockPos(
                Math.min(selection.a().getX(), selection.b().getX()),
                Math.min(selection.a().getY(), selection.b().getY()),
                Math.min(selection.a().getZ(), selection.b().getZ()));
            for (BlockPos pos : selection.clipboard()) {
                BlockPos p = lo.offset(pos);
                double x = p.getX()+.5, y=p.getY()+.5, z=p.getZ()+.5;
                box(matrix, vertices, x-.24,y-.24,z-.24,x+.24,y+.24,z+.24,0xE8AB4A,.27f);
            }
        }
    }

    /**
     * Per-voxel transparent previews, as used by the original GTNH hint cubes.
     * Bounded to 1024 cubes so large selections do not saturate the frame.
     * Orange = removal/replacement; cyan/blue = placement. UI color controls
     * allow overriding the positive (placement) tint.
     */
    private static void ghostBlocks(Matrix4fc matrix, VertexConsumer vertices,
                                    Selection selection, double x0, double y0, double z0,
                                    double x1, double y1, double z1) {
        String mode = selection.mode();
        if (!mode.equals("fill") && !mode.equals("cut")) return;

        int lenX = (int)(x1-x0), lenY = (int)(y1-y0), lenZ = (int)(z1-z0);
        long total = (long)lenX * lenY * lenZ;
        if (total <= 0 || total > 32768) return;
        // Full-resolution near selections, sampled on large volumes.
        int step = total <= 1024 ? 1 : Math.max(2, (int)Math.ceil(Math.cbrt(total / 1024.0)));
        int drawn = 0;
        boolean warning = mode.equals("cut") || (mode.equals("fill") && selection.eraseFill());
        int color = warning ? 0xF6AB4B : selection.rgb();
        for (int y = 0; y < lenY; y += step) {
            for (int z = 0; z < lenZ; z += step) {
                for (int x = 0; x < lenX; x += step) {
                    if (drawn >= 1024) return;
                    boolean edgeX = x == 0 || x == lenX-1;
                    boolean edgeY = y == 0 || y == lenY-1;
                    boolean edgeZ = z == 0 || z == lenZ-1;
                    boolean inside = switch (mode) {
                        case "hollow" -> edgeX || edgeY || edgeZ;
                        case "walls" -> edgeX || edgeZ;
                        case "sphere" -> {
                            double nx = ((x+0.5)/lenX)*2-1, ny = ((y+0.5)/lenY)*2-1,
                                   nz = ((z+0.5)/lenZ)*2-1;
                            yield nx*nx+ny*ny+nz*nz <= 1;
                        }
                        case "cylinder" -> {
                            double nx = ((x+0.5)/lenX)*2-1, nz = ((z+0.5)/lenZ)*2-1;
                            yield nx*nx+nz*nz <= 1;
                        }
                        case "line", "cables" -> false;
                        default -> true;
                    };
                    if (!inside) continue;
                    // Reproduce the GTNH 0.5-block ghost-cube appearance:
                    // smaller than full-sized blocks, centered in each voxel.
                    double wx = x0 + x + .5, wy = y0 + y + .5, wz = z0 + z + .5;
                    int tint = color;
                    if (!warning) {
                        int boundaryAxes = (edgeX ? 1 : 0) + (edgeY ? 1 : 0) + (edgeZ ? 1 : 0);
                        if (boundaryAxes == 3) tint = 0xF6CE54; // corners
                        else if (boundaryAxes == 2) tint = 0x3EDBDF; // edges
                        else if (boundaryAxes == 1) tint = 0x75C8FF; // faces
                    }
                    double side = 0.23;
                    box(matrix, vertices, wx-side,wy-side,wz-side,wx+side,wy+side,wz+side,tint,0.28f);
                    drawn++;
                }
            }
        }
    }

    private static void box(Matrix4fc m, VertexConsumer v,
                            double ax, double ay, double az, double bx, double by, double bz,
                            int rgb, float alpha) {
        float x0 = (float)ax, y0 = (float)ay, z0 = (float)az;
        float x1 = (float)bx, y1 = (float)by, z1 = (float)bz;
        float r = ((rgb >>> 16) & 255) / 255f;
        float g = ((rgb >>> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        quad(m,v,r,g,b,alpha, x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);
        quad(m,v,r,g,b,alpha, x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0);
        quad(m,v,r,g,b,alpha, x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
        quad(m,v,r,g,b,alpha, x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1);
        quad(m,v,r,g,b,alpha, x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0);
        quad(m,v,r,g,b,alpha, x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
    }

    private static void quad(Matrix4fc m, VertexConsumer v, float r,float g,float b,float a,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3) {
        v.addVertex(m,x0,y0,z0).setColor(r,g,b,a);
        v.addVertex(m,x1,y1,z1).setColor(r,g,b,a);
        v.addVertex(m,x2,y2,z2).setColor(r,g,b,a);
        v.addVertex(m,x3,y3,z3).setColor(r,g,b,a);
    }

}
