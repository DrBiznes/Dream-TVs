package com.dreamtvs.fabric;

//? if fabric {
/*import com.dreamtvs.DreamTvs;
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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
//? if >=26.1 {
/^import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
^///?} else
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public final class DreamTvsFabric implements ModInitializer {
    /^* Our break hook runs in this phase, ordered before the default phase DD registers in. ^/
    private static final ResourceLocation EARLY = DreamTvs.id("early");

    public static final Block LCD_PANEL = block("lcd_panel", LcdPanelBlock::new, LcdPanelBlock.defaultProperties());
    public static final Item LCD_PANEL_ITEM = blockItem("lcd_panel", LCD_PANEL);
    public static final Block CRT_TV = block("crt_tv", CrtTvBlock::new, CrtTvBlock.defaultProperties());
    public static final Item CRT_TV_ITEM = blockItem("crt_tv", CRT_TV);
    public static final Block JUMBOTRON = block("jumbotron", JumbotronBlock::new, JumbotronBlock.defaultProperties());
    public static final Item JUMBOTRON_ITEM = blockItem("jumbotron", JUMBOTRON);
    public static final Block FLAT_SCREEN_2X1 = block("flat_screen_2x1", FlatScreen2x1Block::new, FlatScreenBlock.defaultProperties());
    public static final Item FLAT_SCREEN_2X1_ITEM = blockItem("flat_screen_2x1", FLAT_SCREEN_2X1);
    public static final Block FLAT_SCREEN_3X2 = block("flat_screen_3x2", FlatScreen3x2Block::new, FlatScreenBlock.defaultProperties());
    public static final Item FLAT_SCREEN_3X2_ITEM = blockItem("flat_screen_3x2", FLAT_SCREEN_3X2);

    /^* Since 1.21.2 a block's and item's properties must carry its registry id. ^/
    private static Block block(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        //? if >=1.21.2
        //properties.setId(ResourceKey.create(Registries.BLOCK, DreamTvs.id(path)));
        return Registry.register(BuiltInRegistries.BLOCK, DreamTvs.id(path), factory.apply(properties));
    }

    private static Item item(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
        //? if >=1.21.2
        //properties.setId(ResourceKey.create(Registries.ITEM, DreamTvs.id(path)));
        return Registry.register(BuiltInRegistries.ITEM, DreamTvs.id(path), factory.apply(properties));
    }

    public static final Item REMOTE = item("remote", RemoteItem::new, new Item.Properties().stacksTo(1));

    private static Item blockItem(String path, Block block) {
        //? if >=1.21.2 {
        /^return item(path, properties -> new BlockItem(block, properties), new Item.Properties().useBlockDescriptionPrefix());
        ^///?} else
        return item(path, properties -> new BlockItem(block, properties), new Item.Properties());
    }

    /^* Our own creative tab, like Create's. ^/
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DreamTvs.id("main"),
            //? if >=26.1 {
            /^FabricCreativeModeTab.builder()
            ^///?} else
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.dreamtvs"))
                    .icon(() -> new ItemStack(CRT_TV_ITEM))
                    .displayItems((parameters, output) -> {
                        output.accept(LCD_PANEL_ITEM);
                        output.accept(JUMBOTRON_ITEM);
                        output.accept(CRT_TV_ITEM);
                        output.accept(FLAT_SCREEN_2X1_ITEM);
                        output.accept(FLAT_SCREEN_3X2_ITEM);
                        output.accept(REMOTE);
                    })
                    .build());

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        DreamTvs.init(loader.getConfigDir(), loader::isModLoaded);

        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, DreamTvs.id("remote_copy"), RemoteRecipe.COPY_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, DreamTvs.id("remote_unbind"), RemoteRecipe.UNBIND_SERIALIZER);

        // Compat recipes (docs/COMPAT_RECIPES_PLAN.md). The in-progress items only exist with Create installed,
        // since only Create's sequenced assembly uses them.
        ResourceConditions.register(RecipeSourceCondition.TYPE);
        if (CompatRecipes.createLoaded()) {
            for (String path : CompatIndex.transitionalItems(CompatIndex.sources())) {
                item(path, Item::new, new Item.Properties().stacksTo(1));
            }
        }
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resources) -> CompatRecipes.invalidate());

        ServerLifecycleEvents.SERVER_STARTING.register(Screens::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Screens.onServerStopped();
            CompatRecipes.invalidate(); // the next world chooses (and logs) again
        });
        ServerTickEvents.END_SERVER_TICK.register(Screens::onServerTickEnd);
        //? if >=26.1 {
        /^ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Screens.onChunkLoaded(level, chunk.getPos()));
        ^///?} else
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> Screens.onChunkLoaded(level, chunk.getPos()));

        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(EARLY, (level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) Screens.beforeBreak(serverLevel, pos, state, player);
            return true;
        });
    }
}
*///?}
