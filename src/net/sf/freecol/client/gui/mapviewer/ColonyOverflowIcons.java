/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.mapviewer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Unit;


/**
 * LarryDGray's Mods: an always-computed, toggleable row of tiny goods
 * icons shown under a colony's name on the map, below the {@link
 * ColonyBuildingBadges} line - one icon per 100 units a storable
 * goods type is currently at, combining warehouse stock and whatever
 * is parked on a fortified, non-trade-route overflow carrier at the
 * colony's tile (the same carrier concept used by the Warehouse
 * Overflow to Carrier feature). Capped at {@link #MAX_ICONS} icons
 * per goods type, beyond which a single icon carries a "xN" count
 * overlay instead of repeating.
 */
public final class ColonyOverflowIcons {

    private ColonyOverflowIcons() {}

    /** How many combined units of a goods type each icon represents. */
    private static final int UNITS_PER_ICON = 100;

    /** Repeat a goods type's icon at most this many times before
     *  switching to a single icon with a "xN" overlay. */
    public static final int MAX_ICONS = 3;


    /**
     * One goods type's overflow icon count for a colony.
     */
    public static final class Entry {

        public final GoodsType type;
        public final int count;

        Entry(GoodsType type, int count) {
            this.type = type;
            this.count = count;
        }
    }


    /**
     * Compute the overflow icon counts for a colony.
     *
     * @param colony The {@code Colony} to check.
     * @return A list of {@code Entry}, one per goods type currently
     *     at 100+ combined units, never null.
     */
    public static List<Entry> getOverflowCounts(Colony colony) {
        List<Entry> result = new ArrayList<>();
        for (GoodsType type : colony.getSpecification().getStorableGoodsTypeList()) {
            if (type.limitIgnored()) continue;
            int total = colony.getGoodsCount(type);
            for (Unit u : colony.getTile().getUnitList()) {
                if (!u.isCarrier() || u.getTradeRoute() != null
                    || u.getState() != Unit.UnitState.FORTIFIED) continue;
                total += u.getGoodsCount(type);
            }
            int count = total / UNITS_PER_ICON;
            if (count >= 1) result.add(new Entry(type, count));
        }
        return result;
    }

    /**
     * One icon (or icon + "xN" overlay) to draw, at a given x offset.
     */
    private static final class DrawOp {
        final BufferedImage icon;
        final String overlayText;
        DrawOp(BufferedImage icon, String overlayText) {
            this.icon = icon;
            this.overlayText = overlayText;
        }
    }

    /**
     * Compose the overflow icon row into a single image.
     *
     * @param lib The {@code ImageLibrary} to fetch icons from.
     * @param g2d A {@code Graphics2D} used only to size the "xN"
     *     overlay text (via {@link ImageLibrary#getStringImage}).
     * @param entries The overflow entries to draw, from {@link
     *     #getOverflowCounts}.
     * @param overlayColor The color for the "xN" overlay text.
     * @param overlayFont The font for the "xN" overlay text, or null
     *     to skip the overlay entirely (counts over {@link
     *     #MAX_ICONS} then just show a single icon with no count).
     * @param spacing Pixel gap between icons.
     * @return The composed row image, or null if {@code entries} is
     *     empty.
     */
    public static BufferedImage buildIconRow(ImageLibrary lib, Graphics2D g2d,
            List<Entry> entries, Color overlayColor, Font overlayFont, int spacing) {
        if (entries == null || entries.isEmpty()) return null;

        List<DrawOp> ops = new ArrayList<>();
        for (Entry e : entries) {
            BufferedImage icon = lib.getScaledGoodsTypeImage(e.type);
            if (e.count <= MAX_ICONS) {
                for (int i = 0; i < e.count; i++) ops.add(new DrawOp(icon, null));
            } else {
                ops.add(new DrawOp(icon, "×" + e.count));
            }
        }
        if (ops.isEmpty()) return null;

        int totalWidth = spacing * (ops.size() - 1);
        int maxHeight = 0;
        for (DrawOp op : ops) {
            totalWidth += op.icon.getWidth();
            maxHeight = Math.max(maxHeight, op.icon.getHeight());
        }
        final int overlayPad = (overlayFont == null) ? 0 : lib.scaleInt(6);
        BufferedImage row = new BufferedImage(
            Math.max(1, totalWidth + overlayPad),
            Math.max(1, maxHeight + overlayPad),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D rg = row.createGraphics();
        rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        int x = 0;
        for (DrawOp op : ops) {
            int y = maxHeight - op.icon.getHeight();
            rg.drawImage(op.icon, x, y, null);
            if (op.overlayText != null && overlayFont != null) {
                BufferedImage textImg = lib.getStringImage(g2d, op.overlayText,
                    overlayColor, overlayFont);
                rg.drawImage(textImg,
                    x + op.icon.getWidth() - textImg.getWidth() / 2,
                    y + op.icon.getHeight() - textImg.getHeight() / 2, null);
            }
            x += op.icon.getWidth() + spacing;
        }
        rg.dispose();
        return row;
    }
}
