# TopDownView MOD Context

## 1. 全体概要

Minecraft Forge 1.20.1 MOD。トップダウン視点（俯瞰カメラ）を実装し、カメラをプレイヤーの上空に配置して下向きに見下ろす視点を提供する。

**主な機能:**
- トップダウンカメラビュー（プレイヤー上空から見下ろし）
- マウス操作による視点回転
- WASD移動のカメラ向きへのマッピング
- カリング最適化（遠距離ブロックの非描画）
- 設定画面GUI（日本語/英語対応）

---

## 2. 全体の現在状態

**フェーズ:** 環境音修正完了（ビルド成功）

**決定事項:**
- 状態管理を`CameraState`に一本化（Single Source of Truth）
- `ClientForgeEvents`はトップダウンビュー有効/無効のみ管理
- MouseRaycastの後方互換メソッド削除、シンプル化
- リフレクション初期化をstatic初期化ブロックに移動
- 定数を`MathConstants`に集約
- 設定画面の文字列を翻訳キー化（国際化対応）
- **環境音問題**: `SoundEngine`と`ClientLevel`でカメラ位置をプレイヤー位置に偽装

**設定項目（Config）:**
- `cameraPitch`: カメラピッチ（角度）
- `cameraYaw`: カメラヨー（方位）
- `cullingRange`: カリング適用範囲
- `cullingHeightThreshold`: カリング保護高さ

**カメラ距離定数（CameraState）:**
- MIN: 5.0 / MAX: 50.0 / DEFAULT: 9.0

**生成JAR:** `build/libs/topdown_view-1.1.0.jar`

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
| `MathConstants.java` | 数学定数集約 | 安定 |

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
| `MouseRaycast.java` | マウスレイキャスト | シンプル化 |
| `BowTrajectoryCalculator.java` | 弓軌道計算 | MathConstants使用 |
| `gui/ConfigScreen.java` | 設定画面GUI | 翻訳キー化完了 |

### src/main/java/com/topdownview/mixin/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `CameraMixin.java` | カメラ位置/回転制御 | CameraState/MathConstants参照 |
| `GameRendererMixin.java` | レンダリング注入 | MouseRaycast.INSTANCE使用 |
| `MouseHandlerMixin.java` | マウス入力変更 | 安定 |
| `SoundSystemMixin.java` | サウンドリスナー位置偽装 | SoundEngine.updateSource/play |
| `ClientLevelSoundMixin.java` | ClientLevel.playSound偽装 | カメラ位置→プレイヤー位置 |
| `LevelRendererMixin.java` | レベル描画 | リフレクション最適化 |

### src/main/java/com/topdownview/culling/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownCuller.java` | カリング判定 | HashMap化、キャッシュ戦略改善 |
| `CullingManager.java` | カリング管理 | 安定 |

### src/main/resources/

| ファイル | 状態 |
|---------|------|
| `topdown_view.mixins.json` | 安定 |
| `topdown_view_compat.mixins.json` | 安定 |

### src/main/resources/assets/topdown_view/lang/

| ファイル | 状態 |
|---------|------|
| `en_us.json` | 設定画面キー追加済み |
| `ja_jp.json` | 設定画面キー追加済み |

---

## 4. 直近の行動履歴

### 2025-02-25 セッション: 環境音修正

**問題:** トップダウン視点で環境音（バイオーム音、洞窟音など）が聞こえない

**原因:**
- サウンドリスナー位置がカメラ位置（プレイヤー上空）に設定される
- 環境音は`AttenuationType.LINEAR`で距離減衰あり
- カメラからの距離が遠すぎる → カリング → 再生されない

**解決策:**
1. **SoundSystemMixin.java**: `SoundEngine`クラスにMixin
   - `updateSource(Camera)`: カメラの`getPosition()`, `getLookVector()`, `getUpVector()`をプレイヤー位置に偽装
   - `play(SoundInstance)`: `Listener.getListenerPosition()`をプレイヤー位置に偽装
2. **ClientLevelSoundMixin.java**: `ClientLevel`クラスにMixin
   - `playSound()`: カメラ位置をプレイヤー位置に偽装

**パッケージ名（Official Mappings）:**
- `SoundEngine`: `net.minecraft.client.sounds`
- `Listener`: `com.mojang.blaze3d.audio`
- `SoundInstance`: `net.minecraft.client.resources.sounds`
- `ClientLevel`: `net.minecraft.client.multiplayer`

**結論:** ビルド成功

### 2025-02-25 セッション: 翻訳対応

**作業内容:**
1. **言語ファイル更新**: `en_us.json`/`ja_jp.json`に設定画面用翻訳キーを追加
   - `topdown_view.config.title`: 設定画面タイトル
   - `topdown_view.config.culling_range`: カリング範囲スライダー（%s付き）
   - `topdown_view.config.culling_height_threshold`: カリング保護高さスライダー（%d付き）
2. **ConfigScreen.java修正**: ハードコード文字列を`Component.translatable()`に変更
   - 画面タイトル、スライダーラベル全て翻訳キー化

**結論:** ビルド成功、国際化対応完了

### 過去の履歴（圧縮）

- **2025-02-25 コードレビュー・リファクタリング**: 状態管理統合、MouseRaycastシンプル化、リフレクション最適化、定数集約、クリーンアップ
- **パッケージ名統一**: `com.example.examplemod` → `com.topdownview`
- **Config整理**: カリング保護高さをConfig化
