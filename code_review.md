# TopDownView Mod コードレビュー

> [!CAUTION]
> 以下はシニアエンジニアの視点から、バグ・クラッシュ・パフォーマンス・可読性の4軸で指摘した問題点です。重要度順に記載しています。

---

## 🔴 クラッシュリスク（致命的）

### 1. `CameraMixin` — Mixin内の static キャッシュが非InstanceSafe

[CameraMixin.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/mixin/CameraMixin.java#L32-L37)

```java
private static double cachedDelayY = -1;
private static double cachedLerpFactorY = 0;
```

Mixin対象の `Camera` クラスは複数インスタンスが存在しうる（メインカメラ、サードパーティModのカメラなど）。`static` フィールドを使うことで、あるインスタンスでの呼び出しが別インスタンスのキャッシュを汚染する。**直接的なクラッシュには至らないが、カメラの挙動が不安定になるバグを引き起こす。**

---

### 2. `CameraMixin.onSetupTail` — フリーカメラモード中の `updatePrevYaw` / `updatePrevFreeCameraPitch` 呼び出し位置が不正

[CameraMixin.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/mixin/CameraMixin.java#L67-L70)

```java
if (ModState.CAMERA.isFreeCameraMode()) {
    ModState.CAMERA.updatePrevYaw();
    ModState.CAMERA.updatePrevFreeCameraPitch();
}
```

`CameraController.onClientTick` でも `updatePrevYaw()` を呼んでいる（L66）。**フリーカメラモード時に2箇所で prev を更新するため、Lerp補間が正しく動作しない可能性がある。** `setup()` はフレーム毎に呼ばれるが `ClientTick` はティック毎なので、更新頻度の不一致で振動的なカメラ挙動が発生する。

---

### 3. `LevelRendererMixin` — Entity Culling MOD対応の例外握りつぶし

[LevelRendererMixin.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/mixin/LevelRendererMixin.java#L57-L61)

```java
} catch (ClassNotFoundException e) {
} catch (NoSuchMethodException e) {
} catch (IllegalAccessException e) {
}
```

同様に L77-78 でも空の catch。**リフレクション呼び出しの MethodHandle.invoke に対する `Throwable` の握りつぶしは、ClassCastException や LinkageError などの致命的エラーも隠蔽する。** 少なくともログ出力すべき。

---

### 4. `ClickActionHandler.isLeftClickDown` が `public static` で公開

[ClickActionHandler.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/client/ClickActionHandler.java#L17)

```java
public static boolean isLeftClickDown = false;
```

どの外部クラスからも直接書き換え可能。状態の整合性が保証されず、`leftClickHoldTicks` との同期が壊れるリスクがある。

---

## 🟠 バグ（潜在的で再現条件付き）

### 5. `CameraController.updateAnimation` — `normalizeAngle` 未使用で角度差の計算ロジックが手動実装

[CameraController.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/client/CameraController.java#L99-L103)

```java
float diff = targetYaw - currentYaw;
while (diff < -180.0f) diff += 360.0f;
while (diff > 180.0f) diff -= 360.0f;
```

`CameraState.normalizeAngle()` が既に存在するが使われていない。**同じ正規化ロジックが少なくとも6箇所に散在しており（L100, L237, L275, L292, L304）、いずれかの実装に不整合があった場合にバグとなる。** DRY違反。

---

### 6. `TopDownCuller.update` — カメラ位置 `(0,0,0)` を無効値として扱う

[TopDownCuller.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/TopDownCuller.java#L212-L218)

```java
if (rawCameraX == 0.0 && rawCameraY == 0.0 && rawCameraZ == 0.0) {
    contextValid = false;
    return;
}
```

ワールド原点 `(0,0,0)` にプレイヤーがいる場合、カリングが無効になる。`CameraState.DEFAULT_POSITION` との `==` 比較（参照比較）にすべきだが、`CullingManager.scheduleChunkRebuildIfNeeded` (L99) では既に `==` を使っている。統一されていない。

---

### 7. `CullingManager.scheduleChunkRebuildIfNeeded` — 参照等価比較の問題

[CullingManager.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/CullingManager.java#L99)

```java
if (cameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
    return;
}
```

`CameraState.reset()` で `cameraPosition = DEFAULT_POSITION` と設定しているが、`setCameraPosition(new Vec3(...))` を通じて後から同値の `(0,0,0)` が設定された場合、この参照比較は `false` を返す。**一貫性のない「未初期化」判定。**

---

### 8. `Config` — setterにバリデーションが一切ない

[Config.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/Config.java#L206-L249)

全47個のsetterが無検証。`ConfigScreen` から `setCameraDistance(-100)` のような値が渡された場合、`CameraState.setCameraDistance` が例外を投げるが **Config側では保持してしまう二重状態になる。**

---

### 9. `PathfindingEngine.encodePos` — 座標エンコードのオーバーフロー

[PathfindingEngine.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/pathfinding/PathfindingEngine.java#L155-L157)

```java
private static long encodePos(int x, int y, int z) {
    return ((long) x & 0xFFFFFFL) | (((long) y & 0xFFFFL) << 24) | (((long) z & 0xFFFFFFL) << 40);
}
```

Xに24bit、Yに16bit、Zに24bit割り当てているが、**X(24bit) + Y(16bit, offset 24) + Z(24bit, offset 40) = 64bit。ただし X > 16,777,215 またはX < 0 のとき情報が欠落する。** Minecraftの座標範囲（±30,000,000）ではXとZが24bit（±8,388,608）を超えうるため、**遠方でのハッシュ衝突が発生する。**

---

### 10. `PathfindingEngine.findPathToBlock` — PriorityQueue.contains がO(n)

[PathfindingEngine.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/pathfinding/PathfindingEngine.java#L129)

```java
if (!openSet.contains(neighbor)) {
    openSet.add(neighbor);
}
```

`PriorityQueue.contains()` はO(n)。A*のオープンセットではこれが致命的なパフォーマンスボトルネックになる。`PathNode` に `inOpenSet` フラグを持たせてO(1)にすべき。

---

## 🟡 パフォーマンス問題

### 11. `TopDownCuller.isBlockCulled` — 毎 tick で `ModState.STATUS.isEnabled()` が false の時にキャッシュ全消去

[TopDownCuller.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/TopDownCuller.java#L85-L88)

```java
if (!ModState.STATUS.isEnabled()) {
    cullingCache.clear();
    return false;
}
```

Embeddiumのレンダリングパイプラインから毎フレーム大量に呼ばれるメソッド。トップダウンが無効の間、**毎呼び出しで空のHashMap.clear()を呼ぶ。** 一度だけクリアするフラグを設けるべき。

---

### 12. `CullingCacheManager` — キャッシュ満杯時に全消去

[CullingCacheManager.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/cache/CullingCacheManager.java#L19-L22)

```java
if (cache.size() >= MAX_CACHE_SIZE) {
    cache.clear();
    culledCount = 0;
}
```

8000エントリに達したら全消去 → 直後にまた8000エントリまで貯まる → また全消去。**スラッシング問題。** LRUキャッシュまたは世代別キャッシュに置き換えるべき。

---

### 13. `MouseRaycast.rayTraceBlocks` — カスタムレイキャストの毎フレーム実行

[MouseRaycast.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/client/MouseRaycast.java#L188-L246)

ステップ距離を `0.25〜1.0` に適応させても、カメラ距離50ブロックの場合は最大200回ループ × 毎回 `mc.level.getBlockState()` + `TopDownCuller.isBlockCulled()` を呼ぶ。**フレームごとの計算量としてはかなり重い。** `GameRendererMixin.onPick` と `CameraController.updatePlayerRotationToMouse` の両方で呼ばれるため、**実質的に1フレームに2回実行される可能性がある**（重複更新チェックはあるがgameTimeベース）。

---

### 14. `TopDownCuller.updateEntityCulling` — 全エンティティを毎ティック走査

[TopDownCuller.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/TopDownCuller.java#L265)

```java
for (Entity entity : mc.level.entitiesForRendering()) {
```

大規模ワールドでは千単位のエンティティが対象になりうる。空間インデックス（AABB検索）で視界内エンティティだけに絞るべき。

---

### 15. `isEntityGrounded` — 毎ティック全Mobに対してBlockPos.MutableBlockPos生成

[TopDownCuller.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/TopDownCuller.java#L340)

```java
BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
```

`updateEntityCulling` のループ内から呼ばれるため、エンティティの数だけ `MutableBlockPos` を new する。ループの外で一度だけ作り回すべき。

---

### 16. `PathfindingEngine.getNeighbors` — ループ内で `new BlockPos` を生成（未使用）

[PathfindingEngine.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/pathfinding/PathfindingEngine.java#L189)

```java
BlockPos neighborPos = new BlockPos(nx, ny, nz);
```

生成された `neighborPos` は使用されていない。**無駄なオブジェクト生成。**

---

### 17. `CullingManager` — 50ms間隔で `System.currentTimeMillis()` を毎ティック呼び出し

[CullingManager.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/culling/CullingManager.java#L90)

`System.currentTimeMillis()` はtick数でカウントする方が正確（ポーズ中も進むため）。Minecraft `gameTime` で管理すべき。

---

## 🔵 可読性・設計の問題

### 18. `Config.java` — 361行のボイラープレート地獄

47個の設定項目それぞれに対して `ForgeConfigSpec` 宣言、`private static` フィールド、getter、setter、`onLoad` のコピー、`save` のコピーが全て手書き。**設定を1つ追加するたびに6箇所を同時編集する必要がある。** 追加忘れのバグが容易に発生する構造。

---

### 19. FQCNの直書きが大量

コード全体にわたり以下のようなFQCN（完全修飾クラス名）が散見される：

```java
com.topdownview.TopDownViewMod.getLogger().info(...)
com.topdownview.Config.getCameraPitch()
com.topdownview.state.CameraState.getEffectiveDefaultCameraDistance()
```

import文を使えば `Config.getCameraPitch()` で済む。**可読性が大幅に低下している。**

---

### 20. `ClickToMoveState` — `reset()` と `stopMovement()` がほぼ同一

[ClickToMoveState.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/state/ClickToMoveState.java#L240-L272)

`reset()` と `stopMovement()` は全く同じフィールドを同じ値にリセットしている。**コードの重複で、片方だけ更新した場合の不整合リスクがある。** `stopMovement` は `reset` を呼ぶだけにすべき。

---

### 21. `ClientForgeEvents` — PauseScreenのデバッグログが本番に残存

[ClientForgeEvents.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/client/ClientForgeEvents.java#L50-L67)

```java
LOGGER.info("PauseScreen initialized, screen size: {}x{}", screen.width, screen.height);
LOGGER.info("Children count: {}", screen.children().size());
LOGGER.info("Renderables count: {}", screen.renderables.size());
```

全ボタンのforEachログも含め、**ポーズ画面を開くたびに大量のINFOログが出力される。** デバッグ用として残すなら `LOGGER.debug` にすべき。同様の問題は `CameraController.initializeTopDownView` 等にも存在。

---

### 22. プロジェクトルートの不要ファイル群

プロジェクトルートに以下のようなデバッグ・テンポラリファイルが大量に残存：
- `build_log*.txt`（14ファイル）、`error_log*.txt`（5ファイル）
- `hs_err_pid*.log`（4ファイル = JVMクラッシュダンプ）
- `replay_pid*.log`
- `cfr-0.152.jar`、`embeddium-0.3.31+mc1.20.1.jar`
- `temp_*` ディレクトリ（10個）
- `src_backup_1.3.1`, `src_temp`
- `nul`（ゼロバイトのダミーファイル）

これらは `.gitignore` に追加するか削除すべき。

---

### 23. `RenderEventHandler` — `final` 修飾子の欠如

[RenderEventHandler.java](file:///e:/Minecraft/MOD/topdown_view/src/main/java/com/topdownview/client/RenderEventHandler.java#L11)

```java
public class RenderEventHandler {
```

他のイベントハンドラクラスは全て `final` + private constructor パターンを採用している。ここだけ未適用で一貫性がない。`TargetHighlightRenderer` も同様。

---

## 総評

| 観点 | 評価 | コメント |
|------|------|---------|
| **バグ** | ⚠️ 中 | 角度正規化の重複、カメラ座標 `(0,0,0)` 問題、Config バリデーション欠如 |
| **クラッシュ** | ⚠️ 中 | 例外握りつぶし、リフレクション失敗時のサイレントフォールバック |
| **パフォーマンス** | 🔴 高 | キャッシュスラッシング、毎フレームの重いレイキャスト、全エンティティ走査 |
| **可読性** | 🔴 高 | Config のボイラープレート、FQCN直書き、デバッグログ残存、メソッド重複 |

**最も優先して修正すべき項目:**
1. **キャッシュスラッシング** (項目11, 12) → パフォーマンスへの影響が最大
2. **角度正規化の共通化** (項目5) → バグの温床
3. **Config のバリデーション追加** (項目8) → ConfigScreen経由のクラッシュ防止
4. **デバッグログの整理** (項目21) → ユーザー体験
5. **不要ファイルの削除** (項目22) → リポジトリの衛生
