# Changelog

## [1.3.5] - 2026-03-15

### 新機能

- **カメラ追従遅延**: プレイヤーの移動時にカメラがスムーズに追従（Y軸・X軸・Z軸個別設定可能、3D酔い対策）
- **クリック移動システム（全面改修）**: 従来の右クリック移動から左クリックベースに刷新
  - 自動判定: 敵対Mob → 攻撃 / 村人・動物 → インタラクト / その他 → 移動
  - インタラクト可能ブロック対応: チェストやドアなどを左クリックで開ける
  - 破壊モード: 専用キーでブロック破壊
  - ホールドモード: 左クリック押下中は継続追従、離すと停止
  - 移動先ハイライト: クリックした目的地を視覚的に表示
  - 自動ダッシュ: 目的地まで自動でダッシュ移動

### 改善

- 弓・クロスボウ・トライデントの弾道計算を統一・リファクタリング
- クリック移動時の自動ジャンプ精度を改善
- GUI/キーバインドの命名をより直感的に変更

### 修正

- スレッド安全性の問題を修正（volatileフィールド追加）
- Null安全性を改善（nullチェック追加）

---

## [1.3.4] - 2026-03-10

### Added
- **マイニングモード（Mining Mode）**: 断面表示機能（アリの巣風表示）を実装
  - 地下採掘時に周囲を可視化しやすくなる断面ビュー
  - マイニングモード時のカメラ垂直角度（ピッチ）をカスタマイズ可能
- **設定ファイル直接読み込み**: カメラ距離を設定ファイルから直接読み込む機能
- **ワールド参加時の状態リセット**: ワールド参加時にModStateを自動的にリセット
- **ログ出力強化**: 設定保存・読み込み時のログ出力を強化

### Fixed
- **チャンク消失グリッチ修正**: カメラがブロックに埋まった際のチャンク消失問題を修正（Cave Culling & Embeddium Occlusion Culling対応）
- **カリング更新停止問題**: プレイヤー追従の一貫性を確保し、カリングが停止する問題を修正
- **重大パフォーマンス問題**: CameraStateにおける毎フレームファイルI/O問題を修正
- **弓の軌道計算**: ゼロ除算エラーを防止
- **マウスボタン入力**: ハードコードをキー設定参照に修正
- **NullPointerException防止**: CameraMixinにentityのnullチェックを追加
- **リフレクション初期化**: 再試行ロジックとスレッド安全性を改善

### Performance
- レイキャスト・レンダリング・カリングキャッシュのパフォーマンス最適化
- キャッシュクリア処理の最適化

### Changed
- **Configカプセル化**: Configフィールドのカプセル化とデッドコード削除
- **統一されたConfigアクセス**: 全クライアントクラスでConfig getter使用に統一
- **定数化**: カリング用のマジックナンバーをMathConstantsに追加
- **コードクリーンアップ**: state/pathfindingパッケージのデッドコード削除

---

## [1.3.3] - 2026-03-08

### Fixed
- **攻撃/使用キー入れ替え対応**: トップダウンビューで攻撃/使用キーが入れ替えられている設定を正しく反映するように修正
- **弓のクラッシュ防止**: プレイヤーの真上または真下にターゲットがいる場合のゼロ除算エラーを修正

---

## [1.3.2] - 2026-03-07

### Added
- **自動カメラ回転**: 移動方向に合わせてカメラが自動的に回転（設定で切り替え可能）
- **マウスドラッグ回転**: マウスドラッグでカメラ角度を自由に調整
- **武器別攻撃範囲**: 武器タイプごとに攻撃範囲を設定可能（剣、弓など）
- **自動カメラ整列**: プレイヤーの向いている方向にカメラを自動的に合わせる
- **ポーズメニューの設定ボタン**: ポーズメニューから設定画面へのショートカット

### Changed
- **設定画面UX/UI改善**: より直感的でレスポンシブなインターフェース
- **デフォルト有効設定**: ワールド読み込み時にトップダウンビューを自動的に有効にするオプション
- **Mixinとコントローラーのクリーンアップ**: コード構造を改善

---

## [1.3.1] - 2026-03-04

### Fixed
- キーボードとマウスの両方の入力を正しく処理するように入力ハンドリングを修正

### Changed
- カリングシステムの大規模リファクタリング: 保守性とパフォーマンスを向上
- コード構成を改善し、複雑さを軽減

---

## [1.3.0-beta] - 2025-03-03

### Added

#### クリック移動（Click-to-Move）システム
- **右クリック自動移動**: 地面を右クリックすると目的地まで自動的に移動
  - 直線移動アルゴリズムによるシンプルな移動処理
  - 自動ジャンプサポート（設定で強制可能）
  - エンティティ攻撃統合（敵モブを右クリックで追跡・攻撃）
  - 到達判定距離、攻撃範囲の設定項目
- **視覚的フィードバック**: 目的地マーカー表示
- **状態管理**: `ClickToMoveState`による移動状態の一元管理

#### 透かしいブロックレンダリング
- **境界フェード（Boundary Fade）**: カリング境界付近のブロックを滑らかにフェードアウト
  - 距離ベースの透明度計算
  - フェード開始位置、透明度の設定項目
  - 半透明ブロックに対するインタラクション（当たり判定）維持
- **トラップドア半透明化**: プレイヤーの視界を遮るトラップドアを半透明表示（オプション）
  - 透明度のカスタマイズ可能

#### カリングロジックの大幅改善
- **楕円柱キャスト**: プレイヤー周囲の保護領域を楕円柱として定義
  - 水平方向半径、垂直方向半径の個別設定
  - 前方シフトによる視認性向上
- **逆ピラミッド型保護**: カメラからプレイヤーへの視線を遮るブロックを保護
  - 保護傾斜角度の設定
- **ヒステリシスベースの更新**: 位置更新のノイズを低減
- **地面レベル追跡**: 正確な地面判定によるカリング精度向上

#### Baritone統合（実験的）
- **経路探索エンジン**: A*アルゴリズムによる経路探索（現在は無効化）
- **動的回避システム**: 障害物回避ロジック（現在は無効化）
- **衝突判定**: ブロック衝突検出システム
- 注: 現在は直線移動のみ使用、将来のバージョンで有効化予定

#### 設定システムの拡張
- **新しい設定項目**:
  - カリング: `cylinderRadiusHorizontal`, `cylinderRadiusVertical`, `cylinderForwardShift`
  - クリック移動: `clickToMoveEnabled`, `arrivalThreshold`, `attackRange`, `forceAutoJump`
  - 透かし表示: `trapdoorTranslucencyEnabled`, `trapdoorTransparency`
  - フェード: `fadeEnabled`, `fadeStart`, `fadeNearAlpha`
- **設定GUI**: 全ての新しい設定項目に対応したスライダーとトグル

### Changed
- **カメラコントローラー**: ハンド描画キャンセル処理の最適化
- **移動コントローラー**: クリック移動統合による移動処理の改善
- **入力ハンドラー**: 右クリックイベントの拡張
- **クリックアクションハンドラー**: エンティティインタラクションの強化
- **ターゲットハイライトレンダラー**: 目的地マーカー描画の追加
- **カリングマネージャー**: インターフェースの簡素化

### Technical Changes
- **パッケージ構造**:
  - `baritone/`: Baritone統合クラス
  - `pathfinding/`: 経路探索エンジン（CollisionDetector, LocalAvoidanceEngine, PathfindingEngine, etc.）
  - `state/`: `ClickToMoveState`, `PathfindingState`の追加
- **新規クラス**:
  - `ClickToMoveController`: クリック移動のメイン制御
  - `TranslucentBlockRenderer`: 半透明ブロック描画
  - `InteractableBlocks`: インタラクション可能ブロック管理
- **Mixins**: 
  - `EntityAccessor`: エンティティフィールドアクセス
  - `BlockRendererMixin`: ブロックレンダリングフック
- **依存関係**: Baritone APIForge 1.10.3を追加

### Removed
- `api/MinecraftClientAccessor.java`: 未使用のため削除
- `ClientServices.java`: 未使用のため削除
- `ClientForgeEvents.java`の一部メソッド: 他クラスへ統合
- `Culler.java`インターフェース: 直接`TopDownCuller`使用に変更

### Fixed
- 透かしいレンダリングでのカラー対応（Tint乗算の修正）
- 半透明ブロックのクリック判定制限を解除
- カリング境界のフェード処理の視覚的品質向上

---

## [1.2.0] - 2025-02-25

### Fixed
- **Ambient Sound Fix**: Fixed ambient sounds (biome sounds, cave sounds, etc.) not being audible in top-down view mode
  - The sound listener position is now correctly set to the player position instead of the camera position
  - This prevents sounds from being culled due to excessive distance from the camera

### Technical Changes
- Added `SoundSystemMixin.java`: Redirects camera position, look vector, and up vector in `SoundEngine.updateSource()` and `Listener.getListenerPosition()` in `SoundEngine.play()`
- Added `ClientLevelSoundMixin.java`: Redirects camera position in `ClientLevel.playSound()`

---

## [1.1.0] - 2025-02-25

### Added
- **Internationalization Support**: Added translation keys for config screen
  - Japanese (`ja_jp.json`) and English (`en_us.json`) language files
  - Config screen title and slider labels are now translatable

### Changed
- **State Management Refactoring**: Unified state management into `CameraState` (Single Source of Truth)
- **Code Cleanup**: 
  - Removed backward compatibility methods from `MouseRaycast`
  - Moved reflection initialization to static initializer blocks
  - Consolidated constants into `MathConstants`
  - Package renamed from `com.example.examplemod` to `com.topdownview`

---

## [1.0.0] - Initial Release

### Features
- Top-down camera view mode (bird's eye view)
- Mouse-controlled camera rotation
- WASD movement mapped to camera orientation
- Block culling optimization for distant blocks
- Configuration GUI accessible via Mod Menu or keybind
- Configurable culling range and height threshold
