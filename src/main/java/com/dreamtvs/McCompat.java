package com.dreamtvs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

import java.util.Optional;
import java.util.UUID;

//? if >=1.21.9 {
/*import net.minecraft.server.players.NameAndId;
*///?} else {
import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.GameProfileCache;
//?}
//? if >=1.21.11 {
/*import net.minecraft.server.permissions.Permissions;
*///?}

/**
 * Small Minecraft API differences between the versions we build for, in one place so the rest of the code
 * stays free of version conditions. Bigger differences (block hooks, rendering, registration) stay where they
 * are, behind Stonecutter comments.
 */
public final class McCompat {
    private McCompat() {}

    /** Pistons can't move the block (26.x renamed {@code BLOCK} to {@code IMMOVEABLE}). */
    public static final PushReaction IMMOVABLE =
            //? if >=26.1 {
            /*PushReaction.IMMOVEABLE;
            *///?} else
            PushReaction.BLOCK;

    /** A message above the hotbar. */
    public static void overlay(Player player, Component message) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else
        player.displayClientMessage(message, true);
    }

    /** The chunk a block is in. */
    public static ChunkPos chunkAt(BlockPos pos) {
        //? if >=26.1 {
        /*return ChunkPos.containing(pos);
        *///?} else
        return new ChunkPos(pos);
    }

    /** A chunk position packed into a long, for map keys. */
    public static long chunkKey(ChunkPos chunk) {
        //? if >=26.1 {
        /*return chunk.pack();
        *///?} else
        return chunk.toLong();
    }

    public static long chunkKey(int x, int z) {
        //? if >=26.1 {
        /*return ChunkPos.pack(x, z);
        *///?} else
        return ChunkPos.asLong(x, z);
    }

    public static boolean isClientSide(Level level) {
        //? if >=1.21.9 {
        /*return level.isClientSide();
        *///?} else
        return level.isClientSide;
    }

    /** {@code minecraft:overworld} and so on: the key's id as a string. */
    public static String keyId(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier().toString();
        *///?} else
        return key.location().toString();
    }

    /** The dimension id Dream Displays uses as its world key. */
    public static String dimensionId(Level level) {
        return keyId(level.dimension());
    }

    /** Operator level 2 (game master), the level Dream Displays falls back to without LuckPerms. */
    public static boolean isGameMaster(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else
        return player.hasPermissions(2);
    }

    /** A player's last known name, from the server's user cache. */
    public static Optional<String> playerName(MinecraftServer server, UUID id) {
        //? if >=1.21.9 {
        /*return server.services().nameToIdCache().get(id).map(NameAndId::name);
        *///?} else {
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(id).map(GameProfile::getName);
        //?}
    }
}
