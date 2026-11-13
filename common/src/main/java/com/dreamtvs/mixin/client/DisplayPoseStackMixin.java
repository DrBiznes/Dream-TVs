package com.dreamtvs.mixin.client;

import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamtvs.client.PanelPicture;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every layer DD draws for a flat screen (video, placeholder, subtitles) goes through this transform. For our
 * screens the result is pulled in slightly so the picture ends under the panel bezel ({@link PanelPicture}).
 * DD internals (1.10.9), {@code require = 0}: without it a sliver of picture shows at the wall's outer edge.
 */
@Pseudo
@Mixin(targets = "com.dreamdisplays.platform.client.render.DisplayPoseStack", remap = false)
public abstract class DisplayPoseStackMixin {
    @Inject(method = "applyScreenTransform", at = @At("TAIL"), require = 0, remap = false)
    private void dreamtvs$shrinkUnderBezel(PoseStack stack, DisplayFacing facing, int width, int height, CallbackInfo ci) {
        PanelPicture.shrinkUnderBezel(stack, width, height);
    }
}
