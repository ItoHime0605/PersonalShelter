package com.ruadou.personaldimension.friend;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 单个维度主人的好友与权限配置。
 *
 * <p>内容：好友表（好友 UUID -&gt; 组名）、权限组表（组名 -&gt; {@link PermissionGroup}）、默认组名。
 * 权限解析规则（{@link #resolve(UUID)}，主人在 {@link FriendService} 层单独放行全部权限）：
 * <ol>
 *   <li>访客是好友且其分配的组存在 =&gt; 用该组权限；</li>
 *   <li>否则（非好友，或好友未分配/组已删）=&gt; 用默认组权限；</li>
 *   <li>默认组不存在 =&gt; 无任何权限（维度对外私有）。</li>
 * </ol>
 *
 * <p><b>线程约束</b>：本类实例仅在服务端主线程访问（事件处理器与网络包 enqueueWork 均在主线程），
 * 故使用普通 {@link LinkedHashMap}（保留组的插入顺序以便 UI 稳定展示），无需并发容器。
 */
public final class OwnerFriendConfig {

    /** 新建配置时自动播种的默认组名（用户可改名，仅作初始种子值）。 */
    public static final String DEFAULT_GROUP_NAME = "預設群組";

    private static final String TAG_FRIENDS = "Friends";
    private static final String TAG_FRIEND_ID = "Id";
    private static final String TAG_FRIEND_GROUP = "Group";
    private static final String TAG_GROUPS = "Groups";
    private static final String TAG_DEFAULT_GROUP = "DefaultGroup";

    private final Map<UUID, String> friends = new LinkedHashMap<>();
    private final Map<String, PermissionGroup> groups = new LinkedHashMap<>();
    private String defaultGroupName;

    /** 供加载复原用的空构造。 */
    private OwnerFriendConfig() {}

    /** 新建一份带初始默认组（空权限）的配置，主人首次打开管理界面时使用。 */
    public static OwnerFriendConfig createDefault() {
        return createDefault(EnumSet.noneOf(Permission.class));
    }

    /**
     * 新建一份带初始默认组的配置，并指定默认组的初始权限集合。
     * 用于主人首次打开管理界面时按配置播种默认组的权限。
     *
     * @param defaultPerms 默认组的初始权限（由调用方从配置决定）
     */
    public static OwnerFriendConfig createDefault(EnumSet<Permission> defaultPerms) {
        OwnerFriendConfig config = new OwnerFriendConfig();
        config.groups.put(DEFAULT_GROUP_NAME,
            new PermissionGroup(DEFAULT_GROUP_NAME, EnumSet.noneOf(Permission.class)));
        config.defaultGroupName = DEFAULT_GROUP_NAME;
        return config;
    }

    // ——— 好友 ———

    public Map<UUID, String> friends() {
        return this.friends;
    }

    public void addFriend(UUID friend) {
        this.friends.putIfAbsent(friend, this.defaultGroupName == null ? "" : this.defaultGroupName);
    }

    public void removeFriend(UUID friend) {
        this.friends.remove(friend);
    }

    /** 给好友分配组名；空串表示"未分配"（回落到默认组）。 */
    public void assignGroup(UUID friend, String groupName) {
        if (this.friends.containsKey(friend)) {
            this.friends.put(friend, groupName == null ? "" : groupName);
        }
    }

    // ——— 权限组 ———

    public Map<String, PermissionGroup> groups() {
        return this.groups;
    }

    public PermissionGroup group(String name) {
        return this.groups.get(name);
    }

    /** 新建组（同名已存在则忽略，权限集为空）。返回是否新建成功。 */
    public boolean createGroup(String name) {
        return createGroup(name, EnumSet.noneOf(Permission.class));
    }

    /**
     * 新建组并指定初始权限集合（同名已存在则忽略）。
     * 用于按配置播种新建组的默认权限。
     *
     * @param name       组名
     * @param defaultPerms 初始权限集合（由调用方从配置决定）
     * @return 是否新建成功
     */
    public boolean createGroup(String name, EnumSet<Permission> defaultPerms) {
        if (name == null || name.isBlank() || this.groups.containsKey(name) || DEFAULT_GROUP_NAME.equals(name)) {
            return false;
        }
        this.groups.put(name, new PermissionGroup(name, safeCopy(defaultPerms)));
        return true;
    }

    /** 重命名组，并同步更新默认组名与好友分配指向。返回是否成功。 */
    public boolean renameGroup(String oldName, String newName) {
        if (DEFAULT_GROUP_NAME.equals(oldName) || newName == null || newName.isBlank()
            || !this.groups.containsKey(oldName) || this.groups.containsKey(newName)) {
            return false;
        }
        // 保留插入顺序：重建映射，命中旧名处替换为新名
        Map<String, PermissionGroup> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, PermissionGroup> e : this.groups.entrySet()) {
            if (e.getKey().equals(oldName)) {
                e.getValue().setName(newName);
                rebuilt.put(newName, e.getValue());
            } else {
                rebuilt.put(e.getKey(), e.getValue());
            }
        }
        this.groups.clear();
        this.groups.putAll(rebuilt);
        if (oldName.equals(this.defaultGroupName)) {
            this.defaultGroupName = newName;
        }
        for (Map.Entry<UUID, String> e : this.friends.entrySet()) {
            if (oldName.equals(e.getValue())) {
                e.setValue(newName);
            }
        }
        return true;
    }

    /** 删除组，并清理默认组指向与好友分配。返回是否成功。 */
    public boolean deleteGroup(String name) {
        if (DEFAULT_GROUP_NAME.equals(name) || this.groups.remove(name) == null) {
            return false;
        }
        if (name.equals(this.defaultGroupName)) {
            this.defaultGroupName = null;
        }
        for (Map.Entry<UUID, String> e : this.friends.entrySet()) {
            if (name.equals(e.getValue())) {
                e.setValue("");
            }
        }
        return true;
    }

    public String defaultGroupName() {
        return this.defaultGroupName;
    }

    /** 设默认组（组不存在则忽略）。 */
    public void setDefaultGroup(String name) {
        if (DEFAULT_GROUP_NAME.equals(name)) {
            this.defaultGroupName = name;
        }
    }

    /** 安全复制 EnumSet：空集合时 copyOf 会抛异常，用 noneOf 兜底。 */
    private static EnumSet<Permission> safeCopy(EnumSet<Permission> set) {
        return set.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(set);
    }

    // ——— 解析 ———

    /**
     * 解析某访客（非主人）在本维度拥有的权限集合。
     * 好友且分配组存在则用该组；否则回落默认组；默认组缺失则无权限。
     */
    public EnumSet<Permission> resolve(UUID actor) {
        String assigned = this.friends.get(actor);
        PermissionGroup group = (assigned != null && !assigned.isEmpty())
            ? this.groups.get(assigned)
            : null;
        if (group == null && this.defaultGroupName != null) {
            group = this.groups.get(this.defaultGroupName);
        }
        return group != null ? group.permissions() : EnumSet.noneOf(Permission.class);
    }

    // ——— 持久化 ———

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag friendList = new ListTag();
        for (Map.Entry<UUID, String> e : this.friends.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putUUID(TAG_FRIEND_ID, e.getKey());
            f.putString(TAG_FRIEND_GROUP, e.getValue());
            friendList.add(f);
        }
        tag.put(TAG_FRIENDS, friendList);

        ListTag groupList = new ListTag();
        for (PermissionGroup group : this.groups.values()) {
            groupList.add(group.save());
        }
        tag.put(TAG_GROUPS, groupList);

        if (this.defaultGroupName != null) {
            tag.putString(TAG_DEFAULT_GROUP, this.defaultGroupName);
        }
        return tag;
    }

    public static OwnerFriendConfig load(CompoundTag tag) {
        OwnerFriendConfig config = new OwnerFriendConfig();
        ListTag groupList = tag.getList(TAG_GROUPS, Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            PermissionGroup group = PermissionGroup.load(groupList.getCompound(i));
            config.groups.put(group.getName(), group);
        }
        ListTag friendList = tag.getList(TAG_FRIENDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < friendList.size(); i++) {
            CompoundTag f = friendList.getCompound(i);
            config.friends.put(f.getUUID(TAG_FRIEND_ID), f.getString(TAG_FRIEND_GROUP));
        }
        if (tag.contains(TAG_DEFAULT_GROUP)) {
            config.defaultGroupName = tag.getString(TAG_DEFAULT_GROUP);
        }
        return config;
    }
}
