package com.dreamtvs.client;

import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamdisplays.platform.client.render.DisplayPoseStack;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.config.DreamTvsClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.1 {
/*import com.dreamdisplays.libs.kotlin.Unit;
import com.dreamdisplays.libs.kotlin.jvm.functions.Function2;
import com.dreamdisplays.platform.client.render.DisplaySolidPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
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
 * <p>Players can turn it off in the settings ({@code crtOverlay} in the client config).
 *
 * <p>Up to 1.21.x the overlay has to be drawn after DD has flushed its pictures, so each CRT only queues where
 * it is while DD draws it, and everything queued is drawn through the game's buffer source when DD's render pass
 * ends. Uses DD's own screen transform (a DD client internal, 1.10.9); if that ever fails the overlay switches
 * itself off and CRTs show a plain picture.
 *
 * <p>26.x has no buffer source, and the game draws what it is handed later in the frame. There the overlay is
 * handed over straight after each CRT's picture ({@code PanelPicture.end}), in a way that puts it after the
 * picture whichever way DD is drawing ({@link #draw}).
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
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static Field levelSubmits;
    /^* The quads carry their own positions, so the pose the draw list stores with them isn't used. ^/
    private static final PoseStack UNUSED_POSE = new PoseStack();

    /^* Called before DD draws each screen, with the draw callback DD uses for it. ^/
    @SuppressWarnings("rawtypes")
    public static void useDrawer(Function2 drawQuad) {
        drawer = drawQuad;
    }
    *///?}

    private CrtOverlay() {}

    /** Called while DD draws a CRT screen, with the pose DD started that screen with. */
    static void queue(PoseStack stack, DisplayFacing facing, int width, int height) {
        if (broken || !DreamTvsClientConfig.get().crtOverlay) return;
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

    /** Draws every queued overlay: when DD's render pass is over, or on 26.x after each CRT's picture. */
    public static void flush() {
        if (queued.isEmpty()) return;
        // A copy: on 26.x the quads are emitted later in the frame, after the queue has been cleared.
        List<Matrix4f> poses = List.copyOf(queued);
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
                draw(type(BAND), band -> {
                    for (Matrix4f pose : poses) {
                        vertex(band, pose, 0F, y0, 0F, v0);
                        vertex(band, pose, 1F, y0, 1F, v0);
                        vertex(band, pose, 1F, y1, 1F, v1);
                        vertex(band, pose, 0F, y1, 0F, v1);
                    }
                });
            }

            draw(type(TEXTURE), out -> {
                for (Matrix4f pose : poses) {
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

    /**
     * Translucent, full-bright, textured. On 26.x the type is also marked as one of DD's own (a DD client
     * internal, 1.10.9), which keeps it out of the game's order-independent transparency pass: there the
     * overlay came out as an even grey tint. DD's pictures are marked the same way.
     */
    private static RenderType type(ResourceLocation texture) {
        //? if >=26.1 {
        /*return TYPES.computeIfAbsent(texture, t -> DisplaySolidPhase.INSTANCE.mark(ClientCompat.translucentEmissive(t)));
        *///?} else
        return ClientCompat.translucentEmissive(texture);
    }

    //? if >=26.1 {
    /*/^*
     * The level's draw list. It has no getter, and DD's callback only reaches its default order; 26.x isn't
     * obfuscated, so the field has this name on both loaders.
     ^/
    private static SubmitNodeStorage levelSubmits() throws ReflectiveOperationException {
        if (levelSubmits == null) {
            levelSubmits = LevelRenderer.class.getDeclaredField("submitNodeStorage");
            levelSubmits.setAccessible(true);
        }
        return (SubmitNodeStorage) levelSubmits.get(Minecraft.getInstance().levelRenderer);
    }
    *///?}

    /**
     * Emits quads of {@code type} and draws them: now up to 1.21.x, later in the frame on 26.x.
     *
     * <p>On 26.x DD usually hands its pictures to the game's draw list, where draws of different render types
     * come out in no fixed order, so the overlay could land under the picture. It goes into the list's next
     * order instead, which the game draws after everything in the default one. When DD draws its pictures
     * itself at the end of the frame (its {@code ScreenRenderer} callback), it keeps the order it was given.
     */
    @SuppressWarnings("unchecked")
    private static void draw(RenderType type, Consumer<VertexConsumer> quads) throws ReflectiveOperationException {
        //? if >=26.1 {
        /*if (drawer == null) return;
        if (drawer.getClass().getName().contains("ScreenRenderer")) {
            Function2<Object, VertexConsumer, Unit> emit = (pose, out) -> {
                quads.accept(out);
                return Unit.INSTANCE;
            };
            drawer.invoke(type, emit);
        } else {
            levelSubmits().order(1).submitCustomGeometry(UNUSED_POSE, type, (pose, out) -> quads.accept(out));
        }
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
