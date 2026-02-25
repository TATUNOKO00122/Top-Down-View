# TopDownView MOD Context

## 1. 全体概要

Minecraft Forge 1.20.1 MOD。トップダウン視点（俯瞰カメラ）を実装し、カメラをプレイヤーの上空に配置して下向きに見下ろす視点を提供する。

**主な機能:**
- トップダウンカメラビュー（プレイヤー上空から見下ろし）
- マウス操作による視点回転
- WASD移動のカメラ向きへのマッピング
- カリング最適化（遠距離ブロックの非描画）

---

## 2. 全体の現在状態

**フェーズ:** リファクタリング完了（ビルド成功）

**決定事項:**
- 状態管理を`CameraState`に一本化（Single Source of Truth）
- `ClientForgeEvents`はトップダウンビュー有効/無効のみ管理
- MouseRaycastの後方互換メソッド削除、シンプル化
- リフレクション初期化をstatic初期化ブロックに移動
- 定数を`MathConstants`に集約

**設定項目（Config）:**
- `cameraPitch`: カメラピッチ（角度）
- `cameraYaw`: カメラヨー（方位）
- `cullingRange`: カリング適用範囲
- `cullingHeightThreshold`: カリング保護高さ

**カメラ距離定数（CameraState）:**
- MIN: 5.0 / MAX: 50.0 / DEFAULT: 9.0

**生成JAR:** `build/libs/topdown_view-1.0.0.jar`

---

## 3. ファイル・ディレクトリ別状態

### src/main/java/com/topdownview/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownViewMod.java` | メインMODクラス | 安定 |
| `Config.java` | Forge設定管理 | 4項目 |

### src/main/java/com/topdownview/util/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `MathConstants.java` | 数学定数集約 | 新規追加 |

### src/main/java/com/topdownview/state/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `ModState.java` | 状態管理ファサード | 安定 |
| `CameraState.java` | カメラ状態（Single Source of Truth） | カメラ距離定数を集約 |
| `TimeState.java` | 時間管理 | 安定 |
| `ModStatus.java` | 有効/無効状態 | 安定 |

### src/main/java/com/topdownview/client/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `ClientForgeEvents.java` | トップダウン有効/無効のみ | 状態管理をCameraStateに委譲 |
| `CameraController.java` | カメライベント制御 | MathConstants使用 |
| `InputHandler.java` | キー/マウス入力 | CameraState参照に変更 |
| `MovementController.java` | 移動マッピング | 安定 |
| `MouseRaycast.java` | マウスレイキャスト | シンプル化（後方互換メソッド削除） |
| `BowTrajectoryCalculator.java` | 弓軌道計算 | MathConstants使用 |
| `gui/ConfigScreen.java` | 設定画面GUI | 安定 |

### src/main/java/com/topdownview/mixin/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `CameraMixin.java` | カメラ位置/回転制御 | CameraState/MathConstants参照 |
| `GameRendererMixin.java` | レンダリング注入 | MouseRaycast.INSTANCE使用 |
| `MouseHandlerMixin.java` | マウス入力変更 | 安定 |
| `SoundEngineMixin.java` | 音量補正 | 有効 |
| `LevelRendererMixin.java` | レベル描画 | リフレクション最適化（static初期化） |

### src/main/java/com/topdownview/culling/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownCuller.java` | カリング判定 | HashMap化、キャッシュ戦略改善 |
| `CullingManager.java` | カリング管理 | デバッグログ削除 |

### src/main/resources/

| ファイル | 状態 |
|---------|------|
| `topdown_view.mixins.json` | 安定 |
| `topdown_view_compat.mixins.json` | 安定 |

---

## 4. 直近の行動履歴

### 2025-02-25 セッション: コードレビュー・リファクタリング

**作業内容:**
1. **状態管理の統合**: カメラ距離を`CameraState`に一本化、`ClientForgeEvents`の重複排除
2. **MouseRaycastシンプル化**: 後方互換staticメソッド5つ削除、`INSTANCE.update()`パターンに統一
3. **リフレクション最適化**: `LevelRendererMixin`のClass.forNameをstatic初期化ブロックに移動
4. **キャッシュ戦略改善**: `ConcurrentHashMap`→`HashMap`、時間ベース全クリア→距離ベース部分クリア
5. **定数集約**: `MathConstants`クラス新規作成
6. **クリーンアップ**: デバッグログ削除、AssertionError→IllegalStateException変更

**結論:**
- 全フェーズのビルド成功
- コードの可読性・保守性向上

### 過去の履歴（圧縮）

- パッケージ名統一: `com.example.examplemod` → `com.topdownview`
- Config整理: カリング保護高さをConfig化
- サウンド系Mixin削除: デバッグ出力のみのため
