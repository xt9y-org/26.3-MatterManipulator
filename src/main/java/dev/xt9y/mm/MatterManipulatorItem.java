package dev.xt9y.mm;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Normal right-click selects corners; Shift+RMB editing uses the hold queue. */
public final class MatterManipulatorItem extends Item {
    public MatterManipulatorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide() && !player.isShiftKeyDown()
            && player instanceof ServerPlayer server) {
            SimpleEditor.select(server, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && !player.isShiftKeyDown()
            && player instanceof ServerPlayer server) {
            SimpleEditor.select(server,
                net.minecraft.core.BlockPos.containing(server.getEyePosition()
                    .add(server.getLookAngle().scale(5.0))));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("M: Fill cube / Copy / Paste / Cut").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.literal("Right-click: select 2 corners").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Hold Shift + RMB for 1s: apply gradually").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Empty offhand in Fill mode = Air").withStyle(ChatFormatting.GRAY));
    }
}
