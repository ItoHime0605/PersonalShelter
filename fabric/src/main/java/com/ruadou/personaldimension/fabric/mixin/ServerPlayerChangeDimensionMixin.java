package com.ruadou.personaldimension.fabric.mixin;

import java.util.Optional;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterService;
import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;

/**
 * 在 changeDimension 方法 HEAD 处拦截，实现维度切换前的权限检查。
 *
 * <p>Fabric 仅有 AFTER_PLAYER_CHANGE_WORLD 事件（无 BEFORE），无法在传送前取消。
 * 通过 Mixin 注入到 ServerPlayer.changeDimension，在传送发生前检查：
 * <ol>
 *   <li>无 ENTER 权限 → 取消传送（拦截 /tp、传送门等绕过控制器的路径）；</li>
 *   <li>有 ENTER 权限且控制器发起 → 放行（handleVisit 已处理落点）；</li>
 *   <li>有 ENTER 权限但非控制器发起 → 按好友访问传送器模式决定：
 *     <ul>
 *       <li>controllerOnly=true → 放行，不重定向；</li>
 *       <li>controllerOnly=false → 放行但标记重定向，
 *           在 onPlayerChangedDimension 中挪到传送器位置。</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerChangeDimensionMixin {

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void personaldimension$onPreChangeDimension(
            DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ServerLevel target = transition.newLevel();
        // 仅当目标维度是他人个人维度时检查
        Optional<UUID> owner = ModDimensions.ownerOf(target.dimension());
        if (owner.isEmpty() || owner.get().equals(self.getUUID())) {
            return;
        }
        MinecraftServer server = self.getServer();
        if (server == null) {
            return;
        }
        // 无 ENTER 权限：取消传送
        if (!FriendService.hasPermission(server, owner.get(), self.getUUID(), Permission.ENTER)) {
            // 清除可能残留的控制器标记
            PersonalDimensionService.consumeControllerVisit(self.getUUID());
            self.displayClientMessage(
                Component.translatable("personaldimension.message.visit_denied"), true);
            cir.setReturnValue(self);
            return;
        }
        // 控制器发起的传送：消耗标记，放行
        if (PersonalDimensionService.consumeControllerVisit(self.getUUID())) {
            return;
        }
        // 非控制器传送：按好友访问传送器模式决定
        FriendVisitorTeleporterService.VisitorTarget visitTarget =
            FriendVisitorTeleporterService.getVisitorTarget(server, owner.get(), self.getUUID());
        if (visitTarget == null || visitTarget.controllerOnly()) {
            return; // 无传送器配置或控制器传送模式：放行
        }
        // 全部传送方式：放行但标记重定向
        GuardService.markPendingRedirect(self.getUUID());
    }
}
