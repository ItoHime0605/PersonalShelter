package com.ruadou.personaldimension.friend;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人维度访客权限项。
 *
 * <p>每项以稳定字符串 {@link #id()} 作为持久化键（不用 {@code ordinal()}，
 * 以便日后增删权限项时旧存档仍能正确解析），并携带 UI 展示用的语言键。
 * 权限被组织进 {@link PermissionGroup}，再经 {@link OwnerFriendConfig} 分配给好友/默认访客。
 */
public enum Permission {

    /** 进入该个人维度（访客自助访问列表据此放行）。 */
    ENTER("enter"),
    /** 破坏方块。 */
    BREAK("break"),
    /** 放置方块。 */
    PLACE("place"),
    /** 交互方块（按钮/拉杆/门等非容器交互）。 */
    INTERACT_BLOCK("interact_block"),
    /** 交互实体（右键村民/展示框旋转等）。 */
    INTERACT_ENTITY("interact_entity"),
    /** 攻击生物（非玩家）。 */
    ATTACK("attack"),
    /** 开启容器（箱子/熔炉/漏斗等）。 */
    CONTAINER("container"),
    /** 拾取掉落物。 */
    PICKUP("pickup"),
    /** 攻击玩家（PVP）。 */
    PVP("pvp"),
    /** 使用投掷物/远程武器（弓弩、雪球、末影珍珠、喷溅药水、钓鱼竿等），从投掷动作源头门控。 */
    USE_PROJECTILE("use_projectile"),
    /** 使用桶（水桶/熔岩桶/空桶装取流体、鱼桶放出生物；细雪桶因属方块放置归 {@link #PLACE}）。 */
    USE_BUCKET("use_bucket"),
    /** 使用工具改变方块（打火石/火焰弹点燃、锄头耕地、锹铲路、斧去皮/去氧化去蜡、骨粉催熟）。 */
    MODIFY_BLOCK("modify_block"),
    /** 放置/生成实体（刷怪蛋、船、矿车、盔甲架、末影水晶、画与展示框、拴绳栓结、烟花火箭）。 */
    SPAWN_ENTITY("spawn_entity"),
    /** 丢弃物品（Q 键丢出，或在背包界面把物品拖到界面外丢弃）。 */
    DROP_ITEM("drop_item");

    private final String id;

    Permission(String id) {
        this.id = id;
    }

    /** 持久化/网络用的稳定标识符。 */
    public String id() {
        return this.id;
    }

    /** 权限名称的语言键。 */
    public String translationKey() {
        return "gui.personaldimension.permission." + this.id;
    }

    private static final Map<String, Permission> BY_ID = new HashMap<>();
    static {
        for (Permission p : values()) {
            BY_ID.put(p.id, p);
        }
    }

    /** 按 {@link #id()} 反查权限项；未知返回 null（读旧存档遇未知项时跳过）。 */
    public static Permission byId(String id) {
        return BY_ID.get(id);
    }
}
