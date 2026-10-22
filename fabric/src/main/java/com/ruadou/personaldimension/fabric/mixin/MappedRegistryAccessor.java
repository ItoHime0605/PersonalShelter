package com.ruadou.personaldimension.fabric.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * MappedRegistry 内部字段访问器。
 * 使用 Mixin @Accessor 替代反射操作注册表内部字段，运行时自动处理映射。
 *
 * <p>注意：byId 和 toId 使用 fastutil 精确类型，Mixin @Accessor 要求类型精确匹配。
 *
 * @param <T> 注册表元素类型
 */
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {

    /** 注册表冻结标志 */
    @Accessor("frozen")
    boolean isFrozen();

    @Accessor("frozen")
    void setFrozen(boolean frozen);

    /** ResourceKey → Holder 映射 */
    @Accessor("byKey")
    Map<ResourceKey<T>, Holder.Reference<T>> getByKey();

    /** ResourceLocation → Holder 映射 */
    @Accessor("byLocation")
    Map<ResourceLocation, Holder.Reference<T>> getByLocation();

    /** Value → Holder 映射 */
    @Accessor("byValue")
    Map<T, Holder.Reference<T>> getByValue();

    /** ID 顺序列表（fastutil ObjectList） */
    @Accessor("byId")
    ObjectList<Holder.Reference<T>> getById();

    /** Value → ID 映射（fastutil Reference2IntMap） */
    @Accessor("toId")
    Reference2IntMap<T> getToId();

    /** ResourceKey → RegistrationInfo 映射 */
    @Accessor("registrationInfos")
    Map<ResourceKey<T>, RegistrationInfo> getRegistrationInfos();
}
