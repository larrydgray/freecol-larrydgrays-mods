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
schoolhouse, printing press, and church upgrade chains.

### Trade Advisor Sorting *(client option)*
In the Trade Advisor (F9), click a goods column header to sort colonies
by net production of that good; click again to sort by total goods on
hand instead.

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

## Known, unresolved issues

- A rare freeze when hovering the population tooltip on a spied (foreign)
  colony - confirmed via thread dump to be a genuine infinite loop in
  `HashMap.getNode()`, likely an unsynchronized-map concurrency bug. No
  fix yet; force-quitting and reloading the last autosave is the only
  workaround if you hit it.
