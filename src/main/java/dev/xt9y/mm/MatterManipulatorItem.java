package dev.xt9y.mm;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** The client sends one selection per click; Shift+RMB uses the hold queue. */
public final class MatterManipulatorItem extends Item {
    public MatterManipulatorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        // Actual vanilla use events catch fast clicks that fall between
        // 20 Hz client ticks. Normal RMB selects the adjacent face position.
        if (!context.getLevel().isClientSide() && !player.isShiftKeyDown()
                && player instanceof ServerPlayer server) {
            SimpleEditor.select(server,
                context.getClickedPos().relative(context.getClickedFace()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && !player.isShiftKeyDown()
                && player instanceof ServerPlayer server) {
            BlockPos forward = BlockPos.containing(server.getEyePosition()
                .add(server.getLookAngle().scale(5.0)));
            SimpleEditor.select(server, forward);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("M: Fill cube / Copy / Paste / Cut").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.literal("RMB: select adjacent / short Shift+RMB: select block").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Hold Shift + RMB for 1s: apply gradually").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Paste mode: X/Y/Z flip axes").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Empty offhand in Fill mode = Air").withStyle(ChatFormatting.GRAY));
    }
}
