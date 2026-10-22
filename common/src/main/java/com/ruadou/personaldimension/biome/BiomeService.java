package com.ruadou.personaldimension.biome;

import com.ruadou.personaldimension.platform.Platform;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/**
 * 群系收录 / 改造领域服务（服务端）。
 *
 * <p>作为方块层、网络层与底层区块群系存储之间的中间层，集中三件事：
 * <ol>
 *   <li><b>读取</b>：遍历区块内每个 section 的 4×4×4 群系网格，汇总为去重的群系 id 集合；</li>
 *   <li><b>覆盖</b>：把选中群系写入整块的全部群系网格，并标脏落盘；</li>
 *   <li><b>同步</b>：用官方专为群系变更设计的 {@link ClientboundChunksBiomesPacket} 只回传群系数据，
 *       比重发整块轻得多。</li>
 * </ol>
 *
 * <p>群系按 4×4×4 方块（quart）为单位存储：一个 section（16³ 方块）含 4×4×4=64 个群系格。
 * {@code LevelChunkSection#getBiomes()} 声明返回只读接口 {@link PalettedContainerRO}，
 * 但对已加载区块其底层恒为可写的 {@link PalettedContainer}，据此强转后写入即可。
 *
 * <p><b>线程约束</b>：全部方法仅在服务端主线程调用（由方块交互与网络包处理切主线程后进入）。
 */
public final class BiomeService {

    private BiomeService() {}

    /**
     * 方块交互距离校验的额外余量（方块），与原版方块交互校验取值一致，避免站在边缘时误判。
     */
    private static final double INTERACT_PADDING = 1.0D;

    // ——— 收录 ———

    /**
     * 读取收录器方块所在区块内出现的全部群系，供客户端收录界面展示待选项（服务端权威）。
     *
     * <p>先校验目标处确为收录器方块且在玩家交互距离内，防止请求包被伪造成远程扫描任意区块；
     * 不满足返回空列表。收录器不限维度，故此处无维度归属校验。
     */
    public static List<ResourceLocation> chunkBiomesAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Platform.biomeCollectorBlock())
            || !player.canInteractWithBlock(pos, INTERACT_PADDING)) {
            return List.of();
        }
        return List.copyOf(readChunkBiomes(level, new ChunkPos(pos)));
    }

    /**
     * 收录玩家在界面中勾选的群系子集到个人数据（不限维度，服务端权威）。
     *
     * <p>依次校验：目标处确为收录器方块且在玩家交互距离内 -> 逐项过滤掉未出现在该区块的群系（防伪造）。
     * 过滤后为空则提示无可收录；否则报告本次新增数与累计总数。
     */
    public static void collectSelected(ServerPlayer player, ServerLevel level, BlockPos pos,
                                       Collection<ResourceLocation> selected) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!level.getBlockState(pos).is(Platform.biomeCollectorBlock())
            || !player.canInteractWithBlock(pos, INTERACT_PADDING)) {
            return;
        }
        Set<ResourceLocation> present = readChunkBiomes(level, new ChunkPos(pos));
        Set<ResourceLocation> toCollect = new LinkedHashSet<>();
        for (ResourceLocation id : selected) {
            if (present.contains(id)) {
                toCollect.add(id);
            }
        }
        if (toCollect.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.collect_empty"), true);
            return;
        }
        CollectedBiomeData data = CollectedBiomeData.get(server);
        int added = data.addBiomes(player.getUUID(), toCollect);
        int total = data.getBiomes(player.getUUID()).size();
        player.displayClientMessage(
            Component.translatable("personaldimension.message.collected", added, total), true);
    }

    /** 读取一个区块内全部 section 的群系，去重返回其注册 id 集合。 */
    private static Set<ResourceLocation> readChunkBiomes(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (LevelChunkSection section : chunk.getSections()) {
            PalettedContainerRO<Holder<Biome>> biomes = section.getBiomes();
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        biomes.get(x, y, z).unwrapKey()
                            .ifPresent(key -> result.add(key.location()));
                    }
                }
            }
        }
        return result;
    }

    // ——— 改造 ———

    /**
     * 把玩家已收录的某群系覆盖到改造器方块所在区块（仅限玩家自己的个人维度，服务端权威）。
     *
     * <p>依次校验：当前维度是玩家本人的个人维度 -> 目标位置确为改造器方块（防伪造/已被破坏）
     * -> 方块在交互距离内 -> 目标群系已被玩家收录 -> 群系 id 可解析。全部通过才覆盖并同步客户端。
     */
    public static void applyBiome(ServerPlayer player, BlockPos modifierPos, ResourceLocation biomeId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();

        // 1. 维度归属：只能改造自己的个人维度
        Optional<UUID> owner = Platform.ownerOf(level.dimension());
        if (owner.isEmpty() || !owner.get().equals(player.getUUID())) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.modifier_wrong_dim"), true);
            return;
        }
        // 2. 目标处确为改造器方块，且在玩家交互距离内（防越权/远程伪造）
        if (!level.getBlockState(modifierPos).is(Platform.biomeModifierBlock())
            || !player.canInteractWithBlock(modifierPos, INTERACT_PADDING)) {
            return;
        }
        // 3. 该群系必须已被本人收录
        if (!CollectedBiomeData.get(server).hasBiome(player.getUUID(), biomeId)) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.biome_not_collected"), true);
            return;
        }
        // 4. 解析群系 Holder
        Optional<Holder.Reference<Biome>> holder = level.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, biomeId));
        if (holder.isEmpty()) {
            return;
        }
        overwriteChunk(level, new ChunkPos(modifierPos), holder.get());
        player.displayClientMessage(
            Component.translatable("personaldimension.message.biome_modified", biomeId.toString()), true);
    }

    /**
     * 把整块的全部群系格覆盖为 {@code biome}，标脏落盘，并只回传群系数据给追踪该区块的玩家。
     * getBiomes() 声明为只读，但已加载区块底层恒为 {@link PalettedContainer}，强转后 {@code set} 写入。
     */
    private static void overwriteChunk(ServerLevel level, ChunkPos chunkPos, Holder<Biome> biome) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getBiomes() instanceof PalettedContainer<?> raw) {
                @SuppressWarnings("unchecked")
                PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) raw;
                for (int x = 0; x < 4; x++) {
                    for (int y = 0; y < 4; y++) {
                        for (int z = 0; z < 4; z++) {
                            biomes.set(x, y, z, biome);
                        }
                    }
                }
            }
        }
        chunk.setUnsaved(true);
        // 官方群系变更专用包：只传群系 palette，构造时从 section 读取最新群系，故须在写入之后发送
        ClientboundChunksBiomesPacket packet = ClientboundChunksBiomesPacket.forChunks(List.of(chunk));
        for (ServerPlayer tracking : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            tracking.connection.send(packet);
        }
    }
}
