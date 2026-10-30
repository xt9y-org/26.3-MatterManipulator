package dev.xt9y.mm.mixin;

import dev.xt9y.mm.client.MatterManipulatorClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void mattermanipulator$geometryWheel(long window, double xoffset,
                                                 double yoffset, CallbackInfo ci) {
        if (MatterManipulatorClient.holdsGeometryWheel()) ci.cancel();
    }
}
