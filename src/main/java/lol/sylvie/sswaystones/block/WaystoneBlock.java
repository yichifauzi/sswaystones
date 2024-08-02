package lol.sylvie.sswaystones.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.compat.GeyserViewerGui;
import lol.sylvie.sswaystones.gui.JavaViewerGui;
import lol.sylvie.sswaystones.storage.PlayerData;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import lol.sylvie.sswaystones.util.HashUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class WaystoneBlock extends BlockWithEntity implements PolymerBlock {
    public WaystoneBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(WaystoneBlock::new);
    }

    // Visuals
    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return Blocks.STONE_BRICK_WALL.getDefaultState()
                .with(WallBlock.UP, true)
                .with(WallBlock.EAST_SHAPE, WallShape.LOW)
                .with(WallBlock.WEST_SHAPE, WallShape.LOW)
                .with(WallBlock.NORTH_SHAPE, WallShape.LOW)
                .with(WallBlock.SOUTH_SHAPE, WallShape.LOW);
    }

    // Should be indestructible by TNT, also lets me ignore some edge cases.
    @Override
    public float getBlastResistance() {
        return 1200;
    }

    @Override
    public float getHardness() {
        return 2;
    }

    // Placing & breaking
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient || placer == null) return;

        assert world.getServer() != null;
        WaystoneStorage storage = WaystoneStorage.getServerState(world.getServer());
        storage.createWaystone(pos, world, placer);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (world.isClient()) return super.onBreak(world, pos, state, player);

        assert world.getServer() != null;
        WaystoneStorage storage = WaystoneStorage.getServerState(world.getServer());
        WaystoneRecord record = storage.getWaystone(HashUtil.getHash(pos, world.getRegistryKey()));

        if (world.getBlockEntity(pos) instanceof WaystoneBlockEntity waystoneBlockEntity) {
            waystoneBlockEntity.removeDisplay();
        }

        if (record != null) storage.destroyWaystone(record);

        return super.onBreak(world, pos, state, player);
    }

    // Open GUI
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            assert serverPlayer.getServer() != null;
            WaystoneStorage storage = WaystoneStorage.getServerState(serverPlayer.getServer());
            PlayerData playerData = WaystoneStorage.getPlayerState(serverPlayer);

            // Make sure we remember it!
            String waystoneHash = HashUtil.getHash(pos, world.getRegistryKey());
            WaystoneRecord record = storage.getWaystone(waystoneHash);
            if (record != null) {
                if (!playerData.discoveredWaystones.contains(waystoneHash)) {
                    playerData.discoveredWaystones.add(waystoneHash);
                    player.sendMessage(Text.translatable("message.sswaystones.discovered", Text.literal(record.getWaystoneName()).formatted(Formatting.BOLD, Formatting.GOLD)).formatted(Formatting.DARK_PURPLE));
                }
            } else {
                Waystones.LOGGER.warn("There is some FUNKY stuff happening with these waystones!");
                return ActionResult.FAIL;
            }

            if (FabricLoader.getInstance().isModLoaded("geyser-fabric") && GeyserViewerGui.openGuiIfBedrock(serverPlayer, record)) {
                return ActionResult.SUCCESS;
            }

            JavaViewerGui gui = new JavaViewerGui(serverPlayer, record);
            gui.updateMenu();
            gui.open();

            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }

    // Block entity
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WaystoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type.equals(ModBlocks.WAYSTONE_BLOCK_ENTITY)) {
            return (tickerWorld, pos, tickerState, blockEntity) -> WaystoneBlockEntity.tick(tickerWorld, (WaystoneBlockEntity) blockEntity);
        }
        return null;
    }
}