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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.HistoryLineChart;
import net.sf.freecol.client.gui.panel.HistoryLineChart.Series;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.client.gui.report.NationHistory;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Player;


/**
 * LarryDGray's Mods: displays the full-timeline Nation Comparison
 * Report - one stat (settlements, units, military/naval strength, or
 * gold, plus, when Jan de Witt is in Congress, Sons of Liberty%,
 * Founding Fathers, or tax rate) plotted for every live European
 * nation over every turn sampled so far this session.
 */
public final class ReportNationComparisonPanel extends ReportPanel {

    /**
     * One selectable stat, with its own label key and how to pull it
     * out of a recorded {@link NationHistory.NationSample}.
     */
    private enum Stat {
        SETTLEMENTS("report.nationComparison.settlements") {
            @Override int value(NationHistory.NationSample s) {
                return s.numberOfSettlements;
            }
        },
        UNITS("report.nationComparison.units") {
            @Override int value(NationHistory.NationSample s) {
                return s.numberOfUnits;
            }
        },
        MILITARY("report.nationComparison.military") {
            @Override int value(NationHistory.NationSample s) {
                return s.militaryStrength;
            }
        },
        NAVAL("report.nationComparison.naval") {
            @Override int value(NationHistory.NationSample s) {
                return s.navalStrength;
            }
        },
        GOLD("report.nationComparison.gold") {
            @Override int value(NationHistory.NationSample s) {
                return s.gold;
            }
        },
        SOL("report.nationComparison.sonsOfLiberty") {
            @Override int value(NationHistory.NationSample s) {
                return s.soL;
            }
        },
        FOUNDING_FATHERS("report.nationComparison.foundingFathers") {
            @Override int value(NationHistory.NationSample s) {
                return s.foundingFathers;
            }
        },
        TAX("report.nationComparison.tax") {
            @Override int value(NationHistory.NationSample s) {
                return s.tax;
            }
        };

        private final String messageKey;

        Stat(String messageKey) {
            this.messageKey = messageKey;
        }

        abstract int value(NationHistory.NationSample s);

        @Override
        public String toString() {
            return Messages.message(this.messageKey);
        }
    }

    private final JComboBox<Stat> statSelector;
    private final HistoryLineChart chart;


    /**
     * The constructor that will add the items to this panel.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportNationComparisonPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportNationComparisonAction");

        // LarryDGray's Mods: SoL/Founding Fathers/Tax mirror the same
        // gate NationSummary itself applies to those three fields -
        // only meaningful once Jan de Witt is in Congress.
        List<Stat> stats = new ArrayList<>(List.of(
            Stat.SETTLEMENTS, Stat.UNITS, Stat.MILITARY, Stat.NAVAL,
            Stat.GOLD));
        if (getMyPlayer().hasAbility(
                Ability.BETTER_FOREIGN_AFFAIRS_REPORT)) {
            stats.add(Stat.SOL);
            stats.add(Stat.FOUNDING_FATHERS);
            stats.add(Stat.TAX);
        }
        this.statSelector = new JComboBox<>(stats.toArray(new Stat[0]));
        // LarryDGray's Mods: FreeColComboBoxRenderer's default
        // setLabelValues() only knows how to label Integer/Language/
        // String/Named/ObjectWithId/InetAddress/MixerWrapper values,
        // so a plain enum renders as a blank row without this.
        this.statSelector.setRenderer(new FreeColComboBoxRenderer<Stat>() {
            @Override
            protected void setLabelValues(JLabel c, Stat value) {
                c.setText((value == null) ? null : value.toString());
            }
        });
        this.statSelector.addActionListener(
            (ActionEvent ae) -> updateChart());
        this.chart = new HistoryLineChart(freeColClient);

        reportPanel.setLayout(new MigLayout("wrap 1, gap 0 10",
                                            "[fill]", "[][][fill, grow]"));
        reportPanel.add(Utility.localizedLabel(
            "report.nationComparison.selectStat"));
        reportPanel.add(this.statSelector);
        reportPanel.add(this.chart, "grow, push");

        updateChart();
    }


    /**
     * Rebuild the chart's series - one per live European nation with
     * recorded history - from the selected stat.
     */
    private void updateChart() {
        Stat stat = (Stat)this.statSelector.getSelectedItem();
        List<Series> series = new ArrayList<>();
        if (stat != null) {
            for (Player player : getGame().getLiveEuropeanPlayerList()) {
                List<NationHistory.NationSample> history = igc()
                    .getNationHistory().getHistory(player);
                if (history.isEmpty()) continue;
                final int n = history.size();
                int[] turns = new int[n];
                double[] values = new double[n];
                for (int i = 0; i < n; i++) {
                    NationHistory.NationSample s = history.get(i);
                    turns[i] = s.turn;
                    values[i] = stat.value(s);
                }
                series.add(new Series(
                    Messages.message(player.getNationLabel()),
                    player.getNation().getColor(), turns, values));
            }
        }
        this.chart.setSeries(series);
    }
}
