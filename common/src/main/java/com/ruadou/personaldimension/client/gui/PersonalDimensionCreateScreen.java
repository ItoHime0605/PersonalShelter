package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientScreenOpener;
import com.ruadou.personaldimension.dimension.DimensionPreset;
import com.ruadou.personaldimension.dimension.template.DimensionTemplate;
import com.ruadou.personaldimension.menu.PersonalDimensionCreateMenu;
import com.ruadou.personaldimension.network.CreateDimensionPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 个人维度"创建维度"容器界面。
 *
 * <p>容器仅含一个生成器槽（放入后创建时消耗）；预设选择、自定义画板均为纯页面状态，不入容器。
 * <p>界面为单屏双模式：<b>预设模式</b>选择虚空 / 空岛 / 自定义；选中自定义后可"编辑模板"进入
 * <b>画板模式</b>——仿 AE2 样板编码终端：手持方块对着 16×16 网格点击即把该方块的物品图标
 * 作为幽灵项放入格子（不消耗物品），右键或空手清除，可分层堆叠，"完成"返回预设模式再创建。
 *
 * <p>之所以内联单屏而非独立画板 {@code Screen}：切换 Screen 会触发本容器
 * {@code removed()} 的 {@code clearContainer}，把生成器槽物品在客户端误退还；
 * 单屏模式切换（仅重建控件、不关容器）可完全规避此问题。
 */
public class PersonalDimensionCreateScreen extends AbstractContainerScreen<PersonalDimensionCreateMenu> {

    // —— 画板网格：16×16、每格 8px，左上角相对面板 (8,20) —— //
    private static final int GRID_X = 8;
    private static final int GRID_Y = 20;
    private static final int CELL = 8;
    private static final int GRID = DimensionTemplate.SIZE * CELL; // 16 × 8 = 128

    /** 生成器槽背景（drawSlot 18×18）相对面板左上角坐标，比菜单槽物品坐标 (150,26) 各偏移 -1。 */
    private static final int GEN_SLOT_BG_X = 149;
    private static final int GEN_SLOT_BG_Y = 25;

    /** 画板取色失败时的中性回退灰（适配深色主题）。 */
    private static final int BLOCK_FALLBACK = 0xFF5A6472;

    private DimensionPreset selected = DimensionPreset.VOID;
    private boolean canvasMode = false;
    private final TemplateCanvas canvas = new TemplateCanvas();

    // 预设按钮（仅预设模式存在，画板模式为 null）
    private FlatButton voidButton;
    private FlatButton skyIslandButton;
    private FlatButton customButton;

    public PersonalDimensionCreateScreen(PersonalDimensionCreateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 222;
        this.imageHeight = 244;
        // 背包名标签对齐到玩家背包上方（主格起始 y=162）
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.voidButton = null;
        this.skyIslandButton = null;
        this.customButton = null;
        if (this.canvasMode) {
            addCanvasWidgets();
        } else {
            addPresetWidgets();
        }
    }

    // ---------------------------------------------------------------------
    // 预设模式
    // ---------------------------------------------------------------------

    private void addPresetWidgets() {
        int lx = this.leftPos + 8;
        this.voidButton = new FlatButton(
            Component.translatable("gui.personaldimension.create.preset.void"),
            lx, this.topPos + 24, 120, 18, b -> selectPreset(DimensionPreset.VOID));
        this.skyIslandButton = new FlatButton(
            Component.translatable("gui.personaldimension.create.preset.sky_island"),
            lx, this.topPos + 46, 120, 18, b -> selectPreset(DimensionPreset.SKY_ISLAND));
        this.customButton = new FlatButton(
            Component.translatable("gui.personaldimension.create.preset.custom"),
            lx, this.topPos + 68, 120, 18, b -> selectPreset(DimensionPreset.CUSTOM));
        this.addRenderableWidget(this.voidButton);
        // Legacy sky-island and custom-template choices are intentionally hidden.

        // 仅自定义预设提供"编辑模板"入口


        // 右列：返回 / 创建
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            this.leftPos + 142, this.topPos + 108, 72, 18, b -> back()));
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.confirm"),
            this.leftPos + 142, this.topPos + 130, 72, 18, b -> confirm()));

        refreshSelection();
    }

    private void selectPreset(DimensionPreset preset) {
        this.selected = preset;
        // 重建以按需增删"编辑模板"按钮，并刷新单选高亮
        this.rebuildWidgets();
    }

    /** 刷新预设按钮的选中态（描金边），实现单选视觉反馈。 */
    private void refreshSelection() {
        this.voidButton.setSelected(this.selected == DimensionPreset.VOID);

    }

    // ---------------------------------------------------------------------
    // 画板模式
    // ---------------------------------------------------------------------

    private void addCanvasWidgets() {
        int rx = this.leftPos + 142;
        // 层级切换（仅改画板数据，网格与标签每帧从画板读取，无需重建）
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.canvas.layer_down"),
            rx, this.topPos + 76, 34, 16, b -> this.canvas.layerDown()));
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.canvas.layer_up"),
            this.leftPos + 180, this.topPos + 76, 34, 16, b -> this.canvas.layerUp()));
        // 整层填充（以当前手持方块填满本层） / 清空
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.canvas.fill"),
            rx, this.topPos + 96, 72, 16, b -> this.canvas.fillLayer(carriedState())));
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.canvas.clear"),
            rx, this.topPos + 116, 72, 16, b -> this.canvas.clearLayer()));
        // 完成：返回预设模式（画板数据保留在实例字段中）
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.create.canvas.done"),
            rx, this.topPos + 136, 72, 16, b -> closeCanvas()));
    }

    private void openCanvas() {
        this.canvasMode = true;
        this.rebuildWidgets();
    }

    private void closeCanvas() {
        this.canvasMode = false;
        this.rebuildWidgets();
    }

    /** 当前光标所持物品对应的方块状态；空手或非方块物品返回 null。 */
    private BlockState carriedState() {
        ItemStack carried = this.menu.getCarried();
        if (carried.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        return null;
    }

    /**
     * 对某格执行 AE2 幽灵槽式操作：右键清除；左键手持方块则放入该方块（不消耗），空手/非方块则清除。
     * 不做名单校验，由服务端创建时复核拒绝。
     */
    private void applyCell(int cell, int button) {
        if (button == 1) {
            this.canvas.erase(cell);
            return;
        }
        this.canvas.set(cell, carriedState()); // null（空手/非方块）在 canvas.set 内按擦除处理
    }

    /** 网格命中检测：返回格子索引 {@code x*16+z}，未命中返回 -1。 */
    private int cellAt(double mouseX, double mouseY) {
        int gx = this.leftPos + GRID_X;
        int gy = this.topPos + GRID_Y;
        if (mouseX < gx || mouseX >= gx + GRID || mouseY < gy || mouseY >= gy + GRID) {
            return -1;
        }
        int x = (int) ((mouseX - gx) / CELL);
        int z = (int) ((mouseY - gy) / CELL);
        return x * DimensionTemplate.SIZE + z;
    }

    // ---------------------------------------------------------------------
    // 提交 / 返回
    // ---------------------------------------------------------------------

    /** 提交创建：自定义携带画板模板，其余仅传预设 id。成功后由服务端关闭容器并传送。 */
    private void confirm() {
        if (!this.menu.getCarried().isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.displayClientMessage(Component.literal("請先將游標上的物品放回背包。"), false);
            return;
        }
        Platform.sendToServer(new CreateDimensionPacket(DimensionPreset.VOID.id()));
    }

    /** 返回控制器界面：先关闭容器通知服务端，再打开纯客户端控制器界面。 */
    private void back() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
        ClientScreenOpener.openControllerScreen();
    }

    // ---------------------------------------------------------------------
    // 输入拦截
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.canvasMode) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                applyCell(cell, button);
                return true; // 命中格子即消费，避免手持物品被 super 当作"丢出容器"
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.canvasMode) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                applyCell(cell, button); // 拖动连续放置 / 擦除
                return true;
            }
            return false; // 画板模式下禁止在网格外拖动物品，避免触发快速取放
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // ---------------------------------------------------------------------
    // 渲染
    // ---------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 绝对坐标（renderBg 经 renderBackground 在 pushPose 之前调用）
        ModernStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        ModernStyle.slot(graphics, this.leftPos + GEN_SLOT_BG_X, this.topPos + GEN_SLOT_BG_Y);
        drawPlayerInventorySlots(graphics);
        if (this.canvasMode) {
            drawGrid(graphics, mouseX, mouseY);
        }
    }

    /**
     * 绘制玩家背包 3×9 主格与快捷栏的凹槽背景。
     * 槽背景坐标 = 菜单 slot 物品坐标 −1（与 {@link PersonalDimensionCreateMenu} 布局一致）：
     * 主格物品起点 (8,162)、快捷栏 (8,220)，故背景起点 (7,161) / (7,219)。
     */
    private void drawPlayerInventorySlots(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ModernStyle.slot(graphics, this.leftPos + 7 + col * 18, this.topPos + 161 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            ModernStyle.slot(graphics, this.leftPos + 7 + col * 18, this.topPos + 219);
        }
    }

    /**
     * 绘制 16×16 画板：仿工作台合成区的整块凹陷灰槽（统一槽灰底 + 细网格线 + 外围内凹边框），
     * 逐格渲染幽灵方块的物品图标（缩放贴合格内），悬停格叠淡白高亮。物品图标在 renderBg 内绘制，
     * 使随后由 super 绘制的光标手持物品图标始终位于其上，指针不被网格遮挡。
     */
    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gx = this.leftPos + GRID_X;
        int gy = this.topPos + GRID_Y;
        int x2 = gx + GRID;
        int y2 = gy + GRID;
        // 深色凹陷背景：整块暗槽底
        graphics.fill(gx, gy, x2, y2, ModernStyle.SLOT_BG);
        // 细网格线分格（比槽底略亮，柔和不脏）
        for (int i = 1; i < DimensionTemplate.SIZE; i++) {
            graphics.fill(gx + i * CELL - 1, gy, gx + i * CELL, y2, ModernStyle.DIVIDER); // 竖线
            graphics.fill(gx, gy + i * CELL - 1, x2, gy + i * CELL, ModernStyle.DIVIDER); // 横线
        }
        // 外围内凹边框：顶/左深、底/右淡白，营造整体凹陷（与 ModernStyle.slot 同风格）
        graphics.fill(gx - 1, gy - 1, x2 + 1, gy, ModernStyle.SLOT_BORDER);
        graphics.fill(gx - 1, gy - 1, gx, y2 + 1, ModernStyle.SLOT_BORDER);
        graphics.fill(gx - 1, y2, x2 + 1, y2 + 1, 0x22FFFFFF);
        graphics.fill(x2, gy - 1, x2 + 1, y2 + 1, 0x22FFFFFF);
        // 幽灵物品图标：仅渲染已放置的格子，缩放到格内
        for (int x = 0; x < DimensionTemplate.SIZE; x++) {
            for (int z = 0; z < DimensionTemplate.SIZE; z++) {
                BlockState state = this.canvas.get(x * DimensionTemplate.SIZE + z);
                if (state != null) {
                    drawCellItem(graphics, gx + x * CELL, gy + z * CELL, state);
                }
            }
        }
        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            int hx = gx + (hovered / DimensionTemplate.SIZE) * CELL;
            int hz = gy + (hovered % DimensionTemplate.SIZE) * CELL;
            // z=200 使高亮位于 z≈75 的物品图标之上，占用格子后仍可见悬停反馈
            graphics.fill(hx, hz, hx + CELL - 1, hz + CELL - 1, 200, 0x55FFFFFF);
        }
    }

    /** 在格内渲染方块对应物品的缩略图标；该方块无对应物品（如流体等）时回退为地图色填充。 */
    private void drawCellItem(GuiGraphics graphics, int px, int pz, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) {
            graphics.fill(px, pz, px + CELL - 1, pz + CELL - 1, cellColor(state));
            return;
        }
        float scale = CELL / 16f; // 16px 物品图标缩放到 8px 格
        graphics.pose().pushPose();
        graphics.pose().translate(px, pz, 0);
        graphics.pose().scale(scale, scale, scale); // 均匀缩放，避免非等比破坏 3D 模型光照法线
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /** 方块的界面显示色：取其地图色（含 alpha）；无地图色或异常时回退中性灰。 */
    private static int cellColor(BlockState state) {
        try {
            MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            int rgb = mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
            return rgb == 0 ? BLOCK_FALLBACK : (0xFF000000 | rgb);
        } catch (Exception e) {
            return BLOCK_FALLBACK;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 相对面板坐标（renderLabels 在 pushPose(leftPos,topPos) 之后调用）
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, ModernStyle.TEXT, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, ModernStyle.TEXT, false);
        // 右列生成器槽标题
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.create.generator"),
            142, 16, ModernStyle.TEXT, false);
        if (this.canvasMode) {
            // 操作提示（两行，适配窄右列）+ 当前层号
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.create.canvas.hint_place"),
                142, 46, ModernStyle.TEXT, false);
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.create.canvas.hint_clear"),
                142, 56, ModernStyle.TEXT, false);
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.create.canvas.layer", this.canvas.currentY()),
                142, 66, ModernStyle.TEXT, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 画板模式：悬停已放置格子时显示该方块物品的 tooltip（网格区无真实槽，与 super 的槽 tooltip 互斥）
        if (this.canvasMode) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                BlockState state = this.canvas.get(cell);
                if (state != null) {
                    ItemStack stack = new ItemStack(state.getBlock());
                    if (!stack.isEmpty()) {
                        graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                    }
                }
            }
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
