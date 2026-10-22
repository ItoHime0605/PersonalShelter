package com.ruadou.personaldimension.dimension;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.attachment.TravelMemory;
import com.ruadou.personaldimension.attachment.TravelPoint;
import com.ruadou.personaldimension.dimension.template.DimensionTemplate;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;
import com.ruadou.personaldimension.network.DimensionInfoPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;

/**
 * 个人维度业务服务。
 * 编排"获取或创建维度 -> 铺落脚平台 -> 传送玩家 -> 反馈"的完整流程，
 * 作为物品层与底层建维逻辑之间的中间层，降低耦合。
 *
 * <p>平台无关：所有平台特定操作（动态维度管理、网络发送、旅行记忆访问等）
 * 均通过 {@link Platform} SPI 抽象，common 模块此份实现同时服务于 Fabric 和 NeoForge。
 */
public final class PersonalDimensionService {

    private PersonalDimensionService() {}

    /**
     * 标记正在通过控制器"访问"按钮传送的玩家 UUID。
     * 用于区分控制器传送（handleVisit）与其他传送方式（/tp 等），
     * 以便维度进入守卫按好友访问传送器的模式决定放行或重定向。
     * 仅服务端主线程读写，用并发集合防御性保护。
     */
    private static final Set<UUID> CONTROLLER_VISITING = ConcurrentHashMap.newKeySet();

    /** 标记玩家正在通过控制器传送（handleVisit 调用，传送前设置） */
    public static void markControllerVisit(UUID playerId) {
        CONTROLLER_VISITING.add(playerId);
    }

    /** 检查并清除控制器传送标记，返回 true 表示当前传送由控制器发起 */
    public static boolean consumeControllerVisit(UUID playerId) {
        return CONTROLLER_VISITING.remove(playerId);
    }

    /**
     * 控制器"创建维度"逻辑：按预设创建玩家专属个人维度并传送一次。
     *
     * <p>已存在则动作栏提示且不创建；否则记录返回点、建维、按预设铺落脚结构、传送到落脚点、
     * 记录 own 记忆点。前置校验（是否已存在、生成器是否就绪、自定义模板是否合法）由
     * {@code CreateDimensionPacket} 在消耗生成器前完成，故此处走到建维即视为必然成功。
     *
     * <p>{@link DimensionPreset#CUSTOM} 走可存档的 {@link Platform#createStem} 生成器
     * （地形由生成器逐区块平铺），落脚点取模板 {@link DimensionTemplate#spawnY()}，不再手动铺结构；
     * 虚空 / 空岛仍用虚空 stem + 服务端手动铺落脚结构。
     *
     * @param preset   落脚地形预设（见 {@link DimensionPreset}）
     * @param template 自定义模板数据，仅 {@link DimensionPreset#CUSTOM} 使用，其余可为 null
     * @return 是否成功创建（已存在或服务器缺失返回 false）
     */
    public static boolean createPersonalDimension(ServerPlayer player, DimensionPreset preset, DimensionTemplate template) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ResourceKey<Level> levelKey = Platform.levelKeyFor(player.getUUID());
        // 已存在：动作栏提示，不重复创建、不传送
        if (server.getLevel(levelKey) != null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.already_exists"), true);
            return false;
        }
        // 首次创建：若身处个人维度之外，记录当前位置作为控制器"退出"的返回点
        if (!Platform.isPersonalDimension(player.level().dimension())) {
            updateMemory(player, mem -> mem.withOutside(TravelPoint.of(player)));
        }
        // 按预设选择 LevelStem 工厂：自定义模板走可存档生成器，其余共用虚空 stem
        ServerLevel level = getOrCreateOwnLevel(player, () -> Platform.createStem(server, preset, template));
        if (level == null) {
            return false;
        }
        PersonalDimension.LOGGER.info("玩家 [{}] 创建个人维度: {} (预设 {})",
            player.getGameProfile().getName(), level.dimension().location(), preset);

        // 自定义模板由生成器铺好地形，仅取落脚点；其余预设由服务端手动铺设落脚结构
        BlockPos spawn = preset.requiresTemplate() && template != null
            ? new BlockPos(0, template.spawnY(), 0)
            : buildSpawn(level, preset);
        player.teleportTo(level,
            spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            player.getYRot(), player.getXRot());
        // 记录 own 记忆点，使后续控制器"进入"回到此落脚点
        updateMemory(player, mem -> mem.withOwn(TravelPoint.of(player)));
        player.sendSystemMessage(
            Component.translatable("personaldimension.message.entered", level.dimension().location().toString()));
        return true;
    }

    /**
     * 获取或创建玩家自己的个人维度（虚空 stem）并完成持久化绑定，返回其 {@link ServerLevel}。
     * 供进入/传送等场景复用：这些场景维度必已存在，不会触发工厂建维。
     */
    public static ServerLevel getOrCreateOwnLevel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        return getOrCreateOwnLevel(player, () -> Platform.createPersonalStem(server));
    }

    /**
     * 获取或创建玩家自己的个人维度并完成持久化绑定，返回其 {@link ServerLevel}。
     * 维度不存在时用 {@code stemFactory} 生成 LevelStem（决定预设/生成器类型），
     * 抽出此步骤供创建与进入等多处复用，避免重复拼装建维逻辑（服务器缺失时返回 null）。
     */
    public static ServerLevel getOrCreateOwnLevel(ServerPlayer player, Supplier<LevelStem> stemFactory) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        ResourceKey<Level> levelKey = Platform.levelKeyFor(player.getUUID());
        // 传入 PersonalDimensionLevelData 工厂 + tickTime=true，使个人维度时间与主世界隔离
        ServerLevel level = Platform.getOrCreateLevel(
            server, levelKey,
            stemFactory,
            worldData -> new PersonalDimensionLevelData(worldData, worldData.overworldData()),
            true);
        // 绑定持久化载体：首次创建时写入初值，后续变化随维度存档保留
        if (level.getLevelData() instanceof PersonalDimensionLevelData data) {
            attachPersistence(level, data);
        }
        return level;
    }

    /**
     * 处理控制器"进入/退出"按钮：依据玩家<b>当前所处维度类别</b>决定动作（服务端权威）。
     * <ul>
     *   <li>模组维度<b>之外</b>：记录 outside=当前位置，进入自己的个人维度；</li>
     *   <li><b>自己的</b>个人维度：记录 own=当前位置，返回 outside（无则回主世界出生点）；</li>
     *   <li><b>公共</b>维度：返回 publicReturn 记忆点（退出公共维度）；</li>
     *   <li><b>他人的</b>个人维度：返回 visitReturn 记忆点（退出他人维度回到访问前的位置）。</li>
     * </ul>
     */
    public static void handleTravel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> current = player.level().dimension();
        ResourceKey<Level> ownKey = Platform.levelKeyFor(player.getUUID());

        if (!Platform.isModDimension(current)) {
            // 在模组维度之外：进入自己的个人维度
            updateMemory(player, mem -> mem.withOutside(TravelPoint.of(player)));
            teleportToOwn(player);
        } else if (current.equals(ownKey)) {
            // 在自己的个人维度：退出
            updateMemory(player, mem -> mem.withOwn(TravelPoint.of(player)));
            teleportToOutside(player, server);
        } else if (Platform.isPublicDimension(current)) {
            // 在公共维度：退出到 publicReturn
            teleportOutOfPublic(player);
        } else {
            // 在他人的个人维度：退出到 visitReturn
            teleportOutOfVisit(player, server);
        }
    }

    /**
     * 访客自助访问他人个人维度（访问列表点击触发，服务端权威）。
     *
     * <p>流程：校验目标维度存在 -&gt; 服务端复检 {@link Permission#ENTER} 权限（防客户端列表过期/伪造）
     * -&gt; 若当前不在任何个人维度则记录 outside 返回点 -&gt; 传送到目标维度原点上方安全落点。
     * 主人无需在线，权限来自全局 {@link FriendService} 配置。
     *
     * @return 成功传送返回 true；维度不存在或无权限返回 false
     */
    public static boolean handleVisit(ServerPlayer actor, UUID targetOwner) {
        MinecraftServer server = actor.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel target = server.getLevel(Platform.levelKeyFor(targetOwner));
        if (target == null) {
            actor.displayClientMessage(
                Component.translatable("personaldimension.message.visit_unavailable"), true);
            return false;
        }
        if (!FriendService.hasPermission(server, targetOwner, actor.getUUID(), Permission.ENTER)) {
            actor.displayClientMessage(
                Component.translatable("personaldimension.message.visit_denied"), true);
            return false;
        }
        // 记录访问前的位置作为退出返回点（不管当前在哪个维度）
        updateMemory(actor, mem -> mem.withVisitReturn(TravelPoint.of(actor)));
        FriendVisitorTeleporterService.VisitorTarget visitorTarget = FriendVisitorTeleporterService.getVisitorTarget(server, targetOwner, actor.getUUID());
        Vec3 spawnPos = visitorTarget != null ? visitorTarget.pos() : null;
        if (spawnPos != null) {
            // 校验传送器方块是否仍存在（数据可能因方块被破坏但未及时清理而残留）
            BlockPos bePos = BlockPos.containing(spawnPos.x, spawnPos.y - 1, spawnPos.z);
            if (!(target.getBlockState(bePos).getBlock() instanceof
                  com.ruadou.personaldimension.block.FriendVisitorTeleporterBlock)) {
                // 传送器已被破坏：清理残留数据后回退到原点
                FriendVisitorTeleporterData.get(server)
                    .remove(targetOwner, target.dimension(), bePos);
                spawnPos = null;
            }
        }
        // 标记控制器传送，维度守卫据此放行（不拦截 / 不重定向）
        markControllerVisit(actor.getUUID());
        if (spawnPos != null) {
            actor.teleportTo(target, spawnPos.x, spawnPos.y, spawnPos.z, actor.getYRot(), actor.getXRot());
        } else {
            // 兜底：传送到原点上方安全位置（MOTION_BLOCKING 返回 y 已是脚部空气位置）
            int y = Math.max(target.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0);
            actor.teleportTo(target, 0.5D, y, 0.5D, actor.getYRot(), actor.getXRot());
        }
        actor.sendSystemMessage(Component.translatable(
            "personaldimension.message.visiting", FriendService.resolveName(server, targetOwner)));
        return true;
    }

    /**
     * 主人权限配置变更后，驱逐其个人维度内所有已失去 {@link Permission#ENTER} 权限的访客。
     *
     * <p>关闭某组的进入权限、改分组、删组、移除好友或换默认组，都可能使正身处该维度的访客
     * 失去访问资格，但这些改动本身不会把人踢出。故在配置变更后调用本方法复检在场访客：
     * 维度未创建则静默返回；主人本人与仍持 ENTER 的访客保留；其余访客按访客"退出"逻辑回到
     * 进入前记录的 visitReturn 记忆点（缺失则回主世界出生点），并收到访问权限被撤销提示。
     * 复制玩家列表以避免传送引发的并发修改。权限经全局 {@link FriendService} 解析，服务端权威。
     */
    public static void evictUnauthorizedVisitors(MinecraftServer server, UUID owner) {
        ServerLevel level = server.getLevel(Platform.levelKeyFor(owner));
        if (level == null) {
            return; // 维度未创建
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.getUUID().equals(owner)) {
                continue; // 主人始终保留
            }
            if (FriendService.hasPermission(server, owner, p.getUUID(), Permission.ENTER)) {
                continue; // 仍有进入权限
            }
            teleportOutOfVisit(p, server);
            p.displayClientMessage(
                Component.translatable("personaldimension.message.access_revoked"), true);
        }
    }

    /**
     * 校验玩家<b>当前所处维度</b>的访问资格，无 {@link Permission#ENTER} 则送出（服务端权威）。
     *
     * <p>登录与死亡重生都不触发维度切换事件——vanilla 直接把玩家放到存档记录/重生点所在的维度，
     * 因此进入守卫与撤权驱逐都覆盖不到"离线或死亡期间被撤权"的访客，须在这些"非 travel 放置"时机
     * 补检。主人在自己的维度、非个人维度一律放行；被驱逐者回进入前的 visitReturn 记忆点（缺失则回主世界
     * 出生点）并收到撤权提示。
     */
    public static void evictIfUnauthorized(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Optional<UUID> owner = Platform.ownerOf(player.level().dimension());
        if (owner.isEmpty() || owner.get().equals(player.getUUID())) {
            return; // 非个人维度，或玩家在自己的维度
        }
        if (FriendService.hasPermission(server, owner.get(), player.getUUID(), Permission.ENTER)) {
            return; // 仍有进入权限
        }
        teleportOutOfVisit(player, server);
        player.displayClientMessage(
            Component.translatable("personaldimension.message.access_revoked"), true);
    }

    /**
     * 删除玩家自己的个人维度（控制器"删除维度"按钮，经 5 秒确认后触发）。
     *
     * <p>卸载前先清空维度内玩家：拥有者按"退出"逻辑回其个人维度外记忆点，其余在场玩家回
     * 主世界出生点并提示宿主已删除；随后交由 {@link Platform#removeLevel}
     * 卸载运行时维度、从注册表移除 stem、删磁盘目录、通知客户端；最后清除拥有者的 own 记忆点。
     * 维度以发送者 UUID 定位，玩家只能删除自己的维度。
     *
     * @return 维度存在并成功删除返回 true；未创建返回 false
     */
    public static boolean deletePersonalDimension(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ResourceKey<Level> ownKey = Platform.levelKeyFor(player.getUUID());
        ServerLevel level = server.getLevel(ownKey);
        if (level == null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.not_created"), true);
            return false;
        }
        // 卸载前必须清空维度内玩家，避免残留引用指向已卸载维度
        evacuate(level, server, player);
        // 清理该维度下所有玩家的传送点
        TeleporterData.get(server).removeByDimension(ownKey);
        // 清理该维度下的好友访问传送器
        FriendVisitorTeleporterData.get(server).removeByDimension(ownKey);
        if (!Platform.removeLevel(server, ownKey)) {
            return false;
        }
        // 记忆点里的 own 指向已删除维度，一并清除
        updateMemory(player, TravelMemory::withoutOwn);
        player.displayClientMessage(
            Component.translatable("personaldimension.message.deleted"), true);
        PersonalDimension.LOGGER.info("玩家 [{}] 删除个人维度: {}",
            player.getGameProfile().getName(), ownKey.location());
        return true;
    }

    // -----------------------------------------------------------------
    // 公共维度：全局唯一，所有人共享，通过控制器创建
    // -----------------------------------------------------------------

    /**
     * 创建公共维度（全局唯一，通过控制器触发）。
     *
     * <p>已存在则提示且不创建；否则记录返回点、建维、按预设铺落脚结构、传送创建者。
     * 前置校验由 {@code CreateDimensionPacket} 完成。
     *
     * @return 是否成功创建（已存在或服务器缺失返回 false）
     */
    public static boolean createPublicDimension(ServerPlayer player, DimensionPreset preset, DimensionTemplate template) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("只有 OP 可以建立公共維度。"));
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ResourceKey<Level> levelKey = Platform.publicLevelKey();
        if (server.getLevel(levelKey) != null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.public_already_exists"), true);
            return false;
        }
        if (!Platform.isPersonalDimension(player.level().dimension())
            && !Platform.isPublicDimension(player.level().dimension())) {
            updateMemory(player, mem -> mem.withOutside(TravelPoint.of(player)));
        }
        ServerLevel level = getOrCreatePublicLevel(server, () -> Platform.createStem(server, preset, template));
        if (level == null) {
            return false;
        }
        PersonalDimension.LOGGER.info("玩家 [{}] 创建公共维度 (预设 {})",
            player.getGameProfile().getName(), preset);
        BlockPos spawn = preset.requiresTemplate() && template != null
            ? new BlockPos(0, template.spawnY(), 0)
            : buildSpawn(level, preset);
        player.teleportTo(level,
            spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(
            Component.translatable("personaldimension.message.public_entered"));
        return true;
    }

    /**
     * 进入公共维度（控制器"进入公共维度"按钮触发）。
     * 维度未创建则提示；已创建则记录 publicReturn 返回点（仅当玩家当前不在公共维度内）并传送。
     * 不使用 outside，避免覆盖个人维度的外部返回点。
     */
    public static void teleportToPublic(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(Platform.publicLevelKey());
        if (level == null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.public_not_created"), true);
            return;
        }
        if (!Platform.isPublicDimension(player.level().dimension())) {
            updateMemory(player, mem -> mem.withPublicReturn(TravelPoint.of(player)));
        }
        // 强制加载原点区块，确保 heightmap 查询准确（区块未加载时返回 minBuildHeight 会导致传送位置错误）
        level.getChunk(0, 0);
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0);
        player.teleportTo(level, 0.5D, y, 0.5D, player.getYRot(), player.getXRot());
        player.sendSystemMessage(
            Component.translatable("personaldimension.message.public_entered"));
    }

    /**
     * 退出公共维度（控制器"退出公共维度"按钮触发）。
     * 返回进入前记录的 publicReturn 记忆点；无记忆点或目标维度已不存在时兜底回主世界出生点。
     */
    public static void teleportOutOfPublic(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!Platform.isPublicDimension(player.level().dimension())) {
            return; // 不在公共维度内，忽略
        }
        TravelMemory mem = Platform.getTravelMemory(player);
        Optional<TravelPoint> ret = mem.publicReturn();
        if (ret.isPresent()) {
            TravelPoint p = ret.get();
            ServerLevel dst = server.getLevel(p.dimension());
            if (dst != null) {
                player.teleportTo(dst, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
                return;
            }
        }
        // 无记忆点兜底：回主世界出生点
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld,
            spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
    }

    /**
     * 删除公共维度（仅 OP 可执行）。
     *
     * <p>清空维度内所有玩家（回主世界出生点）后卸载维度、删除存档目录。
     *
     * @return 成功删除返回 true；未创建或无权限返回 false
     */
    public static boolean deletePublicDimension(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.public_delete_no_permission"), true);
            return false;
        }
        ResourceKey<Level> key = Platform.publicLevelKey();
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.public_not_created"), true);
            return false;
        }
        // 清空维度内所有玩家
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (ServerPlayer p : List.copyOf(level.players())) {
            p.teleportTo(overworld,
                spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                p.getYRot(), p.getXRot());
            p.sendSystemMessage(Component.translatable("personaldimension.message.public_deleted"));
            updateMemory(p, mem -> mem.withoutPublicReturn());
        }
        TeleporterData.get(server).removeByDimension(key);
        FriendVisitorTeleporterData.get(server).removeByDimension(key);
        if (!Platform.removeLevel(server, key)) {
            return false;
        }
        player.displayClientMessage(
            Component.translatable("personaldimension.message.public_deleted"), true);
        PersonalDimension.LOGGER.info("玩家 [{}] 删除公共维度", player.getGameProfile().getName());
        return true;
    }

    /** 获取或创建公共维度并完成持久化绑定。 */
    private static ServerLevel getOrCreatePublicLevel(MinecraftServer server, Supplier<LevelStem> stemFactory) {
        ServerLevel level = Platform.getOrCreateLevel(
            server, Platform.publicLevelKey(),
            stemFactory,
            worldData -> new PersonalDimensionLevelData(worldData, worldData.overworldData()),
            true);
        if (level.getLevelData() instanceof PersonalDimensionLevelData data) {
            attachPersistence(level, data);
        }
        return level;
    }

    /** 获取公共维度的等级数据（未创建返回 null）。 */
    public static PersonalDimensionLevelData getPublicLevelData(MinecraftServer server) {
        ServerLevel level = server.getLevel(Platform.publicLevelKey());
        if (level != null && level.getLevelData() instanceof PersonalDimensionLevelData data) {
            return data;
        }
        return null;
    }

    /** 获取公共维度的 ServerLevel（未创建返回 null）。 */
    public static ServerLevel getPublicLevel(MinecraftServer server) {
        return server.getLevel(Platform.publicLevelKey());
    }

    /** 公共维度是否存在。 */
    public static boolean isPublicDimensionCreated(MinecraftServer server) {
        return server.getLevel(Platform.publicLevelKey()) != null;
    }

    /**
     * 传出目标维度内的所有玩家：拥有者按"退出"逻辑回个人维度外记忆点，
     * 其余玩家回主世界出生点并提示宿主已删除。复制玩家列表以避免传送时的并发修改。
     */
    private static void evacuate(ServerLevel level, MinecraftServer server, ServerPlayer owner) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.getUUID().equals(owner.getUUID())) {
                teleportToOutside(p, server);
            } else {
                p.teleportTo(overworld,
                    spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    p.getYRot(), p.getXRot());
                p.sendSystemMessage(Component.translatable("personaldimension.message.host_deleted"));
            }
        }
    }

    /**
     * 传送到玩家自己的个人维度。
     *
     * <p>维度未创建则动作栏提示且<b>不自动建维</b>（创建须经控制器"创建维度"界面消耗生成器）；
     * 已创建则回 own 记忆点，记忆点缺失时兜底铺虚空草平台并落到 (0,0,0)。
     */
    private static void teleportToOwn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> ownKey = Platform.levelKeyFor(player.getUUID());
        // 未创建：提示并中止，进入不再触发建维
        if (server.getLevel(ownKey) == null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.not_created"), true);
            return;
        }
        ServerLevel own = getOrCreateOwnLevel(player);
        if (own == null) {
            return;
        }
        TravelMemory mem = Platform.getTravelMemory(player);
        Optional<TravelPoint> ownPoint = mem.own();
        if (ownPoint.isPresent()) {
            TravelPoint p = ownPoint.get();
            player.teleportTo(own, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        } else {
            // Existing dimension: never regenerate the platform on a return visit.
            own.getChunk(0, 0);
            int safeY = Math.max(own.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0);
            BlockPos spawn = new BlockPos(0, safeY, 0);
            player.teleportTo(own,
                spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        }
        player.sendSystemMessage(
            Component.translatable("personaldimension.message.entered", own.dimension().location().toString()));
        sendDimensionInfoToPlayer(player);
    }

    /** 传送回个人维度之外的记忆点；无记忆点或目标维度已不存在时兜底回主世界出生点。 */
    private static void teleportToOutside(ServerPlayer player, MinecraftServer server) {
        TravelMemory mem = Platform.getTravelMemory(player);
        Optional<TravelPoint> outside = mem.outside();
        if (outside.isPresent()) {
            TravelPoint p = outside.get();
            ServerLevel dst = server.getLevel(p.dimension());
            if (dst != null) {
                player.teleportTo(dst, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
                return;
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld,
            spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
    }

    /**
     * 退出他人个人维度：返回访问前记录的 visitReturn 记忆点；
     * 无记忆点或目标维度已不存在时兜底回主世界出生点。退出后清除 visitReturn 避免残留。
     */
    private static void teleportOutOfVisit(ServerPlayer player, MinecraftServer server) {
        TravelMemory mem = Platform.getTravelMemory(player);
        Optional<TravelPoint> ret = mem.visitReturn();
        if (ret.isPresent()) {
            TravelPoint p = ret.get();
            ServerLevel dst = server.getLevel(p.dimension());
            if (dst != null) {
                player.teleportTo(dst, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
                updateMemory(player, TravelMemory::withoutVisitReturn);
                return;
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld,
            spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.translatable("personaldimension.message.exited"));
        updateMemory(player, TravelMemory::withoutVisitReturn);
    }

    /** 以不可变方式更新记忆点，由平台实现负责持久化标脏。 */
    private static void updateMemory(ServerPlayer player, UnaryOperator<TravelMemory> op) {
        Platform.updateTravelMemory(player, op);
    }


    /**
     * 定位玩家自己的个人维度 {@link ServerLevel}（未创建或服务器缺失返回 null）。
     * 供网络层读写按维度存储的规则数据（如生物生成规则），避免各处重复拼装维度查找逻辑。
     */
    public static ServerLevel getOwnLevel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        return server.getLevel(Platform.levelKeyFor(player.getUUID()));
    }

    /**
     * 定位玩家<b>当前可操作</b>的维度时间数据（未创建则返回 null）。
     *
     * <p>上下文感知：玩家身处公共维度时返回公共维度数据，否则返回自己个人维度的数据。
     * 供网络层查询/写入维度时间/天气，使所有设置包自动适配公共维度。
     */
    public static PersonalDimensionLevelData getPersonalLevelData(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        // 身处公共维度：返回公共维度数据
        if (Platform.isPublicDimension(player.level().dimension())) {
            return getPublicLevelData(server);
        }
        // 否则：返回自己个人维度的数据
        ServerLevel personal = server.getLevel(Platform.levelKeyFor(player.getUUID()));
        if (personal != null && personal.getLevelData() instanceof PersonalDimensionLevelData data) {
            return data;
        }
        return null;
    }

    /**
     * 世界重载后接管所有个人维度。
     *
     * <p>重载时 vanilla 会以 {@code DerivedLevelData}+{@code tickTime=false} 重建个人维度，
     * 使时间跟随主世界、时间控制全部失效。此方法在服务器启动完成、玩家登录前调用，
     * 遍历所有维度，把命名空间与路径匹配（personaldimension:personal_*）且时间数据不正确的维度，
     * 替换为 {@link PersonalDimensionLevelData} 并从存档恢复 dayTime/昼夜开关。
     */
    public static void adoptReloadedLevels(MinecraftServer server) {
        WorldData worldData = server.getWorldData();
        for (ServerLevel level : server.getAllLevels()) {
            if (!Platform.isModDimension(level.dimension())) {
                continue;
            }
            if (level.getLevelData() instanceof PersonalDimensionLevelData) {
                continue; // 已是正确类型（正常不会发生，防御性跳过）
            }
            PersonalDimensionLevelData data =
                new PersonalDimensionLevelData(worldData, worldData.overworldData());
            Platform.rebindLevelData(level, data);
            attachPersistence(level, data);
            PersonalDimension.LOGGER.info("已接管重载的个人维度: {}", level.dimension().location());
        }
    }

    /**
     * 为个人维度绑定持久化载体（幂等）。
     * 存档已存在则将其时间状态恢复进 {@code data}；否则用当前值创建并写入存档。
     */
    private static void attachPersistence(ServerLevel level, PersonalDimensionLevelData data) {
        if (data.hasPersistence()) {
            return;
        }
        DimensionDataStorage storage = level.getDataStorage();
        PersonalDimensionData saved = storage.get(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        if (saved != null) {
            // 已有存档：把持久化的时刻/昼夜开关、天气计时与月相设置恢复到运行时数据
            data.applyState(saved.getDayTime(), saved.isDaylightCycle(), saved.isFollowOverworldTime());
            data.applyWeatherState(saved.isRaining(), saved.getRainTime(),
                saved.isThundering(), saved.getThunderTime(), saved.getClearWeatherTime(),
                saved.isWeatherCycle(), saved.isFollowOverworldWeather());
            data.applyMoonPhaseState(saved.getMoonPhaseOverride(), saved.isMoonPhaseCycle(),
                saved.isFollowOverworldMoonPhase());
        } else {
            // 首次创建：以当前运行时值播种存档（含月相默认值：跟随时间自然变化）
            saved = new PersonalDimensionData();
            saved.updateTime(data.getDayTime(), data.isDaylightCycle(), data.isFollowOverworldTime());
            saved.updateWeather(data.isRaining(), data.getRainTime(),
                data.isThundering(), data.getThunderTime(), data.getClearWeatherTime(),
                data.isWeatherCycle(), data.isFollowOverworldWeather());
            saved.updateMoonPhase(data.getMoonPhaseOverride(), data.isMoonPhaseCycle(),
                data.isFollowOverworldMoonPhase());
            storage.set(PersonalDimensionData.ID, saved);
        }
        data.setPersistence(saved);
    }

    /**
     * 向玩家发送其<b>当前可操作维度</b>的信息包，供客户端渲染月相等状态使用。
     * 身处公共维度时发送公共维度信息，否则发送自己个人维度的信息。
     */
    public static void sendDimensionInfoToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // 上下文感知：公共维度优先
        ResourceKey<Level> key;
        if (Platform.isPublicDimension(player.level().dimension())) {
            key = Platform.publicLevelKey();
        } else {
            key = Platform.levelKeyFor(player.getUUID());
        }
        ServerLevel level = server.getLevel(key);
        if (level == null || !(level.getLevelData() instanceof PersonalDimensionLevelData data)) {
            return;
        }
        boolean daylight = data.isDaylightCycle();
        boolean weatherCycle = data.isWeatherCycle();
        boolean followTime = data.isFollowOverworldTime();
        boolean followWeather = data.isFollowOverworldWeather();
        int moonPhaseOverride = data.getMoonPhaseOverride();
        boolean moonPhaseCycle = data.isMoonPhaseCycle();
        boolean followMoonPhase = data.isFollowOverworldMoonPhase();
        // 当前月相：跟随主世界时读主世界（Mixin 不会注入主世界，走原版计算），
        // 否则取 effective（已包含 override 锁定与按 dayTime 循环两种情况）
        int moonPhase = followMoonPhase
            ? server.overworld().getMoonPhase()
            : Math.max(0, data.getEffectiveMoonPhase());
        DimensionInfoPacket packet = new DimensionInfoPacket(
            true,
            data.isRaining(), data.isThundering(),
            data.getRainTime(), data.getThunderTime(), data.getClearWeatherTime(),
            level.getDayTime(), daylight, weatherCycle,
            followTime, followWeather,
            moonPhaseOverride, moonPhaseCycle, followMoonPhase, moonPhase);
        Platform.sendToPlayer(player, packet);
    }

    // ---------------------------------------------------------------------
    // 落脚地形构建：按预设在个人维度原点附近铺设结构，返回玩家站立坐标
    // ---------------------------------------------------------------------

    /** 依预设分派落脚结构的构建，返回玩家应站立的坐标。 */
    private static BlockPos buildSpawn(ServerLevel level, DimensionPreset preset) {
        return switch (preset) {
            case SKY_ISLAND -> buildSkyIsland(level);
            default -> buildVoidPlatform(level);
        };
    }

    /**
     * Personal Shelter 初始平台：依等級 0 的尺寸建立 48×48 草地。
     * 表面 y=-1，下面五層泥土；只在建立維度時生成。
     */
    private static BlockPos buildVoidPlatform(ServerLevel level) {
        int half = com.ruadou.personaldimension.shelter.ShelterUpgradeRules.size(0) / 2;
        int minChunk = (-half) >> 4;
        int maxChunk = (half - 1) >> 4;
        for (int chunkX = minChunk; chunkX <= maxChunk; chunkX++) {
            for (int chunkZ = minChunk; chunkZ <= maxChunk; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                for (int y = -6; y < -1; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState());
                }
                level.setBlockAndUpdate(new BlockPos(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        return new BlockPos(0, 0, 0);
    }

    /**
     * 空岛预设：以 (0,-1) 为圆心铺半径 3 的圆形草地岛（下方两层泥土），
     * 岛上种一棵简易橡树、放一个起始物资箱，玩家站在岛面中心 (0,0,0)。
     * @return 玩家站立坐标 (0,0,0)
     */
    private static BlockPos buildSkyIsland(ServerLevel level) {
        final int radius = 3;
        final int surfaceY = -1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue; // 圆形裁剪，超出半径的角落留空
                }
                BlockPos pos = new BlockPos(dx, surfaceY, dz);
                level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(pos.below(2), Blocks.DIRT.defaultBlockState());
            }
        }
        plantSimpleTree(level, new BlockPos(-2, surfaceY + 1, -2));
        placeStarterChest(level, new BlockPos(2, surfaceY + 1, 2));
        return new BlockPos(0, surfaceY + 1, 0);
    }

    /** 在底座位置种一棵简易橡树：4 格原木 + 顶部 3x3x3 树叶（仅覆盖空气，不破坏原木/已有方块）。 */
    private static void plantSimpleTree(ServerLevel level, BlockPos base) {
        final int trunkHeight = 4;
        for (int i = 0; i < trunkHeight; i++) {
            level.setBlockAndUpdate(base.above(i), Blocks.OAK_LOG.defaultBlockState());
        }
        BlockPos crown = base.above(trunkHeight - 1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos leaf = crown.offset(dx, dy, dz);
                    if (level.getBlockState(leaf).isAir()) {
                        level.setBlockAndUpdate(leaf, Blocks.OAK_LEAVES.defaultBlockState());
                    }
                }
            }
        }
    }

    /** 放置起始物资箱，塞入一份足够开局的基础资源（冰、熔岩、树苗、泥土、骨粉）。 */
    private static void placeStarterChest(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.ICE, 2));
            chest.setItem(1, new ItemStack(Items.LAVA_BUCKET));
            chest.setItem(2, new ItemStack(Items.OAK_SAPLING, 2));
            chest.setItem(3, new ItemStack(Items.DIRT, 16));
            chest.setItem(4, new ItemStack(Items.BONE_MEAL, 16));
        }
    }
}
