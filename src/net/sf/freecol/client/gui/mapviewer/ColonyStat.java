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

import java.util.function.Predicate;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;


/**
 * LarryDGray's Mods: a single warehouse-goods or unit-type count that
 * can be displayed under a colony's name on the map, one at a time,
 * selected via the colony stat toolbar.
 *
 * Letter codes are placeholders -- icons may replace them later.
 */
public enum ColonyStat {

    SOLDIERS('S') {
        @Override
        public int getValue(Colony colony) {
            return countUnits(colony, u -> !u.isNaval() && u.getRole() != null
                && "model.role.soldier".equals(u.getRole().getId()));
        }
    },
    DRAGOONS('D') {
        @Override
        public int getValue(Colony colony) {
            return countUnits(colony, u -> !u.isNaval() && u.getRole() != null
                && "model.role.dragoon".equals(u.getRole().getId()));
        }
    },
    SHIPS('P') {
        @Override
        public int getValue(Colony colony) {
            return countUnits(colony, Unit::isNaval);
        }
    },
    WAGON_TRAINS('W') {
        @Override
        public int getValue(Colony colony) {
            return countUnits(colony,
                u -> "model.unit.wagonTrain".equals(u.getType().getId()));
        }
    },
    ARTILLERY('A') {
        @Override
        public int getValue(Colony colony) {
            return countUnits(colony, u -> {
                String id = u.getType().getId();
                return "model.unit.artillery".equals(id)
                    || "model.unit.damagedArtillery".equals(id);
            });
        }
    },
    FOOD('F') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.food");
        }
    },
    LUMBER('L') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.lumber");
        }
    },
    ORE('O') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.ore");
        }
    },
    TOOLS('T') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.tools");
        }
    },
    MUSKETS('M') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.muskets");
        }
    },
    HORSES('H') {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.horses");
        }
    };


    private final char letter;

    ColonyStat(char letter) {
        this.letter = letter;
    }

    /**
     * Get the placeholder letter shown on this stat's toolbar button
     * and label prefix.
     *
     * @return The letter.
     */
    public char getLetter() {
        return this.letter;
    }

    /**
     * Compute this stat's current value for a colony.
     *
     * @param colony The {@code Colony} to check.
     * @return The value to display.
     */
    public abstract int getValue(Colony colony);

    /**
     * Count units on a colony's tile (garrisoned defenders, ships in
     * port, wagon trains -- not colonists working inside the colony)
     * matching a filter.
     *
     * @param colony The {@code Colony} to check.
     * @param filter The {@code Predicate} units must match.
     * @return The count.
     */
    private static int countUnits(Colony colony, Predicate<Unit> filter) {
        int count = 0;
        for (Unit u : colony.getTile().getUnitList()) {
            if (filter.test(u)) count++;
        }
        return count;
    }

    /**
     * Get the warehouse count of a goods type.
     *
     * @param colony The {@code Colony} to check.
     * @param goodsTypeId The goods type identifier.
     * @return The amount in the warehouse.
     */
    private static int getGoodsCount(Colony colony, String goodsTypeId) {
        final Specification spec = colony.getSpecification();
        final GoodsType goodsType = spec.getGoodsType(goodsTypeId);
        return (goodsType == null) ? 0 : colony.getGoodsCount(goodsType);
    }
}
