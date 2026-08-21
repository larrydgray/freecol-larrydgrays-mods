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

import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.util.CollectionUtils;


/**
 * LarryDGray's Mods: always-on letter badges shown under a colony's
 * name on the map indicating which key buildings are present --
 * Custom House, and the current tier of the schoolhouse, printing
 * press, church, and warehouse upgrade chains -- plus warehouse
 * status warnings (active overflow, a full storage slot).
 * Independent of the toggleable {@link ColonyStat} display.
 */
public final class ColonyBuildingBadges {

    private ColonyBuildingBadges() {}

    /**
     * Compute the badge text for a colony.
     *
     * @param colony The {@code Colony} to check.
     * @param showWarehouseWarnings Whether to include the warehouse
     *     waste/full warning symbols (independently toggleable from
     *     the rest of the badges).
     * @return The badge text, space-separated (possibly empty, never
     *     null).
     */
    public static String getBadges(Colony colony,
                                   boolean showWarehouseWarnings) {
        final Specification spec = colony.getSpecification();
        StringBuilder sb = new StringBuilder();

        if (hasBuilding(colony, spec, "model.building.customHouse")) {
            append(sb, "CH");
        }
        append(sb, currentTierCode(colony, spec, "model.building.schoolhouse"));
        append(sb, currentTierCode(colony, spec, "model.building.printingPress"));
        append(sb, currentTierCode(colony, spec, "model.building.chapel"));
        append(sb, currentTierCode(colony, spec, "model.building.depot"));
        if (showWarehouseWarnings) {
            if (colony.hasWastedGoods()) append(sb, "!");
            if (hasFullWarehouseSlot(colony)) append(sb, "+");
        }

        return sb.toString();
    }

    /**
     * Does this colony currently have any storable goods type sitting
     * at or above its warehouse capacity?
     *
     * @param colony The {@code Colony} to check.
     * @return True if some goods type is at a full warehouse slot.
     */
    private static boolean hasFullWarehouseSlot(Colony colony) {
        final int capacity = colony.getWarehouseCapacity();
        return CollectionUtils.any(colony.getCompactGoodsList(),
            (Goods g) -> g.isStorable() && !g.getType().limitIgnored()
                && g.getAmount() >= capacity);
    }

    private static void append(StringBuilder sb, String code) {
        if (code == null) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(code);
    }

    private static boolean hasBuilding(Colony colony, Specification spec,
                                       String buildingTypeId) {
        BuildingType type = spec.getBuildingType(buildingTypeId);
        return type != null && colony.getBuilding(type) != null;
    }

    /**
     * Get the letter code for the current tier of an upgrade chain
     * (schoolhouse or church) present in a colony, or null if the
     * colony has none built, or (for the church chain) only has the
     * default, un-upgraded first tier.
     *
     * @param colony The {@code Colony} to check.
     * @param spec The {@code Specification} to look up building types in.
     * @param firstLevelId The identifier of the chain's first level.
     * @return The letter code, or null.
     */
    private static String currentTierCode(Colony colony, Specification spec,
                                          String firstLevelId) {
        BuildingType type = spec.getBuildingType(firstLevelId);
        if (type == null) return null;
        Building building = colony.getBuilding(type);
        if (building == null) return null;
        switch (building.getType().getId()) {
        case "model.building.schoolhouse":  return "Sc";
        case "model.building.college":      return "Co";
        case "model.building.university":   return "Un";
        case "model.building.printingPress": return "Pp";
        case "model.building.newspaper":    return "Np";
        case "model.building.church":       return "Ch";
        case "model.building.cathedral":    return "Ca";
        case "model.building.warehouse":    return "Wh";
        case "model.building.warehouseExpansion": return "Wx";
        default: return null; // e.g. a bare chapel or the base Depot -- not shown
        }
    }
}
