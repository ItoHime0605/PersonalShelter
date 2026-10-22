package com.ruadou.personaldimension.neoforge.attachment;

import com.ruadou.personaldimension.attachment.TravelMemory;

import java.util.function.Supplier;

import com.ruadou.personaldimension.neoforge.PersonalDimension;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 数据附着（Data Attachment）集中注册类。
 * 用于把"玩家级持久化数据"挂到玩家实体上，随存档保存、重登恢复。
 */
@SuppressWarnings("null")
public final class ModAttachments {

    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PersonalDimension.MODID);

    /** 玩家进出个人维度的记忆点（outside / own）。copyOnDeath 保证死亡不丢失返回点。 */
    public static final Supplier<AttachmentType<TravelMemory>> TRAVEL_MEMORY =
        ATTACHMENT_TYPES.register("travel_memory",
            () -> AttachmentType.builder(() -> TravelMemory.EMPTY)
                .serialize(TravelMemory.CODEC)
                .copyOnDeath()
                .build());

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
