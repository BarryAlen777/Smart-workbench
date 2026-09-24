package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * REI / JEI / EMI 三套物品管理器共用的「材料够不够」判断。
 * <p>
 * 三家的配方数据结构完全不一样（REI 的 EntryStack、JEI 的 Ingredient、EMI 的 EmiIngredient），
 * 所以各家桥只负责把配方翻译成「每个材料槽的候选物品清单」，真正怎么扣材料只写在这里一份，
 * 免得三家各写一套、改了一处忘了另外两处。
 */
public final class MaterialAvailability {

    private MaterialAvailability() {
    }

    /** 客户端可由智能工作台直接取用的材料：合成格里已有的 + 服务器推来的接入存储快照。 */
    public static List<ItemStack> collectAvailable(SmartWorkbenchMenu menu) {
        List<ItemStack> available = new ArrayList<>();
        for (ItemStack stack : menu.getMatrixItems()) {
            if (!stack.isEmpty()) {
                available.add(stack);
            }
        }
        for (ItemStack stack : menu.getClientStorageStacks()) {
            if (!stack.isEmpty()) {
                available.add(stack);
            }
        }
        return available;
    }

    /**
     * 逐个材料槽从可用材料里扣一件，扣不到的就是缺料。
     * 候选越少的槽越先分配，避免木板这类通用材料被前面的槽先吃掉、后面明明够却报缺料。
     * <p>
     * 没有任何物品候选的槽（比如纯流体的材料）一律当成满足，宁可放行也不要误报缺料。
     *
     * @param candidates 每个材料槽的候选物品（多选一）
     * @param available  手上能用的材料，会在内部复制一份再扣，不会改动传进来的列表
     * @return 与 candidates 等长的数组，true 表示这个槽能配上
     */
    public static boolean[] match(List<List<ItemStack>> candidates, List<ItemStack> available) {
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : available) {
            if (stack != null && !stack.isEmpty()) {
                remaining.add(stack.copy());
            }
        }
        boolean[] matched = new boolean[candidates.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidateCount(candidates.get(i)) > 0) {
                order.add(i);
            } else {
                matched[i] = true;
            }
        }
        order.sort(Comparator.comparingInt(index -> candidateCount(candidates.get(index))));
        for (int index : order) {
            for (ItemStack candidate : candidates.get(index)) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                int at = findMatching(remaining, candidate);
                if (at >= 0) {
                    ItemStack held = remaining.get(at);
                    if (held.getCount() <= 1) {
                        remaining.remove(at);
                    } else {
                        held.shrink(1);
                    }
                    matched[index] = true;
                    break;
                }
            }
        }
        return matched;
    }

    /** 候选清单里真正能拿出来的物品数量（挑候选最少的槽优先分配时用）。 */
    public static int candidateCount(List<ItemStack> candidates) {
        int count = 0;
        for (ItemStack stack : candidates) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 候选物和手上的物品算不算同一个东西。
     * 候选自带 NBT（附魔书、药水之类）时必须连标签一起对上，否则只认物品本身。
     */
    public static boolean matches(ItemStack held, ItemStack candidate) {
        return held.getItem() == candidate.getItem()
                && (candidate.getTag() == null || candidate.getTag().isEmpty()
                || ItemStack.isSameItemSameTags(held, candidate));
    }

    private static int findMatching(List<ItemStack> available, ItemStack candidate) {
        for (int i = 0; i < available.size(); i++) {
            if (matches(available.get(i), candidate)) {
                return i;
            }
        }
        return -1;
    }
}
