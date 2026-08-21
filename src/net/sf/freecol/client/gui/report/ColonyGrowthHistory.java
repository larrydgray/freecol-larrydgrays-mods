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

import net.sf.freecol.common.model.ColonyGrowthSample;
import net.sf.freecol.common.model.Player;


/**
 * LarryDGray's Mods: client-side, session-scoped view of a player's
 * turn-by-turn Colony Growth report history for live display -
 * population, all colonists owned empire-wide, empire-wide Sons of
 * Liberty%, total accumulated liberty, number of settlements, and
 * military/wagon/goods counts. The actual computation and category
 * ids live on {@link ColonyGrowthSample} (common model) so the
 * server's per-turn hook - the authoritative copy that gets saved -
 * and this client-side display share the exact same logic; this
 * class is just a thin, per-session cache wrapping that shared type.
 */
public class ColonyGrowthHistory {

    /** Cap retained samples per player, cheap memory insurance. */
    private static final int MAX_SAMPLES = 500;

    /** Category id constants - see {@link ColonyGrowthSample} for
     *  what each means. Re-exported here so callers that already
     *  depend on this class do not also need to import the common
     *  model class just for these. */
    public static final String WAGON_TRAIN_ID = ColonyGrowthSample.WAGON_TRAIN_ID;
    public static final String PROFESSIONAL_SUFFIX = ColonyGrowthSample.PROFESSIONAL_SUFFIX;
    public static final String SCOUT_ROLE_ID = ColonyGrowthSample.SCOUT_ROLE_ID;
    public static final String SEASONED_SCOUT_ID = ColonyGrowthSample.SEASONED_SCOUT_ID;

    /**
     * One turn's worth of a player's empire-wide growth stats, plus
     * the turn number it was recorded on. A thin, read-only wrapper
     * around a {@link ColonyGrowthSample}.
     */
    public static final class Sample {

        public final int turn;
        public final int population;
        public final int citizens;
        public final int sonsOfLiberty;
        public final int liberty;
        public final int numberOfSettlements;
        public final Map<String, Integer> unitCounts;
        public final Map<String, Integer> goodsCounts;

        Sample(ColonyGrowthSample cgs) {
            this.turn = cgs.getTurn();
            this.population = cgs.getPopulation();
            this.citizens = cgs.getCitizens();
            this.sonsOfLiberty = cgs.getSonsOfLiberty();
            this.liberty = cgs.getLiberty();
            this.numberOfSettlements = cgs.getNumberOfSettlements();
            this.unitCounts = cgs.getUnitCounts();
            this.goodsCounts = cgs.getGoodsCounts();
        }
    }

    private final Map<String, List<Sample>> history = new HashMap<>();


    /**
     * Sample the given player's current empire-wide growth stats for
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
        Sample sample = new Sample(new ColonyGrowthSample(turn, player));
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
        for (ColonyGrowthSample persisted : player.getColonyGrowthHistory()) {
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
