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

import net.sf.freecol.common.i18n.Messages;


/**
 * LarryDGray's Mods: why a {@link Player}'s gold changed, for the
 * Gold Journal report. Every {@code Player.modifyGold(int, GoldCategory)}
 * call site in the codebase is tagged with exactly one of these -
 * see the plain {@code modifyGold(int)} overload (defaults to
 * {@link #OTHER}) for any call site not yet migrated to pass one
 * explicitly.
 */
public enum GoldCategory {
    TRADE_EUROPE,
    TRADE_NATIVE,
    TRADE_FOREIGN,
    CUSTOMS_HOUSE,
    UPKEEP,
    TREASURE,
    TRIBUTE,
    CHIEF_GIFT,
    MONARCH,
    PLUNDER,
    LOST_CITY_RUMOUR,
    LAND_CLAIM,
    RECRUITMENT,
    TRAINING,
    DIPLOMACY,
    DISASTER,
    ARREARS,
    INCITEMENT,
    OTHER;

    /**
     * Get the localized display name for this category, for the Gold
     * Journal report's per-turn note.
     *
     * @return The localized name.
     */
    public String getDisplayName() {
        return Messages.message("goldCategory." + name());
    }
}
