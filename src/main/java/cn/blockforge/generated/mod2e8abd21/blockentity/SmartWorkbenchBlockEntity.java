package cn.blockforge.generated.mod2e8abd21.blockentity;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.compat.BackpackStorageCompat;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 智能工作台本体：3x3 合成格存在方块实体里（关界面不丢东西，借鉴匠魂工作台的作法），
 * 另外缓存一份“可取料的容器”列表——扳手手动绑定的优先，剩下靠附近自动扫描补足。
 */
public class SmartWorkbenchBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int GRID_SIZE = 9;
    /** 与原版一致：离开 64 格（8 格距离）自动关闭界面 */
    private static final double USE_DISTANCE_SQUARED = 64.0D;
    private static final String ORIGINS_TAG = "GridOrigins";

    /** 合成格里一件材料是从哪儿取来的。 */
    public enum OriginKind {
        /** 某台接入容器的某一格，原路插回这里。 */
        STORAGE,
        /** 玩家自己背包里的，退回玩家背包。 */
        PLAYER,
        /** 来自某个接入存储但记不住坐标（背包里的背包这种），优先塞回箱子。 */
        ANY
    }

    /**
     * 合成格里一件材料的来路。存在方块实体上，所以关掉界面、重开、甚至重启游戏之后
     * 切换配方仍能原路退回，而不是一股脑塞进玩家背包。
     */
    public record GridOrigin(ItemStack stack, BlockPos pos, int slot, OriginKind kind) {
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
    /** 与合成格一一对应的材料来路，菜单负责维护。 */
    private final List<List<GridOrigin>> gridOrigins = createOriginLists();
    /** 缓存的取料存储：绑定的在前，自动扫描的在后 */
    private List<IItemHandler> storages = List.of();
    /** 与 {@link #storages} 一一对应的方块坐标；背包内部这类没有坐标的存储记 null。 */
    private List<BlockPos> storagePositions = List.of();
    /**
     * 与 {@link #storagePositions} 同序的「整台容器占的格」：原版/精妙存储的大箱子这类
     * 两格拼一格的容器，清单只记一个锚点坐标，F12 画框就会只框住一半（r233 用户实拍）。
     * 每组第一个元素就是锚点，后面是沿六个方向认「同一份库存」拼回来的其余格；
     * 单格容器就是一元组；背包这类无坐标存储是空组。只在扫描里重算，不进 NBT。
     */
    private List<List<BlockPos>> storageShapes = List.of();
    /** 缓存的输出容器：自动合成的产物优先送进这里 */
    private List<IItemHandler> outputStorages = List.of();
    /** 扳手手动绑定的「取料容器」坐标，不受扫描范围限制 */
    private final List<BlockPos> manualBindings = new ArrayList<>();
    /** 扳手手动绑定的「输出容器」坐标，只收自动合成的产物，不参与取料 */
    private final List<BlockPos> outputBindings = new ArrayList<>();
    private long lastScanTick = Long.MIN_VALUE;
    /**
     * F12 接入总览<b>按玩家</b>记的「上次准回包的世界时间」（r220 审查报告问题①）。
     * 原来整台工作台共享一个窗口：两个人同时按，慢的那个被静默丢、干等 5 秒超时；
     * 改包的客户端还能每 300ms 一次霸占窗口饿死别人。一人一格窗口，谁刷屏只影响谁自己。
     * 只在服务端主线程碰（收包处 {@code enqueueWork}），不上锁、不进 NBT。
     */
    private final Map<java.util.UUID, Long> lastOverviewReplyTickByPlayer = new HashMap<>();
    /**
     * 最近一次扫描时有效的「输出容器」坐标缓存，与 {@link #outputStorages} 同批产生。
     * F12 回包直接读这份账，不做整轮能力重扫（那是 r217 问题①里的放大器）；
     * 读出来之后补一道轻量存在性过滤（r220 问题⑤），缓存窗口里被拆掉的箱子不会再画框。
     */
    private List<BlockPos> cachedOutputPositions = List.of();
    /** 与 {@link #cachedOutputPositions} 同序的「整台容器占的格」，口径同 {@link #storageShapes}。 */
    private List<List<BlockPos>> cachedOutputShapes = List.of();
    /**
     * 最近一次打开界面的玩家。自动扫描只把「这一名玩家」身上的背包接进来，
     * 不能把附近所有人的背包都当公共料源（多人服上等于谁站在旁边谁的东西就能被抽走）。
     */
    private java.util.UUID lastUserId;

    public SmartWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SMART_WORKBENCH_ENTITY.get(), pos, state);
    }

    /* Container */

    @Override
    public int getContainerSize() {
        return GRID_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, count);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.items.set(slot, ItemStack.EMPTY);
        this.setChanged();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null) {
            setChanged(this.level, this.worldPosition, this.getBlockState());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.level != null && !player.isRemoved()
                && this.worldPosition.distToCenterSqr(player.position()) <= USE_DISTANCE_SQUARED;
    }

    @Override
    public void clearContent() {
        if (!this.isEmpty()) {
            // NonNullList.withSize 底层是定长列表：直接 clear() 要么抛
            // UnsupportedOperationException，要么把列表缩成 0 格，之后任何 setItem 都会越界。
            // 逐格写空值才是 Container 契约要求的做法。
            for (int i = 0; i < this.items.size(); i++) {
                this.items.set(i, ItemStack.EMPTY);
            }
            this.setChanged();
        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    /** 合成格是否全空，取料前会检查。 */
    public boolean isGridEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /* 材料来路 */

    private static List<List<GridOrigin>> createOriginLists() {
        List<List<GridOrigin>> lists = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    /** 这一格的材料分别是从哪儿取的，按件的顺序。 */
    public List<GridOrigin> getGridOrigins(int slot) {
        if (slot < 0 || slot >= this.gridOrigins.size()) {
            return List.of();
        }
        return List.copyOf(this.gridOrigins.get(slot));
    }

    /** 菜单同步过来：这一格的材料来路。内容没变就不标记存档，免得每 tick 都写脏。 */
    public void setGridOrigins(int slot, List<GridOrigin> origins) {
        if (slot < 0 || slot >= this.gridOrigins.size()) {
            return;
        }
        List<GridOrigin> next = new ArrayList<>(origins.size());
        for (GridOrigin origin : origins) {
            if (origin != null && !origin.stack().isEmpty()) {
                // 复制一份堆叠，菜单之后再怎么改自己的记录都影响不到存档里的这份
                next.add(new GridOrigin(origin.stack().copy(), origin.pos(), origin.slot(), origin.kind()));
            }
        }
        List<GridOrigin> current = this.gridOrigins.get(slot);
        if (sameOrigins(current, next)) {
            return;
        }
        current.clear();
        current.addAll(next);
        this.setChanged();
    }

    private static boolean sameOrigins(List<GridOrigin> first, List<GridOrigin> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            GridOrigin a = first.get(i);
            GridOrigin b = second.get(i);
            if (a.slot() != b.slot() || a.kind() != b.kind() || !Objects.equals(a.pos(), b.pos())
                    || a.stack().getCount() != b.stack().getCount()
                    || !ItemStack.isSameItemSameTags(a.stack(), b.stack())) {
                return false;
            }
        }
        return true;
    }

    /** 这个取料处理器在世界上对应的方块坐标；背包内部这类没有坐标的返回 null。 */
    public BlockPos storagePositionOf(IItemHandler handler) {
        for (int i = 0; i < this.storages.size(); i++) {
            if (this.storages.get(i) == handler) {
                return i < this.storagePositions.size() ? this.storagePositions.get(i) : null;
            }
        }
        return null;
    }

    /**
     * 这个取料处理器<b>整台容器占的格</b>（口径同 {@link #storageShapes}，与 F12 总览拼组同一把尺子）：
     * 大箱子两格回来就是一组两格，材料溯源的金框才描得住整台（r239，接 r233 的整台一个框）。
     * 背包内部这类没有坐标的存储返回空组；有锚点却没算出组的老缓存兜底成单格组，绝不返回 null。
     */
    public List<BlockPos> storageShapeOf(IItemHandler handler) {
        for (int i = 0; i < this.storages.size(); i++) {
            if (this.storages.get(i) == handler) {
                BlockPos anchor = i < this.storagePositions.size() ? this.storagePositions.get(i) : null;
                if (anchor == null) {
                    return List.of();
                }
                List<BlockPos> shape = i < this.storageShapes.size() ? this.storageShapes.get(i) : List.of();
                return shape.isEmpty() ? List.of(anchor) : shape;
            }
        }
        return List.of();
    }

    /**
     * 反查：这个坐标现在的取料处理器。
     * <p>
     * 存储列表每次重扫都会新建一批处理器包装对象，所以来路里不能存处理器本身，
     * 只能存坐标，退料时再用坐标换回「现在」的那个处理器。
     */
    public IItemHandler handlerAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        for (int i = 0; i < this.storagePositions.size() && i < this.storages.size(); i++) {
            if (pos.equals(this.storagePositions.get(i))) {
                return this.storages.get(i);
            }
        }
        return null;
    }

    /* MenuProvider */

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SmartWorkbenchMenu(containerId, playerInventory, this.worldPosition);
    }

    /* NBT */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
        tag.putLongArray("Bindings", packPositions(this.manualBindings));
        tag.putLongArray("OutputBindings", packPositions(this.outputBindings));
        tag.put(ORIGINS_TAG, saveOrigins());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, this.items);
        this.manualBindings.clear();
        for (long packed : tag.getLongArray("Bindings")) {
            this.manualBindings.add(BlockPos.of(packed));
        }
        this.outputBindings.clear();
        for (long packed : tag.getLongArray("OutputBindings")) {
            this.outputBindings.add(BlockPos.of(packed));
        }
        loadOrigins(tag.getList(ORIGINS_TAG, Tag.TAG_COMPOUND));
    }

    private ListTag saveOrigins() {
        ListTag list = new ListTag();
        for (int slot = 0; slot < this.gridOrigins.size(); slot++) {
            for (GridOrigin origin : this.gridOrigins.get(slot)) {
                if (origin.stack().isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                entry.put("Stack", origin.stack().save(new CompoundTag()));
                if (origin.pos() != null) {
                    entry.putLong("Pos", origin.pos().asLong());
                    entry.putInt("ContainerSlot", origin.slot());
                }
                entry.putString("Kind", origin.kind().name());
                list.add(entry);
            }
        }
        return list;
    }

    private void loadOrigins(ListTag list) {
        for (List<GridOrigin> perSlot : this.gridOrigins) {
            perSlot.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot < 0 || slot >= this.gridOrigins.size()) {
                continue;
            }
            ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
            if (stack.isEmpty()) {
                continue;
            }
            OriginKind kind;
            try {
                kind = OriginKind.valueOf(entry.getString("Kind"));
            } catch (IllegalArgumentException unknown) {
                kind = OriginKind.ANY;
            }
            BlockPos pos = entry.contains("Pos") ? BlockPos.of(entry.getLong("Pos")) : null;
            int containerSlot = entry.contains("ContainerSlot") ? entry.getInt("ContainerSlot") : -1;
            if (pos == null && kind == OriginKind.STORAGE) {
                // 存档里来路不明：按「塞回接入存储」处理，总比塞玩家背包强
                kind = OriginKind.ANY;
            }
            this.gridOrigins.get(slot).add(new GridOrigin(stack, pos, containerSlot, kind));
        }
    }

    private static long[] packPositions(List<BlockPos> positions) {
        long[] packed = new long[positions.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = positions.get(i).asLong();
        }
        return packed;
    }

    /* 扳手绑定 */

    /** 把容器绑成「取料容器」；已经是输出容器的坐标不能再绑，避免取料和产物互相打架。 */
    public boolean addBinding(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (this.manualBindings.contains(immutable) || this.outputBindings.contains(immutable)
                || this.manualBindings.size() >= ModConfig.maxInputBindings()) {
            return false;
        }
        this.manualBindings.add(immutable);
        this.setChanged();
        refreshStorages(true);
        return true;
    }

    /** 把容器绑成「输出容器」：自动合成的产物会送进去。 */
    public boolean addOutputBinding(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (this.outputBindings.contains(immutable) || this.manualBindings.contains(immutable)
                || this.outputBindings.size() >= ModConfig.maxOutputBindings()) {
            return false;
        }
        this.outputBindings.add(immutable);
        this.setChanged();
        refreshStorages(true);
        return true;
    }

    public boolean hasBinding(BlockPos pos) {
        return this.manualBindings.contains(pos) || this.outputBindings.contains(pos);
    }

    /** 取消一个取料容器绑定。 */
    public boolean removeBinding(BlockPos pos) {
        if (!this.manualBindings.remove(pos)) {
            return false;
        }
        this.setChanged();
        refreshStorages(true);
        return true;
    }

    /** 取消一个输出容器绑定。 */
    public boolean removeOutputBinding(BlockPos pos) {
        if (!this.outputBindings.remove(pos)) {
            return false;
        }
        this.setChanged();
        refreshStorages(true);
        return true;
    }

    /** 清空全部绑定（取料 + 输出），返回清掉了几个。 */
    public int clearBindings() {
        int count = this.manualBindings.size() + this.outputBindings.size();
        if (count > 0) {
            this.manualBindings.clear();
            this.outputBindings.clear();
            this.setChanged();
            refreshStorages(true);
        }
        return count;
    }

    public int getBindingCount() {
        return this.manualBindings.size();
    }

    public int getOutputBindingCount() {
        return this.outputBindings.size();
    }

    /** 这个坐标能不能绑：得是个真实存在、能装东西的容器。 */
    public boolean canBind(BlockPos pos) {
        if (this.level == null || pos.equals(this.worldPosition)) {
            return false;
        }
        IItemHandler handler = findItemHandler(this.level, pos);
        return handler != null && handler.getSlots() > 0;
    }

    /* 接入存储 */

    /** 记下这次打开界面的玩家：只把他自己的背包接进来当料源。 */
    public void setLastUser(Player player) {
        this.lastUserId = player == null ? null : player.getUUID();
    }

    /** 把上次打开界面的玩家找回来；玩家下线或换了维度就当作没有，绝不扫其他人。 */
    private Player resolveLastUser() {
        if (this.lastUserId == null || !(this.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (player.getUUID().equals(this.lastUserId)) {
                return player;
            }
        }
        return null;
    }

    /**
     * 重新整理接入的存储：
     * <ol>
     *   <li>取料容器 = 扳手绑定的（优先，不受范围限制）+ 范围内自动扫描到的；</li>
     *   <li>输出容器 = 扳手绑定的输出容器，只收产物、不参与取料；</li>
     *   <li>被绑成输出容器的坐标会从自动扫描里排除，免得刚做出来的东西又被当材料抽走；</li>
     *   <li>指向同一份库存的重复处理器只接一次（原版大箱子的两半就是这种情况）；</li>
     *   <li>再接一层「容器里的精妙背包 + 附近玩家身上的精妙背包」，它们的内部物品也能直接取用。</li>
     * </ol>
     * 服务器端调用，带节流。
     */
    public void refreshStorages(boolean force) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        long now = this.level.getGameTime();
        if (!force && now < this.lastScanTick + 20L) {
            return;
        }
        this.lastScanTick = now;

        List<IItemHandler> inputs = new ArrayList<>();
        List<IItemHandler> outputs = new ArrayList<>();
        // 与 inputs 同步增长：每台存储在世界里的坐标，用来支持「切换配方原路退回」
        List<BlockPos> positions = new ArrayList<>();

        // 输出容器：只认手动绑定的，永远不参与合成取料
        Set<Long> output = new HashSet<>();
        List<BlockPos> outputPositions = new ArrayList<>();
        for (BlockPos bound : this.outputBindings) {
            output.add(bound.asLong());
            IItemHandler handler = findItemHandler(this.level, bound);
            // 同一台大箱子的两半都被绑成输出时只算一台：两半查到的是同一份库存，
            // 记两笔会让 F12 报数翻倍、还会把同一台箱子插两遍产物槽位（r233）。
            if (handler != null && handler.getSlots() > 0 && !containsInventory(outputs, outputPositions, handler, bound)) {
                outputs.add(handler);
                // 顺手把还真实有效的坐标记进缓存，给 F12 总览回包用（被拆走的历史绑定自然进不来）
                outputPositions.add(bound.immutable());
            }
        }

        // 手动绑定的取料容器永远优先，而且不受扫描范围限制
        Set<Long> manual = new HashSet<>();
        for (BlockPos bound : this.manualBindings) {
            manual.add(bound.asLong());
            IItemHandler handler = findItemHandler(this.level, bound);
            if (handler != null && handler.getSlots() > 0 && !containsInventory(inputs, positions, handler, bound)) {
                inputs.add(handler);
                positions.add(bound.immutable());
            }
        }

        int radius = ModConfig.scanRadius();
        int max = ModConfig.maxStorages();
        List<Entry> found = new ArrayList<>();
        BlockPos min = this.worldPosition.offset(-radius, -radius, -radius);
        BlockPos maxPos = this.worldPosition.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, maxPos)) {
            if (pos.equals(this.worldPosition) || manual.contains(pos.asLong()) || output.contains(pos.asLong())) {
                continue;
            }
            // 先挡未加载（同 findItemHandler 的 r225 理由）：getBlockState 内部也是无防护的
            // getChunkAt——range 拉到 50 时立方体角上半径 ~87 格，服务器视距小的时候会够到
            // 真·未加载的区块，每一格都在主线程同步读盘。区块没加载就当没有，加载了自然会扫到。
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir() || !state.hasBlockEntity()) {
                continue;
            }
            if (ModConfig.isBlacklisted(state.getBlock())) {
                continue;
            }
            IItemHandler handler = findItemHandler(this.level, pos);
            if (handler != null && handler.getSlots() > 0) {
                found.add(new Entry(handler, pos.distManhattan(this.worldPosition), pos.immutable()));
            }
        }
        found.sort(Comparator.comparingInt(Entry::distance));
        int added = 0;
        for (Entry entry : found) {
            if (added >= max) {
                break;
            }
            // 原版大箱子的两半、部分模组的双方块容器，每一半通过能力查询拿到的都是
            // 「整个合并库存」的包装器：再接一遍会让同一格材料被数两遍，还白白占掉名额。
            if (containsInventory(inputs, positions, entry.handler(), entry.pos())) {
                continue;
            }
            inputs.add(entry.handler());
            positions.add(entry.pos());
            added++;
        }

        // 精妙背包兼容：容器里放着的背包，以及附近玩家身上/物品栏里的背包，
        // 它们内部的物品也接进来一起当作料源（见 BackpackStorageCompat，靠通用能力查询，无编译期依赖）。
        BackpackStorageCompat backpacks = new BackpackStorageCompat(inputs, BackpackStorageCompat.MAX_BACKPACKS);
        int containerCount = inputs.size();
        for (int i = 0; i < containerCount; i++) {
            backpacks.scan(inputs.get(i));
        }
        backpacks.addPlayerBackpacks(this.level, this.worldPosition, resolveLastUser());

        // 背包内部的处理器没有方块坐标，补齐 null 让 positions 与 inputs 等长
        while (positions.size() < inputs.size()) {
            positions.add(null);
        }

        // 给每台容器算「整台占的格」：锚点上下左右前后摸一圈，凡是能力查回来还指向同一份
        // 库存的邻居都收进这一组（大箱子的第二半就是这种情况），F12 才画得出一个整框。
        List<List<BlockPos>> shapes = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            shapes.add(collectContainerShape(positions.get(i), inputs.get(i)));
        }
        List<List<BlockPos>> outputShapes = new ArrayList<>(outputPositions.size());
        for (BlockPos pos : outputPositions) {
            outputShapes.add(collectContainerShape(pos, findItemHandler(this.level, pos)));
        }

        this.storages = inputs;
        this.outputStorages = outputs;
        this.storagePositions = positions;
        this.storageShapes = List.copyOf(shapes);
        this.cachedOutputPositions = List.copyOf(outputPositions);
        this.cachedOutputShapes = List.copyOf(outputShapes);
    }

    /** 一台多格容器最多认几格：大箱子两格封顶用不上，纯防邻居链无限长（畸形模组的共享库存）。 */
    private static final int MAX_SHAPE_CELLS = 16;

    /**
     * 从锚点出发收集「同一份库存」占的全部方块格：六方向广度优先，邻居能力查回来与锚点
     * 是同一份库存（{@link #sameInventoryAt}，与扫描去重同一把尺子）就算这台的一部分。
     * 单格容器回来就是一元组；没有坐标的存储（背包内部）是空组。
     */
    private List<BlockPos> collectContainerShape(BlockPos anchor, IItemHandler handler) {
        if (anchor == null || handler == null || this.level == null) {
            return List.of();
        }
        List<BlockPos> shape = new ArrayList<>();
        java.util.Deque<BlockPos> frontier = new java.util.ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        shape.add(anchor);
        seen.add(anchor.asLong());
        frontier.add(anchor);
        while (!frontier.isEmpty() && shape.size() < MAX_SHAPE_CELLS) {
            BlockPos current = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!seen.add(next.asLong()) || !this.level.isLoaded(next)) {
                    continue;
                }
                IItemHandler neighbor = findItemHandler(this.level, next);
                if (neighbor != null && sameInventoryAt(handler, anchor, neighbor, next)) {
                    shape.add(next.immutable());
                    frontier.add(next);
                }
            }
        }
        return List.copyOf(shape);
    }

    public List<IItemHandler> getStorages() {
        return this.storages;
    }

    /** 自动合成产物的去处；空列表表示没有绑定输出容器，产物退回玩家背包。 */
    public List<IItemHandler> getOutputStorages() {
        return this.outputStorages;
    }

    public int getConnectedStorageCount() {
        return this.storages.size();
    }

    /**
     * 取料容器的<b>原始</b>分组清单：按取料顺序、锚点去重，精妙背包这类没有方块坐标的库存跳过，
     * 但<b>没过</b> {@link #splitLiveGroups} 的已加载过滤——组数用来算"真实绑了多少台"的总数（r223 问题②）。
     * 每组是「整台容器占的格」，大箱子一元组两格，画框才框得住整台（r233）。
     * 调用前应保证 {@link #refreshStorages} 跑过（服务端回包处已带节流地重扫）。
     */
    private List<List<BlockPos>> rawInputGroups() {
        List<List<BlockPos>> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (List<BlockPos> shape : this.storageShapes) {
            if (!shape.isEmpty() && seen.add(shape.get(0))) {
                out.add(shape);
            }
        }
        return out;
    }

    /**
     * F12 总览回包一次组齐（r223 问题② + r227 问题①）：两份<b>已按已加载区块过滤</b>的可画分组清单、
     * 过滤<b>前</b>的绑定总数，外加"区块加载着、容器却已经没了（拆掉/换成别的方块）"的台数。
     * 玩家在远处按 F12 时，绑在没加载的区块里的箱子进不了清单——没有总数，客户端分不清
     * "真一台没接"和"接了但都在视距外"；没有损坏数，又会把"已被拆掉/换掉"错报成"走近一点就行"，
     * 玩家照做也没用（r227 问题①）。未加载数不用单独进包：总数 − 可画 − 损坏 即得。
     */
    public OverviewReport buildOverviewReport() {
        List<List<BlockPos>> rawInputs = rawInputGroups();
        // 输出侧的原始清单就是 cachedOutputShapes：最近一次扫描时还真实存在、能装东西的那批
        // （被拆走的历史绑定不算）；splitLiveGroups 再挡掉扫描节流窗口里刚被拆掉的（r220 问题⑤）。
        List<List<BlockPos>> rawOutputs = this.cachedOutputShapes;
        LiveSplit in = splitLiveGroups(rawInputs);
        LiveSplit out = splitLiveGroups(rawOutputs);
        return new OverviewReport(in.live(), rawInputs.size(), in.dead(),
                out.live(), rawOutputs.size(), out.dead());
    }

    /**
     * 总览回包六件套：可画的输入分组清单 + 输入绑定总台数 + 输入侧已损坏数，输出侧同构
     * （见 {@link #buildOverviewReport}）。一台大箱子算一组两格、算一台。
     */
    public record OverviewReport(List<List<BlockPos>> inputs, int totalInputs, int deadInputs,
                                 List<List<BlockPos>> outputs, int totalOutputs, int deadOutputs) {
    }

    /** {@link #splitLiveGroups} 的结果：可画分组清单 + 因"容器已不在"（拆掉/换方块）被剔掉的台数。 */
    private record LiveSplit(List<List<BlockPos>> live, int dead) {
    }

    /**
     * 轻量存在性过滤 + 分类剔除（r227 问题①），按<b>组</b>：锚点所在区块<b>没加载</b>的整组不算损坏
     * （走近了也许还在，只体现在"总数 − 可画 − 损坏"的差额里，绝不强制加载别人卸掉的远郊）；
     * 锚点加载着却<b>查不到容器</b>（拆了、换成了别的方块）整组记一笔 dead，客户端据此才说得出
     * "该重新绑定"而不是"走近一点"。锚点活着的组，把区块已卸载的格剔掉再发（画到半空没意义）。
     * 一组一格一次方块实体查找 + 能力查询，清单几十组封顶、又在按玩家的回包窗口后面，扛得住。
     */
    private LiveSplit splitLiveGroups(List<List<BlockPos>> groups) {
        if (this.level == null || groups.isEmpty()) {
            return new LiveSplit(groups, 0);
        }
        List<List<BlockPos>> out = new ArrayList<>(groups.size());
        int dead = 0;
        for (List<BlockPos> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            BlockPos anchor = group.get(0);
            if (!this.level.isLoaded(anchor)) {
                continue;
            }
            IItemHandler anchorHandler = findItemHandler(this.level, anchor);
            if (anchorHandler == null) {
                dead++;
                continue;
            }
            if (group.size() == 1) {
                out.add(group);
                continue;
            }
            // 锚点活着不等于其余格也还在这台里：大箱子另一半被拆掉/换成别的方块时那半格要剔掉（r241 问题②）——
            // 画框按组并 AABB，留着一格死位等于把框盖到空气上，宁可只剩一格也不能框错。
            // 校验用与拼组同一把尺子（sameInventoryAt），空箱子的两半照样认得出是同一台。
            List<BlockPos> members = new ArrayList<>(group.size());
            members.add(anchor);
            for (int i = 1; i < group.size(); i++) {
                BlockPos cell = group.get(i);
                if (!this.level.isLoaded(cell)) {
                    continue;
                }
                IItemHandler cellHandler = findItemHandler(this.level, cell);
                if (cellHandler == null || !sameInventoryAt(anchorHandler, anchor, cellHandler, cell)) {
                    continue;
                }
                members.add(cell);
            }
            out.add(List.copyOf(members));
        }
        return new LiveSplit(List.copyOf(out), dead);
    }

    /**
     * F12 总览回包限频认领：<b>每个玩家</b>对这台工作台每 {@code minIntervalTicks} tick 最多回一份清单。
     * 扫描本身有节流，但<b>回包</b>（组清单 + 发网络包）以前是来一发接一发——
     * 改包客户端高频发包就是在服务端主线程上白刷这条路径（r217 问题①）。
     * 窗口从「整台共享」改成「一人一格」（r220 问题①）：多人同时按互不干扰，
     * 刷屏者也只能刷出自己的回包。正常玩家按一下 F12 要一份清单，间隔远大于这个窗口，碰不到。
     *
     * @return true 表示这次可以回包（并已记账）；false 表示该玩家太频繁，调用方应当静默丢弃
     */
    public boolean claimOverviewReply(java.util.UUID player, int minIntervalTicks) {
        if (this.level == null || player == null) {
            return false;
        }
        long now = this.level.getGameTime();
        Long last = this.lastOverviewReplyTickByPlayer.get(player);
        if (last != null && now < last + minIntervalTicks) {
            return false;
        }
        this.lastOverviewReplyTickByPlayer.put(player, now);
        // 顺手扫垃圾：条目只增不减会慢慢攒下退坑玩家的旧账，超过 16 人就把一分钟没动过的清掉
        if (this.lastOverviewReplyTickByPlayer.size() > 16) {
            this.lastOverviewReplyTickByPlayer.values().removeIf(tick -> tick < now - 1200L);
        }
        return true;
    }

    /**
     * 这个处理器是不是已经接进来的某一份库存的重复包装（带坐标版，r241 问题①）。
     * {@code handlerPositions} 与 {@code handlers} 同序（背包内部这类记 null），
     * 全空的大箱子两半要靠坐标侧的结构配对才认得出是同一台。
     */
    private boolean containsInventory(List<IItemHandler> handlers, List<BlockPos> handlerPositions,
                                      IItemHandler candidate, BlockPos candidatePos) {
        for (int i = 0; i < handlers.size(); i++) {
            BlockPos existingPos = i < handlerPositions.size() ? handlerPositions.get(i) : null;
            if (sameInventoryAt(handlers.get(i), existingPos, candidate, candidatePos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 两个处理器是否指向同一份库存（不带坐标的基线判定）。
     * 同一个包装实例天然同一份——有些模组的大箱子两半查回的就是同一个对象，空库存也只认这条；
     * 否则要求槽位数相同、每个槽位返回的 ItemStack 是同一个对象，并且至少有一个非空格子。
     * 两个全空的容器在这里分不出真假（每个槽都是 {@link ItemStack#EMPTY} 单例），
     * 判 false 交给带坐标的 {@link #sameInventoryAt} 做结构配对兜底。
     */
    private static boolean sameInventory(IItemHandler first, IItemHandler second) {
        if (first == second) {
            return true;
        }
        int slots = first.getSlots();
        if (slots != second.getSlots()) {
            return false;
        }
        boolean sawItem = false;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack a = first.getStackInSlot(slot);
            ItemStack b = second.getStackInSlot(slot);
            if (a != b) {
                return false;
            }
            if (!a.isEmpty()) {
                sawItem = true;
            }
        }
        return sawItem;
    }

    /**
     * 带坐标的"同一份库存"判定（r241 问题①）：先走 {@link #sameInventory}；
     * 判不出来而两边<b>全空</b>时，只再认"同一台大箱子的一左一右"这一种结构配对——
     * 原版大箱子（以及沿用原版 {@code type}/{@code facing} 属性的模组箱子）两半各自
     * new 一个包装对象，空库存时逐槽比引用永远比不出真，但箱子配对关系写在方块状态里，
     * 不随内容变化。两个互不相干的空箱子/空桶不满足配对条件，绝不因为"都空"就并成一台。
     * <p>
     * 具体的配对判定放在 {@link PairedBlockHalves} 里（r243）：那边<b>不引用</b>箱子类，
     * 只读方块状态的通用属性，避免在启动阶段提前加载 {@code ChestBlock} 和会 mixin 它的模组撞车。
     */
    private boolean sameInventoryAt(IItemHandler first, BlockPos posFirst, IItemHandler second, BlockPos posSecond) {
        if (sameInventory(first, second)) {
            return true;
        }
        if (posFirst == null || posSecond == null || first.getSlots() != second.getSlots()) {
            return false;
        }
        return allSlotsEmpty(first) && allSlotsEmpty(second)
                && PairedBlockHalves.arePaired(this.level, posFirst, posSecond);
    }

    private static boolean allSlotsEmpty(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 找方块暴露出来的物品栏能力：先试无朝向的通用能力，不行再逐个方向试。
     * 像精妙存储这种只在特定面暴露物品种类的模组，必须挨个方向找一遍。
     * <p>
     * <b>入口先挡一道 {@code isLoaded}</b>（r225 审查发现）：1.20.1 的 {@code Level.getBlockEntity}
     * 内部<b>没有</b>已加载检查，直接 {@code getChunkAt(pos)}——服务端碰到未加载的坐标会
     * <b>在主线程同步从磁盘加载那个区块</b>（已反汇编核实）。扳手绑定"不受范围限制"，
     * 坐标可能在世界任何角落；这一列不挡，工作台就成了连带扫描/绑定循环每轮都在
     * 强拉别人卸掉的远郊区块的隐形区块加载器。未加载就当场跳过，等区块自然加载后再接上——
     * 这和总览回包"走近一点，或等区块加载后再按"的既有口径本来就是同一句话。
     */
    public static IItemHandler findItemHandler(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity instanceof SmartWorkbenchBlockEntity) {
            return null;
        }
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /** 只给合成格提供交互，不对外暴露能力，避免被漏斗之类白嫖。 */
    private record Entry(IItemHandler handler, int distance, BlockPos pos) {
    }
}
