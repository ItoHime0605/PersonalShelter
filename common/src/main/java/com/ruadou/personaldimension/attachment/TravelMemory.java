package com.ruadou.personaldimension.attachment;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家的进出个人维度记忆，含四个互不干扰的返回点：
 * <ul>
 *   <li>{@link #outside}：最近一次处于"个人维度之外"（主世界/下界等）的位置；</li>
 *   <li>{@link #own}：最近一次处于"自己个人维度"内的位置；</li>
 *   <li>{@link #publicReturn}：进入公共维度前所在的位置（退出公共维度时返回）；</li>
 *   <li>{@link #visitReturn}：访问他人个人维度前所在的位置（退出他人维度时返回）。</li>
 * </ul>
 * 作为不可变值对象，更新时返回新实例（配合 {@code setData} 触发持久化标脏）。
 */
public record TravelMemory(Optional<TravelPoint> outside, Optional<TravelPoint> own,
                           Optional<TravelPoint> publicReturn, Optional<TravelPoint> visitReturn) {

    public static final TravelMemory EMPTY = new TravelMemory(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<TravelMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TravelPoint.CODEC.optionalFieldOf("outside").forGetter(TravelMemory::outside),
        TravelPoint.CODEC.optionalFieldOf("own").forGetter(TravelMemory::own),
        TravelPoint.CODEC.optionalFieldOf("public_return").forGetter(TravelMemory::publicReturn),
        TravelPoint.CODEC.optionalFieldOf("visit_return").forGetter(TravelMemory::visitReturn)
    ).apply(instance, TravelMemory::new));

    public TravelMemory withOutside(TravelPoint point) {
        return new TravelMemory(Optional.of(point), this.own, this.publicReturn, this.visitReturn);
    }

    public TravelMemory withOwn(TravelPoint point) {
        return new TravelMemory(this.outside, Optional.of(point), this.publicReturn, this.visitReturn);
    }

    public TravelMemory withPublicReturn(TravelPoint point) {
        return new TravelMemory(this.outside, this.own, Optional.of(point), this.visitReturn);
    }

    public TravelMemory withVisitReturn(TravelPoint point) {
        return new TravelMemory(this.outside, this.own, this.publicReturn, Optional.of(point));
    }

    /** 清除 own 返回点（删除个人维度后调用，避免残留指向已消失维度的记忆点）。 */
    public TravelMemory withoutOwn() {
        return new TravelMemory(this.outside, Optional.empty(), this.publicReturn, this.visitReturn);
    }

    /** 清除 publicReturn 返回点（删除公共维度后调用，避免残留指向已消失维度的记忆点）。 */
    public TravelMemory withoutPublicReturn() {
        return new TravelMemory(this.outside, this.own, Optional.empty(), this.visitReturn);
    }

    /** 清除 visitReturn 返回点（退出他人维度后调用）。 */
    public TravelMemory withoutVisitReturn() {
        return new TravelMemory(this.outside, this.own, this.publicReturn, Optional.empty());
    }
}
