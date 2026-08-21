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

import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.common.model.NationHistorySample;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;


/**
 * LarryDGray's Mods: client-side, session-scoped view of each live
 * European nation's turn-by-turn {@link NationSummary} stats, for
 * live display in the Nation Comparison report. The server's own
 * per-turn hook (see {@code ServerPlayer.csNewTurn()}) is what
 * actually persists the authoritative copy - keyed by
 * {@link NationHistorySample} - into the save file; this class is
 * just a thin, per-session cache for the chart. Owned by
 * {@code InGameController} and cleared whenever a game is
 * (re)connected.
 */
public class NationHistory {

    /** Cap retained samples per nation, cheap memory insurance. */
    private static final int MAX_SAMPLES = 500;

    /**
     * One turn's worth of a nation's summary stats, plus the turn
     * number it was recorded on.
     */
    public static final class NationSample {

        public final int turn;
        public final int numberOfSettlements;
        public final int numberOfUnits;
        public final int militaryStrength;
        public final int navalStrength;
        public final int gold;
        public final int soL;
        public final int foundingFathers;
        public final int tax;

        NationSample(int turn, NationSummary ns) {
            this.turn = turn;
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
         * LarryDGray's Mods: reconstruct a sample from its persisted
         * form, read back from the save file.
         *
         * @param persisted The {@code NationHistorySample} to copy.
         */
        NationSample(NationHistorySample persisted) {
            this.turn = persisted.getTurn();
            this.numberOfSettlements = persisted.getNumberOfSettlements();
            this.numberOfUnits = persisted.getNumberOfUnits();
            this.militaryStrength = persisted.getMilitaryStrength();
            this.navalStrength = persisted.getNavalStrength();
            this.gold = persisted.getGold();
            this.soL = persisted.getSoL();
            this.foundingFathers = persisted.getFoundingFathers();
            this.tax = persisted.getTax();
        }
    }

    private final Map<String, List<NationSample>> history = new HashMap<>();


    /**
     * Sample every live European nation's current NationSummary for
     * live display, for the given turn. This is purely for this
     * session's chart - the server's own per-turn hook is what
     * actually persists the authoritative copy into the save file,
     * since the client and server keep separate {@code Player}
     * object graphs even in single-player.
     *
     * @param igc The {@code InGameController} to fetch summaries through.
     * @param liveEuropeans The live European players to sample.
     * @param turn The current turn number.
     */
    public void recordTurn(InGameController igc, List<Player> liveEuropeans,
                           int turn) {
        for (Player player : liveEuropeans) {
            NationSummary ns = igc.nationSummary(player);
            if (ns == null) continue;
            List<NationSample> samples = this.history
                .computeIfAbsent(player.getId(), k -> new ArrayList<>());
            samples.add(new NationSample(turn, ns));
            if (samples.size() > MAX_SAMPLES) samples.remove(0);
        }
    }

    /**
     * LarryDGray's Mods: rebuild this observer's in-memory history
     * from whatever was persisted in the save file just loaded, so a
     * reloaded game continues the timeline instead of starting over.
     *
     * @param observer The observing {@code Player} to restore
     *     history for.
     * @param liveEuropeans The live European players to restore
     *     history for.
     */
    public void restoreFrom(Player observer, List<Player> liveEuropeans) {
        for (Player player : liveEuropeans) {
            List<NationSample> samples = new ArrayList<>();
            for (NationHistorySample persisted
                    : observer.getNationHistory(player.getId())) {
                samples.add(new NationSample(persisted));
            }
            this.history.put(player.getId(), samples);
        }
    }

    /**
     * Get the recorded history for a nation.
     *
     * @param player The {@code Player} to get history for.
     * @return The list of samples, oldest first, possibly empty,
     *     never null.
     */
    public List<NationSample> getHistory(Player player) {
        List<NationSample> samples = this.history.get(player.getId());
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
