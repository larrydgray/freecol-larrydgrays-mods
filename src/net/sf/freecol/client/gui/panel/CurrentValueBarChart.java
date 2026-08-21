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

package net.sf.freecol.client.gui.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.common.i18n.Messages;


/**
 * LarryDGray's Mods: a simple current-turn snapshot bar chart - one
 * horizontal bar per category, labelled to the left, sized against
 * whichever bar is largest. Meant to sit alongside a
 * {@link HistoryLineChart} showing the same categories' trend over
 * time: slow-changing counts (how many of each ship you own, say)
 * read more naturally as "how many right now" bars than as a
 * near-flat line.
 */
public class CurrentValueBarChart extends JPanel {

    /** One bar: a label, a color, its current value, and (LarryDGray's
     *  Mods) an optional icon shown to the left instead of the text
     *  label. */
    public static final class Bar {

        public final String label;
        public final Color color;
        public final double value;
        public final Image icon;

        public Bar(String label, Color color, double value) {
            this(label, color, value, null);
        }

        /**
         * LarryDGray's Mods: a bar labelled with an icon instead of
         * (or in addition to) plain text - e.g. a unit type's small
         * image, for a "Labor Advisor as a bar chart" view.
         *
         * @param label Kept for tooltip/accessibility purposes even
         *     when an icon is shown; pass "" if genuinely nothing to
         *     say beyond the icon.
         * @param color The bar's fill color.
         * @param value The bar's value.
         * @param icon The icon to draw at the left instead of the
         *     text label, or null for the original text-only look.
         */
        public Bar(String label, Color color, double value, Image icon) {
            this.label = label;
            this.color = color;
            this.value = value;
            this.icon = icon;
        }
    }

    private final float scale;
    private final Font font;
    private List<Bar> bars = Collections.emptyList();


    /**
     * Creates a new {@code CurrentValueBarChart}.
     *
     * @param freeColClient The enclosing {@code FreeColClient}.
     */
    public CurrentValueBarChart(FreeColClient freeColClient) {
        this.scale = freeColClient.getGUI().getFixedImageLibrary().getScaleFactor();
        this.font = FontLibrary.getScaledFont("simple-plain-tiny");
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension((int)(260 * this.scale),
                                       (int)(300 * this.scale)));
    }


    /**
     * Set the bars to plot, replacing whatever was there before.
     *
     * @param bars The new {@code Bar} list to plot.
     */
    public void setBars(List<Bar> bars) {
        this.bars = (bars == null) ? Collections.emptyList() : bars;
        updatePreferredSize();
        repaint();
    }

    /**
     * LarryDGray's Mods: cap actual height at the preferred height
     * computed above. Without this, a "grow" MigLayout constraint in
     * a tall viewport stretches the component to fill all available
     * space regardless of how many bars there are - and since each
     * row's paint height is actual-height/bar-count, a handful of
     * bars in a tall viewport renders as huge, over-spaced rows
     * instead of the intended row height.
     *
     * @return The preferred size, reused as the maximum.
     */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * LarryDGray's Mods: grow the chart's preferred height to fit
     * however many bars there are - the original fixed 300px height
     * assumed a handful of categories (a few military unit roles);
     * a long list (e.g. every colonist unit type for a Labor Advisor
     * bar-chart view) needs proportionally more room, left to the
     * enclosing scroll pane to handle once it exceeds the viewport.
     */
    private void updatePreferredSize() {
        // LarryDGray's Mods: rows need to be tall enough for the
        // tallest icon in use, plus some breathing room - a fixed
        // 26px row (sized for plain text) left icon-labelled bars
        // (e.g. Labor Advisor's unit type images) crowded edge to
        // edge with barely a gap between them.
        int maxIconHeight = 0;
        for (Bar b : this.bars) {
            if (b.icon != null && b.icon.getHeight(null) > maxIconHeight) {
                maxIconHeight = b.icon.getHeight(null);
            }
        }
        final int rowPadding = (int)(10 * this.scale);
        final int rowHeight = (maxIconHeight > 0)
            ? maxIconHeight + rowPadding
            : (int)(26 * this.scale);
        final int minHeight = (int)(300 * this.scale);
        final int height = Math.max(minHeight,
            this.bars.size() * rowHeight + (int)(12 * this.scale));
        setPreferredSize(new Dimension(getPreferredSize().width, height));
        revalidate();
    }

    /**
     * Clear the chart back to empty.
     */
    public void clear() {
        setBars(null);
    }


    // Override JComponent

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(this.font);

        final int width = getWidth();
        final int height = getHeight();
        final FontMetrics fm = g2d.getFontMetrics();

        if (this.bars.isEmpty()) {
            String msg = Messages.message("historyLineChart.noData");
            int sw = fm.stringWidth(msg);
            g2d.setColor(Color.GRAY);
            g2d.drawString(msg, (width - sw) / 2, height / 2);
            g2d.dispose();
            return;
        }

        double maxValue = 0;
        int labelWidth = 0;
        for (Bar b : this.bars) {
            if (b.value > maxValue) maxValue = b.value;
            int lw = (b.icon != null) ? b.icon.getWidth(null)
                : fm.stringWidth(b.label);
            if (lw > labelWidth) labelWidth = lw;
        }
        if (maxValue <= 0) maxValue = 1;

        final int plotLeft = labelWidth + (int)(8 * this.scale);
        final int plotRight = width - (int)(40 * this.scale);
        final int plotTop = (int)(6 * this.scale);
        final int plotBottom = height - (int)(6 * this.scale);
        if (plotRight <= plotLeft || plotBottom <= plotTop) {
            g2d.dispose();
            return; // panel too small to draw anything sensible
        }

        final int n = this.bars.size();
        final int rowHeight = (plotBottom - plotTop) / n;
        final int barHeight = Math.max(2, (int)(rowHeight * 0.6));
        for (int i = 0; i < n; i++) {
            Bar b = this.bars.get(i);
            int rowY = plotTop + i * rowHeight;
            int barY = rowY + (rowHeight - barHeight) / 2;
            int barLen = Math.max(1, (int)Math.round(
                (plotRight - plotLeft) * (b.value / maxValue)));

            if (b.icon != null) {
                int iw = b.icon.getWidth(null);
                int ih = b.icon.getHeight(null);
                g2d.drawImage(b.icon, plotLeft - iw - (int)(6 * this.scale),
                    rowY + (rowHeight - ih) / 2, null);
            } else {
                g2d.setColor(Color.BLACK);
                g2d.drawString(b.label,
                    plotLeft - fm.stringWidth(b.label) - (int)(6 * this.scale),
                    barY + fm.getAscent());
            }

            g2d.setColor(b.color);
            g2d.fillRect(plotLeft, barY, barLen, barHeight);

            String valueLabel = String.valueOf(Math.round(b.value));
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawString(valueLabel, plotLeft + barLen + (int)(4 * this.scale),
                           barY + fm.getAscent());
        }

        g2d.dispose();
    }
}
