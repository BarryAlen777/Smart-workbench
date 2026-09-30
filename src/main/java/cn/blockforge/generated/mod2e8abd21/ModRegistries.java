package cn.blockforge.generated.mod2e8abd21;

import cn.blockforge.generated.mod2e8abd21.block.SmartWorkbenchBlock;
import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import cn.blockforge.generated.mod2e8abd21.item.WrenchItem;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 所有注册对象集中在这里：方块、物品、方块实体、菜单。
 */
public final class ModRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.Keys.BLOCKS, SmartWorkbenchMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.Keys.ITEMS, SmartWorkbenchMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, SmartWorkbenchMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.Keys.MENU_TYPES, SmartWorkbenchMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SmartWorkbenchMod.MOD_ID);

    public static final RegistryObject<SmartWorkbenchBlock> SMART_WORKBENCH =
            BLOCKS.register("smart_workbench", () -> new SmartWorkbenchBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5F, 3.0F)
                            .sound(net.minecraft.world.level.block.SoundType.WOOD)));

    public static final RegistryObject<Item> SMART_WORKBENCH_ITEM =
            ITEMS.register("smart_workbench",
                    () -> new BlockItem(SMART_WORKBENCH.get(), new Item.Properties()));

    /** 绑定扳手：手动把容器接到工作台上。 */
    public static final RegistryObject<Item> WRENCH =
            ITEMS.register("wrench",
                    () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    /** 只用作创造标签页图标的隐藏物品：不放进任何标签页，正常游玩拿不到。 */
    public static final RegistryObject<Item> CREATIVE_TAB_ICON =
            ITEMS.register("creative_tab_icon",
                    () -> new Item(new Item.Properties()));

    /** 独立创造标签页：工作台和扳手都收在这里，图标用玩家提供的渲染图。 */
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.smart_workbench"))
                    .icon(() -> new ItemStack(CREATIVE_TAB_ICON.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SMART_WORKBENCH_ITEM.get());
                        output.accept(WRENCH.get());
                    })
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .build());

    public static final RegistryObject<BlockEntityType<SmartWorkbenchBlockEntity>> SMART_WORKBENCH_ENTITY =
            BLOCK_ENTITIES.register("smart_workbench",
                    () -> BlockEntityType.Builder.of(SmartWorkbenchBlockEntity::new, SMART_WORKBENCH.get()).build(null));

    public static final RegistryObject<MenuType<SmartWorkbenchMenu>> SMART_WORKBENCH_MENU =
            MENUS.register("smart_workbench", () -> IForgeMenuType.create(SmartWorkbenchMenu::new));

    private ModRegistries() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
