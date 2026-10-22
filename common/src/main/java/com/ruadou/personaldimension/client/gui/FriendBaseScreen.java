package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 好友系统各界面的公共基类。
 *
 * <p>在 {@link PanelScreen} 的手绘面板骨架之上，追加：上一屏引用与返回、
 * 打开与周期性向服务端请求数据。子类只需实现 {@link #buildWidgets()} 与 {@link #requestData()}，
 * 并按需覆写 {@link #renderContent} / {@link #onTick}，避免各界面重复相同的骨架逻辑（DRY）。
 *
 * <p><b>刷新机制</b>：服务端数据经网络线程异步写入客户端缓存，故子类在 {@link #onTick} 中比对
 * 数据内容（record 结构相等），仅在<b>内容变化</b>时调用 {@link #rebuildWidgets()} 重建行控件，
 * 既能反映最新数据又不会因每次请求返回新实例而无限重建。
 */
public abstract class FriendBaseScreen extends PanelScreen {

    /** 列表数据每秒刷新一次（20 tick）。 */
    private static final int REFRESH_INTERVAL = 20;

    protected final Screen parent;
    private int refreshCounter;

    protected FriendBaseScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        buildWidgets();
        requestData();
    }

    /** 构建本界面控件（每次 rebuildWidgets 都会重新调用）。 */
    protected abstract void buildWidgets();

    /** 向服务端请求本界面所需数据（打开与周期刷新时调用，须幂等）。 */
    protected abstract void requestData();

    @Override
    public void tick() {
        if (this.refreshCounter <= 0) {
            requestData();
            this.refreshCounter = REFRESH_INTERVAL;
        } else {
            this.refreshCounter--;
        }
        onTick();
    }

    /** 子类周期回调：比对数据内容并按需 {@link #rebuildWidgets()}。默认无操作。 */
    protected void onTick() {}

    /** 返回上一界面（通常为控制器界面）。 */
    protected void back() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
