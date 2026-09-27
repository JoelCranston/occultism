package com.klikli_dev.occultism.common.entity.ai.sensor;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.OccultismConstants.Color;
import com.klikli_dev.occultism.common.entity.ai.BrainUtil;
import com.klikli_dev.occultism.network.Networking;
import com.klikli_dev.occultism.network.messages.MessageSelectBlock;
import com.klikli_dev.occultism.registry.OccultismMemoryTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;

public class UnreachableTreeWalkTargetSensor<E extends LivingEntity> extends ExtendedSensor<E> {
    private static final List<MemoryModuleType<?>> MEMORIES = ObjectArrayList.of(
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            OccultismMemoryTypes.LAST_TREE_WALK_TARGET.get(),
            OccultismMemoryTypes.WALK_TARGET_UNREACHABLE.get()
    );
    /**
     * If we cannot reach the same walk target for this long, it is considered unreachable, regardless of its height.
     */
    public static final int UNREACHABLE_AFTER_TICKS = 20 * 5;
    private long lastUnpathableTime = 0L;
    private BlockPos unreachableTarget;
    private long unreachableTargetSince = 0L;

    public UnreachableTreeWalkTargetSensor() {
    }

    public List<MemoryModuleType<?>> memoriesUsed() {
        return MEMORIES;
    }

    protected void doTick(ServerLevel level, E entity) {
        Brain<?> brain = entity.getBrain();

        var walkTarget = BrainUtil.getMemory(entity, OccultismMemoryTypes.LAST_TREE_WALK_TARGET.get());
        if (walkTarget == null) {
            this.resetState(brain);
        } else {
            Long unpathableTime = BrainUtil.getMemory(brain, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (unpathableTime == null) {
                this.resetState(brain);
            } else {
                if (this.lastUnpathableTime == 0L) {
                    this.lastUnpathableTime = unpathableTime;
                } else if (this.lastUnpathableTime == unpathableTime) {
                    BrainUtil.clearMemory(brain, OccultismMemoryTypes.WALK_TARGET_UNREACHABLE.get());

                    //track how long we could not reach this specific target, ground level targets are otherwise never marked as unreachable
                    BlockPos targetPos = walkTarget.getTarget().currentBlockPosition();
                    if (!targetPos.equals(this.unreachableTarget)) {
                        this.unreachableTarget = targetPos;
                        this.unreachableTargetSince = level.getGameTime();
                    } else if (level.getGameTime() - this.unreachableTargetSince >= UNREACHABLE_AFTER_TICKS) {
                        this.unreachableTarget = null;
                        BrainUtil.setMemory(brain, OccultismMemoryTypes.WALK_TARGET_UNREACHABLE.get(), true);
                        BrainUtil.clearMemory(brain, OccultismMemoryTypes.LAST_TREE_WALK_TARGET.get());
                        if (Occultism.DEBUG.debugAI) {
                            Networking.sendToTracking(entity, new MessageSelectBlock(targetPos, 50000, Color.RED));
                        }
                    }
                } else if (this.lastUnpathableTime < unpathableTime) {
                    this.lastUnpathableTime = unpathableTime;
                    BrainUtil.setMemory(brain, OccultismMemoryTypes.WALK_TARGET_UNREACHABLE.get(), walkTarget.getTarget().currentBlockPosition().getY() > entity.getEyeY());
                    BrainUtil.clearMemory(brain, OccultismMemoryTypes.LAST_TREE_WALK_TARGET.get());
                    if (Occultism.DEBUG.debugAI) {
                        Networking.sendToTracking(entity, new MessageSelectBlock(walkTarget.getTarget().currentBlockPosition(), 50000, Color.RED));
                    }
                }

            }
        }
    }

    private void resetState(Brain<?> brain) {
        if (this.lastUnpathableTime > 0L) {
            BrainUtil.clearMemory(brain, OccultismMemoryTypes.WALK_TARGET_UNREACHABLE.get());
        }

        this.lastUnpathableTime = 0L;
        this.unreachableTarget = null;
    }
}
