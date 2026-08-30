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

import java.awt.Color;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.client.gui.report.GoldJournalHistory;
import net.sf.freecol.common.model.GoldCategory;
import net.sf.freecol.common.model.Player;


/**
 * LarryDGray's Mods: displays the full-timeline Gold Journal Report -
 * a real ledger, one row per turn sampled so far this session,
 * showing gold in, gold out, net, and the running balance. Unlike the
 * other timeline reports (Colony Growth, Nation Comparison, Trade
 * History), this is a plain scrollable table, not a chart - a
 * journal's whole point is reading specific numbers for a specific
 * period, which a chart is bad at and a table is built for. Note the
 * current (unsaved) session's most recent turns may not appear until
 * the next save/reload - see {@link GoldJournalHistory}'s doc comment
 * for why.
 */
public final class ReportGoldJournalPanel extends ReportPanel {

    private static final Color WARN_COLOR = Color.RED;


    /**
     * The constructor that will add the items to this panel.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportGoldJournalPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportGoldJournalAction");

        Player player = getMyPlayer();
        List<GoldJournalHistory.Sample> history
            = igc().getGoldJournalHistory().getHistory(player);

        reportPanel.setLayout(new MigLayout("wrap 6, gap 10 2",
            "[fill][fill, right][fill, right][fill, right][fill, right][fill, left, grow]",
            "[]"));

        if (history.isEmpty()) {
            reportPanel.add(Utility.localizedLabel(
                "report.goldJournal.noData"), "span, align center");
            return;
        }

        reportPanel.add(createHeaderLabel("report.goldJournal.turn"));
        reportPanel.add(createHeaderLabel("report.goldJournal.in"));
        reportPanel.add(createHeaderLabel("report.goldJournal.out"));
        reportPanel.add(createHeaderLabel("report.goldJournal.net"));
        reportPanel.add(createHeaderLabel("report.goldJournal.balance"));
        reportPanel.add(createHeaderLabel("report.goldJournal.notes"));

        for (GoldJournalHistory.Sample s : history) {
            int net = s.goldIn - s.goldOut;
            reportPanel.add(createCellLabel(String.valueOf(s.turn)));
            reportPanel.add(createAmountLabel(s.goldIn, false));
            reportPanel.add(createAmountLabel(s.goldOut, false));
            reportPanel.add(createAmountLabel(net, true));
            reportPanel.add(createAmountLabel(s.balance, false));
            reportPanel.add(createNotesLabel(s));
        }

        reportPanel.add(buildCategoryTotalsPanel(history), "span, growx, wrap, gaptop 10");

        // LarryDGray's Mods: open already scrolled to the most recent
        // turn (the bottom row) instead of the oldest - the whole
        // point of checking this report mid-game is "what just
        // happened", and scrolling down manually every time got old
        // fast. Deferred via invokeLater since the scroll pane's
        // scrollable range isn't known until after this constructor's
        // layout pass actually completes.
        SwingUtilities.invokeLater(() -> {
            final JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    /**
     * LarryDGray's Mods: build a small totals-by-category table
     * summing every sampled turn's activity, one row per
     * {@code GoldCategory} that ever had any, plus a grand-total row -
     * so the player can see at a glance where their gold has actually
     * come from/gone to over the whole session, not just per turn.
     *
     * @param history The full turn-by-turn history.
     * @return The totals table component.
     */
    private JComponent buildCategoryTotalsPanel(
            List<GoldJournalHistory.Sample> history) {
        Map<GoldCategory, Integer> totalIn = new EnumMap<>(GoldCategory.class);
        Map<GoldCategory, Integer> totalOut = new EnumMap<>(GoldCategory.class);
        for (GoldJournalHistory.Sample s : history) {
            for (Map.Entry<GoldCategory, Integer> e
                    : s.goldInByCategory.entrySet()) {
                totalIn.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (Map.Entry<GoldCategory, Integer> e
                    : s.goldOutByCategory.entrySet()) {
                totalOut.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        Set<GoldCategory> categories = EnumSet.noneOf(GoldCategory.class);
        categories.addAll(totalIn.keySet());
        categories.addAll(totalOut.keySet());

        JPanel panel = new JPanel(new MigLayout("wrap 4, gap 10 2",
            "[fill][fill, right][fill, right][fill, right]", "[]"));
        panel.add(createHeaderLabel("report.goldJournal.category"));
        panel.add(createHeaderLabel("report.goldJournal.in"));
        panel.add(createHeaderLabel("report.goldJournal.out"));
        panel.add(createHeaderLabel("report.goldJournal.net"));

        int grandIn = 0, grandOut = 0;
        for (GoldCategory category : categories) {
            int in = totalIn.getOrDefault(category, 0);
            int out = totalOut.getOrDefault(category, 0);
            grandIn += in;
            grandOut += out;
            panel.add(createCellLabel(category.getDisplayName()));
            panel.add(createAmountLabel(in, false));
            panel.add(createAmountLabel(out, false));
            panel.add(createAmountLabel(in - out, true));
        }
        panel.add(createCellLabel(Messages.message("report.goldJournal.total")));
        panel.add(createAmountLabel(grandIn, false));
        panel.add(createAmountLabel(grandOut, false));
        panel.add(createAmountLabel(grandIn - grandOut, true));
        return panel;
    }

    /**
     * LarryDGray's Mods: build a short, human-readable note for what
     * actually happened on this turn - one "Category +/-amount"
     * fragment per {@code GoldCategory} that had any activity,
     * comma-separated. This is what makes the journal a real ledger
     * rather than just a running total.
     *
     * @param s The turn's {@code GoldJournalHistory.Sample}.
     * @return The notes label.
     */
    private JLabel createNotesLabel(GoldJournalHistory.Sample s) {
        Set<GoldCategory> categories = EnumSet.noneOf(GoldCategory.class);
        categories.addAll(s.goldInByCategory.keySet());
        categories.addAll(s.goldOutByCategory.keySet());

        StringBuilder sb = new StringBuilder();
        for (GoldCategory category : categories) {
            int net = s.goldInByCategory.getOrDefault(category, 0)
                - s.goldOutByCategory.getOrDefault(category, 0);
            if (net == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(category.getDisplayName()).append(' ');
            if (net > 0) sb.append('+');
            sb.append(net);
        }

        JLabel result = new JLabel(sb.toString());
        result.setBorder(Utility.getCellBorder());
        return result;
    }

    private JLabel createHeaderLabel(String key) {
        JLabel result = Utility.localizedLabel(key);
        result.setBorder(Utility.getTopCellBorder());
        return result;
    }

    private JLabel createCellLabel(String text) {
        JLabel result = new JLabel(text, JLabel.TRAILING);
        result.setBorder(Utility.getCellBorder());
        return result;
    }

    private JLabel createAmountLabel(int value, boolean alwaysAddSign) {
        JLabel result = new JLabel(String.valueOf(value), JLabel.TRAILING);
        result.setBorder(Utility.getCellBorder());
        if (value < 0) {
            result.setForeground(WARN_COLOR);
            result.setText(String.valueOf(value));
        } else if (alwaysAddSign && value > 0) {
            result.setText("+" + value);
        }
        return result;
    }
}
