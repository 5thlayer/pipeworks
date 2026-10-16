// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksConfig;
import io.github._5thlayer.pipeworks.fluiditem.FluidItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A storage tank: a node of the configured capacity, 25,000 millibuckets by default, that opens
 * every face. It is a plain block, since its fluid is its segment's and the segment is saved with
 * the level. Tanks that touch through open faces are a tank group, drawn as one tank: its
 * {@link #JOINS} are the faces towards the tanks of its group, which leave out their caps and glass,
 * and its {@link #LEVEL} is its own share of its group's fill. The server sets both, and they are all
 * the client knows of it (ADRs 0003 and 0008).
 */
public class StorageTankBlock extends Block implements FluidSegments.SegmentBlock {

    public static final MapCodec<StorageTankBlock> CODEC = simpleCodec(StorageTankBlock::new);

    /**
     * How full this tank is, 0 to 15, as its share of its group's fill: {@code TankGroupFill.steps}.
     * Drawn as the height of the fluid in the glass.
     */
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    /** By face, whether a tank of its group is there, the way a pipe's arms are set. */
    public static final Map<Direction, BooleanProperty> JOINS = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN));

    public StorageTankBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(LEVEL, 0);
        for (BooleanProperty join : JOINS.values()) {
            state = state.setValue(join, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
        JOINS.values().forEach(builder::add);
    }

    @Override
    public boolean hasArms() {
        return true;
    }

    @Override
    public boolean armOn(BlockState state, Direction side) {
        return state.getValue(JOINS.get(side));
    }

    @Override
    public BlockState withArms(BlockState state, Predicate<Direction> arm) {
        for (Map.Entry<Direction, BooleanProperty> join : JOINS.entrySet()) {
            state = state.setValue(join.getValue(), arm.test(join.getKey()));
        }
        return state;
    }

    /** Joined only to the tanks it is linked to: a pipe, a port or a fluid inventory beside it stays apart. */
    @Override
    public boolean drawsArmTowards(@Nullable BlockState node) {
        return node != null && node.getBlock() instanceof StorageTankBlock;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public long capacity() {
        return PipeworksConfig.tankCapacity();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return SegmentBlocks.refuses(context.getLevel(), context.getClickedPos(), context.getPlayer()) ? null : defaultBlockState();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return FluidItems.use(level, pos, player, hand);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        SegmentBlocks.onPlace(level, pos, state, oldState, this);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        SegmentBlocks.onRemoval(level, pos);
    }
}
