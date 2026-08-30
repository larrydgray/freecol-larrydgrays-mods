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

package net.sf.freecol.client.gui.dialog;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * LarryDGray's Mods: warns, on End Turn, that one or more owned
 * colonies are about to lose a colonist to starvation this turn -
 * mirrors {@link EndTurnDialog}'s shape exactly, listing colonies
 * instead of units still able to move.
 */
public final class EndTurnStarvationDialog extends FreeColConfirmDialog {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(EndTurnStarvationDialog.class.getName());

    /**
     * Wraps a Colony so the JList's keystroke navigation
     * (JList.getNextMatch, which reads toString()) matches on the
     * colony's real name rather than a debugging toString().
     */
    private static class ColonyWrapper {

        public final Colony colony;
        public final String name;


        public ColonyWrapper(Colony colony) {
            this.colony = colony;
            this.name = colony.getName();
        }


        // Override Object

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return name;
        }
    }

    private class ColonyCellRenderer implements ListCellRenderer<ColonyWrapper> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(JList<? extends ColonyWrapper> list,
                                                      ColonyWrapper value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            final JLabel nameLabel = new JLabel(value.name);
            nameLabel.setOpaque(isSelected);
            return nameLabel;
        }
    }


    /** The list of colonies to display. */
    private final JList<ColonyWrapper> colonyList;


    /**
     * The constructor to use.
     *
     * @param freeColClient The freecol client.
     * @param frame The owner frame.
     * @param colonies The colonies about to lose a colonist to
     *     starvation this turn.
     */
    public EndTurnStarvationDialog(FreeColClient freeColClient, JFrame frame,
                                   List<Colony> colonies) {
        super(freeColClient, frame);

        final Player player = getMyPlayer();

        JLabel header = Utility.localizedHeader(
            Messages.nameKey("endTurnStarvationDialog"), Utility.FONTSPEC_TITLE);
        JTextArea text = Utility.localizedTextArea(StringTemplate
            .template("endTurnStarvationDialog.areYouSure")
            .addAmount("%number%", colonies.size()));

        DefaultListModel<ColonyWrapper> model = new DefaultListModel<>();
        for (Colony colony : colonies) {
            model.addElement(new ColonyWrapper(colony));
        }

        this.colonyList = new JList<>(model);
        this.colonyList.setCellRenderer(new ColonyCellRenderer());
        this.colonyList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),
                                          "select");
        this.colonyList.getActionMap().put("select", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    selectColony();
                }
            });
        this.colonyList.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"),
                                          "quit");
        this.colonyList.getActionMap().put("quit", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    EndTurnStarvationDialog.this.setValue(options.get(1));
                }
            });
        this.colonyList.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (e.getValueIsAdjusting()) return;
                    selectColony();
                }
            });
        JScrollPane listScroller = new JScrollPane(this.colonyList);

        JPanel panel = new MigPanel(new MigLayout("wrap 1, fill",
                                                  "[align center]"));
        panel.add(header);
        panel.add(text, "newline 20");
        panel.add(listScroller, "newline 10");
        panel.setSize(panel.getPreferredSize());

        ImageIcon icon = new ImageIcon(
            getImageLibrary().getScaledNationImage(player.getNation()));
        initializeConfirmDialog(frame, false, panel, icon,
                                "endTurnStarvationDialog.endAnyway", "cancel");
    }

    /**
     * Open the currently selected colony's panel.
     */
    private void selectColony() {
        ColonyWrapper wrapper = this.colonyList.getSelectedValue();
        if (wrapper != null && wrapper.colony != null) {
            getGUI().showColonyPanel(wrapper.colony, null);
        }
    }
}
