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
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Colony.ManagerGoal;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * LarryDGray's Mods: the message sent when setting a colony's
 * Manager goal.
 */
public class SetColonyManagerMessage extends AttributeMessage {

    public static final String TAG = "setColonyManager";
    private static final String COLONY_TAG = "colony";
    private static final String MANAGER_TAG = "manager";


    /**
     * Create a new {@code SetColonyManagerMessage} with the
     * supplied colony and goal.
     *
     * @param colony The {@code Colony} whose Manager goal is set.
     * @param goal The new {@code ManagerGoal}.
     */
    public SetColonyManagerMessage(Colony colony, ManagerGoal goal) {
        super(TAG, COLONY_TAG, colony.getId(), MANAGER_TAG, goal.toString());
    }

    /**
     * Create a new {@code SetColonyManagerMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public SetColonyManagerMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, COLONY_TAG, MANAGER_TAG);
    }


    /**
     * Get the manager goal.
     *
     * @return The {@code ManagerGoal}.
     */
    private ManagerGoal getManagerGoal() {
        return getEnumAttribute(MANAGER_TAG, ManagerGoal.class, ManagerGoal.UNMANAGED);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean currentPlayerMessage() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePriority getPriority() {
        return MessagePriority.NORMAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        Colony colony;
        try {
            colony = serverPlayer.getOurFreeColGameObject(getStringAttribute(COLONY_TAG),
                                                          Colony.class);
        } catch (Exception e) {
            return serverPlayer.clientError(e.getMessage());
        }

        // Proceed to set.
        return igc(freeColServer)
            .setColonyManager(serverPlayer, colony, getManagerGoal());
    }
}
