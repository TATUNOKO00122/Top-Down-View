# トップダウン視点MOD サウンド問題調査レポート

## 1. 問題の概要

### 現象
トップダウン視点（俯瞰カメラ）モードにおいて、**カメラを遠ざけると音が聞こえる範囲が狭くなる**。

### ユーザーの期待
- カメラを遠ざけても、プレイヤー周辺の音は通常通り聞こえるべき
- 音の距離減衰は「プレイヤーと音源の距離」に基づくべきで、「カメラと音源の距離」ではない

---

## 2. 調査環境

- Minecraft: 1.20.1
- Forge: 47.4.10
- マッピング: Official (Mojang)
- シングルプレイヤー（統合サーバー）

---

## 3. 原因判明

### 3.1 根本原因

**Flerovium（レンダリング最適化MOD）がサウンドイベント処理に干渉している**

### 3.2 詳細分析

ログ調査の結果、以下の現象が確認された：

```
[ClientLevelMixin] playSeededSound(position) called - Sound: minecraft:entity.arrow.hit, pPlayer: null, mc.player: LocalPlayer[...], willPlay: false
```

- `pPlayer: null`で`playSeededSound`が呼ばれている
- `willPlay: false`のため、`playSound`が呼ばれない
- **結果**: サウンドが再生されない

### 3.3 なぜFleroviumが原因か

Fleroviumは以下の最適化を行う：
1. **Backface culling** - 背面カリング
2. **Fast Math** - 高速数学計算
3. **LOD** - Level of Detail

これらの最適化が、**エンティティのレンダリング距離計算**や**バッファ操作**に影響し、
サウンドイベントの`pPlayer`パラメータに影響を与える可能性がある。

### 3.4 検証結果

| 環境 | Flerovium | 音の聞こえ方 |
|------|-----------|--------------|
| Minecraft1201withForgeEEE | あり | × 遠いと聞こえない |
| Minecraft1201withForgeTERA | なし | ○ 正常に聞こえる |

---

## 4. Minecraft サウンドシステムの構造

```
[サーバー側]
音イベント発生 → World#playSound() 
                ↓ プレイヤー位置からの距離チェック（パケットカリング）
                ↓ 対象プレイヤーにパケット送信

[クライアント側]
パケット受信 → ClientPacketListener.handleSoundEvent()
                ↓
          ClientLevel.playSeededSound()
                ↓ Forgeイベント発火 (PlayLevelSoundEvent.AtPosition)
                ↓ if (event.isCanceled() || event.getSound() == null) return;
                ↓ if (pPlayer == minecraft.player) ← Fleroviumがここで干渉
                ↓
          ClientLevel.playSound() 
                ↓ カメラ位置を使った距離計算
                ↓
          SoundManager.play()
                ↓
          SoundEngine.play()
                ↓ canPlaySound() チェック
                ↓ リスナー位置との距離チェック
                ↓
          Channel.linearAttenuation() → OpenALに渡す
```

---

## 5. 解決策

### 5.1 回避策（推奨）

**Fleroviumを無効化してプレイする**

### 5.2 代替案

Fleroviumの代わりに、以下の最適化MODを使用する:
- **Embeddium** - 単体で使用（Fleroviumなし）
- **Rubidium Extra** - 追加の最適化のみ使用

### 5.3 将来の対応
Fleroviumとの互換性を実現する場合、FleroviumのMixinを解析し、
サウンドイベント処理への干渉を回避する必要があるが現時点では技術的に困難。

---

## 6. 実装済みの対策（Fleroviumなしの環境向け）

### 6.1 ClientLevelSoundMixin
`playSound()`内のカメラ位置をプレイヤー位置に置き換え

```java
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel")
public class ClientLevelSoundMixin {
    @Redirect(method = "playSound", 
              at = @At(value = "INVOKE", 
                       target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectCameraPositionForSound(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return camera.getPosition();
    }
}
```

### 6.2 SoundSystemMixin
`SoundEngine`のリスナー位置を偽装

```java
@Redirect(method = "play", 
          at = @At(value = "INVOKE", 
                   target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
private Vec3 redirectCameraPosition(Camera camera) {
    if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
        return Minecraft.getInstance().player.getEyePosition();
    }
    return camera.getPosition();
}
```

---

## 7. ステータス

**解決済み**: Fleroviumとの非互換性を確認

**回避策**: Fleroviumを無効化してプレイ

**文書**: README.mdに互換性情報を記載済み

---

## 8. ファイル構成

```
src/main/java/com/example/examplemod/mixin/
├── SoundSystemMixin.java           # SoundEngineのリスナー位置偽装
├── ClientLevelSoundMixin.java      # ClientLevel.playSound() のカメラ位置置き換え
└── (その他のMixinはデバッグ用)

src/main/resources/
└── topdown_view.mixins.json        # Mixin設定
```
