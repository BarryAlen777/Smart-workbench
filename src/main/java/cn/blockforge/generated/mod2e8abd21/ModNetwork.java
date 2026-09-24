package cn.blockforge.generated.mod2e8abd21;

import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SCraftRequestPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SPlaceRecipePacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SRecursiveCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CCraftableListPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CRecursiveCraftPromptPacket;
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

    private static final String VERSION = "1";
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
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}
