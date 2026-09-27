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

package com.klikli_dev.occultism.client.render;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.api.common.data.OtherworldBlockTier;
import com.klikli_dev.occultism.common.block.otherworld.IOtherworldBlock;
import com.klikli_dev.occultism.registry.OccultismEffects;
import com.klikli_dev.occultism.util.CuriosUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

import java.util.HashSet;
import java.util.Set;

public class ThirdEyeEffectRenderer implements GuiLayer {

    public static final int MAX_THIRD_EYE_DISTANCE = 10;
    /**
     * Interval in ticks after which the uncovered area is rescanned even if the player did not move,
     * so blocks placed or re-sent by the server are picked up.
     */
    public static final int RESCAN_INTERVAL_TICKS = 10;
    public static final Identifier THIRD_EYE_TEXTURE = Identifier.fromNamespaceAndPath(Occultism.MODID,
            "textures/overlay/third_eye.png");
    public boolean thirdEyeActiveLastTick = false;
    public boolean gogglesActiveLastTick = false;
    public boolean staffActiveLastTick = false;

    public Set<BlockPos> uncoveredBlocks = new HashSet<>();

    protected BlockPos lastScanOrigin = null;
    protected int lastScanStaffRange = -1;
    protected int ticksSinceLastScan = 0;

    @SubscribeEvent
    public void onPlayerTick(Post event) {
        if (event.getEntity().level().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            this.onUncoverTick(event.getEntity());
        }
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        if (this.gogglesActiveLastTick || this.thirdEyeActiveLastTick) {
            this.renderOverlay(guiGraphics);
        }
    }

    public void renderOverlay(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, THIRD_EYE_TEXTURE, 0, 0, 0.0f, 0.0f,
                guiGraphics.guiWidth(), guiGraphics.guiHeight(), 256, 256);
    }

    /**
     * Resets the currently uncovered blocks
     *
     * @param level the level.
     * @param clear true to delete the list of uncovered blocks.
     */
    public void resetUncoveredBlocks(Level level, boolean clear) {
        for (BlockPos pos : this.uncoveredBlocks) {
            this.coverBlock(level, pos);
        }
        if (clear)
            this.uncoveredBlocks.clear();
    }

    /**
     * Updates the uncovered otherworld blocks around the player based on the currently active sources of sight
     * (third eye effect, otherworld goggles, true sight staff).
     * Only rescans the area if the player moved to a different block, the sources changed,
     * or {@link #RESCAN_INTERVAL_TICKS} passed since the last scan.
     *
     * @param player the player.
     */
    public void onUncoverTick(Player player) {
        Level level = player.level();
        boolean hasGoggles = CuriosUtil.hasGoggles(player);
        boolean hasStaff = CuriosUtil.hasStaff(player);
        var effect = player.getEffect(OccultismEffects.THIRD_EYE);
        boolean hasThirdEye = effect != null && effect.getDuration() > 1;

        boolean wasActive = this.thirdEyeActiveLastTick || this.gogglesActiveLastTick || this.staffActiveLastTick;
        boolean sourcesChanged = hasThirdEye != this.thirdEyeActiveLastTick || hasGoggles != this.gogglesActiveLastTick
                || hasStaff != this.staffActiveLastTick;
        this.thirdEyeActiveLastTick = hasThirdEye;
        this.gogglesActiveLastTick = hasGoggles;
        this.staffActiveLastTick = hasStaff;

        if (!hasThirdEye && !hasGoggles && !hasStaff) {
            if (!this.uncoveredBlocks.isEmpty()) {
                //cover blocks again. Try twice: keep the list on the tick we deactivate, clear it on the next tick.
                this.resetUncoveredBlocks(level, !wasActive);
            }
            this.lastScanOrigin = null;
            return;
        }

        int staffRange = hasStaff ? Occultism.CLIENT_CONFIG.visuals.trueSightStaffRange.getAsInt() : -1;
        BlockPos origin = player.blockPosition();
        this.ticksSinceLastScan++;
        if (sourcesChanged || staffRange != this.lastScanStaffRange || !origin.equals(this.lastScanOrigin)
                || this.ticksSinceLastScan >= RESCAN_INTERVAL_TICKS) {
            this.ticksSinceLastScan = 0;
            this.lastScanOrigin = origin;
            this.lastScanStaffRange = staffRange;
            //goggles see tier two blocks, the third eye effect only tier one.
            OtherworldBlockTier nearTier = hasGoggles ? OtherworldBlockTier.TWO : OtherworldBlockTier.ONE;
            this.updateUncoveredBlocks(level, origin, hasThirdEye || hasGoggles, nearTier, staffRange);
        }
    }

    /**
     * Uncovers the otherworld blocks visible from the given origin and covers the previously uncovered blocks that are no longer visible.
     *
     * @param level      the level.
     * @param origin     the position to scan around.
     * @param nearSight  true if blocks up to nearTier within MAX_THIRD_EYE_DISTANCE are visible.
     * @param nearTier   the max tier visible within MAX_THIRD_EYE_DISTANCE.
     * @param staffRange the distance within which tier two blocks are visible, or -1 if the staff is not active.
     */
    public void updateUncoveredBlocks(Level level, BlockPos origin, boolean nearSight, OtherworldBlockTier nearTier, int staffRange) {
        int nearRange = nearSight ? MAX_THIRD_EYE_DISTANCE : -1;
        int distance = Math.max(nearRange, staffRange);
        Set<BlockPos> visibleBlocks = new HashSet<>();
        if (distance >= 0) {
            BlockPos.betweenClosed(origin.offset(-distance, -distance, -distance),
                    origin.offset(distance, distance, distance)).forEach(pos -> {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof IOtherworldBlock block) {
                    int tierLevel = block.getTier().getLevel();
                    int offset = Math.max(Math.abs(pos.getX() - origin.getX()),
                            Math.max(Math.abs(pos.getY() - origin.getY()), Math.abs(pos.getZ() - origin.getZ())));
                    boolean visible = (offset <= staffRange && tierLevel <= OtherworldBlockTier.TWO.getLevel())
                            || (offset <= nearRange && tierLevel <= nearTier.getLevel());
                    if (visible) {
                        if (!state.getValue(IOtherworldBlock.UNCOVERED)) {
                            level.setBlock(pos, state.setValue(IOtherworldBlock.UNCOVERED, true), Block.UPDATE_IMMEDIATE);
                        }
                        visibleBlocks.add(pos.immutable());
                    }
                }
            });
        }

        //only cover blocks that are no longer visible, to avoid needlessly flipping block states.
        for (BlockPos pos : this.uncoveredBlocks) {
            if (!visibleBlocks.contains(pos)) {
                this.coverBlock(level, pos);
            }
        }
        this.uncoveredBlocks = visibleBlocks;
    }

    protected void coverBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IOtherworldBlock && state.getValue(IOtherworldBlock.UNCOVERED)) //handle replaced or removed blocks gracefully
            level.setBlock(pos, state.setValue(IOtherworldBlock.UNCOVERED, false), 1);
    }
}
