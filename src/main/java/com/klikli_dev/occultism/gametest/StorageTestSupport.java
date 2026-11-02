// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.common.blockentity.StorageControllerBlockEntity;
import com.klikli_dev.occultism.common.container.storage.StorageControllerContainer;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared setup for the storage game tests: storage controllers and a server player whose sent packets are recorded
 * instead of being written to a network channel.
 */
public class StorageTestSupport {

    public static StorageControllerBlockEntity placeController(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, OccultismBlocks.STORAGE_CONTROLLER.get());
        return helper.getBlockEntity(pos, StorageControllerBlockEntity.class);
    }

    public static void store(StorageControllerBlockEntity controller, ItemStack stack) {
        controller.insertStack(stack.copy(), false);
    }

    public static int stored(StorageControllerBlockEntity controller, Item item) {
        return controller.itemStackHandler.get(new ItemStack(item));
    }

    /**
     * Creates a server player standing at the center of the given relative position. The player is not added to the
     * level or the player list, its packets are recorded by a {@link RecordingPacketListener}.
     */
    public static ServerPlayer createPlayer(GameTestHelper helper, BlockPos relativePos) {
        var level = helper.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "storage-test-player");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        //sets player.connection
        new RecordingPacketListener(server, connection, player, cookie);
        player.initInventoryMenu();
        moveTo(helper, player, relativePos);
        return player;
    }

    public static void moveTo(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relativePos));
        player.snapTo(pos.x, pos.y, pos.z);
    }

    public static RecordingPacketListener packets(ServerPlayer player) {
        return (RecordingPacketListener) player.connection;
    }

    public static StorageControllerContainer openController(ServerPlayer player, StorageControllerBlockEntity controller) {
        player.openMenu(controller);
        return (StorageControllerContainer) player.containerMenu;
    }

    public static class RecordingPacketListener extends ServerGamePacketListenerImpl {
        public final List<Packet<?>> sentPackets = new ArrayList<>();

        public RecordingPacketListener(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener listener) {
            this.sentPackets.add(packet);
        }

        public <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
            List<T> result = new ArrayList<>();
            for (var packet : this.sentPackets) {
                if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload) && type.isInstance(payload)) {
                    result.add(type.cast(payload));
                }
            }
            return result;
        }

        public List<ClientboundSystemChatPacket> systemMessages() {
            List<ClientboundSystemChatPacket> result = new ArrayList<>();
            for (var packet : this.sentPackets) {
                if (packet instanceof ClientboundSystemChatPacket chat) {
                    result.add(chat);
                }
            }
            return result;
        }

        public void clear() {
            this.sentPackets.clear();
        }
    }
}
