package com.klikli_dev.occultism.common.entity.ai.behaviour;

import com.klikli_dev.occultism.common.entity.ai.BrainUtil;
import com.klikli_dev.occultism.common.entity.ai.sensor.NearestTreeSensor;
import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.klikli_dev.occultism.registry.OccultismMemoryTypes;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class FellTreeBehaviour<E extends SpiritEntity> extends ExtendedBehaviour<E> {
    public static final double FELL_TREE_RANGE_SQUARE = Math.pow(3.5, 2); //we're comparing to square distance
    public static final int MAX_FELL_BLOCKS = 256;
    public static final int MAX_FELL_HORIZONTAL_DISTANCE = 12;
    public static final int MAX_STUMP_BLOCKS = 16;

    private static final List<Pair<MemoryModuleType<?>, MemoryStatus>> MEMORY_REQUIREMENTS = ObjectArrayList.of(
            Pair.of(OccultismMemoryTypes.NEAREST_TREE.get(), MemoryStatus.VALUE_PRESENT));

    protected int breakingTime;
    protected int previousBreakProgress;
    protected BlockPos breakingPos;

    public FellTreeBehaviour() {
        super(MEMORY_REQUIREMENTS, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        var treePos = BrainUtil.getMemory(entity, OccultismMemoryTypes.NEAREST_TREE.get());
        var dist = entity.distanceToSqr(Vec3.atCenterOf(treePos));
        return dist <= FellTreeBehaviour.FELL_TREE_RANGE_SQUARE;
    }


    protected boolean shouldKeepRunning(E entity) {
        return BrainUtil.hasMemory(entity, OccultismMemoryTypes.NEAREST_TREE.get());
    }

    @Override
    protected void tick(E entity) {
        var treePos = BrainUtil.getMemory(entity, OccultismMemoryTypes.NEAREST_TREE.get());
        if (NearestTreeSensor.isLog(entity.level(), treePos)) {
            BrainUtil.setMemory(entity, MemoryModuleType.LOOK_TARGET, new BlockPosTracker(treePos));
            this.breakingTime++;
            entity.swing(InteractionHand.MAIN_HAND, true);
            int i = (int) ((float) this.breakingTime / 160.0F * 10.0F);
            if (this.breakingTime % 20 == 0) {
                entity.playSound(SoundEvents.WOOD_HIT, 1, 1);
                entity.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, 0.5F);
            }
            if (i != this.previousBreakProgress) {
                entity.level().destroyBlockProgress(entity.getId(), treePos, i);
                this.previousBreakProgress = i;
                this.breakingPos = treePos;
            }
            if (this.breakingTime == 160) {
                entity.playSound(SoundEvents.WOOD_BREAK, 1, 1);
                List<BlockPos> stump = this.getAllStump(treePos, entity.level());
                this.fellTree(entity, treePos);
                var felled = BrainUtil.getMemory(entity, OccultismMemoryTypes.LAST_FELLED_TREE.get());
                if (felled != null) {
                    felled.addAll(stump);
                } else {
                    felled = stump;
                }
                BrainUtil.setMemory(entity, OccultismMemoryTypes.LAST_FELLED_TREE.get(), felled);
                this.doStop((ServerLevel) entity.level(), entity, entity.level().getGameTime());
                //we stop here (even though the above condition would save us) because sensor might reset last felled tree meanwhile
            }

        } else {
            //if the tree is gone, just stop and reset.
            this.doStop((ServerLevel) entity.level(), entity, entity.level().getGameTime());
        }
    }

    protected void start(E entity) {
        this.breakingTime = 0;
        this.previousBreakProgress = -1;
        this.breakingPos = null;
    }

    protected void stop(E entity) {
        //reset the break animation, otherwise it stays visible if we abort
        if (this.breakingPos != null) {
            entity.level().destroyBlockProgress(entity.getId(), this.breakingPos, -1);
            this.breakingPos = null;
        }
        BrainUtil.clearMemory(entity, OccultismMemoryTypes.NEAREST_TREE.get());
    }

    private void fellTree(E entity, BlockPos treePos) {
        Level level = entity.level();
        BlockPos base = treePos;
        Queue<BlockPos> blocks = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        blocks.add(base);
        int felledBlocks = 0;

        while (!blocks.isEmpty() && felledBlocks < MAX_FELL_BLOCKS) {

            BlockPos pos = blocks.remove();
            if (!visited.add(pos)) {
                continue;
            }

            //stay close to the stump and never load chunks
            if (Math.abs(pos.getX() - base.getX()) > MAX_FELL_HORIZONTAL_DISTANCE ||
                    Math.abs(pos.getZ() - base.getZ()) > MAX_FELL_HORIZONTAL_DISTANCE ||
                    !level.hasChunkAt(pos)) {
                continue;
            }

            if (!NearestTreeSensor.isLog(level, pos)) {
                continue;
            }

            for (Direction facing : Plane.HORIZONTAL) {
                BlockPos pos2 = pos.relative(facing);
                if (!visited.contains(pos2)) {
                    blocks.add(pos2);
                }
            }

            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos2 = pos.offset(-1 + x, 1, -1 + z);
                    if (!visited.contains(pos2)) {
                        blocks.add(pos2);
                    }
                }
            }

            level.destroyBlock(pos, true);
            felledBlocks++;
        }

    }

    /**
     * Collects the stump positions of the tree, including the given stump position itself.
     * Multi-trunk trees (e.g. 2x2) have multiple connected stump positions.
     */
    private List<BlockPos> getAllStump(BlockPos treePos, Level level) {
        List<BlockPos> stump = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toCheck = new ArrayDeque<>();
        stump.add(treePos);
        visited.add(treePos);
        toCheck.add(treePos);

        while (!toCheck.isEmpty() && stump.size() < MAX_STUMP_BLOCKS) {
            BlockPos pos = toCheck.remove();
            for (Direction facing : Plane.HORIZONTAL) {
                BlockPos posR = pos.relative(facing);
                if (visited.add(posR)
                        && level.hasChunkAt(posR)
                        && level.getBlockState(posR).is(BlockTags.LOGS)
                        && level.getBlockState(posR.below()).is(BlockTags.DIRT)) {
                    stump.add(posR);
                    toCheck.add(posR);
                }
            }
        }
        return stump;
    }

}
