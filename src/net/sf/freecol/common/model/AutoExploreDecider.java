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

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static net.sf.freecol.common.util.CollectionUtils.any;


/**
 * LarryDGray's Mods: pure movement-direction logic for the ship
 * "Auto Explore" order (see {@link Unit.AutoExploreMode}).
 *
 * Re-evaluated fresh every move by the client's per-turn Auto Explore
 * driver ({@code InGameController.autoExploreStep()}). For the two
 * curving boundary types (coastline, deep-water/high-seas) this is a
 * hybrid, not a pure one-tile-at-a-time reactive decision: {@link
 * #followTracedBoundary} first tries to walk a precomputed path built
 * by {@link #buildBoundaryTrace}, which maps out the entire connected
 * chain of already-EXPLORED wall-adjacent tiles in one go, so a stretch
 * the game already has complete information about is walked exactly
 * once, deterministically, rather than re-guessed tile by tile (the
 * source of most of this feature's past circling bugs). Only once that
 * known path runs out - the genuine frontier of explored territory, or
 * a fork needing the player's input - does it fall back to a single
 * reactive step via {@link #followBoundary}. This still never plans
 * through fog: {@link #buildBoundaryTrace} stops the instant it would
 * need to extend into an unexplored tile.
 *
 * The player picks a single {@link Unit.AutoExploreMode} once, when
 * the order is started, rather than the old design letting this class
 * auto-detect which of coastline/arctic-edge/deep-water to prioritise -
 * that auto-detection was a repeated, hard-to-fully-fix source of
 * circling bugs at ambiguous geometry (corners where two boundary
 * types meet). {@code NEAREST_FOG} mode never hugs anything and always
 * steers toward the single nearest patch of actual fog (falling back
 * to a random committed heading if truly nothing - no known boundary,
 * no fog - is within reach at all). The other three modes steer toward
 * the nearest known instance of their one relevant boundary type until
 * they touch it, then hug it - {@link #followTracedBoundary} for the
 * coastline and deep-water/high-seas line, which both curve
 * unpredictably, or the simpler {@link #followStraightEdge} for the
 * polar band, which is a fixed-width row-range with no curvature to
 * follow at all. Which way to hug (the turn bias/heading) is no longer
 * auto-detected either - see {@link #getPendingChoice} for how the
 * player is asked, and only at genuinely ambiguous points (first
 * contact, or a real fork).
 */
public final class AutoExploreDecider {

    private AutoExploreDecider() {} // pure static helper, not instantiable


    /**
     * Detect boundary adjacency at the unit's current tile (updating
     * its phase to match - this is the ONLY place {@code updatePhase}
     * runs each step; {@link #chooseDirection} assumes it has already
     * happened) and report whether the player needs to choose a
     * continuation direction before a move can be computed - either
     * because there is no committed heading yet (first contact with
     * the mode's boundary, or a heading was just invalidated), or
     * because the ship has newly arrived at a genuine fork (2+
     * separate {@link #confirmedRuns} of continuation directions) it
     * has not already been asked about. Must be called exactly once
     * per {@code InGameController.autoExploreStep()}, before
     * {@link #chooseDirection}.
     *
     * @param unit The auto-exploring {@code Unit}.
     * @return The candidate {@code Direction}s to offer the player, or
     *     empty if nothing needs asking right now.
     */
    public static List<Direction> getPendingChoice(Unit unit) {
        final Tile tile = unit.getTile();
        if (tile == null) return Collections.emptyList();

        updatePhase(unit, tile);

        final Predicate<Tile> wall = wallPredicateFor(unit.getAutoExplorePhase());
        if (wall == null) { // OPEN_OCEAN - also always true in NEAREST_FOG mode
            unit.setAutoExploreInBranch(false);
            return Collections.emptyList();
        }

        final List<List<Direction>> runs = confirmedRuns(tile, wall);
        final boolean isBranchHere = runs.size() >= 2;

        if (unit.getAutoExploreHeading() == null) {
            // First contact - always ask, even for a plain 2-option coast.
            unit.setAutoExploreInBranch(isBranchHere);
            return runEndpoints(runs);
        }
        if (isBranchHere && !unit.isAutoExploreInBranch()) {
            // Rising edge only - a NEW fork, not one already being handled.
            unit.setAutoExploreInBranch(true);
            return runEndpoints(runs);
        }
        if (!isBranchHere) unit.setAutoExploreInBranch(false); // left the fork behind
        return Collections.emptyList();
    }

    /**
     * Choose the next direction for an auto-exploring unit to move.
     * Assumes {@link #getPendingChoice} has already run this step (and
     * so {@code updatePhase} has already updated the unit's phase, and
     * any needed heading has already been committed by the player).
     *
     * @param unit The auto-exploring {@code Unit}.
     * @return The {@code Direction} to move, or null if no passable
     *     direction is available at all (fully boxed in) or the unit
     *     has no tile to reason from.
     */
    public static Direction chooseDirection(Unit unit) {
        final Tile tile = unit.getTile();
        if (tile == null) return null;

        if (unit.getAutoExploreMode() == Unit.AutoExploreMode.DIRECTION) {
            // LarryDGray's Mods: reuses followStraightEdge() exactly -
            // "keep going in the committed heading until land or the
            // map edge blocks it, then return null" is precisely
            // DIRECTION mode's whole behaviour (a null return already
            // means "disengage" to the caller, InGameController.
            // autoExploreStep()).
            return followStraightEdge(unit, tile);
        }

        switch (unit.getAutoExplorePhase()) {
        case COAST_HUGGING:
            return followTracedBoundary(unit, tile, AutoExploreDecider::isBoundaryLand);
        case ARCTIC_HUGGING:
            // LarryDGray's Mods: the polar band (Tile.isPolar()) is a
            // pure Y-coordinate row-range, completely independent of
            // terrain - unlike a coastline or the deep-water line, it
            // is never curved or jagged. Once adjacent to it, moving
            // in a fixed committed heading (chosen once at first
            // contact) stays adjacent to it automatically, all the
            // way to either a landmass crossing that row (handled
            // separately - updatePhase() already switches to
            // COAST_HUGGING when that happens) or the literal edge of
            // the map. There is no wall-following/sweep logic to get
            // wrong here, and using the same sweep as the other two
            // phases was a needless source of circling for a case
            // that's geometrically trivial - see followStraightEdge.
            return followStraightEdge(unit, tile);
        case OCEAN_BOUNDARY:
            return followTracedBoundary(unit, tile, AutoExploreDecider::isBoundaryDeepOcean);
        case OPEN_OCEAN:
        default:
            return chooseOpenOceanDirection(unit, tile);
        }
    }


    // Boundary predicates - each isolated to a one-line helper so the
    // "what counts as arctic/deep-ocean" decision stays a one-line
    // swap if it ever needs revisiting.

    /**
     * Is this tile land? Unexplored tiles (null type) are treated as
     * passable open water for planning purposes - their real type is
     * unknown until visited, matching the rest of the codebase's
     * convention of allowing (not favouring) travel through
     * unexplored territory. A move that turns out to hit land simply
     * fails to execute and gets reconsidered next turn.
     */
    private static boolean isBoundaryLand(Tile tile) {
        return tile.isExplored() && tile.isLand();
    }

    /** Is this tile in the map's polar row-band? */
    private static boolean isBoundaryArctic(Tile tile) {
        return tile.isPolar();
    }

    /**
     * Is this tile the ocean/high-seas boundary? True only for
     * {@code model.tile.highSeas} (Europe-reachable deep water),
     * never for ordinary {@code model.tile.ocean}.
     */
    private static boolean isBoundaryDeepOcean(Tile tile) {
        return tile.isExplored() && tile.isDirectlyHighSeasConnected();
    }

    /** Is this tile blocked under any of the three boundary rules? */
    private static boolean isAnyBoundary(Tile tile) {
        return isBoundaryLand(tile) || isBoundaryArctic(tile)
            || isBoundaryDeepOcean(tile);
    }


    /**
     * How far to look when deciding whether to *stay* in a hugging
     * phase, once already in one - deliberately wider than the
     * radius-1 ring used to *enter* a phase, so a minor tile-adjacency
     * fluctuation (a slight outward bulge, a corner) does not bounce
     * the unit straight back out to OPEN_OCEAN mid-circumnavigation.
     * Circling a landmass/shoreline all the way round is meant to be
     * the single highest-priority behaviour once started - see the
     * class doc comment on priority ordering.
     */
    private static final int STICKY_RADIUS = 2;

    /**
     * How far to look, from {@code OPEN_OCEAN}, for a known coastline
     * or arctic edge to steer toward, before falling back to plain
     * unexplored-frontier seeking. Deliberately generous: "known" here
     * means anywhere the player has ever explored, not just current
     * line of sight, so this lets the ship go finish a shoreline it
     * spotted a few turns ago rather than only reacting to what is
     * immediately adjacent right now.
     */
    private static final int SEEK_RADIUS = 6;

    /**
     * Detect boundary adjacency at the unit's current tile and switch
     * phase to match, driven entirely by the player-chosen
     * {@link Unit.AutoExploreMode} - only ONE boundary type is ever
     * relevant now, replacing the old automatic land&gt;arctic&gt;
     * ocean priority scan across all three. {@code NEAREST_FOG} mode
     * never leaves {@code OPEN_OCEAN}. Otherwise: an already-
     * established hugging phase is kept as long as its boundary is
     * still within {@link #STICKY_RADIUS} (so circling a landmass
     * commits through minor coastline wobbles instead of bailing out
     * early) AND there is still something unexplored left to find
     * there (see the inline comment below for why); entering the
     * hugging phase fresh uses the tight radius-1 ring, since
     * *entering* should mean genuinely touching it.
     *
     * @param unit The {@code Unit} to update.
     * @param tile The unit's current {@code Tile}.
     */
    private static void updatePhase(Unit unit, Tile tile) {
        final Unit.AutoExploreMode mode = unit.getAutoExploreMode();
        if (mode == Unit.AutoExploreMode.DIRECTION) {
            // LarryDGray's Mods: fixed heading, chosen once at order
            // start and never touched again - no hugging, no phase
            // transitions, nothing to update here at all.
            return;
        }
        if (mode == Unit.AutoExploreMode.NEAREST_FOG) {
            if (unit.getAutoExplorePhase() != Unit.AutoExplorePhase.OPEN_OCEAN) {
                unit.setAutoExplorePhase(Unit.AutoExplorePhase.OPEN_OCEAN);
                unit.setAutoExploreHeading(null);
                unit.setAutoExploreInBranch(false);
                unit.setAutoExplorePath(null);
            }
            return; // never hugs anything in this mode
        }

        final Predicate<Tile> wall = wallPredicateForMode(mode);
        final Unit.AutoExplorePhase huggingPhase = huggingPhaseForMode(mode);
        final Unit.AutoExplorePhase phase = unit.getAutoExplorePhase();

        if (phase == huggingPhase) {
            final List<Tile> stickyRing = tile.getSurroundingTiles(1, STICKY_RADIUS);
            if (any(stickyRing, wall)) {
                // LarryDGray's Mods: staying in a hugging phase is
                // only worthwhile while there is still something left
                // to discover nearby. Without this check, a short
                // stretch of coast (a small island, a tight cove) that
                // has already been fully revealed gives the wall-
                // follower no reason to ever leave - "still touching
                // the wall" stays true forever, and it will happily
                // retrace the same short loop indefinitely (an actual
                // observed bug: a real 3-tile SW/NW/E repeating cycle,
                // not a hypothetical one). Force a break to OPEN_OCEAN
                // once nothing unexplored remains within reach, even
                // though the wall itself is still right there, so the
                // ship goes find fresh territory instead.
                if (any(stickyRing, t -> !t.isExplored())) {
                    return; // still hugging AND still learning something new
                }
                unit.setAutoExplorePhase(Unit.AutoExplorePhase.OPEN_OCEAN);
                unit.setAutoExploreHeading(null);
                unit.setAutoExploreInBranch(false);
                unit.setAutoExplorePath(null);
                return;
            }
        }

        final List<Tile> ring = tile.getSurroundingTiles(1, 1);
        final Unit.AutoExplorePhase wanted = any(ring, wall)
            ? huggingPhase : Unit.AutoExplorePhase.OPEN_OCEAN;
        if (wanted != phase) {
            unit.setAutoExplorePhase(wanted);
            // LarryDGray's Mods: only one boundary type is ever
            // relevant per mode now (no more "coastline running into
            // the polar band at a landmass corner" case, since a
            // COASTLINE-mode ship never hugs the polar band at all),
            // so the old "keep heading across a corner between two
            // different hugging phases" special case no longer
            // applies - always reset on any phase change here.
            unit.setAutoExploreHeading(null);
            unit.setAutoExploreInBranch(false);
            unit.setAutoExplorePath(null);
        }
    }

    /**
     * The boundary predicate a hugging phase is currently following,
     * or null for {@code OPEN_OCEAN} (nothing to stay stuck to).
     */
    private static Predicate<Tile> wallPredicateFor(Unit.AutoExplorePhase phase) {
        switch (phase) {
        case COAST_HUGGING: return AutoExploreDecider::isBoundaryLand;
        case ARCTIC_HUGGING: return AutoExploreDecider::isBoundaryArctic;
        case OCEAN_BOUNDARY: return AutoExploreDecider::isBoundaryDeepOcean;
        default: return null;
        }
    }

    /**
     * The boundary predicate a given {@link Unit.AutoExploreMode} is
     * seeking, or null for {@code NEAREST_FOG} (nothing to hug).
     */
    public static Predicate<Tile> wallPredicateForMode(Unit.AutoExploreMode mode) {
        switch (mode) {
        case COASTLINE:  return AutoExploreDecider::isBoundaryLand;
        case ARCTIC:     return AutoExploreDecider::isBoundaryArctic;
        case DEEP_WATER: return AutoExploreDecider::isBoundaryDeepOcean;
        default:         return null;
        }
    }

    /**
     * The hugging phase a given {@link Unit.AutoExploreMode} enters
     * once its boundary is touched, or {@code OPEN_OCEAN} for
     * {@code NEAREST_FOG} (which never hugs).
     */
    private static Unit.AutoExplorePhase huggingPhaseForMode(Unit.AutoExploreMode mode) {
        switch (mode) {
        case COASTLINE:  return Unit.AutoExplorePhase.COAST_HUGGING;
        case ARCTIC:     return Unit.AutoExplorePhase.ARCTIC_HUGGING;
        case DEEP_WATER: return Unit.AutoExplorePhase.OCEAN_BOUNDARY;
        default:         return Unit.AutoExplorePhase.OPEN_OCEAN;
        }
    }

    /**
     * Which compass directions from {@code tile} are still confirmed
     * to be touching {@code isWall} if the ship moved there - the same
     * per-candidate test {@link #followBoundary} already uses,
     * evaluated for the whole compass instead of a heading-biased
     * 6-candidate sweep - then grouped into maximal cyclic runs of
     * compass-adjacent directions (wrapping past NW back to N). A
     * plain shoreline produces exactly one run (the two directions
     * tangential to the coast are the only ones whose own neighbourhood
     * still touches it); a river mouth or narrows - land on two
     * separate sides with open channels between - produces two or more
     * separate runs, since the directions pointing into each channel
     * are separated by directions that fail the test. This distinction
     * is what makes a plain coast offer only 2 options while a genuine
     * fork offers more.
     *
     * @param tile The tile to inspect.
     * @param isWall Which boundary type is being hugged.
     * @return The confirmed runs, each a non-empty list of
     *     {@code Direction}s in compass order; empty if nothing is
     *     confirmed at all.
     */
    private static List<List<Direction>> confirmedRuns(Tile tile, Predicate<Tile> isWall) {
        final List<Direction> order = Direction.allDirections;
        final int n = order.size();
        final boolean[] confirmed = new boolean[n];
        for (int i = 0; i < n; i++) {
            final Tile next = tile.getNeighbourOrNull(order.get(i));
            if (next == null || isBoundaryLand(next) || isWall.test(next)) continue;
            confirmed[i] = any(next.getSurroundingTiles(1, 1), isWall);
        }
        final List<List<Direction>> runs = new ArrayList<>();
        final boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!confirmed[i] || visited[i]) continue;
            int start = i;
            while (confirmed[(start - 1 + n) % n] && !visited[(start - 1 + n) % n]) {
                start = (start - 1 + n) % n;
            }
            final List<Direction> run = new ArrayList<>();
            int j = start;
            do {
                run.add(order.get(j));
                visited[j] = true;
                j = (j + 1) % n;
            } while (confirmed[j] && !visited[j]);
            runs.add(run);
            if (run.size() == n) break; // fully surrounded - degenerate, one run only
        }
        return runs;
    }

    /**
     * The candidate directions to offer the player for a set of
     * {@link #confirmedRuns} - the two endpoints of every run (one if
     * a run is a single direction).
     */
    private static List<Direction> runEndpoints(List<List<Direction>> runs) {
        final List<Direction> candidates = new ArrayList<>();
        for (List<Direction> run : runs) {
            candidates.add(run.get(0));
            if (run.size() > 1) candidates.add(run.get(run.size() - 1));
        }
        return candidates;
    }

    /**
     * OPEN_OCEAN: priority order, highest first: (1) a known instance
     * of the unit's {@link Unit.AutoExploreMode} boundary type (none
     * for {@code NEAREST_FOG}) within {@link #SEEK_RADIUS} - steer
     * straight for it by genuine tile distance, so the ship goes to
     * finish a stretch it has already spotted rather than wander off
     * clearing unrelated fog; (2) failing that (or in
     * {@code NEAREST_FOG} mode), the single closest patch of actual
     * fog (unexplored tile) within range, same distance-minimising
     * steer - "closest fog" is literal here, not just "any direction
     * that happens to touch unexplored space"; (3) only once neither
     * exists anywhere in range - truly nothing to see - commit to one
     * direction, chosen at random, and keep going that way until
     * something comes into view. No cleverness in that last case (e.g.
     * steering away from distant deep water) since there is no
     * information nearby to be clever about in the first place.
     *
     * Whichever tile is picked as the target is then COMMITTED TO
     * (see {@link Unit#getAutoExploreTargetTile()}) and reused on
     * every subsequent call rather than recomputed from scratch - the
     * ship's own movement constantly changes which candidate tile is
     * technically "nearest", so recomputing fresh every step could
     * flip the target to a different, marginally closer patch of fog
     * after every single move, producing a visible back-and-forth
     * oscillation instead of steady progress. The committed target is
     * only replaced once it stops being useful: a fog target that has
     * since been explored, or none at all yet.
     *
     * @param unit The {@code Unit} to move.
     * @param tile The unit's current {@code Tile}.
     * @return The chosen {@code Direction}, or null if fully boxed in.
     */
    private static Direction chooseOpenOceanDirection(Unit unit, Tile tile) {
        final Direction lastHeading = unit.getAutoExploreHeading();
        final List<Direction> order = (lastHeading == null)
            ? Direction.allDirections : closestOrder(lastHeading);
        final Predicate<Tile> wall = wallPredicateForMode(unit.getAutoExploreMode());

        Tile target = unit.getAutoExploreTargetTile();
        if (!isUsableOpenOceanTarget(target, wall)) {
            // LarryDGray's Mods: a boundary candidate must still have
            // unexplored territory of its own nearby
            // (hasUnexploredAdjacent()) to be worth steering toward -
            // otherwise this could immediately re-target the very
            // stretch updatePhase() just forced the ship OUT of for
            // being fully explored, bouncing straight back into the
            // same already-fully-hugged loop instead of moving on.
            target = (wall == null) ? null
                : nearestKnown(tile, SEEK_RADIUS,
                    t -> wall.test(t) && t.hasUnexploredAdjacent());
            if (target == null) {
                target = nearestUnexplored(tile, SEEK_RADIUS);
            }
            unit.setAutoExploreTargetTile(target);
        }
        if (target != null) {
            final Direction best = steerToward(tile, order, target);
            if (best != null) {
                unit.setAutoExploreHeading(best);
                return best;
            }
            // Committed target is no longer reachable via any
            // passable neighbour - drop it and fall through to the
            // last-resort sweep below rather than getting stuck.
            unit.setAutoExploreTargetTile(null);
        }

        // LarryDGray's Mods: truly nothing to see - no known coast,
        // arctic edge, or deep water, and no fog either, anywhere
        // within range. Per Larry's own framing: with no information
        // at all to act on, just commit to one direction and keep
        // going that way until something comes into view, rather than
        // trying to be clever about it (e.g. still steering away from
        // distant deep water) - there's nothing nearby to be clever
        // about in the first place. Keep the current heading as long
        // as it stays passable; only re-roll a new random direction
        // once it's actually blocked.
        Direction heading = unit.getAutoExploreHeading();
        Tile next = (heading == null) ? null : tile.getNeighbourOrNull(heading);
        if (next == null || isBoundaryLand(next)) {
            final List<Direction> shuffled = new ArrayList<>(Direction.allDirections);
            Collections.shuffle(shuffled);
            heading = null;
            for (Direction d : shuffled) {
                final Tile candidate = tile.getNeighbourOrNull(d);
                if (candidate != null && !isBoundaryLand(candidate)) {
                    heading = d;
                    break;
                }
            }
        }
        if (heading != null) unit.setAutoExploreHeading(heading);
        return heading;
    }

    /**
     * Is a committed OPEN_OCEAN target still worth pursuing? A
     * boundary target never expires on its own (it's a permanent
     * geographic feature - the ship stops pursuing it only once
     * {@link #updatePhase} detects genuine adjacency and switches
     * phase away from OPEN_OCEAN entirely). A fog target expires the
     * moment it becomes explored, since exploring it was the whole
     * point.
     *
     * @param target The current target {@code Tile}, or null.
     * @param wall The mode's boundary predicate, or null for
     *     {@code NEAREST_FOG} (no boundary target is ever usable then).
     * @return True if still usable.
     */
    private static boolean isUsableOpenOceanTarget(Tile target, Predicate<Tile> wall) {
        return target != null
            && (!target.isExplored() || (wall != null && wall.test(target)));
    }

    /**
     * Pick the direction, from {@code order}, whose resulting tile
     * minimises distance to {@code target} - the shared "steer toward
     * a known point" logic used for coastlines, arctic edges, and
     * closest-fog seeking alike.
     *
     * @param tile The unit's current {@code Tile}.
     * @param order The candidate directions to consider, in
     *     preference order for ties.
     * @param target The {@code Tile} to steer toward.
     * @return The best {@code Direction}, or null if none passable.
     */
    private static Direction steerToward(Tile tile, List<Direction> order,
                                         Tile target) {
        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Direction d : order) {
            final Tile next = tile.getNeighbourOrNull(d);
            if (next == null || isBoundaryLand(next)) continue;
            final int dist = next.getDistanceTo(target);
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        return best;
    }

    /**
     * Find the nearest tile matching {@code pred} within {@code radius}
     * of {@code start}, considering only already-explored tiles (the
     * player must have actually seen it at some point).
     *
     * @param start The {@code Tile} to search around.
     * @param radius How far out to look.
     * @param pred What counts as a match.
     * @return The nearest matching {@code Tile}, or null if none.
     */
    private static Tile nearestKnown(Tile start, int radius, Predicate<Tile> pred) {
        Tile best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Tile t : start.getSurroundingTiles(1, radius)) {
            if (t == null || !t.isExplored() || !pred.test(t)) continue;
            final int dist = start.getDistanceTo(t);
            if (dist < bestDist) { bestDist = dist; best = t; }
        }
        return best;
    }

    /**
     * Find the nearest not-yet-explored tile within {@code radius} of
     * {@code start} - literal "closest fog".
     *
     * @param start The {@code Tile} to search around.
     * @param radius How far out to look.
     * @return The nearest unexplored {@code Tile}, or null if the
     *     entire area within range is already explored.
     */
    private static Tile nearestUnexplored(Tile start, int radius) {
        Tile best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Tile t : start.getSurroundingTiles(1, radius)) {
            if (t == null || t.isExplored()) continue;
            final int dist = start.getDistanceTo(t);
            if (dist < bestDist) { bestDist = dist; best = t; }
        }
        return best;
    }

    /**
     * LarryDGray's Mods: the arctic-hugging mover - just keep going in
     * the committed heading (chosen once at first contact with the
     * polar band, per {@link Unit#getAutoExploreHeading}) until it's
     * genuinely no longer possible. No sweep/turn-bias logic is needed
     * here at all: {@code isBoundaryArctic}'s underlying test
     * ({@link Tile#isPolar}) is a pure Y-coordinate band spanning the
     * full width of the map, so a fixed E/W heading can never lose
     * adjacency to it on its own - only running into land crossing
     * that row (which {@code updatePhase} already detects and reroutes
     * to {@code COAST_HUGGING} for) or the literal edge of the map can
     * stop it.
     *
     * @param unit The {@code Unit} to move.
     * @param tile The unit's current {@code Tile}.
     * @return The committed heading, or null if blocked (map edge or
     *     land) - the caller treats null as "fully boxed in" and
     *     disengages Auto Explore, which doubles as this mode's
     *     "reached the end of this edge" stopping condition.
     */
    private static Direction followStraightEdge(Unit unit, Tile tile) {
        final Direction heading = unit.getAutoExploreHeading();
        if (heading == null) return null;
        final Tile next = tile.getNeighbourOrNull(heading);
        if (next == null || isBoundaryLand(next)) return null;
        return heading;
    }

    /**
     * Generic boundary follower, shared by the coastline and deep-
     * water/high-seas hugging phases (the polar edge is geometrically
     * trivial by comparison - see {@link #followStraightEdge}).
     * Candidates are tried closest-to-straight-ahead first: {@code
     * rotate(0)} (continue straight), then {@code rotate(+1),
     * rotate(-1)} (gentle turns either side), then {@code rotate(+2),
     * rotate(-2)} (sharper turns), and finally {@code rotate(+3)} (the
     * sharpest non-reverse turn kept in the sweep), with a full
     * reverse tried only when genuinely boxed in (e.g. a dead-end
     * cove). Falls back to a full compass scan if there is no
     * established heading yet (just entered this phase).
     *
     * Straight-ahead-first, rather than a fixed sharpest-turn-first
     * order, matters for a real reason: on an ordinary straight (or
     * gently curving) stretch of wall, the tile directly BEHIND the
     * ship is just as "confirmed touching the wall" as the tile
     * directly ahead - the ship was just there. A fixed order that
     * checks a near-reversal before straight-ahead will, the instant
     * their {@link #lookaheadWallScore} scores tie (very common near
     * unexplored territory, where the lookahead can't see far enough
     * to tell them apart), pick the near-reversal purely because it
     * happened to be checked first - a real, observed bug where the
     * ship turned around mid-hug and re-walked already-explored
     * territory instead of continuing on. Trying the smallest turn
     * first means a tie always favours continuing over reversing,
     * while a genuine fork or bend still wins on its own merits
     * whenever it scores strictly higher than going straight.
     *
     * Every candidate MUST be CONFIRMED to still be touching the wall
     * afterwards (i.e. its own neighbours include a wall tile) - not
     * merely "not land, not the wall itself" - per Larry's explicit
     * rule: hugging must never step onto open water hoping to
     * reconnect with the coast later, full stop, even if that means
     * making no progress this turn at all. (An earlier version of this
     * method allowed exactly that as a fallback tier - removed after
     * live testing showed it visibly "jumping out into open water.")
     * The only tile allowed to break this ranking entirely is a
     * genuine retreat, straight back the way the ship just came - and
     * even that must still pass the same confirmation check, since it
     * is a true last resort for a dead end, not a step into the
     * unknown.
     *
     * Among candidates that ARE confirmed, the one taken is whichever
     * scores highest on {@link #lookaheadWallScore} - a short (see
     * {@link #BOUNDARY_LOOKAHEAD_STEPS}) look further along that same
     * heading, counting how many of those tiles keep touching the
     * wall too. A boundary that curves does not stay "confirmed" for
     * every candidate equally far out: a heading that follows the
     * curve keeps registering contact for longer, while one that cuts
     * across it loses contact sooner. Ties resolve to the earliest
     * candidate in the closest-to-straight order above.
     *
     * The reverse direction is deliberately EXCLUDED from the main
     * sweep entirely and tried only as a true last resort - even with
     * straight-ahead-first ordering, an actual dead end (nothing else
     * confirmed at all) should still fall through to retreat rather
     * than being offered on equal footing with everything else.
     *
     * @param unit The {@code Unit} to move.
     * @param tile The unit's current {@code Tile}.
     * @param isWall Which boundary type this phase is hugging.
     * @return The chosen {@code Direction}, or null if nothing confirmed
     *     is available at all (including the retreat option) - the
     *     caller treats this as "stuck" and disengages Auto Explore
     *     rather than gambling on unconfirmed open water.
     */
    private static Direction followBoundary(Unit unit, Tile tile,
                                            Predicate<Tile> isWall) {
        final Direction last = unit.getAutoExploreHeading();
        final Set<Tile> recent = new HashSet<>(unit.getAutoExploreRecentTiles());
        final Direction best = computeConfirmedStep(tile, last, isWall, recent::contains);
        if (best != null) {
            unit.setAutoExploreHeading(best); // confirmed tight hug, best trend match
            return best;
        }
        // LarryDGray's Mods: nothing confirmed to keep touching the
        // wall - per Larry's explicit rule, every move while hugging
        // must land on a tile that is itself still next to the wall,
        // full stop, even if that means giving up progress entirely
        // rather than gambling on open water reconnecting with the
        // coast later. The only exception is retreating back the way
        // it just came (still required to pass the SAME confirmation
        // check, since the ship really was just there) - a true last
        // resort for a genuine dead end, not a step into the unknown.
        if (last != null) {
            final Direction reverse = last.getReverseDirection();
            final Tile next = tile.getNeighbourOrNull(reverse);
            if (next != null && !isBoundaryLand(next) && !isWall.test(next)
                && any(next.getSurroundingTiles(1, 1), isWall)) {
                unit.setAutoExploreHeading(reverse);
                return reverse;
            }
        }
        return null;
    }

    /**
     * LarryDGray's Mods: the pure, non-mutating half of {@link
     * #followBoundary} - which confirmed candidate (if any) best
     * continues hugging {@code isWall} from {@code tile}, biased by
     * {@code last} using the same closest-to-straight-first sweep order.
     * Extracted so {@link #buildBoundaryTrace} can walk a purely
     * hypothetical sequence of steps - reading tile data only, never
     * touching {@code unit} - to plan several moves ahead through
     * already-explored territory without committing anything until
     * the real move is actually taken.
     *
     * @param tile The tile to step from.
     * @param last The heading bias, or null for a full compass scan.
     * @param isWall Which boundary type is being hugged.
     * @param avoid LarryDGray's Mods: tiles to PREFER not stepping onto
     *     (the unit's recent-tile history) - tried as a soft
     *     preference first (excluded from the candidate scan), then
     *     retried without the exclusion if that finds nothing at all,
     *     so a genuinely confirmed coastal tile is never refused just
     *     because it happens to be one the ship already visited (e.g.
     *     a tight peninsula tip a hug must legitimately cross again) -
     *     Larry's "always to a square with a coastline next to it"
     *     rule is the one hard constraint, avoiding recent tiles is
     *     only ever a preference on top of it.
     * @return The best confirmed {@code Direction}, or null if nothing
     *     is confirmed at all from here (only the reverse-retreat case
     *     applies then, a real-time-only concern handled solely by
     *     {@link #followBoundary} itself).
     */
    private static Direction computeConfirmedStep(Tile tile, Direction last,
                                                   Predicate<Tile> isWall,
                                                   Predicate<Tile> avoid) {
        // LarryDGray's Mods: closest-to-straight first, not the old fixed
        // sharpest-right-first sweep. On a straight (or nearly straight)
        // stretch of coastline, the wall sits on the same side both ahead
        // of AND behind the ship - the tile it just came from is just as
        // "confirmed" as the tile it should go to next - so a fixed scan
        // order that checks a near-reversal before straight-ahead will
        // tie-break in favor of turning around and re-walking already-
        // explored tiles the instant their scores are equal. Trying
        // straight-ahead first (then progressively sharper turns, right
        // side first as a tie-break only between otherwise-equal turns)
        // means a real fork or bend still gets picked correctly via
        // lookaheadWallScore's higher score for that direction, but a
        // tie always favors continuing on rather than reversing.
        final List<Direction> candidates = (last == null)
            ? Direction.allDirections
            : List.of(last.rotate(0), last.rotate(1), last.rotate(-1),
                      last.rotate(2), last.rotate(-2), last.rotate(3));
        Direction best = null;
        int bestScore = 0; // 0 = "not confirmed at all" - never accepted below
        Direction bestAnyway = null; // best confirmed candidate even if avoided
        int bestAnywayScore = 0;
        for (Direction d : candidates) {
            final Tile next = tile.getNeighbourOrNull(d);
            if (next == null) continue;
            if (isBoundaryLand(next)) continue; // land always blocks
            if (isWall.test(next)) continue; // don't step onto the wall itself
            // LarryDGray's Mods: Larry's rule is radius-1, full stop - the
            // tile actually being moved onto must itself touch the wall.
            // lookaheadWallScore's multi-tile peek is only a tie-break
            // between candidates that already pass this; on its own it
            // was previously enough to "confirm" a step whose destination
            // tile touched nothing, purely because a DIFFERENT, unrelated
            // patch of wall happened to sit two tiles further along that
            // heading - a real, observed bug where the ship abandoned the
            // coastline it was hugging to angle toward a distant, wholly
            // unconnected landmass just because it was within lookahead
            // range.
            if (!any(next.getSurroundingTiles(1, 1), isWall)) continue;
            final int score = lookaheadWallScore(next, d, isWall);
            if (score > bestAnywayScore) {
                bestAnywayScore = score;
                bestAnyway = d;
            }
            if (avoid.test(next)) continue; // prefer not revisiting, but see bestAnyway
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return (best != null) ? best : bestAnyway;
    }

    /**
     * LarryDGray's Mods: safety/performance bound on {@link
     * #buildBoundaryTrace} - not a real limit on how far a boundary
     * can be traced, just a cap on how much work one trace-build call
     * does. If ever hit, the ship simply re-traces further once it
     * reaches this point (by then, even more may be explored) rather
     * than planning unboundedly far ahead in one call.
     */
    private static final int MAX_TRACE_LENGTH = 40;

    /**
     * LarryDGray's Mods: same safety/performance bound as {@link
     * #MAX_TRACE_LENGTH}, but for the {@code ignoreFog=true} (server-
     * side, ground-truth) case - larger because a trace no longer
     * limited by what the requesting player has personally explored
     * can sensibly cover much more ground in one go (e.g. an entire
     * coastline), and the cost is paid once on the server per request,
     * not client-side every step.
     */
    private static final int MAX_SERVER_TRACE_LENGTH = 200;

    /**
     * LarryDGray's Mods: trace the connected chain of confirmed
     * {@code isWall}-adjacent tiles starting from {@code start}, as
     * far as ALREADY-EXPLORED map data allows - the "map out the edge"
     * fix for circling through territory the game already has complete
     * information about. Walks a purely hypothetical sequence of
     * {@link #computeConfirmedStep} calls (never touching {@code unit}
     * or any real map state) and stops the instant it would need to
     * guess: the next tile is unexplored (the genuine frontier of
     * knowledge - planning through fog would defeat the whole point,
     * since the ship hasn't actually discovered what's there yet), a
     * genuine fork exists at the next tile ({@link #confirmedRuns}
     * finds 2+ continuations - stops BEFORE stepping onto it, so the
     * ship's real walk naturally triggers {@link #getPendingChoice}
     * for it exactly once it actually arrives there), or nothing is
     * confirmed at all (the frontier/fallback/reverse case, left
     * entirely to {@link #followBoundary} for that one real step).
     *
     * Because this only ever extends through tiles that are genuinely
     * connected by the same adjacency test the wall-follower already
     * uses, two boundary stretches that are not actually adjacent to
     * each other (e.g. the east and west high-seas edges of a map,
     * with ordinary ocean or land between them) can never be confused
     * for one continuous edge - a trace started on one can never
     * wander onto the other. No special-casing needed for that; it
     * falls directly out of tracing real connectivity instead of
     * guessing one tile at a time.
     *
     * @param start The tile to trace from (the unit's real current tile).
     * @param startHeading The heading bias to start from (the unit's
     *     current committed heading), or null for a full compass scan
     *     on the very first step.
     * @param isWall Which boundary type is being hugged.
     * @param ignoreFog LarryDGray's Mods: if true, keep extending the
     *     trace through tiles the requesting player has not personally
     *     explored, using their real terrain (always present on the
     *     server's own {@code Tile} objects, absent on the client's).
     *     Only ever true when called server-side, in response to a
     *     {@code RequestBoundaryTraceMessage} - the resulting path is
     *     still just a bare list of headings, so this never reveals
     *     any actual map data to the player; their own fog-of-war
     *     reveal still happens normally as the ship physically visits
     *     each tile. A genuine fork still stops the trace either way,
     *     since that is a real geographic property, not a fog-of-war
     *     artifact, and still needs the player's own input once the
     *     ship's real position gets there.
     * @param avoidTiles LarryDGray's Mods: tiles to treat as already
     *     visited from the very start - the unit's own recent-tile
     *     history ({@code Unit.getAutoExploreRecentTiles()}), so a
     *     loop spanning SEVERAL separate trace-build calls (confirmed
     *     live: a real 3-tile cycle, each individual call short and
     *     cycle-free on its own) gets caught too, not just a loop
     *     entirely within one call.
     * @return The traced sequence of headings, oldest first; empty if
     *     not even a single step could be confirmed from {@code start}.
     */
    public static List<Direction> buildBoundaryTrace(Tile start, Direction startHeading,
                                                      Predicate<Tile> isWall, boolean ignoreFog,
                                                      Collection<Tile> avoidTiles) {
        final List<Direction> path = new ArrayList<>();
        // LarryDGray's Mods: tiles already included earlier in THIS
        // trace, PLUS the unit's own recent real-move history passed
        // in via avoidTiles - prevents both a short local ping-pong
        // within one call (observed live: a real E/SW/E/SW loop
        // between two tiles) and a longer loop spanning several
        // separate calls (observed live: a real 3-tile cycle). Scoped
        // to recent history only, not the ship's whole lifetime - a
        // full circumnavigation is still fine and expected, it just
        // eventually moves far enough that old tiles age out of the
        // recent list.
        final Set<Tile> visited = new HashSet<>(avoidTiles);
        visited.add(start);
        Tile tile = start;
        Direction heading = startHeading;
        final int maxLength = ignoreFog ? MAX_SERVER_TRACE_LENGTH : MAX_TRACE_LENGTH;
        for (int i = 0; i < maxLength; i++) {
            // LarryDGray's Mods: pass the growing visited set as a soft
            // avoid-preference too - lets computeConfirmedStep find a
            // DIFFERENT confirmed candidate that doesn't revisit,
            // rather than only ever considering the single best-
            // scoring one and giving up the instant that one happens
            // to loop back. The hard break below still catches the
            // case where looping back is genuinely the only option.
            final Direction d = computeConfirmedStep(tile, heading, isWall, visited::contains);
            if (d == null) break; // nothing confirmed - defer to followBoundary()
            final Tile next = tile.getNeighbourOrNull(d);
            if (next == null) break; // off the map
            if (visited.contains(next)) break; // would loop back onto this trace itself
            path.add(d);
            if (!ignoreFog && !next.isExplored()) break; // frontier of knowledge
            if (confirmedRuns(next, isWall).size() >= 2) break; // stop BEFORE a fork
            visited.add(next);
            tile = next;
            heading = d;
        }
        return path;
    }

    /**
     * LarryDGray's Mods: the real-time entry point for the coastline
     * and deep-water/high-seas hugging phases - walks a precomputed
     * {@link Unit#getAutoExplorePath} one real step per call, only
     * (re)building a fresh trace via {@link #buildBoundaryTrace} once
     * the stored path runs out. Falls back to plain {@link
     * #followBoundary} whenever a fresh trace comes back empty (the
     * frontier/fallback/reverse case a hypothetical trace deliberately
     * never plans through - see that method's own doc comment).
     *
     * @param unit The {@code Unit} to move.
     * @param tile The unit's current {@code Tile}.
     * @param isWall Which boundary type this phase is hugging.
     * @return The chosen {@code Direction}, or null if fully boxed in.
     */
    private static Direction followTracedBoundary(Unit unit, Tile tile,
                                                   Predicate<Tile> isWall) {
        List<Direction> path = unit.getAutoExplorePath();
        if (path != null && !path.isEmpty()) {
            final Direction d = path.remove(0);
            unit.setAutoExploreHeading(d);
            unit.setAutoExplorePath(path);
            return d;
        }
        final List<Direction> trace = buildBoundaryTrace(tile, unit.getAutoExploreHeading(), isWall,
            false, unit.getAutoExploreRecentTiles());
        if (trace.isEmpty()) {
            return followBoundary(unit, tile, isWall);
        }
        final Direction first = trace.remove(0);
        unit.setAutoExploreHeading(first);
        unit.setAutoExplorePath(trace);
        return first;
    }

    /**
     * LarryDGray's Mods: how many tiles ahead {@link
     * #lookaheadWallScore} looks along a candidate heading to gauge
     * how well that heading tracks a curving boundary (coastline or
     * deep-water/high-seas line - the polar edge needs none of this,
     * see {@link #followStraightEdge}). Kept short deliberately: this
     * is a same-turn tie-break between already-passable, already-
     * radius-1-confirmed candidates, not a path search - a few tiles
     * is enough to distinguish "follows the curve" from "cuts across
     * it" without meaningfully increasing the per-step cost.
     */
    private static final int BOUNDARY_LOOKAHEAD_STEPS = 3;

    /**
     * LarryDGray's Mods: score how well continuing in direction
     * {@code d} from {@code start} tracks {@code isWall} a few tiles
     * further out - the higher the score, the longer that heading
     * keeps touching the boundary before running into land, the wall
     * itself, unexplored territory, or the map edge. This is what lets
     * {@link #followBoundary} tell "this direction follows the curve"
     * from "this direction cuts across it" when more than one
     * candidate is confirmed at the immediate, radius-1 level - a
     * boundary that bends does not stay confirmed equally far out in
     * every direction that starts out looking valid.
     *
     * @param start The first tile in direction {@code d} from the
     *     unit's current position (already confirmed passable and not
     *     the wall itself by the caller).
     * @param d The heading being scored.
     * @param isWall Which boundary type is being hugged.
     * @return How many of the first {@link #BOUNDARY_LOOKAHEAD_STEPS}
     *     tiles starting at {@code start} (inclusive) are themselves
     *     confirmed touching the wall, stopping early at the first
     *     blocked (land/wall-itself/off-map) tile.
     */
    private static int lookaheadWallScore(Tile start, Direction d,
                                          Predicate<Tile> isWall) {
        int score = 0;
        Tile cur = start;
        for (int step = 0; step < BOUNDARY_LOOKAHEAD_STEPS; step++) {
            if (cur == null || isBoundaryLand(cur) || isWall.test(cur)) break;
            if (any(cur.getSurroundingTiles(1, 1), isWall)) score++;
            cur = cur.getNeighbourOrNull(d);
        }
        return score;
    }

    /**
     * Build a deterministic direction order favouring {@code preferred}:
     * itself first, then alternating one step clockwise/anticlockwise
     * out to the full reverse. Used to keep OPEN_OCEAN travel roughly
     * straight instead of scanning in a fixed compass order every time.
     *
     * @param preferred The {@code Direction} to favour.
     * @return The ordered list of all 8 directions.
     */
    private static List<Direction> closestOrder(Direction preferred) {
        List<Direction> result = new ArrayList<>(Direction.NUMBER_OF_DIRECTIONS);
        result.add(preferred);
        for (int step = 1; step < Direction.NUMBER_OF_DIRECTIONS / 2; step++) {
            result.add(preferred.rotate(step));
            result.add(preferred.rotate(-step));
        }
        result.add(preferred.getReverseDirection());
        return result;
    }
}
