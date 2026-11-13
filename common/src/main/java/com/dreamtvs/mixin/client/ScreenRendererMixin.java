package com.dreamtvs.mixin.client;

import com.dreamdisplays.libs.kotlin.jvm.functions.Function2;
import com.dreamdisplays.platform.client.displays.DisplayScreen;
import com.dreamtvs.client.PanelPicture;
import com.dreamtvs.dd.ManagedTag;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dream Displays draws a flat screen on the front face of its block box. An LCD panel is thinner than a block
 * and its glass sits behind that face, so for our screens the picture is moved back onto the glass
 * ({@link PanelPicture}). DD pushes the pose before this call and pops it after, so nothing leaks.
 *
 * <p>This targets DD client internals (1.10.9). If the method changes, the injects just don't apply
 * ({@code require = 0}) and the picture floats a few pixels in front of the panel instead.
 */
@Pseudo
@Mixin(targets = "com.dreamdisplays.platform.client.render.ScreenRenderer", remap = false)
public abstract class ScreenRendererMixin {
    @Inject(method = "renderScreenTexture", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    @SuppressWarnings("rawtypes")
    private void dreamtvs$beginPanel(DisplayScreen screen, PoseStack stack, boolean replay, Function2 drawQuad,
                                     CallbackInfo ci) {
        if (!PanelPicture.begin(screen.getCustomData().containsKey(ManagedTag.KEY), screen.getFacing(), stack)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScreenTexture", at = @At("RETURN"), require = 0, remap = false)
    @SuppressWarnings("rawtypes")
    private void dreamtvs$endPanel(DisplayScreen screen, PoseStack stack, boolean replay, Function2 drawQuad,
                                   CallbackInfo ci) {
        PanelPicture.end();
    }
}
