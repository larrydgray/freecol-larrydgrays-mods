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

    /** One bar: a label, a color, and its current value. */
    public static final class Bar {

        public final String label;
        public final Color color;
        public final double value;

        public Bar(String label, Color color, double value) {
            this.label = label;
            this.color = color;
            this.value = value;
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
        repaint();
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
            int lw = fm.stringWidth(b.label);
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

            g2d.setColor(Color.BLACK);
            g2d.drawString(b.label,
                plotLeft - fm.stringWidth(b.label) - (int)(6 * this.scale),
                barY + fm.getAscent());

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
