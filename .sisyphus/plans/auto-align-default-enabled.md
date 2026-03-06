# Auto Align to Movement + Default Enabled 機能実装計画

## TL;DR

> **Quick Summary**: dev2ブランチから「自動カメラ整列機能」と「デフォルト有効化設定」の2つの機能をmainブランチに移植する。並列実行可能なタスクを活用し、2つのコミットで完了する。
> 
> **Deliverables**: 
> - 自動カメラ整列機能（Vキーで手動整列、移動時の自動整列）
> - デフォルト有効化設定（ゲーム起動時にトップダウン視点を自動有効化）
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 3 waves per feature
> **Critical Path**: Config → State/Events → Controller → InputHandler

---

## Context

### Original Request
mainブランチにdev2の以下の2つの機能を実装する計画を作成：
1. 自動カメラ整列機能 (Auto Align to Movement)
2. デフォルト有効化設定 (Default Enabled)

### Interview Summary
**Key Discussions**:
- **テスト戦略**: 手動テスト（`./gradlew runClient`）を採用
- **dev2の扱い**: そのまま移植（mainの既存構造との整合性を保持）
- **コミット戦略**: 機能単位で2コミット
- **互換性**: Forge Configが自動処理（マイグレーション不要）

### Self-Review: Gap Analysis

**Identified Gaps** (addressed):
- **Gap 1**: dev2には透明化設定の変更も含まれるが、今回は対象外 → 明確に除外
- **Gap 2**: 既存のConfig.javaとdev2のConfig.javaで構造が異なる → mainの構造を維持しつつ新規フィールドを追加
- **Gap 3**: InputHandlerからCameraControllerへのロジック移動 → リファクタリングとして明示

---

## Work Objectives

### Core Objective
dev2ブランチの「自動カメラ整列機能」と「デフォルト有効化設定」をmainブランチに移植し、既存のコード構造・パターンとの整合性を保つ。

### Concrete Deliverables
- **機能1**: 自動カメラ整列機能
  - Config: 6つの設定項目追加
  - CameraState: 4つのフィールド + getter/setter
  - ClientModBusEvents: Vキー追加
  - CameraController: alignCameraToMovement() + アニメーション速度対応
  - InputHandler: Vキー処理 + ロジック移動
- **機能2**: デフォルト有効化設定
  - Config: defaultEnabled追加 + onLoad()での適用
  - ModStatus: initializedフィールド
  - CameraController: 初期化/無効化メソッド
  - InputHandler: toggleロジック調整

### Definition of Done
- [ ] `./gradlew build` が成功
- [ ] `./gradlew runClient` で以下が動作確認できる:
  - [ ] Vキーでカメラが進行方向に整列
  - [ ] 移動中にカメラが自動的に進行方向に回転
  - [ ] 設定でdefaultEnabled=true時、ゲーム起動でトップダウン視点が有効
  - [ ] 設定画面で新規設定項目が表示される

### Must Have
- dev2のコードをそのまま移植
- mainの既存構造・パターンとの整合性
- シングルトンパターンの維持
- バリデーション付きsetter

### Must NOT Have (Guardrails)
- 透明化設定の変更を含めない（dev2の他の変更は対象外）
- 既存のConfig.javaの構造を大幅に変更しない
- 新しい依存関係を追加しない
- コードの重複を作らない

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: NO（手動テストのみ）
- **User wants tests**: Manual-only
- **Framework**: なし（`./gradlew runClient`で手動確認）

### If Automated Verification Only (NO User Intervention)

**機能1: 自動カメラ整列機能**

**For TUI/CLI changes** (using interactive_bash):
```
# Agent executes via bash:
1. Command: ./gradlew build
2. Wait for: "BUILD SUCCESSFUL" in output
3. Assert: Exit code 0
```

**For Frontend/UI changes** (using playwright skill - Minecraft GUI):
```
# Agent executes via Minecraft client:
1. Command: ./gradlew runClient
2. Wait for: Minecraft main menu appears
3. Create new world or load existing
4. Press F4 to enable top-down view
5. Press V to trigger auto-align
6. Assert: Camera rotates to movement direction
7. Walk in a direction for >1 second
8. Assert: Camera automatically aligns to movement direction
```

**機能2: デフォルト有効化設定**

```
# Agent executes via Minecraft client:
1. Set defaultEnabled=true in config
2. Restart Minecraft: ./gradlew runClient
3. Load a world
4. Assert: Top-down view is automatically enabled (no F4 press needed)
5. Assert: Camera is in third-person mode
```

**Evidence to Capture:**
- [ ] `./gradlew build` の出力（成功確認）
- [ ] Minecraftでの動作確認ログ

---

## Execution Strategy

### Parallel Execution Waves

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    機能1: 自動カメラ整列機能
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Wave 1 (Start Immediately):
└── Task 1: Config.java - autoAlign設定追加
    └── 依存: なし
    └── ブロック: 全タスク

Wave 2 (After Wave 1):
├── Task 2: CameraState.java - autoAlign用フィールド追加
│   └── 依存: Task 1
│   └── ブロック: Task 4
└── Task 3: ClientModBusEvents.java - Vキー追加
    └── 依存: なし（Wave 1と並列可）
    └── ブロック: Task 5

Wave 3 (After Wave 2):
└── Task 4: CameraController.java - alignCameraToMovement()追加
    └── 依存: Task 1, Task 2
    └── ブロック: Task 5

Wave 4 (After Wave 3):
└── Task 5: InputHandler.java - Vキー処理 + ロジック移動
    └── 依存: Task 3, Task 4
    └── ブロック: なし（機能1完了）

→ COMMIT 1: 「自動カメラ整列機能の実装」

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                 機能2: デフォルト有効化設定
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Wave 5 (After Commit 1):
├── Task 6: Config.java - defaultEnabled追加
│   └── 依存: なし
│   └── ブロック: Task 8
└── Task 7: ModStatus.java - initializedフィールド追加
    └── 依存: なし
    └── ブロック: Task 8

Wave 6 (After Wave 5):
├── Task 8: CameraController.java - 初期化ロジック追加
│   └── 依存: Task 6, Task 7
│   └── ブロック: Task 9
└── Task 9: InputHandler.java - toggleロジック調整
    └── 依存: Task 8
    └── ブロック: なし（機能2完了）

→ COMMIT 2: 「デフォルト有効化設定の実装」

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Critical Path: Task 1 → Task 2 → Task 4 → Task 5 → (commit 1) → Task 6 → Task 8 → Task 9
Parallel Speedup: ~30% faster than sequential
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1: Config (autoAlign) | None | 2, 4 | - |
| 2: CameraState (autoAlign) | 1 | 4 | 3 |
| 3: ClientModBusEvents (V key) | None | 5 | 2 |
| 4: CameraController (align method) | 1, 2 | 5 | - |
| 5: InputHandler (V key + refactor) | 3, 4 | None | - |
| 6: Config (defaultEnabled) | None | 8 | 7 |
| 7: ModStatus (initialized) | None | 8 | 6 |
| 8: CameraController (init logic) | 6, 7 | 9 | - |
| 9: InputHandler (toggle refactor) | 8 | None | - |

### Agent Dispatch Summary

| Wave | Tasks | Recommended Approach |
|------|-------|---------------------|
| 1 | 1 | Sequential (blocks all) |
| 2 | 2, 3 | Parallel execution |
| 3 | 4 | Sequential |
| 4 | 5 | Sequential |
| 5 | 6, 7 | Parallel execution |
| 6 | 8, 9 | Sequential (9 depends on 8) |

---

## TODOs

---

### 機能1: 自動カメラ整列機能

---

- [ ] 1. Config.java - autoAlign設定追加

  **What to do**:
  - 6つの設定項目を追加:
    - `AUTO_ALIGN_TO_MOVEMENT_ENABLED` (boolean, default: false)
    - `AUTO_ALIGN_ANGLE_THRESHOLD` (int, 0-90, default: 15)
    - `AUTO_ALIGN_COOLDOWN_TICKS` (int, 0-100, default: 30)
    - `STABLE_DIRECTION_ANGLE` (int, 5-60, default: 15)
    - `STABLE_DIRECTION_TICKS` (int, 5-60, default: 20)
    - `AUTO_ALIGN_ANIMATION_SPEED` (double, 0.05-0.5, default: 0.1)
  - Runtime values（public static）を追加
  - onLoad()で値をロード
  - save()で値を保存

  **Must NOT do**:
  - 既存の設定項目の順序を変更しない
  - 他のdev2の設定（透明化等）を追加しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 既存パターンへのフィールド追加、複雑なロジックなし
  - **Skills**: `[]`
    - 特別なスキル不要（Forge Configパターンは既存コードを参照）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (Wave 1)
  - **Blocks**: Tasks 2, 4
  - **Blocked By**: None

  **References**:
  **Pattern References**:
  - `Config.java:28-30` - IntValue定義パターン（defineInRange）
  - `Config.java:41-43` - BooleanValue定義パターン（define）
  - `Config.java:106-120` - Runtime values定義パターン
  - `Config.java:128-143` - onLoad()での値ロードパターン
  - `Config.java:146-163` - save()での値保存パターン

  **Acceptance Criteria**:
  - [ ] 6つの設定項目が定義されている
  - [ ] 各設定に適切なcomment、default値、rangeが設定されている
  - [ ] Runtime values（public static）が追加されている
  - [ ] onLoad()で全ての値がロードされる
  - [ ] save()で全ての値が保存される
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 5完了後にコミット)

---

- [ ] 2. CameraState.java - autoAlign用フィールド追加

  **What to do**:
  - 4つのフィールドを追加:
    - `lastAutoAlignTick` (long, default: 0)
    - `lastMovementDirection` (float, default: 0.0f)
    - `stableDirectionTicks` (int, default: 0)
    - `isAutoAlignAnimation` (boolean, default: false)
  - 各フィールドのgetterを追加
  - 各フィールドのsetterを追加（必要に応じてバリデーション）
  - reset()メソッドに追加

  **Must NOT do**:
  - 既存のフィールドの順序を変更しない
  - 過剰なバリデーションを追加しない（dev2の実装に従う）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 既存パターンへのフィールド追加
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 3)
  - **Blocks**: Task 4
  - **Blocked By**: Task 1

  **References**:
  **Pattern References**:
  - `CameraState.java:34-44` - フィールド定義パターン
  - `CameraState.java:49-93` - Getter定義パターン
  - `CameraState.java:95-154` - Setter定義パターン
  - `CameraState.java:176-189` - reset()メソッドパターン

  **Dev2 Reference**:
  - `dev2:CameraState.java:45-48` - 追加フィールド
  - `dev2:CameraState.java:107-126` - 追加getter
  - `dev2:CameraState.java:157-178` - 追加setter

  **Acceptance Criteria**:
  - [ ] 4つのフィールドが追加されている
  - [ ] 各フィールドにgetterが実装されている
  - [ ] 各フィールドにsetterが実装されている
  - [ ] reset()でフィールドがリセットされる
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 5完了後にコミット)

---

- [ ] 3. ClientModBusEvents.java - Vキー追加

  **What to do**:
  - ALIGN_TO_MOVEMENT_KEYを追加:
    - Key name: "key.topdown_view.align_to_movement"
    - Default key: GLFW.GLFW_KEY_V
    - Category: "key.categories.topdown_view"
  - registerKeys()で登録

  **Must NOT do**:
  - 既存のKeyMappingの順序を変更しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 既存パターンへのKeyMapping追加
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 2)
  - **Blocks**: Task 5
  - **Blocked By**: None

  **References**:
  **Pattern References**:
  - `ClientModBusEvents.java:14-17` - KeyMapping定義パターン
  - `ClientModBusEvents.java:30-34` - registerKeys()パターン

  **Dev2 Reference**:
  - `dev2:ClientModBusEvents.java:29-32` - ALIGN_TO_MOVEMENT_KEY定義
  - `dev2:ClientModBusEvents.java:37` - 登録

  **Acceptance Criteria**:
  - [ ] ALIGN_TO_MOVEMENT_KEYが定義されている
  - [ ] registerKeys()で登録されている
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 5完了後にコミット)

---

- [ ] 4. CameraController.java - alignCameraToMovement()追加

  **What to do**:
  - `MOVEMENT_THRESHOLD`定数を追加 (0.01)
  - `alignCameraToMovement()`メソッドを追加:
    - クールダウンチェック
    - 移動速度チェック
    - 方向安定性チェック
    - 角度差チェック
    - 自動整列実行
  - `updateAnimation()`を修正:
    - isAutoAlignAnimationチェックを追加
    - 自動整列時はautoAlignAnimationSpeedを使用
    - 完了時にsetAutoAlignAnimation(false)を呼ぶ
  - `rotateCamera()`を修正:
    - setAutoAlignAnimation(false)を追加（手動回転時）
  - `onClientTick()`を修正:
    - 自動整列呼び出しを追加

  **Must NOT do**:
  - 既存のメソッドシグネチャを変更しない
  - 過剰なリファクタリングをしない

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: 中規模のロジック追加、既存パターンに従う
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (Wave 3)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1, Task 2

  **References**:
  **Pattern References**:
  - `CameraController.java:68-89` - updateAnimation()パターン
  - `CameraController.java:95-135` - rotateCamera()パターン
  - `CameraController.java:44-63` - onClientTick()パターン

  **Dev2 Reference**:
  - `dev2:CameraController.java:70-72` - MOVEMENT_THRESHOLD
  - `dev2:CameraController.java:74-98` - updateAnimation()修正
  - `dev2:CameraController.java:100-138` - rotateCamera()修正
  - `dev2:CameraController.java:141-184` - alignCameraToMovement()
  - `dev2:CameraController.java:54-56` - onClientTick()修正

  **Acceptance Criteria**:
  - [ ] alignCameraToMovement()が実装されている
  - [ ] updateAnimation()が自動整列速度に対応している
  - [ ] rotateCamera()でisAutoAlignAnimation=falseに設定される
  - [ ] onClientTick()で自動整列が呼ばれる
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 5完了後にコミット)

---

- [ ] 5. InputHandler.java - Vキー処理 + ロジック移動

  **What to do**:
  - handleInput()にVキー処理を追加:
    - alignKeyCodeを取得
    - CameraController.alignCameraToMovement()を呼ぶ
  - enableTopDownView()を削除
  - disableTopDownView()を削除
  - toggleTopDownView()を修正:
    - CameraController.initializeTopDownView()を呼ぶ
    - CameraController.disableTopDownView()を呼ぶ

  **Must NOT do**:
  - 既存のキー処理の順序を変更しない
  - 他のリファクタリングをしない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 小規模な追加とリファクタリング
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (Wave 4)
  - **Blocks**: None (機能1完了)
  - **Blocked By**: Task 3, Task 4

  **References**:
  **Pattern References**:
  - `InputHandler.java:36-48` - handleInput()パターン
  - `InputHandler.java:51-83` - toggleTopDownView()パターン

  **Dev2 Reference**:
  - `dev2:InputHandler.java:32-34` - alignKeyCode取得
  - `dev2:InputHandler.java:42-43` - Vキー処理
  - `dev2:InputHandler.java:45-49` - toggleTopDownView()修正

  **Acceptance Criteria**:
  - [ ] Vキー処理が追加されている
  - [ ] enableTopDownView()が削除されている
  - [ ] disableTopDownView()が削除されている
  - [ ] toggleTopDownView()がCameraControllerのメソッドを呼ぶ
  - [ ] `./gradlew compileJava` が成功

  **Commit**: YES (機能1完了)
  - Message: `feat(camera): 自動カメラ整列機能の実装`
  - Files: Config.java, CameraState.java, ClientModBusEvents.java, CameraController.java, InputHandler.java
  - Pre-commit: `./gradlew build`

---

### 機能2: デフォルト有効化設定

---

- [ ] 6. Config.java - defaultEnabled追加

  **What to do**:
  - DEFAULT_ENABLED設定を追加:
    - comment: "ゲーム起動時にトップダウン視点をデフォルトで有効にする"
    - default: true
  - Runtime value（public static boolean defaultEnabled）を追加
  - onLoad()で値をロード
  - save()で値を保存

  **Must NOT do**:
  - 機能1で追加した設定の順序を変更しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 1つの設定項目追加
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 5 (with Task 7)
  - **Blocks**: Task 8
  - **Blocked By**: None (Commit 1完了後)

  **References**:
  **Pattern References**:
  - `Config.java:41-43` - BooleanValue定義パターン

  **Dev2 Reference**:
  - `dev2:Config.java:80-82` - DEFAULT_ENABLED定義
  - `dev2:Config.java:115` - Runtime value
  - `dev2:Config.java:138` - onLoad()

  **Acceptance Criteria**:
  - [ ] DEFAULT_ENABLEDが定義されている
  - [ ] defaultEnabled runtime valueが追加されている
  - [ ] onLoad()でロードされる
  - [ ] save()で保存される
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 9完了後にコミット)

---

- [ ] 7. ModStatus.java - initializedフィールド追加

  **What to do**:
  - `initialized`フィールドを追加 (boolean, default: false)
  - `isInitialized()`getterを追加
  - `setInitialized(boolean)`setterを追加
  - reset()でinitialized = falseを追加

  **Must NOT do**:
  - 既存のフィールドの順序を変更しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 1つのフィールド追加
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 5 (with Task 6)
  - **Blocks**: Task 8
  - **Blocked By**: None (Commit 1完了後)

  **References**:
  **Pattern References**:
  - `ModStatus.java:12` - フィールド定義パターン
  - `ModStatus.java:18-20` - Getterパターン
  - `ModStatus.java:24-26` - Setterパターン
  - `ModStatus.java:31-33` - reset()パターン

  **Dev2 Reference**:
  - `dev2:ModStatus.java:13` - initializedフィールド
  - `dev2:ModStatus.java:23-25` - isInitialized()
  - `dev2:ModStatus.java:29-31` - setInitialized()
  - `dev2:ModStatus.java:36` - reset()

  **Acceptance Criteria**:
  - [ ] initializedフィールドが追加されている
  - [ ] isInitialized()が実装されている
  - [ ] setInitialized()が実装されている
  - [ ] reset()でinitializedがfalseになる
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 9完了後にコミット)

---

- [ ] 8. CameraController.java - 初期化ロジック追加

  **What to do**:
  - `initializeTopDownView(Minecraft mc)`メソッドを追加:
    - previousCameraTypeを保存
    - CameraType.THIRD_PERSON_BACKに設定
    - releaseMouse()を呼ぶ
    - startTimeを設定
    - setInitialized(true)を呼ぶ
  - `disableTopDownView(Minecraft mc)`メソッドを追加:
    - previousCameraTypeを復元
    - grabMouse()を呼ぶ
    - ModState.resetAll()を呼ぶ
    - CullingManager.forceChunkRebuild()を呼ぶ
  - onClientTick()に初回初期化チェックを追加:
    - enabled && !initialized && player != null && level != null
    - initializeTopDownView()を呼ぶ

  **Must NOT do**:
  - 既存のメソッドのロジックを変更しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 2つのメソッド追加と小規模な修正
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (Wave 6)
  - **Blocks**: Task 9
  - **Blocked By**: Task 6, Task 7

  **References**:
  **Pattern References**:
  - `CameraController.java:44-63` - onClientTick()パターン

  **Dev2 Reference**:
  - `dev2:CameraController.java:49-61` - 初回初期化チェック
  - `dev2:CameraController.java:63-73` - initializeTopDownView()
  - `dev2:CameraController.java:75-85` - disableTopDownView()

  **Acceptance Criteria**:
  - [ ] initializeTopDownView()が実装されている
  - [ ] disableTopDownView()が実装されている
  - [ ] onClientTick()に初回初期化チェックが追加されている
  - [ ] `./gradlew compileJava` が成功

  **Commit**: NO (Task 9完了後にコミット)

---

- [ ] 9. InputHandler.java - toggleロジック調整

  **What to do**:
  - toggleTopDownView()を修正:
    - 既存のロジックを削除（enable/disableTopDownViewは機能1で削除済み）
    - CameraController.initializeTopDownView()を呼ぶ
    - CameraController.disableTopDownView()を呼ぶ

  **Must NOT do**:
  - 他のリファクタリングをしない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 小規模な修正
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (Wave 6)
  - **Blocks**: None (機能2完了)
  - **Blocked By**: Task 8

  **References**:
  **Pattern References**:
  - `InputHandler.java:51-63` - toggleTopDownView()パターン

  **Dev2 Reference**:
  - `dev2:InputHandler.java:45-53` - toggleTopDownView()

  **Acceptance Criteria**:
  - [ ] toggleTopDownView()がCameraControllerのメソッドを呼ぶ
  - [ ] `./gradlew compileJava` が成功

  **Commit**: YES (機能2完了)
  - Message: `feat(config): デフォルト有効化設定の実装`
  - Files: Config.java, ModStatus.java, CameraController.java, InputHandler.java
  - Pre-commit: `./gradlew build`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 5 | `feat(camera): 自動カメラ整列機能の実装` | Config.java, CameraState.java, ClientModBusEvents.java, CameraController.java, InputHandler.java | `./gradlew build` |
| 9 | `feat(config): デフォルト有効化設定の実装` | Config.java, ModStatus.java, CameraController.java, InputHandler.java | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build              # Expected: BUILD SUCCESSFUL
./gradlew runClient          # Expected: Minecraft起動成功
```

### Manual Verification Checklist

**機能1: 自動カメラ整列機能**
- [ ] Minecraft起動後、F4でトップダウン視点を有効化
- [ ] Vキーを押すとカメラが進行方向に整列
- [ ] 移動中、カメラが自動的に進行方向に回転（設定で有効な場合）
- [ ] 設定画面でautoAlignToMovementEnabled等が表示される
- [ ] 設定変更が保存・復元される

**機能2: デフォルト有効化設定**
- [ ] defaultEnabled=trueでMinecraft起動
- [ ] ワールド読み込み後、自動的にトップダウン視点が有効
- [ ] F4でトグルできる（無効化→再有効化）
- [ ] defaultEnabled=falseでMinecraft起動
- [ ] ワールド読み込み後、トップダウン視点は無効

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] 2つのコミットが作成されている
- [ ] `./gradlew build` が成功
