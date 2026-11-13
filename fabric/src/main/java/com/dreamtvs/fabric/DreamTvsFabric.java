package com.dreamtvs.fabric;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.CrtTvBlock;
import com.dreamtvs.block.FlatScreen2x1Block;
import com.dreamtvs.block.FlatScreen3x2Block;
import com.dreamtvs.block.FlatScreenBlock;
import com.dreamtvs.block.JumbotronBlock;
import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.compat.CompatIndex;
import com.dreamtvs.compat.CompatRecipes;
import com.dreamtvs.screen.Screens;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    public static final Block FLAT_SCREEN_2X1 = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("flat_screen_2x1"),
            new FlatScreen2x1Block(FlatScreenBlock.defaultProperties()));
    public static final Item FLAT_SCREEN_2X1_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("flat_screen_2x1"),
            new BlockItem(FLAT_SCREEN_2X1, new Item.Properties()));
    public static final Block FLAT_SCREEN_3X2 = Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id("flat_screen_3x2"),
            new FlatScreen3x2Block(FlatScreenBlock.defaultProperties()));
    public static final Item FLAT_SCREEN_3X2_ITEM = Registry.register(BuiltInRegistries.ITEM, DreamTvs.id("flat_screen_3x2"),
            new BlockItem(FLAT_SCREEN_3X2, new Item.Properties()));

    /** Our own creative tab, like Create's. */
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DreamTvs.id("main"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.dreamtvs"))
                    .icon(() -> new ItemStack(CRT_TV_ITEM))
                    .displayItems((parameters, output) -> {
                        output.accept(LCD_PANEL_ITEM);
                        output.accept(JUMBOTRON_ITEM);
                        output.accept(CRT_TV_ITEM);
                        output.accept(FLAT_SCREEN_2X1_ITEM);
                        output.accept(FLAT_SCREEN_3X2_ITEM);
                    })
                    .build());

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        DreamTvs.init(loader.getConfigDir(), loader::isModLoaded);

        // Compat recipes (docs/COMPAT_RECIPES_PLAN.md). The in-progress items only exist with Create installed,
        // since only Create's sequenced assembly uses them.
        ResourceConditions.register(RecipeSourceCondition.TYPE);
        if (CompatRecipes.createLoaded()) {
            for (String path : CompatIndex.transitionalItems(CompatIndex.sources())) {
                Registry.register(BuiltInRegistries.ITEM, DreamTvs.id(path), new Item(new Item.Properties().stacksTo(1)));
            }
        }
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resources) -> CompatRecipes.invalidate());

        ServerLifecycleEvents.SERVER_STARTING.register(Screens::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Screens.onServerStopped();
            CompatRecipes.invalidate(); // the next world chooses (and logs) again
        });
        ServerTickEvents.END_SERVER_TICK.register(Screens::onServerTickEnd);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> Screens.onChunkLoaded(level, chunk.getPos()));

        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(EARLY, (level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) Screens.beforeBreak(serverLevel, pos, state, player);
            return true;
        });
    }
}
