/*
 * MIT License
 *
 * Copyright 2020 klikli-dev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.klikli_dev.occultism.common.entity.ai.goal;

import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ReturnToWorkAreaGoal extends Goal {

    protected final SpiritEntity entity;
    protected int executionChance;

    public ReturnToWorkAreaGoal(SpiritEntity entity) {
        this(entity, 10);
    }

    public ReturnToWorkAreaGoal(SpiritEntity entity, int executionChance) {
        this.entity = entity;
        this.executionChance = executionChance;
        //we steer navigation, so we must not run in parallel to other goals that move the entity
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {

        //fire on a slow tick based on chance
        long worldTime = this.entity.level().getGameTime() % 10;
        if (this.entity.getNoActionTime() >= 100 && worldTime != 0) {
            return false;
        }
        if (this.entity.getRandom().nextInt(this.executionChance) != 0 && worldTime != 0) {
            return false;
        }

        //do not interfere with depositing items, the deposit target may be outside the work area
        if (!this.entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() &&
                (this.entity.getDepositPosition().isPresent() || this.entity.getDepositEntityUUID().isPresent())) {
            return false;
        }

        return this.entity.getWorkAreaPosition().isPresent() && this.isOutsideWorkArea();
    }

    /**
     * @return true if the entity is outside of the work area (which uses only half height, same as item pickup).
     */
    protected boolean isOutsideWorkArea() {
        BlockPos center = this.entity.getWorkAreaCenter();
        int workAreaSize = this.entity.getWorkAreaSize().getValue();
        return Math.abs(this.entity.getX() - (center.getX() + 0.5)) > workAreaSize ||
                Math.abs(this.entity.getY() - center.getY()) > workAreaSize / 2.0 ||
                Math.abs(this.entity.getZ() - (center.getZ() + 0.5)) > workAreaSize;
    }

    @Override
    public void tick() {
        if (!this.entity.getWorkAreaPosition().isPresent()) {
            this.stop();
            this.entity.getNavigation().stop();
        } else {
            this.entity.getNavigation().moveTo(this.entity.getNavigation().createPath(
                    this.entity.getWorkAreaPosition().orElse(this.entity.blockPosition()), 0), 1.0f);
            double distance = this.entity.position().distanceTo(
                    Vec3.atCenterOf(this.entity.getWorkAreaPosition().orElse(this.entity.blockPosition())));
            if (distance < 1F) {
                this.entity.setDeltaMovement(0, 0, 0);
                this.entity.getNavigation().stop();
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.entity.getNavigation().createPath(
                this.entity.getWorkAreaPosition().orElse(this.entity.blockPosition()), 0), 1.0f);
        super.start();
    }

}
