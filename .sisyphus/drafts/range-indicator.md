# Draft: Range Indicator Implementation

## Requirements (confirmed)
- **射程外赤表示オンオフ**: 設定で制御、デフォルトオフ
- **動的射程判定**: 武器の射程を使用（ForgeMod.ENTITY_REACH）
- **オフセット設定**: Config.attackRangeをオフセットとして使用

## Technical Decisions
- **タブ配置**: Visualタブ
- **フォールバック**: Config.attackRangeを使用（属性取得不可時）
- **オフセット計算**: `baseReach + (Config.attackRange - 3.0)`
  - 3.0はデフォルト値、オフセットとして加減算

## Scope Boundaries
- INCLUDE:
  - 新規設定 `rangeIndicatorEnabled` (Boolean, default: false)
  - 動的射程取得ロジック
  - Visualタブへのトグルボタン追加
  - 日英翻訳キー追加
- EXCLUDE:
  - 既存のattack_rangeスライダーの削除
  - Movementタブの変更
  - 他のターゲットハイライト機能の変更

## Research Findings
- **ForgeMod.ENTITY_REACH**: Forge 1.20.1でプレイヤーの攻撃射程を取得
- **IForgePlayer#getEntityReach()**: クリエイティブモード補正込みの射程
- **属性取得パターン**: `player.getAttributeValue(ForgeMod.ENTITY_REACH.get())`

## Data Flow
```
TargetHighlightRenderer.updateTargetState()
  ↓ player, target を渡す
TargetHighlightState.getAttackRange(Player)
  ↓ 設定チェック + 動的射程計算
射程判定 → 色決定 (getOutlineColor)
  ↓
LevelRendererMixin.onRenderLevelOutline()
  ↓
アウトライン描画
```

## Open Questions
- なし（全て確認済み）
