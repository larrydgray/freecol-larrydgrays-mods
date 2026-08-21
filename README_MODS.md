# LarryDGray's Mods

This is [larrydgray](https://github.com/larrydgray)'s personal working copy of
FreeCol, with a set of optional gameplay/UI tweaks layered on top of stock
FreeCol. Genuine bug fixes (see [Bug Fixes](#bug-fixes) below) are always
active; everything else is an individually toggleable mod, so a "vanilla"
game is always one option-flip away.

These mods are modifications to FreeCol's existing GPL v2 source, not a
separate library - so, same as upstream FreeCol, they're licensed under
the **GPL v2**, per the [LICENSE](LICENSE) file in this repo.

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
showing a warehouse-goods or unit-type count of your choosing.

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
of Liberty %, liberty, land military units, ships & wagons, and each
colony's own population plotted as a separate line. History is sampled
once per turn (server-side, so it survives save/reload) and the report
picks up right where a reloaded save left off.

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

## Known, unresolved issues

- A rare freeze when hovering the population tooltip on a spied (foreign)
  colony - confirmed via thread dump to be a genuine infinite loop in
  `HashMap.getNode()`, likely an unsynchronized-map concurrency bug. No
  fix yet; force-quitting and reloading the last autosave is the only
  workaround if you hit it.
