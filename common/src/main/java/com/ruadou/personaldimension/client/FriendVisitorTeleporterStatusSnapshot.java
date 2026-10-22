package com.ruadou.personaldimension.client;

import java.util.List;

/**
 * 好友访问传送器状态快照（平台无关的数据载体）。
 *
 * @param boundToCurrent  当前传送器绑定的权限组列表
 * @param boundToOthers   被其他传送器绑定的权限组列表
 * @param controllerOnly  当前传送器是否仅控制器传送
 */
public record FriendVisitorTeleporterStatusSnapshot(List<String> boundToCurrent,
                                                    List<String> boundToOthers,
                                                    boolean controllerOnly) {}
