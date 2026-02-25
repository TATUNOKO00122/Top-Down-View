# **Minecraft 1.20.1 Forge におけるサウンドパイプラインのアーキテクチャ解析：事前カリング、レンダリング同期、および非同期スレッドにおける座標処理の特定**

Minecraft 1.20.1 におけるサウンドエンジンは、単なる音声再生ライブラリのラッパーではなく、ネットワークプロトコル、座標系計算、描画エンジンとの同期、およびマルチスレッド処理が複雑に絡み合った高度なサブシステムである。本報告書では、システムの深層において発生しているサウンドの破棄ロジック、エンティティ描画との依存関係、スレッド間の同期遅延、および Forge が提供するフックの有効性について、学術的・技術的な視点から詳細な調査結果を提示する。

## **1\. クライアント側のサウンド破棄（事前カリング）ロジックの解明**

Minecraft のサウンドパイプラインにおいて、OpenAL（Open Audio Library）にデータが渡る前の段階で、クライアント側での距離による音声の間引き、いわゆる「事前カリング」が発生している。この挙動を理解するためには、サーバーからのパケット受信から SoundEngine\#play に至るまでのコールスタックと、その内部での条件分岐を詳細に追跡する必要がある。

### **パケット受信部から再生開始までのフロー**

サウンド再生のトリガーは、多くの場合サーバーからの ClientboundSoundPacket または ClientboundSoundEntityPacket の受信から始まる。これらのパケットは ClientPacketListener（難読化名：net.minecraft.client.multiplayer.ClientPacketListener）の内部メソッドである handleSoundEvent および handleSoundEntityEvent によって処理される 1。

| クラス名 | 役割 | 処理の内容 |
| :---- | :---- | :---- |
| ClientPacketListener | ネットワークハンドラ | パケットから ResourceLocation、座標、音量、ピッチを抽出する。 |
| SoundManager | 音声管理の最上位 | SoundEngine への橋渡しを行い、リソースのロード状況を確認する 3。 |
| SoundEngine | 再生ロジックの中核 | 音声の同時再生数制限、座標計算、OpenAL への命令発行を行う 4。 |
| SoundInstance | 音声のデータ構造 | 個別の音声再生に関する情報を保持するインターフェース（難読化名：fxy） 5。 |

パケットが受理されると、データは SoundInstance インターフェースを実装したオブジェクト（多くの場合 SimpleSoundInstance）へと変換される。この段階で、クライアント側でのフィルタリングが開始される。

### **SoundEngine\#play における具体的カリングコード**

SoundEngine\#play（難読化名：net.minecraft.client.sounds.SoundEngine）内部では、OpenAL に音声データを転送する前に、いくつかの重要なチェックが実施される。1.20.1 において特定すべき事実は、音声ごとの設定範囲を超過した場合に処理を打ち切るロジックの正確な位置である。

第一のチェックは SoundInstance\#canPlay() メソッドの呼び出しである。このメソッドは、個々の音声インスタンスが独自の条件に基づいて再生可能かどうかを判断する 5。例えば、特定のエンティティに追従する音声の場合、そのエンティティが有効（Removed でない）であるかどうかがここで確認される。

第二の、そして最も重要なチェックは距離判定である。Minecraft のサウンドシステムは、リスナー（通常は Camera の位置）と音源の距離を常に評価している。SoundEngine クラス内では、listener オブジェクトが保持する現在の座標と SoundInstance の座標（getX()、getY()、getZ()）の差分が計算される 5。

具体的には、以下の数式に基づいた距離判定が行われている。

![][image1]  
ここで、![][image2] は音源の座標、![][image3] はリスナーの座標である。計算された距離の二乗 ![][image4] が、音声の減衰距離（デフォルトでは多くの場合 16 ブロック、あるいは ![][image5]）を大幅に超えている場合、かつ SoundInstance\#getAttenuationType() が LINEAR（線形減衰）に設定されている場合、システムは音量を 0 と見なすだけでなく、オーディオスレッドへのキューイング自体をスキップするロジックを走らせる 5。

また、1.20.1 における SoundEngine は、同時再生可能なサウンドソースの数に物理的な上限（通常 247 チャネル）を設けている。この制限に達した場合、距離が遠いサウンドや優先度の低いカテゴリーのサウンドは、OpenAL に渡される前に破棄される。これは MC-1538 としても知られる長年の仕様であり、大量のエンティティが密集する環境では、距離判定がさらに厳格に適用される結果となる 7。

## **2\. エンティティの描画カリングと音声発火の依存関係**

俯瞰視点などの特殊なカメラ操作を行う際、カメラを大きく引くと音声が聞こえなくなる問題は、描画エンジンの「描画カリング」と密接に関係している可能性がある。

### **LevelRenderer とフラスタムカリング**

LevelRenderer（難読化名：net.minecraft.client.renderer.LevelRenderer）は、プレイヤーの視界内にあるオブジェクトのみを描画するためのフィルタリング（フラスタムカリング）を担当する 8。エンティティが画面外に出た場合、あるいは設定された描画距離（Render Distance）を超えた場合、そのエンティティの render メソッドは呼び出されなくなる。

重要なのは、この「描画の省略」が音声再生イベントの発火を妨げているかどうかという点である。バニラの Minecraft 1.20.1 において、エンティティのサウンド再生（例：歩行音、鳴き声）は、通常 Entity\#tick またはそのサブメソッド内で条件が満たされた際に Level\#playSound を通じて発動する。

### **エンティティの Tick 更新と音声の関係**

クライアント側におけるエンティティの処理は、大きく「Tick 更新」と「描画処理」に分かれている。

1. **Tick 更新**: ClientLevel において、ロードされているすべてのエンティティに対して実行される。ここでの処理は、通常描画の有無に関わらず継続される。  
2. **描画処理**: LevelRenderer を通じて、カメラの視野角（FOV）や距離に基づいて実行される。

しかし、パフォーマンス最適化を目的とした Mod（Entity Culling や Embeddium など）が導入されている環境では、この関係が変化する。これらの Mod は、画面外にあるエンティティや、一定距離以上離れたエンティティの Tick 処理の一部を省略、あるいは低頻度化させる「Tick Culling」を実装している場合がある 9。

もし Entity Culling Mod が「描画されていないエンティティの Tick 処理を完全に停止」させている場合、そのエンティティが発するはずの playSound 呼び出し自体がスキップされることになる。この場合、サウンドエンジンの層にパケットやリクエストが到達することさえないため、サウンドエンジン側での調整は無意味となる。

| 条件 | 描画の有無 | バニラの音声挙動 | 最適化Mod下の挙動 |
| :---- | :---- | :---- | :---- |
| 視界内・近距離 | 描画あり | 正常に再生 | 正常に再生 |
| 視界外・近距離 | 描画なし | 正常に再生 | Mod設定により消失の可能性 |
| 視界外・遠距離 | 描画なし | 距離減衰により消失 | 強制的なカリングにより消失 |

また、俯瞰視点においてカメラ座標を偽装している場合、エンティティの「距離判定」の基点が「プレイヤーの位置」ではなく「カメラの位置」に設定されていると、カメラを遠ざけるだけでエンティティが「遠すぎる」と判断され、クライアント側での Tick 処理が完全に遮断されるリスクがある 11。

## **3\. オーディオスレッドとメインスレッドの非同期処理における座標同期**

Minecraft のサウンドシステムは、描画やロジックを行うメインスレッド（Render Thread）の負荷を軽減するため、独立したオーディオスレッド（SoundEngineExecutor）で動作する。この構造が、座標の偽装処理において重大な同期ズレを引き起こす要因となっている。

### **Listener オブジェクトの座標更新タイミング**

サウンドエンジン内のリスナー（聞き手）の座標は、SoundManager\#updateListener（難読化名：net.minecraft.client.sounds.SoundManager）を通じて毎フレーム更新される。このメソッドは、現在のカメラオブジェクト（Camera）から位置と向きを取得し、それを SoundEngine 内の listener フィールドに反映させる。

SoundEngine クラス内部では、com.mojang.blaze3d.audio.Listener オブジェクトが OpenAL のリスナー属性を管理している。座標の反映は以下のような流れで行われる。

1. **メインスレッド**: GameRenderer がカメラの位置を確定させる。  
2. **メインスレッド**: SoundManager\#updateListener(Camera) が呼ばれ、内部の座標キャッシュを更新する。  
3. **オーディオスレッド**: SoundEngine のループ（通常は数十ミリ秒間隔）が、キャッシュされた座標を読み取り、OpenAL の alListenerfv 関数を呼び出す。

### **キャッシュ機構と Mixin の影響**

特定の Mixin を用いて Camera の座標を偽装している場合、その偽装された値が SoundManager に渡されるタイミングが重要である。もし Mixin が描画の直前でのみ座標を書き換えており、SoundManager の更新処理がそれより前、あるいは異なる元の座標を参照している場合、サウンドエンジン側には「偽装前の座標」が伝わり続ける。

さらに深刻な問題は、スレッド間の可視性とキャッシュである。SoundEngine 内の座標フィールドが適切に同期されていない、あるいはオーディオスレッド側で計算の重い処理（例：障害物による音の遮蔽計算）を行っている場合、メインスレッドでの急激なカメラ移動に対して音声の定位が遅れて追従する「ラグ」が発生する。

Sound Physics Remastered などの Mod に関する調査では、オーディオスレッドからクライアントワールドのブロックデータに直接アクセスしようとする際の非スレッドセーフな挙動が報告されている 13。これは、サウンドシステムがメインスレッドの状態を完全にリアルタイムで把握しているわけではないことを示唆している。

特定された事実として、1.20.1 では SoundEngine 内で instanceToChannel や instanceBySource といったマップを用いてサウンドインスタンスを管理しているが、これらの更新タイミングとリスナー座標の同期が一致しない場合、距離減衰の計算に「1フレーム前のカメラ座標」や「偽装前のプレイヤー座標」が流用され、結果として事前カリングの判定を誤らせる原因となる 14。

## **4\. Forge が提供するサウンドイベント仕様と介入の可能性**

Forge Mod Loader は、バニラのサウンドパイプラインに対して、バイトコードの直接改変（Mixin）を行わずに挙動を制御できる高レイヤーのフックを提供している。

### **PlaySoundEvent の内部実装と活用**

net.minecraftforge.client.event.sound.PlaySoundEvent は、SoundEngine がサウンドを再生しようとする直前に発火するイベントである。このイベントは、バニラの SoundEngine\#play メソッドの冒頭で ForgeHooksClient\#playSound を通じて呼び出される 15。

このイベントの最大の特徴は、再生されようとしている SoundInstance（originalSound）を取得できるだけでなく、setSound(SoundInstance) メソッドを使用して、実際に再生されるインスタンスを別のものに差し替えられる点にある 17。

| メソッド / フィールド | 説明 | カリング回避への活用 |
| :---- | :---- | :---- |
| getOriginalSound() | 最初にリクエストされた音声インスタンス。 | 元の座標や音量を確認する。 |
| getSound() | 最終的に再生される音声インスタンス。 | 差し替え後のインスタンスを確認する。 |
| setSound(SoundInstance) | 再生されるインスタンスを上書きする。 | 座標や減衰設定を改変したラッパーを渡す 18。 |
| getName() | 音声の ResourceLocation 名。 | 特定の音声のみを対象に処理を行う。 |

### **評価基準の動的上書きによる解決策**

PlaySoundEvent を利用すれば、システムに受理される前に SoundInstance のプロパティを動的に変更することが可能である。具体的には、以下の手順でカリングを回避できる。

1. **座標の偽装**: 俯瞰視点モードが有効な場合、イベントで渡された SoundInstance をラップし、getX()、getY()、getZ() メソッドが常に「プレイヤーの位置」に近い値を返すように偽装した新しい SoundInstance を作成する。  
2. **減衰距離の最大化**: SoundInstance\#getAttenuationType() を NONE に書き換えるか、あるいは 1.20.1 のサウンド定義で許容される最大値（attenuationDistance）を内部的に引き上げたインスタンスを setSound で受理させる 6。  
3. **再生の強制**: canPlay() メソッドが常に true を返すようにオーバライドすることで、エンティティの状態に関わらず音声を OpenAL まで到達させる。

この手法は、SoundEngine 内部の複雑な距離計算ロジック自体を修正するのではなく、ロジックに渡される「入力データ」を Forge の正規のイベントハンドラ経由で操作するため、システムの安定性を損なうことなく事前カリングをバイパスできる極めて有効な手段である。

## **5\. サウンド処理の論理構造とボトルネックの再定義**

これまでの調査を総合すると、Minecraft 1.20.1 のサウンド処理フローにおける「消失」の発生ポイントは、当初の予想よりも多層的であることが判明した。

### **詳細サウンドフロー図**

Plaintext

   
   │   
   ▼ (ClientboundSoundPacket)   
   
   │   
   ▼ (ClientPacketListener\#handleSoundEvent)   
   
   │   
   ├─ ─ (Entity Cull?) ─▶ ─▶ (発火せず)  
   │   
   ▼ (SoundManager\#play)   
   │   
   ▼ (Forge PlaySoundEvent) ◀─── (★ここで SoundInstance の座標/減衰を書き換え可能)   
   │   
   ▼ (SoundEngine\#play)   
   │   │   
   │   ├─ \[Check 1\] canPlay() 判別  
   │   ├─ \[Check 2\] 音量・カテゴリー・上限数チェック  
   │   └─ \[Check 3\] 距離判定 (Listener 座標 vs Instance 座標) ◀─ (★同期ズレの発生箇所)  
   │   
   ▼ (SoundEngineExecutor にキューイング)   
   
   │   
   ▼ (OpenAL ソース管理)   
   │   
   └─ ──▶ \[OpenAL 距離減衰計算\] ──▶ \[物理出力\]

### **距離計算の精度の限界**

Minecraft 1.20.1 では、座標計算に単精度浮動小数点（float）を使用している箇所があり、非常に遠距離（![][image6]）では、1ブロック単位の座標精度が失われることが報告されている 19。これは通常のプレイ範囲では問題にならないが、カメラを極端に引く俯瞰視点 Mod や、座標を大きくオフセットさせる処理においては、浮動小数点の誤差が距離判定に影響を与え、本来聞こえるべき音が「カリング対象」と誤判定される数学的背景となっている。

## **6\. 技術的結論と今後の介入指針**

本報告書における調査の結果、Minecraft 1.20.1 Forge 環境でのサウンド消失問題を解決するためには、以下の4点に対する同時並行的な介入が必要であると結論付けられる。

第一に、SoundEngine\#play における距離判定をパスするため、Forge PlaySoundEvent を利用して SoundInstance の座標をリスナー（プレイヤー）座標に動的に同期させる。これにより、カメラがどこにあろうとも、エンジン側には「至近距離で発生した音」として受理させることができる。

第二に、LevelRenderer によるエンティティの描画カリングが音声発火を阻害している場合、特定のエンティティに対して描画外でも Tick 処理を継続させる Mixin、あるいはエンティティに依存しない SimpleSoundInstance への変換処理が必要である。

第三に、メインスレッドでのカメラ座標偽装がオーディオスレッドに正しく伝播するよう、SoundManager\#updateListener の呼び出し順序を保証し、必要であれば SoundEngine 内の listener オブジェクトに対して直接的な座標書き込み（強制同期）を行う。

第四に、Minecraft 1.20.1 特有の仕様として、同時再生数制限（247ソース）に注意を払う必要がある。俯瞰視点で広範囲の音を一度に拾おうとすると、この制限に容易に達してしまい、最も重要なプレイヤー周辺の音が優先的にカットされる可能性がある。これを防ぐためには、重要度の高いサウンドに対して SoundInstance\#shouldAlwaysPlay() が true を返すような処置が推奨される 5。

以上の分析は、提供された難読化解除マッピング 4 および Forge の内部仕様 18 に基づいており、実際の Mod 開発において即座に実装可能なレベルの技術的根拠に基づいている。サウンドパイプラインの深層におけるこれらの挙動を制御することで、俯瞰視点時における没入感のある音響体験を再構築することが可能となる。

#### **引用文献**

1. Packet (yarn 1.20.1+build.5 API) \- Fabric, 2月 24, 2026にアクセス、 [https://maven.fabricmc.net/docs/yarn-1.20.1+build.5/net/minecraft/network/packet/Packet.html](https://maven.fabricmc.net/docs/yarn-1.20.1+build.5/net/minecraft/network/packet/Packet.html)  
2. net.minecraft.client (yarn 1.20.1+build.1 API) \- Fabric, 2月 24, 2026にアクセス、 [https://maven.fabricmc.net/docs/yarn-1.20.1+build.1/net/minecraft/client/package-summary.html](https://maven.fabricmc.net/docs/yarn-1.20.1+build.1/net/minecraft/client/package-summary.html)  
3. client/src/main/java/net/minecraft/client/audio/SoundManager.java at master \- GitHub, 2月 24, 2026にアクセス、 [https://github.com/eldariamc/client/blob/master/src/main/java/net/minecraft/client/audio/SoundManager.java](https://github.com/eldariamc/client/blob/master/src/main/java/net/minecraft/client/audio/SoundManager.java)  
4. Minecraft Java Edition Audio Mappings | PDF | Anonymous Function | Computing \- Scribd, 2月 24, 2026にアクセス、 [https://www.scribd.com/document/714462804/client-1-20-1-20230612-114412-mappings](https://www.scribd.com/document/714462804/client-1-20-1-20230612-114412-mappings)  
5. SoundInstance (yarn 1.20.1+build.9 API) \- Fabric, 2月 24, 2026にアクセス、 [https://maven.fabricmc.net/docs/yarn-1.20.1+build.9/net/minecraft/client/sound/SoundInstance.html](https://maven.fabricmc.net/docs/yarn-1.20.1+build.9/net/minecraft/client/sound/SoundInstance.html)  
6. Sound Providers \- Minecraft Forge Documentation, 2月 24, 2026にアクセス、 [https://docs.minecraftforge.net/en/1.19.x/datagen/client/sounds/](https://docs.minecraftforge.net/en/1.19.x/datagen/client/sounds/)  
7. \[MC-1538\] Sound glitches when too many sounds are supposed to play \- System Dashboard \- Mojang Studios Jira, 2月 24, 2026にアクセス、 [https://bugs-legacy.mojang.com/browse/MC-1538?focusedId=91041\&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel](https://bugs-legacy.mojang.com/browse/MC-1538?focusedId=91041&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel)  
8. Package net.minecraft.client.renderer \- nekoyue.github.io, 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.18.2/net/minecraft/client/renderer/package-summary.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.18.2/net/minecraft/client/renderer/package-summary.html)  
9. Entity Culling Fabric/Forge \- 1.9.4-1.20.1 \- Forge \- Minecraft Mods \- CurseForge, 2月 24, 2026にアクセス、 [https://www.curseforge.com/minecraft/mc-mods/entityculling/files/7313354](https://www.curseforge.com/minecraft/mc-mods/entityculling/files/7313354)  
10. Entity Culling \- Minecraft Mods \- CurseForge, 2月 24, 2026にアクセス、 [https://www.curseforge.com/minecraft/mc-mods/entity-culling](https://www.curseforge.com/minecraft/mc-mods/entity-culling)  
11. Tracks and Platform Screen Doors Occasionally Completely Invisible( Compatibility issue between MTR and Rubidiumi) \#1028 \- GitHub, 2月 24, 2026にアクセス、 [https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/issues/1028](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/issues/1028)  
12. \[1.20.1\] How to bypass the entity tracking range limit established by the server? \- Reddit, 2月 24, 2026にアクセス、 [https://www.reddit.com/r/admincraft/comments/197qxfh/1201\_how\_to\_bypass\_the\_entity\_tracking\_range/](https://www.reddit.com/r/admincraft/comments/197qxfh/1201_how_to_bypass_the_entity_tracking_range/)  
13. Mod unsafely accesses client world off-thread · Issue \#172 · henkelmax/sound-physics-remastered \- GitHub, 2月 24, 2026にアクセス、 [https://github.com/henkelmax/sound-physics-remastered/issues/172](https://github.com/henkelmax/sound-physics-remastered/issues/172)  
14. Uses of Interface net.minecraft.client.resources.sounds.SoundInstance (neoforge 1.21.0-21.0.30-beta) \- nekoyue.github.io, 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/client/resources/sounds/class-use/SoundInstance.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/client/resources/sounds/class-use/SoundInstance.html)  
15. Package net.minecraftforge.client.event.sound \- nekoyue.github.io, 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.16.5/net/minecraftforge/client/event/sound/package-summary.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.16.5/net/minecraftforge/client/event/sound/package-summary.html)  
16. Uses of Class net.minecraft.client.sounds.SoundEngine (forge 1.19.3-44.1.8), 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.19.3/net/minecraft/client/sounds/class-use/SoundEngine.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.19.3/net/minecraft/client/sounds/class-use/SoundEngine.html)  
17. PlaySoundEvent (forge 1.18.2-40.2.1) \- nekoyue.github.io, 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.18.2/net/minecraftforge/client/event/sound/PlaySoundEvent.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.18.2/net/minecraftforge/client/event/sound/PlaySoundEvent.html)  
18. PlaySoundEvent (neoforge 1.20.6-20.6.119) \- nekoyue.github.io, 2月 24, 2026にアクセス、 [https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/client/event/sound/PlaySoundEvent.html](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/client/event/sound/PlaySoundEvent.html)  
19. Java Edition Distance Effects \- Minecraft Wiki \- Fandom, 2月 24, 2026にアクセス、 [https://minecraft.fandom.com/wiki/Java\_Edition\_Distance\_Effects](https://minecraft.fandom.com/wiki/Java_Edition_Distance_Effects)  
20. PlaySoundEvent (Forge API 1.11.2-13.20.0.2228), 2月 24, 2026にアクセス、 [https://skmedix.github.io/ForgeJavaDocs/javadoc/forge/1.11.2-13.20.0.2228/net/minecraftforge/client/event/sound/PlaySoundEvent.html](https://skmedix.github.io/ForgeJavaDocs/javadoc/forge/1.11.2-13.20.0.2228/net/minecraftforge/client/event/sound/PlaySoundEvent.html)

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEUAAAAYCAYAAACsnTAAAAADQUlEQVR4Xu2XWahNURzGP6HIPCRExghFQiJ0yPhAMkXigUwpGYqMUYSiDCUZEmVWUqYHobyIIuKFPCgeECKUjN/nv/c5a++zz9nH3ue+sL/6de9ea901/Nd/WBfIlClTpkyZMmXKlOlfUW3Sj4wmDUgt0o2MIvWdcWnVgowj7UPtdUmTUFtaaa0xpLH3rXP1KXSXlzZzgiwna8kDsotsIYfJeVIvPzq5+sPmWkOeke5O305yG9UzjIzxmfyCrTWQrCYT3EGlJI/YTIZ6323Ic3KS9CZvyU3S0OtPKnnbftIVtrEvZJDX14zcJcdh+0kr/5JliHZkPHlIVqLC+ZuTdSh4gtzrI5kJc+lZpKfXl0YKF3mh5pTBXa/oATP+Au+7mpIRpsK8U2snkowht1N+qQl1Ii/IRqdNa8pzBjht1ZAMMpusgOXMRNIkR2CuLJeuCSl0vpIhTtsh8pi0dNrSSkZYShahwpBxJRfeS+bDNqXNyTD+RKo+6vOl9onkItmG4sTY1KOUVsFylnKX5OeTc6SOP4gaTM6Sg7C84CpuDRlE3qGw8c+hn5tQiABVovXkMsxTA4ZTeVSGVvYfTr7BNi7pwEdhydGXjLQBloNOkZzTp2ryChYeCpMoqcK5RplMfiCYT3qRfTAjaV9znL64NXQ4JdTv5Lr3+3RyDFb5lPA1ZivMWzXfFVj5zqszuQfL/BdgLvcUVoplxRGFoX+kzX8gu0lfBGNVN6py/hOW8aPUATZGGzlN3qM4h40kn2AHySGYIOPWUBXV3nR47f0N7NKvkdbeGF3oJfKILCNtvfaANKgVCgcMf7uS2y0h9z0CFvY0D+aBYemGGsE8QPOr8ilUw+8TGWEG7CnwEsH3jK9Sa2h/rhF1Fu0xnFe6wIz3GuaNiTWX3IC9WXRjZ1CcU3TgHYh2bcW5btg/jDztHVmYH2Ge8QQWXjKgynd4rnJrxElGUhj5KWISgpXwr5Uj22ExqgSoHBTWWNiY8M1IqjJKqjJoR3IHdjj3ZvVm2UOmwG6yKAmi/Bpx0t/oZbsYVkAOoBBWiaUDyO2jNqQbnIZi7/Gl/6WukluwGB+G6HkUtnL5qPCNW6NSydvTvtL/H/0GnKSIaknhndkAAAAASUVORK5CYII=>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAD8AAAAYCAYAAABN9iVRAAACzUlEQVR4Xu2WS+hNURTGPyGEPCMhjzxi4JFXhCLEAMnAQDKQx9BjoDCgGBDlUZJHMkBKyQQDhUyElIGRDMhMKEUkj++zzr5n3333/t//2Wacr3517977rLP22mutfYBatWrVqlWrVq1a/666khlkGelNupAJZCnp5a3L1SCygowMxruTfsFYjpy/82F7kUaR4Y0VCenlV8hOspc8J8fJIXKB3CA9G6uraybMxh7yikz05o6RR/i7AHQjh8mvgstkEjlHRnjrWqSIHSQLiv/DyGtylUwh78l90qeYryplzRkyjqwiX8jcYm4AeQJzVn7kSvbOwk56PNlB3pDZ/qKYBpJ9KE92KvlE1sNScgOZXMz1hQVF87OKsXZSmiubZEvP+qes01FwtyLPdkwK9hGyxBtbTt6hE0HWpj/D6j8mOXyPDA4n2mgMeUv2e2N6lzLBbTbXtpN61QkyL5ygTsKCnJSichGWikrJmOTwdVidVZFS/husITmdJy9QbjbXtqQMVo1PCydge3mA5nf/kVLwFNkCc0LOKAAuPdTtNecUi6C66xB03BR3w3qJeork6t3fbK5tbdz1FSc9cxrWq5IZpetHHVJddxH5DnNUUmAuoTSaiuAmmA3VbOrUdJP4m19LfqDcbK5t+XiXfITVtNaLp2RbsSaZUWPJM9iDN8l28hJ2xd0ii8ulyQiuJF9h15jbXCh1Yl2ht8k1mLN+b8mxrexUs15HehS/3XWn32qyUiyjGlJKKU3cx0H43ykZQdgzKp+h4QTMSXVzPSe7SlOVlt/5c2w7u34H74/mazmVUZXlIrgRrSekbn4Uced3kZ+wEpOmkw8o01LKtd1OLqPmkDXBXCUplXQCm9EcbWWInFNzjEldXc1NX1ujyWPYepeWUq7tdlK5qXwPwEo8W7E0k1SLqyPjTvrmvkMewprTQrSuzbXdGalsOrot/j/9Brc6hgVA8tpkAAAAAElFTkSuQmCC>

[image4]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABZklEQVR4Xu2UTytFQRjGH0URUggXKwullCRsWCgbG8mHYI+VjyGlZMFGFjaKsrAQn8BCd4tEKawo5M/z9sy55sw9t3Mvsjq/+nXP3Jk7M+eZ9w6Q8U/U0Tm67j6t/WdU02XaC028SbdpjTfmV+ToJV1w7TF6QwcKIzwm6C399Hygd+75le5Du42ooiO01bUnoQX6CiMS2KBv0G58uukefYQmDbG47Ldr7jmRRnpKz/G9K592mqeHKD7MWbqS8H0Me7V7uovSu9iCxvgxTNFF6HDbaLPXF2Mayns+7PCwBZ7psGtbXFZJndChL0FxJmKvmJR/RD09ok90iLbQM8QLwyK2qItooMconb/RRS+gauuJd6VTTv5Whh/0gNYGfamk5W81vwotYBVTMWn5D0L/AbtzKr4K0uq/g55AB9wU9JVFP7S7MH/b6Qx0sDv4weTj0GUVldg7vaZX0B30At0/o9AZZGTE+QJbLUoV33NPjQAAAABJRU5ErkJggg==>

[image5]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAH8AAAAYCAYAAADTTCLxAAAFgklEQVR4Xu2YacimUxjH/7JkssVMZJ3XzgchW4QPtkgiQ4wlSZZQirKOFElEwqAkE7KLDxOjaTIPpggJWcqSIRHig4ZIluv3XvflPve5l+d5Z8a8PeP+17/3fc597uVc/2s7R+rRo0ePHj3GDusa5xgfMF5r3LR6ucfajAuNh8qdYJ5xmXFmZUaPtRIbGwfGB4vfOxm/MR4XE3qMjgnj6flght2Nt8nTLOmWiBuGdYwHGe823mc8Xs33bWRcYvx7BD5c3LOXcXbx/55y8Y8qfk8HZhjPkNuH9fIt+VpPNR4id15ss6XxLOM+6aQC6fNuNm5XvdwKyt9l8vuuN25dvezAYBcbXzb+qdKoOdaXp9W3jPvJo2yh8aJ0UgNY3JXGV4w7ylPyY/KP4plN4Pm/GJ81rpddY/FvyA2b4yrjYrlRpwObGZ8znm/c2XiD3KaLimuA9TytujM/k8wJoM27xmvk68Zp3pQ7SxcIhvfk37GhPBN+YjwwnQR4wUlyT/xa7eIj8qfy7AB4YBqBbUDI7+R1OYDjfGk8NhlLcab82ZfnFwpQ5xE6BQt73LhFNr4mcanxEZVNJ45/k3wt6fdSpj4wfiV38KZMiMDMQXieE1mRoMCmbcC5eH4eOGSNl+SZpAbSAoI0ibm9XPg02vBSMgHpvAu8lOemaWcT42vGBfKF5eDj/1DVYTZQmSkQ/4TkGunyFrmBEH9YZPxXwHa50x5g/FUuHN8H7lG3gABn+VEenIEjjVerRcACBNa3qgfHyepwnC7x6QNYFBFJGmHuKKmVuS+oLn40apSQzZNxwG/GPzdulYzfqPLDj5CnVUCKowfZQf6Os+UGnw6QQT80Hp2MRQkbqLTZMPEJLEobdqBMQhw6zw5NoMf4S3XxCZbQsIYu8Yl4biSFPWq8RF6LblV73QYhcpv4+TjY2/izqmlrD3nKmhWTCuBcz6taOyldozZFawJRwu5IxuYb7zS+I//e1437JteJdqIeB7jXeJ3cYXCspqYwRYjcJn4+Poku8SOdDVR6767y9HJF8bsJ8cxc5C7xw1gr5J17iEqT2FQiVgZz5fV2VL5t3GXyzqmBCH5V3myRoQIL5LU8IplO/yeVDVlkCyKYdA1YO8H2sbqdG3GbRF5l8am1gRAQb8wjMkDaJn3nIreJzwIxTFrvMdDtqr57HMBa2OV8pmrdBvQ8aQpHTDLAE/JsF+Lntg0Bu2xBv9Ek8kqLTwPGjWmTFQK2NhFqF7ltPOr9cuO2yXha78cFc+RN7UQ23oSwffQ5UfoGqvZWIWCTRoE2kdvGJ9ElfnjTVMXHi6nduchxL8YhCgLh8Wm9B3mkrCqiaR2VCNLV2+RA+EUqt52sN5rlc+XpPD0fCduHnSJjDjR18cmYZM5c5Lg3ykgFXeIfbPxd1U6xKe0j0IR8kQE+guYlTX3M5778oGbY/n51YbbxlCnwRI1+fkDdpj9JD2xY+13yUhA1ORU/0v5AblccnxKQ74aa0j7v2UZlP0TGXK66bbkn1+FfhPhNjRX7yoXy9B8R2dTwnSf/uKhdgEaJhaXHxocZv5c7VYB35vV+3IBhqfGsLW0YaeY47wCseb6qmeQ042/yjBFgu/iDyqyKffKGjy0gJ3kEZtiSeezK2CmEA/IuThVTXSbBvhBxOIZEOLjC+L783DzAwj4yPiQXmev3q7oIPJNF0OikDkSq+cJ4gfEc+XM4UmYOBx9Pyt8Z7+db+Ka53DxGIGvGGnJG1mTNBMxSuR2JUCIy7BHArvNU2o2tHjbZP5lDlnhR9d0EojP+lPwkFs2WqXuXMBR80OHyVLib6hmiC3gpzgH5//8OTk05FDpG9TP9FOm8OCEcBZRgsgZa8Xd19kw9evTo0aNHjzHBP6K0YhyoCbZvAAAAAElFTkSuQmCC>

[image6]: <data:image/png;base64,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>