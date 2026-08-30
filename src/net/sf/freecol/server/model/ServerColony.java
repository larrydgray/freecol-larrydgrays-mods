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

package net.sf.freecol.server.model;

import static net.sf.freecol.common.util.CollectionUtils.any;
import static net.sf.freecol.common.util.CollectionUtils.transform;
import static net.sf.freecol.common.util.RandomUtils.getRandomMember;
import static net.sf.freecol.common.util.RandomUtils.randomShuffle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildQueue;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.ExportData;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldCategory;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsLocation;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TypeCountMap;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitChangeType;
import net.sf.freecol.common.model.UnitLocation.NoAddReason;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.LogBuilder;


/**
 * The server version of a colony.
 */
public class ServerColony extends Colony implements TurnTaker {

    private static final Logger logger = Logger.getLogger(ServerColony.class.getName());


    /**
     * Trivial constructor for Game.newInstance.
     *
     * @param game The {@code Game} in which this object belongs.
     * @param id The object identifier.
     */
    public ServerColony(Game game, String id) {
        super(game, id);
    }

    /**
     * Creates a new ServerColony.
     *
     * @param game The {@code Game} in which this object belongs.
     * @param owner The {@code Player} owning this {@code Colony}.
     * @param name The name of the new {@code Colony}.
     * @param tile The location of the {@code Colony}.
     */
    public ServerColony(Game game, Player owner, String name, Tile tile) {
        super(game, owner, name, tile);
        Specification spec = getSpecification();

        setGoodsContainer(new GoodsContainer(game, this));
        sonsOfLiberty = 0;
        oldSonsOfLiberty = 0;
        established = game.getTurn();

        ColonyTile colonyTile = new ServerColonyTile(game, this, tile);
        colonyTiles.add(colonyTile);
        for (Tile t : tile.getSurroundingTiles(getRadius())) {
            colonyTiles.add(new ServerColonyTile(game, this, t));
        }

        Building building;
        List<BuildingType> buildingTypes = spec.getBuildingTypeList();
        for (BuildingType buildingType : transform(buildingTypes, bt ->
                bt.isAutomaticBuild() || isAutomaticBuild(bt))) {
            addBuilding(new ServerBuilding(getGame(), this, buildingType));
        }
        // Set up default production queues.  Do this after calling
        // addBuilding because that will check build queue integrity,
        // and these might fail the population check.
        // FIXME: express this in the spec somehow.
        if (isLandLocked()) {
            buildQueue.add(spec.getBuildingType("model.building.warehouse"));
        } else {
            buildQueue.add(spec.getBuildingType("model.building.docks"));
            addPortAbility();
        }
        for (UnitType unitType : transform(spec.getUnitTypesWithAbility(Ability.BORN_IN_COLONY),
                                           UnitType::needsGoodsToBuild)) {
            populationQueue.add(unitType);
        }
    }

    /**
     * Is a goods type needed for a buildable that this colony could
     * be building.
     *
     * @param goodsType The {@code GoodsType} to check.
     * @return True if the goods could be used to build something.
     */
    private boolean neededForBuildableType(GoodsType goodsType) {
        final Specification spec = getSpecification();
        List<BuildableType> buildables = new ArrayList<>();
        buildables.addAll(spec.getBuildingTypeList());
        buildables.addAll(spec.getUnitTypesWithoutAbility(Ability.PERSON));
        return any(buildables, bt -> canBuild(bt)
            && any(bt.getRequiredGoods(), AbstractGoods.matches(goodsType)));
    }

    /**
     * Build a unit from a build queue.
     *
     * @param buildQueue The {@code BuildQueue} to find the unit in.
     * @param random A pseudo-random number source.
     * @param cs A {@code ChangeSet} to update.
     * @return The unit that was built.
     */
    private Unit csBuildUnit(BuildQueue<? extends BuildableType> buildQueue,
                             Random random, ChangeSet cs) {
        UnitType type = (UnitType)buildQueue.getCurrentlyBuilding();
        Unit unit = new ServerUnit(getGame(), getTile(), owner,
                                   type);//-vis: safe, within colony
        unit.setName(owner.getNameForUnit(type, random));
        if (unit.hasAbility(Ability.BORN_IN_COLONY)) {
            cs.addMessage(owner,
                          new ModelMessage(MessageType.UNIT_ADDED,
                                           "model.colony.newColonist",
                                           this, unit)
                              .addName("%colony%", getName()));
        } else {
            unit.setName(owner.getNameForUnit(type, random));
            cs.addMessage(owner,
                          new ModelMessage(MessageType.UNIT_ADDED,
                                           "model.colony.unitReady",
                                           this, unit)
                              .addName("%colony%", getName())
                              .addStringTemplate("%unit%", unit.getLabel()));
        }

        logger.info("New unit in " + getName() + ": " + type.getSuffix());
        return unit;
    }

    /**
     * Eject units to any available work location.
     *
     * Called on building type changes, see below and
     * @see ServerPlayer#csDamageBuilding
     *
     * -til: Might change the visible colony size.
     *
     * @param workLocation The {@code WorkLocation} to eject from.
     * @param units A list of {@code Unit}s to eject.
     * @return True if units were ejected.
     */
    public boolean ejectUnits(WorkLocation workLocation, List<Unit> units) {
        if (units == null || units.isEmpty()) return false;
        unit: for (Unit u : units) {
            for (WorkLocation wl : transform(getAvailableWorkLocations(),
                                             w -> w != workLocation && w.canAdd(u))) {
                u.setLocation(wl);//-vis: safe/colony
                continue unit;
            }
            u.setLocation(getTile());//-vis: safe/colony
        }
        if (getOwner().isAI()) {
            firePropertyChange(REARRANGE_COLONY, true, false);
        }
        return true;
    }

    /**
     * Builds a building from a build queue.
     *
     * @param buildQueue The {@code BuildQueue} to build from.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the build succeeded.
     */
    private boolean csBuildBuilding(BuildQueue<? extends BuildableType> buildQueue,
                                    ChangeSet cs) {
        BuildingType type = (BuildingType) buildQueue.getCurrentlyBuilding();
        Tile copied = getTile().getTileToCache();
        BuildingType from = type.getUpgradesFrom();
        boolean success;
        if (from == null) {
            success = buildBuilding(new ServerBuilding(getGame(), this, type));//-til
        } else {
            Building building = getBuilding(from);
            List<Unit> eject = building.upgrade();//-til
            success = eject != null;
            if (success) {
                ejectUnits(building, eject);//-til
                if (!eject.isEmpty()) getTile().cacheUnseen(copied);//+til
            } else {
                cs.addMessage(owner, getUnbuildableMessage(type));
            }
        }
        if (success) {
            cs.addMessage(owner,
                new ModelMessage(MessageType.BUILDING_COMPLETED,
                                 "model.colony.buildingReady", this)
                    .addName("%colony%", getName())
                    .addNamed("%building%", type));
            if (owner.isAI()) {
                firePropertyChange(REARRANGE_COLONY, true, false);
            }
            logger.info("New building in " + getName()
                + ": " + type.getSuffix());
        }
        return success;
    }

    /**
     * Removes a buildable from a build queue, and updates the queue so that
     * a valid buildable is now being built if possible.
     *
     * @param queue The {@code BuildQueue} to update.
     * @param cs A {@code ChangeSet} to update.
     * @return The next buildable that can be built, or null if nothing.
     */
    private BuildableType csNextBuildable(BuildQueue<? extends BuildableType> queue,
                                          ChangeSet cs) {
        Specification spec = getSpecification();
        Player owner = getOwner();
        BuildableType buildable;
        boolean invalidate = false;
        final Predicate<GoodsType> notBuildingPred
            = gt -> gt.isBuildingMaterial() && !gt.isStorable()
                && getTotalProductionOf(gt) > 0;

        while ((buildable = queue.getCurrentlyBuilding()) != null) {
            switch (getNoBuildReason(buildable, null)) {
            case LIMIT_EXCEEDED:
                // Expected when a player builds its last available wagon
                // and there is nothing else in the build queue.
                break;
            case NONE:
                return buildable;
            case NOT_BUILDING:
                if (any(spec.getGoodsTypeList(), notBuildingPred)) {
                    cs.addMessage(owner,
                        new ModelMessage(MessageType.WARNING,
                                         "model.colony.cannotBuild", this)
                            .addName("%colony%", getName()));
                }
                return null;

            case POPULATION_TOO_SMALL:
                cs.addMessage(owner,
                    new ModelMessage(MessageType.WARNING,
                                     "model.colony.buildNeedPop",
                                     this)
                        .addName("%colony%", getName())
                        .addNamed("%building%", buildable));
                break;
            default: // Are there other warnings to send?
                logger.warning("Unexpected build failure at " + getName()
                    + " for " + buildable
                    + ": " + getNoBuildReason(buildable, null));
                cs.addMessage(owner,
                              getUnbuildableMessage(buildable));
                break;
            }
            queue.remove(0);
            invalidate = true;
        }
        if (invalidate) invalidateCache();
        return null;
    }

    /**
     * Evict the users from a tile used by this colony, due to military
     * action from another unit.
     *
     * @param enemyUnit The {@code Unit} that has moved in.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csEvictUsers(Unit enemyUnit, ChangeSet cs) {
        Player player = getOwner();
        Tile tile = enemyUnit.getTile();
        ServerColonyTile ct = (ServerColonyTile)getColonyTile(tile);
        if (ct == null) return;
        Tile colonyTile = ct.getColony().getTile();
        Tile copied = colonyTile.getTileToCache();
        if (!ejectUnits(ct, ct.getUnitList())) return;//-til
        colonyTile.cacheUnseen(copied);//+til
        cs.addMessage(player,
            new ModelMessage(MessageType.WARNING,
                             "model.colony.workersEvicted",
                             this, this)
                .addName("%colony%", getName())
                .addStringTemplate("%location%", tile.getLocationLabel())
                .addStringTemplate("%enemyUnit%", enemyUnit.getLabel()));
        cs.add(See.only(player), ct);
        cs.add(See.perhaps(), getTile()); // Colony size might have changed
    }

    /**
     * Change the owner of this colony.
     *
     * -vis: Owner and new owner
     *
     * @param newOwner The new owning {@code Player}.
     * @param reassign If true, reassign the colony tiles.
     * @param change An optional accompanying change type for the units.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csChangeOwner(Player newOwner, boolean reassign, String change,
                              ChangeSet cs) {
        final Player oldOwner = getOwner();
        final Tile tile = getTile();
        final Set<Tile> owned = getOwnedTiles();
        final Set<Tile> unseen
            = ((ServerPlayer)newOwner).collectNewTiles(getVisibleTileSet());

        for (Tile t : owned) t.cacheUnseen(newOwner);//+til
        changeOwner(newOwner);//-vis(both),-til

        List<Unit> units = getAllUnitsList();
        for (Unit u : units) {//-vis(both)
            ((ServerPlayer)oldOwner).csChangeOwner(u, newOwner, change, null, cs);
        }
        cs.addRemoves(See.only(oldOwner), this, units);

        // Disable all exports
        for (ExportData exportDatum : exportData.values()) {
            exportDatum.setExported(false);
        }

        // Clear the build queue
        buildQueue.clear();

        // Used to add free buildings here, but now doing it at the end
        // of the turn

        // Changing the owner might alter bonuses applied by founding fathers:
        updateSoL();
        updateProductionBonus();

        // Allow other neighbouring settlements a chance to claim this
        // colony's tiles except for the center.  Do this after the
        // general change of ownership so that neighbouring attacker units
        // are not seen as tile occupiers that will impeded tile transfers.
        if (reassign) {
            owned.remove(tile);
            ((ServerPlayer)oldOwner).reassignTiles(owned, this);//-til
            owned.add(tile);

            // Make sure units are ejected when a tile is no longer
            // attached to the settlement.
            for (Tile t : transform(owned, t2 -> t2.getOwningSettlement() != this)) {
                ColonyTile ct = getColonyTile(t);
                ejectUnits(ct, ct.getUnitList());
            }
        }

        // Always update the new owner
        unseen.addAll(owned);
        cs.add(See.only(newOwner), unseen);

        // Always update the old owner, and perhaps others.
        cs.add(See.perhaps().always(oldOwner).except(newOwner), owned);
    }

    /**
     * Add a free building to this colony.
     *
     * Triggered directly by election of laSalle, or at end of turn.
     *
     * @param type The {@code BuildingType} to add.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csFreeBuilding(BuildingType type, ChangeSet cs) {
        if (!canBuild(type)) return;

        // Does this free building displace an existing one?
        Building found = getBuilding(type);
        List<Unit> present = (found == null) ? Collections.<Unit>emptyList()
            : found.getUnitList();

        final Player owner = getOwner();
        Building build = new ServerBuilding(getGame(), this, type);
        buildBuilding(build);//-til
        checkBuildQueueIntegrity(true, null);
        cs.add(See.only(owner), this);
        if (owner.isAI()) {
            firePropertyChange(Colony.REARRANGE_COLONY, true, false);
        }

        for (Unit u : present) u.setLocation(build);
    }

    /**
     * Build a new building in this colony.
     *
     * @param building The {@code Building} to build.
     * @return True if the building was built.
     */
    private boolean buildBuilding(Building building) {
        Tile copied = (building.getType().isDefenceType())
            ? getTile().getTileToCache() : null;
        if (!addBuilding(building)) return false;
        getTile().cacheUnseen(copied);
        invalidateCache();
        return true;
    }

    /**
     * Equip a unit for a specific role.
     *
     * @param unit The {@code Unit} to equip.
     * @param role The {@code Role} to equip for.
     * @param roleCount The role count.
     * @param random A pseudo-random number source.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the equipping succeeds.
     */
    public boolean csEquipForRole(Unit unit, Role role, int roleCount,
                                  Random random, ChangeSet cs) {
        boolean ret = equipForRole(unit, role, roleCount);

        if (ret) {
            if (unit.isOnCarrier()) unit.setMovesLeft(0);
            Tile tile = getTile();
            tile.cacheUnseen();//+til
            unit.setLocation(tile);
            cs.add(See.perhaps(), tile);
        }
        return ret;
    }

    /**
     * Add a new convert to this colony.
     *
     * @param brave The convert {@code Unit}.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csAddConvert(Unit brave, ChangeSet cs) {
        if (brave == null) return;
        final Player newOwner = getOwner();
        final ServerPlayer oldOwner = (ServerPlayer)brave.getOwner();

        if (oldOwner.csChangeOwner(brave, newOwner, UnitChangeType.CONVERSION,
                                   getTile(), cs)) { //-vis(other)
            brave.changeRole(getSpecification().getDefaultRole(), 0);
            for (Goods g : brave.getCompactGoodsList()) brave.removeGoods(g);
            brave.setMovesLeft(0);
            brave.setState(Unit.UnitState.ACTIVE);
            cs.addDisappear(newOwner, tile, brave);
            cs.add(See.only(newOwner), getTile());
            StringTemplate nation = oldOwner.getNationLabel();
            cs.addMessage(newOwner,
                new ModelMessage(MessageType.UNIT_ADDED,
                                "model.colony.newConvert", brave)
                    .addStringTemplate("%nation%", nation)
                    .addName("%colony%", getName()));
            newOwner.invalidateCanSeeTiles();//+vis(other)
            logger.fine("Convert at " + getName() + " for " + getName());
        }
    }

    /**
     * Destroy an existing building in this colony.
     *
     * @param building The {@code Building} to destroy.
     * @return True if the building was destroyed.
     */
    public boolean destroyBuilding(Building building) {
        Tile copied = (building.getType().isDefenceType())
            ? getTile().getTileToCache() : null;
        if (!removeBuilding(building)) return false;
        getTile().cacheUnseen(copied);
        invalidateCache();
        checkBuildQueueIntegrity(true, null);
        return true;
    }

    /**
     * LarryDGray's Mods: redirect would-be-wasted goods into an idle,
     * fortified, non-trade-route wagon train or ship at this colony's
     * tile, instead of destroying them. Fortified is read here as
     * "this carrier is deliberately parked, available for something"
     * rather than its usual defensive meaning.
     *
     * @param type The {@code GoodsType} overflowing.
     * @param amount The amount that would otherwise be wasted.
     * @param cs A {@code ChangeSet} to update.
     * @return The amount actually redirected (0 if none, or if the
     *     master {@link GameOptions#ENABLE_WAREHOUSE_OVERFLOW} option
     *     is off).
     */
    private int redirectOverflowToCarrier(GoodsType type, int amount, ChangeSet cs) {
        // LarryDGray's Mods: a continued save's embedded spec predates
        // this option and has no fallback - spec.getBoolean() would
        // throw a hard RuntimeException on every colony's turn
        // processing rather than just no-op, so guard with hasOption().
        final Specification spec = getSpecification();
        if (amount <= 0
            || !spec.hasOption(GameOptions.ENABLE_WAREHOUSE_OVERFLOW, BooleanOption.class)
            || !spec.getBoolean(GameOptions.ENABLE_WAREHOUSE_OVERFLOW)) {
            return 0;
        }
        int remaining = amount;
        for (Unit u : getTile().getUnitList()) {
            if (remaining <= 0) break;
            if (!u.isCarrier() || u.getTradeRoute() != null
                || u.getState() != UnitState.FORTIFIED) continue;
            int loadable = u.getLoadableAmount(type);
            if (loadable <= 0) continue;
            int moveAmount = Math.min(remaining, loadable);
            GoodsLocation.moveGoods(this, type, moveAmount, u);
            // LarryDGray's Mods: without this, the carrier's cargo
            // change is applied server-side but never reaches the
            // client - the Cargo panel would show stale data
            // indefinitely, frozen at whatever it last happened to
            // sync (this exact bug shipped and was caught live).
            cs.add(See.only(getOwner()), u);
            remaining -= moveAmount;
        }
        int redirected = amount - remaining;
        if (redirected > 0) {
            cs.addMessage(getOwner(),
                new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
                                 "model.colony.warehouseOverflowRedirected",
                                 this, type)
                    .addNamed("%goods%", type)
                    .addAmount("%amount%", redirected)
                    .addName("%colony%", getName()));
        }
        return redirected;
    }

    /**
     * LarryDGray's Mods: the reverse of {@link
     * #redirectOverflowToCarrier} - when this colony's warehouse has
     * room for a goods type again (freed up by consumption in
     * production, a trade route pickup, etc.), pull it back in from
     * any fortified, non-trade-route carrier parked at the colony's
     * tile holding some.
     *
     * @param type The {@code GoodsType} to drain back in.
     * @param room How much more of this type the warehouse can
     *     currently hold.
     * @param cs A {@code ChangeSet} to update.
     * @return The amount actually drained back in (0 if none, or if
     *     the master {@link GameOptions#ENABLE_WAREHOUSE_OVERFLOW}
     *     option is off).
     */
    private int drainCarrierIntoWarehouse(GoodsType type, int room, ChangeSet cs) {
        final Specification spec = getSpecification();
        if (room <= 0
            || !spec.hasOption(GameOptions.ENABLE_WAREHOUSE_OVERFLOW, BooleanOption.class)
            || !spec.getBoolean(GameOptions.ENABLE_WAREHOUSE_OVERFLOW)) {
            return 0;
        }
        int remaining = room;
        for (Unit u : getTile().getUnitList()) {
            if (remaining <= 0) break;
            if (!u.isCarrier() || u.getTradeRoute() != null
                || u.getState() != UnitState.FORTIFIED) continue;
            int available = u.getGoodsCount(type);
            if (available <= 0) continue;
            int moveAmount = Math.min(remaining, available);
            GoodsLocation.moveGoods(u, type, moveAmount, this);
            // LarryDGray's Mods: same client-sync fix as
            // redirectOverflowToCarrier - the carrier's cargo change
            // must be explicitly added to the ChangeSet.
            cs.add(See.only(getOwner()), u);
            remaining -= moveAmount;
        }
        int drained = room - remaining;
        if (drained > 0) {
            cs.addMessage(getOwner(),
                new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
                                 "model.colony.warehouseOverflowReturned",
                                 this, type)
                    .addNamed("%goods%", type)
                    .addAmount("%amount%", drained)
                    .addName("%colony%", getName()));
        }
        return drained;
    }

    /**
     * LarryDGray's Mods: snapshot of one unit's work assignment, used
     * to build the Manager's Undo state.
     */
    private static final class ManagerUndoEntry {
        final Unit unit;
        final Location location;
        final GoodsType workType;
        ManagerUndoEntry(Unit unit, Location location, GoodsType workType) {
            this.unit = unit;
            this.location = location;
            this.workType = workType;
        }
    }

    /**
     * LarryDGray's Mods: a candidate worker move found while scanning
     * for the best available reassignment toward the Manager's
     * target goods type. {@code rescueUnit}/{@code rescueLocation} are
     * non-null only when this move would otherwise cause imminent
     * starvation and a second, compensating move (some other unit
     * pulled onto food) was found to keep it safe - see
     * {@link #findManagerRescue}.
     */
    private static final class Candidate {
        final Unit unit;
        final WorkLocation location;
        final int newNet;
        final Unit rescueUnit;
        final WorkLocation rescueLocation;
        Candidate(Unit unit, WorkLocation location, int newNet) {
            this(unit, location, newNet, null, null);
        }
        Candidate(Unit unit, WorkLocation location, int newNet,
                  Unit rescueUnit, WorkLocation rescueLocation) {
            this.unit = unit;
            this.location = location;
            this.newNet = newNet;
            this.rescueUnit = rescueUnit;
            this.rescueLocation = rescueLocation;
        }
    }

    /**
     * LarryDGray's Mods: snapshot of this colony's worker arrangement
     * immediately before the Manager's most recent reassignment pass,
     * or null if there is nothing to undo. Deliberately not persisted
     * - a stale snapshot from a previous session would rarely be
     * useful once several turns have passed anyway.
     */
    private transient List<ManagerUndoEntry> managerUndo = null;

    /**
     * LarryDGray's Mods: apply this colony's Manager goal (if any),
     * reassigning workers to maximize net production of the target
     * goods type. Runs once per turn from csNewTurn(), before this
     * turn's production is snapshotted, so this turn's production
     * already reflects any reassignment made here - and is also
     * called immediately whenever the goal is changed (see
     * {@code InGameController.setColonyManager}), so a player sees
     * the effect right away rather than waiting for the next turn to
     * process, matching how similar auto-management features work in
     * other 4X games.
     *
     * @param cs A {@code ChangeSet} to update.
     */
    public void csApplyManager(ChangeSet cs) {
        final Specification spec = getSpecification();
        if (!spec.hasOption(GameOptions.ENABLE_COLONY_MANAGER, BooleanOption.class)
            || !spec.getBoolean(GameOptions.ENABLE_COLONY_MANAGER)
            || getManagerGoal() == ManagerGoal.UNMANAGED) {
            return;
        }
        final GoodsType target = getManagerGoal().getGoodsType(spec);
        if (target == null) return; // defensive: goods type missing from this ruleset

        final ServerPlayer owner = (ServerPlayer)getOwner();
        List<ManagerUndoEntry> snapshot = new ArrayList<>();
        for (Unit u : getUnitList()) {
            snapshot.add(new ManagerUndoEntry(u, u.getLocation(), u.getWorkType()));
        }

        boolean changed = false;
        int currentNet = getAdjustedNetProductionOf(target);
        final int maxIterations = getUnitCount();
        for (int i = 0; i < maxIterations; i++) {
            Candidate best = findBestManagerMove(target, currentNet);
            if (best == null) break;
            best.unit.setLocation(best.location);
            best.unit.changeWorkType(target);
            cs.add(See.only(owner), best.unit);
            // LarryDGray's Mods: apply the paired rescue move (if any)
            // alongside the primary one - see findManagerRescue() for
            // why a "improving" move can require a second unit pulled
            // onto food to stay safe. Still counts as one iteration of
            // this outer loop.
            if (best.rescueUnit != null) {
                best.rescueUnit.setLocation(best.rescueLocation);
                best.rescueUnit.changeWorkType(spec.getPrimaryFoodType());
                cs.add(See.only(owner), best.rescueUnit);
            }
            currentNet = best.newNet;
            changed = true;
        }

        if (changed) {
            this.managerUndo = snapshot;
            cs.addMessage(owner, new ModelMessage(MessageType.GOODS_MOVEMENT,
                                                  "model.colony.managerReassigned",
                                                  this)
                .addName("%colony%", getName())
                .addNamed("%goods%", target));
            cs.add(See.only(owner), this);
        }
    }

    /**
     * LarryDGray's Mods: how many turns of advance warning counts as
     * "imminent" starvation for the Colony Manager's safety check -
     * per Larry's own wording, a candidate move must not cause
     * starvation "in current turn, or even next turn" (Colony.
     * getStarvationTurns() values of 0 or 1, i.e. strictly less than
     * this threshold; -1 means not starving at all).
     */
    private static final int MANAGER_STARVATION_SAFETY_TURNS = 2;

    /**
     * LarryDGray's Mods: scan for the single best available worker
     * move toward the Manager's target goods type, trying tiers in
     * order (1 = least protected) and stopping at the first tier
     * that has any improving move at all - a more-protected tier is
     * never touched while a less-protected one still helps.
     *
     * A move that would improve target's net production but newly
     * push this colony into imminent starvation (see {@link
     * #MANAGER_STARVATION_SAFETY_TURNS}) is not accepted outright -
     * {@link #findManagerRescue} is tried first, to see if a second,
     * compensating unit can be pulled onto food to keep it safe while
     * still making the original move. Only if no rescue exists is the
     * candidate discarded. A colony already unsafe before this scan
     * even starts (for unrelated reasons) is not blocked further -
     * only a *new* regression caused by the Manager itself triggers
     * this. Goal == the primary food type skips the whole check: every
     * accepted move there can only increase food's own net production,
     * so it can never worsen starvation.
     *
     * @param target The target {@code GoodsType}.
     * @param currentNet This colony's current net production of target.
     * @return The best {@code Candidate} move found, or null if none
     *     of the three tiers has any improving move.
     */
    private Candidate findBestManagerMove(GoodsType target, int currentNet) {
        final GoodsType foodType = getSpecification().getPrimaryFoodType();
        final boolean checkStarvation = target != foodType;
        final int startingStarvation = getStarvationTurns();
        final boolean startedSafe = startingStarvation < 0
            || startingStarvation >= MANAGER_STARVATION_SAFETY_TURNS;
        for (int tier = 1; tier <= 3; tier++) {
            List<Unit> pool = getManagerTierCandidates(tier, target);
            if (pool.isEmpty()) continue;
            Candidate best = null;
            for (WorkLocation wl : getAvailableWorkLocationsList()) {
                for (Unit u : pool) {
                    // LarryDGray's Mods: a candidate's own current
                    // location must always be considered - a tile (or
                    // building) that can produce more than one goods
                    // type should let its worker just switch goods
                    // type in place, without being forced to relocate
                    // first. getNoAddReason() is NOT usable to check
                    // this case: UnitLocation.getNoAddReason() returns
                    // ALREADY_PRESENT (not NONE) whenever the unit is
                    // already standing in that location, since it is
                    // designed to validate genuinely adding a unit,
                    // not "is this unit allowed to keep working here."
                    // So only consult it for an actual relocation.
                    final boolean sameLocation = u.getLocation() == wl;
                    if (!sameLocation
                        && wl.getNoAddReason(u) != NoAddReason.NONE) continue;
                    final Location oldLoc = u.getLocation();
                    final GoodsType oldWork = u.getWorkType();
                    u.setLocation(wl);
                    u.changeWorkType(target);
                    int net = getAdjustedNetProductionOf(target);
                    Candidate rescue = null;
                    boolean blocked = false;
                    if (net > currentNet && checkStarvation && startedSafe) {
                        int afterMove = getStarvationTurns();
                        boolean stillSafe = afterMove < 0
                            || afterMove >= MANAGER_STARVATION_SAFETY_TURNS;
                        if (!stillSafe) {
                            RescueMove rm = findManagerRescue(u, target, foodType);
                            if (rm == null) {
                                blocked = true;
                            } else {
                                // Recompute target's net with BOTH the
                                // primary and rescue moves layered in -
                                // diverting the rescue unit can itself
                                // affect target's production (e.g. a
                                // shared building), so currentNet must
                                // reflect the real, final state. The
                                // rescue move is still tentatively
                                // applied at this point (findManager
                                // Rescue() deliberately leaves a
                                // successful match in place rather than
                                // reverting it) - revert it here, now
                                // that it's been measured.
                                net = getAdjustedNetProductionOf(target);
                                rescue = new Candidate(rm.unit, rm.location, 0);
                                rm.unit.setLocation(rm.oldLocation);
                                rm.unit.changeWorkType(rm.oldWorkType);
                            }
                        }
                    }
                    u.setLocation(oldLoc);
                    u.changeWorkType(oldWork);
                    if (!blocked && net > currentNet
                        && (best == null || net > best.newNet)) {
                        best = (rescue == null) ? new Candidate(u, wl, net)
                            : new Candidate(u, wl, net,
                                            rescue.unit, rescue.location);
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * LarryDGray's Mods: a rescue move found by {@link
     * #findManagerRescue}, still tentatively applied when returned -
     * carries its own pre-move state so the caller can revert it once
     * it has measured the combined (primary + rescue) effect.
     */
    private static final class RescueMove {
        final Unit unit;
        final WorkLocation location;
        final Location oldLocation;
        final GoodsType oldWorkType;
        RescueMove(Unit unit, WorkLocation location,
                  Location oldLocation, GoodsType oldWorkType) {
            this.unit = unit;
            this.location = location;
            this.oldLocation = oldLocation;
            this.oldWorkType = oldWorkType;
        }
    }

    /**
     * LarryDGray's Mods: try to find a second unit that can be pulled
     * onto this colony's primary food type to keep it safe, given the
     * primary candidate move ({@code primary}, tentatively relocated
     * by the caller and still in that state) that would otherwise push
     * it into imminent starvation. Tries the same tiers 1-3 (least-
     * protected first) as the primary move, excluding the primary unit
     * itself and anyone already producing food OR the target good
     * (a target-good producer pulled onto food would silently undo
     * part of the primary move's own improvement).
     *
     * A successful match is deliberately left tentatively APPLIED when
     * returned, not reverted - the caller needs both the primary and
     * this rescue move active at once to correctly measure target's
     * real combined net production, and is responsible for reverting
     * it (via the returned {@code oldLocation}/{@code oldWorkType})
     * once done. Every unsuccessful attempt along the way is reverted
     * immediately, as usual.
     *
     * @param primary The unit already tentatively relocated for the
     *     primary move - excluded from consideration here.
     * @param target The Manager's target {@code GoodsType} (excluded
     *     from the rescue pool, see above).
     * @param foodType This colony's primary food {@code GoodsType}.
     * @return A {@code RescueMove}, still tentatively applied, or null
     *     if no such move restores safety.
     */
    private RescueMove findManagerRescue(Unit primary, GoodsType target,
                                         GoodsType foodType) {
        for (int tier = 1; tier <= 3; tier++) {
            for (Unit u : getManagerTierCandidates(tier, foodType)) {
                if (u == primary || u.getWorkType() == target) continue;
                for (WorkLocation wl : getAvailableWorkLocationsList()) {
                    final boolean sameLocation = u.getLocation() == wl;
                    if (!sameLocation
                        && wl.getNoAddReason(u) != NoAddReason.NONE) continue;
                    final Location oldLoc = u.getLocation();
                    final GoodsType oldWork = u.getWorkType();
                    u.setLocation(wl);
                    u.changeWorkType(foodType);
                    int starvation = getStarvationTurns();
                    boolean safe = starvation < 0
                        || starvation >= MANAGER_STARVATION_SAFETY_TURNS;
                    if (safe) return new RescueMove(u, wl, oldLoc, oldWork);
                    u.setLocation(oldLoc);
                    u.changeWorkType(oldWork);
                }
            }
        }
        return null;
    }

    /**
     * LarryDGray's Mods: classify this colony's workers into the
     * Manager's three reassignment-priority tiers for the given
     * target goal - tier 1 (least protected, moved first): refined/
     * manufactured goods producers; tier 2: other non-food, non-
     * build-queue-input workers; tier 3 (most protected, last
     * resort): food producers and workers feeding the colony's
     * current buildable. A worker already producing the target good
     * is never a candidate to move away from (also naturally covers
     * the case where the goal itself is Food or a build input).
     *
     * @param tier The tier to collect (1-3).
     * @param target The target {@code GoodsType}.
     * @return The matching workers.
     */
    private List<Unit> getManagerTierCandidates(int tier, GoodsType target) {
        List<Unit> result = new ArrayList<>();
        final BuildableType building = this.buildQueue.getCurrentlyBuilding();
        for (Unit u : getUnitList()) {
            final GoodsType wt = u.getWorkType();
            if (wt == null || wt == target) continue;
            final boolean refined = !wt.isFarmed() && wt.getInputType() != null;
            final boolean food = wt.isFoodType();
            final boolean buildRequired = building != null
                && any(building.getRequiredGoodsList(), ag -> ag.getType() == wt);
            switch (tier) {
            case 1: if (refined) result.add(u); break;
            case 2: if (!refined && !food && !buildRequired) result.add(u); break;
            case 3: if (!refined && (food || buildRequired)) result.add(u); break;
            default: break;
            }
        }
        return result;
    }

    /**
     * LarryDGray's Mods: undo this colony's most recent automatic
     * Manager reassignment, restoring every moved unit's exact prior
     * work location and work type. A one-shot action - the snapshot
     * is consumed (cleared) once used, matching "undo the last
     * change" rather than a full history stack.
     *
     * @param serverPlayer The {@code ServerPlayer} to notify.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csUndoManager(ServerPlayer serverPlayer, ChangeSet cs) {
        if (this.managerUndo == null) return; // nothing to undo
        for (ManagerUndoEntry e : this.managerUndo) {
            if (e.unit == null || e.unit.isDisposed()) continue;
            if (e.location instanceof WorkLocation
                && ((WorkLocation)e.location).getNoAddReason(e.unit) != NoAddReason.NONE) {
                continue;
            }
            e.unit.setLocation(e.location);
            e.unit.changeWorkType(e.workType);
            cs.add(See.only(serverPlayer), e.unit);
        }
        this.managerUndo = null;
        cs.add(See.only(serverPlayer), this);
    }

    /**
     * LarryDGray's Mods: the amount of the primary food type the
     * currently-building population-queue unit type requires - used
     * as a floor for Food's effective overflow limit so a low-tier
     * warehouse never caps Food below the colonist requirement.
     * Avoids hardcoding the classic ruleset's 200, so this stays
     * correct if a ruleset changes the colonist food cost.
     *
     * @return The required Food amount, or 0 if nothing is currently
     *     buildable in the population queue.
     */
    private int getRequiredFoodAmount() {
        BuildableType buildable = this.populationQueue.getCurrentlyBuilding();
        if (buildable == null) return 0;
        return buildable.getRequiredAmountOf(getSpecification().getPrimaryFoodType());
    }

    /**
     * Do the checks for user warnings that must wait for all player
     * settlements, units and whatever to stabilize.  Along the way,
     * throw away excess goods.
     *
     * For example, a pioneer might clear a colony tile and change the
     * lumber amount.
     *
     * @param random A {@code Random} number source.
     * @param lb A {@code LogBuilder} to log to.
     * @param cs A {@code ChangeSet} to update.
     */
    public void csNewTurnWarnings(Random random, LogBuilder lb, ChangeSet cs) {
        final Specification spec = getSpecification();
        final BuildQueue<?>[] queues = new BuildQueue<?>[] {
            this.buildQueue, this.populationQueue };
        final GoodsContainer container = getGoodsContainer();

        for (WorkLocation wl : getCurrentWorkLocationsList()) {
            if (wl instanceof ServerBuilding) {
                // FIXME: generalize to other WorkLocations?
                ((ServerBuilding)wl).csCheckMissingInput(getProductionInfo(wl),
                                                         cs);
            }
        }

        for (BuildQueue<?> queue : queues) {
            ProductionInfo info = getProductionInfo(queue);
            if (info == null) continue;
            if (info.getConsumption().isEmpty()) {
                BuildableType build = queue.getCurrentlyBuilding();
                if (build != null) {
                    AbstractGoods needed = new AbstractGoods();
                    int complete = getTurnsToComplete(build, needed);
                    // Warn if about to fail, or if no useful progress
                    // towards completion is possible.
                    if (complete == -2 || complete == -1) {
                        cs.addMessage(owner,
                            new ModelMessage(MessageType.MISSING_GOODS,
                                             "model.colony.buildableNeedsGoods",
                                             this, build)
                                .addName("%colony%", getName())
                                .addNamed("%buildable%", build)
                                .addAmount("%amount%", needed.getAmount())
                                .addNamed("%goodsType%", needed.getType()));
                    }
                }
            }
        }

        // Throw away goods there is no room for, and warn about
        // levels that will be exceeded next turn
        final int limit = getWarehouseCapacity();
        final int adjustment = limit / GoodsContainer.CARGO_SIZE;
        // LarryDGray's Mods: recompute fresh each turn - true only if
        // this pass actually wastes something below.
        setWastedGoods(false);
        for (Goods goods : transform(getCompactGoodsList(),
                                     AbstractGoods::isStorable)) {
            final GoodsType type = goods.getType();
            final ExportData exportData = getExportData(type);
            final int low = exportData.getLowLevel() * adjustment;
            final int high = exportData.getHighLevel() * adjustment;
            int amount = goods.getAmount();
            final int oldAmount = container.getOldGoodsCount(type);

            // LarryDGray's Mods: Food is normally exempt from all
            // warehouse-capacity handling below since it needs to
            // accumulate past normal capacity to reach a new
            // colonist's required amount - but turning "Overflow to
            // Carrier" on for Food specifically opts it back in to
            // the exact same capacity/redirect/drain-in handling as
            // every other goods type below, so it scales with
            // Warehouse/Warehouse Expansion when that's the bigger
            // number - but never drops *below* one under the
            // colonist requirement, even at a base Depot, so flipping
            // the checkbox on never suddenly wastes/redirects food
            // that was already safely accumulating toward 200 (Larry
            // caught this: a bare Depot's own capacity, e.g. 100, is
            // well under 200 and would otherwise cause exactly that).
            // Population growth is paused independently of whatever
            // this cap turns out to be, by the matching skip in
            // csNewTurn() below - that skip is still required since
            // the population queue's own readiness check runs earlier
            // in the turn than any capping here would, using this
            // turn's production before this method ever gets a
            // chance to act on it.
            final boolean foodOverflowActive = type == spec.getPrimaryFoodType()
                && exportData.isOverflowToCarrier();
            final boolean treatAsLimited = !type.limitIgnored() || foodOverflowActive;
            final int effectiveLimit = foodOverflowActive
                ? Math.max(limit, getRequiredFoodAmount() - 1)
                : limit;

            // LarryDGray's Mods: if this goods type has room again
            // (freed up by consumption in production, a trade route
            // pickup, etc.), pull it back in from any fortified, non-
            // trade-route carrier parked here holding some.
            if (treatAsLimited && exportData.isOverflowToCarrier()
                && amount < effectiveLimit) {
                amount += drainCarrierIntoWarehouse(type, effectiveLimit - amount, cs);
            }

            if (amount < low && oldAmount >= low
                && type != spec.getPrimaryFoodType()) {
                cs.addMessage(owner,
                    new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
                                     "model.colony.warehouseEmpty",
                                     this, type)
                        .addNamed("%goods%", type)
                        .addAmount("%level%", low)
                        .addName("%colony%", getName()));
                continue;
            }
            if (!treatAsLimited) {
                continue;
            }
            String messageId = null;
            int waste = 0;
            if (amount > effectiveLimit) {
                // limit has been exceeded
                waste = amount - effectiveLimit;
                // LarryDGray's Mods: try to redirect into an idle
                // carrier before actually destroying anything.
                if (exportData.isOverflowToCarrier()) {
                    waste -= redirectOverflowToCarrier(type, waste, cs);
                }
                if (waste > 0) {
                    container.removeGoods(type, waste);
                    setWastedGoods(true); // LarryDGray's Mods
                    messageId = "model.colony.warehouseWaste";
                }
            } else if (amount == effectiveLimit && oldAmount < effectiveLimit) {
                // limit has been reached during this turn
                messageId = "model.colony.warehouseOverfull";
            } else if (amount > high && oldAmount <= high) {
                // high-water-mark has been reached this turn
                messageId = "model.colony.warehouseFull";
            }
            if (messageId != null) {
                cs.addMessage(owner,
                    new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
                                     messageId, this, type)
                        .addNamed("%goods%", type)
                        .addAmount("%waste%", waste)
                        .addAmount("%level%", high)
                        .addName("%colony%", getName()));
            }

            // No problem this turn, but what about the next?
            if (!(exportData.getExported()
                  && hasAbility(Ability.EXPORT)
                  && owner.canTrade(type, Market.Access.CUSTOM_HOUSE))
                && amount <= effectiveLimit) {
                int loss = amount + getNetProductionOf(type) - effectiveLimit;
                if (loss > 0) {
                    cs.addMessage(owner,
                        new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
                                         "model.colony.warehouseSoonFull",
                                         this, type)
                            .addNamed("%goods%", goods)
                            .addName("%colony%", getName())
                            .addAmount("%amount%", loss));
                }
            }
        }

        // If a build queue is empty, check that we are not producing
        // any goods types useful for BuildableTypes, except if that
        // type is the input to some other form of production.  (Note:
        // isBuildingMaterial is also true for goods used to produce
        // role-equipment, hence neededForBuildableType).  Such
        // production probably means we forgot to reset the build
        // queue.  Thus, if hammers are being produced it is worth
        // warning about, but not if producing tools.
        if (any(queues, BuildQueue::isEmpty)
            && any(spec.getGoodsTypeList(), g ->
                (g.isBuildingMaterial()
                    && !g.isRawMaterial()
                    && !g.isBreedable()
                    && getAdjustedNetProductionOf(g) > 0
                    && neededForBuildableType(g)))) {
            cs.addMessage(owner,
                new ModelMessage(MessageType.BUILDING_COMPLETED,
                    "model.colony.notBuildingAnything", this)
                .addName("%colony%", getName()));
        }

        // LarryDGray's Mods: this method runs in its own later pass,
        // after csNewTurn() already synced this colony's state to the
        // client once earlier in the same overall turn - so a change
        // made only here (like the wastedGoods reset just above) is
        // never seen by the client unless something else in this
        // method happens to add a message that turn too. On a quiet
        // turn with nothing to report, the server-side reset from
        // true back to false would otherwise never reach the client,
        // leaving the "!" badge stuck showing forever once it first
        // appears (caught live: a colony with zero messages this turn
        // still showed "!" from an earlier turn's real waste).
        cs.add(See.only(owner), this);
    }


    // Implement TurnTaker

    /**
     * New turn for this colony.
     * Try to find out if the colony is going to survive (last colonist does
     * not starve) before generating lots of production-related messages.
     *
     * @param random A {@code Random} number source.
     * @param lb A {@code LogBuilder} to log to.
     * @param cs A {@code ChangeSet} to update.
     */
    @Override
    public void csNewTurn(Random random, LogBuilder lb, ChangeSet cs) {
        lb.add("COLONY ", this);
        final Specification spec = getSpecification();
        final ServerPlayer owner = (ServerPlayer)getOwner();
        BuildQueue<?>[] queues = new BuildQueue<?>[] { buildQueue,
                 populationQueue };
        final Tile tile = getTile();

        // The AI is prone to removing all units from a colony.
        // Clean up such cases, to avoid other players seeing the
        // nonsensical 0-unit colony.
        if (getUnitCount() <= 0) {
            lb.add(" 0-unit DISPOSING, ");
            owner.csDisposeSettlement(this, cs);
            return;
        }

        // LarryDGray's Mods: apply this colony's Manager goal (if
        // any) before production for this turn is snapshotted below,
        // so this turn's production already reflects any reassignment.
        csApplyManager(cs);

        boolean tileDirty = false;
        boolean newUnitBorn = false;
        GoodsContainer container = getGoodsContainer();
        container.saveState();

        // Check for learning by experience
        for (WorkLocation wl : getCurrentWorkLocationsList()) {
            if (wl instanceof TurnTaker) {
                ((TurnTaker)wl).csNewTurn(random, lb, cs);
            }
            ProductionInfo productionInfo = getProductionInfo(wl);
            if (productionInfo == null) continue;
            if (!wl.isEmpty()) {
                for (AbstractGoods goods : productionInfo.getProduction()) {
                    UnitType expert = spec.getExpertForProducing(goods.getType());
                    int experience = goods.getAmount() / wl.getUnitCount();
                    for (Unit unit : transform(wl.getUnits(),
                            u -> u.getUnitChange(UnitChangeType.EXPERIENCE,
                                                 expert) != null)) {
                        unit.changeExperienceType(goods.getType());
                        unit.setExperience(unit.getExperience() + experience);
                        cs.addPartial(See.only(owner), unit,
                            "experience", String.valueOf(unit.getExperience()));
                    }
                }
            }
        }

        // We are about to process build completions.  When we build
        // something the production map will be recalculated, so we
        // need to take a copy now so as to be able to apply the
        // production from the previous turn, not the new production
        // with the new buildable present.
        // See BR#3261 where upgrading the carpenter's shop increases
        // the lumber consumption.
        TypeCountMap<GoodsType> productionMap = getProductionMap();

        // Check the build queues and build new stuff.  If a queue
        // does a build add it to the built list, so that we can
        // remove the item built from it *after* applying the
        // production changes.
        List<BuildQueue<? extends BuildableType>> built = new ArrayList<>();
        for (BuildQueue<?> queue : queues) {
            // LarryDGray's Mods: Food's "Overflow to Carrier" checkbox
            // also pauses population growth (see csNewTurnWarnings())
            // - skip the population queue's own completion check here
            // too, since it would otherwise see enough food (this
            // turn's production included) and build a colonist before
            // csNewTurnWarnings() ever gets a chance to cap Food back
            // down. The normal build queue is untouched.
            if (queue == this.populationQueue
                && getExportData(spec.getPrimaryFoodType()).isOverflowToCarrier()) {
                continue;
            }
            ProductionInfo info = getProductionInfo(queue);
            if (info == null) continue;
            if (!info.getConsumption().isEmpty()) {
                // Ready to build something.  FIXME: OO!
                BuildableType buildable = csNextBuildable(queue, cs);
                if (buildable == null) {
                    ; // It was invalid, ignore.
                } else if (buildable instanceof UnitType) {
                    Unit newUnit = csBuildUnit(queue, random, cs);
                    if (newUnit.hasAbility(Ability.BORN_IN_COLONY)) {
                        newUnitBorn = true;
                    }
                    built.add(queue);
                } else if (buildable instanceof BuildingType) {
                    int unitCount = getUnitCount();
                    if (csBuildBuilding(queue, cs)) {
                        built.add(queue);
                        // Visible change if building changed the
                        // stockade level or ejected units.
                        tileDirty = ((BuildingType)buildable).isDefenceType()
                            || unitCount != getUnitCount();
                    }
                } else {
                    throw new IllegalStateException("Bogus buildable: "
                                                    + buildable);
                }
            }
        }

        // Apply the accumulated production changes using the saved
        // production map.
        for (GoodsType goodsType : productionMap.keySet()) {
            int net = productionMap.getCount(goodsType);
            int stored = getGoodsCount(goodsType);
            if (net + stored <= 0) {
                removeGoods(goodsType, stored);
            } else {
                addGoods(goodsType, net);
            }

            // Handle starvation at once.
            if (goodsType == spec.getPrimaryFoodType()) {
                // Check for famine when total primary food goes negative.
                if (net + stored < 0) {
                    if (getUnitCount() > 1) {
                        Unit victim = getRandomMember(logger, "Starver",
                                                      getUnits(), random);
                        // LarryDGray's Mods: capture the victim's label
                        // before removal so a client option can opt
                        // into naming the unit type in the message.
                        StringTemplate victimLabel = victim.getLabel();
                        ((ServerUnit)victim).csRemove(See.only(owner), null,
                            cs);//-vis: safe, all within colony

                        cs.addMessage(owner,
                            new ModelMessage(MessageType.UNIT_LOST,
                                             "model.colony.colonistStarved",
                                             this)
                                .addName("%colony%", getName())
                                .addStringTemplate("%unit%", victimLabel));
                    } else { // Its dead, Jim.
                        cs.addMessage(owner,
                            new ModelMessage(MessageType.UNIT_LOST,
                                             "model.colony.colonyStarved",
                                             this)
                                .addName("%colony%", getName()));
                        owner.csDisposeSettlement(this, cs);
                        return;
                    }
                } else if (net < 0) {
                    int turns = stored / -net;
                    if (turns <= Colony.FAMINE_TURNS && !newUnitBorn) {
                        cs.addMessage(owner,
                            new ModelMessage(MessageType.WARNING,
                                "model.colony.famineFeared", this)
                            .addName("%colony%", getName())
                            .addAmount("%number%", turns));
                        lb.add(" famine in ", turns,
                            " food=", stored, " production=", net);
                    }
                }
            }
        }
        invalidateCache();

        // Now that the goods have been updated it is safe to remove the
        // built item from its build queue.
        if (!built.isEmpty()) {
            for (BuildQueue<? extends BuildableType> queue : built) {
                switch (queue.getCompletionAction()) {
                case SHUFFLE:
                    if (queue.size() > 1) {
                        randomShuffle(logger, "Build queue",
                                      queue.getValues(), random);
                    }
                    break;
                case REMOVE_EXCEPT_LAST:
                    if (queue.size() == 1
                        && queue.getCurrentlyBuilding() instanceof UnitType) {
                        // Repeat last unit
                        break;
                    }
                    // Fall through
                case REMOVE:
                default:
                    queue.remove(0);
                    break;
                }
                csNextBuildable(queue, cs);
            }
            tileDirty = true;
        }

        // Export goods if custom house is built.
        // Do not flush price changes yet, as any price change may change
        // yet again in csYearlyGoodsAdjust.
        if (hasAbility(Ability.EXPORT)) {
            LogBuilder lb2 = new LogBuilder(64);
            lb2.add(" ");
            lb2.mark();
            for (Goods goods : getCompactGoodsList()) {
                GoodsType type = goods.getType();
                ExportData data = getExportData(type);
                if (!data.getExported()
                    || !owner.canTrade(goods.getType(), Market.Access.CUSTOM_HOUSE)) continue;
                int amount = goods.getAmount() - data.getExportLevel();
                if (amount <= 0) continue;
                int oldGold = owner.getGold();
                int marketAmount = owner.sellInEurope(random, container,
                                                      type, amount,
                                                      GoldCategory.CUSTOMS_HOUSE);
                if (marketAmount > 0) {
                    owner.addExtraTrade(new AbstractGoods(type, marketAmount));
                }
                StringTemplate st = StringTemplate.template("model.colony.customs.saleData")
                    .addAmount("%amount%", amount)
                    .addNamed("%goods%", type)
                    .addAmount("%gold%", (owner.getGold() - oldGold));
                lb2.add(Messages.message(st), ", ");
            }
            if (lb2.grew()) {
                lb2.shrink(", ");
                cs.addMessage(owner,
                    new ModelMessage(MessageType.GOODS_MOVEMENT,
                                     "model.colony.customs.sale", this)
                        .addName("%colony%", getName())
                        .addName("%data%", lb2.toString()));
                cs.addPartial(See.only(owner), owner,
                    "gold", String.valueOf(owner.getGold()));
                lb.add(lb2.toString());
            }
        }

        // Check for free buildings
        for (BuildingType buildingType : transform(spec.getBuildingTypeList(),
                bt -> isAutomaticBuild(bt))) {
            buildBuilding(new ServerBuilding(getGame(), this,
                                             buildingType));//-til
        }
        checkBuildQueueIntegrity(true, null);

        // Update SoL
        updateSoL();
        if (sonsOfLiberty / 10 != oldSonsOfLiberty / 10) {
            cs.addMessage(owner,
                new ModelMessage(MessageType.SONS_OF_LIBERTY,
                                 ((sonsOfLiberty > oldSonsOfLiberty)
                                     ? "model.colony.soLIncrease"
                                     : "model.colony.soLDecrease"),
                                 this, spec.getGoodsType("model.goods.bells"))
                    .addAmount("%oldSoL%", oldSonsOfLiberty)
                    .addAmount("%newSoL%", sonsOfLiberty)
                    .addName("%colony%", getName()));

            ModelMessage govMgtMessage = checkForGovMgtChangeMessage();
            if (govMgtMessage != null) {
                cs.addMessage(owner, govMgtMessage);
            }
        }
        updateProductionBonus();

        // We have to wait for the production bonus to stabilize
        // before checking for completion of training.  This is a rare
        // case so it is not worth reordering the work location calls
        // to csNewTurn.
        for (WorkLocation wl : transform(getCurrentWorkLocations(),
                                                   WorkLocation::canTeach)) {
            ServerBuilding building = (ServerBuilding)wl;
            for (Unit teacher : building.getUnitList()) {
                building.csCheckTeach(teacher, cs);
            }
        }
        
        // Repair land units (only used if they have hitpoints).
        for (Unit unit : transform(getTile().getUnits(), u -> !u.isNaval() && u.isDamaged())) {
            ((ServerUnit) unit).csRepairUnit(cs);
        }

        // Try to update minimally.
        if (tileDirty) {
            cs.add(See.perhaps(), tile);
        } else {
            cs.add(See.only(owner), this);
        }
        lb.add(", ");
    }
}
