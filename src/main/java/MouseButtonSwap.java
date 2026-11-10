package mbs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;

@Mod(modid = "mousebuttonswap", name = "Mouse Button Swap", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class MouseButtonSwap {

    Logger logger;
    boolean enabled = true;
    boolean skipIfHolding = true;
    String[] blacklist = new String[0];
    String[] badSlots = {"fake", "ghost", "phantom"};
    boolean warned;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();

        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        try {
            config.load();
            enabled = config.getBoolean("enabled", "general", true, "Turn the mod on or off");
            skipIfHolding = config.getBoolean("skipWhenHoldingItem", "general", true, "Do nothing while holding an item on the cursor");
            blacklist = config.getStringList("guiBlacklist", "general", new String[0], "GUI class names to ignore");
            badSlots = config.getStringList("slotClassKeywords", "general", new String[]{"fake", "ghost", "phantom"}, "Slot classes containing these words are ignored");
        } catch (Exception e) {
            logger.error("config failed to load, using defaults", e);
        } finally {
            if (config.hasChanged()) config.save();
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        try {
            doSwap(event);
        } catch (Exception | LinkageError e) {
            if (!warned) {
                warned = true;
                logger.error("mouse button swap broke", e);
            }
        }
    }

    private void doSwap(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!enabled || event.isCanceled()) return;
        if (!(event.getGui() instanceof GuiContainer) || event.getGui() instanceof GuiContainerCreative) return;
        if (!Mouse.getEventButtonState()) return;

        int button = Mouse.getEventButton();
        if (button < 2) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.playerController == null) return;
        if (skipIfHolding && !mc.player.inventory.getItemStack().isEmpty()) return;

        GuiContainer gui = (GuiContainer) event.getGui();
        String guiName = gui.getClass().getName();

        for (String s : blacklist) {
            s = s.trim();
            if (!s.isEmpty() && guiName.startsWith(s)) return;
        }

        int hotbar = -1;
        for (int i = 0; i < 9 && i < mc.gameSettings.keyBindsHotbar.length; i++) {
            if (mc.gameSettings.keyBindsHotbar[i].isActiveAndMatches(button - 100)) {
                hotbar = i;
                break;
            }
        }
        if (hotbar == -1) return;

        Container container = gui.inventorySlots;
        if (container == null || container != mc.player.openContainer) return;

        Slot slot = gui.getSlotUnderMouse();
        if (slot == null || !slot.isEnabled()) return;

        int id = slot.slotNumber;
        if (id < 0 || id >= container.inventorySlots.size() || container.inventorySlots.get(id) != slot) return;

        String name = slot.getClass().getSimpleName().toLowerCase();
        for (String k : badSlots) {
            k = k.trim().toLowerCase();
            if (!k.isEmpty() && name.contains(k)) return;
        }

        event.setCanceled(true);

        if (slot.inventory == mc.player.inventory && slot.getSlotIndex() == hotbar) return;

        mc.playerController.windowClick(container.windowId, id, hotbar, ClickType.SWAP, mc.player);
    }
}