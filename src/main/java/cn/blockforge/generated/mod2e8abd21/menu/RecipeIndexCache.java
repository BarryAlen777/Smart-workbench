package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端级、全局共享的配方索引（优化方案一）。
 * <p>
 * 以前 {@code plainRecipes / recipesByResult / recipesUsingItem} 三份索引挂在
 * <b>每个菜单实例</b>上：玩家每打开一次工作台，都要把上万份配方全遍历一遍、
 * 对每份材料解候选、再建两张反向索引——首次打开卡 1.5 秒的主体就是它；
 * 五个人同时开着就是五份重复索引。现在全服只建一次，所有菜单共享读。
 * <p>
 * 失效策略（顺带修掉「拿配方总数当版本号、重载后数量没变就不重建」的隐患）：
 * <ul>
 *   <li>数据包重载完成会发 {@code TagsUpdatedEvent}，版本号 +1，索引标脏；</li>
 *   <li>RecipeManager 实例被换掉（重载/换世界）也算脏——真正的重建推迟到下一次
 *       菜单使用时做（懒重建，重载那一下不集中烧线程）；</li>
 *   <li>配方总数变化作为最后一道保险（事件万一被别的模组吞了也能兜住）。</li>
 * </ul>
 * 建索引用的特殊配方自匹配复核带「打开界面那位玩家」做 crafting player：
 * 个别模组的特殊配方会看玩家身份，全服建一次时以第一次触发重建的那位玩家为准。
 * 这一代是<b>以谁的身份建的</b>记在 {@code Generation.openerId} 里：出问题可追溯，
 * 且万一某代是无玩家身份建的，下一次带玩家的调用会重建一次自愈。
 */
final class RecipeIndexCache {

    private RecipeIndexCache() {
    }

    /** 一次重建产出的全套只读索引。 */
    private static final class Generation {
        final RecipeManager manager;
        /**
         * 建这一代时特殊配方自匹配复核所用的玩家身份（审查报告问题③、r186 问题 2）。
         * 复核极少数会看玩家身份的特殊配方，全服共享的这一代以第一次触发重建的
         * 玩家为准——null 表示这一代没带身份（此类配方一律复核失败），
         * {@link #require} 会在下次带来玩家时重建一次自愈。排查「某玩家看不到某配方」
         * 时也能回答「当前索引是谁的身份建的」。故意只存 UUID 不存玩家对象：静态字段
         * 攥着 ServerPlayer 会把它整个实体（连同世界引用）扣在内存里。
         */
        final UUID openerId;
        final long seenEventStamp;
        final int seenRecipeCount;
        final long stamp;
        final List<RecipeFacts> plain;
        final Map<Item, List<RecipeFacts>> byResult;
        final Map<Item, List<RecipeFacts>> usingItem;
        final Map<ResourceLocation, RecipeFacts> byId;

        private Generation(RecipeManager manager, UUID openerId, long seenEventStamp, int seenRecipeCount,
                           long stamp, List<RecipeFacts> plain, Map<Item, List<RecipeFacts>> byResult,
                           Map<Item, List<RecipeFacts>> usingItem, Map<ResourceLocation, RecipeFacts> byId) {
            this.manager = manager;
            this.openerId = openerId;
            this.seenEventStamp = seenEventStamp;
            this.seenRecipeCount = seenRecipeCount;
            this.stamp = stamp;
            this.plain = plain;
            this.byResult = byResult;
            this.usingItem = usingItem;
            this.byId = byId;
        }
    }

    private static volatile Generation current;
    /** 数据包重载事件戳：变了就标脏（方案一里取代「配方总数」当版本号的修法）。
     *  客户端收标签同步包时在<b>网络线程</b>自增，服务端主线程要读它，所以用原子类。 */
    private static final java.util.concurrent.atomic.AtomicLong eventStamp =
            new java.util.concurrent.atomic.AtomicLong();
    private static long generationCounter;

    /** TagsUpdatedEvent（数据包重载/标签更新完成）回调：只标脏，重建推迟到下次使用。 */
    static void onDataPacksUpdated() {
        eventStamp.incrementAndGet();
    }

    /** 当前事件戳快照。 */
    private static long stampValue() {
        return eventStamp.get();
    }

    /**
     * 服务器停服：整代丢弃，别把旧世界的配方和 RecipeManager 扣在静态字段上。
     * 与 {@link #require} 同一把锁（审查报告问题④）——两处今天其实都跑在服务端
     * 主线程上，加锁是白拿的；但 {@code current} 是跨线程读的 volatile 字段，
     * 把它的所有写路径统一到一个监视器下，将来谁在别的线程上调这里也不会和
     * 重建过程交错（置空和写回抢顺序会留一代"幽灵索引"）。
     */
    static synchronized void clear() {
        current = null;
    }

    /** 当前索引的世代号，混进菜单的库存指纹用（重载后指纹必须变）。 */
    static long version() {
        Generation g = current;
        return g == null ? Long.MIN_VALUE : g.stamp;
    }

    /**
     * 全部「普通工作台配方」（已复核自匹配、产物非空）。
     * {@code opener} 是触发本次（可能的）重建的玩家：特殊配方自匹配复核要读玩家身份，
     * 传 null 会让这类配方一律复核失败、被整个剔出索引，还会被所有玩家永久共享。
     */
    static List<RecipeFacts> plain(Level level, ServerPlayer opener) {
        return require(level, opener).plain;
    }

    /** 产物物品 → 能做出它的配方。opener 同上。 */
    static Map<Item, List<RecipeFacts>> byResult(Level level, ServerPlayer opener) {
        return require(level, opener).byResult;
    }

    /** 材料物品 → 用到它的配方（链式可达性闭包的反向索引）。opener 同上。 */
    static Map<Item, List<RecipeFacts>> usingItem(Level level, ServerPlayer opener) {
        return require(level, opener).usingItem;
    }

    /** 按 ID 查索引项；没有返回 null（不在索引 = 不是能一键做的普通配方）。服务端路径用。 */
    static RecipeFacts byId(Level level, ResourceLocation id, ServerPlayer opener) {
        if (id == null || level.isClientSide) {
            return null;
        }
        return require(level, opener).byId.get(id);
    }

    /**
     * 配方对象 → 索引项。正常都是索引里那份；万一拿着重载前的旧对象，
     * 就地临时算一份派生数据顶上（一次性的，不在热路径）。
     */
    static RecipeFacts factsFor(Level level, CraftingRecipe recipe, ServerPlayer opener) {
        if (recipe == null) {
            return null;
        }
        Generation g = level.isClientSide ? current : require(level, opener);
        ResourceLocation id = recipe.getId();
        if (g != null && id != null) {
            RecipeFacts facts = g.byId.get(id);
            if (facts != null && facts.recipe == recipe) {
                return facts;
            }
        }
        return RecipeFacts.of(recipe, level.registryAccess());
    }

    /**
     * 「这份配方能不能一键取料 / 自动摆料」：服务端直接查全局索引（方案四——
     * 以前每次点击都要现场跑一遍 isPlainCraft + 特殊配方自匹配复核，复核还要
     * 现摆一个 3x3 调 matches()，点一下烧一次）；客户端没索引，退回就地检查。
     */
    static boolean accepts(Level level, CraftingRecipe recipe, ServerPlayer player) {
        if (recipe.getId() == null) {
            return false;
        }
        if (level.isClientSide) {
            return RecipeFacts.isPlainCraft(recipe)
                    && RecipeFacts.matchesWithOwnPreviews(recipe, level, null);
        }
        return require(level, player).byId.containsKey(recipe.getId());
    }

    private static synchronized Generation require(Level level, ServerPlayer opener) {
        RecipeManager manager = level.getRecipeManager();
        Generation g = current;
        long stamp = stampValue();
        int count = manager.getRecipes().size();
        // openerId 不只是留档：上一代是无玩家身份建的（复核会剔除看玩家身份的特殊配方），
        // 这次带来了玩家就重建一次自愈。只多烧这一次——重建出的新一代 openerId 非空，
        // 之后的调用全部命中快路径，不会反复重建（审查报告 r186 问题 2）。
        if (g != null && g.manager == manager && g.seenEventStamp == stamp && g.seenRecipeCount == count
                && (g.openerId != null || opener == null)) {
            return g;
        }
        g = build(manager, level, opener, stamp);
        current = g;
        return g;
    }

    private static Generation build(RecipeManager manager, Level level, ServerPlayer opener, long stamp) {
        RegistryAccess registries = level.registryAccess();
        List<RecipeFacts> plain = new ArrayList<>();
        Map<Item, List<RecipeFacts>> byResult = new HashMap<>();
        Map<Item, List<RecipeFacts>> usingItem = new HashMap<>();
        Map<ResourceLocation, RecipeFacts> byId = new HashMap<>();
        Set<Item> touched = new HashSet<>();
        for (CraftingRecipe recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            // 材料表完整 + 特殊配方自匹配复核 + 产物非空：三条不合格都进不了索引，
            // 列表、链式闭包、点击规划三条路共用这一份，一起干净。
            ResourceLocation id = recipe.getId();
            if (id == null || !RecipeFacts.isPlainCraft(recipe)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            if (!RecipeFacts.matchesWithOwnPreviews(recipe, level, opener)) {
                continue;
            }
            RecipeFacts facts = RecipeFacts.ofIndexed(recipe, result);
            plain.add(facts);
            byId.put(id, facts);
            byResult.computeIfAbsent(facts.resultItem, ignored -> new ArrayList<>()).add(facts);
            // 材料种类 → 用到它的配方：链式可达性扫描沿「新点亮的种类」找候选配方。
            touched.clear();
            for (Item[] items : facts.candidateItems) {
                for (Item item : items) {
                    touched.add(item);
                }
            }
            for (Item used : touched) {
                usingItem.computeIfAbsent(used, ignored -> new ArrayList<>()).add(facts);
            }
        }
        for (Map.Entry<Item, List<RecipeFacts>> entry : byResult.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        for (Map.Entry<Item, List<RecipeFacts>> entry : usingItem.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        generationCounter++;
        return new Generation(manager, opener == null ? null : opener.getUUID(), stamp,
                manager.getRecipes().size(), generationCounter,
                List.copyOf(plain), Map.copyOf(byResult), Map.copyOf(usingItem), Map.copyOf(byId));
    }
}
