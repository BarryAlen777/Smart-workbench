package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.network.C2SPlaceRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 和 REI（物品管理器）对接的反射桥。
 * <p>
 * <b>为什么全用反射：</b>本工程的独立编译器只认 Minecraft 和 Forge，一旦 import REI 的类，
 * 整个工程就编译不过；而玩家不装 REI 时这段代码根本不会被执行，用反射最安全。
 * <p>
 * <b>它解决什么问题：</b>REI 判断「原料够不够」时只数玩家背包和菜单槽位，
 * 看不到我们接在箱子上的 IItemHandler，所以明明箱子里有料还弹「原料不足」。
 * 这里往 REI 注册一个优先级更高的转移处理器：缺料判断把接入存储的快照也算进去，
 * 点加号时不让 REI 搬物品，而是把配方 id 发给服务端，由服务端从存储取料摆进合成格。
 */
public final class ReiTransferBridge {

    /** 比原版工作台处理器（优先级 0）高，先轮到我们 */
    private static final double PRIORITY = 1.0D;

    private static final String HANDLER_NAME = "me.shedaniel.rei.api.client.registry.transfer.TransferHandler";
    private static final String CONTEXT_NAME = "me.shedaniel.rei.api.client.registry.transfer.TransferHandler$Context";
    private static final String RESULT_NAME = "me.shedaniel.rei.api.client.registry.transfer.TransferHandler$Result";
    private static final String APPLICABILITY_NAME =
            "me.shedaniel.rei.api.client.registry.transfer.TransferHandler$ApplicabilityResult";
    private static final String REGISTRY_NAME = "me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry";
    private static final String META_NAME = "me.shedaniel.rei.api.client.registry.transfer.TransferHandlerMeta";
    private static final String DISPLAY_NAME = "me.shedaniel.rei.api.common.display.Display";
    private static final String ENTRY_STACK_NAME = "me.shedaniel.rei.api.common.entry.EntryStack";

    private static boolean prepared;
    private static boolean available;

    private static Class<?> handlerClass;
    private static Class<?> registryClass;
    private static Object handlerProxy;

    private static Method contextGetContainerScreen;
    private static Method contextGetMenu;
    private static Method contextGetDisplay;
    private static Method contextIsActuallyCrafting;
    private static Method contextIsStackedCrafting;
    private static Method displayGetInputEntries;
    private static Method displayGetDisplayLocation;
    private static Method entryStackIsEmpty;
    private static Method entryStackGetValue;
    private static Method resultCreateSuccessful;
    private static Method resultCreateNotApplicable;
    private static Method resultCreateFailed;
    private static Method resultTooltipMissing;
    private static Method resultBlocksFurtherHandling;
    private static Method applicabilityCreateApplicable;
    private static Method applicabilityCreateNotApplicable;
    private static Method applicabilityCreateApplicableWithError;

    private ReiTransferBridge() {
    }

    /**
     * 打开智能工作台界面时调用一次：确保我们的处理器挂在 REI 的注册表里。
     * REI 每次重载都会清空注册表，所以这里每次都检查一遍（已经在就什么都不做）。
     */
    public static void ensureRegistered() {
        if (!prepare()) {
            return;
        }
        try {
            Object registry = registryClass.getMethod("getInstance").invoke(null);
            if (registry instanceof Iterable<?> iterable) {
                for (Object existing : iterable) {
                    if (existing == handlerProxy) {
                        return;
                    }
                }
            }
            registryClass.getMethod("register", handlerClass).invoke(registry, handlerProxy);
        } catch (Throwable ignored) {
            // REI 还没初始化好，等下一次打开界面再补挂
        }
    }

    private static synchronized boolean prepare() {
        if (prepared) {
            return available;
        }
        prepared = true;
        try {
            handlerClass = Class.forName(HANDLER_NAME);
            registryClass = Class.forName(REGISTRY_NAME);
            Class<?> contextClass = Class.forName(CONTEXT_NAME);
            Class<?> resultClass = Class.forName(RESULT_NAME);
            Class<?> applicabilityClass = Class.forName(APPLICABILITY_NAME);
            Class<?> displayClass = Class.forName(DISPLAY_NAME);
            Class<?> entryStackClass = Class.forName(ENTRY_STACK_NAME);

            contextGetContainerScreen = contextClass.getMethod("getContainerScreen");
            contextGetMenu = contextClass.getMethod("getMenu");
            contextGetDisplay = contextClass.getMethod("getDisplay");
            contextIsActuallyCrafting = contextClass.getMethod("isActuallyCrafting");
            contextIsStackedCrafting = contextClass.getMethod("isStackedCrafting");
            displayGetInputEntries = displayClass.getMethod("getInputEntries");
            displayGetDisplayLocation = displayClass.getMethod("getDisplayLocation");
            entryStackIsEmpty = entryStackClass.getMethod("isEmpty");
            entryStackGetValue = entryStackClass.getMethod("getValue");
            resultCreateSuccessful = resultClass.getMethod("createSuccessful");
            resultCreateNotApplicable = resultClass.getMethod("createNotApplicable");
            resultCreateFailed = resultClass.getMethod("createFailed", Component.class);
            resultTooltipMissing = resultClass.getMethod("tooltipMissing", List.class);
            resultBlocksFurtherHandling = resultClass.getMethod("blocksFurtherHandling", boolean.class);
            applicabilityCreateApplicable = applicabilityClass.getMethod("createApplicable");
            applicabilityCreateNotApplicable = applicabilityClass.getMethod("createNotApplicable");
            try {
                applicabilityCreateApplicableWithError =
                        applicabilityClass.getMethod("createApplicableWithError", resultClass);
            } catch (Throwable ignored) {
                // 个别老版本没有这个重载：缺料时退回普通的「适用」，只是少了提示细节
                applicabilityCreateApplicableWithError = null;
            }

            // TransferHandlerMeta 不是所有 REI 版本都有；有就一起实现，没有就只当普通处理器。
            Class<?>[] interfaces;
            try {
                Class<?> metaClass = Class.forName(META_NAME);
                metaClass.getMethod("getAvailableIngredients", contextClass);
                interfaces = new Class<?>[]{handlerClass, metaClass};
            } catch (Throwable ignored) {
                interfaces = new Class<?>[]{handlerClass};
            }

            handlerProxy = Proxy.newProxyInstance(handlerClass.getClassLoader(), interfaces, new Handler());
            available = true;
        } catch (Throwable ignored) {
            // 没装 REI，或者 REI 版本对不上：静默停用，游戏照常玩
            available = false;
        }
        return available;
    }

    private static Object invokeContext(Method method, Object context) throws Exception {
        return method.invoke(context);
    }

    /**
     * REI 每渲染一帧都会问一次，一旦这里抛异常它会在控制台每帧打一次堆栈。
     * 所以统一兜一层底：出问题就退回「不适用」，交给原版处理器。
     */
    private static Object safeCheckApplicable(Object context) {
        try {
            return checkApplicable(context);
        } catch (Throwable ignored) {
            return notApplicable();
        }
    }

    private static Object safeHandle(Object context) {
        try {
            return handle(context);
        } catch (Throwable ignored) {
            return notApplicableResult();
        }
    }

    private static Object notApplicable() {
        try {
            return applicabilityCreateNotApplicable.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object notApplicableResult() {
        try {
            return resultCreateNotApplicable.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object checkApplicable(Object context) throws Exception {
        Object screen = invokeContext(contextGetContainerScreen, context);
        Object menu = invokeContext(contextGetMenu, context);
        boolean ours = screen instanceof cn.blockforge.generated.mod2e8abd21.client.SmartWorkbenchScreen
                && menu instanceof cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
        if (!ours) {
            return applicabilityCreateNotApplicable.invoke(null);
        }
        Object display = invokeContext(contextGetDisplay, context);
        cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu workbench =
                (cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu) menu;
        // 客户端还没收到存储快照（刚打开界面那一瞬）：本地根本不知道箱子里有什么，
        // 此时的「缺料」结论不作数，先放行；真正点击时由服务端拿真实库存裁决。
        if (!workbench.hasClientStorageSnapshot()) {
            return applicabilityCreateApplicable.invoke(null);
        }
        List<Object> missing = findMissing(display,
                MaterialAvailability.collectAvailable(workbench, Minecraft.getInstance().player));
        if (missing.isEmpty()) {
            return applicabilityCreateApplicable.invoke(null);
        }
        // 缺料时也必须咬住「这个配方归我管」，只是附一条缺料提示。
        // 这里一旦返回 notApplicable，REI 就会接着去问原版工作台的处理器：
        // 那个处理器只数玩家背包和菜单槽位，看不到我们接在箱子上的库存，
        // 于是一个红色「材料不足」的加号——箱子里明明有料也没用。
        if (applicabilityCreateApplicableWithError != null) {
            Object failed = resultBlocksFurtherHandling.invoke(failedResult(missing), false);
            return applicabilityCreateApplicableWithError.invoke(null, failed);
        }
        return applicabilityCreateApplicable.invoke(null);
    }

    /** 造一个「缺了哪几样」的失败结果，REI 会把它画成加号上的缺料提示。 */
    private static Object failedResult(List<Object> missing) throws Exception {
        Object failed = resultCreateFailed.invoke(null, Component.translatableWithFallback(
                "gui." + SmartWorkbenchMod.MOD_ID + ".msg.transfer_missing",
                "接入存储和背包里还缺 %s 种材料", missing.size()));
        return resultTooltipMissing.invoke(failed, missing);
    }

    /**
     * 告诉 REI「这名玩家手上能用的材料到底有哪些」。
     * <p>
     * REI 的 {@code TransferHandlerMeta} 只给「可合成过滤器」和加号旁边的快速转移提示用；
     * 不实现它，REI 就假定只有玩家背包里的东西算数——箱子、精妙背包里的料全被无视。
     * 合成格 + 服务器推来的存储快照 + 玩家背包，三样都要算进去，否则会反过来漏掉玩家身上的材料。
     */
    private static Object availableIngredients(Object context) {
        try {
            Object menu = invokeContext(contextGetMenu, context);
            if (!(menu instanceof cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu workbench)) {
                return List.of();
            }
            // collectAvailable 已经把「合成格 + 接入存储快照 + 玩家背包」都算进去了，
            // 这里不要再单独加一遍玩家背包，否则同一件材料会被数两次。
            return MaterialAvailability.collectAvailable(workbench, Minecraft.getInstance().player);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static Object handle(Object context) throws Exception {
        Object menu = invokeContext(contextGetMenu, context);
        if (!(menu instanceof cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu workbench)) {
            return resultCreateNotApplicable.invoke(null);
        }
        Object display = invokeContext(contextGetDisplay, context);
        boolean crafting = (Boolean) invokeContext(contextIsActuallyCrafting, context);
        if (crafting) {
            // 必须在这里拦截缺料，而不是无条件返回成功；否则 REI 会继续交给原版处理器，
            // 玩家明明缺木棍时仍然能看见可点击的“+”按钮。
            List<Object> missing = findMissing(display,
                    MaterialAvailability.collectAvailable(workbench, Minecraft.getInstance().player));
            if (!missing.isEmpty()) {
                return resultBlocksFurtherHandling.invoke(failedResult(missing), true);
            }
            Object screen = invokeContext(contextGetContainerScreen, context);
            if (screen instanceof Screen mcScreen) {
                // 和 REI 自带处理器一样：先收起物品管理器，回到工作台界面看结果
                Minecraft.getInstance().setScreen(mcScreen);
            }
            ResourceLocation id = displayLocation(display);
            if (id != null) {
                boolean stacked = (Boolean) invokeContext(contextIsStackedCrafting, context);
                ModNetwork.sendToServer(new C2SPlaceRecipePacket(workbench.getBenchPos(), id, stacked));
            }
        }
        return resultCreateSuccessful.invoke(null);
    }

    /**
     * 配方的每个材料（EntryIngredient 是「多选一」）整理成候选物品清单，
     * 缺料判断交给三家物品管理器共用的 {@link MaterialAvailability}，保证和 JEI / EMI 完全一致。
     */
    private static List<Object> findMissing(Object display, List<ItemStack> available) throws Exception {
        List<?> rawInputs = (List<?>) displayGetInputEntries.invoke(display);
        List<List<ItemStack>> candidates = new ArrayList<>();
        List<Object> rawEntries = new ArrayList<>();
        for (Object entry : rawInputs) {
            if (!(entry instanceof List<?> list)) {
                continue;
            }
            List<ItemStack> options = new ArrayList<>();
            for (Object stack : list) {
                ItemStack item = entryItem(stack);
                if (item != null) {
                    options.add(item);
                }
            }
            if (!options.isEmpty()) {
                candidates.add(options);
                rawEntries.add(entry);
            }
        }
        boolean[] matched = MaterialAvailability.match(candidates, available);
        List<Object> missing = new ArrayList<>();
        for (int i = 0; i < matched.length; i++) {
            if (!matched[i]) {
                missing.add(rawEntries.get(i));
            }
        }
        return missing;
    }

    /** 从 REI 的 EntryStack 里取出 ItemStack；不是物品条目就返回 null。 */
    private static ItemStack entryItem(Object stack) {
        if (stack == null) {
            return null;
        }
        try {
            if ((Boolean) entryStackIsEmpty.invoke(stack)) {
                return null;
            }
            Object value = entryStackGetValue.invoke(stack);
            return value instanceof ItemStack item ? item : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceLocation displayLocation(Object display) {
        try {
            Object value = displayGetDisplayLocation.invoke(display);
            if (value instanceof Optional<?> optional && optional.orElse(null) instanceof ResourceLocation id) {
                return id;
            }
        } catch (Throwable ignored) {
            // 拿不到配方 id 就不发包
        }
        return null;
    }

    /** 我们注册给 REI 的处理器本体；REI 只调 getPriority / compareTo / checkApplicable / handle。 */
    private static final class Handler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getPriority":
                    return PRIORITY;
                case "compareTo":
                    // 和接口默认实现一致：REI 用 Comparator.reverseOrder() 排序，高优先级会排到前面
                    return Double.compare(PRIORITY, otherPriority(args[0]));
                case "checkApplicable":
                    return safeCheckApplicable(args[0]);
                case "handle":
                    return safeHandle(args[0]);
                case "getAvailableIngredients":
                    // TransferHandlerMeta 的方法：只在 REI 支持它时才会被调到
                    return availableIngredients(args[0]);
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "SmartWorkbenchReiTransferHandler";
                default:
                    return null;
            }
        }

        private static double otherPriority(Object other) {
            try {
                return ((Number) handlerClass.getMethod("getPriority").invoke(other)).doubleValue();
            } catch (Throwable ignored) {
                return 0.0D;
            }
        }
    }
}
