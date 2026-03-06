# PauseScreen Settings Button Implementation

## TL;DR

> **Quick Summary**: Minecraft Forge 1.20.1のポーズ画面（PauseScreen）に、オプションボタンの左隣へ20x20ピクセルの設定ボタンを追加する。
>
> **Deliverables**:
> - `ClientForgeEvents.java` - ScreenEvent.Init.Postイベントハンドラ
> - 言語ファイル（en_us.json, ja_jp.json）の更新
>
> **Estimated Effort**: Quick
> **Parallel Execution**: YES - 3 tasks in Wave 1
> **Critical Path**: Task 1/2/3 → Task 4

---

## Context

### Original Request
ポーズ画面（ESCキーで開くPauseScreen）に設定ボタンを追加する。

### Interview Summary
**Key Discussions**:
- ボタン位置: オプションボタンの左隣（右下エリア）
- ボタン見た目: テキスト「⚙」（Unicode文字）
- ツールチップ: 必要（「TopDown View 設定」）
- 検出方法: 位置ベース（言語非依存）

### Technical Decisions
- **イベント**: `ScreenEvent.Init.Post` (Forgeバス)
- **検出ロジック**: 画面右下エリアの最も右にあるボタンを特定し、その左隣に配置
- **ボタンサイズ**: 20x20ピクセル
- **ボタン間隔**: 5ピクセル

---

## Work Objectives

### Core Objective
ポーズ画面から直接ConfigScreenを開けるようにするUI改善。

### Concrete Deliverables
- `src/main/java/com/topdownview/client/ClientForgeEvents.java` (新規)
- `src/main/resources/assets/topdown_view/lang/en_us.json` (更新)
- `src/main/resources/assets/topdown_view/lang/ja_jp.json` (更新)

### Definition of Done
- [ ] ポーズ画面に⚙ボタンが表示される
- [ ] ボタンクリックでConfigScreenが開く
- [ ] ツールチップが表示される
- [ ] `./gradlew build` が成功する

### Must Have
- 位置ベースでボタン検出（言語非依存）
- ConfigScreenへの遷移
- ツールチップ表示

### Must NOT Have (Guardrails)
- 言語依存のテキストマッチング
- 既存ボタンとの位置重複
- カスタムテクスチャファイル（不要なリソース追加）

---

## Verification Strategy (Manual)

> テストインフラストラクチャがないため、手動検証を使用。

### Manual Verification Procedure

**環境セットアップ**:
```bash
./gradlew runClient
```

**検証ステップ**:
1. ゲーム起動後、ESCキーでポーズ画面を開く
2. オプションボタンの左隣に「⚙」ボタンが表示されることを確認
3. ボタンにマウスを乗せ、「TopDown View 設定」ツールチップが表示されることを確認
4. ボタンをクリックし、ConfigScreenが開くことを確認
5. ConfigScreenの「完了」ボタンでポーズ画面に戻ることを確認
6. 言語設定を英語/日本語で切り替えて同様に動作することを確認

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - 並列実行):
├── Task 1: ClientForgeEvents.java 作成
├── Task 2: en_us.json 更新
└── Task 3: ja_jp.json 更新

Wave 2 (After Wave 1):
└── Task 4: ビルド確認
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

- [ ] 1. ClientForgeEvents.java の作成

  **What to do**:
  - 新規ファイル `src/main/java/com/topdownview/client/ClientForgeEvents.java` を作成
  - Forgeバスのイベントハンドラとして登録
  - ScreenEvent.Init.PostでPauseScreenを判定
  - 位置ベースで右下のボタンを検出
  - 設定ボタンを追加

  **Must NOT do**:
  - 言語依存のテキストマッチングを使用しない
  - 他の画面にボタンを追加しない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 単純な新規ファイル作成タスク
  - **Skills**: なし
    - ドメイン固有のスキル不要

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References**:
  - `src/main/java/com/topdownview/client/ClientModBusEvents.java` - イベントハンドラの登録パターン（@Mod.EventBusSubscriber）
  - `src/main/java/com/topdownview/client/gui/ConfigScreen.java:21-24` - ConfigScreenのコンストラクタ呼び出し

  **API References**:
  - `net.minecraft.client.gui.components.Button.builder()` - ボタン作成
  - `net.minecraft.client.gui.components.Tooltip.create()` - ツールチップ作成
  - `net.minecraftforge.client.event.ScreenEvent.Init.Post` - イベント型
  - `net.minecraft.client.gui.screens.PauseScreen` - 対象画面

  **Implementation Code**:
  ```java
  package com.topdownview.client;

  import com.topdownview.TopDownViewMod;
  import com.topdownview.client.gui.ConfigScreen;
  import net.minecraft.client.Minecraft;
  import net.minecraft.client.gui.components.Button;
  import net.minecraft.client.gui.components.Tooltip;
  import net.minecraft.client.gui.screens.PauseScreen;
  import net.minecraft.network.chat.Component;
  import net.minecraftforge.api.distmarker.Dist;
  import net.minecraftforge.client.event.ScreenEvent;
  import net.minecraftforge.eventbus.api.SubscribeEvent;
  import net.minecraftforge.fml.common.Mod;
  import java.util.Comparator;

  /**
   * Forgeバスのクライアントサイドイベントハンドラ
   * ポーズ画面へのボタン追加など
   */
  @Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
  public final class ClientForgeEvents {
      
      private static final int BUTTON_SIZE = 20;
      private static final int BUTTON_SPACING = 5;
      
      private ClientForgeEvents() {
          throw new IllegalStateException("ユーティリティクラス");
      }
      
      @SubscribeEvent
      public static void onScreenInit(ScreenEvent.Init.Post event) {
          // PauseScreen以外は無視
          if (!(event.getScreen() instanceof PauseScreen screen)) {
              return;
          }
          
          // 画面右下エリアの最も右にあるボタンを特定（オプションボタン）
          screen.children().stream()
              .filter(child -> child instanceof Button)
              .map(child -> (Button) child)
              .filter(button -> button.getX() > screen.width / 2)  // 右半分のボタン
              .filter(button -> button.getY() > screen.height - 60)  // 下部のボタン
              .max(Comparator.comparingInt(Button::getX))  // 最も右にあるボタン
              .ifPresent(rightmostBottomButton -> {
                  // その左隣に設定ボタンを配置
                  int x = rightmostBottomButton.getX() - BUTTON_SIZE - BUTTON_SPACING;
                  int y = rightmostBottomButton.getY();
                  
                  Button configButton = Button.builder(
                          Component.literal("⚙"),
                          (button) -> {
                              Minecraft.getInstance().setScreen(new ConfigScreen(screen));
                          })
                          .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                          .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                          .build();
                  
                  event.addListener(configButton);
              });
      }
  }
  ```

  **Acceptance Criteria**:
  - [ ] ファイルが作成されている: `src/main/java/com/topdownview/client/ClientForgeEvents.java`
  - [ ] コンパイルエラーがない（Task 4で確認）
  - [ ] PauseScreenでボタンが表示される（手動確認）

  **Commit**: NO (groups with Task 4)
  - Message: `feat(client): add settings button to pause screen`
  - Files: `src/main/java/com/topdownview/client/ClientForgeEvents.java`
  - Pre-commit: `./gradlew compileJava`

---

- [ ] 2. en_us.json にツールチップ追加

  **What to do**:
  - `src/main/resources/assets/topdown_view/lang/en_us.json` の最後にツールチップキーを追加

  **Must NOT do**:
  - 既存の行を変更しない
  - JSONフォーマットを崩さない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 1行追加のみ
  - **Skills**: なし

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:
  - `src/main/resources/assets/topdown_view/lang/en_us.json` - 既存の言語ファイルフォーマット

  **Implementation**:
  - 56行目（最後の行）の末尾にカンマを追加
  - 新しい行を追加: `  "topdown_view.pause.config_button.tooltip": "TopDown View Settings"`

  **Acceptance Criteria**:
  - [ ] ファイルが有効なJSONである
  - [ ] 新しいキーが追加されている

  **Commit**: NO (groups with Task 4)

---

- [ ] 3. ja_jp.json にツールチップ追加

  **What to do**:
  - `src/main/resources/assets/topdown_view/lang/ja_jp.json` の最後にツールチップキーを追加

  **Must NOT do**:
  - 既存の行を変更しない
  - JSONフォーマットを崩さない

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 1行追加のみ
  - **Skills**: なし

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Task 4
  - **Blocked By**: None (can start immediately)

  **References**:
  - `src/main/resources/assets/topdown_view/lang/ja_jp.json` - 既存の言語ファイルフォーマット

  **Implementation**:
  - 56行目（最後の行）の末尾にカンマを追加
  - 新しい行を追加: `  "topdown_view.pause.config_button.tooltip": "TopDown View 設定"`

  **Acceptance Criteria**:
  - [ ] ファイルが有効なJSONである
  - [ ] 新しいキーが追加されている

  **Commit**: NO (groups with Task 4)

---

- [ ] 4. ビルド確認

  **What to do**:
  - `./gradlew build` を実行してコンパイルエラーがないことを確認

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: コマンド実行のみ
  - **Skills**: なし

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: Task 1, 2, 3 (全ファイル編集完了後)

  **Acceptance Criteria**:
  - [ ] `./gradlew build` が成功する（exit code 0）
  - [ ] コンパイルエラーがない

  **Verification Command**:
  ```bash
  ./gradlew build
  # Expected: BUILD SUCCESSFUL
  ```

  **Commit**: YES
  - Message: `feat(client): add settings button to pause screen`
  - Files: 
    - `src/main/java/com/topdownview/client/ClientForgeEvents.java`
    - `src/main/resources/assets/topdown_view/lang/en_us.json`
    - `src/main/resources/assets/topdown_view/lang/ja_jp.json`
  - Pre-commit: `./gradlew compileJava`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 4 | `feat(client): add settings button to pause screen` | ClientForgeEvents.java, en_us.json, ja_jp.json | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build  # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] ClientForgeEvents.java が作成されている
- [ ] 言語ファイルが更新されている
- [ ] ビルドが成功する
- [ ] 手動テストでボタンが表示される
- [ ] 手動テストでConfigScreenが開く
