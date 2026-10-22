package com.ruadou.personaldimension.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 一个可传送的坐标点：维度键 + 坐标 + 朝向。
 * 作为进出个人维度时记录的"返回点"，可被 Codec 序列化随玩家数据持久化。
 */
public record TravelPoint(ResourceKey<Level> dimension,
                          double x, double y, double z,
                          float yaw, float pitch) {

    public static final Codec<TravelPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TravelPoint::dimension),
        Codec.DOUBLE.fieldOf("x").forGetter(TravelPoint::x),
        Codec.DOUBLE.fieldOf("y").forGetter(TravelPoint::y),
        Codec.DOUBLE.fieldOf("z").forGetter(TravelPoint::z),
        Codec.FLOAT.fieldOf("yaw").forGetter(TravelPoint::yaw),
        Codec.FLOAT.fieldOf("pitch").forGetter(TravelPoint::pitch)
    ).apply(instance, TravelPoint::new));

    /** 以玩家当前所处位置（维度/坐标/朝向）快照为一个返回点。 */
    public static TravelPoint of(ServerPlayer player) {
        return new TravelPoint(
            player.level().dimension(),
            player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot());
    }
}
