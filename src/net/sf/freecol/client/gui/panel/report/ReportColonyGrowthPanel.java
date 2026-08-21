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
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.CurrentValueBarChart;
import net.sf.freecol.client.gui.panel.HistoryLineChart;
import net.sf.freecol.client.gui.panel.HistoryLineChart.Series;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.client.gui.report.ColonyGrowthHistory;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FreeColSpecObjectType;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.UnitType;


/**
 * LarryDGray's Mods: displays the full-timeline Colony Growth Report -
 * one selectable empire-wide statistic plotted over every turn
 * sampled so far this session: population, all colonists owned,
 * number of settlements, Sons of Liberty%, accumulated liberty, every
 * land military unit type together on one chart (split into "any
 * unit in that role" and "the role's professional/expert type"
 * lines, plus muskets/horses in storage as dashed secondary-axis
 * lines), or every ship type plus wagon trains together on another.
 */
public final class ReportColonyGrowthPanel extends ReportPanel {

    /** One line within a (possibly multi-line) selectable stat. */
    private static final class SeriesSpec {

        final String label;
        final Color color;
        final boolean dashed;
        /** LarryDGray's Mods: true for trivial categories (scouts)
         *  that are worth a current-turn bar but not a historical
         *  trend line - excluded from the line chart, still shown in
         *  the current-value bar chart. */
        final boolean barOnly;
        final ToIntFunction<ColonyGrowthHistory.Sample> extractor;

        SeriesSpec(String label, Color color, boolean dashed, boolean barOnly,
                  ToIntFunction<ColonyGrowthHistory.Sample> extractor) {
            this.label = label;
            this.color = color;
            this.dashed = dashed;
            this.barOnly = barOnly;
            this.extractor = extractor;
        }
    }

    /** One dropdown entry: a label, plus the one or more chart lines
     *  it plots together. */
    private static final class StatEntry {

        final String label;
        final List<SeriesSpec> seriesSpecs;
        /** Multi-line unit/wagon counts read better as bars - they
         *  change rarely, so a near-flat line is harder to read than
         *  a bar. The five core growth trends stay lines. */
        final boolean barMode;

        StatEntry(String label, List<SeriesSpec> seriesSpecs, boolean barMode) {
            this.label = label;
            this.seriesSpecs = seriesSpecs;
            this.barMode = barMode;
        }

        StatEntry(String label, ToIntFunction<ColonyGrowthHistory.Sample> extractor) {
            this(label, List.of(new SeriesSpec(label, PALETTE[0], false, false,
                extractor)), false);
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private static final String SOLDIER_ROLE_ID = "model.role.soldier";
    private static final String DRAGOON_ROLE_ID = "model.role.dragoon";

    /** Cycled across the lines within a multi-line stat entry. */
    private static final Color[] PALETTE = {
        Color.BLUE, Color.RED, new Color(0, 140, 0), new Color(210, 105, 0),
        new Color(150, 0, 150), new Color(0, 150, 150), new Color(120, 80, 40),
        Color.GRAY
    };

    private final List<ColonyGrowthHistory.Sample> history;
    private final JComboBox<StatEntry> statSelector;
    private final HistoryLineChart chart;
    private final CurrentValueBarChart barChart;


    /**
     * The constructor that will add the items to this panel.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportColonyGrowthPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportColonyGrowthAction");

        Player player = getMyPlayer();
        this.history = igc().getColonyGrowthHistory().getHistory(player);
        logger.info("LarryDGray's Mods: ReportColonyGrowthPanel opened for "
            + player.getId() + " with " + this.history.size()
            + " samples in the client-side cache");

        List<StatEntry> stats = new ArrayList<>(List.of(
            new StatEntry(Messages.message("report.colonyGrowth.population"),
                s -> s.population),
            new StatEntry(Messages.message("report.colonyGrowth.citizens"),
                s -> s.citizens),
            new StatEntry(Messages.message("report.colonyGrowth.settlements"),
                s -> s.numberOfSettlements),
            new StatEntry(Messages.message("report.colonyGrowth.sonsOfLiberty"),
                s -> s.sonsOfLiberty),
            new StatEntry(Messages.message("report.colonyGrowth.liberty"),
                s -> s.liberty),
            // LarryDGray's Mods: combined count across both roles and
            // both the regular/professional split, for "how many
            // armed land units do I have in total" at a glance.
            new StatEntry(Messages.message("report.colonyGrowth.totalArmedLand"),
                s -> s.unitCounts.getOrDefault(SOLDIER_ROLE_ID, 0)
                    + s.unitCounts.getOrDefault(
                        SOLDIER_ROLE_ID + ColonyGrowthHistory.PROFESSIONAL_SUFFIX, 0)
                    + s.unitCounts.getOrDefault(DRAGOON_ROLE_ID, 0)
                    + s.unitCounts.getOrDefault(
                        DRAGOON_ROLE_ID + ColonyGrowthHistory.PROFESSIONAL_SUFFIX, 0))));

        // LarryDGray's Mods: which military unit types (and wagon
        // trains) to offer isn't known ahead of time - collect every
        // category id ever recorded across this player's whole
        // history, and split by land vs. naval/wagon so each group
        // can be plotted together on its own chart.
        TreeSet<String> categoryIds = new TreeSet<>();
        for (ColonyGrowthHistory.Sample s : this.history) {
            categoryIds.addAll(s.unitCounts.keySet());
        }
        TreeMap<String, String> landLabelToId = new TreeMap<>();
        TreeMap<String, String> navalLabelToId = new TreeMap<>();
        for (String categoryId : categoryIds) {
            String label = labelFor(categoryId);
            (isNavalOrWagon(categoryId) ? navalLabelToId : landLabelToId)
                .put(label, categoryId);
        }

        if (!landLabelToId.isEmpty()) {
            List<SeriesSpec> specs = new ArrayList<>();
            int colorIndex = 0;
            for (Map.Entry<String, String> e : landLabelToId.entrySet()) {
                final String id = e.getValue();
                // LarryDGray's Mods: scouts are usually just one or
                // two units - worth a current-turn bar, not a whole
                // historical trend line.
                boolean barOnly = ColonyGrowthHistory.SCOUT_ROLE_ID.equals(id)
                    || ColonyGrowthHistory.SEASONED_SCOUT_ID.equals(id);
                specs.add(new SeriesSpec(e.getKey(),
                    PALETTE[colorIndex++ % PALETTE.length], false, barOnly,
                    s -> s.unitCounts.getOrDefault(id, 0)));
            }
            // LarryDGray's Mods: muskets/horses in storage overlaid
            // as dashed secondary-axis lines, since a warehouse can
            // hold hundreds while unit counts are typically single
            // or double digits.
            for (GoodsType gt : getSpecification().getGoodsTypeList()) {
                if (!gt.getMilitary()) continue;
                final String id = gt.getId();
                specs.add(new SeriesSpec(Messages.getName(gt),
                    PALETTE[colorIndex++ % PALETTE.length], true, false,
                    s -> s.goodsCounts.getOrDefault(id, 0)));
            }
            stats.add(new StatEntry(
                Messages.message("report.colonyGrowth.landMilitary"), specs, true));
        }
        if (!navalLabelToId.isEmpty()) {
            List<SeriesSpec> specs = new ArrayList<>();
            int colorIndex = 0;
            for (Map.Entry<String, String> e : navalLabelToId.entrySet()) {
                final String id = e.getValue();
                specs.add(new SeriesSpec(e.getKey(),
                    PALETTE[colorIndex++ % PALETTE.length], false, false,
                    s -> s.unitCounts.getOrDefault(id, 0)));
            }
            stats.add(new StatEntry(
                Messages.message("report.colonyGrowth.shipsAndWagons"), specs, true));
        }

        // LarryDGray's Mods: each colony's own population plotted as
        // its own line, rather than only the empire-wide total above
        // - one line per colony ever seen across this player's whole
        // history, labelled by name (or a placeholder if it's since
        // been lost) so a captured/destroyed colony's line simply
        // stops advancing instead of vanishing from the legend.
        TreeSet<String> colonyIds = new TreeSet<>();
        for (ColonyGrowthHistory.Sample s : this.history) {
            colonyIds.addAll(s.colonyPopulations.keySet());
        }
        if (!colonyIds.isEmpty()) {
            List<SeriesSpec> specs = new ArrayList<>();
            int colorIndex = 0;
            for (String colonyId : colonyIds) {
                final String id = colonyId;
                Colony colony = getGame().getFreeColGameObject(id, Colony.class);
                String label = (colony == null)
                    ? Messages.message("report.colonyGrowth.lostColony")
                    : colony.getName();
                specs.add(new SeriesSpec(label,
                    PALETTE[colorIndex++ % PALETTE.length], false, false,
                    s -> s.colonyPopulations.getOrDefault(id, 0)));
            }
            stats.add(new StatEntry(
                Messages.message("report.colonyGrowth.citySizeByColony"), specs, false));
        }

        this.statSelector = new JComboBox<>(stats.toArray(new StatEntry[0]));
        // LarryDGray's Mods: FreeColComboBoxRenderer's default
        // setLabelValues() only knows how to label Integer/Language/
        // String/Named/ObjectWithId/InetAddress/MixerWrapper values,
        // so a plain object renders as a blank row without this.
        this.statSelector.setRenderer(new FreeColComboBoxRenderer<StatEntry>() {
            @Override
            protected void setLabelValues(JLabel c, StatEntry value) {
                c.setText((value == null) ? null : value.toString());
            }
        });
        this.statSelector.addActionListener(
            (ActionEvent ae) -> updateChart());
        this.chart = new HistoryLineChart(freeColClient);
        this.barChart = new CurrentValueBarChart(freeColClient);

        reportPanel.setLayout(new MigLayout("wrap 1, gap 0 10",
                                            "[fill]", "[][][fill, grow]"));
        updateChart();
    }


    /**
     * Is the given military/wagon category a wagon train or a naval
     * unit type? Role-based categories (soldier, dragoon, and their
     * professional variants) are never naval, since ships carry no
     * roles in this ruleset.
     *
     * @param categoryId The category id to check.
     * @return True if this belongs on the Ships &amp; Wagons chart.
     */
    private boolean isNavalOrWagon(String categoryId) {
        if (ColonyGrowthHistory.WAGON_TRAIN_ID.equals(categoryId)) return true;
        String baseId = baseCategoryId(categoryId);
        FreeColSpecObjectType fcot = getSpecification().getType(baseId);
        return (fcot instanceof UnitType) && ((UnitType)fcot).isNaval();
    }

    /**
     * Strip the professional-variant suffix, if present, to get the
     * underlying specification id.
     *
     * @param categoryId The category id, possibly suffixed.
     * @return The base specification id.
     */
    private static String baseCategoryId(String categoryId) {
        return (categoryId.endsWith(ColonyGrowthHistory.PROFESSIONAL_SUFFIX))
            ? categoryId.substring(0, categoryId.length()
                - ColonyGrowthHistory.PROFESSIONAL_SUFFIX.length())
            : categoryId;
    }

    /**
     * Resolve a display label for a military/wagon category id, e.g.
     * "Dragoon" for the plain role id, or "Professional Dragoon" for
     * its professional-variant id.
     *
     * @param categoryId The category id to label.
     * @return The localized display label.
     */
    private String labelFor(String categoryId) {
        String baseId = baseCategoryId(categoryId);
        FreeColSpecObjectType fcot = getSpecification().getType(baseId);
        String baseLabel = (fcot == null) ? baseId : Messages.getName(fcot);
        if (categoryId.endsWith(ColonyGrowthHistory.PROFESSIONAL_SUFFIX)) {
            return Messages.message("report.colonyGrowth.professionalPrefix")
                + " " + baseLabel;
        }
        // LarryDGray's Mods: for an offensive role with an
        // expert-unit (soldier, dragoon), the plain role id is
        // specifically the non-expert-type count now, disjoint from
        // its Professional counterpart. Soldier's plain-role label is
        // "Armed" specifically (per Larry's naming preference, since
        // "just has muskets" reads more naturally than "Soldier" once
        // there's also a Dragoon line on the same chart); other roles
        // (Dragoon) just use their own role name with no prefix.
        // Scout is excluded even though it technically carries a
        // token +1 offence modifier: it's tracked entirely separately
        // (see ColonyGrowthHistory.addUnitCategories), and its own
        // Seasoned-Scout count works differently (counted even when
        // not currently mounted).
        if (!ColonyGrowthHistory.SCOUT_ROLE_ID.equals(baseId)
                && fcot instanceof Role && ((Role)fcot).isOffensive()
                && ((Role)fcot).getExpertUnit() != null) {
            return SOLDIER_ROLE_ID.equals(baseId)
                ? Messages.message("report.colonyGrowth.armed")
                : baseLabel;
        }
        return baseLabel;
    }

    /**
     * Rebuild both charts and the panel layout for the selected stat.
     * A multi-line stat (Land Military Units, Ships &amp; Wagons)
     * shows a current-turn bar chart alongside the historical trend
     * line chart; every other stat is the trend chart alone. Rebuilt
     * from scratch each time, matching the pattern other reports
     * (e.g. {@code ReportTradePanel}) already use for a
     * state-dependent layout.
     */
    private void updateChart() {
        StatEntry stat = (StatEntry)this.statSelector.getSelectedItem();
        List<Series> series = new ArrayList<>();
        List<CurrentValueBarChart.Bar> bars = new ArrayList<>();
        if (stat != null) {
            final int n = this.history.size();
            int[] turns = new int[n];
            for (int i = 0; i < n; i++) turns[i] = this.history.get(i).turn;
            ColonyGrowthHistory.Sample latest
                = (n > 0) ? this.history.get(n - 1) : null;
            for (SeriesSpec spec : stat.seriesSpecs) {
                if (!spec.barOnly) {
                    double[] values = new double[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = spec.extractor.applyAsInt(this.history.get(i));
                    }
                    series.add(new Series(spec.label, spec.color, turns, values,
                                          spec.dashed));
                }
                if (!spec.dashed && latest != null) {
                    bars.add(new CurrentValueBarChart.Bar(spec.label, spec.color,
                        spec.extractor.applyAsInt(latest)));
                }
            }
        }
        this.chart.setSeries(series);
        this.barChart.setBars(bars);

        reportPanel.removeAll();
        reportPanel.add(Utility.localizedLabel("report.colonyGrowth.selectStat"));
        reportPanel.add(this.statSelector);
        if (stat != null && stat.barMode) {
            JPanel chartsRow = new MigPanel(new MigLayout(
                "insets 0, gap 10 0", "[][grow]", "[grow]"));
            chartsRow.add(this.barChart, "grow");
            chartsRow.add(this.chart, "grow, push");
            reportPanel.add(chartsRow, "grow, push");
        } else {
            reportPanel.add(this.chart, "grow, push");
        }
        reportPanel.revalidate();
        reportPanel.repaint();
    }
}
