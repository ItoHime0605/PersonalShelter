package com.ruadou.personaldimension.neoforge.menu;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import com.ruadou.personaldimension.dimension.ModDimensions;

/** Temporary direct entry point while controller GUI integration is pending. */
public final class ShelterUpgradeCommands {
    private ShelterUpgradeCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pshelter")
            .then(Commands.literal("upgrade").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!ModDimensions.ownerOf(player.level().dimension())
                    .map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
                    ctx.getSource().sendFailure(Component.literal("請先進入自己的個人空間，才能使用土地擴張介面。"));
                    return 0;
                }
                player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ShelterUpgradeMenu(id, inventory),
                    Component.literal("土地擴張")));
                return 1;
            })));
    }
}
