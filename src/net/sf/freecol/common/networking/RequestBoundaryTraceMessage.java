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

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * LarryDGray's Mods: request a server-computed Auto Explore boundary
 * trace using the real, full map - see {@code AutoExploreDecider.
 * buildBoundaryTrace(..., ignoreFog=true)}. Sent by the client with no
 * {@code path} attribute (a pure request); the server re-constructs
 * this same message with a computed {@code path} attached and sends it
 * back as the response - mirrors {@code NationSummaryMessage}'s exact
 * dual request/response shape.
 *
 * The response carries only a bare sequence of compass directions,
 * nothing about what is actually at each tile (no terrain type, no
 * resources, no settlements) - it never reveals anything to the
 * player ahead of the ship actually sailing there. The ship's own
 * fog-of-war reveal is completely unaffected, driven entirely by the
 * existing, unmodified movement/sight mechanism.
 */
public class RequestBoundaryTraceMessage extends AttributeMessage {

    public static final String TAG = "requestBoundaryTrace";
    private static final String UNIT_TAG = "unit";
    private static final String WALL_TAG = "wall";
    private static final String HEADING_TAG = "heading";
    private static final String PATH_TAG = "path";
    private static final String RECENT_TAG = "recent";
    private static final String PATH_SEPARATOR = ",";


    /**
     * Create a new outgoing request for a boundary trace.
     *
     * @param unit The {@code Unit} to trace a boundary for.
     * @param wallMode The {@code Unit.AutoExploreMode} whose boundary
     *     type to trace ({@code COASTLINE} or {@code DEEP_WATER}).
     * @param heading The unit's current committed heading, or null for
     *     a full compass scan on the very first step.
     * @param recentTiles LarryDGray's Mods: the unit's own recent-tile
     *     history ({@code Unit.getAutoExploreRecentTiles()}) - tells
     *     the server which tiles to avoid looping back onto, since the
     *     server's own copy of the unit never sees this client-only
     *     tracking otherwise.
     */
    public RequestBoundaryTraceMessage(Unit unit, Unit.AutoExploreMode wallMode,
                                       Direction heading, List<Tile> recentTiles) {
        super(TAG, UNIT_TAG, unit.getId(), WALL_TAG, wallMode.toString(),
              HEADING_TAG, (heading == null) ? "" : heading.toString(),
              RECENT_TAG, encodeTiles(recentTiles));
    }

    /**
     * Create the server's response, carrying the computed trace.
     *
     * @param unit The {@code Unit} the trace was computed for.
     * @param path The traced sequence of headings, oldest first.
     */
    public RequestBoundaryTraceMessage(Unit unit, List<Direction> path) {
        super(TAG, UNIT_TAG, unit.getId(), PATH_TAG, encodePath(path));
    }

    /**
     * Create a new {@code RequestBoundaryTraceMessage} from a stream -
     * either a request (no {@code path} attribute) or a response (with
     * one), distinguished by {@link #hasAttribute}.
     *
     * @param game The {@code Game} this message belongs to (null here).
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public RequestBoundaryTraceMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, UNIT_TAG, WALL_TAG, HEADING_TAG, PATH_TAG, RECENT_TAG);
    }


    /**
     * Encode a path as a delimited string of {@code Direction} names.
     *
     * @param path The path to encode.
     * @return The encoded string, "" for an empty path.
     */
    private static String encodePath(List<Direction> path) {
        final StringBuilder sb = new StringBuilder();
        for (Direction d : path) {
            if (sb.length() > 0) sb.append(PATH_SEPARATOR);
            sb.append(d.toString());
        }
        return sb.toString();
    }

    /**
     * Decode a path previously encoded by {@link #encodePath}.
     *
     * @param encoded The encoded string, possibly empty or null.
     * @return The decoded path, empty if {@code encoded} was empty.
     */
    private static List<Direction> decodePath(String encoded) {
        final List<Direction> path = new ArrayList<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String name : encoded.split(PATH_SEPARATOR)) {
                path.add(Direction.valueOf(name));
            }
        }
        return path;
    }

    /**
     * Encode a list of tiles as a delimited string of tile ids.
     *
     * @param tiles The tiles to encode.
     * @return The encoded string, "" for an empty/null list.
     */
    private static String encodeTiles(List<Tile> tiles) {
        if (tiles == null) return "";
        final StringBuilder sb = new StringBuilder();
        for (Tile t : tiles) {
            if (sb.length() > 0) sb.append(PATH_SEPARATOR);
            sb.append(t.getId());
        }
        return sb.toString();
    }

    /**
     * Decode a tile-id list previously encoded by {@link #encodeTiles},
     * resolving each id against {@code game}.
     *
     * @param game The {@code Game} to resolve tile ids against.
     * @param encoded The encoded string, possibly empty or null.
     * @return The decoded tiles, empty if {@code encoded} was empty.
     */
    private static List<Tile> decodeTiles(Game game, String encoded) {
        final List<Tile> tiles = new ArrayList<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String id : encoded.split(PATH_SEPARATOR)) {
                final Tile t = game.getFreeColGameObject(id, Tile.class);
                if (t != null) tiles.add(t);
            }
        }
        return tiles;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePriority getPriority() {
        return Message.MessagePriority.LATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final String unitId = getStringAttribute(UNIT_TAG);
        Unit unit;
        try {
            unit = serverPlayer.getOurFreeColGameObject(unitId, Unit.class);
        } catch (Exception e) {
            return serverPlayer.clientError(e.getMessage());
        }

        final Unit.AutoExploreMode wallMode
            = Unit.AutoExploreMode.valueOf(getStringAttribute(WALL_TAG));
        final String headingStr = getStringAttribute(HEADING_TAG);
        final Direction heading = (headingStr == null || headingStr.isEmpty())
            ? null : Direction.valueOf(headingStr);
        final List<Tile> recentTiles = decodeTiles(freeColServer.getGame(),
            getStringAttribute(RECENT_TAG));

        return igc(freeColServer)
            .requestBoundaryTrace(serverPlayer, unit, wallMode, heading, recentTiles);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        final Game game = freeColClient.getGame();
        final Unit unit = game.getFreeColGameObject(
            getStringAttribute(UNIT_TAG), Unit.class);
        if (unit == null) return;

        unit.setAutoExplorePath(decodePath(getStringAttribute(PATH_TAG)));
        clientGeneric(freeColClient);
    }
}
