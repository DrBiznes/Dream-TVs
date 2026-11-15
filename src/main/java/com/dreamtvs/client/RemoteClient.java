package com.dreamtvs.client;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.Display;
import com.dreamdisplays.api.display.model.property.DisplayState;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.item.RemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client only. The remote's left-click: turns the bound TV off (pauses it) or back on (resumes it), through
 * Dream Displays' client API, so it follows the screen's own rules for who may control playback.
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

    private static Component toggle(Minecraft minecraft, RemoteItem.Binding binding) {
        if (!binding.dimension().equals(McCompat.dimensionId(minecraft.level))) {
            return Component.translatable("message.dreamtvs.remote.other_dimension");
        }
        try {
            Display display = DreamDisplaysMenu.displayAt(binding.pos());
            if (display == null) return Component.translatable("message.dreamtvs.remote.no_screen");
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
}
