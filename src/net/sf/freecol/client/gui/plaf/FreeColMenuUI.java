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

package net.sf.freecol.client.gui.plaf;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicMenuUI;

import net.sf.freecol.client.gui.ImageLibrary;


/**
 * UI-class for JMenu.
 *
 * LarryDGray's Mods: a JMenu is used both for the top-level menu bar
 * buttons ("Game", "View", ...), which sit on a dark background where
 * the theme's gold foreground has good contrast, and for nested
 * submenus inside a dropdown/popup (e.g. a unit right-click menu's
 * "Work" entry), which sit on a light background where that same gold
 * is unreadable. Both share the single "Menu.foreground" UIDefaults
 * key, so a static override can only get one of those two cases
 * right - decide per-instance instead, based on the JMenu's actual
 * parent at paint time (only reliable point the parent is known).
 */
public class FreeColMenuUI extends BasicMenuUI {

    public static ComponentUI createUI(@SuppressWarnings("unused") JComponent c) {
        return new FreeColMenuUI();
    }


    @Override
    public void installUI(JComponent c) {
        super.installUI(c);

        c.setOpaque(false);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        LAFUtilities.setProperties(g, c);

        // LarryDGray's Mods: for an *idle* (unarmed) menu item,
        // BasicMenuItemUI.paintText() never calls g.setColor() at
        // all - it just draws using whatever color the Graphics
        // object already has. That ambient color is preset from
        // c.getForeground() by the parent's paintChildren() dispatch
        // *before* this method runs, so changing c's foreground
        // property in here is always one step too late to have any
        // visible effect. Setting the Graphics color directly, right
        // here before delegating, is what actually reaches the pixel.
        // (The selectionForeground field swap covers the armed/
        // hovered case, which paintText() *does* read explicitly.)
        final boolean nested = !(c.getParent() instanceof JMenuBar);
        final Color originalSelectionForeground = this.selectionForeground;
        if (nested) {
            Color dark = ImageLibrary
                .getColor("color.menuItemForeground.LookAndFeel");
            g.setColor(dark);
            this.selectionForeground = dark;
        }
        try {
            super.paint(g, c);
        } finally {
            if (nested) this.selectionForeground = originalSelectionForeground;
        }
    }
}
