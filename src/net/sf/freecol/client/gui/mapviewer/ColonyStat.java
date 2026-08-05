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
 * LarryDGray's Mods: a single warehouse-goods, garrison-unit, or
 * working-colonist-profession count that can be displayed under a
 * colony's name on the map, one at a time, selected via the colony
 * stat toolbar.
 *
 * Codes are letter placeholders -- icons may replace them later. Row 1
 * is garrison units and warehouse goods; row 2 (isSecondRow() true) is
 * working colonist professions, shown on a second toolbar row.
 */
public enum ColonyStat {

    // Row 1: garrison units (on the colony's tile, not working in it)
    // and warehouse goods.
    SOLDIERS("S", false) {
        @Override
        public int getValue(Colony colony) {
            return countTileUnits(colony, u -> !u.isNaval() && u.getRole() != null
                && "model.role.soldier".equals(u.getRole().getId()));
        }
    },
    DRAGOONS("D", false) {
        @Override
        public int getValue(Colony colony) {
            return countTileUnits(colony, u -> !u.isNaval() && u.getRole() != null
                && "model.role.dragoon".equals(u.getRole().getId()));
        }
    },
    SHIPS("P", false) {
        @Override
        public int getValue(Colony colony) {
            return countTileUnits(colony, Unit::isNaval);
        }
    },
    WAGON_TRAINS("W", false) {
        @Override
        public int getValue(Colony colony) {
            return countTileUnits(colony,
                u -> "model.unit.wagonTrain".equals(u.getType().getId()));
        }
    },
    ARTILLERY("A", false) {
        @Override
        public int getValue(Colony colony) {
            return countTileUnits(colony, u -> {
                String id = u.getType().getId();
                return "model.unit.artillery".equals(id)
                    || "model.unit.damagedArtillery".equals(id);
            });
        }
    },
    FOOD("F", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.food");
        }
    },
    LUMBER("L", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.lumber");
        }
    },
    ORE("O", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.ore");
        }
    },
    TOOLS("T", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.tools");
        }
    },
    MUSKETS("M", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.muskets");
        }
    },
    HORSES("H", false) {
        @Override
        public int getValue(Colony colony) {
            return getGoodsCount(colony, "model.goods.horses");
        }
    },

    // Row 2: working colonist professions (expert unit types currently
    // assigned to a building or tile in the colony).
    FARMERS("Fm", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.expertFarmer");
        }
    },
    FISHERMEN("Fi", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.expertFisherman");
        }
    },
    LUMBERJACKS("Lj", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.expertLumberJack");
        }
    },
    ORE_MINERS("Om", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.expertOreMiner");
        }
    },
    SILVER_MINERS("Sm", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.expertSilverMiner");
        }
    },
    CARPENTERS("Cp", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.masterCarpenter");
        }
    },
    BLACKSMITHS("Bs", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.masterBlacksmith");
        }
    },
    PREACHERS("Pr", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.firebrandPreacher");
        }
    },
    STATESMEN("St", true) {
        @Override
        public int getValue(Colony colony) {
            return countWorkers(colony, "model.unit.elderStatesman");
        }
    };


    private final String code;
    private final boolean secondRow;

    ColonyStat(String code, boolean secondRow) {
        this.code = code;
        this.secondRow = secondRow;
    }

    /**
     * Get the placeholder letter code shown on this stat's toolbar
     * button and label prefix.
     *
     * @return The code.
     */
    public String getLetter() {
        return this.code;
    }

    /**
     * Does this stat belong on the toolbar's second row (working
     * colonist professions), rather than the first (garrison units and
     * warehouse goods)?
     *
     * @return True if this is a second-row stat.
     */
    public boolean isSecondRow() {
        return this.secondRow;
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
    private static int countTileUnits(Colony colony, Predicate<Unit> filter) {
        int count = 0;
        for (Unit u : colony.getTile().getUnitList()) {
            if (filter.test(u)) count++;
        }
        return count;
    }

    /**
     * Count colonists of a given unit type present at the colony,
     * whether actually working (assigned to a building or tile, via
     * colony.getUnitList()) or just standing idle on the colony's tile
     * ("Outside Colony", via colony.getTile().getUnitList()).  A unit
     * is only ever in one of those two lists at a time, so this cannot
     * double-count.
     *
     * @param colony The {@code Colony} to check.
     * @param unitTypeId The expert unit type identifier.
     * @return The count.
     */
    private static int countWorkers(Colony colony, String unitTypeId) {
        int count = 0;
        for (Unit u : colony.getUnitList()) {
            if (unitTypeId.equals(u.getType().getId())) count++;
        }
        for (Unit u : colony.getTile().getUnitList()) {
            if (unitTypeId.equals(u.getType().getId())) count++;
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
