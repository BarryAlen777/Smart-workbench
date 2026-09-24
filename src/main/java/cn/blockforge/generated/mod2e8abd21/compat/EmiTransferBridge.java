package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SPlaceRecipePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 和 EMI（物品管理器）对接的反射桥。
 * <p>
 * <b>为什么全用反射：</b>本工程的独立编译器只认 Minecraft 和 Forge，EMI 的构件也不在本机的
 * Maven 缓存里，一旦 import EMI 的类就编译不过；玩家不装 EMI 时这段代码根本不会执行。
 * <p>
 * <b>它解决什么问题：</b>EMI 的「填充配方」只认屏幕上的槽位，看不到我们接在箱子上的存储，
 * 所以明明箱子里有料也算不出可合成、填充按钮是灰的。这里往 EMI 的配方处理器表里塞一份
 * 我们自己的处理器：可不可合成按「玩家背包 + 合成格 + 接入存储」一起算，真的点填充时
 * 不让 EMI 在客户端搬物品，而是把配方 id 发给服务端，由服务端从存储取料摆进合成格。
 */
public final class EmiTransferBridge {

    private static final String HANDLER_NAME = "dev.emi.emi.api.recipe.handler.EmiRecipeHandler";
    private static final String FILLER_NAME = "dev.emi.emi.registry.EmiRecipeFiller";
    private static final String CATEGORIES_NAME = "dev.emi.emi.api.recipe.VanillaEmiRecipeCategories";
    private static final String RECIPE_NAME = "dev.emi.emi.api.recipe.EmiRecipe";
    private static final String CONTEXT_NAME = "dev.emi.emi.api.recipe.handler.EmiCraftContext";
    private static final String INVENTORY_NAME = "dev.emi.emi.api.recipe.EmiPlayerInventory";
    private static final String STACK_NAME = "dev.emi.emi.api.stack.EmiStack";
    private static final String INGREDIENT_NAME = "dev.emi.emi.api.stack.EmiIngredient";

    private static boolean prepared;
    private static boolean available;

    private static Class<?> handlerClass;
    private static Object handlerProxy;

    private static Field handlersField;
    private static Field craftingCategoryField;
    private static Method stackOfMethod;
    private static Constructor<?> inventoryConstructor;
    private static Method recipeGetInputsMethod;
    private static Method recipeGetCategoryMethod;
    private static Method recipeGetIdMethod;
    private static Method ingredientGetStacksMethod;
    private static Method stackGetItemMethod;
    private static Method stackIsEmptyMethod;
    private static Method contextGetScreenHandlerMethod;
    private static Method contextGetAmountMethod;

    /** 当前打开的智能工作台菜单；EMI 的 getInventory 只拿到屏幕对象，用它补上合成格和存储快照。 */
    private static SmartWorkbenchMenu activeMenu;

    /** 最近一次挂过处理器的 EMI 处理器表；EMI 重载会换一张全新的表，那时要重挂。 */
    private static Object lastMap;

    /** 从 EMI 接口泛型里推出来的提示组件工厂，失败就一直为 null（不显示提示而已）。 */
    private static Method tooltipFactory;

    private EmiTransferBridge() {
    }

    /**
     * 打开智能工作台界面时调用：记下当前菜单，并确保我们的处理器在 EMI 的配方处理器表里。
     * EMI 重载会重建这张表，所以界面每 tick 都来问一次（已经在就只做一次字段比较）。
     */
    public static void ensureRegistered(SmartWorkbenchMenu menu) {
        activeMenu = menu;
        if (!prepare()) {
            return;
        }
        try {
            Object map = handlersField.get(null);
            if (!(map instanceof Map<?, ?>)) {
                return;
            }
            if (map == lastMap) {
                return;
            }
            Map<Object, Object> raw = castMap(map);
            Object key = ModRegistries.SMART_WORKBENCH_MENU.get();
            Object existing = raw.get(key);
            if (existing instanceof List<?> list && list.contains(handlerProxy)) {
                lastMap = map;
                return;
            }
            List<Object> handlers = new ArrayList<>();
            if (existing instanceof List<?> previous) {
                handlers.addAll(castList(previous));
            }
            // 放最前面，EMI 取第一个能用的处理器时会先轮到我们
            handlers.add(0, handlerProxy);
            raw.put(key, handlers);
            lastMap = map;
        } catch (Throwable ignored) {
            // EMI 还没起好或版本对不上：下次界面 tick 再试
        }
    }

    /** 界面关闭时清掉菜单引用，免得拿着一份已经关掉的界面数据。 */
    public static void clearActiveMenu() {
        activeMenu = null;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(Object map) {
        return (Map<Object, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(List<?> list) {
        return (List<Object>) list;
    }

    private static synchronized boolean prepare() {
        if (prepared) {
            return available;
        }
        prepared = true;
        try {
            handlerClass = Class.forName(HANDLER_NAME);
            Class<?> fillerClass = Class.forName(FILLER_NAME);
            handlersField = findField(fillerClass, "handlers");
            craftingCategoryField = findField(Class.forName(CATEGORIES_NAME), "CRAFTING");

            Class<?> recipeClass = Class.forName(RECIPE_NAME);
            recipeGetInputsMethod = recipeClass.getMethod("getInputs");
            recipeGetCategoryMethod = recipeClass.getMethod("getCategory");
            recipeGetIdMethod = recipeClass.getMethod("getId");

            Class<?> ingredientClass = Class.forName(INGREDIENT_NAME);
            ingredientGetStacksMethod = ingredientClass.getMethod("getEmiStacks");
            Class<?> stackClass = Class.forName(STACK_NAME);
            stackGetItemMethod = stackClass.getMethod("getItemStack");
            stackIsEmptyMethod = stackClass.getMethod("isEmpty");
            stackOfMethod = stackClass.getMethod("of", ItemStack.class);

            inventoryConstructor = Class.forName(INVENTORY_NAME).getConstructor(List.class);

            Class<?> contextClass = Class.forName(CONTEXT_NAME);
            contextGetScreenHandlerMethod = contextClass.getMethod("getScreenHandler");
            contextGetAmountMethod = contextClass.getMethod("getAmount");

            handlerProxy = Proxy.newProxyInstance(handlerClass.getClassLoader(),
                    new Class<?>[]{handlerClass}, new Handler());
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    /* ---------------- 处理器本体 ---------------- */

    /** 我们塞给 EMI 的配方处理器；EMI 只调 getInventory / supportsRecipe / canCraft / craft / getTooltip。 */
    private static final class Handler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getInventory":
                    return safeInventory();
                case "supportsRecipe":
                    return safeSupports(args);
                case "alwaysDisplaySupport":
                    return Boolean.TRUE;
                case "canCraft":
                    return safeCanCraft(args);
                case "craft":
                    return safeCraft(args);
                case "getTooltip":
                    return safeTooltip(method, args);
                case "render":
                    return null;
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "SmartWorkbenchEmiRecipeHandler";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static Object safeInventory() {
        try {
            List<Object> stacks = new ArrayList<>();
            SmartWorkbenchMenu menu = activeMenu;
            if (menu != null) {
                for (ItemStack stack : MaterialAvailability.collectAvailable(menu)) {
                    Object emiStack = stackOfMethod.invoke(null, stack);
                    if (emiStack != null) {
                        stacks.add(emiStack);
                    }
                }
            }
            return inventoryConstructor.newInstance(stacks);
        } catch (Throwable ignored) {
            // 实在建不出来就给个空库存，至少不崩
            try {
                return inventoryConstructor.newInstance(new ArrayList<>());
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static Object safeSupports(Object[] args) {
        try {
            return supports(args[0]);
        } catch (Throwable ignored) {
            return Boolean.FALSE;
        }
    }

    private static boolean supports(Object recipe) throws Exception {
        Object crafting = craftingCategoryField.get(null);
        return crafting != null && recipeGetCategoryMethod.invoke(recipe) == crafting;
    }

    private static Object safeCanCraft(Object[] args) {
        try {
            SmartWorkbenchMenu menu = menuOf(args[1]);
            if (menu == null) {
                return Boolean.FALSE;
            }
            // EMI 的可合成标记可能在存储快照更新前调用，不能用旧快照阻断真正的服务端请求。
            return Boolean.TRUE;
        } catch (Throwable ignored) {
            return Boolean.FALSE;
        }
    }

    private static Object safeCraft(Object[] args) {
        try {
            SmartWorkbenchMenu menu = menuOf(args[1]);
            if (menu == null) {
                return Boolean.FALSE;
            }
            Object id = recipeGetIdMethod.invoke(args[0]);
            if (!(id instanceof ResourceLocation location)) {
                return Boolean.FALSE;
            }
            int amount = 1;
            Object rawAmount = contextGetAmountMethod.invoke(args[1]);
            if (rawAmount instanceof Number number) {
                amount = number.intValue();
            }
            // 一次要合多个（侧栏 Ctrl 点击）就尽量把每格补满，和 REI 的 Shift 点击一个意思
            ModNetwork.sendToServer(new C2SPlaceRecipePacket(menu.getBenchPos(), location, amount > 1));
            return Boolean.TRUE;
        } catch (Throwable ignored) {
            return Boolean.FALSE;
        }
    }

    private static Object safeTooltip(Method method, Object[] args) {
        List<Object> result = new ArrayList<>();
        try {
            SmartWorkbenchMenu menu = args.length > 1 ? menuOf(args[1]) : null;
            boolean craftable = menu != null;
            if (!craftable) {
                Component message = Component.translatableWithFallback(
                        "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough", "附近存储材料不足");
                Object tooltip = tooltipFor(method, message);
                if (tooltip != null) {
                    result.add(tooltip);
                }
            }
        } catch (Throwable ignored) {
            // 提示画不出来不影响按钮状态
        }
        return result;
    }

    /**
     * 把一段文字包成 EMI 要的提示组件。
     * 工厂方法的 Mojang 名字在正式版会被重映射，所以按「参数是 FormattedCharSequence」来找，
     * 不按名字找；找不到就返回 null，最多不显示这行提示。
     */
    private static Object tooltipFor(Method getTooltipMethod, Component message) {
        Method factory = tooltipFactory;
        if (factory == null) {
            try {
                Type returnType = getTooltipMethod.getGenericReturnType();
                if (returnType instanceof ParameterizedType parameterized
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> owner) {
                    for (Method candidate : owner.getMethods()) {
                        if (Modifier.isStatic(candidate.getModifiers())
                                && candidate.getParameterCount() == 1
                                && candidate.getParameterTypes()[0] == FormattedCharSequence.class
                                && owner.isAssignableFrom(candidate.getReturnType())) {
                            factory = candidate;
                            break;
                        }
                    }
                }
                tooltipFactory = factory;
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (factory == null) {
            return null;
        }
        try {
            return factory.invoke(null, message.getVisualOrderText());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SmartWorkbenchMenu menuOf(Object context) {
        try {
            Object handler = contextGetScreenHandlerMethod.invoke(context);
            return handler instanceof SmartWorkbenchMenu menu ? menu : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 配方的每个材料（EmiIngredient 是「多选一」）逐个换成候选物品清单，再交给共用逻辑判断。 */
    private static List<Object> findMissing(Object recipe, List<ItemStack> available) throws Exception {
        List<?> inputs = (List<?>) recipeGetInputsMethod.invoke(recipe);
        List<List<ItemStack>> candidates = new ArrayList<>();
        List<Object> rawIngredients = new ArrayList<>();
        for (Object ingredient : inputs) {
            if (ingredient == null) {
                continue;
            }
            List<?> stacks = (List<?>) ingredientGetStacksMethod.invoke(ingredient);
            List<ItemStack> options = new ArrayList<>();
            for (Object stack : stacks) {
                if (stack == null || Boolean.TRUE.equals(stackIsEmptyMethod.invoke(stack))) {
                    continue;
                }
                if (stackGetItemMethod.invoke(stack) instanceof ItemStack item && !item.isEmpty()) {
                    options.add(item);
                }
            }
            if (!options.isEmpty()) {
                candidates.add(options);
                rawIngredients.add(ingredient);
            }
        }
        boolean[] matched = MaterialAvailability.match(candidates, available);
        List<Object> missing = new ArrayList<>();
        for (int i = 0; i < matched.length; i++) {
            if (!matched[i]) {
                missing.add(rawIngredients.get(i));
            }
        }
        return missing;
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
        return null;
    }
}
