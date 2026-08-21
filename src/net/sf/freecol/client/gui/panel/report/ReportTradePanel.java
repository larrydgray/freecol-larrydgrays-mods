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

import static net.sf.freecol.common.util.CollectionUtils.transform;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.label.MarketLabel;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ExportData;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TypeCountMap;
import net.sf.freecol.common.model.Unit;


/**
 * This panel displays the Trade Report.
 */
public final class ReportTradePanel extends ReportPanel {

    /**
     * LarryDGray's Mods: which single value the Compact View shows
     * per colony/goods cell.
     */
    private static enum TradeDisplayMode { ON_HAND, PRODUCTION, NET_PRODUCTION }

    private List<Colony> colonies;

    /** LarryDGray's Mods: the goods column last clicked to sort by,
     *  and whether the second click (sort by total on hand instead
     *  of production) has happened for it. */
    private GoodsType lastSortColumn = null;
    private boolean sortByTotal = false;

    /** LarryDGray's Mods: which value Compact View currently shows. */
    private TradeDisplayMode displayMode = TradeDisplayMode.ON_HAND;


    /**
     * The constructor that will add the items to this panel.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportTradePanel(FreeColClient freeColClient) {
        super(freeColClient, "reportTradeAction");

        this.colonies = getMyPlayer().getColonyList();
        buildPanel(freeColClient, null);
    }

    /**
     * LarryDGray's Mods: handle a click on a goods column header.
     * The first click on a column sorts by net production
     * (highest first); clicking the same column again toggles to
     * sorting by total goods on hand instead; clicking a different
     * column starts over at production for the new column.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param goodsType The {@code GoodsType} whose column was clicked.
     */
    private void sortColumnClicked(FreeColClient freeColClient,
                                   GoodsType goodsType) {
        this.sortByTotal = (goodsType == this.lastSortColumn)
            && !this.sortByTotal;
        this.lastSortColumn = goodsType;
        buildPanel(freeColClient, goodsType);
    }

    /**
     * LarryDGray's Mods: handle a click on the City Size column
     * header - sorts colonies by population, highest first. Not a
     * goods type, so this bypasses sortColumnClicked's GoodsType-based
     * mechanism entirely: sort directly, then rebuild with no pending
     * goods-column sort.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    private void sortByCitySizeClicked(FreeColClient freeColClient) {
        this.lastSortColumn = null;
        this.colonies.sort(Comparator.comparingInt(Colony::getUnitCount)
            .reversed().thenComparing(Colony::getName));
        buildPanel(freeColClient, null);
    }

    /**
     * LarryDGray's Mods: handle a click on one of the Compact View
     * On Hand / Production / Net Production buttons.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param mode The {@code TradeDisplayMode} to switch to.
     */
    private void displayModeClicked(FreeColClient freeColClient,
                                    TradeDisplayMode mode) {
        this.displayMode = mode;
        buildPanel(freeColClient, null);
    }

    /**
     * LarryDGray's Mods: (re)builds the whole report, optionally
     * sorting the colonies by net production (or, on a second click,
     * total on hand) of one goods type, highest first, so clicking a
     * column header shows at a glance which colonies lead in that
     * good.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param sortBy The {@code GoodsType} to sort colonies by, or
     *     null to leave the current order unchanged.
     */
    private void buildPanel(FreeColClient freeColClient, GoodsType sortBy) {
        // LarryDGray's Mods: Compact View - one row per colony
        // (switchable On Hand / Production / Net Production, zero
        // amounts left blank) plus Liberty Bells and Crosses columns,
        // instead of the default two rows (on hand and net
        // production together, zeroes shown).
        final boolean compactView = freeColClient.getClientOptions()
            .getBoolean(ClientOptions.TRADE_ADVISOR_COMPACT_VIEW);

        if (sortBy != null) {
            // LarryDGray's Mods: in Compact View, sort by whichever
            // value is currently displayed (On Hand / Production /
            // Net Production), matching the buttons, rather than the
            // stock two-way total/production toggle.
            final GoodsType finalSortBy = sortBy;
            Comparator<Colony> byValue;
            if (compactView) {
                switch (this.displayMode) {
                case PRODUCTION:
                    // LarryDGray's Mods: match the Food-production fix
                    // in the value display below - a plain
                    // getTotalProductionOf(food) always reads zero.
                    byValue = Comparator.comparingInt((Colony c)
                        -> (finalSortBy.isFoodType()) ? c.getFoodProduction()
                            : c.getTotalProductionOf(finalSortBy));
                    break;
                case ON_HAND:
                    // LarryDGray's Mods: Liberty Bells / Crosses have
                    // no real warehouse count (not storable) - match
                    // the value display below, which uses liberty/
                    // immigration points instead.
                    byValue = Comparator.comparingInt((Colony c)
                        -> (!finalSortBy.isStorable())
                            ? ("model.goods.bells".equals(finalSortBy.getId())
                                ? c.getLiberty() : c.getImmigration())
                            : c.getGoodsCount(finalSortBy));
                    break;
                case NET_PRODUCTION: default:
                    byValue = Comparator.comparingInt(
                        (Colony c) -> c.getNetProductionOf(finalSortBy));
                    break;
                }
            } else {
                // Break ties by name so that repeated clicks always give a
                // clean, deterministic order instead of leftover order from
                // whichever column was sorted previously.
                byValue = (this.sortByTotal)
                    ? Comparator.comparingInt(
                        (Colony c) -> c.getGoodsCount(finalSortBy))
                    : Comparator.comparingInt(
                        (Colony c) -> c.getNetProductionOf(finalSortBy));
            }
            this.colonies.sort(byValue.reversed()
                .thenComparing(Colony::getName));
        }

        final Player player = getMyPlayer();
        Color warnColor = ImageLibrary.getColor("color.report.trade.warn");

        header.setText(Messages.message(compactView
            ? "report.trade.productionTradeTitle" : "reportTradeAction.name"));

        JPanel goodsHeader = new MigPanel("ReportPanelUI");
        goodsHeader.setBorder(new EmptyBorder(20, 20, 0, 20));
        scrollPane.setColumnHeaderView(goodsHeader);

        final Specification spec = getSpecification();
        List<GoodsType> storableGoods = spec.getStorableGoodsTypeList();
        List<GoodsType> extraGoods = new ArrayList<>();
        if (compactView) {
            GoodsType bells = spec.getGoodsType("model.goods.bells");
            GoodsType crosses = spec.getGoodsType("model.goods.crosses");
            if (bells != null) extraGoods.add(bells);
            if (crosses != null) extraGoods.add(crosses);
        }
        List<GoodsType> allColumns = new ArrayList<>(storableGoods);
        allColumns.addAll(extraGoods);
        Market market = player.getMarket();

        // Display Panel
        reportPanel.removeAll();
        goodsHeader.removeAll();

        String layoutConstraints = "insets 0, gap 0 0";
        // LarryDGray's Mods: the City Size column (column 1) gets its
        // own, wider fixed width - "City Size" as text is much wider
        // than the icon-sized goods columns that follow it, and would
        // otherwise overlap the next column's header/values.
        String columnConstraints = "[25%!, fill]["
            + (int)Math.round(ImageLibrary.ICON_SIZE.width * 2.5)
            + "!, fill]["
            + (int)Math.round(ImageLibrary.ICON_SIZE.width * 1.25)
            + "!, fill]";
        String rowConstraints = "[fill]";

        reportPanel.setLayout(new MigLayout(layoutConstraints,
                                            columnConstraints, rowConstraints));
        goodsHeader.setLayout(new MigLayout(layoutConstraints,
                                            columnConstraints, rowConstraints));
        goodsHeader.setOpaque(true);

        if (compactView) {
            goodsHeader.add(createDisplayModeButtons(freeColClient), "cell 0 0");
        } else {
            JLabel emptyLabel = new JLabel();
            emptyLabel.setBorder(Utility.getTopLeftCellBorder());
            goodsHeader.add(emptyLabel, "cell 0 0");
        }

        /**
         * Total Units Sold by Player
         */
        JLabel jl = createLeftLabel("report.trade.unitsSold");
        jl.setBorder(Utility.getTopLeftCellBorder());
        reportPanel.add(jl, "cell 0 0");
        reportPanel.add(createLeftLabel("report.trade.beforeTaxes"), "cell 0 1");
        reportPanel.add(createLeftLabel("report.trade.afterTaxes"), "cell 0 2");
        reportPanel.add(createLeftLabel("report.trade.cargoUnits"), "cell 0 3");
        reportPanel.add(createLeftLabel("report.trade.totalUnits"), "cell 0 4");
        reportPanel.add(createLeftLabel("report.trade.totalDelta"), "cell 0 5");

        TypeCountMap<GoodsType> totalUnits = new TypeCountMap<>();
        TypeCountMap<GoodsType> deltaUnits = new TypeCountMap<>();
        TypeCountMap<GoodsType> cargoUnits = new TypeCountMap<>();

        for (Unit unit : transform(player.getUnits(), Unit::isCarrier)) {
            for (Goods goods : unit.getCompactGoodsList()) {
                cargoUnits.incrementCount(goods.getType(), goods.getAmount());
                totalUnits.incrementCount(goods.getType(), goods.getAmount());
            }
        }

        // LarryDGray's Mods: City Size column - not a trade item (no
        // GoodsType backing it), so it's built separately from the
        // allColumns loop below. Placed first, immediately after the
        // colony name column and before any goods column, per Larry's
        // request.
        final int citySizeColumn = 1;
        JLabel citySizeHeader = Utility.localizedLabel("report.trade.citySizeShort");
        citySizeHeader.setBorder(Utility.getTopCellBorder());
        // LarryDGray's Mods: short text in the header itself - a
        // narrow column sized for icon-width goods columns has no
        // room for the full "City Size" phrase - with the full name
        // always available as a tooltip.
        citySizeHeader.setToolTipText(Messages.message("report.trade.citySize"));
        if (freeColClient.getClientOptions()
                .getBoolean(ClientOptions.ENABLE_TRADE_ADVISOR_SORT)) {
            citySizeHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            citySizeHeader.setToolTipText(
                Messages.message("report.trade.sortByCitySize"));
            citySizeHeader.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    sortByCitySizeClicked(freeColClient);
                }
            });
        }
        goodsHeader.add(citySizeHeader);

        int column = citySizeColumn;
        for (GoodsType goodsType : allColumns) {
            column++;
            final boolean isMarketGood = storableGoods.contains(goodsType);

            if (isMarketGood) {
                int sales = player.getSales(goodsType);
                int beforeTaxes = player.getIncomeBeforeTaxes(goodsType);
                int afterTaxes = player.getIncomeAfterTaxes(goodsType);
                MarketLabel marketLabel = new MarketLabel(freeColClient, goodsType, market)
                    .addBorder();
                // LarryDGray's Mods: click a column header to sort the
                // colonies below by net production of that good (click
                // again to sort by total on hand instead), highest first.
                if (freeColClient.getClientOptions()
                        .getBoolean(ClientOptions.ENABLE_TRADE_ADVISOR_SORT)) {
                    marketLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    marketLabel.setToolTipText(
                        Messages.message("report.trade.sortByProduction"));
                    marketLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            sortColumnClicked(freeColClient, goodsType);
                        }
                    });
                }
                goodsHeader.add(marketLabel);

                jl = createNumberLabelBlankZero(sales);
                jl.setBorder(Utility.getTopCellBorder());
                reportPanel.add(jl, "cell " + column + " 0");
                reportPanel.add(createNumberLabelBlankZero(beforeTaxes),
                                "cell " + column + " 1");
                reportPanel.add(createNumberLabelBlankZero(afterTaxes),
                                "cell " + column + " 2");
                reportPanel.add(createNumberLabelBlankZero(cargoUnits.getCount(goodsType)),
                                "cell " + column + " 3");
            } else {
                // LarryDGray's Mods: Liberty Bells / Crosses - not
                // tradeable, so no market price/sales figures, just
                // an icon header. Still sortable like every other
                // column, matching the behavior below.
                JLabel goodsTypeHeader = createGoodsTypeHeaderLabel(goodsType);
                if (freeColClient.getClientOptions()
                        .getBoolean(ClientOptions.ENABLE_TRADE_ADVISOR_SORT)) {
                    goodsTypeHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    goodsTypeHeader.setToolTipText(
                        Messages.message("report.trade.sortByProduction"));
                    goodsTypeHeader.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            sortColumnClicked(freeColClient, goodsType);
                        }
                    });
                }
                goodsHeader.add(goodsTypeHeader);
            }
        }

        int row = 6;
        boolean first = true;
        for (Colony colony : colonies) {
            for (GoodsType goodsType : getSpecification().getGoodsTypeList()) {
                deltaUnits.incrementCount(goodsType, colony.getNetProductionOf(goodsType));
            }
            for (Goods goods : colony.getCompactGoodsList()) {
                totalUnits.incrementCount(goods.getType(), goods.getAmount());
            }
            JButton colonyButton = createColonyButton(colony);
            if (colony.hasAbility(Ability.EXPORT)) {
                colonyButton.setText(colonyButton.getText() + "*");
            }
            colonyButton.setBorder((first) ? Utility.getTopLeftCellBorder()
                : Utility.getLeftCellBorder());
            reportPanel.add(colonyButton, "cell 0 " + row
                + " 1 " + (compactView ? 1 : 2));

            JLabel citySizeLabel = createNumberLabel(colony.getUnitCount());
            citySizeLabel.setBorder((first) ? Utility.getTopCellBorder()
                : Utility.getCellBorder());
            reportPanel.add(citySizeLabel, "cell " + citySizeColumn + " " + row
                + " 1 " + (compactView ? 1 : 2));

            column = citySizeColumn;
            for (GoodsType goodsType : allColumns) {
                column++;
                final boolean isMarketGood = storableGoods.contains(goodsType);

                if (compactView) {
                    int value;
                    boolean showSign = false;
                    switch (this.displayMode) {
                    case PRODUCTION:
                        // LarryDGray's Mods: Food is never produced
                        // "as itself" - farmers/fishermen produce
                        // grain/fish, which is only aggregated into
                        // food storage-side (Colony.getNetProductionOf
                        // resolves this via storedAs; the raw
                        // per-work-location getTotalProductionOf does
                        // not) - so a plain getTotalProductionOf(food)
                        // always reads zero. getFoodProduction() is
                        // FreeCol's own existing workaround for this
                        // exact case (see Colony.getFoodProduction()).
                        value = (goodsType.isFoodType())
                            ? colony.getFoodProduction()
                            : colony.getTotalProductionOf(goodsType);
                        break;
                    case NET_PRODUCTION:
                        value = colony.getNetProductionOf(goodsType);
                        showSign = true;
                        break;
                    case ON_HAND: default:
                        value = (isMarketGood) ? colony.getGoodsCount(goodsType)
                            : ("model.goods.bells".equals(goodsType.getId()))
                            ? colony.getLiberty()
                            : colony.getImmigration();
                        break;
                    }
                    JLabel valueLabel = createNumberLabel(value, showSign);
                    if (value == 0) valueLabel.setText("");
                    valueLabel.setBorder((first) ? Utility.getTopCellBorder()
                        : Utility.getCellBorder());
                    if (this.displayMode == TradeDisplayMode.ON_HAND) {
                        valueLabel.setForeground(ImageLibrary
                            .getGoodsColor(goodsType, value, colony));
                    }
                    if (this.displayMode == TradeDisplayMode.NET_PRODUCTION) {
                        Collection<StringTemplate> warnings
                            = colony.getProductionWarnings(goodsType);
                        if (!warnings.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (StringTemplate warning : warnings) {
                                sb.append(Messages.message(warning)).append(' ');
                            }
                            sb.setLength(sb.length()-1);
                            valueLabel.setToolTipText(sb.toString());
                            valueLabel.setForeground(warnColor);
                        }
                    }
                    if (isMarketGood) {
                        ExportData ed = colony.getExportData(goodsType);
                        if (ed.getExported()
                            && this.displayMode == TradeDisplayMode.ON_HAND) {
                            valueLabel.setToolTipText(Messages.message(StringTemplate
                                    .template("report.trade.export")
                                    .addNamed("%goods%", goodsType)
                                    .addAmount("%amount%", ed.getExportLevel())));
                        }
                    }
                    reportPanel.add(valueLabel, "cell " + column + " " + row);

                } else if (isMarketGood) {
                    // Original two-row layout, unchanged.
                    int amount = colony.getGoodsCount(goodsType);
                    JLabel goodsLabel = new JLabel(String.valueOf(amount),
                                                   JLabel.TRAILING);
                    goodsLabel.setBorder((first) ? Utility.getTopCellBorder()
                        : Utility.getCellBorder());
                    goodsLabel.setForeground(ImageLibrary.getGoodsColor(goodsType, amount,
                                                                 colony));
                    ExportData ed = colony.getExportData(goodsType);
                    if (ed.getExported()) {
                        goodsLabel.setToolTipText(Messages.message(StringTemplate
                                .template("report.trade.export")
                                .addNamed("%goods%", goodsType)
                                .addAmount("%amount%", ed.getExportLevel())));
                    }
                    reportPanel.add(goodsLabel, "cell " + column + " " + row);

                    int production = colony.getNetProductionOf(goodsType);
                    JLabel productionLabel = createNumberLabel(production, true);
                    productionLabel.setForeground(ImageLibrary.getGoodsColor(goodsType,
                            production, colony));
                    Collection<StringTemplate> warnings
                        = colony.getProductionWarnings(goodsType);
                    if (!warnings.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (StringTemplate warning : warnings) {
                            sb.append(Messages.message(warning))
                                .append(' ');
                        }
                        sb.setLength(sb.length()-1);
                        productionLabel.setToolTipText(sb.toString());
                        productionLabel.setForeground(warnColor);
                    }
                    reportPanel.add(productionLabel,
                                    "cell " + column + " " + (row + 1));
                }
            }

            row += (compactView) ? 1 : 2;
            first = false;
        }

        row++;
        reportPanel.add(Utility.localizedLabel("report.trade.hasCustomHouse"),
                        "cell 0 " + row + ", span");

        column = citySizeColumn;
        for (GoodsType goodsType : storableGoods) {
            column++;
            reportPanel.add(createNumberLabelBlankZero(totalUnits.getCount(goodsType)),
                            "cell " + column + " 4");
            reportPanel.add(createNumberLabelBlankZero(deltaUnits.getCount(goodsType), true),
                            "cell " + column + " 5, wrap 20");
        }
    }

    /**
     * LarryDGray's Mods: build the On Hand / Production / Net
     * Production button strip shown in Compact View, above the
     * colony-name column.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @return The button panel.
     */
    private JPanel createDisplayModeButtons(FreeColClient freeColClient) {
        JPanel panel = new MigPanel("ReportPanelUI");
        panel.setOpaque(false);
        panel.setLayout(new MigLayout("wrap 1, insets 2, gap 1 1"));
        panel.add(createDisplayModeButton(freeColClient,
            "report.trade.onHand", TradeDisplayMode.ON_HAND));
        panel.add(createDisplayModeButton(freeColClient,
            "report.trade.production", TradeDisplayMode.PRODUCTION));
        panel.add(createDisplayModeButton(freeColClient,
            "report.trade.netProduction", TradeDisplayMode.NET_PRODUCTION));
        return panel;
    }

    private JButton createDisplayModeButton(FreeColClient freeColClient,
                                            String messageKey,
                                            TradeDisplayMode mode) {
        JButton button = new JButton(Messages.message(messageKey));
        button.setFont(button.getFont().deriveFont(
            (this.displayMode == mode) ? Font.BOLD : Font.PLAIN));
        button.setEnabled(this.displayMode != mode);
        button.addActionListener((ActionEvent ae) ->
            displayModeClicked(freeColClient, mode));
        return button;
    }

    /**
     * LarryDGray's Mods: a simple icon-only column header for a
     * goods type with no market entry (Liberty Bells, Crosses).
     *
     * @param goodsType The {@code GoodsType} to label.
     * @return The header label.
     */
    private JLabel createGoodsTypeHeaderLabel(GoodsType goodsType) {
        JLabel label = new JLabel(new ImageIcon(
            getImageLibrary().getScaledGoodsTypeImage(goodsType)));
        label.setToolTipText(Messages.getName(goodsType));
        label.setBorder(Utility.getTopCellBorder());
        label.setVerticalTextPosition(JLabel.BOTTOM);
        label.setHorizontalTextPosition(JLabel.CENTER);
        return label;
    }

    private JLabel createLeftLabel(String key) {
        JLabel result = Utility.localizedLabel(key);
        result.setBorder(Utility.getLeftCellBorder());
        return result;
    }

    private JLabel createNumberLabel(int value) {
        return createNumberLabel(value, false);
    }

    private JLabel createNumberLabel(int value, boolean alwaysAddSign) {
        JLabel result = new JLabel(String.valueOf(value), JLabel.TRAILING);
        result.setBorder(Utility.getCellBorder());
        if (value < 0) {
            result.setForeground(Color.RED);
        } else if (alwaysAddSign && value > 0) {
            result.setText("+" + value);
        }
        return result;
    }

    /**
     * LarryDGray's Mods: like {@link #createNumberLabel(int)}, but a
     * zero value renders as a blank cell instead of "0" - for the
     * summary rows (units sold, income, cargo, totals), where most
     * cells are zero for most goods and the zeroes just add clutter.
     *
     * @param value The value to display.
     * @return The label.
     */
    private JLabel createNumberLabelBlankZero(int value) {
        return createNumberLabelBlankZero(value, false);
    }

    private JLabel createNumberLabelBlankZero(int value, boolean alwaysAddSign) {
        JLabel result = createNumberLabel(value, alwaysAddSign);
        if (value == 0) result.setText("");
        return result;
    }
}
