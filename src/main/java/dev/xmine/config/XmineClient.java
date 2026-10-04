package dev.xmine.config;

import dev.xmine.config.configuration.ConfigManager;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.ModuleRegistry;
import dev.xmine.config.gui.ClickGuiScreen;
import dev.xmine.config.hud.HudManager;
import dev.xmine.config.hud.NotificationManager;
import dev.xmine.config.input.KeybindManager;
import dev.xmine.config.module.ChatHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod entry point: wires the managers together. Everything else lives in its own system. */
public class XmineClient implements ClientModInitializer {
    public static final String MOD_ID = "xmineconfig";
    public static final String NAME = "XMINE CONFIG";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static KeyBinding openKey;

    /** The vanilla-registered key that opens/closes the GUI (default Right Shift, rebindable in Controls). */
    public static KeyBinding openKey() { return openKey; }

    @Override
    public void onInitializeClient() {
        ModuleRegistry.registerDefaults(ModuleManager.INSTANCE);
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.xmineconfig.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.xmineconfig"));
        ConfigManager.load();
        ChatHooks.init();

        ClientTickEvents.END_CLIENT_TICK.register(XmineClient::onTick);
        HudRenderCallback.EVENT.register(XmineClient::onHud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> ConfigManager.saveNow());
        LOGGER.info("{} {} initialised with {} modules", NAME, VERSION, ModuleManager.INSTANCE.all().size());
    }

    private static void onTick(MinecraftClient mc) {
        ConfigManager.tick();
        NotificationManager.tick();
        KeybindManager.poll(mc);
        while (openKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
        }
        if (mc.player == null || mc.world == null) return;
        ModuleManager.INSTANCE.tick();
    }

    private static void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        HudManager.frame();
        if (mc.options.hudHidden) return;
        ModuleManager.INSTANCE.renderHud(ctx, counter.getTickDelta(true));
    }
}
