package com.dreamtvs.neoforge;

//? if neoforge {
import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import com.dreamtvs.client.DreamTvsConfigScreen;
import com.dreamtvs.client.ItemDescription;
import com.dreamtvs.client.RemoteClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only entry point; NeoForge only constructs it on the physical client. */
@Mod(value = DreamTvs.MOD_ID, dist = Dist.CLIENT)
public final class DreamTvsNeoForgeClient {
    public DreamTvsNeoForgeClient(ModContainer container, IEventBus modBus) {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
        // The Config button in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new DreamTvsConfigScreen(parent));
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> ItemDescription.appendTo(event.getItemStack(), event.getToolTip()));

        // The remote's left-click. The event fires for a new press and again every tick the key is held on a block.
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isAttack() && RemoteClient.onAttackKey(Minecraft.getInstance())) {
                event.setSwingHand(false); // RemoteClient swings once per press
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> RemoteClient.onTick(Minecraft.getInstance()));

        // The optional 3D remote model: resourcepacks/remote_3d in the jar, off until picked in Resource Packs.
        modBus.addListener((AddPackFindersEvent event) -> event.addPackFinders(DreamTvs.id("resourcepacks/remote_3d"),
                PackType.CLIENT_RESOURCES, Component.translatable("pack.dreamtvs.remote_3d"), PackSource.BUILT_IN,
                false, Pack.Position.TOP));
    }
}
//?}
