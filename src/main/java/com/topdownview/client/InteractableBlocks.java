package com.topdownview.client;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ブロックのインタラクト可否と操作種別を判定する。
 * プロンプト表示・カリング保護・クリック分岐はすべてこのクラスを単一情報源とする。
 *
 * 判定は Mod 追加ブロックを漏れなく拾うため、次の順でフォールバックする:
 * バニラ基底クラス instanceof → GUI の MenuProvider → バニラタグ → BlockState プロパティ。
 */
public final class InteractableBlocks {

    public enum InteractionKind {
        NONE,     // 操作不可
        OPEN,     // チェスト系コンテナ（空間プロンプト「?」対象）
        INTERACT  // その他の右クリック操作（ドア・ボタン・GUI・鐘など）
    }

    private InteractableBlocks() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    // ==================== プロンプト用分類 ====================

    /**
     * ブロックの右クリック操作種別を判定する。MenuProvider の取得に {@link Level} を要する。
     */
    public static InteractionKind classify(BlockState state, Level level, BlockPos pos) {
        if (state == null || level == null || pos == null || state.isAir()) {
            return InteractionKind.NONE;
        }

        // ユーザー定義 (interactions.json) が最優先。NONE は明示的な除外。
        InteractionKind override = InteractionRegistry.getOverride(state.getBlock());
        if (override != null) {
            return override;
        }

        InteractionKind byBlock = classifyByBlock(state);
        if (byBlock != InteractionKind.NONE) {
            return byBlock;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        // バニラ基底を継承しない MOD 産コンテナも、ブロックエンティティが
        // インベントリ（Container）なら「開く」として扱う。
        // かまど・ホッパー等の既知ユーティリティは保護対象なので除外し、INTERACT に委ねる。
        if (blockEntity instanceof Container && !isProtectionOnlyBlock(state)) {
            return InteractionKind.OPEN;
        }

        // GUI 持ち: BaseEntityBlock 以外の独自実装も含めてジェネリックに検出
        if (blockEntity instanceof MenuProvider || state.getMenuProvider(level, pos) != null) {
            return InteractionKind.INTERACT;
        }

        return classifyByProperty(state);
    }

    // クラス/タグ/プロパティで決まる分類は Block 単位で不変なので、スレッドローカルに結果を持つ。
    // ワーカースレッドのメッシュ生成で同一種類のブロックを大量に判定するため、instanceof連鎖を毎回
    // 走らせない。位置依存なのは BlockEntity 系のみで、その場合だけ NEEDS_POS として都度判定する。
    private static final byte UNKNOWN = 0;
    private static final byte TRUE = 1;
    private static final byte FALSE = 2;
    private static final byte NEEDS_POS = 3;
    private static final ThreadLocal<Reference2ByteOpenHashMap<Block>> BLOCK_CLASSIFICATION =
            ThreadLocal.withInitial(() -> {
                Reference2ByteOpenHashMap<Block> map = new Reference2ByteOpenHashMap<>();
                map.defaultReturnValue(UNKNOWN);
                return map;
            });

    /**
     * カリング保護対象か判定する。プロンプト対象に加え、
     * 昇降・装飾・レッドストーン等の可視保護が必要なブロックも含む。
     */
    public static boolean isInteractable(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || level == null || pos == null) return false;

        Block block = state.getBlock();

        // OPEN / INTERACT の明示指定は保護対象。NONE はプロンプトのみ除外して保護は維持し、
        // EXCLUDE は保護からも除外する。レジストリは再読込され得るためキャッシュしない。
        InteractionKind override = InteractionRegistry.getOverride(block);
        if (override != null) {
            if (override != InteractionKind.NONE) {
                return true;
            }
            if (InteractionRegistry.isUnprotected(block)) {
                return false;
            }
        }

        Reference2ByteOpenHashMap<Block> cache = BLOCK_CLASSIFICATION.get();
        byte cached = cache.getByte(block);
        if (cached == UNKNOWN) {
            cached = classifyPure(state);
            cache.put(block, cached);
        }
        if (cached == TRUE) return true;
        if (cached == FALSE) return false;

        // NEEDS_POS: ブロックエンティティに依存するため位置を伴う判定を行う。
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider || blockEntity instanceof Container) return true;
        if (classifyByProperty(state) != InteractionKind.NONE) return true;
        return isProtectionOnlyBlock(state);
    }

    /** 位置にも設定にも依存しない分類。BlockEntity 系のみ位置依存なので NEEDS_POS を返す。 */
    private static byte classifyPure(BlockState state) {
        if (classifyByBlock(state) != InteractionKind.NONE) return TRUE;

        if (state.getBlock() instanceof BaseEntityBlock) return NEEDS_POS;

        if (classifyByProperty(state) != InteractionKind.NONE) return TRUE;

        return isProtectionOnlyBlock(state) ? TRUE : FALSE;
    }

    // ==================== 判定ヘルパー ====================

    /**
     * クラス・タグで解決できる操作種別を返す（Level 不要）。
     */
    private static InteractionKind classifyByBlock(BlockState state) {
        Block block = state.getBlock();

        if (isOpenContainer(block)) {
            return InteractionKind.OPEN;
        }

        if (block instanceof DoorBlock || state.is(BlockTags.DOORS)
                || block instanceof TrapDoorBlock || state.is(BlockTags.TRAPDOORS)
                || block instanceof FenceGateBlock || state.is(BlockTags.FENCE_GATES)
                || block instanceof ButtonBlock || state.is(BlockTags.BUTTONS)
                || block instanceof LeverBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof BedBlock || state.is(BlockTags.BEDS)
                || block instanceof BellBlock
                || block instanceof CakeBlock
                || block instanceof JukeboxBlock
                || block instanceof NoteBlock) {
            return InteractionKind.INTERACT;
        }

        return InteractionKind.NONE;
    }

    /**
     * バニラ基底を継承しない Mod ブロックを標準プロパティで拾うヒューリスティック。
     * OPEN は開閉系、FACING+POWERED はボタン/レバー系にほぼ固有（オブザーバーのみ除外）。
     */
    private static InteractionKind classifyByProperty(BlockState state) {
        boolean openable = state.hasProperty(BlockStateProperties.OPEN);
        boolean toggle = state.hasProperty(BlockStateProperties.POWERED)
                && state.hasProperty(BlockStateProperties.FACING)
                && !(state.getBlock() instanceof ObserverBlock);
        return (openable || toggle) ? InteractionKind.INTERACT : InteractionKind.NONE;
    }

    private static boolean isOpenContainer(Block block) {
        return block instanceof AbstractChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof EnderChestBlock;
    }

    /**
     * 右クリック操作は持たないがカリング保護が必要なブロック（圧力板・はしご・足場・植木鉢等）。
     */
    private static boolean isProtectionOnlyBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PressurePlateBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof BeaconBlock
                || block instanceof AnvilBlock
                || block instanceof SmithingTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof CartographyTableBlock
                || block instanceof LoomBlock
                || block instanceof CraftingTableBlock
                || block instanceof EnchantmentTableBlock
                || block instanceof CampfireBlock
                || block instanceof ComposterBlock
                || block instanceof LecternBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof LadderBlock
                || block instanceof ScaffoldingBlock
                || block instanceof FlowerPotBlock;
    }

    /**
     * ボタン・レバー等の小さな面付けブロックか。支持ブロックがカリングされると宙に浮くため、
     * カリング保護では視線による無条件保護を与えず近接(Yバンド)のみに留める。
     * ボタン等は noCollission で衝突形状が空のため視覚形状(getShape)で判定する。
     */
    public static boolean isSmallDecoration(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB bounds = shape.bounds();
        return bounds.getXsize() <= 0.5 && bounds.getYsize() <= 0.5 && bounds.getZsize() <= 0.5;
    }
}
