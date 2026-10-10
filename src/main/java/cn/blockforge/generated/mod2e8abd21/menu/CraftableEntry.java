package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面右侧“可合成”列表里的一项：配方 ID + 产物预览 + 是否链式项 + 同产物的其他配方。
 * <p>
 * {@code chain=true} 表示直接材料不齐、但整棵依赖树的基础材料都在，
 * 点它要先自动补做中间材料（下级链式合成）才能出这份配方；界面上画「链」角标。
 * <p>
 * <b>同一件产物有好几份配方时怎么办：</b>比如精妙存储的木箱，既能用「木板+拉杆」做、
 * 也能用「原版箱子+拉杆」做。列表里只列<b>一条</b>，免得玩家看到两个一模一样的图标
 * 分不清点哪个；剩下的几份收进 {@code alternatives}，玩家<b>右键那一格</b>就能轮换过去，
 * 换到的那份会连着自己的材料一起显示在提示框里。
 * <p>
 * 注意这跟「多态合成」(Polymorph) 不是一回事：多态合成管的是<b>同样一把材料能出不同产物</b>
 * 时该出哪一个，而这里是<b>同一个产物该用哪一套材料</b>，方向正好相反——所以多态合成
 * 那个产物格旁的小按钮在这里根本不会出现，切换得我们自己动手。
 */
public record CraftableEntry(ResourceLocation recipeId, ItemStack result, boolean chain,
                             List<Option> alternatives) {

    /**
     * 同一件产物的一份候选配方。
     *
     * @param recipeId 配方 ID
     * @param chain    这份配方是直接有料，还是要先链式补做中间材料
     */
    public record Option(ResourceLocation recipeId, boolean chain) {

        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(this.recipeId);
            buf.writeBoolean(this.chain);
        }

        public static Option read(FriendlyByteBuf buf) {
            return new Option(buf.readResourceLocation(), buf.readBoolean());
        }
    }

    /** 一条列表项最多带几份候选配方：再多也只是刷屏，还会把网络包撑大。 */
    public static final int MAX_OPTIONS = 8;

    public CraftableEntry {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    /** 只有一份配方、没有候选的普通列表项。 */
    public static CraftableEntry of(ResourceLocation recipeId, ItemStack result, boolean chain) {
        return new CraftableEntry(recipeId, result, chain, List.of());
    }

    /** 当前这份 + 候选们，按优先级排（排在最前面的就是现在正在用的那份）。 */
    public List<Option> options() {
        List<Option> all = new ArrayList<>(1 + this.alternatives.size());
        all.add(new Option(this.recipeId, this.chain));
        all.addAll(this.alternatives);
        return all;
    }

    /** 这件产物一共有几份配方（含当前这份）。 */
    public int optionCount() {
        return 1 + this.alternatives.size();
    }

    public boolean hasAlternatives() {
        return !this.alternatives.isEmpty();
    }

    /**
     * 换成候选里的某一份配方：新项用的就是 {@code id}，其余候选按<b>环形顺序</b>排。
     * <p>
     * 为什么是环形：右键是「换下一份」，连着点要把每份配方各轮一遍再回到开头。
     * 如果换完就把原来那份塞回候选最前面，列表就会在两份之间来回跳，第三份永远轮不到。
     * 所以换完之后，候选从「被选中那份的后面」接着排，前面那截接到尾巴上。
     * <p>
     * {@code id} 不在本项候选之内时原样返回（列表刚刷新、配方被别的模组改掉之类）。
     */
    public CraftableEntry withActive(ResourceLocation id) {
        if (id == null || id.equals(this.recipeId)) {
            return this;
        }
        List<Option> all = options();
        int at = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).recipeId().equals(id)) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return this;
        }
        List<Option> rest = new ArrayList<>(all.size() - 1);
        for (int i = at + 1; i < all.size(); i++) {
            rest.add(all.get(i));
        }
        for (int i = 0; i < at; i++) {
            rest.add(all.get(i));
        }
        Option active = all.get(at);
        return new CraftableEntry(active.recipeId(), this.result, active.chain(), rest);
    }

    /** 轮换到下一份候选配方（右键列表项就是走这里）。只有一份时原样返回。 */
    public CraftableEntry cycled() {
        List<Option> all = options();
        return all.size() < 2 ? this : withActive(all.get(1).recipeId());
    }

    /**
     * 把另一条「同产物」的列表项并进本条的候选清单（服务端去重时用）。
     * 排在前面的一份继续当「当前在用」，后来的只进候选；重复的配方 ID 不会收第二次，
     * 总数封顶 {@link #MAX_OPTIONS}，免得某个产物被整合包塞了几十份配方把网络包撑爆。
     */
    public CraftableEntry mergedWith(CraftableEntry other) {
        if (other == null || optionCount() >= MAX_OPTIONS) {
            return this;
        }
        List<Option> merged = new ArrayList<>(this.alternatives);
        for (Option option : other.options()) {
            if (option.recipeId().equals(this.recipeId)) {
                continue; // 本条正在用的就是这份，不用重复收
            }
            boolean seen = false;
            for (Option have : merged) {
                if (have.recipeId().equals(option.recipeId())) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            if (1 + merged.size() >= MAX_OPTIONS) {
                break;
            }
            merged.add(option);
        }
        return new CraftableEntry(this.recipeId, this.result, this.chain, merged);
    }

    /** 判据：物品、数量、NBT 全一样才算「同一件产物」。 */
    public static String resultKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return String.valueOf(id) + "#" + stack.getCount()
                + "#" + (stack.hasTag() ? stack.getTag().toString() : "");
    }

    public String resultKey() {
        return resultKey(this.result);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.recipeId);
        buf.writeItemStack(this.result, false);
        buf.writeBoolean(this.chain);
        buf.writeVarInt(this.alternatives.size());
        for (Option option : this.alternatives) {
            option.write(buf);
        }
    }

    public static CraftableEntry read(FriendlyByteBuf buf) {
        ResourceLocation recipeId = buf.readResourceLocation();
        ItemStack result = buf.readItem();
        boolean chain = buf.readBoolean();
        int size = buf.readVarInt();
        List<Option> alternatives = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            alternatives.add(Option.read(buf));
        }
        return new CraftableEntry(recipeId, result, chain, alternatives);
    }

    /** 用来判断列表有没有变化，变了才发包。 */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.recipeId).append('|').append(this.result.getCount())
                .append(this.chain ? "|chain" : "");
        for (Option option : this.alternatives) {
            sb.append('|').append(option.recipeId()).append(option.chain() ? "c" : "d");
        }
        return sb.toString();
    }
}
