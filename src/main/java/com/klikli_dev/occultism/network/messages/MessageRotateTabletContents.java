/*
 * MIT License
 *
 * Copyright 2026 klikli-dev
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

package com.klikli_dev.occultism.network.messages;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.network.IMessage;
import com.klikli_dev.occultism.registry.OccultismItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Collections;

/**
 * Asks the server to rotate the contents of the wormhole tablet held in the given hand.
 * The client never sends item data, the server only rotates the contents the tablet already has.
 */
public class MessageRotateTabletContents implements IMessage {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Occultism.MODID, "rotate_tablet_contents");
    public static final Type<MessageRotateTabletContents> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageRotateTabletContents> STREAM_CODEC = CustomPacketPayload.codec(MessageRotateTabletContents::encode, MessageRotateTabletContents::new);

    public InteractionHand hand;
    public boolean forward;

    public MessageRotateTabletContents(RegistryFriendlyByteBuf buf) {
        this.decode(buf);
    }

    public MessageRotateTabletContents(InteractionHand hand, boolean forward) {
        this.hand = hand;
        this.forward = forward;
    }

    @Override
    public void onServerReceived(MinecraftServer minecraftServer, ServerPlayer player) {
        //do not touch the tablet while another menu (e.g. the tablet menu) is open, it would write its own copy back.
        if (player.containerMenu != player.inventoryMenu)
            return;

        ItemStack stack = player.getItemInHand(this.hand);
        if (!stack.is(OccultismItems.WORMHOLE_TABLET))
            return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null)
            return;

        NonNullList<ItemStack> items = NonNullList.create();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack content = contents.getStackInSlot(i);
            if (!content.isEmpty())
                items.add(content.copy());
        }
        if (items.size() < 2)
            return;

        Collections.rotate(items, this.forward ? 1 : -1);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeBoolean(this.forward);
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.forward = buf.readBoolean();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
