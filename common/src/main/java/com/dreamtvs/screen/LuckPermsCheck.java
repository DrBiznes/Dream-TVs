package com.dreamtvs.screen;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.util.UUID;

/**
 * The only class that touches LuckPerms (an optional dependency). It's loaded only after
 * {@link ScreenPermissions} has seen the LuckPerms API on the classpath.
 */
final class LuckPermsCheck {
    private LuckPermsCheck() {}

    /** {@code true}/{@code false} if LuckPerms sets the node for this player, {@code null} if it doesn't say. */
    static Boolean check(UUID player, String node) {
        LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException notLoadedYet) {
            return null;
        }
        User user = luckPerms.getUserManager().getUser(player);
        if (user == null) return null;
        return switch (user.getCachedData().getPermissionData().checkPermission(node)) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case UNDEFINED -> null;
        };
    }
}
