package com.ruadou.personaldimension.dimension;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 传送器记录条目：维度键 + 坐标 + 可选名称。
 * 用于在全局 {@link TeleporterData} 中持久化玩家放置的维度传送器位置。
 *
 * <p>自带 {@link #STREAM_CODEC} 与 {@link #LIST_STREAM_CODEC}，供网络包直接复用序列化逻辑。
 * 同时提供 {@link #save}/{@link #load} 手动 NBT 序列化，与项目既有 SavedData 风格一致。
 */
public record TeleporterEntry(ResourceKey<Level> dimension, int x, int y, int z, String name, String note) {

    /** SavedData 持久化用 Codec（备用）。 */
    public static final Codec<TeleporterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TeleporterEntry::dimension),
        Codec.INT.fieldOf("x").forGetter(TeleporterEntry::x),
        Codec.INT.fieldOf("y").forGetter(TeleporterEntry::y),
        Codec.INT.fieldOf("z").forGetter(TeleporterEntry::z),
        Codec.STRING.fieldOf("name").forGetter(TeleporterEntry::name),
        Codec.STRING.fieldOf("note").forGetter(TeleporterEntry::note)
    ).apply(instance, TeleporterEntry::new));

    /** 单条目的网络编解码器。 */
    public static final StreamCodec<ByteBuf, TeleporterEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TeleporterEntry decode(ByteBuf buf) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            ResourceKey<Level> dim = fb.readResourceKey(Registries.DIMENSION);
            int x = fb.readInt();
            int y = fb.readInt();
            int z = fb.readInt();
            String name = fb.readUtf();
            String note = fb.readUtf();
            return new TeleporterEntry(dim, x, y, z, name, note);
        }

        @Override
        public void encode(ByteBuf buf, TeleporterEntry value) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            fb.writeResourceKey(value.dimension());
            fb.writeInt(value.x());
            fb.writeInt(value.y());
            fb.writeInt(value.z());
            fb.writeUtf(value.name());
            fb.writeUtf(value.note());
        }
    };

    /** 列表编解码器，供传送器列表包复用。 */
    public static final StreamCodec<ByteBuf, List<TeleporterEntry>> LIST_STREAM_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list());

    // ——— 手动 NBT 序列化（与项目 SavedData 风格一致）——

    private static final String TAG_DIM = "dim";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_NAME = "name";
    private static final String TAG_NOTE = "note";

    /** 从 BlockPos 构造便捷方法（note 默认为空）。 */
    public static TeleporterEntry of(ResourceKey<Level> dimension, BlockPos pos, String name) {
        return new TeleporterEntry(dimension, pos.getX(), pos.getY(), pos.getZ(), name, "");
    }

    /** 保存到 NBT。 */
    public CompoundTag save(CompoundTag tag) {
        tag.putString(TAG_DIM, dimension.location().toString());
        tag.putInt(TAG_X, x);
        tag.putInt(TAG_Y, y);
        tag.putInt(TAG_Z, z);
        tag.putString(TAG_NAME, name);
        tag.putString(TAG_NOTE, note);
        return tag;
    }

    /** 从 NBT 加载（旧数据无 note 字段时默认空字符串）。 */
    public static TeleporterEntry load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(tag.getString(TAG_DIM)));
        return new TeleporterEntry(dim,
            tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z),
            tag.getString(TAG_NAME),
            tag.contains(TAG_NOTE) ? tag.getString(TAG_NOTE) : "");
    }

    /** 坐标对应的 BlockPos。 */
    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}
