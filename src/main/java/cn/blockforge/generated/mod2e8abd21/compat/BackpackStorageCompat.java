package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 精妙背包（Sophisticated Backpacks）兼容：让工作台能用到「背包里的物品」。
 * <p>
 * 精妙背包这类模组会把背包物品本身做成一个物品栏（在物品栈上挂 {@code ITEM_HANDLER} 能力，
 * 漏斗/管道都是这么取料的）。所以这里一个精妙背包的类都不用 import，只查这个通用能力：
 * <ul>
 *   <li>接入的容器（箱子、精妙存储抽屉等）里放着的背包，把它的内部库存也接进来；</li>
 *   <li>工作台一定范围内玩家身上（装备格、主背包、快捷栏、Curios 饰品栏）的背包同样接进来。</li>
 * </ul>
 * 没装精妙背包时这些查询全部落空，等于什么都没做，不会影响原来的功能。
 */
public final class BackpackStorageCompat {

    /**
     * 只往下钻一层：容器的格子 → 格子里放着的背包，就到此为止。
     * <p>
     * 不再往背包里继续找背包，是因为精妙背包的「初始背包」升级会把子背包的内容
     * 直接拼进外层背包自己的库存里（{@code InceptionInventoryHandler}），外层背包一接进来，
     * 里面的子背包内容就已经能取到了；再单独接一次子背包，同一批物品会被数两遍。
     */
    public static final int MAX_DEPTH = 1;

    /** 一次最多接入多少个背包，避免一整箱背包把取料规划拖慢（背包动辄几十格，接太多会很慢）。 */
    public static final int MAX_BACKPACKS = 32;

    private final List<IItemHandler> out;
    private final Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final int limit;
    private int added;

    /**
     * @param out   接好的存储列表，新找到的背包会直接追加到这里
     * @param limit 最多追加多少个
     */
    public BackpackStorageCompat(List<IItemHandler> out, int limit) {
        this.out = out;
        this.limit = Math.max(0, limit);
    }

    /** 扫一个容器：把它格子里放着的背包的内部库存接到 {@code out} 上，返回累计接入了几个。 */
    public int scan(IItemHandler source) {
        scanInto(source, MAX_DEPTH);
        return this.added;
    }

    /**
     * 接工作台附近的玩家身上/物品栏里的背包。
     * 范围由配置 {@code storage.playerBackpackRange} 控制（默认 3 即 3x3x3，最大 50）。
     */
    public int addPlayerBackpacks(Level level, BlockPos benchPos) {
        int radius = ModConfig.playerBackpackRadius();
        AABB area = new AABB(benchPos).inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (this.added >= this.limit) {
                break;
            }
            scanPlayerInventory(player);
        }
        return this.added;
    }

    public int added() {
        return this.added;
    }

    /* 内部实现 */

    private void scanInto(IItemHandler source, int depth) {
        if (source == null || depth <= 0) {
            return;
        }
        int slots = source.getSlots();
        for (int slot = 0; slot < slots && this.added < this.limit; slot++) {
            addNested(source.getStackInSlot(slot), depth);
        }
    }

    /** 这个物品栈如果自己带库存（精妙背包、其他模组的背包/便携容器），就把它的库存接进来。 */
    private void addNested(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty() || stack.getCount() > 1 || this.added >= this.limit) {
            // 堆叠在一起的背包没有独立库存，精妙背包自己也会拒绝这种查询
            return;
        }
        // 按物品栈的“是不是同一个对象”去重：同一个背包从两条路都能摸到（比如箱子里的又跟着玩家）
        // 时只接一次，否则同格材料会被数两遍，规划就会以为料够、真取时却不够。
        if (!this.seen.add(stack)) {
            return;
        }
        IItemHandler nested = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (nested == null || nested.getSlots() <= 0) {
            return;
        }
        this.out.add(nested);
        this.added++;
        scanInto(nested, depth - 1);
    }

    private void scanPlayerInventory(Player player) {
        Inventory inventory = player.getInventory();
        scanStackList(inventory.items);
        scanStackList(inventory.armor);
        scanStackList(inventory.offhand);
        scanCurios(player);
    }

    private void scanStackList(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (this.added >= this.limit) {
                return;
            }
            addNested(stack, MAX_DEPTH);
        }
    }

    /**
     * Curios 饰品栏：精妙背包装了 Curios 后会背在饰品栏里，那不属于原版物品栏，得单独问一次。
     * 全程反射，没装 Curios 就直接跳过；Curios 的方法名在它支持的版本里是稳定的。
     */
    private void scanCurios(Player player) {
        if (this.added >= this.limit) {
            return;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object helper = invokeStatic(api, "getCuriosHelper");
            Object curios = resolve(invoke(helper, "getCuriosHandler", player));
            if (curios == null) {
                return;
            }
            IItemHandler equipped = asItemHandler(invoke(curios, "getEquippedCurios"));
            if (equipped != null) {
                scanInto(equipped, MAX_DEPTH);
                return;
            }
            if (invoke(curios, "getCurios") instanceof Map<?, ?> bySlot) {
                for (Object stacksHandler : bySlot.values()) {
                    IItemHandler stacks = asItemHandler(invoke(stacksHandler, "getStacks"));
                    if (stacks != null) {
                        scanInto(stacks, MAX_DEPTH);
                    }
                    if (this.added >= this.limit) {
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 没装 Curios / 版本对不上：当作玩家没有饰品位，原版那几个格子已经扫过了
        }
    }

    private static Object resolve(Object lazyOptional) {
        return lazyOptional instanceof LazyOptional<?> optional ? optional.orElse(null) : null;
    }

    private static IItemHandler asItemHandler(Object candidate) {
        return candidate instanceof IItemHandler handler ? handler : null;
    }

    private static Object invokeStatic(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 按名字和参数类型找一个方法调一下，找不到或调用失败都返回 null（兼容不同版本的 API）。 */
    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method candidate : target.getClass().getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !params[i].isInstance(args[i])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return candidate.invoke(target, args);
            } catch (Throwable ignored) {
                // 换个同名方法接着找
            }
        }
        return null;
    }
}
