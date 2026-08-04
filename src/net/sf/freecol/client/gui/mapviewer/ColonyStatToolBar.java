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

package net.sf.freecol.client.gui.mapviewer;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;


/**
 * LarryDGray's Mods: a row of square toggle buttons, one per
 * {@link ColonyStat}, letting the player pick a single warehouse-goods
 * or unit-type count to display under every owned colony's name on the
 * map.  Mutually exclusive (including an explicit "off" button), like
 * a set of radio buttons.  Selection persists via
 * {@link ClientOptions#COLONY_STAT_DISPLAY}.
 */
public class ColonyStatToolBar extends JPanel {

    private final FreeColClient freeColClient;


    /**
     * Creates the colony stat tool bar.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ColonyStatToolBar(FreeColClient freeColClient) {
        this.freeColClient = freeColClient;

        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));
        setOpaque(true);

        final int current = freeColClient.getClientOptions()
            .getInteger(ClientOptions.COLONY_STAT_DISPLAY);
        final ButtonGroup group = new ButtonGroup();

        final JToggleButton offButton = new JToggleButton(
            Messages.message("colonyStatToolBar.off"));
        offButton.setToolTipText(Messages.message("colonyStatToolBar.off.shortDescription"));
        offButton.setSelected(current < 0);
        offButton.addActionListener((ActionEvent ae) -> select(-1));
        group.add(offButton);
        add(offButton);

        for (ColonyStat stat : ColonyStat.values()) {
            final int ordinal = stat.ordinal();
            final JToggleButton button = new JToggleButton(
                String.valueOf(stat.getLetter()));
            button.setToolTipText(Messages.message(
                "colonyStatToolBar." + stat.name().toLowerCase()
                    + ".shortDescription"));
            button.setSelected(current == ordinal);
            button.addActionListener((ActionEvent ae) -> select(ordinal));
            group.add(button);
            add(button);
        }

        setSize(getPreferredSize());
    }

    /**
     * Record the newly selected stat (or -1 for none) and repaint the
     * map so colony labels reflect the change immediately.
     *
     * @param ordinal The {@code ColonyStat} ordinal, or -1 for off.
     */
    private void select(int ordinal) {
        this.freeColClient.getClientOptions()
            .setInteger(ClientOptions.COLONY_STAT_DISPLAY, ordinal);
        this.freeColClient.getGUI().refresh();
    }
}
