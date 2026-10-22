package com.ruadou.personaldimension.fabric.attachment;

import com.ruadou.personaldimension.attachment.TravelMemory;
import com.ruadou.personaldimension.fabric.PersonalDimension;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

/**
 * 数据附着（Data Attachment）集中注册类（Fabric 实现）。
 * 用于把"玩家级持久化数据"挂到玩家实体上，随存档保存、重登恢复。
 * 
 * <p>使用 Fabric API 的 AttachmentRegistry，对应 NeoForge 的 DeferredRegister + AttachmentType。
 */
public final class ModAttachments {

    private ModAttachments() {}

    /** 玩家进出个人维度的记忆点（outside / own）。copyOnDeath 保证死亡不丢失返回点。 */
    public static final AttachmentType<TravelMemory> TRAVEL_MEMORY = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "travel_memory"),
        builder -> builder
            .initializer(() -> TravelMemory.EMPTY)
            .persistent(TravelMemory.CODEC)
            .copyOnDeath()
    );

    public static void init() {
        // 静态初始化即完成注册，此方法仅用于触发类加载
    }
}
