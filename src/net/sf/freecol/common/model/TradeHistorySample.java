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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.CollectionUtils;


/**
 * LarryDGray's Mods: one turn's worth of a player's empire-wide goods
 * on-hand, gross production, and net production totals, persisted in
 * the save file so the Trade History report's history survives a
 * reload. Written/read as a child of the owning {@code Player},
 * private to that player. Computed here (common model) so both the
 * server's per-turn hook and the client's live report share identical
 * logic - mirrors {@link ColonyGrowthSample}'s shape exactly.
 */
public class TradeHistorySample extends FreeColObject {

    public static final String TAG = "tradeHistorySample";

    /** Separates entries within an encoded id-count map string. */
    private static final String ENTRY_SEPARATOR = ";";
    /** Separates an id from its count within one entry. */
    private static final String KV_SEPARATOR = ":";

    private int turn;
    private Map<String, Integer> goodsOnHand;
    private Map<String, Integer> goodsProduction;
    private Map<String, Integer> goodsNetProduction;
    private Map<String, Integer> goodsSales;
    private Map<String, Integer> goodsUnitsBought;
    private Map<String, Integer> goodsUnitsSold;
    private Map<String, Integer> goodsIncomeBeforeTaxes;
    private Map<String, Integer> goodsIncomeAfterTaxes;
    private Map<String, Integer> goodsUnitsInCargo;


    /**
     * Trivial constructor to allow creation with Game.newInstance.
     */
    public TradeHistorySample() {
        setId("");
    }

    /**
     * Compute a fresh sample of the given player's current
     * empire-wide goods totals. Shared by the server's per-turn hook
     * (the authoritative copy that actually gets saved) and the
     * client's own report for live display during a session.
     *
     * @param turn The current turn number.
     * @param player The {@code Player} to sample.
     */
    public TradeHistorySample(int turn, Player player) {
        this();
        this.turn = turn;
        List<Colony> colonies = player.getColonyList();

        // LarryDGray's Mods: units currently loaded on carriers, same
        // computation as ReportTradePanel's own cargoUnits total -
        // unlike the others below, this is a live snapshot (goes up
        // and down with shipping activity) rather than a running
        // empire-wide total.
        Map<String, Integer> unitsInCargo = new HashMap<>();
        player.getUnits().filter(Unit::isCarrier).forEach(unit -> {
            for (Goods goods : unit.getCompactGoodsList()) {
                unitsInCargo.merge(goods.getType().getId(), goods.getAmount(),
                    Integer::sum);
            }
        });
        this.goodsUnitsInCargo = unitsInCargo;

        Map<String, Integer> onHand = new HashMap<>();
        Map<String, Integer> production = new HashMap<>();
        Map<String, Integer> netProduction = new HashMap<>();
        Map<String, Integer> sales = new HashMap<>();
        Map<String, Integer> unitsBought = new HashMap<>();
        Map<String, Integer> unitsSold = new HashMap<>();
        Map<String, Integer> incomeBeforeTaxes = new HashMap<>();
        Map<String, Integer> incomeAfterTaxes = new HashMap<>();
        for (GoodsType gt : player.getSpecification().getStorableGoodsTypeList()) {
            onHand.put(gt.getId(),
                CollectionUtils.sum(colonies, c -> c.getGoodsCount(gt)));
            // LarryDGray's Mods: Food is never produced "as itself" -
            // farmers/fishermen produce grain/fish, which only
            // aggregates into food storage-side; a plain
            // getTotalProductionOf(food) always reads zero.
            // getFoodProduction() is FreeCol's own existing
            // workaround for this exact case.
            production.put(gt.getId(), (gt.isFoodType())
                ? CollectionUtils.sum(colonies, Colony::getFoodProduction)
                : CollectionUtils.sum(colonies, c -> c.getTotalProductionOf(gt)));
            netProduction.put(gt.getId(),
                CollectionUtils.sum(colonies, c -> c.getNetProductionOf(gt)));
            // LarryDGray's Mods: cumulative empire-wide totals since
            // the game started, not per-turn deltas - plotted over
            // time, the slope shows how fast trade is happening
            // rather than just the raw total. Sales nets buys
            // (negative) against sells (positive); units bought/sold
            // track each side separately, always non-negative.
            sales.put(gt.getId(), player.getSales(gt));
            unitsBought.put(gt.getId(), player.getUnitsBought(gt));
            unitsSold.put(gt.getId(), player.getUnitsSold(gt));
            incomeBeforeTaxes.put(gt.getId(), player.getIncomeBeforeTaxes(gt));
            incomeAfterTaxes.put(gt.getId(), player.getIncomeAfterTaxes(gt));
        }
        this.goodsOnHand = onHand;
        this.goodsProduction = production;
        this.goodsNetProduction = netProduction;
        this.goodsSales = sales;
        this.goodsUnitsBought = unitsBought;
        this.goodsUnitsSold = unitsSold;
        this.goodsIncomeBeforeTaxes = incomeBeforeTaxes;
        this.goodsIncomeAfterTaxes = incomeAfterTaxes;
    }

    /**
     * Create a new sample by reading a stream.
     *
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if there is a problem reading
     *     the stream.
     */
    public TradeHistorySample(FreeColXMLReader xr) throws XMLStreamException {
        readFromXML(xr);
    }

    public final int getTurn() {
        return this.turn;
    }

    public final Map<String, Integer> getGoodsOnHand() {
        return this.goodsOnHand;
    }

    public final Map<String, Integer> getGoodsProduction() {
        return this.goodsProduction;
    }

    public final Map<String, Integer> getGoodsNetProduction() {
        return this.goodsNetProduction;
    }

    public final Map<String, Integer> getGoodsSales() {
        return this.goodsSales;
    }

    public final Map<String, Integer> getGoodsUnitsBought() {
        return this.goodsUnitsBought;
    }

    public final Map<String, Integer> getGoodsUnitsSold() {
        return this.goodsUnitsSold;
    }

    public final Map<String, Integer> getGoodsIncomeBeforeTaxes() {
        return this.goodsIncomeBeforeTaxes;
    }

    public final Map<String, Integer> getGoodsIncomeAfterTaxes() {
        return this.goodsIncomeAfterTaxes;
    }

    public final Map<String, Integer> getGoodsUnitsInCargo() {
        return this.goodsUnitsInCargo;
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

    private static final String GOODS_ON_HAND_TAG = "goodsOnHand";
    private static final String GOODS_PRODUCTION_TAG = "goodsProduction";
    private static final String GOODS_NET_PRODUCTION_TAG = "goodsNetProduction";
    private static final String GOODS_SALES_TAG = "goodsSales";
    private static final String GOODS_UNITS_BOUGHT_TAG = "goodsUnitsBought";
    private static final String GOODS_UNITS_SOLD_TAG = "goodsUnitsSold";
    private static final String GOODS_INCOME_BEFORE_TAXES_TAG = "goodsIncomeBeforeTaxes";
    private static final String GOODS_INCOME_AFTER_TAXES_TAG = "goodsIncomeAfterTaxes";
    private static final String GOODS_UNITS_IN_CARGO_TAG = "goodsUnitsInCargo";
    private static final String TURN_TAG = "turn";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TURN_TAG, this.turn);
        xw.writeAttribute(GOODS_ON_HAND_TAG, encode(this.goodsOnHand));
        xw.writeAttribute(GOODS_PRODUCTION_TAG, encode(this.goodsProduction));
        xw.writeAttribute(GOODS_NET_PRODUCTION_TAG, encode(this.goodsNetProduction));
        xw.writeAttribute(GOODS_SALES_TAG, encode(this.goodsSales));
        xw.writeAttribute(GOODS_UNITS_BOUGHT_TAG, encode(this.goodsUnitsBought));
        xw.writeAttribute(GOODS_UNITS_SOLD_TAG, encode(this.goodsUnitsSold));
        xw.writeAttribute(GOODS_INCOME_BEFORE_TAXES_TAG, encode(this.goodsIncomeBeforeTaxes));
        xw.writeAttribute(GOODS_INCOME_AFTER_TAXES_TAG, encode(this.goodsIncomeAfterTaxes));
        xw.writeAttribute(GOODS_UNITS_IN_CARGO_TAG, encode(this.goodsUnitsInCargo));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        this.turn = xr.getAttribute(TURN_TAG, 0);
        this.goodsOnHand = decode(xr.getAttribute(GOODS_ON_HAND_TAG, (String)null));
        this.goodsProduction = decode(xr.getAttribute(GOODS_PRODUCTION_TAG, (String)null));
        this.goodsNetProduction = decode(xr.getAttribute(GOODS_NET_PRODUCTION_TAG, (String)null));
        this.goodsSales = decode(xr.getAttribute(GOODS_SALES_TAG, (String)null));
        this.goodsUnitsBought = decode(xr.getAttribute(GOODS_UNITS_BOUGHT_TAG, (String)null));
        this.goodsUnitsSold = decode(xr.getAttribute(GOODS_UNITS_SOLD_TAG, (String)null));
        this.goodsIncomeBeforeTaxes = decode(xr.getAttribute(GOODS_INCOME_BEFORE_TAXES_TAG, (String)null));
        this.goodsIncomeAfterTaxes = decode(xr.getAttribute(GOODS_INCOME_AFTER_TAXES_TAG, (String)null));
        this.goodsUnitsInCargo = decode(xr.getAttribute(GOODS_UNITS_IN_CARGO_TAG, (String)null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXMLTagName() { return TAG; }
}
