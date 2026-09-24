package cn.blockforge.generated.mod2e8abd21;

import cn.blockforge.generated.mod2e8abd21.client.SmartWorkbenchScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 智能工作台：3x3 合成 + 附近存储自动取料。
 * 单独安装即可用原版合成，不需要匠魂。
 */
@Mod(SmartWorkbenchMod.MOD_ID)
public final class SmartWorkbenchMod {

    public static final String MOD_ID = "smart_workbench";

    public SmartWorkbenchMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistries.register(bus);
        ModConfig.register();
        ModNetwork.register();
        bus.addListener(ModRegistries::addToCreativeTab);
        bus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(ModRegistries.SMART_WORKBENCH_MENU.get(), SmartWorkbenchScreen::new));
    }
}
