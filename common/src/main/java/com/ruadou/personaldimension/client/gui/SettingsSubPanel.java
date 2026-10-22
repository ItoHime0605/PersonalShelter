package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientDimensionInfo;
import com.ruadou.personaldimension.client.DimensionInfoSnapshot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 设置子页（时间 / 天气）的公共基类。
 *
 * <p>统一顶行骨架：左上角返回按钮 + 右侧"跟随主世界"开关。开启跟随时子页内的受控控件
 * （预设 / 循环开关 / 输入框等）灰显禁用，避免与"跟随"语义冲突。正文由子类经
 * {@link #buildBody} 注入，跟随判定与切换由 {@link #isFollowing}/{@link #toggleFollow} 提供。
 *
 * <p>本类非 {@code Screen} 子类，控件添加与几何度量均经
 * {@link PersonalDimensionControllerScreen} 暴露的包内访问器完成
 * （PanelScreen 的 protected 成员跨包不可直接访问）。
 */
abstract class SettingsSubPanel {

    protected final PersonalDimensionControllerScreen screen;
    protected final List<AbstractWidget> dependents = new ArrayList<>();
    protected Button followButton;

    protected SettingsSubPanel(PersonalDimensionControllerScreen screen) {
        this.screen = screen;
    }

    /** 构建子页：顶行返回 + 跟随开关，随后交由子类填充正文，最后套用跟随门控。 */
    final void build() {
        int x = screen.panelContentLeft();
        int padding = screen.panelPadding();
        int gap = 6;

        int backY = screen.panelContentTop() + padding;
        screen.add(new FlatButton(
                Component.translatable("gui.personaldimension.controller.back"),
                x, backY, 44, 20, b -> screen.backToSettingsHome()));
        this.followButton = new FlatButton(followMessage(),
                x + 44 + gap, backY, screen.panelContentWidth() - 44 - gap, 20, b -> toggleFollow());
        screen.add(this.followButton);

        buildBody(x, backY + 20 + padding, gap);
        refreshGating();
    }

    /** 每 tick 刷新跟随按钮文字与门控（子类可覆写以追加自身按钮的刷新）。 */
    void tick() {
        if (this.followButton != null) {
            this.followButton.setMessage(followMessage());
        }
        refreshGating();
    }

    /** 键盘事件钩子（时间子页覆写以处理输入框回车）。默认不消费。 */
    boolean keyPressed(int keyCode) {
        return false;
    }

    /** 跟随开启时禁用（灰显）受控控件。 */
    protected void refreshGating() {
        boolean follow = isFollowing();
        for (AbstractWidget widget : this.dependents) {
            widget.active = !follow;
        }
    }

    /** "跟随主世界: 开/关"按钮文字。 */
    protected Component followMessage() {
        String stateKey = isFollowing()
            ? "gui.personaldimension.controller.follow.on"
            : "gui.personaldimension.controller.follow.off";
        return Component.translatable("gui.personaldimension.controller.follow")
            .append(": ")
            .append(Component.translatable(stateKey));
    }

    /** 当前服务端权威信息快照（可能为空）。 */
    protected DimensionInfoSnapshot info() {
        return ClientDimensionInfo.info();
    }

    /** 构建正文控件：起始 x、正文起始 y、按钮间距。 */
    protected abstract void buildBody(int x, int bodyTop, int gap);

    /** 是否跟随主世界（时间 / 天气各自判定）。 */
    protected abstract boolean isFollowing();

    /** 切换跟随主世界（发送对应网络包）。 */
    protected abstract void toggleFollow();
}
