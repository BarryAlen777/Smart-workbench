package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 原版工作台的「按住 Shift 连点产物、材料用完自动从背包补」。
 * <p>
 * <b>为什么要有这个类：</b>Minecraft 1.20.1 的原版工作台本身<b>不会</b>补料——
 * 合成一次只把用掉的材料扣掉，格子空了就得手动再摆（原版 {@code ResultSlot#onTake}
 * 里只有「扣材料 + 退空桶」，没有任何往格子里回填的代码）。玩家以前体验到的补料
 * 来自别的模组或整合包里的附加功能。本模组从第一版起就没有碰过原版工作台
 * （没有 mixin、没有替换原版菜单），所以那个手感不会由我们这边"改坏"，
 * 但玩家想要它就等于没了着落——这里干脆自己实现一份，装不装别的补料模组都能用。
 * <p>
 * <b>怎么做的：</b>监听 Forge 的 {@code ItemCraftedEvent}（原版每次拿走产物都会发一次）。
 * 有三个前提：服务端、玩家按着 Shift、当前界面是原版工作台（{@link CraftingMenu}，
 * 排除本模组自己的智能工作台）。事件是在「扣材料之前」发的，所以不能当场补，
 * 而是记下这一下之前的合成格快照，等<b>下一个玩家 tick</b>（材料已经扣完）再补。
 * <p>
 * <b>补什么：</b>只补「这一下合成后变空、而合成前有东西」的格子；每格按合成前的数量
 * 从背包主栏（快捷栏 + 背包 0~35 格）里取同种、同 NBT 的物品放回去，不够就有多少放多少。
 * <p>
 * <b>不会出问题的地方：</b>只在服务端动，动的都是玩家自己背包里的东西；
 * 补料只是把物品从背包挪到合成格，不复制任何东西；界面关了就不补；
 * 原版「配方书 Shift 一键做满」这类路径结束后合成格是空的，补出来的也只是玩家自己的材料，
 * 不会凭空多东西。想关掉的话把配置 {@code craft.vanillaCraftRefill} 改成 false。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaCraftRefill {

    /** 玩家背包里真正能当材料的格子：快捷栏 0~8 + 主背包 9~35。 */
    private static final int MAIN_INVENTORY_SLOTS = 36;

    /** 合成格最多 3x3，超过这个数的容器不是原版合成台，一律不碰。 */
    private static final int MAX_GRID_SLOTS = 9;

    /** 等下一个 tick 再补的待办；同一玩家、同一界面、同一合成格只留一条（快照合并进去）。 */
    private static final List<Pending> PENDING = new ArrayList<>();

    private VanillaCraftRefill() {
    }

    /** 原版拿走产物时记一笔：这一下合成之前，合成格长什么样。 */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!ModConfig.vanillaCraftRefill()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Shift+左键 才是「连点产物」的场景；普通单击合成不补，免得玩家只想做一个也被塞满。
        if (!player.isShiftKeyDown()) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        // 只看原版工作台；智能工作台自己也 extends CraftingMenu，但它有自己的补齐逻辑，必须排除
        if (!(menu instanceof CraftingMenu) || menu instanceof SmartWorkbenchMenu) {
            return;
        }
        Container grid = event.getInventory();
        if (!(grid instanceof CraftingContainer)) {
            return;
        }
        int size = grid.getContainerSize();
        if (size <= 0 || size > MAX_GRID_SLOTS) {
            return;
        }
        ItemStack[] before = new ItemStack[size];
        boolean any = false;
        for (int i = 0; i < size; i++) {
            before[i] = grid.getItem(i).copy();
            any |= !before[i].isEmpty();
        }
        if (!any) {
            return;
        }
        // 同一个玩家一 tick 内连点多次（配方书 Shift 一键做满这类会连发事件）：
        // 快照逐格取较大者合并，而不是整条覆盖——只留最后一份的话，同一 tick 里
        // 更早几次消耗掉的格子就没人补了（审查报告问题④）。
        // 合并的甄别条件是「同一位玩家 + 同一个菜单 + 同一个合成格容器」：
        // 万一一 tick 内在两个不同的原版工作台之间切换，两份快照各记各的，
        // 不能把后一个界面的消耗并进前一个界面的待办里（审查报告问题②）。
        Pending existing = null;
        for (Pending pending : PENDING) {
            if (pending.player == player && pending.menu == menu && pending.grid == grid) {
                existing = pending;
                break;
            }
        }
        if (existing == null) {
            PENDING.add(new Pending(player, menu, grid, before));
        } else {
            existing.mergeEarlier(before);
        }
    }

    /** 玩家退出：待办连同它攥着的玩家/菜单/容器引用一起清掉，别留在静态表里（报告问题⑥）。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.removeIf(pending -> pending.player == player);
        }
    }

    /** 停服：清空全部待办，跨存档不残留。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** 扣完材料后的下一个 tick 再补，避免和原版「扣材料 + 退空桶」抢同一个瞬间。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (PENDING.isEmpty() || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.player != player) {
                continue;
            }
            it.remove();
            pending.refill();
        }
    }

    private record Pending(ServerPlayer player, AbstractContainerMenu menu, Container grid,
                           ItemStack[] before) {

        /**
         * 并入同一 tick 里另一次合成的快照：空格子取有货的那份；同种物品取数量大的那份；
         * <b>两格放着不同种物品时保留先记的那份（mine）</b>——后一次的快照里看不到更早那次
         * 消耗掉的物品种类，照抄过去等于前一回用掉的东西没人补了（审查报告问题①，
         * 原来的三元写法在这里会无条件覆盖，与注释意图相反）。
         */
        void mergeEarlier(ItemStack[] other) {
            for (int i = 0; i < before.length && i < other.length; i++) {
                ItemStack mine = before[i];
                ItemStack theirs = other[i];
                if (theirs.isEmpty()) {
                    continue;
                }
                if (mine.isEmpty()
                        || (ItemStack.isSameItemSameTags(mine, theirs) && theirs.getCount() > mine.getCount())) {
                    before[i] = theirs.copy();
                }
            }
        }

        void refill() {
            // 人已经把界面关了（或换了界面）：原版会把格子里剩的东西退回去，这里什么都不做
            if (this.player.containerMenu != this.menu) {
                return;
            }
            Inventory inventory = this.player.getInventory();
            boolean changed = false;
            for (int i = 0; i < this.before.length; i++) {
                ItemStack want = this.before[i];
                if (want.isEmpty()) {
                    continue;
                }
                // 这格还有剩料（或退回来的空桶之类），说明没被扣空，不动它
                if (!this.grid.getItem(i).isEmpty()) {
                    continue;
                }
                int need = Math.min(want.getCount(), want.getMaxStackSize());
                ItemStack refill = ItemStack.EMPTY;
                for (int slot = 0; slot < MAIN_INVENTORY_SLOTS && need > 0; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, want)) {
                        continue;
                    }
                    int take = Math.min(stack.getCount(), need);
                    if (refill.isEmpty()) {
                        refill = stack.copy();
                        refill.setCount(take);
                    } else {
                        refill.grow(take);
                    }
                    stack.shrink(take);
                    if (stack.isEmpty()) {
                        inventory.setItem(slot, ItemStack.EMPTY);
                    }
                    need -= take;
                }
                if (!refill.isEmpty()) {
                    this.grid.setItem(i, refill);
                    changed = true;
                }
            }
            if (changed) {
                // 让客户端看到的合成格和产物格都跟上（服务端这边其实已经重算过一次产物了）
                this.menu.broadcastChanges();
            }
        }
    }
}
