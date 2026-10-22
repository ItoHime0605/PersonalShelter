package com.ruadou.personaldimension.friend;

import java.util.EnumSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 一个可命名的权限组：名称 + 一组 {@link Permission}。
 *
 * <p>主人可给组取名、勾选其拥有的权限，并把好友分配到某个组；未分配组的访客套用默认组。
 * 权限集合用 {@link EnumSet} 保证紧凑与顺序稳定。
 */
public final class PermissionGroup {

    private static final String TAG_NAME = "Name";
    private static final String TAG_PERMS = "Perms";

    private String name;
    private final EnumSet<Permission> permissions;

    public PermissionGroup(String name) {
        this(name, EnumSet.noneOf(Permission.class));
    }

    public PermissionGroup(String name, EnumSet<Permission> permissions) {
        this.name = name;
        this.permissions = permissions;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean has(Permission permission) {
        return this.permissions.contains(permission);
    }

    /** 打开/关闭某权限。 */
    public void set(Permission permission, boolean enabled) {
        if (enabled) {
            this.permissions.add(permission);
        } else {
            this.permissions.remove(permission);
        }
    }

    /** 返回权限集合的只读快照（避免外部直接改动内部状态）。 */
    public EnumSet<Permission> permissions() {
        return EnumSet.copyOf(this.permissions.isEmpty()
            ? EnumSet.noneOf(Permission.class)
            : this.permissions);
    }

    /** 权限位掩码（bit i = Permission.values()[i] 是否开启），供网络传输/UI 复用。 */
    public int mask() {
        int mask = 0;
        for (Permission p : this.permissions) {
            mask |= (1 << p.ordinal());
        }
        return mask;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_NAME, this.name);
        ListTag list = new ListTag();
        for (Permission p : this.permissions) {
            list.add(StringTag.valueOf(p.id()));
        }
        tag.put(TAG_PERMS, list);
        return tag;
    }

    public static PermissionGroup load(CompoundTag tag) {
        EnumSet<Permission> perms = EnumSet.noneOf(Permission.class);
        ListTag list = tag.getList(TAG_PERMS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            Permission p = Permission.byId(list.getString(i));
            if (p != null) {
                perms.add(p);
            }
        }
        return new PermissionGroup(tag.getString(TAG_NAME), perms);
    }
}
