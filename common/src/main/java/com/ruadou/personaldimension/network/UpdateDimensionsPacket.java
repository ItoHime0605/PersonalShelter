package com.ruadou.personaldimension.network;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.ruadou.personaldimension.PersonalDimension;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;


/**
 * 维度列表更新包：通知客户端向其已知维度集合增删维度键。
 * 动态创建的维度未包含在客户端登录时同步的维度列表中，需通过此包补充，
 * 否则客户端会因"未知维度"导致传送/渲染出错。
 *
 * @param keys 需要增删的维度键集合
 * @param add  true 表示新增，false 表示移除
 */
public record UpdateDimensionsPacket(Set<ResourceKey<Level>> keys, boolean add)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateDimensionsPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "update_dimensions"));

    public static final StreamCodec<ByteBuf, UpdateDimensionsPacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION)
                .apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf),
            UpdateDimensionsPacket::keys,
            ByteBufCodecs.BOOL,
            UpdateDimensionsPacket::add,
            UpdateDimensionsPacket::new);

    public void handle() {
        ClientHandler.handle(this);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端调用封装在独立静态类，避免服务端加载客户端类。 */
    private static final class ClientHandler {
        private static void handle(UpdateDimensionsPacket packet) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Set<ResourceKey<Level>> dimensionList = player.connection.levels();
            if (dimensionList == null) {
                return;
            }
            Consumer<ResourceKey<Level>> consumer =
                packet.add() ? dimensionList::add : dimensionList::remove;
            packet.keys().forEach(consumer);
        }
    }
}
