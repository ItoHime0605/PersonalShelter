package com.ruadou.personaldimension.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.client.FriendDataSnapshot;
import com.ruadou.personaldimension.friend.FriendPermissionData;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.OwnerFriendConfig;
import com.ruadou.personaldimension.friend.PermissionGroup;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;


/**
 * 好友与权限配置快照包（服务端 -&gt; 客户端）。
 *
 * <p>携带某维度主人的权限组列表、好友列表与默认组名，供好友管理 / 权限组界面读取展示。
 * 含嵌套列表，字段超出 {@code StreamCodec.composite} 上限，故手写编解码器
 * （用 {@link FriendlyByteBuf} 包装底层 {@link ByteBuf} 以复用 UUID/String/VarInt 读写）。
 *
 * @param groups       权限组视图列表（组名 + 权限位掩码）
 * @param friends      好友视图列表（UUID + 显示名 + 所属组名，空串表示未分配）
 * @param defaultGroup 默认组名（空串表示未设置默认组）
 */
public record FriendDataPacket(List<GroupView> groups, List<FriendView> friends, String defaultGroup)
        implements CustomPacketPayload {

    /** 权限组视图：组名 + 权限位掩码（bit i = {@code Permission.values()[i]} 是否开启）。 */
    public record GroupView(String name, int mask) {}

    /** 好友视图：UUID + 显示名 + 所属组名（空串=未分配，运行时回落默认组）。 */
    public record FriendView(UUID id, String name, String group) {}

    public static final CustomPacketPayload.Type<FriendDataPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "friend_data"));

    public static final StreamCodec<ByteBuf, FriendDataPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public FriendDataPacket decode(ByteBuf buf) {
                FriendlyByteBuf fb = new FriendlyByteBuf(buf);
                int groupCount = fb.readVarInt();
                List<GroupView> groups = new ArrayList<>(groupCount);
                for (int i = 0; i < groupCount; i++) {
                    groups.add(new GroupView(fb.readUtf(), fb.readVarInt()));
                }
                int friendCount = fb.readVarInt();
                List<FriendView> friends = new ArrayList<>(friendCount);
                for (int i = 0; i < friendCount; i++) {
                    friends.add(new FriendView(fb.readUUID(), fb.readUtf(), fb.readUtf()));
                }
                return new FriendDataPacket(groups, friends, fb.readUtf());
            }

            @Override
            public void encode(ByteBuf buf, FriendDataPacket packet) {
                FriendlyByteBuf fb = new FriendlyByteBuf(buf);
                fb.writeVarInt(packet.groups.size());
                for (GroupView g : packet.groups) {
                    fb.writeUtf(g.name());
                    fb.writeVarInt(g.mask());
                }
                fb.writeVarInt(packet.friends.size());
                for (FriendView f : packet.friends) {
                    fb.writeUUID(f.id());
                    fb.writeUtf(f.name());
                    fb.writeUtf(f.group());
                }
                fb.writeUtf(packet.defaultGroup);
            }
        };

    /**
     * 构建某维度主人的配置快照（主人查看 / 编辑自己的好友配置时用）。
     * 用 {@link FriendPermissionData#getOrCreateConfig(UUID)}，首次打开即播种默认组。
     */
    public static FriendDataPacket of(MinecraftServer server, UUID owner) {
        OwnerFriendConfig config = FriendPermissionData.get(server).getOrCreateConfig(owner);
        List<GroupView> groups = new ArrayList<>();
        for (PermissionGroup group : config.groups().values()) {
            groups.add(new GroupView(group.getName(), group.mask()));
        }
        List<FriendView> friends = new ArrayList<>();
        for (Map.Entry<UUID, String> e : config.friends().entrySet()) {
            friends.add(new FriendView(e.getKey(), FriendService.resolveName(server, e.getKey()), e.getValue()));
        }
        String defaultGroup = config.defaultGroupName() == null ? "" : config.defaultGroupName();
        return new FriendDataPacket(groups, friends, defaultGroup);
    }

    public void handle() {
        // 仅客户端接收；Packet 转 Snapshot 后写入 common 缓存，避免平台端持有客户端专用类
        java.util.List<FriendDataSnapshot.GroupView> groups = new java.util.ArrayList<>(this.groups.size());
        for (GroupView g : this.groups) {
            groups.add(new FriendDataSnapshot.GroupView(g.name(), g.mask()));
        }
        java.util.List<FriendDataSnapshot.FriendView> friends = new java.util.ArrayList<>(this.friends.size());
        for (FriendView f : this.friends) {
            friends.add(new FriendDataSnapshot.FriendView(f.id(), f.name(), f.group()));
        }
        ClientFriendData.updateConfig(new FriendDataSnapshot(groups, friends, defaultGroup));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
