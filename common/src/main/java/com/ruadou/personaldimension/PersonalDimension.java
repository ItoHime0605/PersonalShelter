package com.ruadou.personaldimension;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * 模组共享常量基类。
 * 两端 Fabric / NeoForge 主类继承此类以复用 MODID 与 LOGGER，
 * 保证 common 模块内的注册（Packet、BlockEntity、Attachment 等）能统一引用同一份常量。
 */
public class PersonalDimension {

    /** 模组命名空间，所有注册名的前缀。 */
    public static final String MODID = "personaldimension";

    /** 共享日志器。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 个人维度可建造 Y 下界（含），与 dimension_type/personal.json 的 min_y 一致。 */
    public static final int MIN_BUILD_Y = -64;

    /** 个人维度可建造 Y 上界（不含）= min_y + height，用于校验自定义模板层高。 */
    public static final int MAX_BUILD_Y = 320;
}
