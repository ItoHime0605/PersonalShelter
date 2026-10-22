package com.ruadou.personaldimension.dimension;

/**
 * 个人维度创建预设。
 *
 * <p>作为创建流程各层（容器界面、网络包、服务端建维）共享的契约：界面上传 {@link #id()}，
 * 服务端用 {@link #byId(int)} 还原并按预设建维，非法值兜底为 {@link #VOID}，避免越权/越界。
 *
 * <ul>
 *   <li>{@link #VOID} / {@link #SKY_ISLAND}：共用虚空 stem，差异只在落脚结构（服务端手动铺设）；</li>
 *   <li>{@link #CUSTOM}：使用携带玩家自定义 {@code DimensionTemplate} 的可存档区块生成器，逐区块平铺。</li>
 * </ul>
 */
public enum DimensionPreset {
    /** 虚空：脚下一块 3x3 草方块平台。 */
    VOID(0),
    /** 空岛：漂浮的小圆形草岛 + 树 + 起始物资箱。 */
    SKY_ISLAND(1),
    /** 自定义模板：由画板编辑的 16×16×N 布局逐区块平铺。 */
    CUSTOM(2);

    private final int id;

    DimensionPreset(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }

    /** 是否需要玩家上传自定义模板数据。 */
    public boolean requiresTemplate() {
        return this == CUSTOM;
    }

    /** 按稳定 id 还原预设；未知 id 兜底为 {@link #VOID}，防止客户端传入非法值。 */
    public static DimensionPreset byId(int id) {
        for (DimensionPreset preset : values()) {
            if (preset.id == id) {
                return preset;
            }
        }
        return VOID;
    }
}
