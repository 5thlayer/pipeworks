// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github._5thlayer.groundworks.DismantleSpan;
import io.github._5thlayer.groundworks.DismantleStart;
import io.github._5thlayer.groundworks.Dismantles;
import io.github._5thlayer.groundworks.Groundworks;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.groundworks.ShortestPath;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.dismantle.PipeFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pipeworks' claim: its pipe Dismantle Family joins pipes where their arms link and no other way,
 * claims only the pipes its tag names, and tells the player its own reasons (ADR 0005). The gesture,
 * the queue, the stored start and the shortest path are Groundworks' own tests; the ring here holds
 * only that the family names a tie in its own words.
 *
 * <p>Each test sneak-clicks a start with an iron pickaxe, which the shipped {@code groundworks:dismantles}
 * holds, through the player's game mode, asks {@link Dismantles#spanTo} for the end, clicks it, and
 * holds the world, the inventory and the stored start to the span.
 */
final class PipeDismantleTests {

    private static final BlockPos START = new BlockPos(2, 1, 3);

    private PipeDismantleTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_pipe_dismantle_takes_up_a_straight_run", 20, helper -> {
            List<BlockPos> run = row(helper, 6);
            takesUp(helper, run.get(1), run.get(4), run.subList(1, 5), List.of(run.getFirst(), run.getLast()));
        });
        tests.test("a_pipe_dismantle_between_opposite_points_of_a_ring_changes_nothing", 20, helper -> {
            for (int x = 0; x < 3; x++) pipe(helper, START.east(x));
            for (int z = 1; z < 3; z++) pipe(helper, START.east(2).south(z));
            for (int x = 1; x >= 0; x--) pipe(helper, START.east(x).south(2));
            pipe(helper, START.south(1));
            refused(helper, START, START.east(2).south(2), ShortestPath.Refused.TIED, "message.pipeworks.dismantle.tied");
        });
        tests.test("a_pipe_dismantle_across_a_closed_connection_changes_nothing", 20, helper -> {
            List<BlockPos> run = row(helper, 4);
            FluidPipes.close(helper.getLevel(), helper.absolutePos(run.get(1)), Direction.EAST);
            refused(helper, run.getFirst(), run.getLast(), ShortestPath.Refused.NOT_JOINED, "message.pipeworks.dismantle.not_joined");
        });
        tests.test("a_pipe_dismantle_ending_on_a_machine_changes_nothing", 20, helper -> {
            helper.setBlock(START.east(3), TestPort.BLOCK.get());
            row(helper, 3);
            refused(helper, START, START.east(3), Refusal.Dismantle.NOT_SAME_KIND, "message.groundworks.dismantle_not_same_kind");
        });
        tests.test("a_creative_pipe_is_a_member_of_the_family", 20, helper -> {
            helper.setBlock(START.east(1), PipeworksRegistries.CREATIVE_PIPE.get());
            pipe(helper, START);
            pipe(helper, START.east(2));
            takesUp(helper, START, START.east(2), List.of(START, START.east(1), START.east(2)), List.of());
        });
        tests.test("a_pipe_dismantle_with_no_room_drops_the_rest_at_the_players_feet", 20, PipeDismantleTests::fullInventory);
        tests.test("an_item_outside_the_tool_tag_stores_no_pipe_dismantle_start", 20, PipeDismantleTests::notATool);
    }

    private static void fullInventory(GameTestHelper helper) {
        List<BlockPos> run = row(helper, 3);
        var player = started(helper, run.getFirst());
        for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
        }
        click(helper, player, run.getLast());
        if (standing(helper, run) != null) {
            helper.fail("a dismantle with no room left " + helper.getBlockState(standing(helper, run)), standing(helper, run));
            return;
        }
        Map<Item, Integer> dropped = new HashMap<>();
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3))) {
            dropped.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        }
        if (!Map.of(pipeItem(), 3).equals(dropped)) {
            helper.fail("a dismantle with no room dropped " + dropped + " at the player's feet, not 3 pipes", START);
            return;
        }
        helper.succeed();
    }

    private static void notATool(GameTestHelper helper) {
        List<BlockPos> run = row(helper, 3);
        var player = new ListeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        use(helper, player, run.getFirst(), true);
        if (storedStart(player) != null) {
            helper.fail("a sneak-click with a stick stored a dismantle start", run.getFirst());
            return;
        }
        helper.succeed();
    }

    /**
     * Asks the span of a click at {@code end} from {@code start}, clicks, and holds the world to it:
     * exactly {@code taken} gone, {@code kept} still pipes, a pipe item for each taken block, and the
     * start cleared.
     */
    private static void takesUp(GameTestHelper helper, BlockPos start, BlockPos end, List<BlockPos> taken, List<BlockPos> kept) {
        var player = started(helper, start);
        DismantleSpan span = spanOf(helper, player, end);
        List<BlockPos> expected = taken.stream().map(helper::absolutePos).toList();
        if (span == null || span.isRefused() || !Objects.equals(Set.copyOf(expected), Set.copyOf(span.takes()))) {
            helper.fail("the dismantle to " + end + " was planned as " + described(span) + ", not " + expected, end);
            return;
        }
        click(helper, player, end);
        BlockPos left = standing(helper, taken);
        if (left != null) {
            helper.fail("the span named " + left + " and the click left " + helper.getBlockState(left), left);
            return;
        }
        for (BlockPos pos : kept) {
            if (!helper.getBlockState(pos).is(PipeFamily.PIPES)) {
                helper.fail("the dismantle took " + pos + ", which is outside its span", pos);
                return;
            }
        }
        Map<Item, Integer> carried = inventory(player);
        int pipes = carried.getOrDefault(pipeItem(), 0) + carried.getOrDefault(PipeworksRegistries.CREATIVE_PIPE_ITEM.get(), 0);
        if (pipes != taken.size() || carried.values().stream().mapToInt(Integer::intValue).sum() != pipes) {
            helper.fail("the dismantle handed over " + carried + ", not " + taken.size() + " pipes", end);
            return;
        }
        if (!helper.getEntities(EntityType.ITEM).isEmpty()) {
            helper.fail("the dismantle left items on the ground", end);
            return;
        }
        if (storedStart(player) != null) {
            helper.fail("a dismantle left its start stored", end);
            return;
        }
        helper.succeed();
    }

    private static void refused(GameTestHelper helper, BlockPos start, BlockPos end, Refusal refusal, String message) {
        var player = started(helper, start);
        DismantleSpan span = spanOf(helper, player, end);
        if (span == null || span.refusal() != refusal || !span.takes().isEmpty()) {
            helper.fail("the dismantle to " + end + " was planned as " + described(span) + ", not " + refusal, end);
            return;
        }
        Map<BlockPos, BlockState> before = world(helper);
        Map<Item, Integer> carried = inventory(player);
        var stored = player.getMainHandItem().getComponents();
        player.heard.clear();
        click(helper, player, end);

        Map<BlockPos, BlockState> after = world(helper);
        List<BlockPos> changed = before.keySet().stream().filter(pos -> before.get(pos) != after.get(pos)).toList();
        if (!changed.isEmpty()) {
            helper.fail("a refused dismantle changed " + changed.size() + " blocks, first at " + changed.getFirst(), end);
            return;
        }
        if (!carried.equals(inventory(player)) || !Objects.equals(stored, player.getMainHandItem().getComponents())) {
            helper.fail("a refused dismantle changed the player's inventory or the stored start", end);
            return;
        }
        if (!player.heard.equals(List.of(message))) {
            helper.fail("the player was told " + player.heard + ", not " + message, end);
            return;
        }
        helper.succeed();
    }

    /** {@code count} pipes running east from {@link #START}. */
    private static List<BlockPos> row(GameTestHelper helper, int count) {
        List<BlockPos> run = new ArrayList<>();
        for (int i = 0; i < count; i++) run.add(pipe(helper, START.east(i)));
        return run;
    }

    private static BlockPos pipe(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, PipeworksRegistries.PIPE.get().defaultBlockState());
        return at;
    }

    private static Item pipeItem() {
        return PipeworksRegistries.PIPE_ITEM.get();
    }

    /** A survival player holding an iron pickaxe, standing on the platform, who has sneak-clicked a start at {@code start}. */
    private static ListeningPlayer started(GameTestHelper helper, BlockPos start) {
        var player = new ListeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(START.getX() + 0.5, START.getY(), START.getZ() - 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        use(helper, player, start, true);
        if (!helper.absolutePos(start).equals(storedStart(player))) {
            helper.fail("a sneak-click on a pipe stored " + storedStart(player) + " as the start", start);
        }
        return player;
    }

    private static @Nullable BlockPos storedStart(ListeningPlayer player) {
        DismantleStart start = player.getMainHandItem().get(Groundworks.DISMANTLE_START.get());
        return start == null ? null : start.pos();
    }

    private static @Nullable DismantleSpan spanOf(GameTestHelper helper, ListeningPlayer player, BlockPos end) {
        return Dismantles.spanTo(helper.getLevel(), player.getMainHandItem(), helper.absolutePos(end));
    }

    private static Object described(@Nullable DismantleSpan span) {
        return span == null ? "nothing" : span.isRefused() ? span.refusal() : span.takes();
    }

    private static void click(GameTestHelper helper, ListeningPlayer player, BlockPos at) {
        use(helper, player, at, false);
    }

    private static void use(GameTestHelper helper, ListeningPlayer player, BlockPos at, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        BlockPos absolute = helper.absolutePos(at);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static @Nullable BlockPos standing(GameTestHelper helper, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!helper.getBlockState(pos).isAir()) return pos;
        }
        return null;
    }

    private static Map<BlockPos, BlockState> world(GameTestHelper helper) {
        return BlockPos.betweenClosedStream(helper.getBounds())
                .map(BlockPos::immutable)
                .collect(Collectors.toMap(Function.identity(), pos -> helper.getLevel().getBlockState(pos)));
    }

    /** What the player carries besides the pickaxe. */
    private static Map<Item, Integer> inventory(ListeningPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        counts.remove(player.getMainHandItem().getItem());
        return counts;
    }
}
