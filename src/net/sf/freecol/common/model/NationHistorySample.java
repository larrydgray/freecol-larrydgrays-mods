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

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * LarryDGray's Mods: one turn's worth of a single observed nation's
 * {@link NationSummary} stats, from the owning player's point of
 * view, persisted in the save file so the Nation Comparison report's
 * history survives a reload. Written/read as a child of the
 * observing {@code Player}, private to that player, mirroring the
 * client-side {@code client.gui.report.NationHistory.NationSample}
 * it is converted to/from.
 */
public class NationHistorySample extends FreeColObject {

    public static final String TAG = "nationHistorySample";

    private int turn;
    private String nationId;
    private int numberOfSettlements;
    private int numberOfUnits;
    private int militaryStrength;
    private int navalStrength;
    private int gold;
    private int soL;
    private int foundingFathers;
    private int tax;


    /**
     * Trivial constructor to allow creation with Game.newInstance.
     */
    public NationHistorySample() {
        setId("");
    }

    /**
     * Create a new sample from a freshly-fetched {@code NationSummary}.
     *
     * @param turn The turn number.
     * @param nationId The observed player's identifier.
     * @param ns The {@code NationSummary} to copy values from.
     */
    public NationHistorySample(int turn, String nationId, NationSummary ns) {
        this();
        this.turn = turn;
        this.nationId = nationId;
        this.numberOfSettlements = ns.getNumberOfSettlements();
        this.numberOfUnits = ns.getNumberOfUnits();
        this.militaryStrength = ns.getMilitaryStrength();
        this.navalStrength = ns.getNavalStrength();
        this.gold = ns.getGold();
        this.soL = ns.getSoL();
        this.foundingFathers = ns.getFoundingFathers();
        this.tax = ns.getTax();
    }

    /**
     * Create a new sample by reading a stream.
     *
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if there is a problem reading
     *     the stream.
     */
    public NationHistorySample(FreeColXMLReader xr) throws XMLStreamException {
        readFromXML(xr);
    }

    public final int getTurn() {
        return this.turn;
    }

    public final String getNationId() {
        return this.nationId;
    }

    public final int getNumberOfSettlements() {
        return this.numberOfSettlements;
    }

    public final int getNumberOfUnits() {
        return this.numberOfUnits;
    }

    public final int getMilitaryStrength() {
        return this.militaryStrength;
    }

    public final int getNavalStrength() {
        return this.navalStrength;
    }

    public final int getGold() {
        return this.gold;
    }

    public final int getSoL() {
        return this.soL;
    }

    public final int getFoundingFathers() {
        return this.foundingFathers;
    }

    public final int getTax() {
        return this.tax;
    }


    // Serialization

    private static final String FOUNDING_FATHERS_TAG = "foundingFathers";
    private static final String GOLD_TAG = "gold";
    private static final String MILITARY_STRENGTH_TAG = "militaryStrength";
    private static final String NATION_ID_TAG = "nationId";
    private static final String NAVAL_STRENGTH_TAG = "navalStrength";
    private static final String NUMBER_OF_SETTLEMENTS_TAG = "numberOfSettlements";
    private static final String NUMBER_OF_UNITS_TAG = "numberOfUnits";
    private static final String SOL_TAG = "SoL";
    private static final String TAX_TAG = "tax";
    private static final String TURN_TAG = "turn";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TURN_TAG, this.turn);
        xw.writeAttribute(NATION_ID_TAG, this.nationId);
        xw.writeAttribute(NUMBER_OF_SETTLEMENTS_TAG, this.numberOfSettlements);
        xw.writeAttribute(NUMBER_OF_UNITS_TAG, this.numberOfUnits);
        xw.writeAttribute(MILITARY_STRENGTH_TAG, this.militaryStrength);
        xw.writeAttribute(NAVAL_STRENGTH_TAG, this.navalStrength);
        xw.writeAttribute(GOLD_TAG, this.gold);
        xw.writeAttribute(SOL_TAG, this.soL);
        xw.writeAttribute(FOUNDING_FATHERS_TAG, this.foundingFathers);
        xw.writeAttribute(TAX_TAG, this.tax);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        this.turn = xr.getAttribute(TURN_TAG, 0);
        this.nationId = xr.getAttribute(NATION_ID_TAG, (String)null);
        this.numberOfSettlements = xr.getAttribute(NUMBER_OF_SETTLEMENTS_TAG, 0);
        this.numberOfUnits = xr.getAttribute(NUMBER_OF_UNITS_TAG, 0);
        this.militaryStrength = xr.getAttribute(MILITARY_STRENGTH_TAG, 0);
        this.navalStrength = xr.getAttribute(NAVAL_STRENGTH_TAG, 0);
        this.gold = xr.getAttribute(GOLD_TAG, 0);
        this.soL = xr.getAttribute(SOL_TAG, 0);
        this.foundingFathers = xr.getAttribute(FOUNDING_FATHERS_TAG, 0);
        this.tax = xr.getAttribute(TAX_TAG, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXMLTagName() { return TAG; }
}
