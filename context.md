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

**フェーズ:** リリース準備完了（ビルド成功）

**決定事項:**
- パッケージ名を `com.topdownview` に統一
- サウンド系デバッグMixinを削除（効果限定的のため）
- カメラ距離設定は定数化（ConfigではなくClientForgeEventsで管理）
- カリング保護高さをConfig化

**設定項目（Config）:**
- `cameraPitch`: カメラピッチ（角度）
- `cameraYaw`: カメラヨー（方位）
- `cullingRange`: カリング適用範囲
- `cullingHeightThreshold`: カリング保護高さ

**カメラ距離定数（ClientForgeEvents）:**
- MIN: 5.0 / MAX: 50.0 / DEFAULT: 9.0

**生成JAR:** `build/libs/topdown_view-1.0.0.jar`

---

## 3. ファイル・ディレクトリ別状態

### src/main/java/com/topdownview/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownViewMod.java` | メインMODクラス | 安定 |
| `Config.java` | Forge設定管理 | 4項目（pitch, yaw, cullingRange, cullingHeightThreshold） |

### src/main/java/com/topdownview/client/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `ClientForgeEvents.java` | クライアント状態管理 | デバッグコード削除済み |
| `CameraController.java` | カメライベント制御 | 安定 |
| `InputHandler.java` | キー/マウス入力 | 安定 |
| `MovementController.java` | 移動マッピング | 安定 |
| `MouseRaycast.java` | マウスレイキャスト | 安定 |
| `gui/ConfigScreen.java` | 設定画面GUI | カリング範囲・保護高さスライダー |

### src/main/java/com/topdownview/mixin/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `CameraMixin.java` | カメラ位置/回転制御 | 安定 |
| `GameRendererMixin.java` | レンダリング注入 | 安定 |
| `MouseHandlerMixin.java` | マウス入力変更 | 安定 |
| `SoundEngineMixin.java` | 音量補正（プレイヤー距離ベース） | 有効 |
| `LevelRendererMixin.java` | レベル描画 | 安定 |
| その他 | エンティティ/ブロック描画 | 安定 |

### src/main/java/com/topdownview/culling/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownCuller.java` | カリング判定 | Config.cullingHeightThreshold使用 |
| `CullingManager.java` | カリング管理 | 安定 |

### src/main/resources/

| ファイル | 状態 |
|---------|------|
| `topdown_view.mixins.json` | パッケージ名更新済み、インデント修正済み |
| `topdown_view_compat.mixins.json` | パッケージ名更新済み |

---

## 4. 直近の行動履歴

### 2025-02-25 セッション: リリース前レビュー・修正

**作業内容:**
1. サウンド系Mixin削除（6ファイル）: デバッグ出力のみのため削除
   - SoundSystemMixin, SoundManagerMixin, ChannelMixin
   - ClientLevelMixin, ClientLevelSoundMixin, ClientPacketListenerMixin
2. デバッグコード削除: ClientForgeEventsのSystem.out.println削除
3. build.gradle修正: ローカル絶対パス（Sound Physics JAR）削除
4. パッケージ名変更: `com.example.examplemod` → `com.topdownview`
5. mixin設定ファイル更新: パッケージ名・インデント修正

**結論:**
- ビルド成功（topdown_view-1.0.0.jar生成）
- リリース準備完了

### 過去の履歴（圧縮）

- Config整理: カメラ距離は定数化、カリング保護高さをConfig化
- サウンド問題: Flerovium MOD非互換が原因（無効化で回避）
