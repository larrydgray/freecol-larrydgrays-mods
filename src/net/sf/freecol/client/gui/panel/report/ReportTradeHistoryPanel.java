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
import java.util.function.Function;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.HistoryLineChart;
import net.sf.freecol.client.gui.panel.HistoryLineChart.Series;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.client.gui.report.TradeHistory;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;


/**
 * LarryDGray's Mods: displays the full-timeline Trade History Report -
 * a selectable group of storable goods types (Basic/farmed vs.
 * Refined/manufactured, since all 16 at once was too much on one
 * chart) plotted together over every turn sampled so far this
 * session, empire-wide, one colored line per goods type. A second
 * selector picks which of several per-goods values is currently
 * shown for every line at once - showing more than one metric at
 * once was rejected as too cluttered with this many goods types on
 * one chart.
 */
public final class ReportTradeHistoryPanel extends ReportPanel {

    /** One dropdown entry: a group label plus the goods types in it. */
    private static final class GoodsGroup {

        final String label;
        final List<GoodsType> goods;

        GoodsGroup(String label, List<GoodsType> goods) {
            this.label = label;
            this.goods = goods;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /** One dropdown entry: a label plus how to pull that value out of
     *  a sample. */
    private static final class MetricEntry {

        final String label;
        final Function<TradeHistory.Sample, java.util.Map<String, Integer>> valuesOf;

        MetricEntry(String label,
                   Function<TradeHistory.Sample, java.util.Map<String, Integer>> valuesOf) {
            this.label = label;
            this.valuesOf = valuesOf;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /** Cycled across the lines within the selected group. */
    private static final Color[] PALETTE = {
        Color.BLUE, Color.RED, new Color(0, 140, 0), new Color(210, 105, 0),
        new Color(150, 0, 150), new Color(0, 150, 150), new Color(120, 80, 40),
        Color.GRAY, new Color(220, 20, 120), new Color(0, 90, 200)
    };

    private final List<TradeHistory.Sample> history;
    private final JComboBox<GoodsGroup> groupSelector;
    private final JComboBox<MetricEntry> metricSelector;
    private final HistoryLineChart chart;


    /**
     * The constructor that will add the items to this panel.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportTradeHistoryPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportTradeHistoryAction");

        Player player = getMyPlayer();
        this.history = igc().getTradeHistory().getHistory(player);
        logger.info("LarryDGray's Mods: ReportTradeHistoryPanel opened for "
            + player.getId() + " with " + this.history.size()
            + " samples in the client-side cache");

        // LarryDGray's Mods: split by isFarmed() (tile-farmed raw
        // material vs. everything produced in a building or bred/
        // imported) rather than isRefined()/madeFrom, since horses
        // (bred from food) and trade goods (imported, no production
        // chain at all) would otherwise land in the wrong bucket -
        // isFarmed() gives a clean, evenly-split "raw vs. everything
        // else" grouping matching the classic ruleset's own farmed
        // flag.
        final Specification spec = getSpecification();
        List<GoodsType> basic = new ArrayList<>();
        List<GoodsType> refined = new ArrayList<>();
        for (GoodsType gt : spec.getStorableGoodsTypeList()) {
            // LarryDGray's Mods: a good with no farmed source and no
            // production chain at all (Trade Goods in the classic
            // ruleset - bought in Europe, not grown or manufactured,
            // and typically just carried as cargo rather than
            // stockpiled) has nothing meaningful to show on most of
            // these metrics - it would only ever plot a flat, noisy
            // zero line except on Units In Cargo.
            if (!gt.isFarmed() && gt.getInputType() == null) continue;
            (gt.isFarmed() ? basic : refined).add(gt);
        }
        List<GoodsGroup> groups = List.of(
            new GoodsGroup(Messages.message("report.tradeHistory.basicGoods"), basic),
            new GoodsGroup(Messages.message("report.tradeHistory.refinedGoods"), refined));

        this.groupSelector = new JComboBox<>(groups.toArray(new GoodsGroup[0]));
        this.groupSelector.setRenderer(labelRenderer());
        this.groupSelector.addActionListener(
            (ActionEvent ae) -> updateChart());

        List<MetricEntry> metrics = List.of(
            new MetricEntry(Messages.message("report.trade.onHand"),
                s -> s.goodsOnHand),
            new MetricEntry(Messages.message("report.trade.production"),
                s -> s.goodsProduction),
            new MetricEntry(Messages.message("report.trade.netProduction"),
                s -> s.goodsNetProduction),
            new MetricEntry(Messages.message("report.tradeHistory.salesNet"),
                s -> s.goodsSales),
            new MetricEntry(Messages.message("report.tradeHistory.unitsBought"),
                s -> s.goodsUnitsBought),
            new MetricEntry(Messages.message("report.tradeHistory.unitsSold"),
                s -> s.goodsUnitsSold),
            new MetricEntry(Messages.message("report.tradeHistory.incomeBeforeTaxes"),
                s -> s.goodsIncomeBeforeTaxes),
            new MetricEntry(Messages.message("report.tradeHistory.incomeAfterTaxes"),
                s -> s.goodsIncomeAfterTaxes),
            new MetricEntry(Messages.message("report.tradeHistory.unitsInCargo"),
                s -> s.goodsUnitsInCargo));
        this.metricSelector = new JComboBox<>(metrics.toArray(new MetricEntry[0]));
        this.metricSelector.setSelectedIndex(2); // Net Production, matches
                                                  // the Trade Advisor default
        this.metricSelector.setRenderer(labelRenderer());
        this.metricSelector.addActionListener(
            (ActionEvent ae) -> updateChart());

        this.chart = new HistoryLineChart(freeColClient);

        reportPanel.setLayout(new MigLayout("wrap 1, gap 0 10",
                                            "[fill]", "[][fill, grow]"));
        updateChart();
    }

    /**
     * LarryDGray's Mods: FreeColComboBoxRenderer's default
     * setLabelValues() only knows how to label Integer/Language/
     * String/Named/ObjectWithId/InetAddress/MixerWrapper values, so a
     * plain object renders as a blank row without this - both combo
     * boxes on this panel just need their toString() shown, so share
     * one renderer.
     *
     * @return The renderer.
     */
    private static <T> FreeColComboBoxRenderer<T> labelRenderer() {
        return new FreeColComboBoxRenderer<T>() {
            @Override
            protected void setLabelValues(JLabel c, T value) {
                c.setText((value == null) ? null : value.toString());
            }
        };
    }

    /**
     * Rebuild the chart for the selected goods group and metric.
     */
    private void updateChart() {
        GoodsGroup group = (GoodsGroup)this.groupSelector.getSelectedItem();
        MetricEntry metric = (MetricEntry)this.metricSelector.getSelectedItem();
        List<Series> series = new ArrayList<>();
        if (group != null && metric != null) {
            final int n = this.history.size();
            int[] turns = new int[n];
            for (int i = 0; i < n; i++) turns[i] = this.history.get(i).turn;

            int colorIndex = 0;
            for (GoodsType gt : group.goods) {
                final String id = gt.getId();
                double[] values = new double[n];
                for (int i = 0; i < n; i++) {
                    values[i] = metric.valuesOf.apply(this.history.get(i))
                        .getOrDefault(id, 0);
                }
                series.add(new Series(Messages.getName(gt),
                    PALETTE[colorIndex++ % PALETTE.length], turns, values, false));
            }
        }
        this.chart.setSeries(series);

        reportPanel.removeAll();
        JPanel selectors = new MigPanel(new MigLayout("insets 0, gap 10 0",
                                                       "[][][][]", "[]"));
        selectors.add(Utility.localizedLabel("report.tradeHistory.selectGoods"));
        selectors.add(this.groupSelector);
        selectors.add(Utility.localizedLabel("report.tradeHistory.selectMetric"));
        selectors.add(this.metricSelector);
        reportPanel.add(selectors);
        reportPanel.add(this.chart, "grow, push");
        reportPanel.revalidate();
        reportPanel.repaint();
    }
}
