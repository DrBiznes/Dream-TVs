package com.dreamtvs.client;

import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamdisplays.platform.client.render.DisplayPoseStack;
import com.dreamtvs.DreamTvs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.1 {
/*import com.dreamdisplays.libs.kotlin.Unit;
import com.dreamdisplays.libs.kotlin.jvm.functions.Function2;
*///?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client only. The CRT look, drawn over the Dream Displays picture on CRT TVs: one translucent texture with the
 * phosphor glow, aperture grille, scanlines, darkened edges, glass glare and the curved edge of the tube, and
 * under it a soft bar of light that rolls slowly down the screen.
 *
 * <p>The overlay has to be drawn after DD has flushed its pictures, so each CRT only queues where it is while
 * DD draws it, and everything queued is drawn when DD's render pass ends. Uses DD's own screen transform (a DD
 * client internal, 1.10.9); if that ever fails the overlay switches itself off and CRTs show a plain picture.
 *
 * <p>Up to 1.21.x the overlay goes through the game's buffer source. 26.x has none, so there it goes through the
 * draw callback DD hands its own screen drawing ({@link #useDrawer}), which DD flushes in the order things were
 * drawn.
 */
public final class CrtOverlay {
    private static final ResourceLocation TEXTURE = DreamTvs.id("textures/effect/crt_overlay.png");
    private static final ResourceLocation BAND = DreamTvs.id("textures/effect/crt_band.png");
    /** The rolling bar: its height as a share of the tube, and how long one pass takes. */
    private static final float BAND_HEIGHT = 0.22F;
    private static final long BAND_PERIOD_MS = 9000L;
    /** How far in front of the picture the overlay sits, in blocks. Less than DD's subtitle lift. */
    private static final float LIFT = 0.003F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final List<Matrix4f> queued = new ArrayList<>();
    private static boolean broken;
    //? if >=26.1 {
    /*/^* DD's {@code (RenderType, (Pose, VertexConsumer) -> Unit) -> Unit} for the current render pass. ^/
    @SuppressWarnings("rawtypes")
    private static Function2 drawer;

    /^* Called before DD draws each screen, with the draw callback DD uses for it. ^/
    @SuppressWarnings("rawtypes")
    public static void useDrawer(Function2 drawQuad) {
        drawer = drawQuad;
    }
    *///?}

    private CrtOverlay() {}

    /** Called while DD draws a CRT screen, with the pose DD started that screen with. */
    static void queue(PoseStack stack, DisplayFacing facing, int width, int height) {
        if (broken) return;
        stack.pushPose();
        try {
            DisplayPoseStack.INSTANCE.liftTowardViewer(stack, facing, LIFT);
            // Ends in PanelPicture.fitToGlass (via our mixin), so the unit square is exactly the tube.
            DisplayPoseStack.INSTANCE.applyScreenTransform(stack, facing, width, height);
            queued.add(new Matrix4f(stack.last().pose()));
        } catch (Throwable t) {
            broken = true;
            DreamTvs.LOGGER.warn("Dream Displays client internals changed; the CRT scanline overlay is off", t);
        } finally {
            stack.popPose();
        }
    }

    /** Called when DD's render pass is over: draws every queued overlay. */
    public static void flush() {
        if (queued.isEmpty()) return;
        try {
            // The rolling bar first, so the tube's edge in the main texture covers its ends.
            float travel = 1F + BAND_HEIGHT;
            float phase = (System.currentTimeMillis() % BAND_PERIOD_MS) / (float) BAND_PERIOD_MS;
            float top = 1F + BAND_HEIGHT - phase * (travel + BAND_HEIGHT); // from above the tube to below it
            float y1 = Math.min(1F, top);
            float y0 = Math.max(0F, top - BAND_HEIGHT);
            if (y1 > y0) {
                // v runs 0 at the bar's top to 1 at its bottom; clipped where the bar leaves the tube.
                float v1 = (top - y1) / BAND_HEIGHT;
                float v0 = (top - y0) / BAND_HEIGHT;
                draw(ClientCompat.translucentEmissive(BAND), band -> {
                    for (Matrix4f pose : queued) {
                        vertex(band, pose, 0F, y0, 0F, v0);
                        vertex(band, pose, 1F, y0, 1F, v0);
                        vertex(band, pose, 1F, y1, 1F, v1);
                        vertex(band, pose, 0F, y1, 0F, v1);
                    }
                });
            }

            draw(ClientCompat.translucentEmissive(TEXTURE), out -> {
                for (Matrix4f pose : queued) {
                    vertex(out, pose, 0F, 0F, 0F, 1F);
                    vertex(out, pose, 1F, 0F, 1F, 1F);
                    vertex(out, pose, 1F, 1F, 1F, 0F);
                    vertex(out, pose, 0F, 1F, 0F, 0F);
                }
            });
        } catch (Throwable t) {
            broken = true;
            DreamTvs.LOGGER.warn("Couldn't draw the CRT scanline overlay; it is off", t);
        } finally {
            queued.clear();
        }
    }

    /** Emits quads of {@code type} and draws them now. */
    @SuppressWarnings("unchecked")
    private static void draw(RenderType type, Consumer<VertexConsumer> quads) {
        //? if >=26.1 {
        /*if (drawer == null) return;
        Function2<Object, VertexConsumer, Unit> emit = (pose, out) -> {
            quads.accept(out);
            return Unit.INSTANCE;
        };
        drawer.invoke(type, emit);
        *///?} else {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        quads.accept(buffers.getBuffer(type));
        buffers.endBatch(type);
        //?}
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float u, float v) {
        Vector3f at = pose.transformPosition(x, y, 0F, new Vector3f());
        out.addVertex(at.x, at.y, at.z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0F, 0F, 1F);
    }
}
