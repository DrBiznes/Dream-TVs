package com.dreamtvs.fabric;

//? if fabric {
/*import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import com.dreamtvs.client.ItemDescription;
import com.dreamtvs.client.RemoteClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class DreamTvsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> ItemDescription.appendTo(stack, lines));
        // The remote's left-click. Fires every tick the attack key is down; clickCount is 0 while it's held.
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> RemoteClient.onAttack(client, clickCount != 0));
        // The optional 3D remote model: resourcepacks/remote_3d in the jar, off until picked in Resource Packs.
        FabricLoader.getInstance().getModContainer(DreamTvs.MOD_ID).ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(DreamTvs.id("remote_3d"), mod,
                        Component.translatable("pack.dreamtvs.remote_3d"), ResourcePackActivationType.NORMAL));
    }
}
*///?}
