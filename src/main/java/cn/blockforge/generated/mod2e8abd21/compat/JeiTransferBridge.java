package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SPlaceRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 和 JEI（物品管理器）对接的反射桥。
 * <p>
 * <b>为什么全用反射：</b>本工程的独立编译器只认 Minecraft 和 Forge，一旦 import JEI 的类，
 * 整个工程就编译不过；而玩家不装 JEI 时这段代码根本不会被执行，用反射最安全。
 * <p>
 * <b>它解决什么问题：</b>JEI 判断「原料够不够」时只数玩家背包和菜单槽位，看不到我们接在箱子上的
 * 存储，所以明明箱子里有料也会报缺料、合成按钮点不动。这里往 JEI 已建好的转移处理器表里塞一份
 * 我们自己的处理器：缺料判断把接入存储算进去，点合成按钮时不让 JEI 搬物品，而是把配方 id
 * 发给服务端，由服务端从存储取料摆进合成格（和 REI 桥走同一个网络包）。
 * <p>
 * JEI 15.x 的转移接口改过签名（旧版 6 个参数、15.57 起多了 2 个参数的默认方法），
 * 反射桥能同时认这两套，装哪个版本的 JEI 都不会链接失败。
 */
public final class JeiTransferBridge {

    private static final String HANDLER_NAME = "mezz.jei.api.recipe.transfer.IRecipeTransferHandler";
    private static final String ERROR_NAME = "mezz.jei.api.recipe.transfer.IRecipeTransferError";
    private static final String ERROR_TYPE_NAME = "mezz.jei.api.recipe.transfer.IRecipeTransferError$Type";
    private static final String CONTEXT_NAME = "mezz.jei.api.recipe.transfer.IRecipeTransferContext";
    private static final String INTERNAL_NAME = "mezz.jei.common.Internal";
    private static final String RUNTIME_NAME = "mezz.jei.api.runtime.IJeiRuntime";
    private static final String MANAGER_NAME = "mezz.jei.library.recipes.RecipeTransferManager";
    private static final String RECIPE_TYPES_NAME = "mezz.jei.api.constants.RecipeTypes";
    private static final String TOOLTIP_BUILDER_NAME = "mezz.jei.api.gui.builder.ITooltipBuilder";

    private static boolean prepared;
    private static boolean available;

    private static Class<?> handlerClass;
    private static Class<?> errorClass;
    private static Object userFacingType;
    private static Object craftingRecipeType;
    private static Object handlerProxy;

    private static Method optionalRuntimeMethod;
    private static Method runtimeMethod;
    private static Method getTransferManagerMethod;
    private static Field handlersField;
    private static Method tooltipAddAllMethod;
    private static Method contextGetContainer;
    private static Method contextGetRecipe;
    private static Method contextIsMaxTransfer;

    /** 最近一次已经挂过处理器的 JEI 运行时管理器；换了新的（JEI 重载）才需要重挂。 */
    private static Object lastManager;

    private JeiTransferBridge() {
    }

    /**
     * 打开智能工作台界面时调用：确保我们的处理器在 JEI 的转移处理器表里。
     * JEI 重载会重建这张表，所以界面每 tick 都来问一次（已经在就只做一次字段比较，几乎不花钱）。
     */
    public static void ensureRegistered() {
        if (!prepare()) {
            return;
        }
        try {
            Object runtime = currentRuntime();
            if (runtime == null) {
                return;
            }
            Object manager = getTransferManagerMethod.invoke(runtime);
            if (manager == null || manager == lastManager) {
                return;
            }
            Object existing = handlersField.get(manager);
            if (existing == null) {
                return;
            }
            if (tableContains(existing, SmartWorkbenchMenu.class, craftingRecipeType)) {
                lastManager = manager;
                return;
            }
            handlersField.set(manager, rebuildTable(existing));
            lastManager = manager;
        } catch (Throwable ignored) {
            // JEI 还没起好或版本对不上：下次界面 tick 再试
        }
    }

    private static synchronized boolean prepare() {
        if (prepared) {
            return available;
        }
        prepared = true;
        try {
            handlerClass = Class.forName(HANDLER_NAME);
            errorClass = Class.forName(ERROR_NAME);
            userFacingType = Class.forName(ERROR_TYPE_NAME).getField("USER_FACING").get(null);
            craftingRecipeType = Class.forName(RECIPE_TYPES_NAME).getField("CRAFTING").get(null);

            Class<?> internalClass = Class.forName(INTERNAL_NAME);
            optionalRuntimeMethod = findMethod(internalClass, "getOptionalJeiRuntime");
            runtimeMethod = internalClass.getMethod("getJeiRuntime");
            getTransferManagerMethod = Class.forName(RUNTIME_NAME).getMethod("getRecipeTransferManager");
            handlersField = findHandlersField(Class.forName(MANAGER_NAME));
            tooltipAddAllMethod = findMethod(Class.forName(TOOLTIP_BUILDER_NAME), "addAll", Collection.class);

            // 15.57 起的新上下文；老版本没有这个接口，拿不到就只认 6 个参数的老签名
            Class<?> contextClass = findClass(CONTEXT_NAME);
            if (contextClass != null) {
                contextGetContainer = contextClass.getMethod("getContainer");
                contextGetRecipe = contextClass.getMethod("getRecipe");
                contextIsMaxTransfer = contextClass.getMethod("isMaxTransfer");
            }

            handlerProxy = Proxy.newProxyInstance(handlerClass.getClassLoader(),
                    new Class<?>[]{handlerClass}, new Handler());
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 表字段没有固定名字也不要紧：认类型里带 Table 的那个。 */
    private static Field findHandlersField(Class<?> managerClass) throws NoSuchFieldException {
        for (Field field : managerClass.getDeclaredFields()) {
            if (field.getType().getName().endsWith("Table")) {
                field.setAccessible(true);
                return field;
            }
        }
        Field field = managerClass.getDeclaredField("recipeTransferHandlers");
        field.setAccessible(true);
        return field;
    }

    private static Object currentRuntime() throws Exception {
        if (optionalRuntimeMethod != null) {
            Object optional = optionalRuntimeMethod.invoke(null);
            return optional instanceof Optional<?> value ? value.orElse(null) : null;
        }
        return runtimeMethod.invoke(null);
    }

    private static boolean tableContains(Object table, Object row, Object column) throws Exception {
        Class<?> tableClass = Class.forName("com.google.common.collect.Table");
        Object result = tableClass.getMethod("contains", Object.class, Object.class).invoke(table, row, column);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Guava 的表是只读的，想加一行就得照原样抄一份再补上我们的那一行。
     * 这里刻意不 import Guava，全部按名字反射，避免给工程加依赖。
     */
    private static Object rebuildTable(Object table) throws Exception {
        Class<?> tableClass = Class.forName("com.google.common.collect.Table");
        Class<?> immutableTableClass = Class.forName("com.google.common.collect.ImmutableTable");
        Class<?> hashBasedTableClass = Class.forName("com.google.common.collect.HashBasedTable");
        Class<?> cellClass = Class.forName("com.google.common.collect.Table$Cell");

        Object copy = hashBasedTableClass.getMethod("create").invoke(null);
        Method put = tableClass.getMethod("put", Object.class, Object.class, Object.class);
        Method cellSet = tableClass.getMethod("cellSet");
        Method getRowKey = cellClass.getMethod("getRowKey");
        Method getColumnKey = cellClass.getMethod("getColumnKey");
        Method getValue = cellClass.getMethod("getValue");

        for (Object cell : (Collection<?>) cellSet.invoke(table)) {
            put.invoke(copy, getRowKey.invoke(cell), getColumnKey.invoke(cell), getValue.invoke(cell));
        }
        put.invoke(copy, SmartWorkbenchMenu.class, craftingRecipeType, handlerProxy);
        return immutableTableClass.getMethod("copyOf", tableClass).invoke(null, copy);
    }

    /* ---------------- 处理器本体 ---------------- */

    /** 我们塞给 JEI 的转移处理器；JEI 只调 getContainerClass / getMenuType / getRecipeType / transferRecipe。 */
    private static final class Handler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getContainerClass":
                    return SmartWorkbenchMenu.class;
                case "getMenuType":
                    return Optional.of(ModRegistries.SMART_WORKBENCH_MENU.get());
                case "getRecipeType":
                    return craftingRecipeType;
                case "transferRecipe":
                    return safeTransfer(args);
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "SmartWorkbenchJeiTransferHandler";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static Object safeTransfer(Object[] args) {
        try {
            return transfer(args);
        } catch (Throwable ignored) {
            // 出问题就当没这回事，把按钮交回给 JEI 自己显示，别把界面搞崩
            return null;
        }
    }

    private static Object transfer(Object[] args) throws Exception {
        if (args == null) {
            return null;
        }
        Object container;
        Object recipe;
        boolean maxTransfer;
        boolean doTransfer;
        if (args.length == 2 && contextGetContainer != null) {
            // 15.57 起：transferRecipe(IRecipeTransferContext, boolean)
            Object context = args[0];
            container = contextGetContainer.invoke(context);
            recipe = contextGetRecipe.invoke(context);
            maxTransfer = Boolean.TRUE.equals(contextIsMaxTransfer.invoke(context));
            doTransfer = Boolean.TRUE.equals(args[1]);
        } else {
            // 老签名：transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer)
            container = args[0];
            recipe = args[1];
            maxTransfer = args.length > 4 && Boolean.TRUE.equals(args[4]);
            doTransfer = args.length > 5 && Boolean.TRUE.equals(args[5]);
        }
        if (!(container instanceof SmartWorkbenchMenu menu)) {
            return null;
        }
        if (!(recipe instanceof Recipe<?> mcRecipe) || !menu.canAutoPlace(mcRecipe)) {
            return userError(1, Component.translatableWithFallback(
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported",
                    "这个配方不支持一键取料"));
        }
        List<ItemStack> missing = findMissing(mcRecipe, MaterialAvailability.collectAvailable(menu));
        // 快照只用于 JEI 的悬停提示；真正点击时必须交给服务端重新读取存储，
        // 否则箱子刚被漏斗补料就会被旧快照误判为缺料。
        if (!missing.isEmpty() && !doTransfer) {
            return userError(missing.size(), Component.translatableWithFallback(
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.transfer_missing",
                    "接入存储里还缺 %s 种材料", missing.size()));
        }
        if (doTransfer) {
            ResourceLocation id = mcRecipe.getId();
            if (id != null) {
                ModNetwork.sendToServer(new C2SPlaceRecipePacket(menu.getBenchPos(), id, maxTransfer));
            }
        }
        return null;
    }

    private static List<ItemStack> findMissing(Recipe<?> recipe, List<ItemStack> available) {
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return List.of();
        }
        NonNullList<Ingredient> ingredients = crafting.getIngredients();
        List<List<ItemStack>> candidates = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<ItemStack> options = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    options.add(stack);
                }
            }
            if (!options.isEmpty()) {
                candidates.add(options);
            }
        }
        boolean[] matched = MaterialAvailability.match(candidates, available);
        List<ItemStack> missing = new ArrayList<>();
        for (int i = 0; i < matched.length; i++) {
            if (!matched[i]) {
                missing.add(candidates.get(i).get(0));
            }
        }
        return missing;
    }

    private static void notifyPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatableWithFallback(
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough",
                    "附近存储材料不足"), true);
        }
    }

    /** 缺料时给 JEI 的「用户可见错误」：按钮会变红并显示我们的提示。 */
    private static Object userError(int missingCount, Component message) {
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(message);
        return Proxy.newProxyInstance(errorClass.getClassLoader(), new Class<?>[]{errorClass},
                new ErrorHandler(tooltips, missingCount));
    }

    private static final class ErrorHandler implements InvocationHandler {

        private final List<Component> tooltips;
        private final int missingCount;

        private ErrorHandler(List<Component> tooltips, int missingCount) {
            this.tooltips = tooltips;
            this.missingCount = missingCount;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getType":
                    return userFacingType;
                case "getTooltip":
                    if (args != null && args.length == 1 && tooltipAddAllMethod != null) {
                        try {
                            tooltipAddAllMethod.invoke(args[0], tooltips);
                        } catch (Throwable ignored) {
                            // 提示画不出来就算了，按钮状态仍然是对的
                        }
                        return null;
                    }
                    return tooltips;
                case "getMissingCountHint":
                    return missingCount;
                case "showError":
                    return null;
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "SmartWorkbenchJeiTransferError";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }
}
