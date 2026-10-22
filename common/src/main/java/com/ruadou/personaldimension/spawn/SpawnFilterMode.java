package com.ruadou.personaldimension.spawn;

/**
 * 生物生成名单模式。
 *
 * <p>与 {@link com.ruadou.personaldimension.Config.BlockListMode} 对偶：个人维度默认放行所有生物，
 * 仅拦截列表内的实体，故默认取 {@link #BLACKLIST}。
 * <ul>
 *   <li>{@link #BLACKLIST}（默认）：列表内的实体<b>禁止</b>生成，其余全部放行；</li>
 *   <li>{@link #WHITELIST}：仅列表内的实体<b>允许</b>生成，其余全部拦截。</li>
 * </ul>
 */
public enum SpawnFilterMode {
    /** 黑名单：列表内实体禁止生成，其余放行。 */
    BLACKLIST,
    /** 白名单：仅列表内实体允许生成，其余拦截。 */
    WHITELIST;

    /** 按名称解析（持久化用 {@code name()} 字符串），无法识别时回退默认黑名单。 */
    public static SpawnFilterMode byName(String name) {
        for (SpawnFilterMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return BLACKLIST;
    }
}
