# TopDownView MOD Context

## 1. 全体概要

Minecraft Forge 1.20.1 MOD。トップダウン視点（俯瞰カメラ）を実装し、カメラをプレイヤーの上空に配置して下向きに見下ろす視点を提供する。

**主な機能:**
- トップダウンカメラビュー（プレイヤー上空から見下ろし）
- マウス操作による視点回転
- WASD移動のカメラ向きへのマッピング
- ハイブリッドカリング（円柱カリング+天井のみカリング）
- 移動方向追従型の保護帯（帯状領域のブロックを保護）
- 設定画面GUI（日本語/英語対応）

---

## 2. 全体の現在状態

**フェーズ:** ハイブリッドカリング方式実装完了

**決定事項:**
- カリング方式: ハイブリッド方式
  - カメラ↔プレイヤー間(t<=1.0): 円柱カリング（従来通り）
  - プレイヤーより奥(t>1.0): プレイヤー周囲円柱内の天井のみカリング
- 保護方式: 移動方向に沿った帯状領域
- 保護高さ: 全体保護（baseProtectionHeight）と移動方向保護（directionalProtectionHeight）を分離
- 天井判定: ceilingHeight（プレイヤー足元からの高さで天井と判定）
- 停止時: 最後の移動方向を無期限保持
- キャッシュクリア: 移動方向が5°以上変化したら実行
- カリングパラメータは全て整数（ブロック単位）で統一
- プレイヤー位置・カメラ位置はブロック座標（中心）に丸めて判定
- 状態管理を`CameraState`に一本化（Single Source of Truth）

**設定項目（Config）:**
| パラメータ | 型 | 初期値 | 範囲 | 説明 |
|-----------|-----|-------|------|------|
| `cameraPitch` | double | 45.0 | 0.0〜90.0 | カメラピッチ（角度） |
| `cameraYaw` | double | 0.0 | -180.0〜180.0 | カメラヨー（方位） |
| `ceilingHeight` | int | 2 | 0〜10 | 天井カリング高さ（プレイヤー周囲円柱用） |
| `baseProtectionHeight` | int | 1 | 0〜10 | 足元からの全体保護高さ（無条件表示） |
| `cylinderRadius` | int | 3 | 1〜10 | 円柱半径（ブロック数） |
| `cylinderExtension` | int | 5 | 0〜20 | プレイヤー周囲円柱半径（ブロック数） |

**カメラ距離定数（CameraState）:**
- MIN: 5.0 / MAX: 50.0 / DEFAULT: 9.0

**生成JAR:** `build/libs/topdown_view-1.2.0.jar`

---

## 3. ファイル・ディレクトリ別状態

### src/main/java/com/topdownview/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownViewMod.java` | メインMODクラス | 安定 |
| `Config.java` | Forge設定管理 | cullingRange削除、ceilingHeight追加済み |

### src/main/java/com/topdownview/util/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `MathConstants.java` | 数学定数集約 | 安定 |

### src/main/java/com/topdownview/state/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `ModState.java` | 状態管理ファサード | 安定 |
| `CameraState.java` | カメラ状態（Single Source of Truth） | 安定 |
| `TimeState.java` | 時間管理 | 安定 |
| `ModStatus.java` | 有効/無効状態 | 安定 |

### src/main/java/com/topdownview/client/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `ClientForgeEvents.java` | トップダウン有効/無効のみ | 安定 |
| `CameraController.java` | カメライベント制御 | 安定 |
| `InputHandler.java` | キー/マウス入力 | 安定 |
| `MovementController.java` | 移動マッピング | 安定 |
| `MouseRaycast.java` | マウスレイキャスト | 安定 |
| `BowTrajectoryCalculator.java` | 弓軌道計算 | 安定 |
| `gui/ConfigScreen.java` | 設定画面GUI | スライダー4件（全てint型） |

### src/main/java/com/topdownview/mixin/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `CameraMixin.java` | カメラ位置/回転制御 | 安定 |
| `GameRendererMixin.java` | レンダリング注入 | 安定 |
| `MouseHandlerMixin.java` | マウス入力変更 | 安定 |
| `SoundSystemMixin.java` | サウンドリスナー位置偽装 | 安定 |
| `ClientLevelSoundMixin.java` | ClientLevel.playSound偽装 | 安定 |
| `LevelRendererMixin.java` | レベル描画 | 安定 |

### src/main/java/com/topdownview/culling/

| ファイル | 役割 | 状態 |
|---------|------|------|
| `TopDownCuller.java` | カリング判定 | ハイブリッド方式実装済み |
| `CullingManager.java` | カリング管理 | 安定 |

### src/main/resources/assets/topdown_view/lang/

| ファイル | 状態 |
|---------|------|
| `en_us.json` | 翻訳キー4件 |
| `ja_jp.json` | 翻訳キー4件 |

---

## 4. 直近の行動履歴

### 2025-02-26: 移動方向保護削除

**目的:** 移動方向保護機能の削除によるシンプル化

**削除内容:**
- `Config.java`: directionalProtectionHeight, protectionWidth, protectionDistance 削除
- `ConfigScreen.java`: 該当スライダー3件削除
- 翻訳ファイル: 該当キー3件削除

**結論:** ビルド成功

### 2025-02-26: ハイブリッドカリング方式実装

**目的:** 新旧カリングロジックの長所を組み合わせたハイブリッド方式の実装

**問題:** 前の方式は天井がカリングされない、現在の方式は奥の壁/地面もカリングされる

**実装内容:**
- `TopDownCuller.java`: 投影位置`t`で判定分岐（t<=1.0: 円柱カリング、t>1.0: 天井のみカリング）
- `Config.java`: `cullingRange`削除、`ceilingHeight`追加
- `ConfigScreen.java`、翻訳ファイル: スライダー・キー更新

**結論:** ビルド成功

### 過去の履歴（圧縮）

- カリングパラメータ整数化: Config項目をint型統一
- 移動方向保護帯実装: 扇形→帯状保護、方向追従キャッシュ最適化
- シリンダーキャスト実装: 円柱状判定、ブロック座標丸め
- 環境音修正: SoundEngine/ClientLevelでカメラ位置偽装
- 翻訳対応: 設定画面の文字列を翻訳キー化
- リファクタリング: 状態管理統合、定数集約、パッケージ名統一
