package com.ruadou.personaldimension.dimension.template;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自定义地形模板：一个区块占地（16×16）× 若干层的方块布局，供 {@code TemplateChunkGenerator} 逐区块平铺。
 *
 * <p>采用<b>调色板 + 索引</b>压缩：{@link #palette} 存放去重后的方块状态（约定索引 0 恒为空气），
 * {@link #indices} 为每个格子指向调色板的下标，长度恒为 {@code layerCount × 256}，
 * 按 {@code layer×256 + x×16 + z} 排布。相比逐格存 BlockState，存档（level.dat）与网络传输都更紧凑。
 *
 * <p>本类不可变；编辑期由客户端画板持有可变状态，提交时经 {@link #of} 归一化（去重、裁剪尾部空层）为本模板。
 */
public record DimensionTemplate(int baseY, int layerCount, List<BlockState> palette, int[] indices) {

    /** 单层占地边长（一个区块）。 */
    public static final int SIZE = 16;
    /** 单层格子数（16×16）。 */
    public static final int AREA = SIZE * SIZE;
    /** 层数上限，兼作反序列化时的内存安全上限，防止恶意包超额分配。 */
    public static final int MAX_LAYERS = 32;

    /** 存档用 Codec（随 LevelStem 写入 level.dat，重载时还原）。 */
    public static final Codec<DimensionTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("base_y").forGetter(DimensionTemplate::baseY),
            Codec.INT.fieldOf("layer_count").forGetter(DimensionTemplate::layerCount),
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(DimensionTemplate::palette),
            Codec.INT_STREAM.fieldOf("indices").forGetter(t -> IntStream.of(t.indices()))
    ).apply(instance, (baseY, layerCount, palette, indices) ->
            new DimensionTemplate(baseY, layerCount, palette, indices.toArray())));

    /** 网络用编解码：读取端带上限校验，避免伪造包触发大额分配（内存安全）。 */
    public static final StreamCodec<FriendlyByteBuf, DimensionTemplate> STREAM_CODEC =
            StreamCodec.of(DimensionTemplate::write, DimensionTemplate::read);

    /** 空气对应的调色板索引（约定固定为 0）。 */
    private static final int AIR_INDEX = 0;

    /** 取 (x, layer, z) 处方块状态；越界返回空气以保证生成健壮性。 */
    public BlockState stateAt(int x, int layer, int z) {
        if (layer < 0 || layer >= this.layerCount || x < 0 || x >= SIZE || z < 0 || z >= SIZE) {
            return Blocks.AIR.defaultBlockState();
        }
        int cell = this.indices[layer * AREA + x * SIZE + z];
        return (cell < 0 || cell >= this.palette.size())
                ? Blocks.AIR.defaultBlockState()
                : this.palette.get(cell);
    }

    /** (x,z) 列最高的非空气层下标；整列皆空返回 -1。 */
    public int topSolidLayer(int x, int z) {
        for (int layer = this.layerCount - 1; layer >= 0; layer--) {
            if (!stateAt(x, layer, z).isAir()) {
                return layer;
            }
        }
        return -1;
    }

    /** 出生落脚 Y：模板中心 (0,0) 列最高实心层之上一格；整列皆空则回退到 baseY。 */
    public int spawnY() {
        int top = topSolidLayer(0, 0);
        return top < 0 ? this.baseY : this.baseY + top + 1;
    }

    /** 调色板中出现过的实际方块（去空气），供服务端按配置名单校验。 */
    public Stream<Block> paletteBlocks() {
        return this.palette.stream().filter(state -> !state.isAir()).map(BlockState::getBlock);
    }

    /**
     * 由可变编辑数据归一化构造模板：去重生成调色板、裁剪尾部整层空气、至少保留一层。
     *
     * @param baseY  层 0 所在世界 Y
     * @param layers 从下往上的层列表，每层为长度 {@link #AREA} 的方块状态数组（元素可为 null 表示空气）
     */
    public static DimensionTemplate of(int baseY, List<BlockState[]> layers) {
        int effective = 0;
        for (int i = 0; i < layers.size(); i++) {
            if (!isEmptyLayer(layers.get(i))) {
                effective = i + 1;
            }
        }
        if (effective == 0) {
            effective = 1; // 至少一层，避免空模板
        }

        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState()); // 索引 0 恒为空气
        int[] indices = new int[effective * AREA];
        for (int layer = 0; layer < effective; layer++) {
            BlockState[] cells = layer < layers.size() ? layers.get(layer) : null;
            for (int i = 0; i < AREA; i++) {
                BlockState state = cells == null ? null : cells[i];
                indices[layer * AREA + i] = (state == null || state.isAir())
                        ? AIR_INDEX
                        : indexOf(palette, state);
            }
        }
        return new DimensionTemplate(baseY, effective, palette, indices);
    }

    private static int indexOf(List<BlockState> palette, BlockState state) {
        int existing = palette.indexOf(state);
        if (existing >= 0) {
            return existing;
        }
        palette.add(state);
        return palette.size() - 1;
    }

    private static boolean isEmptyLayer(BlockState[] cells) {
        if (cells == null) {
            return true;
        }
        for (BlockState state : cells) {
            if (state != null && !state.isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void write(FriendlyByteBuf buf, DimensionTemplate template) {
        buf.writeVarInt(template.baseY);
        buf.writeVarInt(template.layerCount);
        buf.writeVarInt(template.palette.size());
        for (BlockState state : template.palette) {
            buf.writeVarInt(Block.getId(state));
        }
        for (int index : template.indices) {
            buf.writeVarInt(index);
        }
    }

    private static DimensionTemplate read(FriendlyByteBuf buf) {
        int baseY = buf.readVarInt();
        int layerCount = buf.readVarInt();
        if (layerCount < 1 || layerCount > MAX_LAYERS) {
            throw new DecoderException("非法模板层数: " + layerCount);
        }
        int paletteSize = buf.readVarInt();
        if (paletteSize < 1 || paletteSize > AREA * MAX_LAYERS) {
            throw new DecoderException("非法模板调色板大小: " + paletteSize);
        }
        List<BlockState> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(Block.stateById(buf.readVarInt()));
        }
        int[] indices = new int[layerCount * AREA];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = buf.readVarInt();
        }
        return new DimensionTemplate(baseY, layerCount, palette, indices);
    }
}
