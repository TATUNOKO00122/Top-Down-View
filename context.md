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

**フェーズ:** Config整理完了・安定動作中

**決定事項:**
- カメラ距離設定（デフォルト/最小/最大）は機能していないため削除 → 定数化
- カリング保護高さをConfig化（整数、範囲0-10、デフォルト2）
- サウンド問題はFlerovium MODとの非互換性が原因（無効化で回避）

**設定項目（現在有効）:**
- `cameraPitch`: カメラピッチ（角度）
- `cameraYaw`: カメラヨー（方位）
- `cullingRange`: カリング適用範囲
- `cullingHeightThreshold`: カリング保護高さ（プレイヤー足元からの高さ）

**カメラ距離定数（ClientForgeEvents）:**
- MIN: 5.0 / MAX: 50.0 / DEFAULT: 9.0

---

## 3. ファイル・ディレクトリ別状態

### src/main/java/com/example/examplemod/

| ファイル | 役割 | 直近の変更 |
|---------|------|-----------|
| `Config.java` | Forge設定管理 | min/max/default距離削除、cullingHeightThreshold追加（整数） |

### src/main/java/com/example/examplemod/client/

| ファイル | 役割 | 直近の変更 |
|---------|------|-----------|
| `ClientForgeEvents.java` | クライアント状態管理 | カメラ距離定数（MIN/MAX/DEFAULT）を追加 |
| `gui/ConfigScreen.java` | 設定画面GUI | 距離スライダー削除、カリング保護高さスライダー追加（IntConfigSlider） |

### src/main/java/com/example/examplemod/state/

| ファイル | 役割 | 直近の変更 |
|---------|------|-----------|
| `CameraState.java` | カメラ状態管理 | get_default_camera_distance()をClientForgeEvents参照に変更 |

### src/main/java/com/example/examplemod/culling/

| ファイル | 役割 | 直近の変更 |
|---------|------|-----------|
| `TopDownCuller.java` | カリング判定 | HEIGHT_THRESHOLD_STEP定数削除 → Config.cullingHeightThreshold使用 |

### src/main/java/com/example/examplemod/mixin/

| ファイル | 役割 | 直近の変更 |
|---------|------|-----------|
| `MinecraftClientMixin.java` | Minecraft.tick注入 | 未使用@Shadowフィールド(player, hitResult)削除 |

---

## 4. 直近の行動履歴

### 2025-02-25 セッション: Config整理

**作業内容:**
1. `minCameraDistance`, `maxCameraDistance`をConfigから削除 → 定数化
2. `cameraDistance`（デフォルト距離）を削除 → 機能していなかったため
3. カリング保護高さ（`cullingHeightThreshold`）をConfig化（整数、0-10、デフォルト2）
4. Mixinクラッシュ修正: 未使用@Shadowフィールド削除

**結論:**
- Configは4項目に整理（pitch, yaw, cullingRange, cullingHeightThreshold）
- カメラ距離は定数管理（スクロールでゲーム内調整可能）

### 過去: サウンド問題調査

- 原因: Flerovium MODとの非互換性
- 対策: Flerovium無効化
- 詳細: `SOUND_INVESTIGATION_REPORT.md`
