package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.network.DeletePersonalDimensionPacket;
import com.ruadou.personaldimension.network.OpenCreateMenuPacket;
import com.ruadou.personaldimension.network.OpenShelterUpgradePacket;
import com.ruadou.personaldimension.network.SetShelterEntryLockPacket;
import com.ruadou.personaldimension.network.PersonalDimensionTravelPacket;
import com.ruadou.personaldimension.network.ReturnToOwnShelterPacket;
import com.ruadou.personaldimension.network.PublicDimensionPacket;
import com.ruadou.personaldimension.network.RequestDimensionInfoPacket;
import com.ruadou.personaldimension.network.RequestShelterNamePacket;
import com.ruadou.personaldimension.network.RequestLandRadioPacket;
import com.ruadou.personaldimension.network.SetLandRadioPacket;
import com.ruadou.personaldimension.network.EditLandRadioStationPacket;
import com.ruadou.personaldimension.radio.LandRadioSettings;
import com.ruadou.personaldimension.radio.DefaultRadioStations;
import com.ruadou.personaldimension.client.ClientLandRadioData;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import com.ruadou.personaldimension.client.ClientRadioListening;
import com.ruadou.personaldimension.client.ClientRadioPlaybackStatus;
import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.network.SetShelterNamePacket;
import com.ruadou.personaldimension.client.ClientShelterName;
import net.minecraft.client.gui.components.EditBox;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 个人维度控制器界面。
 *
 * <p>整体为现代深色扁平面板；标题栏为贴合面板顶边的<b>横向标签</b>（信息 / 设置 / 好友，
 * 浏览器 / 记事本风），选中标签略亮凸起并越过分隔线与正文相连，左侧带强调色竖条。
 * 设置页内为二级菜单：设置主页提供各类分组入口，点击进入时间 / 天气子页（子页左上角有返回按钮）。
 *
 * <p>信息与时间均来自服务端权威快照 DimensionInfoSnapshot，
 * 因此展示的始终是玩家<b>自己个人维度</b>的状态（无论玩家当前身处何处）。
 */
public class PersonalDimensionControllerScreen extends PanelScreen {

    private static final int REFRESH_INTERVAL = 10; // 每 0.5 秒向服务端刷新一次信息

    // —— 顶部横向标签（浏览器 / 记事本风，贴合面板顶边）——
    private static final int TAB_STRIP_LEFT = 4; // 标签栏起始 x（相对 leftPos），令首标签强调竖条对齐原标题位
    private static final int TAB_TEXT_LEFT = 9;  // 标签内文字左内缩（含左侧强调竖条空间）
    private static final int TAB_PAD_RIGHT = 8;  // 标签内文字右留白
    private static final int TAB_GAP = 2;        // 相邻标签间距

    // 文字色沿用公共样式（现代深色主题主文字）
    private static final int TEXT_COLOR = ModernStyle.TEXT;

    /** 一级标签页。 */
    private enum Tab {
        INFO("gui.personaldimension.controller.tab.info"),
        SETTINGS("gui.personaldimension.controller.tab.settings"),
        FRIENDS("gui.personaldimension.controller.tab.friends"),
        TELEPORT("gui.personaldimension.controller.tab.teleport");

        private final String titleKey;

        Tab(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    /** 设置页内的二级子页。 */
    private enum SettingsPage {
        HOME, TIME, WEATHER, MOON_PHASE, THEME, RADIO
    }

    /** 删除确认的目标：个人维度或公共维度（决定确认文案与下发包）。 */
    private enum DeleteTarget {
        PERSONAL, PUBLIC
    }

    private Tab activeTab = Tab.INFO;
    private SettingsPage settingsPage = SettingsPage.HOME;
    private int refreshCounter;
    private Boolean lastEntryLockState;
    private int radioPage;
    private boolean radioCustomPage;
    private String radioEditingId = "";
    private EditBox radioNameInput;
    private EditBox radioUrlInput;
    private long lastRadioRevision = -1;
    private String radioEditorError = "";

    private Button travelButton;
    private boolean renaming;
    private EditBox shelterNameInput;

    // 当前激活的设置子页（时间 / 天气），非设置子页时为 null；tick/keyPressed 委派给它
    private SettingsSubPanel activePanel;

    // 传送标签页面板（仅 TELEPORT 标签激活时非 null）
    private TeleportTabPanel teleportPanel;

    // —— 删除确认态（信息页专用）——
    private boolean confirmingDelete;        // 是否处于"删除确认"界面
    private DeleteTarget deleteTarget;       // 当前确认删除的目标（个人 / 公共）
    private Button deleteConfirmButton;      // "确定"按钮引用（ModernHoldButton，按住满进度触发）

    public PersonalDimensionControllerScreen() {
        super(Component.translatable("gui.personaldimension.controller.title"));
    }

    public void showSettingsHome() {
        this.activeTab = Tab.SETTINGS;
        this.settingsPage = SettingsPage.HOME;
    }

    @Override
    protected void init() {
        super.init();
        this.travelButton = null;
        this.shelterNameInput = null;
        this.radioNameInput = null;
        this.radioUrlInput = null;
        this.activePanel = null;
        // 注意：teleportPanel 不在此重置——rebuildPanel() 会触发 init()，
        // 若每次 new 新实例则 selectedIndex/scrollOffset 丢失，导致编辑和滚动失效。
        // 仅在切换标签时由 selectTab() 置 null 重建。
        this.deleteConfirmButton = null;

        if (isVisitingFriendLand()) {
            addVisitorButtons();
        } else if (this.activeTab == Tab.SETTINGS) {
            if (this.settingsPage == SettingsPage.HOME) {
                addSettingsHome();
            } else if (this.settingsPage == SettingsPage.TIME) {
                this.activePanel = new TimeSubPanel(this);
                this.activePanel.build();
            } else if (this.settingsPage == SettingsPage.WEATHER) {
                this.activePanel = new WeatherSubPanel(this);
                this.activePanel.build();
            } else if (this.settingsPage == SettingsPage.MOON_PHASE) {
                this.activePanel = new MoonPhaseSubPanel(this);
                this.activePanel.build();
            } else if (this.settingsPage == SettingsPage.THEME) {
                addThemeSelection();
            } else {
                addRadioSettings();
            }
        } else if (this.activeTab == Tab.FRIENDS) {
            // 好友页：好友管理 / 访问维度两个入口
            addFriendsHome();
        } else if (this.activeTab == Tab.TELEPORT) {
            // 传送页：保留面板实例（跨 rebuild 不丢失 selectedIndex/scrollOffset）
            if (this.teleportPanel == null) {
                this.teleportPanel = new TeleportTabPanel(this);
                this.teleportPanel.requestList();
            }
            this.teleportPanel.build();
        } else {
            // 信息页底部提供"创建维度""进入/退出"个人维度按钮
            addInfoButtons();
        }

        // 打开即请求一次，避免首帧信息为空
        requestInfo();
        Platform.sendToServer(new RequestShelterNamePacket());
        if (this.activeTab == Tab.SETTINGS && this.settingsPage == SettingsPage.RADIO) {
            Platform.sendToServer(new RequestLandRadioPacket());
        }
    }

    private boolean isVisitingFriendLand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var dimension = mc.player.level().dimension();
        return Platform.isModDimension(dimension) && !Platform.isPublicDimension(dimension)
            && !dimension.equals(Platform.levelKeyFor(mc.player.getUUID()));
    }

    /** 拖曳時即時調整本機電台音量；左右按鈕仍可精準增減 1%。 */
    private void addVolumeSlider(int x, int y, int width, int height) {
        this.addRenderableWidget(new AbstractSliderButton(x, y, width, height,
                Component.literal("音量 " + ClientRadioVolume.get() + "%"), ClientRadioVolume.get() / 100.0D) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("音量 " + Math.round(value * 100.0D) + "%"));
            }

            @Override
            protected void applyValue() {
                ClientRadioVolume.set((int) Math.round(value * 100.0D));
            }
        });
    }

    private void addVisitorButtons() {
        int x = this.leftPos + (PANEL_WIDTH - 174) / 2;
        int y = contentTop() + 62;
        this.addRenderableWidget(new FlatButton(
            Component.literal("收聽地主電台：" + (ClientRadioListening.isListening() ? "是" : "否")),
            x, y, 174, 22, b -> {
                ClientRadioListening.setListening(!ClientRadioListening.isListening());
                rebuildWidgets();
            }));
        this.addRenderableWidget(new FlatButton(Component.literal("音量 －"),
            x, y + 30, 55, 20, b -> {
                ClientRadioVolume.set(ClientRadioVolume.get() - 1);
                rebuildWidgets();
            }));
        addVolumeSlider(x + 59, y + 30, 56, 20);
        this.addRenderableWidget(new FlatButton(Component.literal("音量 ＋"),
            x + 119, y + 30, 55, 20, b -> {
                ClientRadioVolume.set(ClientRadioVolume.get() + 1);
                rebuildWidgets();
            }));
        this.addRenderableWidget(new FlatButton(Component.literal("退出好友土地"), x, y + 72, 174, 22,
            b -> { Platform.sendToServer(new PersonalDimensionTravelPacket()); onClose(); }));
        this.addRenderableWidget(new FlatButton(Component.literal("回到自己私人土地"), x, y + 102, 174, 22,
            b -> { Platform.sendToServer(new ReturnToOwnShelterPacket()); onClose(); }));
    }

    // —— 供同包设置子面板访问 PanelScreen 的 protected 骨架（跨包不可直接访问）——

    /** 添加控件（包装受保护的 addRenderableWidget）。 */
    <T extends AbstractWidget> T add(T widget) {
        return this.addRenderableWidget(widget);
    }

    int panelContentLeft() {
        return contentLeft();
    }

    int panelContentTop() {
        return contentTop();
    }

    int panelContentWidth() {
        return contentWidth();
    }

    int panelPadding() {
        return PADDING;
    }

    int panelLeftPos() {
        return leftPos;
    }

    int panelTopPos() {
        return topPos;
    }

    int panelWidth() {
        return PANEL_WIDTH;
    }

    int panelHeight() {
        return PANEL_HEIGHT;
    }

    /** 重建控件（供同包面板类调用，Screen.rebuildWidgets 为 protected）。 */
    void rebuildPanel() {
        rebuildWidgets();
    }

    /** 子页返回按钮：回到设置主页。 */
    void backToSettingsHome() {
        openSettingsPage(SettingsPage.HOME);
    }

    /**
     * 信息页底部按钮组。
     * <ul>
     *   <li>删除确认态：仅显示"确定 / 取消"（{@link #addDeleteConfirmButtons()}，"确定"为长按变红按钮），屏蔽其余入口；</li>
     *   <li>正常态：最上方为公共维度按钮（进入/创建/删除），次上方随个人维度存在与否显示"删除/创建"，底部恒为"进入/退出"。</li>
     * </ul>
     */
    private void addInfoButtons() {
        // 处于删除确认态：只呈现确定 / 取消，避免误触其它入口
        if (this.confirmingDelete) {
            addDeleteConfirmButtons();
            return;
        }
        int btnWidth = 146;
        if (ownDimensionExists()) {
            int renameY = contentTop() + PADDING;
            int nameX = contentLeft();
            int available = contentWidth();
            if (renaming) {
                shelterNameInput = new EditBox(this.font, nameX, renameY, available - 62, 20,
                    Component.literal("私人土地名稱"));
                shelterNameInput.setMaxLength(48);
                shelterNameInput.setValue(ClientShelterName.get());
                shelterNameInput.setHint(Component.literal("輸入名稱"));
                this.addRenderableWidget(shelterNameInput);
                this.addRenderableWidget(new FlatButton(Component.literal("儲存"),
                    nameX + available - 58, renameY, 58, 18, b -> saveShelterName()));
            } else {
                this.addRenderableWidget(new FlatButton(Component.literal("土地改名"),
                    nameX + available - 78, renameY, 78, 18, b -> { renaming = true; rebuildWidgets(); }));
            }
        }
        int gap = 8;
        int x = this.leftPos + (PANEL_WIDTH - btnWidth) / 2;
        int travelY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        int upperY = travelY - PADDING - 20;
        if (ownDimensionExists()) {
            // 已创建：提供"删除维度"入口（点击进入长按确认）
            this.addRenderableWidget(new FlatButton(
                    Component.translatable("gui.personaldimension.controller.delete.button"),
                    this.leftPos + PANEL_WIDTH - PADDING - 78, contentTop() + PADDING + 22, 78, 18, b -> beginDelete()));
        } else {
            // 未创建：提供"创建维度"入口
            this.addRenderableWidget(new FlatButton(
                    Component.translatable("gui.personaldimension.controller.create.button"),
                    x, upperY, btnWidth, 20, b -> openCreateMenu()));
        }
        this.travelButton = new FlatButton(travelButtonMessage(), x, travelY, btnWidth, 20, b -> requestTravel());
        this.addRenderableWidget(this.travelButton);
    }

    /**
     * 删除确认态的"确定 / 取消"按钮。
     * "确定"为 {@link ModernHoldButton}：按住时红色进度从左到右填充，满后触发并闪烁一次表示已执行，
     * 松手或移出按钮则进度回落——比 5 秒倒计时更直观且可中断。取消随时可点，恢复正常信息页。
     */
    private void saveShelterName() {
        if (shelterNameInput == null) return;
        Platform.sendToServer(new SetShelterNamePacket(shelterNameInput.getValue()));
        renaming = false;
        rebuildWidgets();
    }

    private void addDeleteConfirmButtons() {
        int btnWidth = 69;
        int gap = 8;
        int totalWidth = btnWidth * 2 + gap;
        int x = this.leftPos + (PANEL_WIDTH - totalWidth) / 2;
        int y = this.topPos + PANEL_HEIGHT - PADDING - 20;
        this.deleteConfirmButton = new ModernHoldButton(
                Component.translatable("gui.personaldimension.controller.delete.confirm"),
                x, y, btnWidth, 20, b -> confirmDelete());
        this.addRenderableWidget(this.deleteConfirmButton);
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.controller.delete.cancel"),
                x + btnWidth + gap, y, btnWidth, 20, b -> cancelDelete()));
    }

    /** 进入删除个人维度的确认态：标记目标并重建控件切到确认界面。 */
    private void beginDelete() {
        beginDeleteConfirm(DeleteTarget.PERSONAL);
    }

    /** 进入删除公共维度的确认态：标记目标并重建控件切到确认界面。 */
    private void beginDeletePublic() {
        beginDeleteConfirm(DeleteTarget.PUBLIC);
    }

    /** 通用入口：进入删除确认态。 */
    private void beginDeleteConfirm(DeleteTarget target) {
        this.confirmingDelete = true;
        this.deleteTarget = target;
        this.rebuildWidgets();
    }

    /** 取消删除：退出确认态并重建回正常信息页。 */
    private void cancelDelete() {
        this.confirmingDelete = false;
        this.deleteTarget = null;
        this.rebuildWidgets();
    }

    /** 确认删除：长按满进度触发，根据目标下发对应删除包并关闭界面。 */
    private void confirmDelete() {
        if (this.deleteTarget == DeleteTarget.PUBLIC) {
            Platform.sendToServer(new PublicDimensionPacket(PublicDimensionPacket.Action.DELETE));
        } else {
            Platform.sendToServer(new DeletePersonalDimensionPacket());
        }
        this.onClose();
    }

    /** 玩家自己的个人维度是否已创建（依据客户端已知维度集合）。 */
    private boolean ownDimensionExists() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && mc.player.connection.levels().contains(Platform.levelKeyFor(mc.player.getUUID()));
    }

    /** 公共维度是否已创建（依据客户端已知维度集合）。 */
    private boolean publicDimensionExists() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && mc.player.connection.levels().contains(Platform.publicLevelKey());
    }

    /** 玩家是否为 OP（权限等级 >= 2）。 */
    private boolean isOp() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    /** 进入公共维度。 */
    private void enterPublic() {
        Platform.sendToServer(new PublicDimensionPacket(PublicDimensionPacket.Action.ENTER));
        this.onClose();
    }

    /** 退出公共维度：返回进入前的 outside 记忆点。 */
    private void exitPublic() {
        Platform.sendToServer(new PublicDimensionPacket(PublicDimensionPacket.Action.EXIT));
        this.onClose();
    }

    /** 玩家当前是否处于公共维度。 */
    private boolean isInPublicDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && Platform.isPublicDimension(mc.player.level().dimension());
    }

    /** 创建公共维度：打开创建容器界面（与个人维度共用，服务端通过菜单标志区分）。 */
    private void createPublic() {
        Platform.sendToServer(new OpenCreateMenuPacket(true));
    }

    /** 请求打开"创建维度"容器界面：由服务端 openMenu 打开（内含生成器槽）。 */
    private void openCreateMenu() {
        Platform.sendToServer(new OpenCreateMenuPacket());
    }

    /** 按钮文字：身处模组维度（个人或公共）显示"退出"，否则显示"进入"。 */
    private Component travelButtonMessage() {
        Minecraft mc = Minecraft.getInstance();
        boolean inModDim = mc.player != null
            && Platform.isModDimension(mc.player.level().dimension());
        return Component.translatable(inModDim
            ? "gui.personaldimension.controller.travel.exit"
            : "gui.personaldimension.controller.travel.enter");
    }

    /** 请求进出：发送无参传送包由服务端裁决，随后关闭界面以便玩家看到传送结果。 */
    private void requestTravel() {
        Platform.sendToServer(new PersonalDimensionTravelPacket());
        this.onClose();
    }

    /** 设置主页：各分组入口（时间 / 天气 / 月相 / 生物生成控制，后续可在此追加游戏规则等）。 */
    private void addSettingsHome() {
        int contentTop = contentTop();
        int btnWidth = 146;
        int x = this.leftPos + (PANEL_WIDTH - btnWidth) / 2;
        int firstY = contentTop + PADDING;
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.controller.settings.time_entry"),
                x, firstY, btnWidth, 20,
                b -> openSettingsPage(SettingsPage.TIME)));
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.controller.settings.weather_entry"),
                x, firstY + 20 + PADDING, btnWidth, 20,
                b -> openSettingsPage(SettingsPage.WEATHER)));
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.controller.settings.moon_phase_entry"),
                x, firstY + 2 * (20 + PADDING), btnWidth, 20,
                b -> openSettingsPage(SettingsPage.MOON_PHASE)));
        this.addRenderableWidget(new FlatButton(Component.literal("介面選擇"),
                x, firstY + 3 * (20 + PADDING), btnWidth, 20,
                b -> openSettingsPage(SettingsPage.THEME)));
        if (ownDimensionExists()) {
            boolean locked = com.ruadou.personaldimension.client.ClientDimensionInfo.info() != null
                && com.ruadou.personaldimension.client.ClientDimensionInfo.info().shelterEntryLocked();
            lastEntryLockState = locked;
            FlatButton entryLockToggle = new FlatButton(
                Component.literal("進入點鎖定：" + (locked ? "是" : "否")),
                contentLeft(), firstY + 5 * (20 + PADDING), contentWidth(), 20,
                button -> {
                    boolean currentLocked = com.ruadou.personaldimension.client.ClientDimensionInfo.info() != null
                        && com.ruadou.personaldimension.client.ClientDimensionInfo.info().shelterEntryLocked();
                    Platform.sendToServer(new SetShelterEntryLockPacket(!currentLocked));
                });
            entryLockToggle.setSelected(locked);
            this.addRenderableWidget(entryLockToggle);
        }
        // 土地電台設定獨立於全服聊天及廣播；此頁僅讀取伺服器權威狀態。
        if (ownDimensionExists()) {
            this.addRenderableWidget(new FlatButton(Component.literal("私人土地電台"),
                contentLeft(), firstY + 6 * (20 + PADDING), contentWidth(), 20,
                b -> openSettingsPage(SettingsPage.RADIO)));
        }
        // 生物生成控制需读写本人维度的规则存储，维度未创建时不提供入口
        if (ownDimensionExists()) {
            this.addRenderableWidget(new FlatButton(
                    Component.translatable("gui.personaldimension.controller.settings.shelter_upgrade"),
                    x, firstY + 4 * (20 + PADDING), btnWidth, 20,
                    b -> Platform.sendToServer(new OpenShelterUpgradePacket())));
        }
    }

    /** 電台編輯畫面使用獨立的輸入欄，無論玩家在哪裡，寫入的都是自己的土地。 */
    private void addRadioEditor() {
        int x = contentLeft() + PADDING, y = contentTop() + PADDING;
        int width = contentWidth() - 2 * PADDING;
        this.addRenderableWidget(new FlatButton(Component.literal("返回電台清單"),
                x, y, width, 20, b -> { radioEditingId = ""; radioEditorError = ""; rebuildWidgets(); }));
        var editing = ClientLandRadioData.customStations().stream()
                .filter(st -> st.id().equals(radioEditingId)).findFirst().orElse(null);
        this.radioNameInput = new EditBox(this.font, x, y + 43, width, 20, Component.literal("電台名稱"));
        this.radioNameInput.setMaxLength(LandRadioSettings.MAX_NAME_LENGTH);
        this.radioNameInput.setHint(Component.literal("輸入電台名稱"));
        if (editing != null) this.radioNameInput.setValue(editing.name());
        this.addRenderableWidget(radioNameInput);
        this.radioUrlInput = new EditBox(this.font, x, y + 93, width, 20, Component.literal("串流網址"));
        this.radioUrlInput.setMaxLength(LandRadioSettings.MAX_URL_LENGTH);
        this.radioUrlInput.setHint(Component.literal("http:// 或 https:// 串流網址"));
        if (editing != null) this.radioUrlInput.setValue(editing.streamUrl());
        this.addRenderableWidget(radioUrlInput);
        this.addRenderableWidget(new FlatButton(Component.literal(editing == null ? "新增電台" : "儲存修改"),
                x, y + 127, width, 20, b -> {
            String name = radioNameInput.getValue().trim();
            String url = radioUrlInput.getValue().trim();
            if (name.isEmpty() || name.length() > LandRadioSettings.MAX_NAME_LENGTH
                    || !LandRadioSettings.isValidStreamUrl(url)) {
                radioEditorError = "請輸入有效的電台名稱與 HTTP／HTTPS 串流網址";
                return;
            }
            radioEditorError = "";
            Platform.sendToServer(new EditLandRadioStationPacket(
                    editing == null ? "add" : "edit", editing == null ? "" : radioEditingId, name, url));
            radioEditingId = "";
            rebuildWidgets();
        }));
    }

    /** 內建與自訂電台共用分頁，伺服器端再次驗證 ID 與地主身分。 */
    private void addRadioSettings() {
        if (!radioEditingId.isBlank()) { addRadioEditor(); return; }
        int x = contentLeft() + PADDING;
        int y = contentTop() + PADDING;
        int width = contentWidth() - 2 * PADDING;
        int half = (width - 6) / 2;
        this.addRenderableWidget(new FlatButton(Component.literal("返回設定"),
                x, y, half, 20, b -> backToSettingsHome()));
        this.addRenderableWidget(new FlatButton(Component.literal("重新整理"),
                x + half + 6, y, half, 20,
                b -> Platform.sendToServer(new RequestLandRadioPacket())));
        this.addRenderableWidget(new FlatButton(Component.literal("關閉土地電台"),
                x, y + 25, width, 20,
                b -> Platform.sendToServer(new SetLandRadioPacket(false, ClientLandRadioData.stationId()))));
        this.addRenderableWidget(new FlatButton(Component.literal("內建電台"),
                x, y + 49, half, 20, b -> { radioCustomPage = false; radioPage = 0; rebuildWidgets(); }));
        this.addRenderableWidget(new FlatButton(Component.literal("管理自訂電台"),
                x + half + 6, y + 49, half, 20,
                b -> { radioCustomPage = true; radioPage = 0; rebuildWidgets(); }));
        int pageSize = 3;
        var custom = ClientLandRadioData.customStations();
        int count = radioCustomPage ? custom.size() : DefaultRadioStations.ENTRIES.size();
        int pageCount = Math.max(1, (count + pageSize - 1) / pageSize);
        radioPage = Math.max(0, Math.min(radioPage, pageCount - 1));
        for (int index = radioPage * pageSize; index < Math.min(count, (radioPage + 1) * pageSize); index++) {
            int row = index % pageSize;
            String id = radioCustomPage ? custom.get(index).id() : DefaultRadioStations.ENTRIES.get(index).id();
            String name = radioCustomPage ? custom.get(index).name() : DefaultRadioStations.ENTRIES.get(index).name();
            int rowY = y + 74 + row * 25;
            int selectWidth = radioCustomPage ? width - 86 : width;
            FlatButton selection = new FlatButton(Component.literal(name),
                    x, rowY, selectWidth, 20,
                    b -> Platform.sendToServer(new SetLandRadioPacket(true, id)));
            selection.setSelected(ClientLandRadioData.enabled() && id.equals(ClientLandRadioData.stationId()));
            this.addRenderableWidget(selection);
            if (radioCustomPage) {
                this.addRenderableWidget(new FlatButton(Component.literal("編輯"),
                        x + width - 82, rowY, 39, 20,
                        b -> { radioEditingId = id; rebuildWidgets(); }));
                this.addRenderableWidget(new FlatButton(Component.literal("刪除"),
                        x + width - 40, rowY, 40, 20,
                        b -> Platform.sendToServer(new EditLandRadioStationPacket("delete", id, "", ""))));
            }
        }
        FlatButton previous = new FlatButton(Component.literal("上一頁"),
                x, y + 153, half, 20, b -> { radioPage--; rebuildWidgets(); });
        previous.active = radioPage > 0;
        this.addRenderableWidget(previous);
        FlatButton next = new FlatButton(Component.literal("下一頁"),
                x + half + 6, y + 153, half, 20, b -> { radioPage++; rebuildWidgets(); });
        next.active = radioPage + 1 < pageCount;
        this.addRenderableWidget(next);
        if (radioCustomPage) {
            FlatButton add = new FlatButton(Component.literal("新增電台（" + custom.size() + "/32）"),
                    x, y + 177, half, 19,
                    b -> { radioEditingId = "new"; radioEditorError = ""; rebuildWidgets(); });
            add.active = custom.size() < LandRadioSettings.MAX_STATIONS;
            this.addRenderableWidget(add);
            // Keep the personal listening toggle accessible on both station tabs.
            this.addRenderableWidget(new FlatButton(Component.literal("收聽：" + (ClientRadioListening.isListening() ? "是" : "否")),
                    x + half + 6, y + 177, half, 19,
                    b -> { ClientRadioListening.setListening(!ClientRadioListening.isListening()); rebuildWidgets(); }));
        } else {
            this.addRenderableWidget(new FlatButton(Component.literal("收聽：" + (ClientRadioListening.isListening() ? "是" : "否")),
                    x, y + 177, width, 20,
                    b -> { ClientRadioListening.setListening(!ClientRadioListening.isListening()); rebuildWidgets(); }));
        }
        this.addRenderableWidget(new FlatButton(Component.literal("－"),
                x, y + 202, 38, 16,
                b -> { ClientRadioVolume.set(ClientRadioVolume.get() - 1); rebuildWidgets(); }));
        addVolumeSlider(x + 42, y + 202, width - 84, 16);
        this.addRenderableWidget(new FlatButton(Component.literal("＋"),
                x + width - 38, y + 202, 38, 16,
                b -> { ClientRadioVolume.set(ClientRadioVolume.get() + 1); rebuildWidgets(); }));
    }

    /** Local, persistent color theme chooser. */
    private void addThemeSelection() {
        int x = contentLeft() + PADDING;
        int y = contentTop() + PADDING + 38;
        int themeGap = 6;
        int themeWidth = (contentWidth() - 2 * PADDING - themeGap) / 2;
        String[] names = {"預設", "白色", "Windows 藍", "粉紅色", "草綠色", "淺藍色", "黑色", "廢土風格", "異塵餘生風格", "魔法空間風格"};
        this.addRenderableWidget(new FlatButton(Component.literal("返回設定"),
            x, contentTop() + PADDING, themeWidth, 16, b -> backToSettingsHome()));
        this.addRenderableWidget(new FlatButton(Component.literal("字體放大：" + (ModernStyle.largeText() ? "開" : "關")),
            x + themeWidth + themeGap, contentTop() + PADDING, themeWidth, 16, b -> {
                ModernStyle.setLargeText(!ModernStyle.largeText());
                rebuildWidgets();
            }));
        for (int i = 0; i < names.length; i++) {
            final int choice = i;
            int row = i / 2, col = i % 2;
            FlatButton button = new FlatButton(Component.literal(names[i]),
                x + col * (themeWidth + themeGap), y + row * 27, themeWidth, 21, b -> {
                    ModernStyle.applyTheme(choice);
                    rebuildWidgets();
                });
            button.setSelected(ModernStyle.themeIndex() == i);
            this.addRenderableWidget(button);
        }
    }

    /** 好友主页：好友管理 / 访问维度两个入口（打开独立界面，关闭后返回本控制器）。 */
    private void addFriendsHome() {
        int contentTop = contentTop();
        int btnWidth = 160;
        int x = this.leftPos + (PANEL_WIDTH - btnWidth) / 2;
        int firstY = contentTop + PADDING;
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.friend.manage"),
                x, firstY, btnWidth, 20,
                b -> this.minecraft.setScreen(new FriendManagerScreen(this))));
        this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.visit.entry"),
                x, firstY + 20 + PADDING, btnWidth, 20,
                b -> this.minecraft.setScreen(new VisitScreen(this))));
    }

    private void selectTab(Tab tab) {
        if (this.activeTab != tab) {
            this.activeTab = tab;
            this.settingsPage = SettingsPage.HOME; // 切换一级页时回到设置主页
            this.confirmingDelete = false;         // 离开信息页时退出删除确认态
            this.teleportPanel = null;             // 切走时清掉传送面板，切回来时重建
            this.rebuildWidgets();
        }
    }

    private void openSettingsPage(SettingsPage page) {
        if (this.settingsPage != page) {
            this.settingsPage = page;
            if (page == SettingsPage.RADIO) {
                ClientLandRadioData.clear();
            }
            this.rebuildWidgets();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (activeTab == Tab.SETTINGS && settingsPage == SettingsPage.RADIO
                && lastRadioRevision != ClientLandRadioData.revision()) {
            lastRadioRevision = ClientLandRadioData.revision();
            if (radioNameInput == null) rebuildWidgets();
        }
        if (!isVisitingFriendLand() && activeTab == Tab.SETTINGS && settingsPage == SettingsPage.HOME
            && ownDimensionExists() && com.ruadou.personaldimension.client.ClientDimensionInfo.info() != null) {
            boolean locked = com.ruadou.personaldimension.client.ClientDimensionInfo.info().shelterEntryLocked();
            if (lastEntryLockState == null || lastEntryLockState.booleanValue() != locked) {
                lastEntryLockState = locked;
                rebuildWidgets();
            }
        }
        // 定期向服务端请求个人维度信息（含仅服务端持有的时刻/计时字段）
        if (this.refreshCounter <= 0) {
            requestInfo();
            if (this.teleportPanel != null) {
                this.teleportPanel.requestList();
            }
            this.refreshCounter = REFRESH_INTERVAL;
        } else {
            this.refreshCounter--;
        }
        // 设置子页（时间 / 天气 / 月相）自行刷新其按钮文字与门控
        if (this.activePanel != null) {
            this.activePanel.tick();
        }
        // 传送面板刷新（列表变化时触发重建）
        if (this.teleportPanel != null) {
            this.teleportPanel.tick();
        }
        // 进出按钮文字随玩家当前所处维度刷新（跨维度移动后同步显示进入/退出）
        if (this.travelButton != null) {
            this.travelButton.setMessage(travelButtonMessage());
        }
        // 删除确认使用 ModernHoldButton 自管理进度（按住满触发），无需 tick 驱动
    }

    private void requestInfo() {
        if (Minecraft.getInstance().getConnection() != null) {
            Platform.sendToServer(new RequestDimensionInfoPacket());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 设置子页优先处理（如时间输入框聚焦时回车应用）
        if (renaming && shelterNameInput != null && (keyCode == 257 || keyCode == 335)) {
            saveShelterName();
            return true;
        }
        if (this.activePanel != null && this.activePanel.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 顶部横向标签栏：贴合面板顶边绘制信息 / 设置 / 好友三个标签。
     * 选中标签以略亮底色凸起、左侧带强调竖条，并向下盖住分隔线与正文相连（浏览器 / 记事本风）；
     * 未选中标签为淡色薄片，悬停提亮。
     */
    @Override
    protected void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isVisitingFriendLand()) {
            ModernStyle.fillRoundTop(graphics, this.leftPos + 1, this.topPos + 1,
                PANEL_WIDTH - 2, HEADER_HEIGHT, ModernStyle.CHAMFER, ModernStyle.HEADER_BG);
            graphics.fill(this.leftPos + 1, this.topPos + HEADER_HEIGHT,
                this.leftPos + PANEL_WIDTH - 1, this.topPos + HEADER_HEIGHT + 1, ModernStyle.DIVIDER);
            graphics.drawString(this.font, Component.literal("好友私人土地｜訪客資訊"),
                this.leftPos + PADDING, this.topPos + 7, ModernStyle.TEXT, false);
            return;
        }
        // 标题栏底 + 分隔线（分隔线随后被选中标签盖住形成"融合"）
        ModernStyle.fillRoundTop(graphics, this.leftPos + 1, this.topPos + 1, PANEL_WIDTH - 2,
            HEADER_HEIGHT, ModernStyle.CHAMFER, ModernStyle.HEADER_BG);
        int dividerY = this.topPos + HEADER_HEIGHT;
        graphics.fill(this.leftPos + 1, dividerY, this.leftPos + PANEL_WIDTH - 1, dividerY + 1, ModernStyle.DIVIDER);

        for (Tab tab : Tab.values()) {
            int tx = tabX(tab);
            int tw = tabWidth(tab);
            boolean active = (tab == this.activeTab);
            boolean hover = mouseX >= tx && mouseX < tx + tw
                && mouseY >= this.topPos + 1 && mouseY < dividerY;
            if (active) {
                // 选中：略亮凸起底（向下越过分隔线 1px 与正文相连）+ 左侧强调竖条
                ModernStyle.fillRoundTop(graphics, tx, this.topPos + 2, tw, HEADER_HEIGHT - 1,
                    ModernStyle.CHAMFER, ModernStyle.CARD_BG);
                graphics.fill(tx + 4, this.topPos + 5, tx + 6, this.topPos + 14, ModernStyle.ACCENT);
            } else {
                // 未选中：淡色薄片（悬停提亮）
                ModernStyle.fillRoundTop(graphics, tx, this.topPos + 3, tw, HEADER_HEIGHT - 3,
                    ModernStyle.CHAMFER, hover ? ModernStyle.GHOST_FILL_HOVER : ModernStyle.GHOST_FILL);
            }
            graphics.drawString(this.font, Component.translatable(tab.titleKey),
                tx + TAB_TEXT_LEFT, this.topPos + 6, active ? ModernStyle.TEXT : ModernStyle.TEXT_SUB, false);
        }
    }

    /** 标签宽度：文字左内缩 + 文字宽 + 右留白。 */
    private int tabWidth(Tab tab) {
        return TAB_TEXT_LEFT + this.font.width(Component.translatable(tab.titleKey)) + TAB_PAD_RIGHT;
    }

    /** 标签左端 x：从标签栏起点起累加前序标签宽度与间距。 */
    private int tabX(Tab tab) {
        int x = this.leftPos + TAB_STRIP_LEFT;
        for (Tab t : Tab.values()) {
            if (t == tab) {
                return x;
            }
            x += tabWidth(t) + TAB_GAP;
        }
        return x;
    }

    /** 信息页正文（正常态显示信息，删除确认态显示警示）。 */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isVisitingFriendLand()) {
            graphics.drawString(this.font, Component.literal("好友私人土地"), contentLeft() + 8,
                contentTop() + 22, ModernStyle.TEXT, false);
            graphics.drawString(this.font, Component.literal("訪客模式：無法管理土地設定"), contentLeft() + 8,
                contentTop() + 44, ModernStyle.TEXT_SUB, false);
            return;
        }
        if (this.activeTab == Tab.INFO) {
            if (this.confirmingDelete) {
                renderDeleteConfirm(graphics, this.leftPos + PADDING, contentTop() + PADDING);
            } else {
                ControllerInfoText.render(graphics, this.font, this.leftPos + PADDING, contentTop() + PADDING, contentWidth());
            }
        } else if (this.activeTab == Tab.SETTINGS && this.settingsPage == SettingsPage.RADIO) {
            int x = contentLeft() + PADDING;
            if (!radioEditingId.isBlank()) {
                int y = contentTop() + PADDING;
                graphics.drawString(this.font, Component.literal("電台名稱"), x, y + 30, ModernStyle.TEXT, false);
                graphics.drawString(this.font, Component.literal("串流網址"), x, y + 80, ModernStyle.TEXT, false);
                if (!radioEditorError.isEmpty()) {
                    graphics.drawString(this.font, Component.literal("輸入內容無效，請檢查名稱與網址"), x, y + 157, ModernStyle.DANGER, false);
                }
                return;
            }
            int y = contentTop() + PADDING + 211;
            graphics.drawString(this.font, Component.literal("私人土地電台：" + (ClientLandRadioData.enabled() ? "開啟" : "關閉")),
                x, y, ModernStyle.TEXT, false);
            String id = ClientLandRadioData.stationId();
            graphics.drawString(this.font, Component.literal("目前頻道：" + (id.isBlank() ? "未選擇" : id)),
                x, y + 12, ModernStyle.TEXT_SUB, false);
            graphics.drawString(this.font, Component.literal("播放狀態：" + ClientRadioPlaybackStatus.get()),
                x, y + 24, ModernStyle.TEXT_SUB, false);

        } else if (this.activeTab == Tab.TELEPORT && this.teleportPanel != null) {
            this.teleportPanel.render(graphics, mouseX, mouseY);
        }
    }

    /** 命中检测：返回鼠标所在的顶部横向标签，否则 null（仅标题栏高度范围内命中）。 */
    private Tab tabAt(double mouseX, double mouseY) {
        if (mouseY < this.topPos + 1 || mouseY >= this.topPos + HEADER_HEIGHT) {
            return null;
        }
        for (Tab tab : Tab.values()) {
            int tx = tabX(tab);
            if (mouseX >= tx && mouseX < tx + tabWidth(tab)) {
                return tab;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisitingFriendLand()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            Tab clicked = tabAt(mouseX, mouseY);
            if (clicked != null) {
                selectTab(clicked);
                return true;
            }
        }
        if (this.teleportPanel != null && this.teleportPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.teleportPanel != null) {
            return this.teleportPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.teleportPanel != null && this.teleportPanel.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.teleportPanel != null) {
            this.teleportPanel.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 删除确认正文：根据 deleteTarget 选择对应警示文案（个人 / 公共），红色标题 + 折行后果说明。 */
    private void renderDeleteConfirm(GuiGraphics graphics, int x, int y) {
        int warnColor = ModernStyle.DANGER; // 红色警示
        int lineHeight = this.font.lineHeight + 4;
        boolean isPublic = this.deleteTarget == DeleteTarget.PUBLIC;
        String titleKey = isPublic
            ? "gui.personaldimension.controller.delete.prompt_title_public"
            : "gui.personaldimension.controller.delete.prompt_title";
        String warnKey = isPublic
            ? "gui.personaldimension.controller.delete.prompt_warning_public"
            : "gui.personaldimension.controller.delete.prompt_warning";
        graphics.drawString(this.font, Component.translatable(titleKey), x, y, warnColor, false);
        int textY = y + lineHeight + 4;
        for (var line : this.font.split(Component.translatable(warnKey), PANEL_WIDTH - PADDING * 2)) {
            graphics.drawString(this.font, line, x, textY, TEXT_COLOR, false);
            textY += lineHeight;
        }
    }
}
