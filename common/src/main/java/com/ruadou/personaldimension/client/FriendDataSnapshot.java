package com.ruadou.personaldimension.client;

/**
 * 好友配置快照（平台无关的数据载体）。
 *
 * <p>从 {@code FriendDataPacket} 抽取的纯数据视图，供客户端缓存与界面渲染使用。
 *
 * @param groups       权限组视图列表（组名 + 权限位掩码）
 * @param friends      好友视图列表（UUID + 显示名 + 所属组名）
 * @param defaultGroup 默认组名（空串表示未设置）
 */
public record FriendDataSnapshot(java.util.List<GroupView> groups,
                                 java.util.List<FriendView> friends,
                                 String defaultGroup) {

    /** 权限组视图：组名 + 权限位掩码。 */
    public record GroupView(String name, int mask) {}

    /** 好友视图：UUID + 显示名 + 所属组名。 */
    public record FriendView(java.util.UUID id, String name, String group) {}
}
