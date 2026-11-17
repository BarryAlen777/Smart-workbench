package cn.blockforge.generated.mod2e8abd21.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：一条配方每样材料<b>现在分别放在哪儿</b>（材料溯源的返回包）。
 * <p>
 * 每个「去处」要么是<b>世界上的一台容器</b>（箱子、桶、精妙存储抽屉——带坐标，
 * 客户端既能把字写在提示框里、也能进世界把那台容器框出来高亮），
 * 要么是<b>没有方块坐标的库存</b>（精妙背包内部、玩家自己身上——只出文字，不画框）。
 * 带坐标的去处同时带<b>整台容器占的格</b> {@code cells}（大箱子两格拼一台），
 * 金框描的是整台而不是锚点那一格——和 F12 总览的分组清单同一口径（r239，接 r233）。
 * 上限：一份配方最多回 9 种材料（3×3 摆满正好九种，一种都不会被挤掉）、
 * 每种最多 4 个去处，悬停查询而已，宁可少说不刷屏。
 */
public class S2CTraceResponsePacket {

    /** 一种材料 + 它现在的去处清单。partial=true 表示扫描预算耗尽、去处清单可能不全。 */
    public record MaterialHit(ItemStack preview, int need, List<Spot> spots, boolean partial) {
    }

    /**
     * 一件材料的一个去处：容器显示名、锚点坐标（背包/身上这类没有的就是 null）、
     * 里面有多少个、整台容器占的格（{@code pos==null} 时为空；非空时第一个就是锚点）。
     */
    public record Spot(Component name, BlockPos pos, int count, List<BlockPos> cells) {
    }

    public static final int MAX_MATERIALS = 9;
    public static final int MAX_SPOTS = 4;

    /** 单个去处带的格数上限：服务端拼组封顶 16，这里留余量，只挡畸形包。 */
    private static final int MAX_CELLS_PER_SPOT = 32;

    private final BlockPos benchPos;
    private final ResourceLocation recipeId;
    private final List<MaterialHit> materials;

    public S2CTraceResponsePacket(BlockPos benchPos, ResourceLocation recipeId, List<MaterialHit> materials) {
        this.benchPos = benchPos;
        this.recipeId = recipeId;
        this.materials = List.copyOf(materials);
    }

    public S2CTraceResponsePacket(FriendlyByteBuf buf) {
        this.benchPos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        // 解码在客户端 netty 线程上跑，分配先于校验：钳到合法上限，畸形包撑不爆内存。
        // 多出来没读的字节随这帧一起作废（包体按长度分帧），不会串到下一个包。
        int size = Math.max(0, Math.min(buf.readVarInt(), MAX_MATERIALS));
        List<MaterialHit> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack preview = buf.readItem();
            int count = buf.readVarInt();
            if (!preview.isEmpty()) {
                preview.setCount(Math.max(1, count));
            }
            int need = buf.readVarInt();
            boolean partial = buf.readBoolean();
            int spotCount = Math.max(0, Math.min(buf.readVarInt(), MAX_SPOTS));
            List<Spot> spots = new ArrayList<>(spotCount);
            for (int j = 0; j < spotCount; j++) {
                Component name = buf.readComponent();
                BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
                int have = buf.readVarInt();
                List<BlockPos> cells = List.of();
                if (pos != null) {
                    int cellCount = Math.max(0, Math.min(buf.readVarInt(), MAX_CELLS_PER_SPOT));
                    List<BlockPos> read = new ArrayList<>(cellCount);
                    for (int k = 0; k < cellCount && buf.readableBytes() >= 8; k++) {
                        read.add(buf.readBlockPos());
                    }
                    // 锚点必须排在组第一格：客户端按 group.get(0) 去重、按它算剔除距离。
                    // 不管收到的顺序如何（畸形包/未来改格式把锚点塞在组中间），统一归位（r241 问题③）：
                    // 找到了就挪到首位、其余格保序；压根没带就当场补一个，绝不让别的格冒充锚点。
                    cells = normalizeCells(read, pos);
                }
                spots.add(new Spot(name, pos, have, cells));
            }
            // 材料本体是空的（物品被注册表回收之类）就别进清单，免得界面画出一格 "Air"
            if (!preview.isEmpty()) {
                list.add(new MaterialHit(preview, need, spots, partial));
            }
        }
        this.materials = List.copyOf(list);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.benchPos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeVarInt(this.materials.size());
        for (MaterialHit hit : this.materials) {
            buf.writeItem(hit.preview().copyWithCount(1));
            buf.writeVarInt(Math.min(999, hit.preview().getCount()));
            buf.writeVarInt(Math.min(999, hit.need()));
            buf.writeBoolean(hit.partial());
            buf.writeVarInt(hit.spots().size());
            for (Spot spot : hit.spots()) {
                buf.writeComponent(spot.name());
                buf.writeBoolean(spot.pos() != null);
                if (spot.pos() != null) {
                    buf.writeBlockPos(spot.pos());
                }
                buf.writeVarInt(Math.min(999, spot.count()));
                // 整台格组只在带坐标的去处后跟发（背包/身上画不了框，一个字节都不必占）
                if (spot.pos() != null) {
                    int cells = Math.min(spot.cells().size(), MAX_CELLS_PER_SPOT);
                    buf.writeVarInt(cells);
                    for (int k = 0; k < cells; k++) {
                        buf.writeBlockPos(spot.cells().get(k));
                    }
                }
            }
        }
    }

    /**
     * 解码兜底：把锚点统一归位到组首（r241 问题③）。正常服务端回包本来就锚点第一，这里原样返回；
     * 锚点在组中间就挪到首位、其余格保序；压根没带锚点就补一个——客户端画整台框、去重、
     * 距离剔除全按 {@code group.get(0)} 当锚点，顺序错了会把别的格当锚点，框的位置跟着偏。
     */
    private static List<BlockPos> normalizeCells(List<BlockPos> read, BlockPos anchor) {
        int at = read.indexOf(anchor);
        if (at == 0) {
            return List.copyOf(read);
        }
        List<BlockPos> fixed = new ArrayList<>(read.size() + (at < 0 ? 1 : 0));
        fixed.add(anchor);
        for (int k = 0; k < read.size(); k++) {
            if (k != at) {
                fixed.add(read.get(k));
            }
        }
        return List.copyOf(fixed);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null
                    && minecraft.player.containerMenu instanceof cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.benchPos)) {
                menu.setClientTraceResult(this.recipeId, this.materials);
            }
        });
        context.setPacketHandled(true);
    }
}
