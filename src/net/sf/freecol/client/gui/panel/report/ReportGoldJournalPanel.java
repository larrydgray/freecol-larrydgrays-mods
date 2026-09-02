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
import java.util.ArrayList;
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
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.client.gui.report.GoldJournalHistory;
import net.sf.freecol.common.i18n.NameCache;
import net.sf.freecol.common.model.GoldCategory;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;


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

        // LarryDGray's Mods: the column headers live in the scroll
        // pane's own column-header-view, not in the scrollable body,
        // so they stay pinned in place while the turn list scrolls -
        // same technique ReportTradePanel's goodsHeader uses. Each
        // column is LOCKED to an explicit pixel width (the trailing
        // "!") rather than left to "[fill]" - otherwise the header
        // panel and the scrollable body, being two separate MigLayout
        // instances, each size their own columns from their own
        // content (short header words vs. longer numbers) and drift
        // out of alignment with each other, exactly as ReportTradePanel
        // already has to guard against for its own header/body split.
        final String columnConstraints = "[50!, right][60!][50!, right]"
            + "[60!, right][60!, right][60!, right][70!, right][fill, left, grow]";
        reportPanel.setLayout(new MigLayout("wrap 8, gap 10 2",
            columnConstraints, "[]"));

        if (history.isEmpty()) {
            reportPanel.add(Utility.localizedLabel(
                "report.goldJournal.noData"), "span, align center");
            return;
        }

        final JPanel header = new MigPanel("ReportPanelUI");
        header.setLayout(new MigLayout("wrap 8, gap 10 2",
            columnConstraints, "[]"));
        header.setOpaque(true);
        header.add(createHeaderLabel("report.goldJournal.year"));
        header.add(createHeaderLabel("report.goldJournal.season"));
        header.add(createHeaderLabel("report.goldJournal.turn"));
        header.add(createHeaderLabel("report.goldJournal.in"));
        header.add(createHeaderLabel("report.goldJournal.out"));
        header.add(createHeaderLabel("report.goldJournal.net"));
        header.add(createHeaderLabel("report.goldJournal.balance"));
        header.add(createHeaderLabel("report.goldJournal.notes"));
        scrollPane.setColumnHeaderView(header);

        for (GoldJournalHistory.Sample s : history) {
            int net = s.goldIn - s.goldOut;
            // LarryDGray's Mods: Year/Season alongside the raw turn
            // number, reusing the same conversion the top status bar
            // uses ("Spring 1600") - Turn.getTurnSeason() returns -1
            // for turns with no seasons (pre-1600), left blank then.
            final int season = Turn.getTurnSeason(s.turn);
            reportPanel.add(createCellLabel(
                String.valueOf(Turn.getTurnYear(s.turn))));
            reportPanel.add(createCellLabel(
                (season < 0) ? "" : NameCache.getSeasonName(season)));
            reportPanel.add(createCellLabel(String.valueOf(s.turn)));
            reportPanel.add(createAmountLabel(s.goldIn, false));
            reportPanel.add(createAmountLabel(s.goldOut, false));
            reportPanel.add(createAmountLabel(net, true));
            reportPanel.add(createAmountLabel(s.balance, false));
            reportPanel.add(createNotesLabel(s));
        }

        // LarryDGray's Mods: the category totals summary lives outside
        // the scroll pane entirely (a fixed footer) rather than as the
        // last row of the scrollable turn list, so it's always visible
        // without scrolling all the way down to it. Uses the same
        // "[fill]" row-grows-others-don't MigLayout idiom ReportPanel's
        // own outer layout already uses for its header/scrollpane/OK
        // rows, rather than BorderLayout - BorderLayout.CENTER should
        // also grow to fill remaining space in theory, but in practice
        // left the scroll area sized to its content instead of the
        // available height, wasting the rest of the dialog as blank
        // space below the footer.
        final JPanel wrapper = new JPanel(new MigLayout("wrap 1, insets 0",
            "[fill, grow]", "[fill, grow]0[]"));
        wrapper.setOpaque(false);
        wrapper.add(scrollPane, "grow");
        wrapper.add(buildCategoryTotalsPanel(history), "growx");
        setMainComponent(wrapper);

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
     * summing every sampled turn's activity, one row per every
     * {@code GoldCategory} that exists (not just ones that have had
     * activity yet - a category with nothing to show still shows a
     * 0/0/0 row, so the table reads as a complete reference rather
     * than a shifting list). Split into 3 side-by-side sections (each
     * with its own header) rather than one long vertical list, both to
     * use the dialog's spare width and to leave more vertical room for
     * the scrollable turn list above it - the grand total row appears
     * only in the last section.
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
        final List<GoldCategory> all = new ArrayList<>(EnumSet.allOf(GoldCategory.class));
        int grandIn = 0, grandOut = 0;
        for (GoldCategory category : all) {
            grandIn += totalIn.getOrDefault(category, 0);
            grandOut += totalOut.getOrDefault(category, 0);
        }

        final int sectionCount = 3;
        final int perSection = (all.size() + sectionCount - 1) / sectionCount;

        JPanel outer = new JPanel(new MigLayout("wrap " + sectionCount + ", gap 30 0",
            "[fill][fill][fill]", "[top]"));
        for (int i = 0; i < sectionCount; i++) {
            final int from = i * perSection;
            final int to = Math.min(from + perSection, all.size());
            if (from >= to) continue;
            final boolean isLast = (i == sectionCount - 1);

            JPanel section = new JPanel(new MigLayout("wrap 4, gap 10 2",
                "[fill][fill, right][fill, right][fill, right]", "[]"));
            section.add(createHeaderLabel("report.goldJournal.category"));
            section.add(createHeaderLabel("report.goldJournal.in"));
            section.add(createHeaderLabel("report.goldJournal.out"));
            section.add(createHeaderLabel("report.goldJournal.net"));
            for (GoldCategory category : all.subList(from, to)) {
                int in = totalIn.getOrDefault(category, 0);
                int out = totalOut.getOrDefault(category, 0);
                section.add(createCellLabel(category.getDisplayName()));
                section.add(createAmountLabel(in, false));
                section.add(createAmountLabel(out, false));
                section.add(createAmountLabel(in - out, true));
            }
            if (isLast) {
                section.add(createCellLabel(Messages.message("report.goldJournal.total")));
                section.add(createAmountLabel(grandIn, false));
                section.add(createAmountLabel(grandOut, false));
                section.add(createAmountLabel(grandIn - grandOut, true));
            }
            outer.add(section, "top");
        }
        return outer;
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

    // LarryDGray's Mods: a blank cell for zero reads cleaner than a
    // wall of "0"s, especially now the category totals table always
    // shows every category (most of which are 0 for any given game) -
    // matches the same blank-instead-of-zero convention already used
    // in ReportTradePanel. A single space rather than a truly empty
    // string, so the label still reserves its normal line height and
    // that row doesn't look visually shorter/uneven next to rows with
    // real numbers in that column.
    private JLabel createAmountLabel(int value, boolean alwaysAddSign) {
        JLabel result = new JLabel((value == 0) ? " " : String.valueOf(value),
            JLabel.TRAILING);
        result.setBorder(Utility.getCellBorder());
        if (value < 0) {
            result.setForeground(WARN_COLOR);
        } else if (alwaysAddSign && value > 0) {
            result.setText("+" + value);
        }
        return result;
    }
}
