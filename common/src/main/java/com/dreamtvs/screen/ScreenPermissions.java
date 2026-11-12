package com.dreamtvs.screen;

import com.dreamdisplays.api.server.ServerDisplay;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may change (grow, shrink, break) a screen. Plan §5.7.
 * Minimal version: the owner, or an operator (level 2), matching DD's fallback for {@code dreamdisplays.delete.others}.
 * TODO(M2): LuckPerms node checks and the {@code extendPolicy} config.
 */
public final class ScreenPermissions {
    private ScreenPermissions() {}

    public static boolean canModify(ServerPlayer player, ServerDisplay display) {
        return display.getOwnerId().equals(player.getUUID()) || player.hasPermissions(2);
    }
}
