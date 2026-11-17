package cn.blockforge.generated.mod2e8abd21;

import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftGridPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SCraftRequestPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SConnectionScanPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SPlaceRecipePacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SQueuePacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SRecursiveCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2STraceRequestPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CAutoRefillPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CConnectionScanPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CCraftableListPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CQueueStatusPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CRecursiveCraftPromptPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CStorageSnapshotPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CTraceResponsePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道：服务器把「可合成列表 + 接入存储快照」推给客户端；
 * 客户端把「点了列表里的哪个配方」（含 Ctrl 批量合成）和「REI 点了加号要摆哪份配方」发回服务器。
 */
public final class ModNetwork {

    /**
     * 网络协议版本。任一包字段结构变了都要 +1（连不上会明确提示版本不一致，而不是解包错位）：
     * S2CCraftableListPacket 加「快照截断」位、材料溯源加两包、合成队列加两包各 +1；
     * 这一轮溯源回包每种材料加了「扫描是否被预算截断」的 partial 位、材料上限 8→9，再 +1；
     * 再加一包轻量「只刷存储快照」（列表内容没变时不再重发整张表），再 +1；
     * F12 接入总览加「问工作台连着哪些容器」一对包，再 +1；
     * r223/r224 给总览回包加了「过滤前绑定总数」两个 varint（S2CConnectionScanPacket 的
     * encode/decode 字段各多两笔），包格式变了、版本串却没动——新客户端连旧服务端解包
     * 会越界（r225 审查报告问题①），这里补上这一笔，再 +1；
     * r227 又给同一份回包加了「已被拆掉/换掉」台数两个 varint（r227 审查报告问题①），再 +1；
     * r233 把同一份回包的两份坐标清单改成<b>分组</b>清单（大箱子两格拼一台、合描一个框），
     * encode/decode 结构变了，再 +1；
     * r239 给溯源回包的每个带坐标去处加了「整台容器占的格」清单（金框同样描整台），
     * encode/decode 结构又变，再 +1。
     */
    private static final String VERSION = "12";
    private static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SmartWorkbenchMod.MOD_ID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        INSTANCE.messageBuilder(S2CCraftableListPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCraftableListPacket::encode)
                .decoder(S2CCraftableListPacket::new)
                .consumerNetworkThread(S2CCraftableListPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SCraftRequestPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCraftRequestPacket::encode)
                .decoder(C2SCraftRequestPacket::new)
                .consumerNetworkThread(C2SCraftRequestPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SPlaceRecipePacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SPlaceRecipePacket::encode)
                .decoder(C2SPlaceRecipePacket::new)
                .consumerNetworkThread(C2SPlaceRecipePacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SRecursiveCraftPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRecursiveCraftPacket::encode)
                .decoder(C2SRecursiveCraftPacket::new)
                .consumerNetworkThread(C2SRecursiveCraftPacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CRecursiveCraftPromptPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CRecursiveCraftPromptPacket::encode)
                .decoder(S2CRecursiveCraftPromptPacket::new)
                .consumerNetworkThread(S2CRecursiveCraftPromptPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SAutoCraftPacket.class, 5, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAutoCraftPacket::encode)
                .decoder(C2SAutoCraftPacket::new)
                .consumerNetworkThread(C2SAutoCraftPacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CAutoRefillPacket.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAutoRefillPacket::encode)
                .decoder(S2CAutoRefillPacket::new)
                .consumerNetworkThread(S2CAutoRefillPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SAutoCraftGridPacket.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAutoCraftGridPacket::encode)
                .decoder(C2SAutoCraftGridPacket::new)
                .consumerNetworkThread(C2SAutoCraftGridPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2STraceRequestPacket.class, 8, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STraceRequestPacket::encode)
                .decoder(C2STraceRequestPacket::new)
                .consumerNetworkThread(C2STraceRequestPacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CTraceResponsePacket.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CTraceResponsePacket::encode)
                .decoder(S2CTraceResponsePacket::new)
                .consumerNetworkThread(S2CTraceResponsePacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SQueuePacket.class, 10, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SQueuePacket::encode)
                .decoder(C2SQueuePacket::new)
                .consumerNetworkThread(C2SQueuePacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CQueueStatusPacket.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CQueueStatusPacket::encode)
                .decoder(S2CQueueStatusPacket::new)
                .consumerNetworkThread(S2CQueueStatusPacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CStorageSnapshotPacket.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CStorageSnapshotPacket::encode)
                .decoder(S2CStorageSnapshotPacket::new)
                .consumerNetworkThread(S2CStorageSnapshotPacket::handle)
                .add();
        INSTANCE.messageBuilder(C2SConnectionScanPacket.class, 13, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SConnectionScanPacket::encode)
                .decoder(C2SConnectionScanPacket::new)
                .consumerNetworkThread(C2SConnectionScanPacket::handle)
                .add();
        INSTANCE.messageBuilder(S2CConnectionScanPacket.class, 14, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CConnectionScanPacket::encode)
                .decoder(S2CConnectionScanPacket::new)
                .consumerNetworkThread(S2CConnectionScanPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}
