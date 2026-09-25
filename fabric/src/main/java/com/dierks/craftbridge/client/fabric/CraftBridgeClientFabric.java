package com.dierks.craftbridge.client.fabric;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.LinkPayload;
import com.dierks.craftbridge.client.RecipeResults;
import com.dierks.craftbridge.client.jei.CraftCount;
import com.dierks.craftbridge.client.ui.ChestSort;
import com.dierks.craftbridge.client.ui.StoragePanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

/**
 * The Fabric half: register the payload types, and pump the connection's events into the
 * shared client. Nothing about what the mod does lives here.
 */
public final class CraftBridgeClientFabric implements ClientModInitializer {

    private static final String MOD_ID = "craftbridge_client";
    /**
     * {@code AbstractContainerScreen.hoveredSlot}: protected, and Fabric has no getter for it
     * (NeoForge adds one). Read reflectively rather than through an access widener, so the
     * build stays as it is; the runtime names are Mojang's on both versions. Null when it cannot
     * be reached, and then middle-click sorting simply never finds a slot.
     */
    private static final Field HOVERED_SLOT = findHoveredSlot();

    @Override
    public void onInitializeClient() {
        for (String channel : LinkPayload.TO_CLIENT) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            PayloadTypeRegistry.clientboundPlay().register(type, LinkPayload.codec(type));
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    context.client().execute(() ->
                            CraftBridgeClient.get().receive(payload.channel(), payload.data())));
        }
        for (String channel : LinkPayload.TO_SERVER) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            PayloadTypeRegistry.serverboundPlay().register(type, LinkPayload.codec(type));
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                CraftBridgeClient.get().connected(
                        (channel, data) -> ClientPlayNetworking.send(LinkPayload.of(channel, data)),
                        modVersion()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CraftBridgeClient.get().disconnected());
        ClientTickEvents.END_CLIENT_TICK.register(client -> CraftBridgeClient.get().clientTick());

        // The recipes the server synced through Fabric API, read on demand from the current
        // connection's recipe container rather than kept from an event: that container belongs
        // to the connection, so it can never outlive it or leak into the next server.
        RecipeResults.setSource(CraftBridgeClientFabric::syncedRecipes);
        ChestSort.setHoveredSlotLookup(CraftBridgeClientFabric::hoveredSlot);

        // Draw the storage panel over every screen; the panel itself decides whether this one
        // is a crafting menu with a live session.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, tickProgress) ->
                    StoragePanel.render(rendered, graphics));
            // Returning false stops the screen underneath from also seeing the click. In order:
            // the panel (left and right only), a right-click on JEI's [+] (the craft-count
            // prompt), then middle-click sorting. JEI's overlay registers its own in BEFORE_INIT,
            // so a click it takes reaches none of these; its recipe screen handles clicks inside
            // the screen itself, after them.
            ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) ->
                    !StoragePanel.click(clicked, event.x(), event.y(), event.button())
                            && !CraftCount.click(clicked, event.x(), event.y(), event.button())
                            && !ChestSort.click(clicked, event));
            // And the release of that click, or the screen treats it as a release outside its
            // window and drops whatever is on the cursor. Drags in between go the same way.
            ScreenMouseEvents.allowMouseRelease(screen).register((released, event) ->
                    !StoragePanel.release(released, event.button())
                            && !ChestSort.release(released, event.button()));
            ScreenMouseEvents.allowMouseDrag(screen).register((dragged, event, horizontal, vertical) ->
                    !StoragePanel.dragging(dragged, event.button()));
            // The wheel over the panel scrolls it; over JEI's [+] it sets the craft count.
            ScreenMouseEvents.allowMouseScroll(screen).register((scrolled, mouseX, mouseY, horizontal, vertical) ->
                    !StoragePanel.scroll(scrolled, mouseX, mouseY, vertical)
                            && !CraftCount.scroll(scrolled, mouseX, mouseY, vertical));
        });
    }

    private static Collection<RecipeHolder<?>> syncedRecipes() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        // Fabric API mixes FabricRecipeAccess into the client's recipe container; cast through
        // Object so this compiles whatever the container's declared type admits to.
        Object recipes = connection.recipes();
        if (recipes instanceof FabricRecipeAccess access) {
            return access.getSynchronizedRecipes().recipes();
        }
        return List.of();
    }

    private static Field findHoveredSlot() {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        if (HOVERED_SLOT == null) {
            return null;
        }
        try {
            return HOVERED_SLOT.get(screen) instanceof Slot slot ? slot : null;
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
