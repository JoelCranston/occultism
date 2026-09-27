package com.klikli_dev.occultism.common.misc;

import com.klikli_dev.occultism.common.item.tool.FlamingPasteItem;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public class FireballDispenseBehavior extends DefaultDispenseItemBehavior {
    private final ProjectileItem projectileItem;
    private final ProjectileItem.DispenseConfig dispenseConfig;

    public FireballDispenseBehavior(Item item) {
        if (item instanceof ProjectileItem projectileItem) {
            this.projectileItem = projectileItem;
            this.dispenseConfig = projectileItem.createDispenseConfig();
        } else {
            String var10002 = String.valueOf(item);
            throw new IllegalArgumentException(var10002 + " not instance of " + ProjectileItem.class.getSimpleName());
        }
    }

    public ItemStack execute(BlockSource source, ItemStack dispensed) {
        ServerLevel level = source.level();
        Direction direction = source.state().getValue(DispenserBlock.FACING);
        Position position = this.dispenseConfig.positionFunction().getDispensePosition(source, direction);
        //the fake player is shared, so only move it temporarily and restore its position afterwards
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        Vec3 previousPosition = fakePlayer.position();
        fakePlayer.setPos(position.x(), position.y(), position.z());
        int random = level.getRandom().nextInt(3)+1;
        if (this.projectileItem instanceof FlamingPasteItem flamingPasteItem)
            Projectile.spawnProjectileUsingShoot(flamingPasteItem.asBigProjectile(level, fakePlayer, dispensed, direction, random),
                level, dispensed, direction.getStepX(), direction.getStepY(), direction.getStepZ(),
                this.dispenseConfig.power(), this.dispenseConfig.uncertainty()*0.1F);
        dispensed.hurtAndBreak(random, level, (LivingEntity) null, (item) -> {});
        fakePlayer.setPos(previousPosition);
        return dispensed;
    }

    protected void playSound(BlockSource source) {
        source.level().levelEvent(this.dispenseConfig.overrideDispenseEvent().orElse(1002), source.pos(), 0);
    }
}
