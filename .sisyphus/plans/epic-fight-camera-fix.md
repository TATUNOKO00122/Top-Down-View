# Epic Fight カメラ競合問題修正

## TL;DR

> **Quick Summary**: CameraMixinの優先度を950に変更し、Epic Fightより先に実行されるようにする。また、EpicFightCompatにMethodHandleキャッシュと複数クラスパス対応を追加して、バージョン互換性とパフォーマンスを向上させる。
> 
> **Deliverables**: 
> - CameraMixin.java (優先度950、RETURN Inject削除)
> - EpicFightCompat.java (MethodHandleキャッシュ、複数クラスパス、ログ強化)
> - ClientModBusEvents.java (ログ追加)
> 
> **Estimated Effort**: Short
> **Parallel Execution**: YES - 3 tasks in Wave 1
> **Critical Path**: Task 1 → Task 4 (Integration Test)

---

## Context

### Original Request
TopDownView MODでEpic Fightとのカメラ制御競合が発生。「TopDownView優先モード」と「Epic Fight優先モード」の両方が上手く動作しない問題を修正する。

### Root Cause Analysis (Oracle分析済み)

| 問題 | 現状 | 影響 |
|------|------|------|
| **Mixin優先度** | `priority = 1100` | Epic Fight (1000) より後に実行。Epic FightがHEADでcancelすると、TopDownViewのInject自体が実行されない |
| **RETURN Inject** | 存在するが到達不能 | HEADでcancelされるとRETURNも実行されないデッドコード |
| **MethodHandleキャッシュ** | なし | 毎フレームリフレクションでパフォーマンス低下 |
| **複数クラスパス対応** | なし | Epic Fightバージョン変更でクラッシュの可能性 |
| **ログ** | 一部のみ | 問題診断が困難 |

### Technical Decisions

1. **Mixin優先度950**: 
   - 低い数字が先に実行されるMixin仕様
   - 950 < 1000 (Epic Fight) なのでTopDownViewが先に実行
   - 動的変更はMixin仕様上不可能

2. **RETURN Inject削除**:
   - HEADで制御を決定するため、RETURNは不要
   - デッドコードを削除して可読性向上

3. **MethodHandleキャッシュ**:
   - `MethodHandles.lookup()` と `MethodHandle` をstaticフィールドにキャッシュ
   - 初回アクセス時のみリフレクション、以降は高速呼び出し

4. **複数クラスパス対応**:
   - Epic Fight 20.9.x系をメインターゲット
   - 古いバージョンのクラスパスをフォールバックでサポート

### Scope Boundaries

**INCLUDE:**
- CameraMixin.java の優先度変更とRETURN Inject削除
- EpicFightCompat.java のパフォーマンス改善とバージョン互換性
- ClientModBusEvents.java のログ追加
- 手動テストシナリオの定義

**EXCLUDE:**
- Epic Fight MOD自体の変更
- 他MODとの互換性対応
- 自動テストの実装（手動確認のみ）
- 設定GUIの変更

---

## Work Objectives

### Core Objective
Epic Fightとのカメラ制御競合を解消し、ユーザーが設定で選択した優先モード（TopDownView優先/Epic Fight優先）が正しく動作するようにする。

### Concrete Deliverables
- `src/main/java/com/topdownview/mixin/CameraMixin.java` - 優先度950、RETURN削除
- `src/main/java/com/topdownview/util/EpicFightCompat.java` - MethodHandleキャッシュ、複数クラスパス、ログ強化
- `src/main/java/com/topdownview/client/ClientModBusEvents.java` - ログ出力追加

### Definition of Done
- [ ] `./gradlew build` が成功
- [ ] Epic Fight + TopDownView同時インストール環境で両モードが動作
- [ ] TopDownView優先モード: Epic Fightの弓使用時もトップダウンビューが維持
- [ ] Epic Fight優先モード: Epic Fightの弓使用時はEpic Fightカメラ、それ以外はトップダウンビュー

### Must Have
- Mixin優先度950への変更
- HEAD Injectでの完全なカメラ制御
- MethodHandleのstaticキャッシュ
- 複数クラスパスのフォールバック対応
- 診断用ログの追加

### Must NOT Have (Guardrails)
- Epic Fightのクラス構造を仮定しない（リフレクション失敗時は安全にフォールバック）
- Mixin内で複雑なロジックを書かない（EpicFightCompatに委譲）
- 他MODのMixinに依存しない
- クライアントサイドでスレッド安全性を破らない（HashMapはメインスレッドのみ）

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: NO (Minecraft MOD、手動テストが標準)
- **User wants tests**: Manual-only
- **Framework**: N/A

### Manual Verification Procedures

#### Test Environment Setup
1. Minecraft 1.20.1 + Forge 47.4.10
2. TopDownView MOD (ビルド済み)
3. Epic Fight 20.9.x (最新安定版)

#### Test Scenarios

**Scenario A: TopDownView優先モード (`epicFightCameraPriority = 0`)**
```
1. ゲーム起動 → 設定でTopDownView有効化
2. F4キーでトップダウンビュー切替
3. 弓を装備して右クリック長押し
4. 期待: トップダウンビューが維持され、Epic Fightのカメラにならない
5. ログ確認: "TopDownView camera control active (priority mode)"
```

**Scenario B: Epic Fight優先モード (`epicFightCameraPriority = 1`)**
```
1. ゲーム起動 → 設定でTopDownView有効化、Epic Fight優先選択
2. F4キーでトップダウンビュー有効化
3. 通常移動 → 期待: トップダウンビューで動作
4. 弓を装備して右クリック長押し → 期待: Epic FightのTPSカメラに切り替わる
5. 弓を解除 → 期待: トップダウンビューに戻る
6. ログ確認: "Epic Fight camera active, deferring control"
```

**Scenario C: Epic Fight未インストール環境**
```
1. Epic Fightなしでゲーム起動
2. TopDownView有効化
3. 期待: 通常通りトップダウンビューが動作
4. ログ確認: "Epic Fight not detected, TopDownView has full control"
```

#### Log Verification Points
- 起動時: Epic Fight検出結果
- カメラ切り替え時: 制御権の判定結果
- エラー時: 例外スタックトレース

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - 並列実行):
├── Task 1: CameraMixin.java 修正
├── Task 2: EpicFightCompat.java 改善
└── Task 3: ClientModBusEvents.java ログ追加

Wave 2 (After Wave 1):
└── Task 4: ビルド確認と統合テスト

Critical Path: Task 1 → Task 4
Parallel Speedup: ~60% faster than sequential
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 4 | 2, 3 |
| 2 | None | 4 | 1, 3 |
| 3 | None | 4 | 1, 2 |
| 4 | 1, 2, 3 | None | None (final) |

---

## TODOs

### Wave 1（並列実行可能）

---

- [ ] 1. CameraMixin.java - Mixin優先度変更とRETURN Inject削除

  **What to do**:
  - `@Mixin(priority = 1100)` → `@Mixin(priority = 950)` に変更
  - `onSetupReturn()` メソッド全体を削除
  - `onSetupHead()` の条件分岐を単純化
  - Javadocコメントを更新して新しい動作を説明

  **Must NOT do**:
  - Mixin内でEpicFightCompatの複雑なロジックを呼ばない
  - 新しいInjectポイントを追加しない
  - setupTopDownCamera()のロジックを変更しない

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Mixin修正はMinecraft Moddingの専門知識が必要だが、既存パターンの変更のため
  - **Skills**: [`minecraft-client-safety`]
    - `minecraft-client-safety`: クライアントサイドのMixin安全性確保（@Environment、スレッド安全性）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References** (existing code to follow):
  - `src/main/java/com/topdownview/mixin/CameraMixin.java:38-57` - 現在のHEAD Injectパターン
  - `src/main/java/com/topdownview/mixin/CameraMixin.java:62-89` - setupTopDownCamera()の実装

  **API/Type References**:
  - `src/main/java/com/topdownview/util/EpicFightCompat.java:shouldTopDownViewControlCamera()` - カメラ制御判定API
  - `src/main/java/com/topdownview/state/ModState.java:STATUS.isEnabled()` - 有効状態チェック

  **Acceptance Criteria**:

  **Automated Verification (Bash)**:
  ```bash
  # Agent runs:
  ./gradlew compileJava
  # Assert: Exit code 0
  # Assert: Output contains "BUILD SUCCESSFUL"
  
  # Verify Mixin priority change
  grep -n "priority = 950" src/main/java/com/topdownview/mixin/CameraMixin.java
  # Assert: Output shows line with "priority = 950"
  
  # Verify RETURN method removed
  grep -c "onSetupReturn" src/main/java/com/topdownview/mixin/CameraMixin.java
  # Assert: Output is "0"
  ```

  **Evidence to Capture**:
  - [ ] Compile output showing BUILD SUCCESSFUL
  - [ ] Grep output confirming priority = 950
  - [ ] Grep output confirming onSetupReturn removed

  **Commit**: NO (groups with Task 4)
  - Message: `fix(mixin): change CameraMixin priority to 950, remove dead RETURN inject`
  - Files: `src/main/java/com/topdownview/mixin/CameraMixin.java`

---

- [ ] 2. EpicFightCompat.java - MethodHandleキャッシュ、複数クラスパス、ログ強化

  **What to do**:
  
  **2a. MethodHandleキャッシュ追加**:
  - staticフィールドに `MethodHandle` をキャッシュ
  - `MethodHandles.lookup()` もstaticキャッシュ
  - 初回アクセス時に初期化、以降はキャッシュから使用
  - `invokeExact()` の型安全性を確保（`methodhandles-type-safety` スキル参照）

  **2b. 複数クラスパス対応**:
  - クラスパスの候補リストを定義:
    - `yesman.epicfight.api.client.camera.EpicFightCameraAPI` (20.9.x)
    - `yesman.epicfight.client.camera.EpicFightCameraAPI` (20.0.x - 20.8.x フォールバック)
  - 順番に試行して成功したものを使用
  - 失敗時は安全にフォールバック（falseを返す）

  **2c. ログ強化**:
  - 起動時: Epic Fight検出結果（INFO）
  - 初回アクセス時: 使用したクラスパス（DEBUG）
  - エラー時: 例外の詳細（ERROR）
  - カメラ制御切り替え時: 制御権の変化（DEBUG）

  **Must NOT do**:
  - Epic Fightのクラスを直接importしない（リフレクションのみ）
  - MethodHandleの型不一致を無視しない（`invokeExact`は厳密な型要求）
  - キャッシュを不必要にクリアしない
  - メインスレッド以外から呼び出し可能にしない（HashMap使用）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: リフレクションとキャッシュパターンの実装、mod-integration知識が必要
  - **Skills**: [`mod-integration`, `methodhandles-type-safety`]
    - `mod-integration`: 外部MOD（Epic Fight）との疎結合連携設計
    - `methodhandles-type-safety`: MethodHandles使用時の型安全性、invokeExactの落とし穴回避

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References** (existing code to follow):
  - `src/main/java/com/topdownview/util/EpicFightCompat.java:45-65` - 現在のisTPSMode実装（リフレクション）
  - `src/main/java/com/topdownview/util/EpicFightCompat.java:29-39` - クラス検出パターン

  **Documentation References**:
  - `skill://methodhandles-type-safety` - MethodHandle型安全性ガイドライン

  **External References**:
  - Java MethodHandle API: `https://docs.oracle.com/javase/17/docs/api/java/lang/invoke/MethodHandle.html`

  **Acceptance Criteria**:

  **Automated Verification (Bash)**:
  ```bash
  # Agent runs:
  ./gradlew compileJava
  # Assert: Exit code 0
  
  # Verify MethodHandle cache fields exist
  grep -n "MethodHandle" src/main/java/com/topdownview/util/EpicFightCompat.java
  # Assert: Output contains "private static MethodHandle"
  
  # Verify multi-path support
  grep -n "EPIC_FIGHT_CAMERA_API_CLASS" src/main/java/com/topdownview/util/EpicFightCompat.java
  # Assert: Output shows array or list pattern
  
  # Verify logging added
  grep -c "LOGGER\." src/main/java/com/topdownview/util/EpicFightCompat.java
  # Assert: Count >= 5 (multiple log points)
  ```

  **Evidence to Capture**:
  - [ ] Compile output showing BUILD SUCCESSFUL
  - [ ] Grep output showing MethodHandle cache
  - [ ] Grep output showing multi-path pattern
  - [ ] Grep output showing logging statements

  **Commit**: NO (groups with Task 4)
  - Message: `feat(compat): add MethodHandle cache, multi-classpath support, enhanced logging`
  - Files: `src/main/java/com/topdownview/util/EpicFightCompat.java`

---

- [ ] 3. ClientModBusEvents.java - ログ出力追加

  **What to do**:
  - `onClientSetup()` にログ出力を追加
  - Epic Fightイベントフック登録の成功/失敗をログ
  - ログレベル: INFO（成功）、WARN（部分的失敗）、ERROR（完全失敗）

  **Must NOT do**:
  - 既存のイベント登録ロジックを変更しない
  - 過剰なログを追加しない（起動時のみ）
  - 例外を飲み込まない（ログ出力する）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 単純なログ追加、数行の変更
  - **Skills**: []
    - なし: 単純な変更のため特別なスキル不要

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References**:
  - `src/main/java/com/topdownview/util/EpicFightCompat.java:19` - LOGGERパターン
  - `src/main/java/com/topdownview/client/ClientModBusEvents.java:50-56` - 現在のonClientSetup

  **Acceptance Criteria**:

  **Automated Verification (Bash)**:
  ```bash
  # Agent runs:
  ./gradlew compileJava
  # Assert: Exit code 0
  
  # Verify logging import added
  grep -n "import.*Logger" src/main/java/com/topdownview/client/ClientModBusEvents.java
  # Assert: Output shows import statement
  
  # Verify log statements in onClientSetup
  grep -A5 "onClientSetup" src/main/java/com/topdownview/client/ClientModBusEvents.java | grep "LOGGER"
  # Assert: Output contains LOGGER call
  ```

  **Evidence to Capture**:
  - [ ] Compile output showing BUILD SUCCESSFUL
  - [ ] Grep output showing Logger import
  - [ ] Grep output showing log statements

  **Commit**: NO (groups with Task 4)
  - Message: `feat(client): add logging for Epic Fight event hook registration`
  - Files: `src/main/java/com/topdownview/client/ClientModBusEvents.java`

---

### Wave 2（Wave 1完了後）

---

- [ ] 4. ビルド確認と統合テスト

  **What to do**:
  - `./gradlew build` を実行してビルド成功を確認
  - JARファイルが生成されることを確認
  - 手動テストシナリオのドキュメントを作成（ユーザー用）

  **Must NOT do**:
  - テストを自動化しない（Minecraft MODは手動テストが標準）
  - ビルド失敗時に修正を試みない（Wave 1のタスクに差し戻し）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: ビルド実行と確認のみ
  - **Skills**: []
    - なし: ビルドコマンド実行のみ

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (after Wave 1)
  - **Blocks**: None (final task)
  - **Blocked By**: Tasks 1, 2, 3

  **References**:

  **Documentation References**:
  - `AGENTS.md` - Build commands section

  **Acceptance Criteria**:

  **Automated Verification (Bash)**:
  ```bash
  # Agent runs:
  ./gradlew build
  # Assert: Exit code 0
  # Assert: Output contains "BUILD SUCCESSFUL"
  
  # Verify JAR exists
  ls -la build/libs/*.jar
  # Assert: Output shows at least one JAR file
  ```

  **Manual Verification**:
  - [ ] Test Scenario A: TopDownView優先モード
  - [ ] Test Scenario B: Epic Fight優先モード
  - [ ] Test Scenario C: Epic Fight未インストール環境

  **Evidence to Capture**:
  - [ ] Build output showing BUILD SUCCESSFUL
  - [ ] JAR file listing
  - [ ] Test scenario results (user-reported)

  **Commit**: YES
  - Message: `fix(epicfight): resolve camera control conflict with Epic Fight mod

- Change CameraMixin priority from 1100 to 950 to run before Epic Fight
- Remove dead RETURN inject that was never reached when HEAD cancels
- Add MethodHandle caching in EpicFightCompat for performance
- Support multiple class paths for Epic Fight version compatibility
- Add comprehensive logging for diagnostics`
  - Files: All modified files
  - Pre-commit: `./gradlew build`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 4 (final) | `fix(epicfight): resolve camera control conflict...` | All 3 files | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build                                          # Expected: BUILD SUCCESSFUL
./gradlew runClient                                      # Manual test in-game
```

### Final Checklist
- [ ] CameraMixin優先度が950に変更されている
- [ ] RETURN Injectが削除されている
- [ ] EpicFightCompatにMethodHandleキャッシュがある
- [ ] 複数クラスパス対応が実装されている
- [ ] ログが追加されている
- [ ] `./gradlew build` が成功
- [ ] TopDownView優先モードが動作
- [ ] Epic Fight優先モードが動作
- [ ] Epic Fight未インストール環境で動作
