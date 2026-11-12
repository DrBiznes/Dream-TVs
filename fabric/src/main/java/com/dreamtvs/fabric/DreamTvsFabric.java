package com.dreamtvs.fabric;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvBlock;
import com.dreamtvs.screen.Screens;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class DreamTvsFabric implements ModInitializer {
    /** Our break hook runs in this phase, ordered before the default phase DD registers in. */
    private static final ResourceLocation EARLY = DreamTvs.id("early");

    public static final Block TV = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("tv"),
            new TvBlock(TvBlock.defaultProperties()));
    public static final Item TV_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("tv"),
            new BlockItem(TV, new Item.Properties()));

    @Override
    public void onInitialize() {
        DreamTvs.init();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(TV_ITEM));

        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(EARLY, (level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) Screens.beforeBreak(serverLevel, pos, state, player);
            return true;
        });
    }
}
