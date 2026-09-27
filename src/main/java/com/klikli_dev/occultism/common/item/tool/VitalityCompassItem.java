package com.klikli_dev.occultism.common.item.tool;

import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismTags.Entities;
import com.klikli_dev.occultism.util.ItemNBTUtil;
import com.klikli_dev.occultism.util.TextUtil;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class VitalityCompassItem extends Item {

    public static final float NOT_FOUND = 0;
    private static final double TARGET_UPDATE_DISTANCE_SQ = 4;

    public VitalityCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {

        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;

        if (!target.isAlive())
            return InteractionResult.PASS;

        //This is called from PlayerEventHandler#onPlayerRightClickEntity, because we need to bypass sitting entities processInteraction
        if (target.level().isClientSide())
            return InteractionResult.PASS;

        if (target.getType().builtInRegistryHolder().is(Entities.VITALITY_COMPASS_DENY_LIST)) {
            player.sendSystemMessage(Component.translatable(this.getDescriptionId() + ".message.target_blocked", target.getName()));
            return InteractionResult.FAIL;
        } else {
            ItemNBTUtil.setSpiritEntityUUID(stack, target.getUUID());
            ItemNBTUtil.setBoundSpiritName(stack, target.getName().getString());
            player.sendSystemMessage(Component.translatable(this.getDescriptionId() + ".message.target_linked", target.getName()));
            player.swing(hand);
            player.setItemInHand(hand, stack); //need to write the item back to hand, otherwise we only modify a copy
            player.inventoryMenu.broadcastChanges();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack pStack, TooltipContext pContext, TooltipDisplay pTooltipDisplay, Consumer<Component> pTooltipAdder, TooltipFlag pTooltipFlag) {
        super.appendHoverText(pStack, pContext, pTooltipDisplay, pTooltipAdder, pTooltipFlag);
        pTooltipAdder.accept(Component.translatable(this.getDescriptionId() + ".tooltip",
                TextUtil.formatDemonName(ItemNBTUtil.getBoundSpiritName(pStack))));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        //legacy: the angle used to be computed on the server every tick, it is now computed on the client.
        if (stack.has(OccultismDataComponents.COMPASS_ANGLE))
            stack.remove(OccultismDataComponents.COMPASS_ANGLE);

        GlobalPos currentTarget = stack.get(OccultismDataComponents.COMPASS_TARGET);
        GlobalPos newTarget = null;
        if (stack.has(OccultismDataComponents.SPIRIT_ENTITY_UUID)) {
            Entity target = level.getEntity(stack.get(OccultismDataComponents.SPIRIT_ENTITY_UUID));
            if (target != null) {
                newTarget = GlobalPos.of(target.level().dimension(), target.blockPosition());
            }
        }

        //only store the target position if it changed noticeably (or periodically), to avoid syncing the stack every tick
        if (newTarget == null) {
            if (currentTarget != null)
                stack.remove(OccultismDataComponents.COMPASS_TARGET);
        } else if (currentTarget == null || !currentTarget.dimension().equals(newTarget.dimension()) ||
                currentTarget.pos().distSqr(newTarget.pos()) > TARGET_UPDATE_DISTANCE_SQ ||
                (level.getGameTime() % 20 == 0 && !currentTarget.pos().equals(newTarget.pos()))) {
            stack.set(OccultismDataComponents.COMPASS_TARGET, newTarget);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }
}
