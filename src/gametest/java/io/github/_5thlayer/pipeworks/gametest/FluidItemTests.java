// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.Objects;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Whether a fluid item used on a tank or pipe fills or empties it through its segment
 * (5thlayer/factoryworks ADR-0121): a bucket, and another mod's portable tank through
 * {@code Capabilities.Fluid.ITEM}. The amounts themselves are {@code FluidItemRuleTest}'s.
 *
 * <p>Each test lays its nodes in a row at z 4, which {@code at(x)} indexes, and clicks the top of
 * the node at x 1.
 */
final class FluidItemTests {

    private static final int Z = 4;

    private FluidItemTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_water_bucket_fills_a_tank_and_an_empty_bucket_takes_it_back", 20, FluidItemTests::bucketInAndOut);
        tests.test("an_empty_bucket_from_a_stack_goes_to_the_inventory_filled", 20, FluidItemTests::stackOfBuckets);
        tests.test("a_bucket_of_another_fluid_is_refused_and_places_nothing", 20, FluidItemTests::refusesOtherFluid);
        tests.test("a_bucket_used_in_creative_is_left_unchanged", 20, FluidItemTests::creative);
        tests.test("a_bucket_used_on_a_pipe_fills_its_segment", 20, FluidItemTests::onPipe);
        tests.test("a_bucket_that_does_not_fit_whole_is_refused", 20, FluidItemTests::doesNotFit);
        tests.test("another_mods_portable_tank_fills_and_empties_a_tank", 20, FluidItemTests::portableTank);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static void tank(GameTestHelper helper) {
        helper.setBlock(at(1), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
    }

    private static Player player(GameTestHelper helper, GameType mode, ItemStack held) {
        Player player = helper.makeMockPlayer(mode);
        // A mock player takes the mode's abilities only when told, and creative's are what leave a bucket be.
        mode.updatePlayerAbilities(player.getAbilities());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** A plain right-click on the top of the node at x 1 with what {@code player} holds. */
    private static void use(GameTestHelper helper, Player player) {
        BlockPos pos = helper.absolutePos(at(1));
        helper.useBlock(at(1), player, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
    }

    private static void put(GameTestHelper helper, Fluid fluid, int amount) {
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(1)), null);
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
        }
    }

    private static SegmentGraph.@Nullable Contents contents(GameTestHelper helper) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.getLevel(), helper.absolutePos(at(1)));
    }

    /** Fails unless the segment at x 1 holds {@code amount} mB of {@code fluid}, or nothing for 0. */
    private static void holds(GameTestHelper helper, @Nullable Fluid fluid, long amount, String when) {
        SegmentGraph.Contents contents = contents(helper);
        String key = amount == 0 || fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid).toString();
        if (contents == null || contents.amount() != amount || !Objects.equals(contents.fluid(), key)) {
            helper.fail(when + ", the segment holds " + contents + ", not " + amount + " mB of " + fluid, at(1));
        }
    }

    private static void holding(GameTestHelper helper, Player player, ItemStack expected, String when) {
        ItemStack held = player.getMainHandItem();
        if (!ItemStack.matches(held, expected)) {
            helper.fail(when + ", the hand holds " + held + ", not " + expected, at(1));
        }
    }

    private static void bucketInAndOut(GameTestHelper helper) {
        tank(helper);
        Player player = player(helper, GameType.SURVIVAL, new ItemStack(Items.WATER_BUCKET));
        use(helper, player);
        holds(helper, Fluids.WATER, 1_000, "after a water bucket");
        holding(helper, player, new ItemStack(Items.BUCKET), "after emptying a water bucket");
        use(helper, player);
        holds(helper, Fluids.WATER, 0, "after an empty bucket");
        holding(helper, player, new ItemStack(Items.WATER_BUCKET), "after filling the bucket");
        helper.succeed();
    }

    private static void stackOfBuckets(GameTestHelper helper) {
        tank(helper);
        put(helper, Fluids.WATER, 2_000);
        Player player = player(helper, GameType.SURVIVAL, new ItemStack(Items.BUCKET, 3));
        use(helper, player);
        holds(helper, Fluids.WATER, 1_000, "after one of three empty buckets");
        holding(helper, player, new ItemStack(Items.BUCKET, 2), "after filling one of three empty buckets");
        if (player.getInventory().countItem(Items.WATER_BUCKET) != 1) {
            helper.fail("the filled bucket is not in the inventory", at(1));
        }
        helper.succeed();
    }

    private static void refusesOtherFluid(GameTestHelper helper) {
        tank(helper);
        put(helper, Fluids.WATER, 500);
        Player player = player(helper, GameType.SURVIVAL, new ItemStack(Items.LAVA_BUCKET));
        use(helper, player);
        holds(helper, Fluids.WATER, 500, "after a lava bucket on water");
        holding(helper, player, new ItemStack(Items.LAVA_BUCKET), "after a refused lava bucket");
        if (!helper.getBlockState(at(1).above()).isAir()) {
            helper.fail("the refused bucket placed its fluid beside the tank", at(1).above());
        }
        helper.succeed();
    }

    private static void creative(GameTestHelper helper) {
        tank(helper);
        Player player = player(helper, GameType.CREATIVE, new ItemStack(Items.WATER_BUCKET));
        use(helper, player);
        holds(helper, Fluids.WATER, 1_000, "after a water bucket in creative");
        holding(helper, player, new ItemStack(Items.WATER_BUCKET), "after a water bucket in creative");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        use(helper, player);
        holds(helper, Fluids.WATER, 0, "after an empty bucket in creative");
        holding(helper, player, new ItemStack(Items.BUCKET), "after an empty bucket in creative");
        helper.succeed();
    }

    private static void onPipe(GameTestHelper helper) {
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        helper.setBlock(at(2), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        Player player = player(helper, GameType.SURVIVAL, new ItemStack(Items.WATER_BUCKET));
        use(helper, player);
        holds(helper, Fluids.WATER, 1_000, "after a water bucket on the pipe");
        holding(helper, player, new ItemStack(Items.BUCKET), "after emptying a water bucket on the pipe");
        helper.succeed();
    }

    /** A lone pipe holds 100 mB, so a bucket's 1,000 cannot go in whole. */
    private static void doesNotFit(GameTestHelper helper) {
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        Player player = player(helper, GameType.SURVIVAL, new ItemStack(Items.WATER_BUCKET));
        use(helper, player);
        holds(helper, null, 0, "after a water bucket on a lone pipe");
        holding(helper, player, new ItemStack(Items.WATER_BUCKET), "after a water bucket on a lone pipe");
        helper.succeed();
    }

    private static void portableTank(GameTestHelper helper) {
        tank(helper);
        Player player = player(helper, GameType.SURVIVAL, TestCanister.holding(Fluids.WATER, TestCanister.CAPACITY));
        use(helper, player);
        holds(helper, Fluids.WATER, 4_000, "after a full canister");
        if (!TestCanister.content(player.getMainHandItem()).isEmpty()) {
            helper.fail("the emptied canister holds " + TestCanister.content(player.getMainHandItem()), at(1));
        }
        put(helper, Fluids.WATER, 1_000);
        player.setItemInHand(InteractionHand.MAIN_HAND, TestCanister.holding(Fluids.WATER, 1_000));
        use(helper, player);
        holds(helper, Fluids.WATER, 2_000, "after a canister of 1,000 mB filled from 5,000");
        var content = TestCanister.content(player.getMainHandItem());
        if (content.getFluid() != Fluids.WATER || content.getAmount() != TestCanister.CAPACITY) {
            helper.fail("the part-full canister came back holding " + content, at(1));
        }
        helper.succeed();
    }
}
