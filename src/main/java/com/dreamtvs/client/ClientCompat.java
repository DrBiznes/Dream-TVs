package com.dreamtvs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.function.Function;

//? if >=1.21.2 {
/*import net.minecraft.client.gui.components.toasts.ToastManager;
*///?} else
import net.minecraft.client.gui.components.toasts.ToastComponent;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else
import net.minecraft.client.renderer.RenderType;
//? if <1.21.9
import net.minecraft.client.gui.screens.Screen;

/** Client only. Small client API differences between the Minecraft versions we build for (see {@code McCompat}). */
public final class ClientCompat {
    private ClientCompat() {}

    /** A cycle button builder that starts at {@code initial}. */
    public static <T> CycleButton.Builder<T> cycleButton(Function<T, Component> names, T initial) {
        //? if >=1.21.11 {
        /*return CycleButton.builder(names, initial);
        *///?} else
        return CycleButton.builder(names).withInitialValue(initial);
    }

    //? if >=1.21.2 {
    /*public static ToastManager toasts(Minecraft minecraft) {
        //? if >=26.1 {
        /^return minecraft.gui.toastManager();
        ^///?} else
        return minecraft.getToastManager();
    }
    *///?} else {
    public static ToastComponent toasts(Minecraft minecraft) {
        return minecraft.getToasts();
    }
    //?}

    /** The arm swing of a left-click, as the vanilla attack does it. */
    public static void swingMainHand(LocalPlayer player) {
        //? if >=26.1 {
        /*player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
        *///?} else
        player.swing(InteractionHand.MAIN_HAND);
    }

    public static boolean hasShiftDown() {
        //? if >=1.21.9 {
        /*return Minecraft.getInstance().hasShiftDown();
        *///?} else
        return Screen.hasShiftDown();
    }

    public static boolean hasControlDown() {
        //? if >=1.21.9 {
        /*return Minecraft.getInstance().hasControlDown();
        *///?} else
        return Screen.hasControlDown();
    }

    /** Translucent, full-bright, textured: what the CRT overlay draws with. */
    public static RenderType translucentEmissive(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return RenderTypes.entityTranslucentEmissive(texture);
        *///?} else
        return RenderType.entityTranslucentEmissive(texture);
    }
}
