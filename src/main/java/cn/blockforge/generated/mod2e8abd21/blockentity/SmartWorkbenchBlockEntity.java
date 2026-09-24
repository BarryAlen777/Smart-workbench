package cn.blockforge.generated.mod2e8abd21.blockentity;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.compat.BackpackStorageCompat;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 智能工作台本体：3x3 合成格存在方块实体里（关界面不丢东西，借鉴匠魂工作台的作法），
 * 另外缓存一份“可取料的容器”列表——扳手手动绑定的优先，剩下靠附近自动扫描补足。
 */
public class SmartWorkbenchBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int GRID_SIZE = 9;
    /** 与原版一致：离开 64 格（8 格距离）自动关闭界面 */
    private static final double USE_DISTANCE_SQUARED = 64.0D;

    private final NonNullList<ItemStack> items = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
    /** 缓存的取料存储：绑定的在前，自动扫描的在后 */
    private List<IItemHandler> storages = List.of();
    /** 缓存的输出容器：自动合成的产物优先送进这里 */
    private List<IItemHandler> outputStorages = List.of();
    /** 扳手手动绑定的「取料容器」坐标，不受扫描范围限制 */
    private final List<BlockPos> manualBindings = new ArrayList<>();
    /** 扳手手动绑定的「输出容器」坐标，只收自动合成的产物，不参与取料 */
    private final List<BlockPos> outputBindings = new ArrayList<>();
    private long lastScanTick = Long.MIN_VALUE;

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
            this.items.clear();
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

    /**
     * 重新整理接入的存储：
     * <ol>
     *   <li>取料容器 = 扳手绑定的（优先，不受范围限制）+ 范围内自动扫描到的；</li>
     *   <li>输出容器 = 扳手绑定的输出容器，只收产物、不参与取料；</li>
     *   <li>被绑成输出容器的坐标会从自动扫描里排除，免得刚做出来的东西又被当材料抽走；</li>
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

        // 输出容器：只认手动绑定的，永远不参与合成取料
        Set<Long> output = new HashSet<>();
        for (BlockPos bound : this.outputBindings) {
            output.add(bound.asLong());
            IItemHandler handler = findItemHandler(this.level, bound);
            if (handler != null && handler.getSlots() > 0) {
                outputs.add(handler);
            }
        }

        // 手动绑定的取料容器永远优先，而且不受扫描范围限制
        Set<Long> manual = new HashSet<>();
        for (BlockPos bound : this.manualBindings) {
            manual.add(bound.asLong());
            IItemHandler handler = findItemHandler(this.level, bound);
            if (handler != null && handler.getSlots() > 0) {
                inputs.add(handler);
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
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir() || !state.hasBlockEntity()) {
                continue;
            }
            if (ModConfig.isBlacklisted(state.getBlock())) {
                continue;
            }
            IItemHandler handler = findItemHandler(this.level, pos);
            if (handler != null && handler.getSlots() > 0) {
                found.add(new Entry(handler, pos.distManhattan(this.worldPosition)));
            }
        }
        found.sort(Comparator.comparingInt(Entry::distance));
        for (int i = 0; i < found.size() && i < max; i++) {
            inputs.add(found.get(i).handler);
        }

        // 精妙背包兼容：容器里放着的背包，以及附近玩家身上/物品栏里的背包，
        // 它们内部的物品也接进来一起当作料源（见 BackpackStorageCompat，靠通用能力查询，无编译期依赖）。
        BackpackStorageCompat backpacks = new BackpackStorageCompat(inputs, BackpackStorageCompat.MAX_BACKPACKS);
        int containerCount = inputs.size();
        for (int i = 0; i < containerCount; i++) {
            backpacks.scan(inputs.get(i));
        }
        backpacks.addPlayerBackpacks(this.level, this.worldPosition);

        this.storages = inputs;
        this.outputStorages = outputs;
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
     * 找方块暴露出来的物品栏能力：先试无朝向的通用能力，不行再逐个方向试。
     * 像精妙存储这种只在特定面暴露物品种类的模组，必须挨个方向找一遍。
     */
    public static IItemHandler findItemHandler(Level level, BlockPos pos) {
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
    private record Entry(IItemHandler handler, int distance) {
    }
}
