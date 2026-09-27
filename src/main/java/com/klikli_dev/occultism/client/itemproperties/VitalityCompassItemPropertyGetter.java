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

package com.klikli_dev.occultism.client.itemproperties;

import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class VitalityCompassItemPropertyGetter implements RangeSelectItemModelProperty {
    public static final MapCodec<VitalityCompassItemPropertyGetter> MAP_CODEC = MapCodec.unit(new VitalityCompassItemPropertyGetter());

    private final CompassWobble wobbleRandom = new CompassWobble();

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        long gameTime = level != null ? level.getGameTime() : 0;
        GlobalPos target = stack.get(OccultismDataComponents.COMPASS_TARGET);
        if (target != null && owner instanceof Entity entity && entity.level().dimension().equals(target.dimension())) {
            return this.getRotationTowardsCompassTarget(entity, target.pos());
        }

        //no target (or target in another dimension / not loaded) -> spin randomly
        return this.getRandomlySpinningRotation(seed, gameTime);
    }

    @Override
    public MapCodec<VitalityCompassItemPropertyGetter> type() {
        return MAP_CODEC;
    }

    private float getRotationTowardsCompassTarget(Entity entity, BlockPos pos) {
        double angleToPos = this.getAngleFromEntityToPos(entity, pos);
        double rotation = this.getWrappedVisualRotationY(entity);
        double angle = 0.5 - (rotation - 0.25 - angleToPos);
        return Mth.positiveModulo((float) angle, 1.0F);
    }

    private double getAngleFromEntityToPos(Entity entity, BlockPos pos) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        return Math.atan2(vec3.z() - entity.getZ(), vec3.x() - entity.getX()) / 6.2831854820251465;
    }

    private double getWrappedVisualRotationY(Entity entity) {
        return entity instanceof Player player ? Mth.positiveModulo((player.getYHeadRot() / 360.0F), 1.0) : Mth.positiveModulo((entity.getVisualRotationYInDegrees() / 360.0F), 1.0);
    }

    private float getRandomlySpinningRotation(int seed, long ticks) {
        if (this.wobbleRandom.shouldUpdate(ticks)) {
            this.wobbleRandom.update(ticks, Math.random());
        }

        double d0 = this.wobbleRandom.rotation + (double) ((float) this.hash(seed) / 2.14748365E9F);
        return Mth.positiveModulo((float) d0, 1.0F);
    }

    private int hash(int value) {
        return value * 1327217883;
    }

    static class CompassWobble {
        double rotation;
        private double deltaRotation;
        private long lastUpdateTick;

        CompassWobble() {
        }

        boolean shouldUpdate(long ticks) {
            return this.lastUpdateTick != ticks;
        }

        void update(long ticks, double rotation) {
            this.lastUpdateTick = ticks;
            double d0 = rotation - this.rotation;
            d0 = Mth.positiveModulo(d0 + 0.5, 1.0) - 0.5;
            this.deltaRotation += d0 * 0.1;
            this.deltaRotation *= 0.8;
            this.rotation = Mth.positiveModulo(this.rotation + this.deltaRotation, 1.0);
        }
    }
}
