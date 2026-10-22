package com.ruadou.personaldimension.network;

import java.util.Optional;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.DimensionPreset;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.dimension.template.DimensionTemplate;
import com.ruadou.personaldimension.menu.PersonalDimensionCreateMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;


/**
 * 提交创建个人维度请求（客户端 -> 服务端）。
 *
 * <p>仅在玩家正打开创建容器时受理（校验 containerMenu 类型，杜绝伪造/越权）。
 * 校验顺序：维度未存在 -> 生成器槽确有生成器 -> 自定义模板合法（层高在维度边界内、
 * 调色板方块均通过配置名单）-> <b>先消耗生成器</b>（防止随后传送关闭容器时
 * {@code removed()} 退还导致刷物品）-> 按预设建维、传送、关闭界面。
 *
 * <p>客户端上传预设 id，{@link DimensionPreset#CUSTOM} 时附带 {@link #template}；
 * 其余预设 {@code template} 为空。所有边界/名单校验均在服务端复核（系统边界，不信任客户端）。
 *
 * @param presetId 预设 id，见 {@link DimensionPreset#byId(int)}
 * @param template 自定义模板数据，仅 {@link DimensionPreset#CUSTOM} 携带
 */
public record CreateDimensionPacket(int presetId, Optional<DimensionTemplate> template) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateDimensionPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "create_dimension"));

    public static final StreamCodec<FriendlyByteBuf, CreateDimensionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreateDimensionPacket::presetId,
            ByteBufCodecs.optional(DimensionTemplate.STREAM_CODEC), CreateDimensionPacket::template,
            CreateDimensionPacket::new);

    /** 便捷构造：仅预设、无模板（虚空 / 空岛）。 */
    public CreateDimensionPacket(int presetId) {
        this(presetId, Optional.empty());
    }

    public void handle(ServerPlayer player) {
        // 必须处于创建界面：否则忽略，杜绝伪造包越权创建
        if (!(player.containerMenu instanceof PersonalDimensionCreateMenu menu)) {
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            player.sendSystemMessage(Component.literal("請先將游標上的物品放回背包。"));
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        boolean isPublic = menu.isPublic();
        if (isPublic && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("只有 OP 可以建立公共維度。"));
            return;
        }
        // 已存在：动作栏提示，不重复创建
        ResourceKey<Level> targetKey = isPublic
            ? Platform.publicLevelKey()
            : Platform.levelKeyFor(player.getUUID());
        if (server.getLevel(targetKey) != null) {
            player.displayClientMessage(
                Component.translatable(isPublic
                    ? "personaldimension.message.public_already_exists"
                    : "personaldimension.message.already_exists"), true);
            return;
        }
        // 生成器槽必须放有生成器
        if (menu.getGenerator().isEmpty()) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.need_generator"), true);
            return;
        }
        DimensionPreset preset = DimensionPreset.byId(this.presetId);
        // 自定义模板：必须携带并通过层高 / 名单校验，否则中止且不消耗生成器
        if (preset.requiresTemplate()
            && (this.template.isEmpty() || !validateTemplate(player, this.template.get()))) {
            return;
        }
        // 先消耗生成器再建维/传送（此时各前置校验均已通过，创建必然成功）
        menu.consumeGenerator();
        boolean created = isPublic
            ? PersonalDimensionService.createPublicDimension(
                player, preset, this.template.orElse(null))
            : PersonalDimensionService.createPersonalDimension(
                player, preset, this.template.orElse(null));
        if (created) {
            player.closeContainer();
        }
    }

    /**
     * 服务端复核自定义模板（不信任客户端）：
     * <ul>
     *   <li>所有层的世界 Y 必须落在个人维度可建造区间 [{@link ModDimensions#MIN_BUILD_Y}, {@link ModDimensions#MAX_BUILD_Y})；</li>
     *   <li>调色板中每个方块都须通过 {@link Config#isBlockAllowed(Block)}（防止铺入基岩、屏障等）。</li>
     * </ul>
     * 任一不满足则动作栏提示并返回 false（调用方据此中止且不消耗生成器）。
     */
    private static boolean validateTemplate(ServerPlayer player, DimensionTemplate template) {
        int lowestY = template.baseY();
        int highestY = template.baseY() + template.layerCount() - 1;
        if (lowestY < Platform.minBuildY() || highestY >= Platform.maxBuildY()) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.template_out_of_bounds",
                    Platform.minBuildY(), Platform.maxBuildY() - 1), true);
            return false;
        }
        Optional<Block> denied = template.paletteBlocks()
            .filter(block -> !Platform.isBlockAllowed(block))
            .findFirst();
        if (denied.isPresent()) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.template_block_denied",
                    BuiltInRegistries.BLOCK.getKey(denied.get()).toString()), true);
            return false;
        }
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
