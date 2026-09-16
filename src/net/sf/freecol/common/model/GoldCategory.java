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
    SHIP_ARTILLERY_PURCHASE,
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

    /**
     * LarryDGray's Mods: a short 2-3 letter designator for this
     * category, for labelling a pie slice too small to fit the full
     * display name. Hand-picked (not derived from the display name)
     * so near-homonyms like Treasure/Tribute or Land Claim/Lost City
     * Rumour never collide.
     *
     * @return The abbreviation.
     */
    public String getAbbreviation() {
        switch (this) {
            case TRADE_EUROPE: return "EU";
            case TRADE_NATIVE: return "NAT";
            case TRADE_FOREIGN: return "FOR";
            case CUSTOMS_HOUSE: return "CH";
            case UPKEEP: return "UPK";
            case TREASURE: return "TRE";
            case TRIBUTE: return "TRI";
            case CHIEF_GIFT: return "CG";
            case MONARCH: return "CR";
            case PLUNDER: return "PLU";
            case LOST_CITY_RUMOUR: return "LCR";
            case LAND_CLAIM: return "LND";
            case RECRUITMENT: return "REC";
            case TRAINING: return "TRN";
            case SHIP_ARTILLERY_PURCHASE: return "S&A";
            case DIPLOMACY: return "DIP";
            case DISASTER: return "DIS";
            case ARREARS: return "ARR";
            case INCITEMENT: return "INC";
            default: return "OTH";
        }
    }
}
