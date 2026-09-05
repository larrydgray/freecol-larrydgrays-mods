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

import java.awt.Color;
import java.awt.Component;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.label.GoodsLabel;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ExportData;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.GameOptions;


/**
 * A dialog to display a colony warehouse.
 */
public final class WarehouseDialog extends FreeColConfirmDialog {

    private static final Logger logger = Logger.getLogger(WarehouseDialog.class.getName());

    /**
     * LarryDGray's Mods: goods types the "Setting 1" Custom House
     * preset button drives all the way down to an export level of 0
     * (luxury/manufactured goods with no domestic use once made).
     */
    private static final Set<String> SETTING1_ZERO_EXPORT_GOODS = Set.of(
        "model.goods.rum", "model.goods.cigars", "model.goods.cloth",
        "model.goods.coats", "model.goods.silver");

    /**
     * LarryDGray's Mods: goods types the "Setting 1" preset keeps
     * buffered at (warehouse capacity - 50) instead - raw materials
     * still needed for domestic manufacturing, plus Horses/Muskets/
     * Tools still needed to equip units.
     */
    private static final Set<String> SETTING1_BUFFERED_EXPORT_GOODS = Set.of(
        "model.goods.sugar", "model.goods.cotton", "model.goods.furs",
        "model.goods.tobacco", "model.goods.lumber", "model.goods.ore",
        "model.goods.horses", "model.goods.muskets", "model.goods.tools");

    private JPanel warehousePanel;


    /**
     * Creates a dialog to display the warehouse.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param frame The owner frame.
     * @param colony The {@code Colony} containing the warehouse.
     */
    public WarehouseDialog(FreeColClient freeColClient, JFrame frame,
            Colony colony) {
        super(freeColClient, frame);

        warehousePanel = new MigPanel(new MigLayout("wrap 4"));
        warehousePanel.setOpaque(false);
        for (GoodsType type : freeColClient.getGame().getSpecification()
                 .getStorableGoodsTypeList()) {
            warehousePanel.add(new WarehouseGoodsPanel(freeColClient,
                                                       colony, type));
        }

        JScrollPane scrollPane = new JScrollPane(warehousePanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        JPanel panel = new MigPanel(new MigLayout("fill, wrap 1", "", ""));
        panel.add(Utility.localizedHeader(warehouseHeaderKey(colony),
                                          Utility.FONTSPEC_TITLE),
                  "align center");

        // LarryDGray's Mods: one-click Custom House export preset,
        // only meaningful (and only enabled) once the colony can
        // actually export at all. Own dedicated toggle, per
        // feedback_freecol_mods_must_be_switchable.
        if (freeColClient.getClientOptions()
                .getBoolean(ClientOptions.SHOW_WAREHOUSE_SETTING1_BUTTON)) {
            JButton setting1Button = new JButton(
                Messages.message("warehouseDialog.setting1"));
            Utility.localizeToolTip(setting1Button,
                "warehouseDialog.setting1.shortDescription");
            setting1Button.setEnabled(colony.hasAbility(Ability.EXPORT));
            setting1Button.addActionListener(ae -> applySetting1());
            panel.add(setting1Button, "align center");
        }

        panel.add(scrollPane, "grow");
        panel.setSize(panel.getPreferredSize());

        ImageIcon icon = new ImageIcon(
            getImageLibrary().getSmallSettlementImage(colony));
        initializeConfirmDialog(frame, true, panel, icon, "ok", "cancel");
    }

    /**
     * Get the message key for this dialog's header, reflecting the
     * colony's actual warehouse-chain building tier (Depot/Warehouse/
     * Warehouse Expansion) rather than a single generic "Warehouse"
     * label regardless of what is actually built.
     *
     * @param colony The {@code Colony} whose warehouse is being shown.
     * @return The header message key to use.
     */
    private static String warehouseHeaderKey(Colony colony) {
        Specification spec = colony.getSpecification();
        BuildingType depotType = spec.getBuildingType("model.building.depot");
        Building building = (depotType == null) ? null
            : colony.getBuilding(depotType);
        if (building == null) return Messages.nameKey("warehouseDialog");
        switch (building.getType().getId()) {
        case "model.building.depot":
            return Messages.nameKey("warehouseDialog.depot");
        case "model.building.warehouseExpansion":
            return Messages.nameKey("warehouseDialog.expanded");
        default: // model.building.warehouse -- the plain default label
            return Messages.nameKey("warehouseDialog");
        }
    }


    /**
     * LarryDGray's Mods: apply the "Setting 1" Custom House export
     * preset to every goods panel currently shown - check Export and
     * set an export level for every goods type except Food and Trade
     * Goods, which are left untouched. Only changes the on-screen
     * spinner/checkbox state; the usual OK/Cancel + saveSettings()
     * flow still decides whether any of it is actually kept.
     */
    private void applySetting1() {
        for (Component c : warehousePanel.getComponents()) {
            if (!(c instanceof WarehouseGoodsPanel)) continue;
            WarehouseGoodsPanel goodsPanel = (WarehouseGoodsPanel)c;
            String id = goodsPanel.goodsType.getId();
            int level;
            if (SETTING1_ZERO_EXPORT_GOODS.contains(id)) {
                level = 0;
            } else if (SETTING1_BUFFERED_EXPORT_GOODS.contains(id)) {
                level = Math.max(0,
                    goodsPanel.colony.getWarehouseCapacity() - 50);
            } else {
                continue; // Food, Trade Goods -- left untouched
            }
            goodsPanel.export.setSelected(true);
            goodsPanel.exportLevel.setValue(level);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean getResponse() {
        Boolean result = super.getResponse();
        if (result) {
            for (Component c : warehousePanel.getComponents()) {
                if (c instanceof WarehouseGoodsPanel) {
                    ((WarehouseGoodsPanel)c).saveSettings();
                }
            }
        }
        warehousePanel = null;
        return result;
    }


    private class WarehouseGoodsPanel extends MigPanel {

        private final Colony colony;

        private final GoodsType goodsType;

        private final JCheckBox export;

        /**
         * LarryDGray's Mods: redirect this goods type's warehouse
         * overflow into an idle carrier instead of wasting it.
         */
        private final JCheckBox overflowToCarrier;

        private final JSpinner lowLevel;

        private final JSpinner highLevel;

        private final JSpinner importLevel;
        
        private final JSpinner exportLevel;


        public WarehouseGoodsPanel(FreeColClient freeColClient, Colony colony,
                                   GoodsType goodsType) {
            super("WarehouseGoodsPanelUI", new MigLayout("wrap 2", "", ""));

            final boolean enhancedTradeRoutes = colony.getSpecification()
                .getBoolean(GameOptions.ENHANCED_TRADE_ROUTES);
            this.colony = colony;
            this.goodsType = goodsType;
            final int capacity = colony.getWarehouseCapacity();
            final int maxCapacity = 300; // TODO: magic number
            
            setOpaque(false);
            setBorder(Utility.localizedBorder(goodsType, new Color(0)));
            Utility.padBorder(this, 6,6,6,6);

            ExportData exportData = colony.getExportData(goodsType);

            // goods label
            Goods goods = new Goods(colony.getGame(), colony, goodsType,
                                    colony.getGoodsCount(goodsType));
            GoodsLabel goodsLabel = new GoodsLabel(freeColClient, goods);
            goodsLabel.setHorizontalAlignment(JLabel.LEADING);
            add(goodsLabel, "span 1 2");

            // low level settings
            String str;
            SpinnerNumberModel lowLevelModel
                = new SpinnerNumberModel(exportData.getLowLevel(), 0, 100, 1);
            lowLevel = new JSpinner(lowLevelModel);
            Utility.localizeToolTip(lowLevel,
                "warehouseDialog.lowLevel.shortDescription");
            add(lowLevel);

            // high level settings
            SpinnerNumberModel highLevelModel
                = new SpinnerNumberModel(exportData.getHighLevel(), 0, 100, 1);
            highLevel = new JSpinner(highLevelModel);
            Utility.localizeToolTip(highLevel,
                "warehouseDialog.highLevel.shortDescription");
            add(highLevel);

            // LarryDGray's Mods: export checkbox before the import
            // level spinner (swapped from upstream FreeCol's order)
            // so this row reads checkbox-then-spinner, matching the
            // Overflow to Carrier row below it.
            export = new JCheckBox(Messages.message("warehouseDialog.export"),
                                   exportData.getExported());
            Utility.localizeToolTip(export,
                "warehouseDialog.export.shortDescription");
            if (!colony.hasAbility(Ability.EXPORT)) {
                export.setEnabled(false);
            }
            add(export);

            if (enhancedTradeRoutes) { // import level settings
                int importInit = exportData.getEffectiveImportLevel(capacity);
                SpinnerNumberModel importLevelModel
                    = new SpinnerNumberModel(importInit, 0,
                        (goodsType.limitIgnored()) ? maxCapacity : capacity, 1);
                importLevel = new JSpinner(importLevelModel);
                Utility.localizeToolTip(importLevel,
                    "warehouseDialog.importLevel.shortDescription");
                add(importLevel);
            } else {
                importLevel = null;
            }

            // LarryDGray's Mods: overflow-to-carrier checkbox, one per
            // goods type, gated by the master Game Option so an
            // inert checkbox reads as visibly disabled rather than
            // silently doing nothing. A continued save's embedded spec
            // may predate this option entirely - hasOption() first
            // avoids spec.getBoolean() throwing on old saves. On the
            // primary food type's panel this checkbox does double
            // duty: Food is normally exempt from warehouse capacity
            // so it can accumulate to a new colonist's required
            // amount, but turning this on for Food caps it one below
            // that requirement instead and redirects the surplus -
            // which, as a side effect, also stops new colonists from
            // being spawned. Larry's own realization: no separate
            // "pause growth" checkbox needed, see
            // ServerColony.csNewTurnWarnings()/csNewTurn().
            final Specification spec = colony.getSpecification();
            final boolean warehouseOverflowEnabled =
                spec.hasOption(GameOptions.ENABLE_WAREHOUSE_OVERFLOW, BooleanOption.class)
                && spec.getBoolean(GameOptions.ENABLE_WAREHOUSE_OVERFLOW);
            overflowToCarrier = new JCheckBox(
                Messages.message("warehouseDialog.overflowToCarrier"),
                exportData.isOverflowToCarrier());
            Utility.localizeToolTip(overflowToCarrier,
                (goodsType == spec.getPrimaryFoodType())
                    ? "warehouseDialog.overflowToCarrier.food.shortDescription"
                    : "warehouseDialog.overflowToCarrier.shortDescription");
            overflowToCarrier.setEnabled(warehouseOverflowEnabled);
            add(overflowToCarrier);

            // export level settings
            SpinnerNumberModel exportLevelModel
                = new SpinnerNumberModel(exportData.getExportLevel(), 0,
                    (goodsType.limitIgnored()) ? maxCapacity : capacity, 1);
            exportLevel = new JSpinner(exportLevelModel);
            Utility.localizeToolTip(exportLevel,
                "warehouseDialog.exportLevel.shortDescription");
            add(exportLevel);

            setSize(getPreferredSize());
        }

        public void saveSettings() {
            int lowLevelValue = ((SpinnerNumberModel)lowLevel.getModel())
                .getNumber().intValue();
            int highLevelValue = ((SpinnerNumberModel)highLevel.getModel())
                .getNumber().intValue();
            int importLevelValue = (importLevel == null) ? -1
                : ((SpinnerNumberModel)importLevel.getModel())
                    .getNumber().intValue();
            int exportLevelValue = ((SpinnerNumberModel)exportLevel.getModel())
                .getNumber().intValue();
            ExportData exportData = colony.getExportData(goodsType);
            int importValue = exportData.getEffectiveImportLevel(colony.getWarehouseCapacity());
            boolean changed = (export.isSelected() != exportData.getExported())
                || (lowLevelValue != exportData.getLowLevel())
                || (highLevelValue != exportData.getHighLevel())
                || (importLevel != null && importLevelValue != importValue)
                || (exportLevelValue != exportData.getExportLevel())
                || (overflowToCarrier.isSelected() != exportData.isOverflowToCarrier());
            exportData.setExported(export.isSelected());
            exportData.setLowLevel(lowLevelValue);
            exportData.setHighLevel(highLevelValue);
            exportData.setImportLevel(importLevelValue);
            exportData.setExportLevel(exportLevelValue);
            exportData.setOverflowToCarrier(overflowToCarrier.isSelected());
            if (changed) {
                freeColClient.getInGameController()
                    .setGoodsLevels(colony, goodsType);
            }
        }
    }
}
