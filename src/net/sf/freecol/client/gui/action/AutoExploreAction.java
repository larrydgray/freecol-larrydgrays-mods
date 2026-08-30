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

package net.sf.freecol.client.gui.action;

import java.awt.event.ActionEvent;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.GameOptions;


/**
 * LarryDGray's Mods: an action to toggle the "Auto Explore" order on
 * the active unit - ships only for now, scouts are a planned
 * follow-up. Searches open water toward unexplored territory, then
 * hugs whatever boundary it runs into (coastline, the polar edge, or
 * the ocean/high seas line), disengaging automatically on contact
 * with another nation's unit or settlement.
 */
public class AutoExploreAction extends UnitAction {

    public static final String id = "autoExploreAction";


    /**
     * Creates this action.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public AutoExploreAction(FreeColClient freeColClient) {
        super(freeColClient, id);

        addImageIcons("autoExplore");
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        igc().toggleAutoExplore(getGUI().getActiveUnit());
    }


    // Override FreeColAction

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldBeEnabled() {
        final Unit unit = (getGUI() == null) ? null : getGUI().getActiveUnit();
        return super.shouldBeEnabled()
            && unit != null
            && unit.isNaval()
            && getFreeColClient().getGame().getSpecification()
                   .hasOption(GameOptions.ENABLE_AUTO_EXPLORE, BooleanOption.class)
            && getFreeColClient().getGame().getSpecification()
                   .getBoolean(GameOptions.ENABLE_AUTO_EXPLORE);
    }
}
