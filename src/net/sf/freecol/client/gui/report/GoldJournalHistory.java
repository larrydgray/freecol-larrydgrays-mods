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

import net.sf.freecol.common.model.GoldCategory;
import net.sf.freecol.common.model.GoldJournalSample;
import net.sf.freecol.common.model.Player;


/**
 * LarryDGray's Mods: client-side, session-scoped view of a player's
 * turn-by-turn Gold Journal report history. The actual computation
 * lives on {@link GoldJournalSample} (common model) and is sampled
 * server-side every turn - see {@code ServerPlayer.csNewTurn()} - so
 * this class is just a thin cache restored from the save/login data,
 * mirroring {@link TradeHistory} exactly. Unlike Colony
 * Growth/Nation Comparison/Trade History, there is no client-side
 * live-preview {@code recordTurn()} here: those samples describe
 * *current state* the client can independently recompute from its
 * own synced copy of the game, but a gold journal entry describes
 * *this turn's deltas*, which only ever get accumulated inside
 * {@code ServerPlayer.modifyGold()} on the server's authoritative
 * object - the client has no equivalent to replay. So this session's
 * most recent turns only appear after the next save/reload, same as
 * every report's history did before the client-side live-preview
 * mechanism existed.
 */
public class GoldJournalHistory {

    /** Cap retained samples per player, cheap memory insurance. */
    private static final int MAX_SAMPLES = 500;

    /**
     * One turn's worth of a player's gold journal, plus the turn
     * number it was recorded on. A thin, read-only wrapper around a
     * {@link GoldJournalSample}.
     */
    public static final class Sample {

        public final int turn;
        public final int goldIn;
        public final int goldOut;
        public final int balance;
        public final Map<GoldCategory, Integer> goldInByCategory;
        public final Map<GoldCategory, Integer> goldOutByCategory;

        Sample(GoldJournalSample gjs) {
            this.turn = gjs.getTurn();
            this.goldIn = gjs.getGoldIn();
            this.goldOut = gjs.getGoldOut();
            this.balance = gjs.getBalance();
            this.goldInByCategory = gjs.getGoldInByCategory();
            this.goldOutByCategory = gjs.getGoldOutByCategory();
        }
    }

    private final Map<String, List<Sample>> history = new HashMap<>();


    /**
     * LarryDGray's Mods: rebuild this player's in-memory history from
     * whatever the server has persisted and just sent down as part of
     * a fresh login, so a reloaded game continues the journal instead
     * of starting over.
     *
     * @param player The {@code Player} to restore history for.
     */
    public void restoreFrom(Player player) {
        List<Sample> samples = new ArrayList<>();
        for (GoldJournalSample persisted : player.getGoldJournalHistory()) {
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
