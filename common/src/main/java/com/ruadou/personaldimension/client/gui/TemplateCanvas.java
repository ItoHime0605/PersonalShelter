package com.ruadou.personaldimension.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ruadou.personaldimension.dimension.template.DimensionTemplate;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 自定义地形画板的可变编辑状态（纯客户端）。
 *
 * <p>持有 16×16×N 的分层方块布局与当前编辑层，提供按格放置 / 擦除 / 整层填充 / 层级切换，
 * 提交时经 {@link DimensionTemplate#of} 归一化为不可变模板。本类<b>只管数据与操作</b>，
 * 不含屏幕坐标与渲染逻辑（由 {@code PersonalDimensionCreateScreen} 负责），遵循单一职责。
 *
 * <p>层从 {@link #BASE_Y} 向上堆叠；每层为长度 {@link DimensionTemplate#AREA} 的数组，
 * 元素为 {@code null} 表示空气；层数组按需惰性创建，避免为未编辑层预分配内存。
 * 单格索引约定 {@code cell = x*16 + z}，与 {@link DimensionTemplate} 内部索引一致。
 */
public final class TemplateCanvas {

    /** 层 0 所在世界 Y。取 0 使显示的层号即世界 Y（0..MAX_LAYERS-1），且恒落在维度可建造区间内。 */
    private static final int BASE_Y = 0;

    private final List<BlockState[]> layers = new ArrayList<>();
    private int currentLayer;

    public int baseY() {
        return BASE_Y;
    }

    public int currentLayer() {
        return this.currentLayer;
    }

    /** 当前编辑层对应的世界 Y。 */
    public int currentY() {
        return BASE_Y + this.currentLayer;
    }

    /** 上移一层（不超过 {@link DimensionTemplate#MAX_LAYERS} 上限）。 */
    public void layerUp() {
        if (this.currentLayer < DimensionTemplate.MAX_LAYERS - 1) {
            this.currentLayer++;
        }
    }

    /** 下移一层（不低于 0）。 */
    public void layerDown() {
        if (this.currentLayer > 0) {
            this.currentLayer--;
        }
    }

    /** 当前层某格方块状态（空气或未创建层返回 null）。 */
    public BlockState get(int cell) {
        if (this.currentLayer >= this.layers.size()) {
            return null;
        }
        BlockState[] arr = this.layers.get(this.currentLayer);
        return arr == null ? null : arr[cell];
    }

    /** 在某格放置指定方块（{@code state} 为 null 视为擦除）。 */
    public void set(int cell, BlockState state) {
        if (state == null) {
            erase(cell);
        } else {
            ensureLayer(this.currentLayer)[cell] = state;
        }
    }

    /** 擦除某格为空气。 */
    public void erase(int cell) {
        if (this.currentLayer < this.layers.size()) {
            BlockState[] arr = this.layers.get(this.currentLayer);
            if (arr != null) {
                arr[cell] = null;
            }
        }
    }

    /** 用指定方块填满当前层（{@code state} 为 null 则忽略）。 */
    public void fillLayer(BlockState state) {
        if (state != null) {
            Arrays.fill(ensureLayer(this.currentLayer), state);
        }
    }

    /** 清空当前层为全空气。 */
    public void clearLayer() {
        if (this.currentLayer < this.layers.size()) {
            this.layers.set(this.currentLayer, null);
        }
    }

    /** 归一化为不可变模板（去重调色板、裁剪尾部空层、至少一层）。 */
    public DimensionTemplate build() {
        return DimensionTemplate.of(BASE_Y, this.layers);
    }

    /** 惰性扩容到指定层并确保该层数组已创建。 */
    private BlockState[] ensureLayer(int layer) {
        while (this.layers.size() <= layer) {
            this.layers.add(null);
        }
        BlockState[] arr = this.layers.get(layer);
        if (arr == null) {
            arr = new BlockState[DimensionTemplate.AREA];
            this.layers.set(layer, arr);
        }
        return arr;
    }
}
