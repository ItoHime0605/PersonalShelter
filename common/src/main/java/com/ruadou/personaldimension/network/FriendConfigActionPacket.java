package com.ruadou.personaldimension.network;

import java.util.UUID;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.config.ConfigManager;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



/**
 * 好友 / 权限组配置变更包（客户端 -&gt; 服务端）。
 *
 * <p>把主人对自己维度配置的 8 类改动合并为单个包，减少包类数量并便于后续扩展。
 * 所有改动一律以<b>发送者 UUID</b> 作为维度主人（{@link ServerPlayer#getUUID()}），
 * 玩家只能改自己的配置，无越权风险；改动后回传 {@link FriendDataPacket} 刷新界面。
 *
 * @param action      操作类型
 * @param target      目标玩家 UUID（增删好友 / 分配组用；其余操作填 {@link #EMPTY_UUID} 占位）
 * @param argA        主参数（组名 / 重命名旧名等，随 action 含义不同）
 * @param argB        次参数（重命名新名，仅 RENAME_GROUP 使用）
 * @param permOrdinal 权限序号（仅 SET_PERMISSION 使用，取 {@link Permission#ordinal()}）
 * @param flag        布尔参数（仅 SET_PERMISSION 使用，表示开启 / 关闭）
 */
public record FriendConfigActionPacket(Action action, UUID target,
                                       String argA, String argB, int permOrdinal, boolean flag)
        implements CustomPacketPayload {

    /** 无关目标玩家的操作用此 UUID 占位（编解码需非空）。 */
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    /** 配置改动类型。 */
    public enum Action {
        ADD_FRIEND, REMOVE_FRIEND, ASSIGN_GROUP,
        CREATE_GROUP, RENAME_GROUP, DELETE_GROUP,
        SET_PERMISSION, SET_DEFAULT_GROUP
    }

    public static final CustomPacketPayload.Type<FriendConfigActionPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "friend_config_action"));

    public static final StreamCodec<ByteBuf, FriendConfigActionPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public FriendConfigActionPacket decode(ByteBuf buf) {
                FriendlyByteBuf fb = new FriendlyByteBuf(buf);
                Action action = fb.readEnum(Action.class);
                UUID target = fb.readUUID();
                // 限制字符串长度防恶意客户端 OOM：组名/参数 50 字符
                String argA = fb.readUtf(50);
                String argB = fb.readUtf(50);
                int permOrdinal = fb.readVarInt();
                boolean flag = fb.readBoolean();
                return new FriendConfigActionPacket(action, target, argA, argB, permOrdinal, flag);
            }

            @Override
            public void encode(ByteBuf buf, FriendConfigActionPacket packet) {
                FriendlyByteBuf fb = new FriendlyByteBuf(buf);
                fb.writeEnum(packet.action);
                fb.writeUUID(packet.target);
                fb.writeUtf(packet.argA);
                fb.writeUtf(packet.argB);
                fb.writeVarInt(packet.permOrdinal);
                fb.writeBoolean(packet.flag);
            }
        };

    // ——— 静态工厂：避免调用方直接填 null / 占位值 ———

    public static FriendConfigActionPacket addFriend(UUID friend) {
        return new FriendConfigActionPacket(Action.ADD_FRIEND, friend, "", "", 0, false);
    }

    public static FriendConfigActionPacket removeFriend(UUID friend) {
        return new FriendConfigActionPacket(Action.REMOVE_FRIEND, friend, "", "", 0, false);
    }

    /** 给好友分配组名；空串表示解除分配（回落默认组）。 */
    public static FriendConfigActionPacket assignGroup(UUID friend, String groupName) {
        return new FriendConfigActionPacket(Action.ASSIGN_GROUP, friend, groupName, "", 0, false);
    }

    public static FriendConfigActionPacket createGroup(String name) {
        return new FriendConfigActionPacket(Action.CREATE_GROUP, EMPTY_UUID, name, "", 0, false);
    }

    public static FriendConfigActionPacket renameGroup(String oldName, String newName) {
        return new FriendConfigActionPacket(Action.RENAME_GROUP, EMPTY_UUID, oldName, newName, 0, false);
    }

    public static FriendConfigActionPacket deleteGroup(String name) {
        return new FriendConfigActionPacket(Action.DELETE_GROUP, EMPTY_UUID, name, "", 0, false);
    }

    public static FriendConfigActionPacket setPermission(String groupName, Permission permission, boolean enabled) {
        return new FriendConfigActionPacket(
            Action.SET_PERMISSION, EMPTY_UUID, groupName, "", permission.ordinal(), enabled);
    }

    public static FriendConfigActionPacket setDefaultGroup(String name) {
        return new FriendConfigActionPacket(Action.SET_DEFAULT_GROUP, EMPTY_UUID, name, "", 0, false);
    }

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID owner = player.getUUID();
        switch (this.action) {
            case ADD_FRIEND -> FriendService.addFriend(server, owner, this.target);
            case REMOVE_FRIEND -> FriendService.removeFriend(server, owner, this.target);
            case ASSIGN_GROUP -> FriendService.assignGroup(server, owner, this.target, this.argA);
            case CREATE_GROUP -> FriendService.createGroup(server, owner, this.argA);
            case RENAME_GROUP -> FriendService.renameGroup(server, owner, this.argA, this.argB);
            case DELETE_GROUP -> FriendService.deleteGroup(server, owner, this.argA);
            case SET_PERMISSION -> {
                Permission[] all = Permission.values();
                if (this.permOrdinal >= 0 && this.permOrdinal < all.length) {
                    Permission perm = all[this.permOrdinal];
                    // 服务端校验：被配置禁用的权限项不允许通过包设置（防恶意客户端绕过）
                    if (ConfigManager.isPermissionEnabled(perm)) {
                        FriendService.setGroupPermission(server, owner, this.argA, perm, this.flag);
                    }
                }
            }
            case SET_DEFAULT_GROUP -> FriendService.setDefaultGroup(server, owner, this.argA);
        }
        // 权限收紧（关进入权限 / 改分组 / 删组 / 移除好友 / 换默认组）可能使在场访客失去 ENTER，
        // 但配置改动本身不会踢人，故变更后统一复检并驱逐已无权限的在场访客
        PersonalDimensionService.evictUnauthorizedVisitors(server, owner);
        // 改动后回传最新快照，使发起改动的客户端界面即时刷新
        Platform.sendToPlayer(player, FriendDataPacket.of(server, owner));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
