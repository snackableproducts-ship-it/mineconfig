package dev.xmine.config.module;

import dev.xmine.config.core.ModuleManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/** Registers the Fabric chat events once; each handler checks whether its module is enabled. */
public final class ChatHooks {
    private ChatHooks() {}

    public static void init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> overlay || !blocked(msg.getString()));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signed, sender, params, time) -> !blocked(msg.getString()));
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> { if (!overlay) gg(msg.getString()); });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, time) -> gg(msg.getString()));
    }

    private static boolean blocked(String s) {
        MiscModules.ChatFilter f = ModuleManager.INSTANCE.get(MiscModules.ChatFilter.class);
        return f != null && f.isEnabled() && f.blocks(s);
    }

    private static void gg(String s) {
        MiscModules.AutoGG g = ModuleManager.INSTANCE.get(MiscModules.AutoGG.class);
        if (g != null && g.isEnabled()) g.onMessage(s);
    }
}
