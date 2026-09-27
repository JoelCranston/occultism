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

package com.klikli_dev.occultism.util;

import com.klikli_dev.occultism.api.common.data.OtherworldBlockTier;
import com.klikli_dev.occultism.api.common.item.IOtherworldTool;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import net.minecraft.world.item.ItemStack;

public class OtherworldUtil {

    //region Static Methods

    public static OtherworldBlockTier getToolLevel(ItemStack tool) {
        OtherworldBlockTier toolTier = OtherworldBlockTier.NONE;
        if (tool.getItem() instanceof IOtherworldTool otherTool)
            toolTier = otherTool.getHarvestTier(tool);
        if (tool.has(OccultismDataComponents.OTHERWORLD_TOOL_TIER))
            toolTier = OtherworldBlockTier.get(tool.get(OccultismDataComponents.OTHERWORLD_TOOL_TIER));
        return toolTier;
    }
    //endregion Static Methods
}
