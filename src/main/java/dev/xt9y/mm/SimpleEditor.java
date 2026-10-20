package dev.xt9y.mm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One selection, four modes and a resumable, server-ticked work queue. */
final class SimpleEditor {
    private static final int MAX_BLOCKS = 32768;
    private static final int BLOCKS_PER_TICK = 48;
    private static final int HEARTBEAT_TIMEOUT = 12;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    enum Mode { FILL, COPY, PASTE, CUT }
    record Cell(BlockState state, CompoundTag nbt) {}

    private static final class Job {
        final Mode mode;
        final ResourceKey<Level> dimension;
        final List<BlockPos> positions;
        final List<Cell> destinationCells;
        final Map<BlockPos, Cell> captured = new LinkedHashMap<>();
        final BlockPos origin;
        final BlockState material;
        int index;
        int phase;
        long lastPulse;
        boolean active;

        Job(Mode mode, ResourceKey<Level> dimension, List<BlockPos> positions,
            List<Cell> destinationCells, BlockPos origin, BlockState material) {
            this.mode = mode;
            this.dimension = dimension;
            this.positions = positions;
            this.destinationCells = destinationCells;
            this.origin = origin;
            this.material = material;
        }
    }

    private static final class Session {
        Mode mode = Mode.FILL;
        BlockPos a, b;
        ResourceKey<Level> dimension;
        Map<BlockPos, Cell> clipboard = Map.of();
        boolean cutClipboard;
        boolean flipX, flipY, flipZ;
        Job job;
    }

    private static Session session(ServerPlayer player) {
        Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        if (s.dimension != null && !s.dimension.equals(player.level().dimension())) {
            s.a = null;
            s.b = null;
            s.job = null;
        }
        s.dimension = player.level().dimension();
        return s;
    }

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[MM] " + message));
    }

    private static void progress(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.literal("[MM] " + message));
    }

    private static boolean creative(ServerPlayer player) {
        if (player.isCreative()) return true;
        say(player, "Creative mode required (inventory NBT can be duplicated)");
        return false;
    }

    private static String title(Mode mode) {
        return switch (mode) {
            case FILL -> "Fill cube";
            case COPY -> "Copy";
            case PASTE -> "Paste";
            case CUT -> "Cut";
        };
    }

    private static String coords(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static void sync(ServerPlayer player, Session s) {
        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, StatePayload.TYPE)) return;
        String data = s.mode.name().toLowerCase(java.util.Locale.ROOT) + "|"
            + (s.a == null ? "_" : coords(s.a)) + "|"
            + (s.b == null ? "_" : coords(s.b)) + "|"
            + s.clipboard.size() + "|" + s.cutClipboard + "|"
            + s.flipX + "|" + s.flipY + "|" + s.flipZ + "|"
            + coords(clipboardMax(s.clipboard));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new StatePayload(data));
    }


    private static BlockPos clipboardMax(Map<BlockPos, Cell> clipboard) {
        int x = 0, y = 0, z = 0;
        for (BlockPos p : clipboard.keySet()) {
            x = Math.max(x, p.getX());
            y = Math.max(y, p.getY());
            z = Math.max(z, p.getZ());
        }
        return new BlockPos(x, y, z);
    }

    /** Vertical mirror handles facing and top/bottom, upper/lower properties. */
    private static <T extends Comparable<T>> BlockState flipVerticalProperty(BlockState state, Property<T> property) {
        String value = property.getName(state.getValue(property));
        String opposite = switch (value) {
            case "up" -> "down";
            case "down" -> "up";
            case "top" -> "bottom";
            case "bottom" -> "top";
            case "upper" -> "lower";
            case "lower" -> "upper";
            case "floor" -> "ceiling";
            case "ceiling" -> "floor";
            default -> null;
        };
        if (opposite == null) return state;
        return property.getValue(opposite).map(next -> state.setValue(property, next)).orElse(state);
    }

    private static BlockState flippedState(BlockState state, Session s) {
        if (s.flipX) state = state.mirror(Mirror.FRONT_BACK);
        if (s.flipZ) state = state.mirror(Mirror.LEFT_RIGHT);
        if (s.flipY) {
            // Copy the properties before replacing values on immutable block states.
            List<Property<?>> properties = new ArrayList<>(state.getProperties());
            for (Property<?> property : properties) state = flipVerticalProperty(state, property);
        }
        return state;
    }

    private static void syncClipboard(ServerPlayer player, Session s) {
        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ClipboardPayload.TYPE)) return;
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (BlockPos offset : s.clipboard.keySet()) {
            if (count++ >= 768) break;
            if (!result.isEmpty()) result.append(';');
            result.append(coords(offset));
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ClipboardPayload(result.toString()));
    }

    static int mode(ServerPlayer player, String requested) {
        if (!creative(player)) return 0;
        Session s = session(player);
        Mode next;
        if (requested.equalsIgnoreCase("next")) {
            next = Mode.values()[(s.mode.ordinal() + 1) % Mode.values().length];
        } else {
            try {
                next = Mode.valueOf(requested.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                say(player, "Modes: fill, copy, paste, cut");
                return 0;
            }
        }
        if (next != s.mode && s.job != null) {
            s.job.active = false;
            s.job = null;
        }
        s.mode = next;
        sync(player, s);
        say(player, title(next) + " mode"
            + (s.a != null && s.b != null ? " | Region selected" : ""));
        return 1;
    }

    static int status(ServerPlayer player) {
        Session s = session(player);
        sync(player, s);
        say(player, title(s.mode) + " | " +
            (s.a == null ? "Select first corner" : s.b == null ? "Select second corner" : "Region selected")
            + " | Clipboard: " + s.clipboard.size());
        return 1;
    }

    static int clear(ServerPlayer player) {
        Session s = session(player);
        s.a = null;
        s.b = null;
        s.job = null;
        sync(player, s);
        say(player, "Selection cleared");
        return 1;
    }

    static int flip(ServerPlayer player, String axis) {
        if (!creative(player)) return 0;
        Session s = session(player);
        if (s.mode != Mode.PASTE) return 0;
        if (s.job != null) {
            s.job.active = false;
            s.job = null;
        }
        boolean enabled;
        switch (axis.toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> { s.flipX = !s.flipX; enabled = s.flipX; }
            case "y" -> { s.flipY = !s.flipY; enabled = s.flipY; }
            case "z" -> { s.flipZ = !s.flipZ; enabled = s.flipZ; }
            default -> { return 0; }
        }
        sync(player, s);
        say(player, "Paste flip " + axis.toUpperCase(java.util.Locale.ROOT) + ": " +
            (enabled ? "ON" : "OFF"));
        return 1;
    }

    static int shortcut(ServerPlayer player, String requested) {
        return switch (requested.toLowerCase(java.util.Locale.ROOT)) {
            case "copy" -> mode(player, "copy");
            case "cut" -> mode(player, "cut");
            case "paste" -> mode(player, "paste");
            case "reset" -> clear(player);
            default -> 0;
        };
    }

    /** Normal right-click marks corners only. No mode ever edits here. */
    static void select(ServerPlayer player, BlockPos clicked) {
        if (!creative(player)) return;
        Session s = session(player);
        // Paste uses the player's current aim when the hold begins;
        // ordinary clicks must not accidentally replace the saved selection.
        if (s.mode == Mode.PASTE) return;
        // The client sends exactly one packet per physical mouse press.
        // No tick-based debounce here: rapid double-clicks may intentionally
        // select two nearby corners or a single-block region.
        s.job = null;
        if (s.a == null || s.b != null) {
            s.a = clicked.immutable();
            s.b = null;
            say(player, "First corner selected");
        } else {
            long x = Math.abs((long)s.a.getX() - clicked.getX()) + 1;
            long y = Math.abs((long)s.a.getY() - clicked.getY()) + 1;
            long z = Math.abs((long)s.a.getZ() - clicked.getZ()) + 1;
            if (x * y * z > MAX_BLOCKS) {
                say(player, "Region too large (max " + MAX_BLOCKS + " blocks)");
                return;
            }
            s.b = clicked.immutable();
            say(player, "Region selected (" + (x * y * z) + " blocks)");
        }
        sync(player, s);
    }

    private static boolean loaded(ServerLevel level, BlockPos position) {
        return level.isInWorldBounds(position) && level.hasChunkAt(position);
    }

    private static BlockPos lower(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos upper(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static List<BlockPos> selected(ServerLevel level, Session s) {
        if (s.a == null || s.b == null) throw new IllegalArgumentException("Select two corners first");
        BlockPos lo = lower(s.a, s.b), hi = upper(s.a, s.b);
        long volume = ((long)hi.getX()-lo.getX()+1)
            * ((long)hi.getY()-lo.getY()+1) * ((long)hi.getZ()-lo.getZ()+1);
        if (volume > MAX_BLOCKS) throw new IllegalArgumentException("Selection exceeds " + MAX_BLOCKS + " blocks");
        List<BlockPos> positions = new ArrayList<>((int)volume);
        for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
            if (!loaded(level, pos)) throw new IllegalArgumentException("Selection contains unloaded/out-of-world blocks");
            positions.add(pos.immutable());
        }
        return positions;
    }

    private static Cell snapshot(ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        CompoundTag data = blockEntity == null ? null
            : blockEntity.saveWithFullMetadata(level.registryAccess()).copy();
        return new Cell(level.getBlockState(position), data);
    }

    static int begin(ServerPlayer player, BlockPos target) {
        if (!creative(player) || !player.getMainHandItem().is(MatterManipulatorMod.TOOL)) return 0;
        Session s = session(player);
        if (s.job != null && s.job.mode == s.mode
                && s.job.dimension.equals(player.level().dimension())) {
            s.job.lastPulse = player.level().getGameTime();
            s.job.active = true;
            return 1;
        }
        ServerLevel level = (ServerLevel) player.level();
        try {
            Job job;
            if (s.mode == Mode.PASTE) {
                if (s.clipboard.isEmpty()) throw new IllegalArgumentException("Clipboard empty");
                List<BlockPos> positions = new ArrayList<>(s.clipboard.size());
                List<Cell> cells = new ArrayList<>(s.clipboard.size());
                BlockPos size = clipboardMax(s.clipboard);
                for (Map.Entry<BlockPos, Cell> entry : s.clipboard.entrySet()) {
                    BlockPos offset = entry.getKey();
                    int x = s.flipX ? size.getX() - offset.getX() : offset.getX();
                    int y = s.flipY ? size.getY() - offset.getY() : offset.getY();
                    int z = s.flipZ ? size.getZ() - offset.getZ() : offset.getZ();
                    BlockPos dest = target.offset(x, y, z);
                    if (!loaded(level, dest)) throw new IllegalArgumentException("Destination includes unloaded/out-of-world blocks");
                    Cell original = entry.getValue();
                    Cell transformed = new Cell(flippedState(original.state(), s), original.nbt());
                    validateEntity(level, dest, transformed);
                    positions.add(dest);
                    cells.add(transformed);
                }
                job = new Job(s.mode, level.dimension(), positions, cells, target, null);
            } else {
                List<BlockPos> positions = selected(level, s);
                BlockPos origin = lower(s.a, s.b);
                BlockState material = null;
                if (s.mode == Mode.FILL) {
                    if (player.getOffhandItem().isEmpty()) {
                        material = Blocks.AIR.defaultBlockState();
                    } else if (player.getOffhandItem().getItem() instanceof BlockItem blockItem) {
                        material = blockItem.getBlock().defaultBlockState();
                    } else {
                        throw new IllegalArgumentException("Offhand must contain a block or be empty (air)");
                    }
                }
                job = new Job(s.mode, level.dimension(), positions, List.of(), origin, material);
            }
            job.active = true;
            job.lastPulse = level.getGameTime();
            s.job = job;
            progress(player, title(job.mode) + " 0/" + job.positions.size());
            return 1;
        } catch (RuntimeException exception) {
            say(player, "Cannot start: " + exception.getMessage());
            return 0;
        }
    }

    static int pulse(ServerPlayer player) {
        Session s = session(player);
        if (s.job == null || s.job.mode != s.mode) return 0;
        s.job.lastPulse = player.level().getGameTime();
        s.job.active = true;
        return 1;
    }

    static int stop(ServerPlayer player) {
        Session s = session(player);
        if (s.job != null) s.job.active = false;
        return 1;
    }

    /** Register from END_SERVER_TICK. Each edit stays bounded per tick. */
    static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            Session s = entry.getValue();
            Job job = s.job;
            if (job == null || !job.active) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isCreative() || !player.getMainHandItem().is(MatterManipulatorMod.TOOL)
                || !job.dimension.equals(player.level().dimension())) {
                job.active = false;
                continue;
            }
            ServerLevel level = (ServerLevel)player.level();
            if (level.getGameTime() - job.lastPulse > HEARTBEAT_TIMEOUT) {
                job.active = false;
                continue;
            }
            try {
                process(player, s, job, level);
            } catch (RuntimeException exception) {
                job.active = false;
                s.job = null;
                say(player, "Stopped: " + exception.getMessage());
            }
        }
    }

    private static void process(ServerPlayer player, Session session, Job job, ServerLevel level) {
        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && session.job == job) {
            if (job.index == job.positions.size()) {
                if ((job.mode == Mode.CUT) && job.phase == 0) {
                    session.clipboard = new LinkedHashMap<>(job.captured);
                    session.cutClipboard = true;
                    syncClipboard(player, session);
                    sync(player, session);
                    job.phase = 1;
                    job.index = 0;
                } else {
                    finish(player, session, job);
                    break;
                }
            }
            if (session.job != job) break;
            BlockPos pos = job.positions.get(job.index);
            if (!loaded(level, pos)) throw new IllegalArgumentException("Chunk unloaded while editing");
            switch (job.mode) {
                case FILL -> write(level, pos, new Cell(job.material, null));
                case PASTE -> write(level, pos, job.destinationCells.get(job.index));
                case COPY, CUT -> {
                    if (job.phase == 0) {
                        job.captured.put(pos.subtract(job.origin), snapshot(level, pos));
                    } else {
                        write(level, pos, new Cell(Blocks.AIR.defaultBlockState(), null));
                    }
                }
            }
            job.index++;
        }
        if (session.job == job && level.getGameTime() % 5 == 0) {
            String verb = job.phase == 1 ? "Cut" : title(job.mode);
            progress(player, verb + " " + job.index + "/" + job.positions.size());
        }
    }

    private static void finish(ServerPlayer player, Session s, Job job) {
        if (job.mode == Mode.COPY) {
            s.clipboard = new LinkedHashMap<>(job.captured);
            s.cutClipboard = false;
            syncClipboard(player, s);
            sync(player, s);
        } else if (job.mode == Mode.PASTE && s.cutClipboard) {
            s.clipboard = Map.of();
            s.cutClipboard = false;
            syncClipboard(player, s);
            sync(player, s);
        }
        s.job = null;
        progress(player, title(job.mode) + " complete");
        say(player, switch (job.mode) {
            case FILL -> "Cube filled (" + job.positions.size() + " blocks)";
            case COPY -> "Copied (" + s.clipboard.size() + " blocks incl. NBT)";
            case CUT -> "Cut (" + job.positions.size() + " blocks) — ready to paste";
            case PASTE -> "Pasted (" + job.positions.size() + " blocks incl. NBT)";
        });
    }

    /** Preflight full NBT restoration to avoid invalid block entities mid-paste. */
    private static void validateEntity(ServerLevel level, BlockPos pos, Cell cell) {
        if (cell.nbt() == null) return;
        CompoundTag data = destinationTag(cell.nbt(), pos);
        if (BlockEntity.loadStatic(pos, cell.state(), data, level.registryAccess()) == null) {
            throw new IllegalArgumentException("Cannot restore block entity at " + coords(pos));
        }
    }

    private static CompoundTag destinationTag(CompoundTag stored, BlockPos pos) {
        CompoundTag data = stored.copy();
        data.putInt("x", pos.getX());
        data.putInt("y", pos.getY());
        data.putInt("z", pos.getZ());
        return data;
    }

    private static void write(ServerLevel level, BlockPos pos, Cell cell) {
        BlockEntity restored = null;
        if (cell.nbt() != null) {
            restored = BlockEntity.loadStatic(pos, cell.state(),
                destinationTag(cell.nbt(), pos), level.registryAccess());
            if (restored == null) throw new IllegalArgumentException("Invalid block entity at " + coords(pos));
        }

        // Empty-space erasure should not cause unnecessary neighbor updates.
        if (cell.state().isAir() && level.getBlockState(pos).isAir()
            && level.getBlockEntity(pos) == null) return;

        // Clear existing inventories before replacing chests to avoid item drops.
        BlockEntity existing = level.getBlockEntity(pos);
        if (existing instanceof Container container) container.clearContent();
        if (existing != null) level.removeBlockEntity(pos);

        // Replace the state even if the block type is unchanged so old NBT is gone.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!cell.state().isAir()) {
            level.setBlock(pos, cell.state(), Block.UPDATE_ALL);
            if (restored != null) {
                level.setBlockEntity(restored);
                restored.setChanged();
                level.sendBlockUpdated(pos, cell.state(), cell.state(), Block.UPDATE_ALL);
            }
        }
    }

    private SimpleEditor() {}
}
