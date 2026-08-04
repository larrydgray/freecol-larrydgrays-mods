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

package net.sf.freecol.client.gui.panel.report;

import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Nameable;
import net.sf.freecol.common.model.Named;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;


/**
 * This panel displays the Turn Report.
 */
public final class ReportTurnPanel extends ReportPanel {

    private static final Logger logger = Logger.getLogger(ReportTurnPanel.class.getName());

    /** Map message identifiers to label. */
    private final Hashtable<String, List<JComponent>> labelsByMessage
        = new Hashtable<>();
    /** Map message identifiers to text pane. */
    private final Hashtable<String, List<JComponent>> textPanesByMessage
        = new Hashtable<>();
    /**
     * All the components making up a message's row, keyed by message
     * object identity.
     *
     * Note: ModelMessage.getId() returns the *shared* i18n template key
     * (e.g. "model.building.notProducing"), not a unique per-instance
     * id -- many distinct messages in the same report legitimately share
     * one.  A String-keyed map here would silently alias unrelated rows
     * together, so this is keyed by object identity instead.
     */
    private final IdentityHashMap<ModelMessage, List<JComponent>> rowComponentsByMessage
        = new IdentityHashMap<>();
    /** The messages to display. */
    private final List<ModelMessage> messages = new ArrayList<>();

    /**
     * Messages the player has deprioritized (greyed out) this turn.  Not
     * persisted -- reset whenever a new turn's messages are loaded via
     * setMessages().  Identity-keyed, see rowComponentsByMessage.
     */
    private final Set<ModelMessage> greyedMessages
        = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Messages the player has dismissed (struck through and hidden) this
     * turn.  Not persisted -- reset whenever a new turn's messages are
     * loaded via setMessages().  Identity-keyed, see
     * rowComponentsByMessage.
     */
    private final Set<ModelMessage> struckMessages
        = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Whether dismissed messages are currently shown (undo mode). */
    private boolean showDismissed = false;


    /**
     * Creates the turn report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param messages The {@code ModelMessages} to display in the report.
     */
    public ReportTurnPanel(FreeColClient freeColClient,
                           List<ModelMessage> messages) {
        super(freeColClient, "reportTurnAction");

        // Layout is (re)configured in displayMessages(), which depends
        // on the "Enhanced Turn Report" option (LarryDGray's Mods) and
        // so needs to be able to react if that option changes between
        // turns.
        setMessages(messages);
    }


    /**
     * Set the messages being displayed by this report.
     *
     * @param messages The {@code ModelMessages} to display in the report.
     */
    public void setMessages(List<ModelMessage> messages) {
        reportPanel.removeAll();
        this.messages.clear();
        rowComponentsByMessage.clear();
        greyedMessages.clear();
        struckMessages.clear();
        showDismissed = false;
        if (messages != null) this.messages.addAll(messages);
        displayMessages();
    }
        
    /**
     * Does this message look urgent enough to float to the top of the
     * report, ahead of the normal type/source grouping?
     *
     * @param message The {@code ModelMessage} to check.
     * @return True if the rendered message text mentions starvation.
     */
    private boolean isUrgent(ModelMessage message) {
        return Messages.message(message).toLowerCase(Locale.ROOT)
            .contains("starving");
    }

    /**
     * Add or remove a strikethrough style over all the text in a
     * message's text pane, without disturbing its other styling
     * (colours, embedded link buttons, etc).
     *
     * @param textPane The {@code JTextPane} to restyle.
     * @param strike True to strike the text through.
     */
    private void setStrikeThrough(JTextPane textPane, boolean strike) {
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setStrikeThrough(attr, strike);
        doc.setCharacterAttributes(0, doc.getLength(), attr, false);
    }

    private void displayMessages() {
        final Game game = getFreeColClient().getGame();
        final ClientOptions co = getClientOptions();
        final int groupBy = co.getInteger(ClientOptions.MESSAGES_GROUP_BY);

        // LarryDGray's Mods: Enhanced Turn Report.  Off leaves the
        // report exactly as upstream renders it -- no checkboxes, no
        // starving-first sort, classic 4-column layout.
        final boolean enhanced = co.getBoolean("model.option.enhancedTurnReport");
        reportPanel.setLayout((enhanced)
            // hidemode 3 so a hidden (dismissed) row collapses
            // completely instead of leaving a blank gap.
            ? new MigLayout("wrap 6, hidemode 3",
                "[center][550!]:push[][][][]", "")
            : new MigLayout("wrap 4", "[center][550!]:push[][]", ""));

        // Pull urgent (starving) messages to the top, ahead of the
        // normal group-by-type/source sort, which still applies to the
        // remainder.
        final List<ModelMessage> urgent = new ArrayList<>();
        final List<ModelMessage> rest = new ArrayList<>();
        if (enhanced) {
            for (ModelMessage m : this.messages) {
                (isUrgent(m) ? urgent : rest).add(m);
            }
        } else {
            rest.addAll(this.messages);
        }
        final Comparator<ModelMessage> comparator
            = co.getModelMessageComparator(game);
        if (comparator != null) rest.sort(comparator);
        this.messages.clear();
        this.messages.addAll(urgent);
        this.messages.addAll(rest);

        if (enhanced) {
            // Master toggle to reveal dismissed (struck-through)
            // messages again, so a dismissal can always be undone.
            JCheckBox showDismissedBox = new JCheckBox(
                Messages.message("report.turn.showDismissed"));
            Utility.localizeToolTip(showDismissedBox, "report.turn.showDismissed");
            showDismissedBox.setSelected(showDismissed);
            showDismissedBox.addActionListener((ActionEvent ae) -> {
                    showDismissed = showDismissedBox.isSelected();
                    for (ModelMessage sm : struckMessages) {
                        List<JComponent> row = rowComponentsByMessage.get(sm);
                        if (row == null) continue;
                        for (JComponent jc : row) jc.setVisible(showDismissed);
                    }
                    reportPanel.revalidate();
                    reportPanel.repaint();
                });
            reportPanel.add(showDismissedBox, "newline, span, wrap");

            if (!urgent.isEmpty()) {
                JLabel headline = Utility.localizedHeaderLabel(
                    StringTemplate.template("report.turn.urgent"),
                    SwingConstants.LEADING, Utility.FONTSPEC_SUBTITLE);
                reportPanel.add(headline, "newline 20, skip, span");
            }
        }

        Object source = this;
        ModelMessage.MessageType type = null;
        int index = 0;
        // this.messages is exactly urgent followed by rest (see above),
        // so a plain position check tells us which section we are in --
        // avoids relying on ModelMessage.equals(), which is content-based
        // and can consider distinct messages "equal".
        for (ModelMessage message : this.messages) {
            boolean inUrgentSection = index < urgent.size();
            if (index == urgent.size()) {
                // Entered the regular section; reset the grouping
                // trackers so its first message gets its own headline
                // rather than being suppressed by whatever the last
                // urgent message happened to share a type/source with.
                source = this;
                type = null;
            }
            index++;
            // Add headline if the grouping changed (skipped inside the
            // urgent section, which has its own single headline above).
            if (!inUrgentSection) {
                switch (groupBy) {
                case ClientOptions.MESSAGES_GROUP_BY_SOURCE:
                    FreeColGameObject messageSource = game.getMessageSource(message);
                    if (messageSource != source) {
                        source = messageSource;
                        reportPanel.add(getHeadline(messageSource), "newline 20, skip");
                    }
                    break;
                case ClientOptions.MESSAGES_GROUP_BY_TYPE:
                    if (message.getMessageType() != type) {
                        type = message.getMessageType();
                        JLabel headline = Utility.localizedHeaderLabel(type,
                            Utility.FONTSPEC_SUBTITLE);
                        reportPanel.add(headline, "newline 20, skip, span");
                    }
                    break;
                default:
                    break;
                }
            }

            final List<JComponent> rowComponents = new ArrayList<>();

            JComponent component = new JLabel();
            FreeColObject messageDisplay = game.getMessageDisplay(message);
            final ImageLibrary lib = getImageLibrary();
            if (messageDisplay != null) {
                Image image = lib.getObjectImage(messageDisplay);
                ImageIcon icon = (image == null) ? null : new ImageIcon(image);
                if (messageDisplay instanceof Colony
                    || messageDisplay instanceof Europe) {
                    JButton button = Utility.getLinkButton(null, icon,
                        messageDisplay.getId());
                    button.addActionListener(this);
                    component = button;
                } else if (messageDisplay instanceof Unit) {
                    JButton button = Utility.getLinkButton(null, icon,
                        ((Unit)messageDisplay).up().getId());
                    button.addActionListener(this);
                    component = button;
                } else { // includes Player
                    component = new JLabel(icon);
                }
            }

            reportPanel.add(component, "newline");
            rowComponents.add(component);

            final JTextPane textPane = Utility.getDefaultTextPane();
            try {
                insertMessage(textPane.getStyledDocument(), message,
                              getMyPlayer());
            } catch (BadLocationException ble) {
                logger.log(Level.WARNING, "message insert fail", ble);
            }
            reportPanel.add(textPane);
            rowComponents.add(textPane);

            final JComponent label = component;

            // Ignore button (or a blank placeholder to keep the column
            // grid regular across every row).
            JComponent ignoreComponent;
            switch (message.getMessageType()) {
            case WAREHOUSE_CAPACITY: {
                JButton ignoreButton = new JButton("x");
                Utility.localizeToolTip(ignoreButton,
                    StringTemplate.copy("report.turn.ignore", message));
                final ModelMessage m = message;
                ignoreButton.addActionListener((ActionEvent ae) -> {
                        boolean flag = label.isEnabled();
                        igc().ignoreMessage(m, flag);
                        textPane.setEnabled(!flag);
                        label.setEnabled(!flag);
                    });
                ignoreComponent = ignoreButton;
                break;
            }
            default:
                ignoreComponent = new JLabel();
                break;
            }
            reportPanel.add(ignoreComponent);
            rowComponents.add(ignoreComponent);

            // Fill the message maps so that we can iterate through
            // them by message identifier in the ActionListeners.
            String id = message.getId();
            List<JComponent> components;
            if ((components = textPanesByMessage.get(id)) == null)
                textPanesByMessage.put(id,
                    components = new ArrayList<JComponent>());
            components.add(textPane);

            if ((components = labelsByMessage.get(id)) == null)
                labelsByMessage.put(id,
                    components = new ArrayList<JComponent>());
            components.add(label);

            // Filter button (or a blank placeholder), if an option is
            // present for this message's type.
            final String msgKey = message.getOptionName();
            JComponent filterComponent;
            if (co.hasOption(msgKey, BooleanOption.class)) {
                JButton filterButton = new JButton("X");
                Utility.localizeToolTip(filterButton, StringTemplate
                    .template("report.turn.filter")
                    .addNamed("%type%", message.getMessageType()));
                final ModelMessage mess = message;
                filterButton.addActionListener((ActionEvent ae) -> {
                        boolean flag = co.getBoolean(msgKey);
                        co.setBoolean(msgKey, !flag);
                        for (ModelMessage m : messages) {
                            if (m.getMessageType() != mess.getMessageType()) continue;
                            for (JComponent jc : textPanesByMessage.get(m.getId())) {
                                jc.setEnabled(!flag);
                            }
                            for (JComponent jc : labelsByMessage.get(m.getId())) {
                                jc.setEnabled(!flag);
                            }
                        }
                    });
                filterComponent = filterButton;
            } else {
                filterComponent = new JLabel();
            }
            reportPanel.add(filterComponent, (enhanced) ? "" : "wrap");
            rowComponents.add(filterComponent);

            if (enhanced) {
                // Deprioritize (grey out) checkbox.
                JCheckBox greyBox = new JCheckBox();
                Utility.localizeToolTip(greyBox, "report.turn.deprioritize");
                greyBox.setSelected(greyedMessages.contains(message));
                greyBox.addActionListener((ActionEvent ae) -> {
                        boolean flag = greyBox.isSelected();
                        if (flag) greyedMessages.add(message);
                        else greyedMessages.remove(message);
                        label.setEnabled(!flag);
                        textPane.setEnabled(!flag);
                    });
                reportPanel.add(greyBox);
                rowComponents.add(greyBox);

                // Dismiss (strike through, hide) checkbox.
                JCheckBox strikeBox = new JCheckBox();
                Utility.localizeToolTip(strikeBox, "report.turn.dismiss");
                strikeBox.setSelected(struckMessages.contains(message));
                strikeBox.addActionListener((ActionEvent ae) -> {
                        boolean flag = strikeBox.isSelected();
                        setStrikeThrough(textPane, flag);
                        if (flag) struckMessages.add(message);
                        else struckMessages.remove(message);
                        if (!showDismissed) {
                            List<JComponent> row = rowComponentsByMessage.get(message);
                            for (JComponent jc : row) jc.setVisible(!flag);
                            reportPanel.revalidate();
                            reportPanel.repaint();
                        }
                    });
                reportPanel.add(strikeBox, "wrap");
                rowComponents.add(strikeBox);
            }

            rowComponentsByMessage.put(message, rowComponents);
        }
    }

    private JComponent getHeadline(FreeColGameObject source) {
        String text;
        String commandId = null;
        if (source == null) {
            text = "";
        } else if (source instanceof Player) {
            Player player = (Player) source;
            StringTemplate template = StringTemplate
                .template("report.turn.playerNation")
                .addName("%player%", player.getName())
                .addStringTemplate("%nation%", player.getNationLabel());
            text = Messages.message(template);
        } else if (source instanceof Europe) {
            Europe europe = (Europe) source;
            text = Messages.getName(europe);
            commandId = europe.getId();
        } else if (source instanceof Market) {
            Market market = (Market) source;
            StringTemplate template = market.getOwner().getMarketName();
            text = Messages.message(template);
            Europe europe = getMyPlayer().getEurope();
            commandId = (europe != null) ? europe.getId() : null;
        } else if (source instanceof Colony) {
            final Colony colony = (Colony) source;
            text = colony.getName();
            commandId = colony.getId();
        } else if (source instanceof Building) {
            final Colony colony = ((Building)source).getColony();
            text = colony.getName();
            commandId = colony.getId();
        } else if (source instanceof Unit) {
            final Unit unit = (Unit) source;
            text = unit.getDescription(Unit.UnitLabelType.NATIONAL);
            commandId = unit.getLocation().getId();
        } else if (source instanceof Tile) {
            final Tile tile = (Tile) source;
            StringTemplate template = tile.getLocationLabelFor(getMyPlayer());
            text = Messages.message(template);
            commandId = tile.getId();
        } else if (source instanceof Named) {
            text = Messages.message(((Named)source).getNameKey());
        } else if (source instanceof Nameable) {
            text = ((Nameable)source).getName();
        } else {
            text = source.toString();
        }

        Font font = FontLibrary.getScaledFont(Utility.FONTSPEC_SUBTITLE, text);
        JComponent headline;
        if (commandId != null) {
            JButton button = new JButton(text);
            button.addActionListener(this);
            button.setActionCommand(commandId);
            headline = button;
            headline.setForeground(Utility.getLinkColor());
        } else {
            headline = new JLabel(text);
        }
        headline.setFont(font);
        headline.setOpaque(false);
        headline.setBorder(Utility.blankBorder(5, 0, 0, 0));
        return headline;
    }

    private void insertMessage(StyledDocument document, ModelMessage message,
                               Player player) throws BadLocationException {
        for (Object o : message.splitLinks(player)) {
            if (o instanceof String) {
                document.insertString(document.getLength(), (String)o,
                                      document.getStyle("regular"));
            } else if (o instanceof JButton) {
                JButton b = (JButton)o;
                b.addActionListener(this);
                StyleConstants.setComponent(document.getStyle("button"), b);
                document.insertString(document.getLength(), " ",
                                      document.getStyle("button"));
            }
        }
    }
}
