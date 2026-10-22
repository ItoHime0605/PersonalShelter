package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientSpawnRules;
import com.ruadou.personaldimension.network.ModifySpawnEntityPacket;
import com.ruadou.personaldimension.network.RequestSpawnRulesPacket;
import com.ruadou.personaldimension.network.SetSpawnFilterModePacket;
import com.ruadou.personaldimension.network.SetSpawnSourceScopePacket;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.spawn.SpawnFilterMode;
import com.ruadou.personaldimension.spawn.SpawnSourceScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/**
 * 生物生成控制界面（维度主人）。
 *
 * <p>顶部为"名单模式"（黑/白名单）与"拦截来源"（全部 / 仅自然刷怪）两个切换按钮；下方为搜索框。
 * 再下方分两栏：左栏为可滚动实体列表，每行由「窄名称按钮」+「√/✗ 开关」组成；右栏为选中实体的
 * 3D 模型预览（点击名称即选中预览，实体转头朝向鼠标）。<b>点击名称仅切换预览、不改名单</b>，
 * 加入/移出名单只由行内的 √/✗ 开关负责。名单内的实体在列表中<b>置顶</b>：勾选后不立即置顶，
 * 而是先停顿一小会，再一格一格向上爬升到目标位置（见 {@link #advanceReorderAnim()}）。
 * 所有改动经网络包下发，仅作用于发送者本人维度，服务端权威。
 *
 * <p><b>焦点约束</b>：列表含搜索 {@link EditBox}，搜索/滚动/周期刷新时<b>不能</b>调用
 * {@link #rebuildWidgets()}（会重建输入框并丢失焦点）。因此本界面创建固定数量的行控件，
 * 仅通过 {@link #refreshRows()} 就地更新其文字/选中态/可见性，输入框始终保持存活。
 */
public class SpawnControlScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_H = 18;
    /** 左栏（实体列表）总宽。 */
    private static final int LIST_W = 172;
    /** 行内 √/✗ 开关按钮宽。 */
    private static final int TOGGLE_W = 18;
    /** 名称按钮与开关按钮之间的间隙。 */
    private static final int ROW_GAP = 2;

    /** 勾选后爬升前的停顿时长（tick）；约 0.4s，先给用户即时的 √ 反馈再动。 */
    private static final int ANIM_PAUSE_TICKS = 8;
    /** 两次相邻上移之间的间隔（tick）；越大爬升越慢、越"一点一点"。 */
    private static final int ANIM_STEP_TICKS = 1;

    /** 一个可选实体候选：注册 id + 本地化显示名。 */
    private record Candidate(ResourceLocation id, Component name) {}

    private List<Candidate> candidates;          // 全部候选（构建一次）
    private List<Candidate> filtered = new ArrayList<>(); // 当前显示顺序（爬升动画就地改写）
    private int scrollOffset;
    private boolean draggingBar;
    /** 名单集合快照，用于在名单变化时触发重排（置顶）。 */
    private Set<ResourceLocation> lastInList = Set.of();

    /** 目标次序：实体 id → 在"置顶+按名排序"结果中的下标，爬升动画向此收敛。 */
    private final Map<ResourceLocation, Integer> targetRank = new HashMap<>();
    /** 爬升前停顿倒计时（tick），>0 期间保持原位不动。 */
    private int animPause;
    /** 距下一次相邻上移的间隔倒计时（tick）。 */
    private int animStepCd;
    /** 标记"本次名单变化由用户勾选触发"（走爬升动画）；区别于初次加载/外部同步（直接快照）。 */
    private boolean expectUserToggle;

    // 右侧预览：当前选中的实体 id / 名称，及缓存的预览实体实例（仅在选中变化时重建）
    private ResourceLocation selectedId;
    private Component selectedName;
    private LivingEntity previewEntity;

    private EditBox searchBox;
    private FlatButton modeButton;
    private FlatButton scopeButton;
    private FlatButton[] nameButtons;
    private FlatButton[] toggleButtons;

    public SpawnControlScreen(Screen parent) {
        super(Component.translatable("gui.personaldimension.spawn.title"), parent);
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestSpawnRulesPacket());
    }

    @Override
    protected void buildWidgets() {
        if (this.candidates == null) {
            buildCandidates();
        }
        int x = contentLeft();
        int w = contentWidth();
        int half = (w - 4) / 2;

        // 顶部：名单模式 / 拦截来源 两个切换按钮
        int topY = this.topPos + 20;
        this.modeButton = new FlatButton(modeLabel(), x, topY, half, 20, b -> toggleMode());
        this.scopeButton = new FlatButton(scopeLabel(), x + half + 4, topY, w - half - 4, 20, b -> toggleScope());
        this.addRenderableWidget(this.modeButton);
        this.addRenderableWidget(this.scopeButton);

        // 搜索框
        int searchY = topY + 24;
        this.searchBox = new EditBox(this.font, x, searchY, w, 18,
            Component.translatable("gui.personaldimension.spawn.search_hint"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.personaldimension.spawn.search_hint"));
        this.searchBox.setResponder(s -> onSearchChanged());
        this.addRenderableWidget(this.searchBox);

        // 左栏固定数量的列表行：名称按钮 + √/✗ 开关，内容由 refreshRows 就地填充
        int listTop = listTop();
        int nameW = LIST_W - TOGGLE_W - ROW_GAP - ScrollBar.WIDTH - ScrollBar.GAP;
        this.nameButtons = new FlatButton[VISIBLE_ROWS];
        this.toggleButtons = new FlatButton[VISIBLE_ROWS];
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int rowIndex = i;
            int rowY = listTop + i * ROW_H;
            FlatButton name = new FlatButton(Component.empty(),
                x, rowY, nameW, ROW_H - 2, b -> selectRow(rowIndex));
            FlatButton toggle = new FlatButton(Component.empty(),
                x + nameW + ROW_GAP, rowY, TOGGLE_W, ROW_H - 2, b -> onToggleClicked(rowIndex));
            this.nameButtons[i] = name;
            this.toggleButtons[i] = toggle;
            this.addRenderableWidget(name);
            this.addRenderableWidget(toggle);
        }

        // 返回
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, this.topPos + PANEL_HEIGHT - PADDING - 20, w, 20, b -> back()));

        applyFilter();
        refreshRows();
    }

    /** 构建全部可选实体候选：排除 {@link MobCategory#MISC}（掉落物、投射物、船矿车等非生物）。 */
    private void buildCandidates() {
        List<Candidate> list = new ArrayList<>();
        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if (entry.getValue().getCategory() == MobCategory.MISC) {
                continue;
            }
            list.add(new Candidate(entry.getKey().location(), entry.getValue().getDescription()));
        }
        this.candidates = list;
    }

    /**
     * 依据搜索框文本过滤候选（匹配注册 id 或显示名，忽略大小写），并<b>立即</b>按目标次序排列
     * （名单内置顶、其余按显示名排序）。用于初始化与搜索变化——此类变化直接快照、不走爬升动画。
     */
    private void applyFilter() {
        String q = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> inList = ClientSpawnRules.entities();
        List<Candidate> result = new ArrayList<>();
        for (Candidate c : this.candidates) {
            if (q.isEmpty()
                || c.id().toString().toLowerCase(Locale.ROOT).contains(q)
                || c.name().getString().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(c);
            }
        }
        result.sort(listOrder(inList));
        this.filtered = result;
        this.lastInList = new HashSet<>(inList);
        indexTarget(result);
        this.animPause = 0;
        this.animStepCd = 0;
    }

    /** 目标排序规则：名单内置顶（0）其余在后（1），组内按显示名不区分大小写。 */
    private static Comparator<Candidate> listOrder(Set<ResourceLocation> inList) {
        return Comparator
            .comparingInt((Candidate c) -> inList.contains(c.id()) ? 0 : 1)
            .thenComparing(c -> c.name().getString(), String.CASE_INSENSITIVE_ORDER);
    }

    /** 记录目标次序：把已排序列表的下标写入 {@link #targetRank}，供爬升动画判定逆序对。 */
    private void indexTarget(List<Candidate> sorted) {
        this.targetRank.clear();
        for (int i = 0; i < sorted.size(); i++) {
            this.targetRank.put(sorted.get(i).id(), i);
        }
    }

    /** 搜索文本变化：重算过滤结果并回到顶部，就地刷新行（不重建控件）。 */
    private void onSearchChanged() {
        applyFilter();
        this.scrollOffset = 0;
        refreshRows();
    }

    /** 就地更新可见行的文字 / 选中态 / 可见性，输入框与按钮保持存活。 */
    private void refreshRows() {
        if (this.nameButtons == null) {
            return;
        }
        int size = this.filtered.size();
        int maxOffset = Math.max(0, size - VISIBLE_ROWS);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxOffset));
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            FlatButton name = this.nameButtons[i];
            FlatButton toggle = this.toggleButtons[i];
            int idx = this.scrollOffset + i;
            if (idx < size) {
                Candidate c = this.filtered.get(idx);
                name.visible = true;
                name.active = true;
                name.setMessage(c.name());
                name.setSelected(c.id().equals(this.selectedId)); // 金边=当前预览项
                toggle.visible = true;
                toggle.active = true; // √/✗ 图标由 renderOverlay 叠加绘制
            } else {
                name.visible = false;
                toggle.visible = false;
            }
        }
    }

    /** 点击名称：仅选中该实体作右侧预览，不改动名单。 */
    private void selectRow(int rowIndex) {
        int idx = this.scrollOffset + rowIndex;
        if (idx < 0 || idx >= this.filtered.size()) {
            return;
        }
        Candidate c = this.filtered.get(idx);
        if (c.id().equals(this.selectedId)) {
            return;
        }
        this.selectedId = c.id();
        this.selectedName = c.name();
        rebuildPreviewEntity();
        refreshRows(); // 立即更新金边指示
    }

    /** 点击行内 √/✗ 开关：把对应实体加入/移出名单（依当前是否在名单中翻转），服务端回传后刷新。 */
    private void onToggleClicked(int rowIndex) {
        int idx = this.scrollOffset + rowIndex;
        if (idx < 0 || idx >= this.filtered.size()) {
            return;
        }
        ResourceLocation id = this.filtered.get(idx).id();
        boolean inList = ClientSpawnRules.entities().contains(id);
        // 标记本次名单变化为用户勾选：服务端回传到达后走"停顿→爬升"动画（而非瞬间置顶）
        this.expectUserToggle = true;
        Platform.sendToServer(new ModifySpawnEntityPacket(id, !inList));
    }

    /**
     * 依当前 {@link #selectedId} 重建缓存的预览实体实例。
     * 用客户端世界创建（不加入世界，仅用于渲染），个别实体在客户端无法创建时预览留空。
     */
    private void rebuildPreviewEntity() {
        this.previewEntity = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || this.selectedId == null) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(this.selectedId);
        if (type == null) {
            return;
        }
        Entity created;
        try {
            created = type.create(mc.level);
        } catch (Exception e) {
            created = null; // 极少数实体在客户端脱离服务端上下文创建可能异常，预览失败即忽略
        }
        if (created instanceof LivingEntity living) {
            this.previewEntity = living;
        }
    }

    private void toggleMode() {
        SpawnFilterMode next = ClientSpawnRules.mode() == SpawnFilterMode.BLACKLIST
            ? SpawnFilterMode.WHITELIST : SpawnFilterMode.BLACKLIST;
        Platform.sendToServer(new SetSpawnFilterModePacket(next.ordinal()));
    }

    private void toggleScope() {
        SpawnSourceScope next = ClientSpawnRules.scope() == SpawnSourceScope.ALL_SOURCES
            ? SpawnSourceScope.NATURAL_ONLY : SpawnSourceScope.ALL_SOURCES;
        Platform.sendToServer(new SetSpawnSourceScopePacket(next.ordinal()));
    }

    private Component modeLabel() {
        String key = ClientSpawnRules.mode() == SpawnFilterMode.WHITELIST
            ? "gui.personaldimension.spawn.mode.whitelist"
            : "gui.personaldimension.spawn.mode.blacklist";
        return Component.translatable("gui.personaldimension.spawn.mode")
            .append(": ").append(Component.translatable(key));
    }

    private Component scopeLabel() {
        String key = ClientSpawnRules.scope() == SpawnSourceScope.NATURAL_ONLY
            ? "gui.personaldimension.spawn.scope.natural"
            : "gui.personaldimension.spawn.scope.all";
        return Component.translatable("gui.personaldimension.spawn.scope")
            .append(": ").append(Component.translatable(key));
    }

    @Override
    protected void onTick() {
        // 服务端回传经网络线程写入客户端缓存后，这里每 tick 就地刷新，反映最新模式/来源/名单
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
        }
        if (this.scopeButton != null) {
            this.scopeButton.setMessage(scopeLabel());
        }
        // 名单集合变化（增删实体）：用户勾选走"停顿→爬升"动画，其余（初次加载/外部同步）直接快照
        if (!ClientSpawnRules.entities().equals(this.lastInList)) {
            if (this.expectUserToggle) {
                this.expectUserToggle = false;
                beginReorderAnim();
            } else {
                applyFilter();
            }
        }
        advanceReorderAnim();
        refreshRows();
    }

    /**
     * 用户勾选后启动重排动画：算出新的目标次序（{@link #targetRank}）但<b>不动</b>当前显示顺序，
     * 仅置停顿计时。随后由 {@link #advanceReorderAnim()} 逐 tick 把 {@link #filtered} 向目标冒泡。
     */
    private void beginReorderAnim() {
        Set<ResourceLocation> inList = ClientSpawnRules.entities();
        this.lastInList = new HashSet<>(inList);
        List<Candidate> sorted = new ArrayList<>(this.filtered);
        sorted.sort(listOrder(inList));
        indexTarget(sorted);
        this.animPause = ANIM_PAUSE_TICKS;
        this.animStepCd = 0;
    }

    /**
     * 推进爬升动画：先耗尽停顿计时；之后每 {@link #ANIM_STEP_TICKS} 个 tick 修正一个相邻逆序对
     * （把靠后应下沉的元素与其后邻交换一次），使错位元素一格一格移向目标位；全部就位则静止。
     */
    private void advanceReorderAnim() {
        if (this.animPause > 0) {
            this.animPause--;
            return;
        }
        if (this.animStepCd > 0) {
            this.animStepCd--;
            return;
        }
        if (stepReorderOnce()) {
            this.animStepCd = ANIM_STEP_TICKS;
        }
    }

    /** 扫描到首个逆序对（前项目标下标 > 后项）即交换一次并返回 true；已完全有序返回 false。 */
    private boolean stepReorderOnce() {
        for (int i = 0; i + 1 < this.filtered.size(); i++) {
            if (rankOf(this.filtered.get(i)) > rankOf(this.filtered.get(i + 1))) {
                Collections.swap(this.filtered, i, i + 1);
                return true;
            }
        }
        return false;
    }

    /** 候选在目标次序中的下标；缺失（不应发生）按末尾处理。 */
    private int rankOf(Candidate c) {
        Integer r = this.targetRank.get(c.id());
        return r == null ? Integer.MAX_VALUE : r;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawPreview(graphics, mouseX, mouseY);

        int size = this.filtered.size();
        if (size == 0) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.spawn.empty"),
                contentLeft(), this.topPos + 74, ModernStyle.TEXT_SUB, false);
        } else if (size > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, size) + "/" + size),
                this.leftPos + PANEL_WIDTH - PADDING - 48, this.topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(size, VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, size);
        }
    }

    /** 滚动条轨道左沿 x：贴左栏列表右缘（与右侧预览框之间留白）。 */
    private int scrollbarX() {
        return contentLeft() + LIST_W - ScrollBar.WIDTH;
    }

    /** 列表行区顶部 y（= 搜索框下方，与预览框同高）。 */
    private int listTop() {
        return this.topPos + 66;
    }

    /** 绘制右栏预览：凹陷深色框 + 选中实体 3D 模型（转头朝向鼠标）+ 名称/提示。 */
    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        int x1 = previewLeft();
        int y1 = previewTop();
        int x2 = previewRight();
        int y2 = previewBottom();

        // 凹陷深色预览框（顶/左深边、底/右淡白亮边，在深色面板上形成内凹感）
        int frameBorder = ModernStyle.darken(ModernStyle.CARD_BG, 0.5f);
        graphics.fill(x1, y1, x2, y2, ModernStyle.CARD_BG);
        graphics.fill(x1, y1, x2, y1 + 1, frameBorder);
        graphics.fill(x1, y1, x1 + 1, y2, frameBorder);
        graphics.fill(x1, y2 - 1, x2, y2, 0x22FFFFFF);
        graphics.fill(x2 - 1, y1, x2, y2, 0x22FFFFFF);

        if (this.previewEntity != null) {
            // 缩放：使实体最大边约占预览框高的一半（与原版玩家在背包中 scale=30 的观感一致）
            float bb = Math.max(this.previewEntity.getBbWidth(), this.previewEntity.getBbHeight());
            int scale = Mth.clamp((int) (0.5F * (y2 - y1) / Math.max(bb, 0.1F)), 5, 60);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, x1 + 2, y1 + 2, x2 - 2, y2 - 2, scale, 0.0F,
                (float) mouseX, (float) mouseY, this.previewEntity);
            if (this.selectedName != null) {
                int cx = (x1 + x2) / 2;
                Component label = GuiText.ellipsize(this.font, this.selectedName, x2 - x1 - 4);
                graphics.drawString(this.font, label,
                    cx - this.font.width(label) / 2, y2 + 4, ModernStyle.TEXT_SUB, false);
            }
        } else {
            Component hint = Component.translatable("gui.personaldimension.spawn.preview_hint");
            int cx = (x1 + x2) / 2;
            graphics.drawString(this.font, hint,
                cx - this.font.width(hint) / 2, (y1 + y2) / 2 - 4, ModernStyle.TEXT_WEAK, false);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.toggleButtons == null) {
            return;
        }
        Set<ResourceLocation> inList = ClientSpawnRules.entities();
        int size = this.filtered.size();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            FlatButton toggle = this.toggleButtons[i];
            int idx = this.scrollOffset + i;
            if (toggle == null || !toggle.visible || idx >= size) {
                continue;
            }
            boolean on = inList.contains(this.filtered.get(idx).id());
            int mx = toggle.getX() + (toggle.getWidth() - 7) / 2;
            if (on) {
                FriendIcons.drawCheckMark(graphics, mx, toggle.getY() + (toggle.getHeight() - 6) / 2);
            } else {
                FriendIcons.drawCrossMark(graphics, mx, toggle.getY() + (toggle.getHeight() - 7) / 2);
            }
        }
    }

    private int previewLeft() {
        return contentLeft() + LIST_W + 4;
    }

    private int previewRight() {
        return contentLeft() + contentWidth();
    }

    private int previewTop() {
        return this.topPos + 66;
    }

    private int previewBottom() {
        return this.topPos + 66 + VISIBLE_ROWS * ROW_H - 2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = this.filtered.size();
        int maxOffset = Math.max(0, size - VISIBLE_ROWS);
        int next = Math.max(0, Math.min(maxOffset, this.scrollOffset - (int) Math.signum(scrollY)));
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            refreshRows(); // 就地刷新，不重建（保住搜索框焦点）
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 抓取滚动条须优先于控件判定；命中即拖动，不把点击透传给列表行 / 搜索框
        if (button == 0 && ScrollBar.needed(this.filtered.size(), VISIBLE_ROWS)
            && ScrollBar.isOver(mouseX, mouseY, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H)) {
            this.draggingBar = true;
            setScrollByMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar && button == 0) {
            setScrollByMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 由鼠标 y 设置滚动偏移并就地刷新行（不重建，保住搜索框焦点）。 */
    private void setScrollByMouse(double mouseY) {
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, this.filtered.size());
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            refreshRows();
        }
    }
}
