package mbs;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;

@Mod(modid = "mousebuttonswap", name = "Mouse Button Swap", version = "1.0", acceptedMinecraftVersions = "[1.7.10]")
public class MouseButtonSwap {

    static Logger log;
    static boolean enabled = true;
    static boolean skipIfHolding = true;
    static String[] guiBlacklist = new String[0];
    static String[] slotWords = {"fake", "ghost", "phantom"};
    static boolean warned = false;
    boolean[] was = new boolean[32];

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        log = e.getModLog();
        Configuration cfg = new Configuration(e.getSuggestedConfigurationFile());
        try {
            cfg.load();
            enabled = cfg.getBoolean("enabled", "general", true, "Turn the mod on or off");
            skipIfHolding = cfg.getBoolean("skipWhenHoldingItem", "general", true, "Do nothing while holding an item on the cursor");
            guiBlacklist = cfg.getStringList("guiBlacklist", "general", new String[0], "GUI class names to ignore");
            slotWords = cfg.getStringList("slotClassKeywords", "general", new String[]{"fake", "ghost", "phantom"}, "Slot classes containing these words are ignored");
        } catch (Exception ex) {
            log.error("couldn't load config, using defaults", ex);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
        if (e.getSide() == Side.CLIENT) {
            FMLCommonHandler.instance().bus().register(this);
            log.info("[MBS] loaded");
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        try {
            poll();
        } catch (Exception ex) {
            fail(ex);
        } catch (LinkageError ex) {
            fail(ex);
        }
    }

    private void fail(Throwable t) {
        if (warned) return;
        warned = true;
        log.error("[MBS] swap failed", t);
    }

    private void poll() {
        Minecraft mc = Minecraft.getMinecraft();
        int n = Math.min(Mouse.getButtonCount(), was.length);
        for (int i = 0; i < n; i++) {
            boolean down = Mouse.isButtonDown(i);
            boolean pressed = down && !was[i];
            was[i] = down;
            if (pressed && i >= 2 && mc.currentScreen instanceof GuiContainer) {
                trySwap(mc, (GuiContainer) mc.currentScreen, i);
            }
        }
    }

    private static boolean badSlot(Slot slot) {
        Class<?> c = slot.getClass();
        while (c != null && Slot.class.isAssignableFrom(c)) {
            String n = c.getSimpleName().toLowerCase();
            for (String w : slotWords) {
                if (w == null) continue;
                w = w.trim().toLowerCase();
                if (w.length() > 0 && n.contains(w)) return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static Slot slotUnder(Minecraft mc, GuiContainer gui) {
        int w = gui.width;
        int h = gui.height;
        int mx = Mouse.getX() * w / mc.displayWidth;
        int my = h - Mouse.getY() * h / mc.displayHeight - 1;
        Integer left = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiLeft", "field_147003_i");
        Integer top = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiTop", "field_147009_r");
        int sx = mx - left;
        int sy = my - top;
        for (Object o : gui.inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            if (sx >= s.xDisplayPosition - 1 && sx < s.xDisplayPosition + 17 && sy >= s.yDisplayPosition - 1 && sy < s.yDisplayPosition + 17) return s;
        }
        return null;
    }

    private void trySwap(Minecraft mc, GuiContainer gui, int button) {
        if (!enabled) return;
        if (gui instanceof GuiContainerCreative) return;
        if (mc.thePlayer == null || mc.playerController == null) return;
        if (skipIfHolding && mc.thePlayer.inventory.getItemStack() != null) return;

        String guiName = gui.getClass().getName();
        for (String s : guiBlacklist) {
            if (s == null) continue;
            s = s.trim();
            if (s.length() > 0 && guiName.startsWith(s)) return;
        }

        int hotbar = -1;
        for (int i = 0; i < 9 && i < mc.gameSettings.keyBindsHotbar.length; i++) {
            if (mc.gameSettings.keyBindsHotbar[i].getKeyCode() == button - 100) {
                hotbar = i;
                break;
            }
        }
        if (hotbar < 0) return;

        Container cont = gui.inventorySlots;
        if (cont == null || cont != mc.thePlayer.openContainer) return;

        Slot slot = slotUnder(mc, gui);
        if (slot == null || badSlot(slot)) return;
        if (slot.inventory == mc.thePlayer.inventory && slot.getSlotIndex() == hotbar) return;

        log.info("[MBS] swap slot " + slot.slotNumber + " with hotbar " + hotbar);
        mc.playerController.windowClick(cont.windowId, slot.slotNumber, hotbar, 2, mc.thePlayer);
    }
}
