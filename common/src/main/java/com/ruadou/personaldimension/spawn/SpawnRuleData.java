package com.ruadou.personaldimension.spawn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 个人维度的生物生成规则持久化载体。
 *
 * <p>规则按维度隔离：存到<b>该维度自己的</b> {@code DimensionDataStorage}
 * （dimensions/&lt;dim&gt;/data/personal_dimension_spawn_rules.dat），随维度存档读写、随维度删除清理，
 * 无需额外按 UUID 索引。职责单一，独立于时间/天气的 {@code PersonalDimensionData}（SRP）。
 *
 * <p>持久化避免使用枚举 ordinal：mode/scope 存 {@code name()} 字符串，缺失时经 {@code byName} 回退默认；
 * entities 存实体注册 id 字符串列表，读取时用 {@link ResourceLocation#tryParse} 校验，忽略非法项。
 *
 * <p><b>线程约束</b>：仅服务端主线程访问，使用 {@link LinkedHashSet} 保持插入顺序以便界面稳定展示。
 */
public class SpawnRuleData extends SavedData {

    /** 存档文件名（位于 dimensions/&lt;dim&gt;/data/personal_dimension_spawn_rules.dat）。 */
    public static final String ID = "personal_dimension_spawn_rules";

    private static final String TAG_MODE = "Mode";
    private static final String TAG_SCOPE = "Scope";
    private static final String TAG_ENTITIES = "Entities";

    // 第三参 DataFixTypes 传 null：本数据无需 Mojang 数据修复器（用原版 3 参构造，跨加载器通用）。
    public static final SavedData.Factory<SpawnRuleData> FACTORY =
        new SavedData.Factory<>(SpawnRuleData::new, SpawnRuleData::load, null);

    private SpawnFilterMode mode = SpawnFilterMode.BLACKLIST;
    private SpawnSourceScope scope = SpawnSourceScope.ALL_SOURCES;
    private final Set<ResourceLocation> entities = new LinkedHashSet<>();

    public SpawnRuleData() {}

    /** 从该维度自己的存档获取（不存在则创建）生物生成规则。 */
    public static SpawnRuleData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    private static SpawnRuleData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpawnRuleData data = new SpawnRuleData();
        data.mode = SpawnFilterMode.byName(tag.getString(TAG_MODE));
        data.scope = SpawnSourceScope.byName(tag.getString(TAG_SCOPE));
        ListTag list = tag.getList(TAG_ENTITIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                data.entities.add(id);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(TAG_MODE, this.mode.name());
        tag.putString(TAG_SCOPE, this.scope.name());
        ListTag list = new ListTag();
        for (ResourceLocation id : this.entities) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_ENTITIES, list);
        return tag;
    }

    public SpawnFilterMode getMode() {
        return this.mode;
    }

    public SpawnSourceScope getScope() {
        return this.scope;
    }

    /** 只读获取名单实体 id 集合（供界面展示与网络回传）。 */
    public Set<ResourceLocation> getEntities() {
        return Collections.unmodifiableSet(this.entities);
    }

    public void setMode(SpawnFilterMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.setDirty();
        }
    }

    public void setScope(SpawnSourceScope scope) {
        if (this.scope != scope) {
            this.scope = scope;
            this.setDirty();
        }
    }

    /** 增删名单中的一个实体 id；有实际变化才标脏，避免无意义写入。 */
    public void setEntity(ResourceLocation id, boolean add) {
        boolean changed = add ? this.entities.add(id) : this.entities.remove(id);
        if (changed) {
            this.setDirty();
        }
    }

    /**
     * 判断指定实体在本维度是否<b>被禁止</b>生成。
     * 黑名单：在列表内即禁止；白名单：不在列表内即禁止（与 {@link SpawnFilterMode} 语义对偶）。
     */
    public boolean isBlocked(ResourceLocation id) {
        boolean inList = this.entities.contains(id);
        return this.mode == SpawnFilterMode.WHITELIST ? !inList : inList;
    }
}
