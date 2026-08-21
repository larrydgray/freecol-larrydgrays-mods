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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;


/**
 * LarryDGray's Mods: a simple, reusable multi-series line chart
 * plotting a value against turn number, for the Colony Growth and
 * Nation Comparison reports. No external charting library exists in
 * this codebase, so this is hand-drawn Graphics2D, styled loosely
 * after {@link FreeColProgressBar} (scale-aware, antialiased, uses
 * the same tiny scaled font).
 *
 * A series may be marked {@code dashed}, meaning it is plotted
 * against its own secondary (right-hand) value axis instead of the
 * primary (left-hand) one shared by the solid series - e.g. a goods
 * stockpile in the hundreds plotted alongside unit counts in the
 * tens would otherwise squash the unit lines flat.
 */
public class HistoryLineChart extends JPanel {

    /**
     * One plotted line: a label, a color, its (turn, value) samples
     * in turn order, and whether it belongs on the secondary
     * (dashed, right-axis) scale rather than the primary one.
     */
    public static final class Series {

        public final String label;
        public final Color color;
        public final int[] turns;
        public final double[] values;
        public final boolean dashed;

        public Series(String label, Color color, int[] turns, double[] values) {
            this(label, color, turns, values, false);
        }

        public Series(String label, Color color, int[] turns, double[] values,
                      boolean dashed) {
            this.label = label;
            this.color = color;
            this.turns = turns;
            this.values = values;
            this.dashed = dashed;
        }
    }

    /** A value axis's min/max range, with padding already applied. */
    private static final class Range {
        double min, max;
        Range(double min, double max) { this.min = min; this.max = max; }
    }

    private final float scale;
    private final Font font;
    private List<Series> series = Collections.emptyList();


    /**
     * Creates a new {@code HistoryLineChart}.
     *
     * @param freeColClient The enclosing {@code FreeColClient}.
     */
    public HistoryLineChart(FreeColClient freeColClient) {
        this.scale = freeColClient.getGUI().getFixedImageLibrary().getScaleFactor();
        this.font = FontLibrary.getScaledFont("simple-plain-tiny");
        setOpaque(true);
        setBackground(Color.WHITE);
        updatePreferredSize();
    }


    /**
     * Set the series to plot, replacing whatever was there before.
     *
     * @param series The new {@code Series} list to plot.
     */
    public void setSeries(List<Series> series) {
        this.series = (series == null) ? Collections.emptyList() : series;
        updatePreferredSize();
        repaint();
    }

    /**
     * Grow the preferred height to fit however many legend rows the
     * current series list needs, on top of a fixed minimum plot area.
     * A legend that outgrows a size fixed at construction time would
     * otherwise get silently clipped by the container - the plotted
     * lines still render (they are always kept within bounds by
     * construction), but the legend text for the last row or two
     * would fall outside the component's actual bounds and Swing
     * simply never paints outside them, with no error to show for it.
     */
    private void updatePreferredSize() {
        FontMetrics fm = getFontMetrics(this.font);
        int rows = ((this.series.size() + 1) / 2) + 1;
        int legendHeight = rows * fm.getHeight();
        int minHeight = (int)(150 * this.scale) + legendHeight;
        setPreferredSize(new Dimension((int)(600 * this.scale),
            Math.max((int)(300 * this.scale), minHeight)));
        revalidate();
    }

    /**
     * Clear the chart back to empty.
     */
    public void clear() {
        setSeries(null);
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

        // Any series with at least one sample?
        boolean hasData = false;
        for (Series s : this.series) {
            if (s.turns.length > 0) { hasData = true; break; }
        }
        if (!hasData) {
            String msg = Messages.message("historyLineChart.noData");
            int sw = fm.stringWidth(msg);
            g2d.setColor(Color.GRAY);
            g2d.drawString(msg, (width - sw) / 2, height / 2);
            g2d.dispose();
            return;
        }

        // Split into the primary (solid, left-axis) and secondary
        // (dashed, right-axis) series. If every series happened to
        // be marked dashed, there is nothing to contrast them
        // against, so just treat them all as primary.
        List<Series> primary = new ArrayList<>();
        List<Series> secondary = new ArrayList<>();
        for (Series s : this.series) {
            (s.dashed ? secondary : primary).add(s);
        }
        if (primary.isEmpty()) {
            primary.addAll(secondary);
            secondary.clear();
        }

        // Combined turn range across every series, both axes share it.
        int minTurn = Integer.MAX_VALUE, maxTurn = Integer.MIN_VALUE;
        for (Series s : this.series) {
            for (int t : s.turns) {
                if (t < minTurn) minTurn = t;
                if (t > maxTurn) maxTurn = t;
            }
        }
        if (minTurn == maxTurn) maxTurn = minTurn + 1; // avoid /0

        Range primaryRange = valueRange(primary);
        Range secondaryRange = secondary.isEmpty() ? null : valueRange(secondary);

        // Plot area, inset for axis labels and the legend strip below.
        final int marginLeft = (int)(45 * this.scale);
        final int marginRight = (secondaryRange == null) ? (int)(6 * this.scale)
            : (int)(45 * this.scale);
        final int marginBottom = (int)(20 * this.scale);
        final int legendHeight = (int)((((this.series.size() + 1) / 2) + 1)
            * fm.getHeight());
        final int plotTop = (int)(6 * this.scale);
        final int plotBottom = height - marginBottom - legendHeight;
        final int plotLeft = marginLeft;
        final int plotRight = width - marginRight;
        if (plotBottom <= plotTop || plotRight <= plotLeft) {
            g2d.dispose();
            return; // panel too small to draw anything sensible
        }
        final int plotWidth = plotRight - plotLeft;
        final int plotHeight = plotBottom - plotTop;

        // Horizontal gridlines, labelled with the primary (left) axis.
        g2d.setColor(new Color(0, 0, 0, 30));
        final int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            int y = plotTop + plotHeight * i / gridLines;
            g2d.drawLine(plotLeft, y, plotRight, y);
            double value = primaryRange.max
                - (primaryRange.max - primaryRange.min) * i / gridLines;
            String label = String.valueOf(Math.round(value));
            int lw = fm.stringWidth(label);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawString(label, plotLeft - lw - (int)(4 * this.scale),
                           y + fm.getAscent() / 2);
            g2d.setColor(new Color(0, 0, 0, 30));
        }
        // Secondary (right) axis labels, if any dashed series are present.
        if (secondaryRange != null) {
            g2d.setColor(Color.DARK_GRAY);
            for (int i = 0; i <= gridLines; i++) {
                int y = plotTop + plotHeight * i / gridLines;
                double value = secondaryRange.max
                    - (secondaryRange.max - secondaryRange.min) * i / gridLines;
                String label = String.valueOf(Math.round(value));
                g2d.drawString(label, plotRight + (int)(4 * this.scale),
                               y + fm.getAscent() / 2);
            }
        }
        // Turn-number labels at the ends of the X axis.
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString(String.valueOf(minTurn), plotLeft,
                       plotBottom + fm.getAscent() + (int)(2 * this.scale));
        String maxTurnLabel = String.valueOf(maxTurn);
        g2d.drawString(maxTurnLabel,
                       plotRight - fm.stringWidth(maxTurnLabel),
                       plotBottom + fm.getAscent() + (int)(2 * this.scale));

        // Each series as a polyline with sample-point markers, solid
        // ones against the primary range, dashed ones against the
        // secondary range. Markers matter here because two series
        // that share the same value at every turn would otherwise
        // draw as one indistinguishable line, hiding whichever was
        // drawn first.
        final BasicStroke solidStroke = new BasicStroke(Math.max(1.0f,
            1.5f * this.scale));
        final BasicStroke dashedStroke = new BasicStroke(Math.max(1.0f,
            1.5f * this.scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[] {Math.max(4.0f, 4.0f * this.scale),
                                Math.max(3.0f, 3.0f * this.scale)}, 0.0f);
        final float markerRadius = Math.max(1.5f, 2.0f * this.scale);
        for (Series s : this.series) {
            if (s.turns.length == 0) continue;
            boolean isSecondary = secondary.contains(s);
            Range range = isSecondary ? secondaryRange : primaryRange;
            g2d.setStroke(isSecondary ? dashedStroke : solidStroke);
            GeneralPath path = new GeneralPath();
            for (int i = 0; i < s.turns.length; i++) {
                float x = plotLeft + (float)(plotWidth
                    * (s.turns[i] - minTurn)) / (maxTurn - minTurn);
                float y = plotBottom - (float)(plotHeight
                    * (s.values[i] - range.min)) / (float)(range.max - range.min);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            g2d.setColor(s.color);
            g2d.draw(path);
            for (int i = 0; i < s.turns.length; i++) {
                float x = plotLeft + (float)(plotWidth
                    * (s.turns[i] - minTurn)) / (maxTurn - minTurn);
                float y = plotBottom - (float)(plotHeight
                    * (s.values[i] - range.min)) / (float)(range.max - range.min);
                g2d.fill(new Ellipse2D.Float(x - markerRadius, y - markerRadius,
                    markerRadius * 2, markerRadius * 2));
            }
        }

        // Compact legend below the plot, two columns. Each swatch is
        // drawn with the series' own stroke so dashed lines are
        // recognizable as dashed in the legend too.
        int legendX = plotLeft, legendY = plotBottom + marginBottom;
        final int swatch = (int)(14 * this.scale);
        final int colWidth = plotWidth / 2;
        int col = 0;
        for (Series s : this.series) {
            int x = legendX + col * colWidth;
            int lineY = legendY + fm.getAscent() / 2;
            g2d.setStroke(secondary.contains(s) ? dashedStroke : solidStroke);
            g2d.setColor(s.color);
            g2d.drawLine(x, lineY, x + swatch, lineY);
            g2d.setColor(Color.BLACK);
            g2d.drawString(s.label, x + swatch + (int)(4 * this.scale),
                           legendY + fm.getAscent());
            col++;
            if (col >= 2) {
                col = 0;
                legendY += fm.getHeight();
            }
        }

        g2d.dispose();
    }

    /**
     * Compute the padded value range spanning every sample in the
     * given series.
     *
     * @param seriesList The {@code Series} to span.
     * @return The padded {@code Range}.
     */
    private static Range valueRange(List<Series> seriesList) {
        double minValue = Double.MAX_VALUE, maxValue = -Double.MAX_VALUE;
        for (Series s : seriesList) {
            for (double v : s.values) {
                if (v < minValue) minValue = v;
                if (v > maxValue) maxValue = v;
            }
        }
        if (minValue > maxValue) { minValue = 0; maxValue = 1; } // no samples
        if (minValue == maxValue) maxValue = minValue + 1; // avoid /0
        final double pad = (maxValue - minValue) * 0.05;
        minValue -= pad;
        maxValue += pad;
        if (minValue > 0 && minValue < pad * 2) minValue = 0;
        return new Range(minValue, maxValue);
    }
}
