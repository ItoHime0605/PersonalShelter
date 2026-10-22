package com.ruadou.personaldimension.block;

import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.block.entity.LightningInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.MoonPhaseInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.TimeInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.WeatherInterfaceBlockEntity;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 维度接口（时间 / 天气 / 闪电）领域服务（服务端）。
 *
 * <p>作为方块层、网络层与个人维度世界数据之间的中间层，集中两类动作：
 * <ol>
 *   <li><b>应用</b>（{@code applyXxx}）：红石上升沿触发，读取方块实体所存设置写入所在维度的
 *       {@link PersonalDimensionLevelData}；仅当所在维度确为个人维度时生效，其它维度静默忽略。</li>
 *   <li><b>配置</b>（{@code configureXxx}）：玩家在界面中选择后由网络包触发，复检维度归属、方块存在与
 *       交互距离后写入方块实体并同步客户端，权限完全由服务端裁决，无越权风险。</li>
 * </ol>
 *
 * <p><b>线程约束</b>：全部方法仅在服务端主线程调用（由方块红石回调与网络包处理切主线程后进入）。
 */
public final class DimensionInterfaceService {

    private DimensionInterfaceService() {}

    /**
     * 方块交互距离校验的额外余量（方块），与原版方块交互一致；距离本身取玩家
     * {@code BLOCK_INTERACTION_RANGE} 属性，自动跟随整合包加长交互的道具，仍能拦截远程伪造包。
     */
    private static final double INTERACT_PADDING = 1.0D;

    // ——— 红石激活：应用已配置设置到所在维度 ———

    /** 红石上升沿：把月相接口方块所存目标月相应用到其所在个人维度（非个人维度静默忽略）。 */
    public static void applyMoonPhase(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MoonPhaseInterfaceBlockEntity be)) {
            return;
        }
        if (level.getLevelData() instanceof PersonalDimensionLevelData data) {
            data.setMoonPhase(be.getMoonPhase());
        }
    }

    /** 红石上升沿：把天气接口方块所存目标天气应用到其所在个人维度（非个人维度静默忽略）。 */
    public static void applyWeather(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WeatherInterfaceBlockEntity be)) {
            return;
        }
        if (level.getLevelData() instanceof PersonalDimensionLevelData data) {
            data.setWeatherState(be.getWeatherState());
        }
    }

    /** 红石上升沿：把时间接口方块所存目标时刻应用到其所在个人维度，并向维度内玩家广播（非个人维度静默忽略）。 */
    public static void applyTime(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TimeInterfaceBlockEntity be)) {
            return;
        }
        if (level.getLevelData() instanceof PersonalDimensionLevelData data) {
            data.forceSetDayTime(be.getDayTime());
            MinecraftServer server = level.getServer();
            if (server != null) {
                server.forceTimeSynchronization();
            }
        }
    }

    /**
     * 红石上升沿：在方块所在 chunk 内尝试生成一次闪电，完整复用原版 {@code ServerLevel.tickChunk} 的判定链：
     * chunk 内随机 XZ → MOTION_BLOCKING 最高方块 → {@code findLightningTargetAround}（128 格内避雷针优先
     * + 3×3×h 范围暴露天空生物吸引）→ {@code isRainingAt} 校验（能见天且降雨非雪，生物群系支持）
     * → 生成 {@link LightningBolt}。
     *
     * <p>与原版的唯一差异：把每 tick 1/100000 概率的随机尝试改成"红石上升沿确定性地催生一次"，
     * 自然条件校验全部保留，晴天 / 暴雪 / 不能见天时按按钮不劈（与原版自然闪电一致），避雷针吸引也由
     * 原版方法自动处理。非个人维度静默忽略，与 {@link #applyWeather}/{@link #applyTime} 一致。
     *
     * <p><b>平台抽象</b>：{@code findLightningTargetAround} 原为 protected，
     * NeoForge 通过 AT 暴露为 public 直接调用，Fabric 通过 Mixin @Invoker 暴露，
     * 由 {@link Platform#findLightningTargetAround} 统一封装，避免反射。
     *
     * <p><b>跨模组兼容</b>：方块实体存储"是否标记为自然闪电"选项（默认自然）。设为自然时，给生成的闪电
     * 打上 {@code ae2lt.natural_weather_lightning} NBT 标签——AE2LT 等闪电科技模组通过该标签区分
     * "自然 / 人造闪电"（其 mixin 只拦截 {@code tickChunk} 里诞生的闪电打标签，红石触发的不经过）。
     * 打标签由 {@link Platform#markNaturalLightning} 封装：NeoForge 写 {@code getPersistentData()}，
     * Fabric 端暂未实现（无害冗余）。设为"人造"时不打标签，行为与三叉戟引雷等人工闪电一致。
     */
    public static void applyLightning(ServerLevel level, BlockPos pos) {
        if (!(level.getLevelData() instanceof PersonalDimensionLevelData)) {
            return;
        }
        // 读取方块实体的"自然 / 人造"选项；方块实体缺失时回退为自然（true）
        boolean natural = true;
        if (level.getBlockEntity(pos) instanceof LightningInterfaceBlockEntity be) {
            natural = be.isNatural();
        }

        // 在方块所在 chunk 内随机选 XZ，取 MOTION_BLOCKING 高度图最高方块作为候选落点
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int x = (chunkX << 4) + level.random.nextInt(16);
        int z = (chunkZ << 4) + level.random.nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos target = new BlockPos(x, y, z);

        // 复用原版 findLightningTargetAround：128 格内避雷针优先，否则 3×3×h 范围暴露天空生物吸引
        target = Platform.findLightningTargetAround(level, target);

        // 自然闪电条件：必须能见天且降雨（非雪，且生物群系支持降雨）
        if (!level.isRainingAt(target)) {
            return;
        }

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(target));
            bolt.setVisualOnly(false);
            if (natural) {
                // 兼容 AE2LT 等模组：标记为自然天气闪电，使收集器 / 配方按自然闪电处理
                Platform.markNaturalLightning(bolt);
            }
            level.addFreshEntity(bolt);
        }
    }


    // ——— 界面配置：写入方块实体并同步客户端 ———

    /** 玩家在界面选择目标天气（0晴/1雨/2雷雨），复检后写入方块实体并同步。 */
    public static void configureWeather(ServerPlayer player, BlockPos pos, int weatherState) {
        ServerLevel level = player.serverLevel();
        if (!verify(player, level, pos, Platform.weatherInterfaceBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof WeatherInterfaceBlockEntity be) {
            be.setWeatherState(Mth.clamp(weatherState, 0, 2));
            sync(level, pos);
        }
    }

    /** 玩家在界面选择目标时刻，归一化到 0..23999 后复检写入方块实体并同步。 */
    public static void configureTime(ServerPlayer player, BlockPos pos, long dayTime) {
        ServerLevel level = player.serverLevel();
        if (!verify(player, level, pos, Platform.timeInterfaceBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TimeInterfaceBlockEntity be) {
            be.setDayTime(Math.floorMod(dayTime, 24000L));
            sync(level, pos);
        }
    }

    /** 玩家在界面选择自然 / 人造闪电，复检后写入方块实体并同步。 */
    public static void configureLightning(ServerPlayer player, BlockPos pos, boolean natural) {
        ServerLevel level = player.serverLevel();
        if (!verify(player, level, pos, Platform.lightningInterfaceBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof LightningInterfaceBlockEntity be) {
            be.setNatural(natural);
            sync(level, pos);
        }
    }

    /** 玩家在界面选择目标月相（0..7），复检后写入方块实体并同步。 */
    public static void configureMoonPhase(ServerPlayer player, BlockPos pos, int moonPhase) {
        ServerLevel level = player.serverLevel();
        if (!verify(player, level, pos, Platform.moonPhaseInterfaceBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof MoonPhaseInterfaceBlockEntity be) {
            be.setMoonPhase(Mth.clamp(moonPhase, 0, 7));
            sync(level, pos);
        }
    }

    /**
     * 配置前置校验：当前维度是玩家本人的个人维度 -&gt; 目标处确为期望的接口方块且在交互距离内。
     * 任一不满足即拒绝（第一步给出提示，后两步仅静默拒绝，因多为方块已被破坏或远程伪造）。
     */
    private static boolean verify(ServerPlayer player, ServerLevel level, BlockPos pos, Block expected) {
        Optional<UUID> owner = Platform.ownerOf(level.dimension());
        if (owner.isEmpty() || !owner.get().equals(player.getUUID())) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.interface_wrong_dim"), true);
            return false;
        }
        return level.getBlockState(pos).is(expected)
            && player.canInteractWithBlock(pos, INTERACT_PADDING);
    }

    /** 触发方块实体的客户端同步（经 getUpdatePacket 推送最新 NBT 供界面回显）。 */
    private static void sync(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }
}
