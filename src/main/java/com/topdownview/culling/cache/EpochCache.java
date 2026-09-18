package com.topdownview.culling.cache;

import java.util.function.Supplier;

/**
 * チャンクビルドのワーカースレッドから並列に読まれる per-thread キャッシュの基底。
 *
 * <p>スレッドごとにマップを持ち、エポックを進めるだけで全スレッドの内容を遅延破棄する。
 * サブクラスはマップ種別と上限処理のみを実装する。
 *
 * @param <M> スレッドごとに保持するマップ型
 */
abstract class EpochCache<M> {

    private volatile int epoch = 0;

    private final ThreadLocal<Local<M>> threadLocal;

    private static final class Local<M> {
        final M map;
        int epoch = -1;

        Local(M map) {
            this.map = map;
        }
    }

    protected EpochCache(Supplier<M> mapFactory) {
        this.threadLocal = ThreadLocal.withInitial(() -> new Local<>(mapFactory.get()));
    }

    /** 現在スレッドのマップを返す。エポックが進んでいれば空にしてから返す。 */
    protected final M map() {
        Local<M> local = threadLocal.get();
        int globalEpoch = epoch;
        if (local.epoch != globalEpoch) {
            clearMap(local.map);
            local.epoch = globalEpoch;
        }
        return local.map;
    }

    /** エポックを進めて全スレッドのキャッシュを無効化する。 */
    public final void clear() {
        epoch++;
        onClear();
    }

    protected abstract void clearMap(M map);

    /** {@link #clear()} 時にマップ以外も破棄する場合にオーバーライドする。 */
    protected void onClear() {
    }
}
