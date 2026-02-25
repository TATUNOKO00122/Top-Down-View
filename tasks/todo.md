# TopDownView MOD リファクタリング計画

## 概要
パフォーマンス・可読性の観点からコードレビューで指摘された問題を修正する。

## 決定事項
- カメラ距離：定数維持（Config化しない）
- MouseRaycast：後方互換メソッド削除、シンプル化
- 実行順序：状態管理統合を優先

---

## フェーズ1：状態管理の統合

### 1.1 CameraStateへの一本化
- [ ] `CameraState`にカメラ距離（MIN/MAX/DEFAULT定数とランタイム値）を集約
- [ ] `ClientForgeEvents`の`CAMERA_DISTANCE` AtomicReferenceを削除
- [ ] `ClientForgeEvents`のカメラ距離getter/setterを`CameraState`に委譲

### 1.2 影響範囲の修正
- [ ] `MouseRaycast.java` - `CameraState.INSTANCE.getCameraDistance()`に変更
- [ ] `CameraMixin.java` - 同上
- [ ] `InputHandler.java` - 同上
- [ ] その他参照箇所の確認・修正

### 1.3 ClientForgeEventsの整理
- [ ] `StateChangeListener`インターフェース削除
- [ ] `stateChangeListener`フィールド削除
- [ ] `notifyStateChange()`メソッド削除
- [ ] `setStateChangeListener()`メソッド削除

---

## フェーズ2：MouseRaycastのシンプル化

### 2.1 後方互換メソッドの削除
- [ ] `rayTraceBlock(Minecraft, float, double)` 削除
- [ ] `rayTraceEntity(Minecraft, float, double)` 削除
- [ ] `getHitResult(Minecraft, float, double)` 削除
- [ ] `performRayTraceBlock()` 削除
- [ ] `performGetHitResult()` 削除

### 2.2 APIの統一
- [ ] `update()`メソッドを使用するパターンに統一
- [ ] `getLastHitResult()`, `getLastBlockHit()`, `getLastEntityHit()`のみ公開
- [ ] 呼び出し元（`GameRendererMixin`等）の修正

### 2.3 内部リファクタリング
- [ ] `RaycastResult`レコードの妥当性確認
- [ ] `performRaycast()`の責務明確化
- [ ] 不要な`Objects.requireNonNull`の削除

---

## フェーズ3：LevelRendererMixinのリフレクション最適化

### 3.1 static初期化ブロックへの移動
- [ ] `Class.forName()`をstatic初期化ブロックで1回のみ実行
- [ ] `Method`オブジェクトをstaticフィールドにキャッシュ
- [ ] `entityCullingLoaded`フラグで早期リターン

### 3.2 コード整理
- [ ] `onRenderEntityHead()`の可読性向上
- [ ] 例外処理の簡素化

---

## フェーズ4：TopDownCuller キャッシュ戦略改善

### 4.1 ConcurrentHashMap → HashMap
- [ ] Minecraftメインスレッドのみ動作のため`HashMap`に変更
- [ ] 適切な初期容量設定

### 4.2 キャッシュクリア戦略の改善
- [ ] 全クリアではなく、プレイヤー位置からの距離ベースで古いエントリを削除
- [ ] またはLRUキャッシュの検討

---

## フェーズ5：定数の集約

### 5.1 MathConstants作成
- [ ] `com.topdownview.util.MathConstants`作成
- [ ] `DEGREES_TO_RADIANS`, `RADIANS_TO_DEGREES`定義

### 5.2 各ファイルの修正
- [ ] `CameraController.java`
- [ ] `CameraMixin.java`
- [ ] `MouseRaycast.java`

---

## フェーズ6：未使用コード・クリーンアップ

### 6.1 TimeState確認
- [ ] `zoomTime`, `startZoom`, `zoomOutTime`の使用確認
- [ ] 未使用なら削除

### 6.2 その他
- [ ] `CullingManager`のデバッグログ削除
- [ ] `AssertionError` → `IllegalStateException`変更
- [ ] 過剰なnullチェックの緩和

---

## フェーズ7：ビルド・動作確認

- [ ] `./gradlew build` 成功確認
- [ ] `./gradlew runClient` で動作確認
- [ ] 主要機能の動作確認：
  - [ ] トップダウンビュー切り替え（F4）
  - [ ] カメラ回転（R）
  - [ ] ズーム（Alt+スクロール）
  - [ ] ブロック破壊・設置
  - [ ] エンティティ攻撃

---

## 実行メモ

### 実行日時：
### 実行者：
### 注意事項：
- 各フェーズ完了後にビルド確認を行う
- 状態管理の変更は慎重に行う（バグの温床になりやすい）
