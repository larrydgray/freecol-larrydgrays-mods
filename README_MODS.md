# LarryDGray's Mods

This is [larrydgray](https://github.com/larrydgray)'s personal working copy of
FreeCol, with a set of optional gameplay/UI tweaks layered on top of stock
FreeCol. Genuine bug fixes (see [Bug Fixes](#bug-fixes) below) are always
active; everything else is an individually toggleable mod, so a "vanilla"
game is always one option-flip away.

These mods are modifications to FreeCol's existing GPL v2 source, not a
separate library - so, same as upstream FreeCol, they're licensed under
the **GPL v2**, per the [LICENSE](LICENSE) file in this repo.

## Community

Public Telegram group for players of this mod:
[LarryDGray_FreeCol_Moded](https://t.me/LarryDGray_FreeCol_Moded).

## Before you play: game options vs. client options

FreeCol has two kinds of settings, and the mods below use both:

- **Client options** are per-player UI/display preferences. They can be
  changed at any time, including mid-game, from the in-game Options menu.
- **Game options** change actual game rules. They're chosen once, on the
  "New Game" screen, before the game starts, and get frozen into the save
  file at creation. **Changing a game option will have no effect on an
  existing save** - you need to start a new game to see it take effect.

Each mod below is labelled with which kind it is. Since game options
can't be edited once a game is running, the in-game Options menu
(**LarryDGray's Mods > Status**) shows a read-only, live-synced summary
of which game options are actually active in your current save.

## Mods

### Enhanced Turn Report *(client option)*
Per-line dismiss/deprioritize checkboxes on the Turn Report, a "show
dismissed" toggle, and starvation warnings sorted to the top.

### Colony Stat Toolbar *(client option)*
A toggleable two-row bar under every owned colony's name on the map,
showing a warehouse-goods or unit-type count of your choosing. Row 1:
**DU** (Defending Units - soldiers + dragoons + artillery combined),
**AD** (Artillery Defence - 1/0 for whether the Coastal Defence Bonus
is currently active here: artillery present, or an armed ship docked),
Soldiers, Dragoons, Artillery, **Sc** (Scouts - equipped as scout, or
a Seasoned Scout regardless of current role), **P** (cargo ships only)
and **G** (Gunships - combat-capable ships: frigate/privateer/man-o-
war), Wagon Trains, the usual warehouse goods, and **Bd** (Current
Production - the front item of the colony's build queue, e.g.
`wagonTrain`, or `-` if nothing is queued).

### Colony Building Badges *(always on, display-only)*
Always-on letter badges under a colony's name showing key
infrastructure milestones: Custom House presence, and the tier of the
schoolhouse, printing press, church, and warehouse upgrade chains
(`Wh`/`Wx` for Warehouse/Warehouse Expansion).

### Warehouse Warning Badges *(client option, default on)*
Adds `!`/`+` to the Colony Building Badges line, independently
toggleable from it: `!` if the colony actively wasted goods to
overflow last turn, `+` if any goods type is currently sitting at a
full warehouse slot.

### Overflow Product Icons *(client option, default on)*
A row of tiny goods icons under the Colony Building Badges line - one
icon per 100 combined units (warehouse stock plus whatever's parked on
a fortified, non-trade-route carrier at the colony's tile) a storable
goods type currently has, capped at 3 icons before switching to a
single icon with a small `×N` overlay. Independent toggle from the
badges above, settable either in Options or via the "OI" button on the
Colony Stat Toolbar (see below) for quick access mid-game.

### Warehouse Overflow to Carrier *(game option, default on; per-goods/colony checkboxes, default off)*
Rather than destroying goods that overflow a full warehouse, redirect
them into an idle wagon train or ship parked (fortified) at the
colony, if one has room - skips any carrier currently assigned to a
trade route. The reverse also happens automatically: once the
warehouse has room again for that goods type (consumed in production,
picked up by a trade route, etc.), it's pulled back in from the
parked carrier. Each goods type has its own "Overflow to Carrier"
checkbox in the Warehouse dialog (off by default, like Export).
Greyed out entirely if the "Warehouse Overflow to Carrier" game
option is off (New Game screen, off requires starting a fresh game).

On the Food row this same checkbox does double duty. Food is normally
exempt from warehouse capacity entirely, since it needs to accumulate
past it to reach a new colonist's required amount (200 in the classic
ruleset) - turning the checkbox on for Food opts it back in to the
same capacity/redirect/drain-in handling as every other good, so it
scales with Warehouse/Warehouse Expansion (rising as high as 300 with
an Expansion built) - but never drops *below* one under the colonist
requirement even at a bare Depot, so flipping the checkbox on never
suddenly wastes or redirects food that was already safely stockpiled
toward 200. Separately, population growth for that colony is paused
for as long as the checkbox stays on, regardless of how high its
effective cap turns out to be - no dedicated "pause growth" checkbox
needed. Turn the checkbox back off and the very next time stored Food
is at or above 200, a colonist is born as normal, consuming exactly
200 and leaving the remainder in the warehouse.

### Town Magistrate *(game option, default on; per-colony dropdown, default Unmanaged)*
A dropdown in the Colony screen next to the colony name lets you set
a colony to auto-optimize toward one goal - Food, Lumber, Ore,
Crosses, or Liberty Bells. While set, every turn the colony reshuffles
its own workers (pulling refined-goods producers first, then other
non-essential workers, and only as a last resort food producers or
workers feeding the current build queue item) to maximize net
production of that one good. Applies immediately when you pick a
goal, not just on the next turn. "Apply to All Colonies" copies the
current dropdown's setting to every colony you own. "Undo Magistrate"
reverts the colony's most recent automatic reassignment (server-side
only, lost on reload). Off entirely if the "Town Magistrate" game
option is off (New Game screen, off requires starting a fresh game).
Also added: `<`/`>` buttons next to the colony name dropdown to step
to the previous/next colony without opening the list.

### Auto Explore *(game option, default on; ships only)*
A new standing order for ships - toggle it from the Orders menu (`X`
key), the unit's right-click menu, or the ship's own menu entry.
Starting it asks which strategy to use: follow coastlines, follow the
arctic edge, follow the deep water/high-seas edge, just find and clear
the nearest fog (never hugging anything), or go one fixed compass
direction (picked once, immediately) until land or the map's edge
blocks it, then stop - no hugging, no further prompts, useful for just
sending a ship straight out to see what's there. Once running, the ship
moves on its own every turn with no further input - it silently steers
toward the nearest known instance of its chosen boundary type (or the
nearest fog, in "nearest fog" mode), and only interrupts to ask a
direction question at genuinely ambiguous points: first contact with
the boundary (normally just 2 choices - the two ways along it - widened
automatically at a real fork, like a river mouth or a narrows between
two landmasses, where more than 2 continuations are actually open), or
later reaching a *new* such fork. Once answered, it commits and keeps
going silently in that direction with no more nagging until the next
ambiguous point. This replaced an earlier version that tried to
auto-detect which way to turn and which boundary to prioritize, which
was the source of persistent "circling" bugs - the redesign removes
that guessing entirely by asking instead.

The arctic edge needs none of the above once a direction (east or
west) is picked - the polar band is a fixed row-range with no
curvature, so the ship just keeps going that way until land or the
map's edge stops it, with no wall-following logic to get wrong at all.
Coastline and deep-water/high-seas hugging, which both curve
unpredictably, map out the entire stretch of boundary in one go and
walk it exactly once, rather than re-deciding tile by tile - a stretch
the ship is going to sail anyway is never re-guessed, which is what
caused most of the remaining circling. When the already-explored map
isn't enough to plan the whole stretch, it asks the server (which
always has the true, full map) to work out the real route and hands
back only a bare list of directions to follow - never any map data
itself, so nothing is shown to the player ahead of the ship actually
sailing there. Your own fog-of-war reveal is completely unaffected:
tiles still fill in one at a time, in the same order, exactly as the
ship physically visits them - the only thing that changes is the ship
no longer has to guess or backtrack while doing it. It only asks the
player again at a genuine fork, which the server-computed route
correctly stops at too.

Shows as an "A" on the unit's occupation indicator while active. Does
NOT stop for sighting another nation's unit or settlement - it sails
past them the same as anything else. Any manual move or destination
cancels it. Scouts get the same treatment as a planned follow-up. Off
entirely if the "Auto Explore" game option is off (New Game screen, off
requires starting a fresh game).

### Minimum Colony Distance *(game option, default 1 = off; Colony Options tab)*
An integer option (1-5) requiring a newly founded colony to be at least
that many tiles from every one of your other colonies. At 1 (the
default) nothing changes from vanilla - colonies may be founded
directly adjacent. Raised to 2 or more, this guarantees colonies' work
radii (each 1 tile out from its own center) never overlap, so two
colonies can never compete for the same resource tile. Lives in the
vanilla "Colony Options" tab rather than the LarryDGray's Mods tab,
alongside the other colony-founding rules it's most related to.
Requires a new game (New Game screen) since it's a new spec option.

### Colony Manager *(game option, default on)*
A per-colony auto-worker-optimizer. Set a colony's Manager goal
(Food/Lumber/Ore/Crosses/Bells) from its colony panel and it
re-optimizes toward that goal every turn - pulling workers off other
jobs in priority order (refined-goods producers first, other
non-essential workers next, food/current-buildable-input producers
only as a last resort) toward whichever tile/building improves net
production of the target good the most. A real Undo button reverts the
colony's entire worker arrangement to how it was immediately before
the Manager's last reassignment pass. An "Apply to All Colonies"
button sets every owned colony to the same goal at once.

Never makes a move that would cause a colonist to starve within the
current turn or the next (added after it did exactly that chasing an
Ore goal) - if the "ideal" move would drop food into that danger zone,
it first looks for a second, currently-available unit it can pull onto
food to keep the colony safe, and only applies the original move
alongside that compensating one. If no such rescue exists anywhere in
the colony, the risky move is skipped entirely rather than applied. A
Food-goal Manager is unaffected by any of this, since increasing food
production can't itself cause a food deficit.

### Warn Before Starvation *(game option, default on; Colony Options tab)*
Ending the turn first checks whether any owned colony is about to lose
a colonist to starvation *this* turn (not next turn - only the
immediate case) and, if so, shows a dialog naming which settlement(s),
with "End Turn Anyway" or "Cancel." Click a listed colony to open its
panel. Runs before the existing "units still have moves left" end-turn
dialog, so both can appear in sequence on the same turn if both apply.
Lives in the vanilla "Colony Options" tab, like Minimum Colony
Distance above.

### Trade Advisor Sorting *(client option)*
In the Trade Advisor (F9), click a goods column header to sort colonies
by net production of that good; click again to sort by total goods on
hand instead.

### Trade Advisor Compact View *(client option, default on)*
Replaces the Trade Advisor's default two-rows-per-colony layout with a
single row, switchable via three buttons above the colony column: On
Hand, Production, and Net Production. Zero amounts are left blank
instead of showing "0". Also adds Liberty Bells and Crosses as extra
columns (icon-only header, no market price, since they aren't
tradeable goods) so colony production of those shows at a glance too.
Clicking a goods column header (Trade Advisor Sorting, above) sorts by
whichever of the three values is currently active, so the sort order
always matches what's on screen. The report retitles itself
"Production/Trade Report" while this is on. Compact View's rows also
start with a **City Size** column (each colony's population) right
after the colony name, sortable the same way as any goods column.

### Labor Advisor: Bar Chart View *(display-only, always available)*
A Grid/Bar Chart toggle on the Labor Advisor (F2). The bar chart shows
every unit type you own as a horizontal bar - icon on the left, count
at the end of the bar - sorted highest-count-first, alongside the
original icon grid.

### Colony Growth Report *(client option, default on)*
New Reports menu entry showing a full turn-by-turn timeline, not just
the current turn's snapshot: population, citizens, settlements, Sons
of Liberty %, liberty, land military units, ships & wagons, each
colony's own population plotted as a separate line, and a combined
"Total Armed Land Units" chart that also plots a **Total Gunships**
line (frigates/privateers/men-o-war, same split as the Colony Stat
Toolbar's P/G). History is sampled once per turn (server-side, so it
survives save/reload) and the report picks up right where a reloaded
save left off.

### Nation Comparison Report *(client option, default on)*
The same full-timeline treatment as Colony Growth, but for every
European nation you've met: settlements, units, military/naval
strength, and gold, plus (with Jan de Witt) Sons of Liberty %, Founding
Fathers, and tax rate.

### Trade History Report *(client option, default on)*
New Reports menu entry for empire-wide goods trends: pick Basic Goods
or Refined Goods, then a metric (On Hand, Production, Net Production,
Sales, Units Bought, Units Sold, Income Before/After Taxes, or Units In
Cargo), and see every good in that group plotted together over the
whole game's turn history.

### Gold Journal Report *(always on, display-only)*
New Reports menu entry, a real per-turn ledger rather than a chart:
Turn / In / Out / Net / Balance, plus a **Notes** column breaking down
exactly what happened that turn by category - Europe Trade, Native
Trade, Customs House, Upkeep, Treasure, Tribute, Crown (monarch
gifts/mercenary costs), Plunder, Ruins (Lost City Rumours), Land Claim,
Recruitment, Diplomacy, Disaster, Arrears, Incitement. Every place in
the game that changes gold funnels through one method, so this covers
all of them automatically, including any added in the future. Note:
unlike the other timeline reports, the current session's most recent
turns won't show up until the next save/reload - gold deltas are only
ever counted on the server's authoritative copy of your player, with
no way for the client to replay them live.

### Build Queue: Add All (No Tools) *(client option, default on)*
A hammer-icon button next to Buy in the Build Queue dialog - queues
every currently-buildable building that doesn't require tools, in the
order the Buildings list shows them. Batches a frequent manual action
into one click. Its own dedicated toggle, specific to this dialog -
more buttons may join it there later, each independently switchable.

### Build Queue: Emergency War Effort / Quick Boost to Land Shipping *(client options, default on)*
Two more buttons in the Build Queue dialog, each its own toggle.
Unlike the Add All button above, these act empire-wide: "Emergency War
Effort" moves Artillery to the front of *every* colony's build queue
that can currently build one; "Quick Boost to Land Shipping" does the
same for a Wagon Train. If one is already queued somewhere else in a
colony, it's moved to the front rather than queuing a duplicate.

### Window Title Branding *(always on)*
The window title bar reads "FreeCol &lt;version&gt; with LarryDGray's
Mods", so this build is visually distinguishable from stock FreeCol at
a glance (e.g. in screenshots).

### Caravan mechanic *(always on)*
A dragoon, soldier, scout, or wagon train can lead other land units as
cargo, using the same carrier machinery ships already use - no new unit
type or graphics. Leadership rank: dragoon > soldier > scout > wagon
train, with the expert version of a role (Veteran Soldier, Seasoned
Scout) outranking a non-expert in the same role. Flat 12-slot capacity.
"Form Caravan" / "Disperse Caravan" from the tile right-click menu.

### Send Fleet *(always on)*
Give a whole stack of your own ships on one tile a single destination
order in one go - each ship still sails and fights independently.

### Armada mechanic *(always on)*
A flagship ship can carry other ships as passengers (300-slot capacity,
scoped so it never inflates the flagship's own cargo capacity).
Flagship rank by defense: Man-o-War > Frigate > Galleon > Privateer >
Merchantman > Caravel. Automatically disperses all escorted ships onto
the tile the instant the flagship is chosen as a combat defender, win
or lose, so combat never has to deal with a nested fleet.

### Naval Bombardment *(always on)*
A combat-capable ship (Frigate/Privateer/Man-o-War) can attack a
coastal settlement directly:
- If the settlement has an armed defender (garrison unit, docked ship,
  or a colonist eligible to auto-equip from stored muskets, e.g. Paul
  Revere), combat resolves normally.
- If the settlement is genuinely undefended, the ship bombards the town
  instead of the attack being rejected: a building, a citizen, a
  warehouse goods stack, or production is damaged at random (reuses
  FreeCol's existing Disaster system).

### Naval Scouting *(game option, default off)*
A ship can sail up to a native settlement's coast and speak with the
chief - the same Attack/Speak/Tribute interaction a land Scout gets -
without needing to land a Scout first. Off by default since this is new
gameplay capability rather than a restored/preserved behaviour. An armed
ship still gets the full dialog (Speak/Tribute/Attack) instead of
auto-bombarding, so turning this on doesn't cost a warship anything.

### Artillery Bombardment *(game option, default on)*
Extends Naval Bombardment's logic to artillery: with the
**Artillery Bombardment** game option on, a cannon attacking an
undefended settlement bombards it the same way a ship does, instead of
walking in and capturing it outright. With it off, artillery keeps
vanilla capture behavior.

### Coastal Defence Bonus *(always on, adjustable amount)*
A settlement defending against any attacker (naval or land) gets a
defense bonus if it has artillery present or an armed ship docked -
representing shore batteries and covering fire, regardless of which
unit actually ends up defending. Size is adjustable via the
**Coastal Defence Bonus Amount** game option (default 50%). Note:
FreeCol applies percentage combat modifiers sequentially, not
additively, so this compounds hard on top of Stockade/Colony/Fortified
bonuses - a well-garrisoned, fortified colony behind a stockade is a
serious deterrent.

### Artillery Support Bonus *(game option, default on, adjustable amount)*
The offense-side mirror of the Coastal Defence Bonus: a land attacker
gets a bonus when backed up by artillery or an armed ship on its own
tile (not the defender's). Toggle: **Artillery Support Bonus**. Amount:
**Artillery Support Bonus Amount** (default 50%). A third option,
**Artillery Supports Artillery** (default off), controls whether an
attacking cannon can itself benefit from a second cannon backing it up
- off by default, since the bonus is meant for infantry/cavalry backed
by artillery, not artillery backing up artillery.

### Ships Require Cloth *(game option, default on)*
Vanilla ship-building only costs hammers and tools. With **Ships
Require Cloth** on, all six ship types also require sailcloth,
scaled roughly by size: Caravel 20, Merchantman 30, Galleon 40,
Privateer 40, Frigate 80, Man-o-War 120.

### Name Starved Unit Type *(client option, default on)*
When a colonist starves to death, the message names their unit type -
"Our Expert Farmer has starved to death in X" instead of just "A
colonist has starved to death in X." Covers both the turn report and
the end-of-turn popup.

### Condensed Unit Menu *(client option, default on)*
Single-word labels in the unit right-click menu for a garrisoned
colony unit: Change Work -> Work, Activate Unit -> Activate, Clear
Orders -> Orders, Remove All Equipment -> Unequip, Equip as Scout ->
Scout, Commission as Missionary -> Missionary, Clear Specialty ->
Specialty. (Fortify/Sentry are left alone since they're already one
word.)

### Start Game Screen: Save/Load/Reset Nation Setup *(client option)*
The New Game "Nation/Availability/Advantage/Color/Player" screen gets
three buttons and a checkbox, shown for whoever actually controls that
screen (single player, or the multiplayer host/admin):
- **Save Settings** - saves the current nation availability/color setup
  to a file.
- **Load Settings** - manually (re)loads that saved setup, regardless
  of the checkbox below.
- **Reset to Defaults** - resets nation availability back to the game's
  built-in defaults (colors are left alone).
- **Autoload Last Settings** checkbox - when on, automatically loads
  the saved setup every time this screen opens. The checkbox's own
  on/off state is remembered across sessions too.

### Restart *(always on)*
A **Restart** item in the Game menu, next to Quit - a dev/debug
convenience that spawns a fresh copy of the application (reading
whatever jar is on disk right now) and closes the current one, so a
freshly rebuilt jar can be picked up with one click instead of
manually closing and relaunching by hand.

## Bug Fixes

Fixes below are always active and are genuine FreeCol bugs, not mod
preferences.

- **"Ghost soldier" capture bug** - a long-standing capture bug, root-caused
  and fixed; submitted upstream as
  [FreeCol/freecol#167](https://github.com/FreeCol/freecol/pull/167).
- **Unit right-click menu always opening at the top-left of the window**
  instead of near the clicked unit, for any Windows user in windowed mode -
  the popup-positioning workaround in `DragListener.java` was firing far
  more broadly than its own comment described.
- **European first-contact "greeting" loop** - accepting a first-contact
  greeting from another European power could silently fail to complete the
  underlying peace treaty, leaving the two players stuck at `UNCONTACTED`
  and causing the same nation to re-send the greeting (and error) every
  turn.
- **Loading a save with "host as multiplayer" chosen still left the client
  thinking it was single-player** - the "Loading Savegame" dialog's choice
  was being discarded by a hardcoded `setSinglePlayer(true)` right after.
- **Game Options and Map Generator Options never actually restored
  themselves between sessions** - they were being saved correctly, but the
  matching reload-on-startup logic was dead, commented-out code.
- **Submenu text (e.g. "Work" in a unit right-click menu) rendered in
  unreadable gold-on-light instead of dark text** - the theme already had
  a deliberate fix for this exact contrast problem on plain menu items,
  but missed submenus, which share a UI key with the top-level menu bar
  (where the gold color is correct). Fixed with a per-instance check
  instead of a blanket override, so the top-level bar keeps its gold.
  Root cause turned out to be one level deeper than the UI key itself:
  Swing's idle-state menu text painting never calls `Graphics.setColor()`
  - it just draws with whatever color the `Graphics` object already has,
  which gets preset from the component's foreground *before* a custom
  paint routine even runs. Changing the component's foreground property
  inside that routine was always one step too late to matter; the fix
  had to set the `Graphics` color directly instead.
- **Dragging goods from a warehouse into a carrier's cargo hold silently
  refused the transfer** if the only free room was a partially-filled slot
  of the same goods type smaller than the full amount being dragged (e.g.
  70 in the warehouse, 40 already in the cart, no other empty hold) - the
  drop was rejected outright instead of topping the slot off at 100 and
  leaving the remainder in the warehouse, even though the code to do that
  correctly already existed and just never got a chance to run.
- **Renaming a colony didn't update its map label until you scrolled the
  camera over it** - the map caches settlement labels per tile and the
  rename action never marked that tile as needing a redraw, so the old
  name stuck around on-screen until an unrelated pan forced a refresh.
- **Food's Production figure always read zero** - `Colony.
  getTotalProductionOf()` only counts raw production of the exact goods
  type asked for, but farmers/fishermen produce grain/fish, not "food"
  directly (only the storage side aggregates those into food). Visible
  in the Trade Advisor's Production mode and its sort-by-column, and in
  the new Trade History report - fixed by using FreeCol's own existing
  `getFoodProduction()` helper (originally written for a different
  caller) for the food case specifically.

## Wish List

Ideas that have been floated but aren't scoped or built yet - not
commitments, just a record so they don't get lost. (AI Difficulty
Levels is its own much larger, separately-tracked design effort and
isn't listed here.)

- **Missionary auto-upgrade** - once a mission is established, if a
  better missionary (e.g. an actual expert Missionary) becomes
  available, swap them into the mission automatically; the displaced
  missionary appears back outside the settlement instead of just
  vanishing. Intent is faster conversions - the ruleset already grants
  `Ability.EXPERT_MISSIONARY` to better missionary types, presumably
  feeding into conversion speed.
- **Missionary survives settlement destruction** - today, destroying a
  native settlement kills any missionary stationed there outright
  (confirmed in `ServerPlayer.csDisposeSettlement()`) - no capture, no
  ejection, just gone with a "mission destroyed" message. This mod
  would let the missionary survive and appear outside instead.
- **Abandon Mission before attacking** - a player-issued order to
  voluntarily withdraw your own missionary from a settlement before
  assaulting it, so attacking doesn't cost you the missionary as a
  side effect if you want to keep them.
- **Spy on native settlements** - the existing Spy scouting action only
  works on European colonies; `SpySettlementMessage.getColony()` is
  hardcoded to `Colony.class` and can't target an `IndianSettlement` at
  all. There's currently no way for a player to learn how many braves
  defend a native settlement short of attacking it outright - this
  would extend Spy to work against native settlements too.
- **"Custer's Last Stand" ambush event** - random chance that attacking
  a native settlement triggers nearby braves appearing on all sides and
  counter-attacking, instead of the fight staying contained to just the
  settlement itself. Punishes attacking a native settlement blind - ties
  directly into the "no intel on defenders" gap above (Spy on native
  settlements), since scouting first would be the way to avoid getting
  ambushed.
- **Events History Report** - a log of positive and negative events over
  time (disasters, native gifts/tribute, and similar), with running
  gain/loss totals - broader than the existing Gold Journal, which is
  gold-only. The existing `HistoryEvent` log (Report menu) only covers
  big narrative milestones (colony founded, war declared, founding
  father joined, etc.), not disasters or native contributions, so this
  would need its own new sampling, not a reuse of that log. Non-gold
  events (a building destroyed, a unit lost) don't have an obvious
  common unit to total against gold-based ones - worth resolving before
  building this. Larry's suggestion: this might make more sense living
  under/alongside the existing (vanilla) History report rather than as
  a fully separate new report screen - worth deciding when this gets
  scoped.
- **Population Vital Events report** - a "births and deaths" style log
  of how your colonist count actually changes over time: born from food
  surplus, died (starvation, natural disaster, combat, etc.), recruited
  from Europe,
  trained, granted by the Crown (monarch mercenaries/reinforcements),
  native converts, captured (both directions - a unit lost to capture,
  and one gained by capturing an enemy's), and gained from ruins (Lost
  City Rumour outcomes that hand you a colonist). Larry's own words:
  "I'm not sure what that might
  look like yet" - a real idea, but the shape (one combined report vs.
  folded into the Events History Report above, what the running total
  even means) isn't decided.
- **Sell All button in Europe** - one click to sell every good currently
  loaded on a docked ship, instead of dragging each cargo slot onto the
  market individually.
- **Naval Scouting eligibility options** - today's Naval Scouting toggle
  is all-or-nothing (any European ship, or off). Wishlist is to make
  that configurable, with options like: all naval units can act as
  scout (today's behavior); only Galleon; only Galleon and Frigate; or
  only a naval unit currently carrying an actual land Scout as a
  passenger (i.e. the Scout doesn't have to disembark to speak with the
  chief - having one aboard is what unlocks it for that ship). Likely
  shape: replace the current boolean game option with a multi-choice
  one. Confirmed a combat-capable ship (a gunship) keeps full normal
  attack capability under any of these variants - scouting is checked
  before the attack move-type in `getNavalMoveType()`, so an armed ship
  just gets offered Attack/Speak/Tribute instead of auto-bombarding,
  never losing its ability to fight.

  **Related, broader idea**: "spying on a foreign colony" is today
  *also* a land-Scout-only action (`Ability.NEGOTIATE`, the
  `ENTER_FOREIGN_COLONY_WITH_SCOUT` move type) - the same shape native
  chief-speaking was in before Naval Scouting existed. Worth
  considering letting a ship do this too, under the same eligibility
  options and the same combat-preserving ordering, rather than treating
  native contact and foreign-colony spying as two separate features.
- **Disarm in the field: dragoon &rarr; scout** - let a dragoon voluntarily
  drop its equipment and re-role as a scout without returning to a
  colony first. The muskets/horse would presumably just be lost (or,
  for flavor, left behind as a pile on the tile) rather than recovered.
- **Build Queue named/saved worklists** - save a specific build sequence
  as a named template, reload it later. A more general version of the
  "Add All (No Tools)" button above.
- **Warehouse export-level presets** - apply a saved set of per-goods
  export thresholds at once instead of setting each one by hand.
- **Gold Journal drill-down** - click a category in the Notes column
  (e.g. "Europe Trade") to see which goods were bought/sold that turn.
  Needs new per-goods detail inside each category, not just the
  per-category totals tracked today.
- **Real game-option toggles for Naval Bombardment, Caravans/Armadas,
  and Send Fleet** - these are still always-on, with only a read-only
  status display; Artillery Bombardment already got the full toggle
  treatment.
- **A dedicated "Storage" unit state** for Warehouse Overflow to
  Carrier, replacing the current design where a carrier's ordinary
  Fortified state doubles as the "eligible for overflow duty" signal -
  a player fortifying a ship purely for defense might be surprised it's
  also become an overflow sink.
- **"Pin" units** (overflow-storage ships/wagons, or colonists left to
  gain experience) so they can't be accidentally moved or reassigned by
  trade routes/goto orders.
- **Extend Paul Revere's auto-equip** to cover dragoons (muskets +
  horses), not just soldiers (muskets only).
- **Sell ships back to Europe** at roughly 2/3 market price - currently
  ships can only be bought, never sold.
- **Max unit stack size per tile** to prevent "doom stacks" - floated
  tentatively, not a firm want yet.
- **Temporary allied native war parties** - actual native combat units
  (foot and mounted) fighting alongside the player temporarily, not a
  permanent addition to the army. The biggest lift on this list - needs
  a recruitment trigger, a lifecycle for when they leave, and new
  graphics to distinguish them from a native's own units.
- **Spy-on-settlement intel** - a chance to learn something extra about
  a colony (goods, garrison, building tiers) whenever you spy on it.
- **Field resupply** - a soldier/dragoon that's lost its equipment
  auto-rearms from a nearby wagon train instead of marching home.
- **Colopedia/help screens updated** to actually mention what these
  mods change, instead of only describing stock FreeCol.

## Known, unresolved issues

- A rare freeze when hovering the population tooltip on a spied (foreign)
  colony - confirmed via thread dump to be a genuine infinite loop in
  `HashMap.getNode()`, likely an unsynchronized-map concurrency bug. No
  fix yet; force-quitting and reloading the last autosave is the only
  workaround if you hit it.
- Auto Explore's past "circling" bugs went through several rounds of
  live-tested fixes: an inconsistent wall-follower turn bias, the
  open-water "nearest fog" target flip-flopping every step, a fully-
  explored short coastline/loop having no exit condition, a candidate
  being accepted as "confirmed" purely because a multi-tile lookahead
  peeked toward a distant, unrelated landmass rather than the actual
  coastline being hugged, and a fixed sharpest-turn-first scan order
  that would tie-break in favour of reversing back through already-
  explored tiles instead of just continuing straight on a plain
  stretch of coast. All were ultimately traced to the same root cause:
  the wall-follower either guessed which way to turn instead of
  requiring genuine radius-1 wall contact, or broke ties by scan order
  instead of by which candidate actually kept progress moving forward.
  Confirmed working well in live play as of the last test - if
  circling still turns up somewhere, the workaround is unchanged:
  manually cancel the order (Orders menu, right-click, or the `X` key)
  and move the ship yourself past whatever spot it's stuck at.
