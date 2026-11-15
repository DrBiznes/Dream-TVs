package com.dreamtvs.neoforge;

//? if neoforge {
import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.CrtTvBlock;
import com.dreamtvs.block.FlatScreen2x1Block;
import com.dreamtvs.block.FlatScreen3x2Block;
import com.dreamtvs.block.FlatScreenBlock;
import com.dreamtvs.block.JumbotronBlock;
import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.compat.CompatIndex;
import com.dreamtvs.compat.CompatRecipes;
import com.dreamtvs.item.RemoteItem;
import com.dreamtvs.item.RemoteRecipe;
import com.dreamtvs.screen.Screens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
//? if >=1.21.2 {
/*import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
*///?} else
import net.neoforged.neoforge.event.AddReloadListenerEvent;
//? if >=26.1 {
/*import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
*///?} else
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Function;
import java.util.function.Supplier;

@Mod(DreamTvs.MOD_ID)
public final class DreamTvsNeoForge {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DreamTvs.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DreamTvs.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DreamTvs.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, DreamTvs.MOD_ID);

    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, DreamTvs.MOD_ID);

    static {
        CONDITIONS.register("recipe_source", () -> RecipeSourceCondition.CODEC);
        RECIPE_SERIALIZERS.register("remote_copy", () -> RemoteRecipe.COPY_SERIALIZER);
        RECIPE_SERIALIZERS.register("remote_unbind", () -> RemoteRecipe.UNBIND_SERIALIZER);
    }

    public static final DeferredBlock<LcdPanelBlock> LCD_PANEL = block("lcd_panel", LcdPanelBlock::new, LcdPanelBlock::defaultProperties);
    public static final DeferredItem<BlockItem> LCD_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LCD_PANEL);
    public static final DeferredBlock<CrtTvBlock> CRT_TV = block("crt_tv", CrtTvBlock::new, CrtTvBlock::defaultProperties);
    public static final DeferredItem<BlockItem> CRT_TV_ITEM = ITEMS.registerSimpleBlockItem(CRT_TV);
    public static final DeferredBlock<JumbotronBlock> JUMBOTRON = block("jumbotron", JumbotronBlock::new, JumbotronBlock::defaultProperties);
    public static final DeferredItem<BlockItem> JUMBOTRON_ITEM = ITEMS.registerSimpleBlockItem(JUMBOTRON);
    public static final DeferredBlock<FlatScreen2x1Block> FLAT_SCREEN_2X1 = block("flat_screen_2x1", FlatScreen2x1Block::new, FlatScreenBlock::defaultProperties);
    public static final DeferredItem<BlockItem> FLAT_SCREEN_2X1_ITEM = ITEMS.registerSimpleBlockItem(FLAT_SCREEN_2X1);
    public static final DeferredBlock<FlatScreen3x2Block> FLAT_SCREEN_3X2 = block("flat_screen_3x2", FlatScreen3x2Block::new, FlatScreenBlock::defaultProperties);
    public static final DeferredItem<BlockItem> FLAT_SCREEN_3X2_ITEM = ITEMS.registerSimpleBlockItem(FLAT_SCREEN_3X2);

    public static final DeferredItem<RemoteItem> REMOTE =
            //? if >=1.21.2 {
            /*ITEMS.registerItem("remote", RemoteItem::new, () -> new Item.Properties().stacksTo(1));
            *///?} else
            ITEMS.registerItem("remote", RemoteItem::new, new Item.Properties().stacksTo(1));

    /** NeoForge 21.2+ takes the properties as a supplier, so it can set the block's id on them first. */
    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                           Supplier<BlockBehaviour.Properties> properties) {
        //? if >=1.21.2 {
        /*return BLOCKS.registerBlock(name, factory, properties);
        *///?} else
        return BLOCKS.registerBlock(name, factory, properties.get());
    }

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
                output.accept(REMOTE);
            })
            .build());

    public DreamTvsNeoForge(IEventBus modBus) {
        DreamTvs.init(FMLPaths.CONFIGDIR.get(), ModList.get()::isLoaded);

        // Compat recipes (docs/COMPAT_RECIPES_PLAN.md). The in-progress items only exist with Create installed,
        // since only Create's sequenced assembly uses them.
        if (CompatRecipes.createLoaded()) {
            for (String path : CompatIndex.transitionalItems(CompatIndex.sources())) {
                //? if >=1.21.2 {
                /*ITEMS.registerSimpleItem(path, () -> new Item.Properties().stacksTo(1));
                *///?} else
                ITEMS.registerSimpleItem(path, new Item.Properties().stacksTo(1));
            }
        }
        // Fires before every data load (world load and /reload), so the choice is made and logged afresh.
        //? if >=1.21.2 {
        /*NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> CompatRecipes.invalidate());
        *///?} else
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> CompatRecipes.invalidate());

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        CONDITIONS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> Screens.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Screens.onServerStopped());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Screens.onServerTickEnd(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) Screens.onChunkLoaded(level, event.getChunk().getPos());
        });

        // HIGHEST so we run before DD's protection listener (NORMAL priority).
        //? if >=26.1 {
        /*NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (BreakBlockEvent event) -> {
        *///?} else
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Screens.beforeBreak(level, event.getPos(), event.getState(), event.getPlayer());
            }
        });
    }
}
//?}
