package cn.blockforge.generated.mod2e8abd21.compat;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 与「多态合成」(Polymorph) 对接的反射桥。
 * <p>
 * <b>多态合成解决什么问题：</b>几个不同模组可能都加了「产物一样、但材料或排列不同」的配方。
 * 原版遇到这种情况只会闷头取第一份，玩家没得选；多态合成会在产物格旁边加一个小按钮，
 * 让玩家挑用哪一份配方。
 * <p>
 * <b>为什么全用反射：</b>和 JEI / REI / EMI 那三座桥同样的理由——本工程的独立编译器只认
 * Minecraft 和 Forge，一旦 import 多态合成的类，整个工程就编译不过；而玩家不装多态合成时
 * 这段代码根本不会执行。所以这里只在运行时按类名找方法，找不到就安静地走回原版逻辑。
 * <p>
 * <b>怎么对接：</b>多态合成对合成台用的是「玩家级」配方选择（{@code IPlayerRecipeData}）。
 * 我们只要做两件事：
 * <ol>
 *   <li>算产物时不再自己 {@code getRecipeFor}，而是问它「这名玩家当前选的是哪份配方」；</li>
 *   <li>玩家在本模组界面上明确点了某一份配方时，反过来告诉它「现在选这一份」。</li>
 * </ol>
 * 界面上的选择按钮不用我们画：多态合成会在打开任意容器界面时自动找产物格，
 * 而我们的产物格装的正是 {@code ResultContainer}，它认得出来。
 */
public final class PolymorphCompat {

    private static final String API_CLASS = "com.illusivesoulworks.polymorph.api.PolymorphApi";
    private static final String COMMON_INTERFACE =
            "com.illusivesoulworks.polymorph.api.common.base.IPolymorphCommon";
    private static final String PLAYER_DATA_INTERFACE =
            "com.illusivesoulworks.polymorph.api.common.capability.IPlayerRecipeData";
    private static final String DATA_INTERFACE =
            "com.illusivesoulworks.polymorph.api.common.capability.IRecipeData";

    private static boolean probed;
    private static boolean available;

    private static Method commonMethod;
    private static Method getRecipeDataMethod;
    private static Method setContainerMenuMethod;
    private static Method selectRecipeMethod;
    private static Method getRecipeMethod;

    private PolymorphCompat() {
    }

    /** 玩家有没有装多态合成。只探测一次，之后走缓存。 */
    public static boolean isLoaded() {
        probe();
        return available;
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            Class<?> commonIface = Class.forName(COMMON_INTERFACE);
            Class<?> playerDataIface = Class.forName(PLAYER_DATA_INTERFACE);
            Class<?> dataIface = Class.forName(DATA_INTERFACE);

            commonMethod = api.getMethod("common");
            getRecipeDataMethod = commonIface.getMethod("getRecipeData", Player.class);
            setContainerMenuMethod = playerDataIface.getMethod("setContainerMenu", AbstractContainerMenu.class);
            selectRecipeMethod = dataIface.getMethod("selectRecipe", Recipe.class);
            // 泛型擦除后就是 getRecipe(RecipeType, Container, Level, List)
            getRecipeMethod = dataIface.getMethod("getRecipe", RecipeType.class, Container.class, Level.class, List.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    /** 取这名玩家身上的配方数据；没装多态合成或结构对不上时返回 null。 */
    private static Object recipeDataOf(Player player) {
        probe();
        if (!available || player == null) {
            return null;
        }
        try {
            Object common = commonMethod.invoke(null);
            if (common == null) {
                return null;
            }
            Object maybeData = getRecipeDataMethod.invoke(common, player);
            if (maybeData instanceof Optional<?> optional && optional.isPresent()) {
                return optional.get();
            }
        } catch (Throwable ignored) {
            // 版本不匹配等情况一律当作没装，交给原版逻辑
        }
        return null;
    }

    /**
     * 告诉多态合成「这名玩家现在选的是这份配方」。
     * 我们在界面上点了某一项、或 REI / JEI 把某份配方摆进合成格时调用，
     * 这样产物格显示的就是玩家真正选的那一份，而不是同产物配方里随便一份。
     */
    public static void selectRecipe(Player player, Recipe<?> recipe) {
        if (recipe == null) {
            return;
        }
        Object data = recipeDataOf(player);
        if (data == null) {
            return;
        }
        try {
            selectRecipeMethod.invoke(data, recipe);
        } catch (Throwable ignored) {
            // 选不上就退化成原版行为，不影响合成
        }
    }

    /**
     * 让多态合成决定当前合成格该出哪一份配方。
     * 返回 empty 表示没装多态合成、没选过、或当前材料跟已选配方对不上，调用方应走自己的兜底逻辑。
     */
    @SuppressWarnings("unchecked")
    public static Optional<CraftingRecipe> pickRecipe(AbstractContainerMenu menu,
                                                      RecipeType<CraftingRecipe> type,
                                                      Container inventory, Level level, Player player) {
        Object data = recipeDataOf(player);
        if (data == null || menu == null || inventory == null || level == null) {
            return Optional.empty();
        }
        try {
            // 多态合成靠这个把「配方列表」推给正在看这个界面的客户端，别省
            setContainerMenuMethod.invoke(data, menu);
            Object maybeRecipe = getRecipeMethod.invoke(data, type, inventory, level, Collections.emptyList());
            if (maybeRecipe instanceof Optional<?> optional && optional.isPresent()
                    && optional.get() instanceof CraftingRecipe crafting) {
                return Optional.of(crafting);
            }
        } catch (Throwable ignored) {
            // 链接失败时不要卡住合成，退回原版
        }
        return Optional.empty();
    }
}
