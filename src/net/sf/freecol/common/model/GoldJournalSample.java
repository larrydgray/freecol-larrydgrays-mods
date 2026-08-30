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
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * LarryDGray's Mods: one turn's worth of a player's gold journal -
 * how much gold was gained/lost this turn (broken down by {@link
 * GoldCategory}), and the resulting balance, persisted in the save
 * file so the Gold Journal report's history survives a reload.
 * Written/read as a child of the owning {@code Player}, private to
 * that player. Every gold change funnels through {@link
 * Player#modifyGold(int, GoldCategory)}, which accumulates running
 * per-category in/out totals regardless of the reason (trade, upkeep,
 * treasure, tribute, etc.) - this sample just snapshots those totals
 * plus the current balance once a turn, and the accumulators are
 * reset for the next turn.
 */
public class GoldJournalSample extends FreeColObject {

    public static final String TAG = "goldJournalSample";

    /** Separates entries within an encoded category-amount map string. */
    private static final String ENTRY_SEPARATOR = ";";
    /** Separates a category from its amount within one entry. */
    private static final String KV_SEPARATOR = ":";

    private int turn;
    private int goldIn;
    private int goldOut;
    private int balance;
    private Map<GoldCategory, Integer> goldInByCategory;
    private Map<GoldCategory, Integer> goldOutByCategory;


    /**
     * Trivial constructor to allow creation with Game.newInstance.
     */
    public GoldJournalSample() {
        setId("");
    }

    /**
     * Compute a fresh sample from the given player's current
     * accumulated gold in/out totals and current balance. Called
     * once per turn from the server's per-turn hook, the
     * authoritative copy that actually gets saved.
     *
     * @param turn The current turn number.
     * @param player The {@code Player} to sample.
     */
    public GoldJournalSample(int turn, Player player) {
        this();
        this.turn = turn;
        this.goldIn = player.getGoldInThisTurn();
        this.goldOut = player.getGoldOutThisTurn();
        this.balance = player.getGold();
        this.goldInByCategory = new HashMap<>(player.getGoldInByCategoryThisTurn());
        this.goldOutByCategory = new HashMap<>(player.getGoldOutByCategoryThisTurn());
    }

    /**
     * Create a new sample by reading a stream.
     *
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if there is a problem reading
     *     the stream.
     */
    public GoldJournalSample(FreeColXMLReader xr) throws XMLStreamException {
        readFromXML(xr);
    }

    public final int getTurn() {
        return this.turn;
    }

    public final int getGoldIn() {
        return this.goldIn;
    }

    public final int getGoldOut() {
        return this.goldOut;
    }

    public final int getBalance() {
        return this.balance;
    }

    public final Map<GoldCategory, Integer> getGoldInByCategory() {
        return this.goldInByCategory;
    }

    public final Map<GoldCategory, Integer> getGoldOutByCategory() {
        return this.goldOutByCategory;
    }

    /**
     * Encode a category-to-amount map as a single string, since a
     * variable set of keys does not fit neatly into fixed XML
     * attributes.
     *
     * @param map The map to encode.
     * @return The encoded string.
     */
    private static String encode(Map<GoldCategory, Integer> map) {
        StringBuilder sb = new StringBuilder(64);
        for (Map.Entry<GoldCategory, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(ENTRY_SEPARATOR);
            sb.append(e.getKey().name()).append(KV_SEPARATOR).append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Decode a string produced by {@link #encode} back into a map.
     *
     * @param s The encoded string, possibly null or empty.
     * @return The decoded map, never null.
     */
    private static Map<GoldCategory, Integer> decode(String s) {
        Map<GoldCategory, Integer> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String entry : s.split(ENTRY_SEPARATOR)) {
            int i = entry.lastIndexOf(KV_SEPARATOR);
            if (i < 0) continue;
            try {
                GoldCategory category = GoldCategory.valueOf(entry.substring(0, i));
                map.put(category, Integer.parseInt(entry.substring(i + 1)));
            } catch (IllegalArgumentException iae) {
                // Ignore a malformed/unknown entry rather than fail the whole load.
            }
        }
        return map;
    }


    // Serialization

    private static final String BALANCE_TAG = "balance";
    private static final String GOLD_IN_TAG = "goldIn";
    private static final String GOLD_IN_BY_CATEGORY_TAG = "goldInByCategory";
    private static final String GOLD_OUT_TAG = "goldOut";
    private static final String GOLD_OUT_BY_CATEGORY_TAG = "goldOutByCategory";
    private static final String TURN_TAG = "turn";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TURN_TAG, this.turn);
        xw.writeAttribute(GOLD_IN_TAG, this.goldIn);
        xw.writeAttribute(GOLD_OUT_TAG, this.goldOut);
        xw.writeAttribute(BALANCE_TAG, this.balance);
        xw.writeAttribute(GOLD_IN_BY_CATEGORY_TAG, encode(this.goldInByCategory));
        xw.writeAttribute(GOLD_OUT_BY_CATEGORY_TAG, encode(this.goldOutByCategory));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        this.turn = xr.getAttribute(TURN_TAG, 0);
        this.goldIn = xr.getAttribute(GOLD_IN_TAG, 0);
        this.goldOut = xr.getAttribute(GOLD_OUT_TAG, 0);
        this.balance = xr.getAttribute(BALANCE_TAG, 0);
        this.goldInByCategory = decode(xr.getAttribute(GOLD_IN_BY_CATEGORY_TAG, (String)null));
        this.goldOutByCategory = decode(xr.getAttribute(GOLD_OUT_BY_CATEGORY_TAG, (String)null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXMLTagName() { return TAG; }
}
