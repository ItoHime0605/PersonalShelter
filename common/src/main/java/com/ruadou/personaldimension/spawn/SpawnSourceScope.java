package com.ruadou.personaldimension.spawn;

/**
 * 生物生成拦截的来源范围。
 *
 * <p>决定"禁止生成"作用到哪些来源：
 * <ul>
 *   <li>{@link #ALL_SOURCES}（默认）：拦截一切来源（自然刷怪、刷怪蛋、刷怪笼、繁殖、指令召唤等）；</li>
 *   <li>{@link #NATURAL_ONLY}：仅拦截自然刷怪与区块生成，玩家主动放置/召唤不受限。</li>
 * </ul>
 */
public enum SpawnSourceScope {
    /** 全部来源：任何方式的生成都受名单约束。 */
    ALL_SOURCES,
    /** 仅自然刷怪：只约束自然/区块生成，主动召唤放行。 */
    NATURAL_ONLY;

    /** 按名称解析（持久化用 {@code name()} 字符串），无法识别时回退默认全部来源。 */
    public static SpawnSourceScope byName(String name) {
        for (SpawnSourceScope scope : values()) {
            if (scope.name().equals(name)) {
                return scope;
            }
        }
        return ALL_SOURCES;
    }
}
