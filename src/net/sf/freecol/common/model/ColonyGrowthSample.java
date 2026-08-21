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

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.CollectionUtils;


/**
 * LarryDGray's Mods: one turn's worth of a player's empire-wide
 * growth stats, persisted in the save file so the Colony Growth
 * report's history survives a reload. Written/read as a child of the
 * owning {@code Player}, private to that player (never sent to
 * anyone else). Computed here (common model, so both the server's
 * per-turn hook and the client's live report can share the exact same
 * categorization logic) - the client-side
 * {@code client.gui.report.ColonyGrowthHistory.Sample} wraps one of
 * these rather than recomputing anything itself.
 */
public class ColonyGrowthSample extends FreeColObject {

    public static final String TAG = "colonyGrowthSample";

    /** Separates entries within an encoded id-count map string. */
    private static final String ENTRY_SEPARATOR = ";";
    /** Separates an id from its count within one entry. */
    private static final String KV_SEPARATOR = ":";

    /** Wagon trains have zero offence, so they need a special case
     *  alongside the offence-based military detection below. Public
     *  so the report panel can group wagon trains in with ships
     *  rather than land military units. */
    public static final String WAGON_TRAIN_ID = "model.unit.wagonTrain";

    /** Appended to a role id to key the count of units of that role's
     *  {@code expert-unit} type specifically (e.g. Veteran Soldiers
     *  equipped as soldiers). The plain role id counts every OTHER
     *  unit of that role instead (a "Regular" count) - the two are
     *  disjoint and add up to the role's true total, not an
     *  inclusive-total/subset pair. Uses '#' since real specification
     *  ids never contain one. */
    public static final String PROFESSIONAL_SUFFIX = "#professional";

    /** Scouts are trivial in number (usually one or two) so they are
     *  tracked for the current-turn bar chart only, never the trend
     *  line - see {@code ReportColonyGrowthPanel}. Public so the
     *  panel can recognize and special-case them. */
    public static final String SCOUT_ROLE_ID = "model.role.scout";

    /** A Seasoned Scout counts here regardless of whether it is
     *  currently equipped with the scout role - unlike the
     *  professional-variant counts above, which require the role to
     *  be active. Public for the same reason as {@link #SCOUT_ROLE_ID}. */
    public static final String SEASONED_SCOUT_ID = "model.unit.seasonedScout";

    private int turn;
    private int population;
    private int citizens;
    private int sonsOfLiberty;
    private int liberty;
    private int numberOfSettlements;
    private Map<String, Integer> unitCounts;
    private Map<String, Integer> goodsCounts;


    /**
     * Trivial constructor to allow creation with Game.newInstance.
     */
    public ColonyGrowthSample() {
        setId("");
    }

    /**
     * Create a new sample from freshly-computed values.
     *
     * @param turn The turn number.
     * @param population The population value.
     * @param citizens The citizens value.
     * @param sonsOfLiberty The Sons of Liberty% value.
     * @param liberty The liberty value.
     * @param numberOfSettlements The settlement count.
     * @param unitCounts The military/wagon category counts.
     * @param goodsCounts The military goods totals.
     */
    public ColonyGrowthSample(int turn, int population, int citizens,
                              int sonsOfLiberty, int liberty,
                              int numberOfSettlements,
                              Map<String, Integer> unitCounts,
                              Map<String, Integer> goodsCounts) {
        this();
        this.turn = turn;
        this.population = population;
        this.citizens = citizens;
        this.sonsOfLiberty = sonsOfLiberty;
        this.liberty = liberty;
        this.numberOfSettlements = numberOfSettlements;
        this.unitCounts = new HashMap<>(unitCounts);
        this.goodsCounts = new HashMap<>(goodsCounts);
    }

    /**
     * Compute a fresh sample of the given player's current
     * empire-wide growth stats. Shared by the server's per-turn hook
     * (the authoritative copy that actually gets saved) and the
     * client's own report for live display during a session.
     *
     * @param turn The current turn number.
     * @param player The {@code Player} to sample.
     */
    public ColonyGrowthSample(int turn, Player player) {
        this();
        this.turn = turn;
        List<Colony> colonies = player.getColonyList();
        this.population = CollectionUtils.sum(colonies, Colony::getUnitCount);
        this.sonsOfLiberty = player.getSoL();
        this.liberty = CollectionUtils.sum(colonies, Colony::getLiberty);
        this.numberOfSettlements = colonies.size();

        int citizenCount = 0;
        Map<String, Integer> counts = new HashMap<>();
        for (Unit unit : player.getUnitSet()) {
            if (unit.isColonist()) citizenCount++;
            addUnitCategories(unit, counts);
        }
        this.citizens = citizenCount;
        this.unitCounts = counts;

        Map<String, Integer> goods = new HashMap<>();
        for (GoodsType gt : player.getSpecification().getGoodsTypeList()) {
            if (!gt.getMilitary()) continue;
            goods.put(gt.getId(),
                CollectionUtils.sum(colonies, c -> c.getGoodsCount(gt)));
        }
        this.goodsCounts = goods;
    }

    /**
     * Add the given unit to every military/wagon category it counts
     * under. A unit made offensive by a role (soldier, dragoon...)
     * counts under that role's plain id (a "Regular" count) or, when
     * its base type matches the role's {@code expert-unit}, under
     * that role's {@link #PROFESSIONAL_SUFFIX} id instead - the two
     * are disjoint and add up to the role's true total.
     *
     * @param unit The {@code Unit} to categorize.
     * @param counts The category-id-to-count map to update.
     */
    private static void addUnitCategories(Unit unit, Map<String, Integer> counts) {
        UnitType type = unit.getType();
        Role unitRole = unit.getRole();

        // LarryDGray's Mods: scouts are tracked independently of the
        // categorization below, since a Seasoned Scout should still
        // count as one even when not currently mounted/equipped as a
        // scout, and a mounted scout of any base type should still
        // count as a scout. The scout role carries a token +1 offence
        // modifier (self-defense while scouting, not real combat
        // capability), so it must be excluded from the generic
        // offensive-role branch below - otherwise a mounted scout
        // gets double-counted there too, under a spurious "Regular
        // Scout"/"Professional Scout" pair.
        boolean isScoutRole = SCOUT_ROLE_ID.equals(unitRole.getId());
        if (isScoutRole) {
            counts.merge(SCOUT_ROLE_ID, 1, Integer::sum);
        }
        if (SEASONED_SCOUT_ID.equals(type.getId())) {
            counts.merge(SEASONED_SCOUT_ID, 1, Integer::sum);
        }

        if (WAGON_TRAIN_ID.equals(type.getId())) {
            counts.merge(WAGON_TRAIN_ID, 1, Integer::sum);
            return;
        }
        if (type.isNaval()) {
            // LarryDGray's Mods: every ship type belongs on the Ships
            // & Wagons chart, not just combat-capable ones (a
            // Caravel/Merchantman/Galleon has zero offence but is
            // still a ship worth tracking).
            counts.merge(type.getId(), 1, Integer::sum);
            return;
        }
        if (!isScoutRole && unitRole.isOffensive()) {
            // LarryDGray's Mods: "Regular" and "Professional" are
            // disjoint counts that add up to the role's total, not
            // an inclusive-total/subset pair - a unit is one or the
            // other, never both.
            String roleId = unitRole.getId();
            UnitType expert = unitRole.getExpertUnit();
            boolean isProfessional = (expert != null && expert == type);
            counts.merge(isProfessional ? roleId + PROFESSIONAL_SUFFIX : roleId,
                1, Integer::sum);
            return;
        }
        if (type.getOffence() > 0) {
            counts.merge(type.getId(), 1, Integer::sum);
        }
    }

    /**
     * Create a new sample by reading a stream.
     *
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if there is a problem reading
     *     the stream.
     */
    public ColonyGrowthSample(FreeColXMLReader xr) throws XMLStreamException {
        readFromXML(xr);
    }

    public final int getTurn() {
        return this.turn;
    }

    public final int getPopulation() {
        return this.population;
    }

    public final int getCitizens() {
        return this.citizens;
    }

    public final int getSonsOfLiberty() {
        return this.sonsOfLiberty;
    }

    public final int getLiberty() {
        return this.liberty;
    }

    public final int getNumberOfSettlements() {
        return this.numberOfSettlements;
    }

    public final Map<String, Integer> getUnitCounts() {
        return this.unitCounts;
    }

    public final Map<String, Integer> getGoodsCounts() {
        return this.goodsCounts;
    }

    /**
     * Encode an id-to-count map as a single string, since a variable
     * set of keys does not fit neatly into fixed XML attributes.
     * Safe as a flat delimited string because specification ids never
     * contain either separator character.
     *
     * @param map The map to encode.
     * @return The encoded string.
     */
    private static String encode(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder(64);
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(ENTRY_SEPARATOR);
            sb.append(e.getKey()).append(KV_SEPARATOR).append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Decode a string produced by {@link #encode} back into a map.
     *
     * @param s The encoded string, possibly null or empty.
     * @return The decoded map, never null.
     */
    private static Map<String, Integer> decode(String s) {
        Map<String, Integer> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String entry : s.split(ENTRY_SEPARATOR)) {
            int i = entry.lastIndexOf(KV_SEPARATOR);
            if (i < 0) continue;
            try {
                map.put(entry.substring(0, i),
                    Integer.parseInt(entry.substring(i + 1)));
            } catch (NumberFormatException nfe) {
                // Ignore a malformed entry rather than fail the whole load.
            }
        }
        return map;
    }


    // Serialization

    private static final String CITIZENS_TAG = "citizens";
    private static final String GOODS_COUNTS_TAG = "goodsCounts";
    private static final String LIBERTY_TAG = "liberty";
    private static final String NUMBER_OF_SETTLEMENTS_TAG = "numberOfSettlements";
    private static final String POPULATION_TAG = "population";
    private static final String SONS_OF_LIBERTY_TAG = "sonsOfLiberty";
    private static final String TURN_TAG = "turn";
    private static final String UNIT_COUNTS_TAG = "unitCounts";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TURN_TAG, this.turn);
        xw.writeAttribute(POPULATION_TAG, this.population);
        xw.writeAttribute(CITIZENS_TAG, this.citizens);
        xw.writeAttribute(SONS_OF_LIBERTY_TAG, this.sonsOfLiberty);
        xw.writeAttribute(LIBERTY_TAG, this.liberty);
        xw.writeAttribute(NUMBER_OF_SETTLEMENTS_TAG, this.numberOfSettlements);
        xw.writeAttribute(UNIT_COUNTS_TAG, encode(this.unitCounts));
        xw.writeAttribute(GOODS_COUNTS_TAG, encode(this.goodsCounts));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        this.turn = xr.getAttribute(TURN_TAG, 0);
        this.population = xr.getAttribute(POPULATION_TAG, 0);
        this.citizens = xr.getAttribute(CITIZENS_TAG, 0);
        this.sonsOfLiberty = xr.getAttribute(SONS_OF_LIBERTY_TAG, 0);
        this.liberty = xr.getAttribute(LIBERTY_TAG, 0);
        this.numberOfSettlements = xr.getAttribute(NUMBER_OF_SETTLEMENTS_TAG, 0);
        this.unitCounts = decode(xr.getAttribute(UNIT_COUNTS_TAG, (String)null));
        this.goodsCounts = decode(xr.getAttribute(GOODS_COUNTS_TAG, (String)null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXMLTagName() { return TAG; }
}
