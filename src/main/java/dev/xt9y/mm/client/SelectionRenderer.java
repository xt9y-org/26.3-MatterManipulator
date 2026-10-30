package dev.xt9y.mm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import dev.xt9y.mm.MatterManipulatorMod;
import dev.xt9y.mm.PasteTransform;

/**
 * Always-visible mode-colored prediction when holding the tool.
 * The game-managed submit-node pipeline avoids nested render passes.
 */
public final class SelectionRenderer {
    private static final int ORANGE = 0xFF9D2E;
    private static final int BLUE = 0x34BFFF;
    private static final int MAX_HINTS = 1024;

    private record Selection(BlockPos a, BlockPos b, BlockPos hover, BlockPos target,
                             String mode, java.util.List<BlockPos> clipboard,
                             int clipboardCount, BlockPos clipboardMax,
                             boolean flipX, boolean flipY, boolean flipZ,
                             int rotationY, int rotationX, boolean locked) {}
    private Selection state;

    public void register() {
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (!Preview.visible || mc.player == null ||
                !mc.player.getMainHandItem().is(MatterManipulatorMod.TOOL)) {
                state = null;
                return;
            }

            BlockPos target;
            BlockPos hover;
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                target = hit.getBlockPos().relative(hit.getDirection());
                hover = mc.hasShiftDown() ? hit.getBlockPos() : target;
            } else {
                target = BlockPos.containing(mc.player.getEyePosition()
                    .add(mc.player.getLookAngle().scale(5.0)));
                hover = target;
            }
            boolean locked = "paste".equals(Preview.mode) && Preview.pasteAnchor != null;
            if (locked) target = Preview.pasteAnchor;
            state = new Selection(Preview.a, Preview.b, hover, target,
                Preview.mode, Preview.clipboard, Preview.clipboardCount, Preview.clipboardMax,
                Preview.flipX, Preview.flipY, Preview.flipZ,
                Preview.rotationY, Preview.rotationX, locked);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext context) {
        Selection selected = state;
        if (selected == null) return;
        PoseStack pose = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        context.submitNodeCollector().submitCustomGeometry(
            pose, RenderTypes.debugFilledBox(),
            (entry, vertices) -> geometry(selected, entry.pose(), vertices));
        pose.popPose();
    }

    private static void geometry(Selection s, Matrix4fc m, VertexConsumer v) {
        // This blue block is ALWAYS visible, even with no selection.
        if (s.hover() != null && !s.mode().equals("paste")) {
            BlockPos h = s.hover();
            cube(m, v, h.getX(), h.getY(), h.getZ(), BLUE, .24f);
            outline(m, v, h.getX(), h.getY(), h.getZ(),
                h.getX()+1, h.getY()+1, h.getZ()+1, BLUE, .86f, .026);
        }

        if (s.mode().equals("paste")) {
            paste(m, v, s);
            return;
        }

        // While marking the second corner, predict the full selected region against the cursor.
        if (s.a() != null) {
            BlockPos b = s.b() == null ? s.hover() : s.b();
            if (b != null) {
                int minX = Math.min(s.a().getX(), b.getX());
                int minY = Math.min(s.a().getY(), b.getY());
                int minZ = Math.min(s.a().getZ(), b.getZ());
                int maxX = Math.max(s.a().getX(), b.getX()) + 1;
                int maxY = Math.max(s.a().getY(), b.getY()) + 1;
                int maxZ = Math.max(s.a().getZ(), b.getZ()) + 1;
                outline(m, v, minX, minY, minZ, maxX, maxY, maxZ, BLUE, .88f, .036);

                // Filling, copying, and cutting preview every sampled voxel
                // as a transparent blue cube, never an unrelated green/blue box.
                volume(m, v, minX, minY, minZ, maxX, maxY, maxZ, BLUE);
                cube(m, v, s.a().getX(), s.a().getY(), s.a().getZ(), BLUE, .30f);
                if (s.b() != null) {
                    cube(m, v, s.b().getX(), s.b().getY(), s.b().getZ(), BLUE, .30f);
                }
            }
        }
    }

    private static void paste(Matrix4fc m, VertexConsumer v, Selection s) {
        if (s.target() == null) return;
        int x = s.target().getX();
        int y = s.target().getY();
        int z = s.target().getZ();

        if (s.clipboardCount() == 0) {
            // Paste target stays visible even if no clipboard has been copied yet.
            outline(m, v, x, y, z, x+1, y+1, z+1, ORANGE, .85f, .028);
            cube(m, v, x, y, z, ORANGE, .2f);
            return;
        }
        BlockPos max = s.clipboardMax();
        BlockPos rotatedMax = PasteTransform.rotatedMax(max, s.rotationY(), s.rotationX());
        outline(m, v, x, y, z, x+rotatedMax.getX()+1, y+rotatedMax.getY()+1, z+rotatedMax.getZ()+1,
            ORANGE, s.locked() ? .96f : .80f, s.locked() ? .055 : .033);

        // Clipboard positions are sampled for previews but complete for actual paste.
        int hintCount = 0;
        for (BlockPos pos : s.clipboard()) {
            if (hintCount++ >= MAX_HINTS) break;
            BlockPos mapped = PasteTransform.transform(pos, max, s.flipX(), s.flipY(),
                s.flipZ(), s.rotationY(), s.rotationX());
            cube(m, v, x+mapped.getX(), y+mapped.getY(), z+mapped.getZ(), ORANGE, .32f);
        }
    }

    private static void volume(Matrix4fc m, VertexConsumer v,
                               int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        long total = (long)(x1-x0)*(y1-y0)*(z1-z0);
        if (total <= 0 || total > 32768) return;
        int stride = total <= MAX_HINTS ? 1
            : Math.max(2, (int)Math.ceil(Math.cbrt(total/(double)MAX_HINTS)));
        int emitted = 0;
        for (int y = y0; y < y1; y += stride) {
            for (int z = z0; z < z1; z += stride) {
                for (int x = x0; x < x1; x += stride) {
                    if (emitted++ >= MAX_HINTS) return;
                    cube(m, v, x, y, z, color, .23f);
                }
            }
        }
    }

    /** Half-sized hint cube centered within an occupied block. */
    private static void cube(Matrix4fc m, VertexConsumer v,
                             int x, int y, int z, int color, float alpha) {
        box(m, v, x+.27, y+.27, z+.27, x+.73, y+.73, z+.73, color, alpha);
    }

    private static void outline(Matrix4fc m, VertexConsumer v,
                                double x0, double y0, double z0, double x1, double y1, double z1,
                                int color, float alpha, double w) {
        for (int i=0; i<4; i++) {
            double x=(i&1)==0?x0:x1, z=(i&2)==0?z0:z1;
            box(m,v,x-w,y0,z-w,x+w,y1,z+w,color,alpha);
        }
        for (int i=0; i<4; i++) {
            double y=(i&1)==0?y0:y1, z=(i&2)==0?z0:z1;
            box(m,v,x0,y-w,z-w,x1,y+w,z+w,color,alpha);
        }
        for (int i=0; i<4; i++) {
            double x=(i&1)==0?x0:x1, y=(i&2)==0?y0:y1;
            box(m,v,x-w,y-w,z0,x+w,y+w,z1,color,alpha);
        }
    }

    private static void box(Matrix4fc m, VertexConsumer v,
                            double ax, double ay, double az, double bx, double by, double bz,
                            int rgb, float alpha) {
        float x0=(float)ax, y0=(float)ay, z0=(float)az;
        float x1=(float)bx, y1=(float)by, z1=(float)bz;
        float r=((rgb>>>16)&255)/255f;
        float g=((rgb>>>8)&255)/255f;
        float b=(rgb&255)/255f;
        quad(m,v,r,g,b,alpha,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);
        quad(m,v,r,g,b,alpha,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0);
        quad(m,v,r,g,b,alpha,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
        quad(m,v,r,g,b,alpha,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1);
        quad(m,v,r,g,b,alpha,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0);
        quad(m,v,r,g,b,alpha,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
    }

    private static void quad(Matrix4fc m, VertexConsumer v, float r, float g, float b, float a,
                             float x0,float y0,float z0,float x1,float y1,float z1,
                             float x2,float y2,float z2,float x3,float y3,float z3) {
        v.addVertex(m,x0,y0,z0).setColor(r,g,b,a);
        v.addVertex(m,x1,y1,z1).setColor(r,g,b,a);
        v.addVertex(m,x2,y2,z2).setColor(r,g,b,a);
        v.addVertex(m,x3,y3,z3).setColor(r,g,b,a);
    }
}
