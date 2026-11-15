package com.dreamtvs.mixin.client;

import com.dreamtvs.client.PanelPicture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crops the video on TVs whose glass isn't the shape of the block box (the CRT): while Dream Displays emits the
 * video quad, its texture coordinates are narrowed to the part of the video that fills the glass
 * ({@link PanelPicture#setVideo}).
 *
 * <p>Kept apart from {@link ScreenRendererMixin} on purpose. This one leans on a compiler-generated lambda name
 * and a private helper's argument order (DD 1.10.9), so it is the likeliest to break on a DD update, and a
 * mixin class that fails to apply takes all of its injects with it. If it fails, CRT video is stretched with
 * black bars and nothing else changes.
 */
@Pseudo
@Mixin(targets = "com.dreamdisplays.platform.client.render.ScreenRenderer", remap = false)
public abstract class ScreenRendererVideoMixin {
    /** The lambda that emits the video quad (it returns Kotlin's Unit). Only then are coordinates cropped. */
    @Inject(method = "renderGpuTexture$lambda$0", at = @At("HEAD"), require = 0, remap = false)
    private static void dreamtvs$videoQuadBegins(CallbackInfoReturnable<?> ci) {
        PanelPicture.videoQuad(true);
    }

    @Inject(method = "renderGpuTexture$lambda$0", at = @At("RETURN"), require = 0, remap = false)
    private static void dreamtvs$videoQuadEnds(CallbackInfoReturnable<?> ci) {
        PanelPicture.videoQuad(false);
    }

    /** {@code addVertex(pose, builder, x, y, z, r, g, b, u, v)}: u and v are its 4th and 5th floats. */
    @ModifyVariable(method = "addVertex", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0, remap = false)
    private float dreamtvs$cropU(float u) {
        return PanelPicture.cropU(u);
    }

    @ModifyVariable(method = "addVertex", at = @At("HEAD"), ordinal = 4, argsOnly = true, require = 0, remap = false)
    private float dreamtvs$cropV(float v) {
        return PanelPicture.cropV(v);
    }
}
