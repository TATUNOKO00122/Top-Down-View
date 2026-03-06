# Draft: Epic Fight Camera Priority Settings

## Requirements (confirmed)

### 機能1: 視点制御ができない問題の修正
- Epic Fight使用時にドラッグ回転、マウススクロールズームが効かない問題を解決
- 入力処理（ドラッグ回転、ズーム）は有効にし、カメラ位置の設定のみをスキップ

### 機能2: 設定によるカメラ優先切り替え
- `epicFightCameraPriority`: Enum設定（TOPDOWN_PRIORITY / EPICFIGHT_PRIORITY）
- ConfigScreenに「互換性」タブ（TAB_COMPAT = 5）を追加
- Epic Fight検出時のみ、互換性設定を表示

## Technical Decisions

### 設定値の保存方法
- ForgeConfigSpec.EnumValueを使用（既存パターンに従う）
- Runtime値はintまたはenumで保持

### 入力処理とカメラ制御の分離
- CameraController.onClientTick()のロジックを分割
- 入力処理（updateDragRotation）は常に実行
- カメラ位置設定（CameraMixin）のみ条件付きスキップ

## Open Questions

### Q1: マウススクロールズームの現状
**コード分析結果:**
- `InputHandler.onMouseScroll()`は独立したイベントハンドラ
- `EpicFightCompat.isEpicFightCameraActive()`のチェック**なし**
- 理論上はズームが動作するはず

**質問:** マウススクロールズームは実際に動作していないか？それともドラッグ回転のみの問題か？

### Q2: 設定Enum値
**提案:**
```java
public enum EpicFightCameraPriority {
    TOPDOWN_PRIORITY,    // TopDownViewのカメラ制御を優先
    EPICFIGHT_PRIORITY   // Epic Fightのカメラ制御を優先（現在の動作）
}
```

**質問:** この2値でよいか？それとも「自動（AUTO）」という選択肢も必要か？

### Q3: カメラ優先設定の挙動
**提案:**
- `TOPDOWN_PRIORITY`: Epic Fightがアクティブでも**TopDownViewがカメラを制御**（Epic Fightの弓エイム等は無効化）
- `EPICFIGHT_PRIORITY`: Epic Fightアクティブ時は**Epic Fightに委譲**（現在の動作）

**質問:** この理解で正しいか？TOPDOWN_PRIORITY選択時、Epic Fightの戦闘アニメーションへの影響を許容するか？

### Q4: 入力処理分離の範囲
**提案:**
- ドラッグ回転（`InputHandler.updateDragRotation`）: Epic Fightアクティブ時も**有効**
- カメラ位置設定（`CameraMixin.onSetup`）: 設定に基づきスキップ判断

**質問:** `updatePlayerRotationToMouse()`と`alignCameraToMovement()`も同様に分離すべきか？
（これらはプレイヤーの向き制御に関わる）

### Q5: 互換性タブの表示条件
**提案:**
- Epic Fight検出時（`EpicFightCompat.isEpicFightLoaded()`がtrue）のみタブを表示
- 検出されない場合はタブ自体を非表示

**質問:** タブを非表示にするか、それとも「Epic Fightが検出されません」のようなメッセージを表示するか？

## Scope Boundaries

### INCLUDE:
- Config.javaへのEnum設定追加
- ConfigScreen.javaへの互換性タブ追加
- EpicFightCompat.javaへの設定判定メソッド追加
- CameraController.javaの入力処理分離
- CameraMixin.javaの条件分岐修正

### EXCLUDE:
- Epic Fight本体の修正
- 他MODとの互換性対応
- 新しいキーバインドの追加

## Research Findings

### Config.java パターン
- `ForgeConfigSpec.IntValue`や`BooleanValue`を使用
- Runtime値は`public static`フィールドで公開
- `onLoad()`で初期値を設定、`save()`で保存

### ConfigScreen.java パターン
- タブは`switch (currentTab)`で切り替え
- 各タブは`initXxxTab()`メソッドで実装
- UI要素は`Button.builder()`や`ConfigSlider`で作成

### CameraController.java パターン
- `@SubscribeEvent`で`ClientTickEvent`を処理
- `EpicFightCompat.isEpicFightCameraActive()`で条件分岐
- 入力処理は`InputHandler.updateDragRotation(mc)`を呼び出し
