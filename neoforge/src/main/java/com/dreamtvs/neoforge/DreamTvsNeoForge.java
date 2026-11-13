package com.dreamtvs.neoforge;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.CrtTvBlock;
import com.dreamtvs.block.FlatScreen2x1Block;
import com.dreamtvs.block.FlatScreen3x2Block;
import com.dreamtvs.block.FlatScreenBlock;
import com.dreamtvs.block.JumbotronBlock;
import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.screen.Screens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DreamTvs.MOD_ID)
public final class DreamTvsNeoForge {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DreamTvs.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DreamTvs.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DreamTvs.MOD_ID);

    public static final DeferredBlock<LcdPanelBlock> LCD_PANEL = BLOCKS.registerBlock("lcd_panel", LcdPanelBlock::new, LcdPanelBlock.defaultProperties());
    public static final DeferredItem<BlockItem> LCD_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LCD_PANEL);
    public static final DeferredBlock<CrtTvBlock> CRT_TV = BLOCKS.registerBlock("crt_tv", CrtTvBlock::new, CrtTvBlock.defaultProperties());
    public static final DeferredItem<BlockItem> CRT_TV_ITEM = ITEMS.registerSimpleBlockItem(CRT_TV);
    public static final DeferredBlock<JumbotronBlock> JUMBOTRON = BLOCKS.registerBlock("jumbotron", JumbotronBlock::new, JumbotronBlock.defaultProperties());
    public static final DeferredItem<BlockItem> JUMBOTRON_ITEM = ITEMS.registerSimpleBlockItem(JUMBOTRON);
    public static final DeferredBlock<FlatScreen2x1Block> FLAT_SCREEN_2X1 = BLOCKS.registerBlock("flat_screen_2x1", FlatScreen2x1Block::new, FlatScreenBlock.defaultProperties());
    public static final DeferredItem<BlockItem> FLAT_SCREEN_2X1_ITEM = ITEMS.registerSimpleBlockItem(FLAT_SCREEN_2X1);
    public static final DeferredBlock<FlatScreen3x2Block> FLAT_SCREEN_3X2 = BLOCKS.registerBlock("flat_screen_3x2", FlatScreen3x2Block::new, FlatScreenBlock.defaultProperties());
    public static final DeferredItem<BlockItem> FLAT_SCREEN_3X2_ITEM = ITEMS.registerSimpleBlockItem(FLAT_SCREEN_3X2);

    /** Our own creative tab, like Create's. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dreamtvs"))
            .icon(CRT_TV_ITEM::toStack)
            .displayItems((parameters, output) -> {
                output.accept(LCD_PANEL_ITEM);
                output.accept(JUMBOTRON_ITEM);
                output.accept(CRT_TV_ITEM);
                output.accept(FLAT_SCREEN_2X1_ITEM);
                output.accept(FLAT_SCREEN_3X2_ITEM);
            })
            .build());

    public DreamTvsNeoForge(IEventBus modBus) {
        DreamTvs.init();

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> Screens.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Screens.onServerStopped());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Screens.onServerTickEnd(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) Screens.onChunkLoaded(level, event.getChunk().getPos());
        });

        // HIGHEST so we run before DD's protection listener (NORMAL priority).
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Screens.beforeBreak(level, event.getPos(), event.getState(), event.getPlayer());
            }
        });
    }
}
