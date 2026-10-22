package com.ruadou.personaldimension.friend;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一个"玩家标识 + 显示名"对，用于在线玩家列表、可访问维度列表等 UI 展示。
 * 名称在服务端构建时解析（在线玩家 -&gt; 档案缓存 -&gt; UUID 字符串兜底）。
 *
 * <p>自带 {@link #STREAM_CODEC} 与 {@link #LIST_STREAM_CODEC}，使在线玩家包与可访问维度包
 * 复用同一份序列化逻辑（结构相同），避免重复手写缓冲区读写。
 */
public record NamedPlayer(UUID id, String name) {

    /** 单个条目的编解码器（UUID + 显示名）。 */
    public static final StreamCodec<ByteBuf, NamedPlayer> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public NamedPlayer decode(ByteBuf buf) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            return new NamedPlayer(fb.readUUID(), fb.readUtf());
        }

        @Override
        public void encode(ByteBuf buf, NamedPlayer value) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            fb.writeUUID(value.id());
            fb.writeUtf(value.name());
        }
    };

    /** 列表编解码器，供在线玩家 / 可访问维度列表包直接复用。 */
    public static final StreamCodec<ByteBuf, List<NamedPlayer>> LIST_STREAM_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list());
}
