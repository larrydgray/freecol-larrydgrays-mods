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

package net.sf.freecol.common.networking;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * LarryDGray's Mods: the message sent when the client requests
 * starting or stopping a unit's "Auto Explore" order.
 */
public class SetAutoExploreMessage extends AttributeMessage {

    public static final String TAG = "setAutoExplore";
    private static final String UNIT_TAG = "unit";
    private static final String START_TAG = "start";


    /**
     * Create a new {@code SetAutoExploreMessage} with the supplied
     * unit and desired state.
     *
     * @param unit The {@code Unit} to start/stop Auto Exploring.
     * @param start True to start, false to stop.
     */
    public SetAutoExploreMessage(Unit unit, boolean start) {
        super(TAG, UNIT_TAG, unit.getId(), START_TAG, String.valueOf(start));
    }

    /**
     * Create a new {@code SetAutoExploreMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to (null here).
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public SetAutoExploreMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, UNIT_TAG, START_TAG);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePriority getPriority() {
        return Message.MessagePriority.NORMAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final Specification spec = freeColServer.getGame().getSpecification();
        if (!spec.hasOption(GameOptions.ENABLE_AUTO_EXPLORE, BooleanOption.class)
            || !spec.getBoolean(GameOptions.ENABLE_AUTO_EXPLORE)) {
            return serverPlayer.clientError("Auto Explore is disabled.");
        }

        final String unitId = getStringAttribute(UNIT_TAG);
        final boolean start = getBooleanAttribute(START_TAG, Boolean.FALSE);

        Unit unit;
        try {
            unit = serverPlayer.getOurFreeColGameObject(unitId, Unit.class);
        } catch (Exception e) {
            logger.warning("LarryDGray's Mods: SetAutoExploreMessage unit lookup "
                + "failed: " + e.getMessage());
            return serverPlayer.clientError(e.getMessage());
        }

        ChangeSet cs = igc(freeColServer).setAutoExplore(serverPlayer, unit, start);
        logger.info("LarryDGray's Mods: SetAutoExploreMessage handled for "
            + unit + ", now isAutoExploring=" + unit.isAutoExploring());
        return cs;
    }
}
