# Draft: Click-and-Drag Camera Rotation Feature

## Requirements (Confirmed)

### Core Feature
- クリックアンドドラッグによるカメラ回転を追加
- 中マウスボタン（または設定可能）でドラッグ
- カメラがプレイヤー周りを滑らかに回転
- 既存のRキー回転と共存（置き換えない）

### Configuration Requirements
- 感度設定（Config.javaに追加）
- キーバインド設定（ClientModBusEvents.javaに追加）
- 回転速度の調整可能性

### Technical Constraints
- Minecraft Forge 1.20.1
- Client-side only（サーバーサイド影響なし）
- 既存のCameraState/InputHandler/CameraControllerとの統合

## Files to Modify (Tentative)

1. `CameraState.java` - ドラッグ状態フィールド追加
2. `InputHandler.java` - マウスドラッグイベント処理
3. `Config.java` - ドラッグ回転設定追加
4. `ClientModBusEvents.java` - ドラッグキーバインド追加
5. `CameraController.java` - ドラッグ回転ロジック統合

## Open Questions

### UX/Behavior
- [ ] ドラッグ中のカーソル表示（ロックする？非表示にする？そのまま？）
- [ ] ピッチ（上下）回転も許可する？ヨー（左右）のみ？
- [ ] 回転の加速/減速アニメーションを入れる？
- [ ] インベントリを開いたらドラッグ解除？

### Technical
- [ ] 中マウスボタン固定か、任意のマウスボタンに設定可能か
- [ ] 感度のデフォルト値
- [ ] 1ピクセル移動あたりの回転角度の計算式
- [ ] 既存のスナップ回転との切り替え動作

### Edge Cases
- [ ] ドラッグ中にRキーを押した場合の動作
- [ ] 他のMODとのキー/マウスコンフリクト対策
- [ ] ワールド読み込み中のドラッグ無効化

## Research Findings

### 現在の回転システム（既存実装）

**CameraState.java (回転関連):**
- `yaw`, `prevYaw`, `targetYaw` - 角度管理
- `isAnimating`, `isAutoAlignAnimation` - アニメーション状態
- `normalizeAngle()` - 角度正規化（-180°〜+180°）
- **既にドラッグ用フィールド存在**: `isDragging`, `dragStartYaw`, `dragStartMouseX`

**InputHandler.java:**
- Rキー押下 → `CameraController.rotateCamera()` 呼び出し
- マウススクロール → カメラ距離変更（ズーム）
- **ドラッグ処理は未実装**

**CameraController.java:**
- `rotateCamera()` - 画面位置基準のスナップ回転（90°/45°/15°）
- `updateAnimation()` - Lerpアニメーション
- **ドラッグ更新ロジックは未実装**

**Config.java:**
- `rotateAngleMode` - スナップ角度モード
- `cameraPitch` - ピッチ設定
- **ドラッグ感度設定は未実装**

**ClientModBusEvents.java:**
- 4つのキーバインド定義（Toggle, Rotate, Zoom Modifier, Align）
- **ドラッグ用キーバインドは未実装**

### 重要な発見
⚠️ **部分的に実装済み**: CameraStateにドラッグフィールドが存在するが、ロジックが未完成
✅ **再利用可能**: 既存のフィールドとgetter/setterを活用可能
📋 **必要な追加**: 入力処理、設定、キーバインド、更新ロジック

## Technical Decisions

### Forge 1.20.1 Best Practices（Librarian Research Results）

**1. マウスイベント処理**
- 使用するイベント: `InputEvent.MouseButton.Pre/Post`
- 中マウスボタン: `InputConstants.MOUSE_BUTTON_MIDDLE` (値=2)
- アクション判定: `GLFW.GLFW_PRESS` (1) / `GLFW.GLFW_RELEASE` (0)

**2. ドラッグ検出パターン**
- Press時: ドラッグ開始フラグON、開始位置記録
- Release時: ドラッグ終了フラグOFF
- 移動監視: `onClientTick` でマウス位置を監視
- 重要: `mc.mouseHandler.releaseMouse()` でカーソル解放

**3. カメラ回転方式**
- **推奨**: アニメーション（Lerp）による滑らかな回転
- **非推奨**: 直接yaw/pitch操作（ユーザー体験悪化）
- 既存の `updateAnimation()` パターンを再利用可能

**4. キーバインディング**
- Forge 1.20.1新API: `RegisterKeyMappingsEvent` を使用
- カテゴリ登録: `KeyMapping.Category.register()`
- 翻訳キー: `"key.topdownview.dragRotate"`
- コントロール画面で自動的に設定可能

**5. 設定連携**
- 感度設定: `ForgeConfigSpec.DoubleValue` + `defineInRange`
- 範囲制限: 0.1（遅い）〜 2.0（速い）
- 即時反映: `ModConfigEvent` + リスナーパターン
- 既存の `notifyConfigChanged()` を活用

**6. スレッド安全性**
- **全操作をクライアントスレッドで実行**
- `TickEvent.ClientTickEvent` 内で更新
- 別スレッドからのアクセス禁止

**7. Vanilla衝突防止**
- カメラタイプ: `CameraType.THIRD_PERSON_BACK`
- マウス制御: `releaseMouse()` / `grabMouse()`
- FOV固定: `ComputeFovModifierEvent` で1.0固定

### 実装構造案

```java
// InputHandler.java - ボタン押下/解放
@SubscribeEvent
public static void onMouseButton(InputEvent.MouseButton.Pre event) {
    if (event.getButton() == Config.dragButton) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            // ドラッグ開始
            ModState.CAMERA.setDragging(true);
            ModState.CAMERA.setDragStartMouseX(mc.mouseHandler.xpos());
            ModState.CAMERA.setDragStartYaw(ModState.CAMERA.getYaw());
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            // ドラッグ終了
            ModState.CAMERA.setDragging(false);
        }
    }
}

// CameraController.java - ドラッグ更新
@SubscribeEvent
public static void onClientTick(TickEvent.ClientTickEvent event) {
    if (ModState.CAMERA.isDragging()) {
        updateDragRotation();
    }
}

private static void updateDragRotation() {
    double deltaX = mc.mouseHandler.xpos() - ModState.CAMERA.getDragStartMouseX();
    float rotationChange = (float) (deltaX * Config.dragRotationSensitivity);
    float newYaw = ModState.CAMERA.getDragStartYaw() - rotationChange;
    ModState.CAMERA.setYaw(newYaw);
}
```

## Scope Boundaries
- INCLUDE: ドラッグ回転機能の実装、設定追加、キーバインド
- EXCLUDE: （未確定）
