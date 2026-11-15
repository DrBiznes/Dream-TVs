package com.dreamtvs.screen;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.config.DreamTvsConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Who may do what with TV screens (plan §5.7). Same node names and fallbacks as Dream Displays: LuckPerms decides
 * when it's installed and the node is set; otherwise the fallback applies (everyone, or op level 2).
 */
public final class ScreenPermissions {
    public static final String CREATE = "dreamdisplays.create";
    public static final String CREATE_BYPASS = "dreamdisplays.create.bypass";
    public static final String DELETE = "dreamdisplays.delete";
    public static final String DELETE_OTHERS = "dreamdisplays.delete.others";

    /** Owner of screens formed without a player (e.g. {@code /fill}). Only admins can change them. */
    public static final UUID SERVER_OWNER = new UUID(0L, 0L);

    private enum Fallback { EVERYONE, OP }

    private static final boolean LUCKPERMS = classExists("net.luckperms.api.LuckPermsProvider");

    private ScreenPermissions() {}

    /** Forming a brand new screen ({@code /display create} in DD). */
    public static boolean canCreate(ServerPlayer player) {
        return has(player, CREATE, Fallback.EVERYONE);
    }

    /** Going over {@code maxScreensPerPlayer}. */
    public static boolean canBypassLimit(ServerPlayer player) {
        return has(player, CREATE_BYPASS, Fallback.OP);
    }

    /** Growing, shrinking, breaking or merging a screen owned by {@code owner}. */
    public static boolean canModify(ServerPlayer player, UUID owner) {
        if (DreamTvsConfig.get().extendPolicy == DreamTvsConfig.ExtendPolicy.ANYONE) return true;
        if (owner.equals(player.getUUID())) return has(player, DELETE, Fallback.EVERYONE);
        return has(player, DELETE_OTHERS, Fallback.OP);
    }

    private static boolean has(ServerPlayer player, String node, Fallback fallback) {
        if (LUCKPERMS) {
            try {
                Boolean answer = LuckPermsCheck.check(player.getUUID(), node);
                if (answer != null) return answer;
            } catch (LinkageError e) {
                DreamTvs.LOGGER.debug("LuckPerms API unusable, using fallback for {}", node, e);
            }
        }
        return switch (fallback) {
            case EVERYONE -> true;
            case OP -> McCompat.isGameMaster(player);
        };
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ScreenPermissions.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
