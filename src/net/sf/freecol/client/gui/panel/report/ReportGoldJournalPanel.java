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
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JComboBox;
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
     * than a shifting list), sorted by net descending per Larry's
     * request. Split into 3 side-by-side sections (each with its own
     * header) rather than one long vertical list, both to use the
     * dialog's spare width and to leave more vertical room for the
     * scrollable turn list above it - the grand total row appears only
     * in the last section. Each category gets a color swatch next to
     * its name, matching the same color used for its pie slice in the
     * chart built alongside this table.
     *
     * @param history The full turn-by-turn history.
     * @return The totals table + pie chart component.
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
        all.sort(Comparator.comparingInt((GoldCategory c)
                -> totalIn.getOrDefault(c, 0) - totalOut.getOrDefault(c, 0))
            .reversed());
        int grandIn = 0, grandOut = 0;
        for (GoldCategory category : all) {
            grandIn += totalIn.getOrDefault(category, 0);
            grandOut += totalOut.getOrDefault(category, 0);
        }

        // LarryDGray's Mods: one color per category, shared between the
        // table's swatches and the pie chart's slices. Uses the golden
        // angle rather than an even 1/N split around the hue wheel -
        // an even split put the FIRST and LAST entries (biggest gain
        // and biggest loss, after the net-descending sort) at opposite
        // ends of a full circle, which is to say right back next to
        // each other in hue (0 and 360 are the same red) - exactly the
        // two categories most important to tell apart at a glance. The
        // golden angle spreads colors with no such wraparound collision
        // regardless of how many categories end up with a slice.
        final Map<GoldCategory, Color> colors = new EnumMap<>(GoldCategory.class);
        for (int i = 0; i < all.size(); i++) {
            final float hue = (float)((i * 0.618033988749895) % 1.0);
            colors.put(all.get(i), Color.getHSBColor(hue, 0.6f, 0.85f));
        }

        final int sectionCount = 3;
        final int perSection = (all.size() + sectionCount - 1) / sectionCount;

        JPanel tables = new JPanel(new MigLayout("wrap " + sectionCount + ", gap 30 0",
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
                JLabel nameLabel = createCellLabel(category.getDisplayName());
                nameLabel.setIcon(new ColorSwatchIcon(colors.get(category), 10));
                nameLabel.setIconTextGap(6);
                section.add(nameLabel);
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
            tables.add(section, "top");
        }

        // LarryDGray's Mods: one pie, not several side by side or
        // stacked - those kept fighting the report's fixed dialog
        // size (a stacked pair overflowed the bottom in Larry's own
        // screenshot; side by side needed the whole footer widened).
        // A dropdown switches between 4 views instead: Net Gains
        // (categories with positive net, the default), Net Losses
        // (negative net, sized by magnitude), In (every category's
        // total inflow, ignoring outflow), Out (every category's
        // total outflow, ignoring inflow) - the last two surface a
        // category that nets to ~0 but still moved real money both
        // ways, which neither of the net-based views would ever show
        // at all.
        final List<GoldCategory> gains = new ArrayList<>();
        final List<GoldCategory> losses = new ArrayList<>();
        final List<GoldCategory> ins = new ArrayList<>();
        final List<GoldCategory> outs = new ArrayList<>();
        final Map<GoldCategory, Integer> gainsValues = new EnumMap<>(GoldCategory.class);
        final Map<GoldCategory, Integer> lossesValues = new EnumMap<>(GoldCategory.class);
        for (GoldCategory category : all) {
            final int in = totalIn.getOrDefault(category, 0);
            final int out = totalOut.getOrDefault(category, 0);
            final int net = in - out;
            if (net > 0) {
                gains.add(category);
                gainsValues.put(category, net);
            } else if (net < 0) {
                losses.add(category);
                lossesValues.put(category, -net);
            }
            if (in > 0) ins.add(category);
            if (out > 0) outs.add(category);
        }

        final JComboBox<String> modeBox = new JComboBox<>(new String[] {
            Messages.message("report.goldJournal.netGains"),
            Messages.message("report.goldJournal.netLosses"),
            Messages.message("report.goldJournal.in"),
            Messages.message("report.goldJournal.out")
        });
        final GoldCategoryPieChart pieChart
            = new GoldCategoryPieChart(gains, colors, gainsValues);
        modeBox.addActionListener(ae -> {
            switch (modeBox.getSelectedIndex()) {
                case 1: pieChart.setData(losses, lossesValues); break;
                case 2: pieChart.setData(ins, totalIn); break;
                case 3: pieChart.setData(outs, totalOut); break;
                default: pieChart.setData(gains, gainsValues); break;
            }
        });

        JPanel pieBox = new JPanel(new MigLayout("wrap 1, gap 0 10", "[fill]", "[][]"));
        pieBox.add(modeBox);
        pieBox.add(pieChart, "width 380!, height 340!");

        JPanel combined = new JPanel(new MigLayout("gap 20 0", "[fill][]", "[top]"));
        combined.add(tables);
        combined.add(pieBox);
        return combined;
    }

    /**
     * LarryDGray's Mods: a small solid-color square icon, used as the
     * legend swatch next to a category name in the totals table -
     * matches the color of that category's pie slice.
     */
    private static final class ColorSwatchIcon implements Icon {
        private final Color color;
        private final int size;

        ColorSwatchIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, size, size);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, size - 1, size - 1);
        }
    }

    /**
     * LarryDGray's Mods: a hand-drawn pie chart over an arbitrary
     * (category, non-negative value) list - genuinely generic over
     * whichever of the Gold Journal's 4 dropdown views is currently
     * selected (Net Gains, Net Losses, In, Out - see
     * {@link #buildCategoryTotalsPanel}), via {@link #setData}. The
     * caller is responsible for pre-filtering to categories that
     * belong in the current view and pre-converting to a positive
     * magnitude (e.g. Net Losses passes each category's net negated) -
     * this class only ever draws what it's handed, with no notion of
     * sign itself. Each slice is labelled with its category's short
     * abbreviation ({@link GoldCategory#getAbbreviation()}) at the end
     * of a leader line drawn out from the slice's edge, rather than
     * inside the wedge itself - a thin slice is often too narrow to
     * hold even a 2-3 letter label without overlapping its neighbours.
     */
    private static final class GoldCategoryPieChart extends JComponent {
        private List<GoldCategory> categories;
        private final Map<GoldCategory, Color> colors;
        private Map<GoldCategory, Integer> values;

        GoldCategoryPieChart(List<GoldCategory> categories,
                             Map<GoldCategory, Color> colors,
                             Map<GoldCategory, Integer> values) {
            this.categories = categories;
            this.colors = colors;
            this.values = values;
            setOpaque(false);
        }

        /**
         * LarryDGray's Mods: switch what this chart displays (one of
         * the 4 dropdown modes) without rebuilding the component -
         * every slice's value already comes from a plain lookup, so
         * whichever map/list the caller hands in just IS the chart,
         * no per-mode branching needed inside this class at all.
         *
         * @param categories The categories to show, already filtered
         *     to only ones with something to show.
         * @param values Each category's slice value for this mode.
         */
        void setData(List<GoldCategory> categories, Map<GoldCategory, Integer> values) {
            this.categories = categories;
            this.values = values;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            final Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            double total = 0;
            for (GoldCategory c : categories) {
                total += values.getOrDefault(c, 0);
            }
            if (total > 0) {
                // LarryDGray's Mods: the circle itself is kept fairly
                // small and biased toward the lower right of the
                // component, leaving generous room all around for the
                // leader-line labels below (rather than cramming a
                // 2-3 letter label inside a slice that might be a
                // sliver too thin to hold it).
                final int radius = (int)(Math.min(getWidth(), getHeight()) * 0.26);
                final int cx = (int)(getWidth() * 0.52);
                final int cy = (int)(getHeight() * 0.56);
                final int size = radius * 2;
                final int baseLeaderLength = 22;
                final int staggerStep = 16;
                // LarryDGray's Mods: a cluster of several small,
                // similarly-angled slices in a row (categories are
                // drawn largest-first, so the small ones naturally end
                // up adjacent) puts their labels on top of each other
                // at a fixed leader length - stagger progressively
                // longer leader lengths for consecutive slices whose
                // midpoint angle is close to the previous one, resetting
                // back to the base length once the angle gap widens out
                // again.
                double prevMidAngleDeg = Double.NaN;
                int leaderLength = baseLeaderLength;

                double startAngle = 90.0;
                g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                final FontMetrics fm = g2.getFontMetrics();
                for (GoldCategory c : categories) {
                    final int value = values.getOrDefault(c, 0);
                    if (value == 0) continue;
                    final double extent = 360.0 * value / total;

                    g2.setColor(colors.get(c));
                    final Arc2D.Double arc = new Arc2D.Double(cx - radius, cy - radius,
                        size, size, startAngle, -extent, Arc2D.PIE);
                    g2.fill(arc);
                    g2.setColor(getBackground() == null ? Color.WHITE : getBackground());
                    g2.draw(arc);

                    // LarryDGray's Mods: a straight leader line from
                    // the slice's edge out to its label, rather than
                    // drawing the abbreviation inside the wedge - a
                    // thin slice's own wedge is often too narrow to
                    // hold even a 2-3 letter label without it
                    // overlapping its neighbours.
                    final double midAngleDeg = startAngle - extent / 2;
                    final double midAngle = Math.toRadians(midAngleDeg);
                    final double cos = Math.cos(midAngle);
                    final double sin = Math.sin(midAngle);
                    if (!Double.isNaN(prevMidAngleDeg)
                        && Math.abs(midAngleDeg - prevMidAngleDeg) < 18.0) {
                        leaderLength += staggerStep;
                    } else {
                        leaderLength = baseLeaderLength;
                    }
                    prevMidAngleDeg = midAngleDeg;

                    final int edgeX = (int)Math.round(cx + radius * cos);
                    final int edgeY = (int)Math.round(cy - radius * sin);
                    final int outerX = (int)Math.round(cx + (radius + leaderLength) * cos);
                    final int outerY = (int)Math.round(cy - (radius + leaderLength) * sin);
                    g2.setColor(Color.DARK_GRAY);
                    g2.drawLine(edgeX, edgeY, outerX, outerY);

                    final String label = c.getAbbreviation();
                    final int tw = fm.stringWidth(label);
                    final int textX = (cos >= 0) ? outerX + 3 : outerX - 3 - tw;
                    final int textY = outerY + fm.getAscent() / 2 - 2;
                    g2.setColor(Color.BLACK);
                    g2.drawString(label, textX, textY);

                    startAngle -= extent;
                }
            }
            g2.dispose();
        }
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
