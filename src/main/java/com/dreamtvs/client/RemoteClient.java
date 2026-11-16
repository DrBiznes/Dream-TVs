package com.dreamtvs.client;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.Display;
import com.dreamdisplays.api.display.model.property.DisplayState;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.dd.ManagedTag;
import com.dreamtvs.item.RemoteItem;
import com.dreamtvs.net.RemoteRangePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client only. The remote's right-click opens the bound TV's Dream Displays menu, and its left-click turns the TV
 * off (pauses it) or back on (resumes it), through Dream Displays' client API, so it follows the screen's own
 * rules for who may control playback. Both only work within the server's {@code remoteRange}.
 *
 * <p>Vanilla has no "item left-clicked" hook, so each loader feeds its attack-key event in here. While a bound
 * remote is in the main hand the attack is swallowed: no punching, no block breaking.
 */
public final class RemoteClient {
    /** NeoForge only: the attack key is still down from a click we already handled. */
    private static boolean held;

    private RemoteClient() {}

    /**
     * The attack key is down.
     * @param freshClick true for a new press, false while it's being held
     * @return true if the remote took the click, so the normal attack must not happen
     */
    public static boolean onAttack(Minecraft minecraft, boolean freshClick) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return false;
        ItemStack stack = player.getMainHandItem();
        RemoteItem.Binding binding = RemoteItem.binding(stack);
        if (binding == null) return false;
        if (freshClick) {
            ClientCompat.swingMainHand(player);
            McCompat.overlay(player, toggle(minecraft, binding));
        }
        return true;
    }

    /** For loaders whose event doesn't say whether the press is new (NeoForge); pair with {@link #onTick}. */
    public static boolean onAttackKey(Minecraft minecraft) {
        boolean taken = onAttack(minecraft, !held);
        if (taken) held = true;
        return taken;
    }

    public static void onTick(Minecraft minecraft) {
        if (held && !minecraft.options.keyAttack.isDown()) held = false;
    }

    /**
     * The remote's right-click (from {@code RemoteItem}, through {@code TvClientHooks}): opens the bound TV's menu.
     * @return what to tell the player, or {@code null} when the menu opened
     */
    public static @Nullable Component use(RemoteItem.Binding binding) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        Reach reach;
        try {
            reach = reach(minecraft, binding);
        } catch (Throwable t) {
            DreamTvs.LOGGER.warn("Couldn't look up the remote's TV", t);
            return Component.translatable("message.dreamtvs.menu_fallback");
        }
        if (reach.problem() != null) return reach.problem();
        return DreamDisplaysMenu.open(reach.found().display())
                ? null : Component.translatable("message.dreamtvs.remote.no_screen");
    }

    private static Component toggle(Minecraft minecraft, RemoteItem.Binding binding) {
        try {
            Reach reach = reach(minecraft, binding);
            if (reach.problem() != null) return reach.problem();
            Display display = reach.found().display();
            String url = display.getUrl();
            if (url == null || url.isEmpty()) return Component.translatable("message.dreamtvs.remote.no_video");
            boolean off = display.getState() instanceof DisplayState.Paused;
            CommandResult result = off ? display.play() : display.pause();
            if (result != CommandResult.OK) return Component.translatable("message.dreamtvs.remote.refused");
            return Component.translatable(off ? "message.dreamtvs.remote.on" : "message.dreamtvs.remote.off");
        } catch (Throwable t) {
            DreamTvs.LOGGER.warn("Couldn't switch the TV with the remote", t);
            return Component.translatable("message.dreamtvs.remote.refused");
        }
    }

    /** The bound TV's screen, or why the remote can't reach it (exactly one of the two is set). */
    private record Reach(DreamDisplaysMenu.Found found, Component problem) {
        static Reach problem(String key, Object... args) {
            return new Reach(null, Component.translatable("message.dreamtvs.remote." + key, args));
        }
    }

    private static Reach reach(Minecraft minecraft, RemoteItem.Binding binding) {
        if (!binding.dimension().equals(McCompat.dimensionId(minecraft.level))) return Reach.problem("other_dimension");
        DreamDisplaysMenu.Found found = DreamDisplaysMenu.find(binding.pos());
        if (found == null) return Reach.problem("no_screen");
        int range = RemoteRangePayload.clientRange();
        if (range > 0 && distanceSqr(minecraft.player.getEyePosition(), found.bounds()) > (double) range * range) {
            return Reach.problem("out_of_range", range);
        }
        return new Reach(found, null);
    }

    /** Squared distance from {@code point} to the nearest point of the TV blocks in {@code bounds}. */
    static double distanceSqr(Vec3 point, ManagedTag.Bounds bounds) {
        double dx = gap(point.x(), bounds.min().getX(), bounds.max().getX() + 1);
        double dy = gap(point.y(), bounds.min().getY(), bounds.max().getY() + 1);
        double dz = gap(point.z(), bounds.min().getZ(), bounds.max().getZ() + 1);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double gap(double value, double low, double high) {
        return value < low ? low - value : value > high ? value - high : 0;
    }
}
