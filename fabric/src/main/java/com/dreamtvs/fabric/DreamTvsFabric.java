package com.dreamtvs.fabric;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.CrtTvBlock;
import com.dreamtvs.block.JumbotronBlock;
import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.screen.Screens;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

    public static final Block LCD_PANEL = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("lcd_panel"),
            new LcdPanelBlock(LcdPanelBlock.defaultProperties()));
    public static final Item LCD_PANEL_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("lcd_panel"),
            new BlockItem(LCD_PANEL, new Item.Properties()));
    public static final Block CRT_TV = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("crt_tv"),
            new CrtTvBlock(CrtTvBlock.defaultProperties()));
    public static final Item CRT_TV_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("crt_tv"),
            new BlockItem(CRT_TV, new Item.Properties()));
    public static final Block JUMBOTRON = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("jumbotron"),
            new JumbotronBlock(JumbotronBlock.defaultProperties()));
    public static final Item JUMBOTRON_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("jumbotron"),
            new BlockItem(JUMBOTRON, new Item.Properties()));

    @Override
    public void onInitialize() {
        DreamTvs.init();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(LCD_PANEL_ITEM);
            entries.accept(CRT_TV_ITEM);
            entries.accept(JUMBOTRON_ITEM);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(Screens::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Screens.onServerStopped());
        ServerTickEvents.END_SERVER_TICK.register(Screens::onServerTickEnd);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> Screens.onChunkLoaded(level, chunk.getPos()));

        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(EARLY, (level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) Screens.beforeBreak(serverLevel, pos, state, player);
            return true;
        });
    }
}
