package com.dreamtvs.screen;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.server.ServerDisplay;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvBlock;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Turns TV block changes into Dream Displays screens. Minimal version: one TV = one 1×1 screen.
 * The multiblock solver/reconciler (plan §5.2–5.3) replaces the bodies of these methods in M2.
 */
public final class Screens {
    private Screens() {}

    /** A TV was placed by {@code player}: give it a screen. */
    public static void onPlaced(ServerLevel level, BlockPos pos, Direction facing, ServerPlayer player) {
        if (!DreamDisplaysBridge.isReady()) {
            player.displayClientMessage(Component.translatable("message.dreamtvs.not_ready"), true);
            return;
        }
        ServerDisplay display = DreamDisplaysBridge.create(player.getUUID(), level, pos, pos, facing);
        if (display == null) {
            player.displayClientMessage(Component.translatable("message.dreamtvs.refused"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.dreamtvs.formed", 1, 1), true);
    }

    /** A TV left the world by any means (/setblock, our own break, ...): drop its screen if one is left. */
    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (!DreamDisplaysBridge.isReady()) return;
        ServerDisplay display = DreamDisplaysBridge.managedAt(level, pos);
        if (display != null) delete(display, pos);
    }

    /**
     * Runs before Dream Displays' break protection (plan §5.4). If the player may change this screen we
     * remove it first, so DD finds nothing to protect and the break goes through. If they may not, we leave
     * the screen in place and DD cancels the break.
     */
    public static void beforeBreak(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!(state.getBlock() instanceof TvBlock) || !DreamDisplaysBridge.isReady()) return;
        ServerDisplay display = DreamDisplaysBridge.managedAt(level, pos);
        if (display == null) return;
        if (player instanceof ServerPlayer serverPlayer && !ScreenPermissions.canModify(serverPlayer, display)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dreamtvs.not_yours", ownerName(level, display.getOwnerId())), true);
            return;
        }
        delete(display, pos);
    }

    private static void delete(ServerDisplay display, BlockPos pos) {
        CommandResult result = display.delete();
        if (result != CommandResult.OK) {
            DreamTvs.LOGGER.warn("Dream Displays refused to remove screen {} at {}: {}",
                    display.getId(), pos.toShortString(), result);
        }
    }

    private static String ownerName(ServerLevel level, UUID owner) {
        GameProfileCache cache = level.getServer().getProfileCache();
        if (cache == null) return owner.toString();
        return cache.get(owner).map(GameProfile::getName).orElse(owner.toString());
    }
}
