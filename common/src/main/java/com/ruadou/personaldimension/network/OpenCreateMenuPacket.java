package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.menu.PersonalDimensionCreateMenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;


/**
 * 请求打开"创建维度"容器界面（客户端 -> 服务端）。
 *
 * <p>由控制器信息页的"创建维度"按钮触发。服务端 {@code openMenu} 后会向客户端下发 OpenScreen，
 * 自动切换到 {@link PersonalDimensionCreateMenu} 对应的界面。
 *
 * @param isPublic true=创建公共维度，false=创建个人维度
 */
public record OpenCreateMenuPacket(boolean isPublic) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenCreateMenuPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "open_create_menu"));

    public static final StreamCodec<ByteBuf, OpenCreateMenuPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenCreateMenuPacket::isPublic,
            OpenCreateMenuPacket::new);

    /** 便捷构造：默认创建个人维度。 */
    public OpenCreateMenuPacket() {
        this(false);
    }

    public void handle(ServerPlayer player) {
        if (this.isPublic && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("只有 OP 可以建立公共維度。"));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, p) -> new PersonalDimensionCreateMenu(id, inventory, this.isPublic),
            Component.translatable("gui.personaldimension.create.title")));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
