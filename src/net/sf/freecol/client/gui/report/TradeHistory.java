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

package net.sf.freecol.client.gui.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TradeHistorySample;


/**
 * LarryDGray's Mods: client-side, session-scoped view of a player's
 * turn-by-turn Trade History report history - empire-wide goods
 * on-hand and net production totals. The actual computation lives on
 * {@link TradeHistorySample} (common model) so the server's per-turn
 * hook - the authoritative copy that gets saved - and this client-side
 * display share the exact same logic; this class is just a thin,
 * per-session cache wrapping that shared type. Mirrors
 * {@link ColonyGrowthHistory} exactly.
 */
public class TradeHistory {

    /** Cap retained samples per player, cheap memory insurance. */
    private static final int MAX_SAMPLES = 500;

    /**
     * One turn's worth of a player's empire-wide goods totals, plus
     * the turn number it was recorded on. A thin, read-only wrapper
     * around a {@link TradeHistorySample}.
     */
    public static final class Sample {

        public final int turn;
        public final Map<String, Integer> goodsOnHand;
        public final Map<String, Integer> goodsProduction;
        public final Map<String, Integer> goodsNetProduction;
        public final Map<String, Integer> goodsSales;
        public final Map<String, Integer> goodsUnitsBought;
        public final Map<String, Integer> goodsUnitsSold;
        public final Map<String, Integer> goodsIncomeBeforeTaxes;
        public final Map<String, Integer> goodsIncomeAfterTaxes;
        public final Map<String, Integer> goodsUnitsInCargo;

        Sample(TradeHistorySample ths) {
            this.turn = ths.getTurn();
            this.goodsOnHand = ths.getGoodsOnHand();
            this.goodsProduction = ths.getGoodsProduction();
            this.goodsNetProduction = ths.getGoodsNetProduction();
            this.goodsSales = ths.getGoodsSales();
            this.goodsUnitsBought = ths.getGoodsUnitsBought();
            this.goodsUnitsSold = ths.getGoodsUnitsSold();
            this.goodsIncomeBeforeTaxes = ths.getGoodsIncomeBeforeTaxes();
            this.goodsIncomeAfterTaxes = ths.getGoodsIncomeAfterTaxes();
            this.goodsUnitsInCargo = ths.getGoodsUnitsInCargo();
        }
    }

    private final Map<String, List<Sample>> history = new HashMap<>();


    /**
     * Sample the given player's current empire-wide goods totals for
     * live display, for the given turn. This is purely for this
     * session's chart - the server's own per-turn hook is what
     * actually persists the authoritative copy into the save file,
     * since the client and server keep separate {@code Player}
     * object graphs even in single-player.
     *
     * @param player The {@code Player} to sample.
     * @param turn The current turn number.
     */
    public void recordTurn(Player player, int turn) {
        Sample sample = new Sample(new TradeHistorySample(turn, player));
        List<Sample> samples = this.history
            .computeIfAbsent(player.getId(), k -> new ArrayList<>());
        samples.add(sample);
        if (samples.size() > MAX_SAMPLES) samples.remove(0);
    }

    /**
     * LarryDGray's Mods: rebuild this player's in-memory history from
     * whatever the server has persisted and just sent down as part of
     * a fresh login, so a reloaded game continues the timeline
     * instead of starting over.
     *
     * @param player The {@code Player} to restore history for.
     */
    public void restoreFrom(Player player) {
        List<Sample> samples = new ArrayList<>();
        for (TradeHistorySample persisted : player.getTradeHistory()) {
            samples.add(new Sample(persisted));
        }
        this.history.put(player.getId(), samples);
    }

    /**
     * Get the recorded history for a player.
     *
     * @param player The {@code Player} to get history for.
     * @return The list of samples, oldest first, possibly empty,
     *     never null.
     */
    public List<Sample> getHistory(Player player) {
        List<Sample> samples = this.history.get(player.getId());
        return (samples == null) ? new ArrayList<>() : samples;
    }

    /**
     * Forget all recorded history, e.g. when a different game is
     * connected.
     */
    public void clear() {
        this.history.clear();
    }
}
