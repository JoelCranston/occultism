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

package com.klikli_dev.occultism.common.entity.ai;

import com.klikli_dev.occultism.api.common.blockentity.IStorageControllerProxy;
import com.klikli_dev.occultism.api.common.data.GlobalBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StorageProxySearch {

    /**
     * Finds the closest storage controller proxy linked to the given storage controller.
     * Only iterates the block entities of already loaded chunks, instead of querying every position in the area.
     *
     * @param level                     the level to search in.
     * @param center                    the center of the search area.
     * @param horizontalRange           the horizontal range around the center.
     * @param verticalRange             the vertical range around the center.
     * @param storageControllerPosition the storage controller the proxy needs to be linked to.
     * @param sorter                    the sorter used to determine the closest proxy.
     * @return the closest linked proxy, or null if none was found.
     */
    public static BlockEntity findClosestLinkedProxy(Level level, BlockPos center, int horizontalRange, int verticalRange,
                                                     GlobalBlockPos storageControllerPosition, BlockSorter sorter) {
        if (storageControllerPosition == null)
            return null;

        int minX = center.getX() - horizontalRange;
        int maxX = center.getX() + horizontalRange;
        int minY = center.getY() - verticalRange;
        int maxY = center.getY() + verticalRange;
        int minZ = center.getZ() - horizontalRange;
        int maxZ = center.getZ() + horizontalRange;

        BlockEntity closest = null;
        BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos();
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                chunkPos.set(SectionPos.sectionToBlockCoord(chunkX), center.getY(), SectionPos.sectionToBlockCoord(chunkZ));
                //never load chunks for this search
                if (!level.hasChunkAt(chunkPos))
                    continue;

                for (BlockEntity blockEntity : level.getChunkAt(chunkPos).getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY ||
                            pos.getZ() < minZ || pos.getZ() > maxZ || blockEntity.isRemoved())
                        continue;

                    if (blockEntity instanceof IStorageControllerProxy proxy &&
                            storageControllerPosition.equals(proxy.getLinkedStorageControllerPosition())) {
                        if (closest == null || sorter.compare(pos, closest.getBlockPos()) < 0)
                            closest = blockEntity;
                    }
                }
            }
        }
        return closest;
    }
}
