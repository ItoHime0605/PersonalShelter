package com.ruadou.personaldimension.block;

import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.block.entity.TeleporterBlockEntity;
import com.ruadou.personaldimension.dimension.TeleporterData;
import com.ruadou.personaldimension.dimension.TeleporterEntry;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度传送器方块：放置后记录位置到全局 {@link TeleporterData}，
 * 供玩家从个人维度控制器 GUI 中选择并传送到此方块上方。
 *
 * <p>方块本身无 GUI、无红石逻辑——仅作为传送目标锚点。
 * 放置时绑定放置者 UUID，破坏时从全局数据中移除对应条目。
 */
public class TeleporterBlock extends Block implements EntityBlock {

    public TeleporterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeleporterBlockEntity(pos, state);
    }

    /** 放置后：写入放置者 UUID 并注册到全局传送器数据。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        Optional<UUID> dimOwner = Platform.ownerOf(level.dimension());
        if (dimOwner.isPresent() && !dimOwner.get().equals(player.getUUID())) {
            return; // 在他人维度放置已被平台事件拦截，此处为防御性兜底
        }
        if (level.getBlockEntity(pos) instanceof TeleporterBlockEntity be) {
            be.setOwner(player.getUUID());
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            TeleporterEntry entry = TeleporterEntry.of(level.dimension(), pos, "");
            TeleporterData.get(server).add(player.getUUID(), entry);
        }
    }

    /** 破坏/替换时：从全局传送器数据中移除该位置条目。 */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TeleporterBlockEntity be) {
                UUID owner = be.getOwner();
                if (owner != null) {
                    MinecraftServer server = level.getServer();
                    if (server != null) {
                        TeleporterData.get(server).remove(owner, level.dimension(), pos);
                    }
                }
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }
}
