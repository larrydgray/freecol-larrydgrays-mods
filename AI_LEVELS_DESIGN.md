# AI Difficulty Levels — Design Notes (WORKING DRAFT)

Status: **spec-in-progress, nothing implemented yet.** Captures the shape of
the idea while Larry fills in the actual strategy content from 20 years of
play. Not a commitment to build this — just a place to park it so it
doesn't get lost.

## The problem this solves

FreeCol's existing "difficulty" levels (Very Easy / Easy / Medium / Hard /
Very Hard, plus Custom — `data/rules/classic/specification.xml`,
`difficultyLevels` option group) don't make the AI play *smarter* or
*dumber*. Confirmed by reading `server/ai/`: the AI's decision-making code
never reads difficulty level at all. What difficulty actually changes:

- World/economy knobs (immigration crosses needed, native alarm growth,
  monarch tax/war frequency) — `model.difficulty.immigration/natives/monarch`.
- AI "cheats" — occasional free units, free boycott removal, boosted trade
  profit — `model.difficulty.cheat`. A handicap given *to* the AI, not
  smarter play *by* it.

So today, every difficulty level plays with identical AI strategy. This doc
is about actually changing that.

## Naming — DECIDED

**Settler → Planter → Merchant → Governor → Captain-General**

Maps onto the existing five tiers (Very Easy → Very Hard) in that order.

## Proposed mechanism

Reuse FreeCol's existing `Ability` system (data-driven boolean flags,
already used everywhere for unit/player/building capabilities, checked via
`hasAbility(...)`) rather than inventing a new mechanism:

1. Define new AI-specific abilities, e.g. (names illustrative, not final):
   - `model.ability.aiAdvancedColonyPlanning`
   - `model.ability.aiAdvancedMilitary`
   - `model.ability.aiAdvancedTrade`
2. Grant a cumulative set of these abilities per difficulty tier in the
   spec (same pattern as the existing per-level `model.difficulty.cheat`
   integer options) — lowest tier gets none, highest tier gets all of them.
3. In the relevant AI Java code (`server/ai/EuropeanAIPlayer`,
   `NativeAIPlayer`, and the mission classes — `TransportMission`,
   `WishRealizationMission`, military-related missions, etc.), branch on
   `hasAbility(...)` to pick between a "baseline" heuristic and a "smarter"
   one where one exists.

**The abilities/flags are the easy part.** The real work is writing the
actual smarter behavior each flag unlocks — none of that exists yet. This
doc's job is to build the list of *what* "smarter" means, concretely,
before any code gets written.

### Refinement: noise-injected scoring for graduated decisions

For decisions that are naturally "pick the best option from a scored list"
(worker placement is the clearest example, but this generalizes to a lot
of the AI's choices), a discrete Ability on/off flag per behavior is too
blunt — it can't express "usually good, occasionally makes a believable
mistake." Better mechanism for these cases:

```
finalScore = calculatedScore + randomError
```

where the AI picks the option with the best `finalScore`, and the
magnitude of `randomError` shrinks as AI level rises. This means:

- The AI doesn't need a "make a mistake now" special case — it just has
  imperfect judgment baked into every decision, so sometimes it's still
  optimal, sometimes slightly inefficient, sometimes visibly bad.
- One scoring system can power every level — lower levels aren't running
  different code, they're running the same code with more noise (and,
  additionally, fewer factors considered at all — see below).
- Different mistakes can layer independently. E.g. for worker placement:
  a weaker AI might correctly identify "need more lumber" but assign the
  wrong worker to the job; a somewhat stronger one picks the right worker
  but doesn't anticipate that pulling them off fishing causes a food
  shortage two turns later; the strongest sees both the immediate need
  and the downstream consequence.

**Core design principle this implies: AI level should represent economic
competence (how much of the picture it sees, and how reliably it acts on
it), not artificial production bonuses.** This is the thing that's
actually broken today per the "Cheat" section above — cheat grants are a
production bonus, not competence. The noise + factor-count approach is how
to build actual competence differences instead.

Applied to worker placement specifically, as a concrete illustration of
the whole level spread:

- **Low**: mostly need-based, high noise. Sees "we need food," assigns
  *someone* who can produce it, largely indifferent to whether that's a
  Native Convert, Free Colonist, criminal, or indentured servant.
- **Mid**: scores productivity and specialty matching, but with enough
  noise that it occasionally picks the second- or third-best worker
  instead of the actual best one — a believable mistake, not a scripted
  one.
- **High**: strong specialization matching, aware of opportunity cost
  (pulling a good worker off one job costs something elsewhere), low
  noise.
- **Expert/Strong**: looks ahead — training progress, population growth,
  full production chains, upcoming shortages — not just the current
  turn's snapshot.

## Colony building: weak → strong strategies

*(Larry to fill in — what does a weak AI colony-builder do vs. a strong
one, based on 20 years of your own strategy experience? Site selection,
production chain sequencing, building priorities, worker allocation,
founding father choices, expansion pacing, etc.)*

- Weak: Focuses on simply producing basic goods with any unit type,
  without considering that unit types below Free Colonist (indentured
  servant, petty criminal) carry their own production penalties, and
  that the penalty varies by which good is being produced — a weak
  colony-builder just doesn't factor this in at all, not matching
  units to their expert specialty/production bonus either. Also
  randomly moves Free Colonists between different production jobs,
  which resets/prevents the on-the-job training toward becoming an
  expert at that job.
- Moderate:
- Strong: Leaves a Free Colonist on one production type consistently
  until it trains up into an expert at that job, rather than shuffling
  it around. Lower-tier units (indentured servant, petty criminal) can
  be freely moved between jobs instead, since they don't have that
  same training-progression concern to protect. Concretely: a higher-
  level AI deliberately assigns one or more Free Colonists to cotton
  farming, tobacco farming, sugar cane farming, or fur trapping (the
  classic raw-material production chains behind cloth/cigars/rum/coats
  — see the "early-game refining experts" point below), specifically
  so they train up into the matching expert (Master Cotton Planter,
  Master Tobacco Planter, Master Sugar Planter, Expert Fur Trapper)
  rather than staying generalist indefinitely. Uses something like a
  "settlement mayor" decision process to choose what gets built next
  (building queue priority driven by the colony's actual needs, not
  arbitrary/default order). Recognizes the church → cathedral upgrade
  chain as an immigration lever: more crosses produced means faster
  immigration, so it's worth prioritizing, not just a flavor building
  — and follows through by actually manning the church/cathedral with
  workers, not just constructing it and leaving it empty, since crosses
  only come from staffed production.

  Caveat from Larry: how heavily to prioritize immigration isn't purely
  a weak-vs-strong thing — it's partly a playstyle choice, and Larry's
  own play has swung between immigration-focused and not, over
  different stretches of time. So "strong" here probably means being
  deliberate/aware either way (knowing the crosses/immigration lever
  exists and choosing consciously whether to lean on it), not "always
  max crosses" as a fixed rule.

  Town Hall / liberty bells: not important in a brand-new, small
  settlement, but as the colony grows you have to start producing
  liberty bells (statesmen in the Town Hall) to keep growing the rebel
  (Sons of Liberty) percentage — otherwise rebel% falls behind
  population growth and triggers a significant production penalty
  colony-wide. A strong colony-builder recognizes the transition point
  and starts staffing the Town Hall before that penalty bites, rather
  than reacting after production has already dropped.

  A newbie AI wouldn't handle any of this well (no differentiation
  between settlements at all). A higher-level AI needs to classify its
  own settlements into something like minor / normal / major, and scale
  liberty-bell attention up with settlement importance — major
  settlements get more Town Hall staffing priority than minor ones,
  rather than applying one flat rule to every colony regardless of
  its role.

  Early-game refining experts: a low-level AI wouldn't know to rush
  recruiting/purchasing a refining expert from Europe as early as
  possible (e.g. Master Weaver, Master Tobacconist, Master Distiller),
  even though gold is tight early on and it means fighting to scrape
  the cost together. First colonies typically end up producing a raw
  good at the main house — cotton, tobacco, sugar cane — and getting
  the matching expert in quickly lets you sell the refined product
  (cloth, cigars, rum) instead of the raw good, at roughly 3x the
  value. A strong colony-builder treats this as a priority early
  investment, not something to save gold on.

  Max production requires pursuing three things together, not any one
  in isolation: upgraded buildings (production chain tiers), upgraded
  workers in every position (experts, not generalists), and max rebel%
  (Sons of Liberty) for the production bonus it grants. The highest-
  level AI understands this as one combined goal and works all three
  in parallel. Lower-level AIs handle this less seriously/responsibly
  — e.g. pursuing only one of the three dimensions, or working on them
  inconsistently/piecemeal rather than as a coordinated push.
  This combined push is how a single settlement gets driven up to
  its practical max size (Larry recalls something like 38 citizens)
  with full production — not achievable by any one of the three alone.

  Printing Press / Newspaper: important for liberty bell production
  (same "grows in importance as the colony grows" pattern as Town
  Hall above), so more valuable in mid/large settlements than small
  ones — though Larry personally has been building these early
  regardless of settlement size lately.

  General caveat from Larry (applies broadly across this whole
  section, not just this one point): strategy isn't one-size-fits-all
  — what's "right" can vary by how you want to try things, so treat
  these weak/moderate/strong descriptions as a spread of *how much the
  AI understands and can act on*, not as one single prescribed correct
  playstyle every AI level should converge toward.

  Important qualifier on the minor/normal/major classification above:
  a strong AI does NOT try to build every upgrade in every settlement —
  that's wasteful in itself, not a sign of strength. Not every colony
  should get pushed toward mid/large. **Open question, unresolved**:
  Larry can't yet articulate precisely how he decides which settlements
  he pushes to mid/large size vs. which he deliberately keeps minor —
  it's currently intuition/experience rather than a nameable rule. This
  needs more thought before it can be turned into actual AI logic; don't
  invent a rule here to fill the gap, revisit once Larry can pin it down
  (site quality? role in the empire — e.g. resource source vs. population
  hub? something else?).

  If a colony is wasting product (overflowing warehouse capacity,
  losing goods to spoilage), building a wagon train sometimes gets used
  as extra mobile storage space to buy time until the real fix (bigger
  warehouse, better shipping/sales cadence) is in place.

  The resource tiles surrounding a settlement site should primarily
  determine its main production focus — though it can vary a lot site
  to site, not a strict formula. A low-level AI wouldn't pay attention
  to this at all (production focus disconnected from what's actually
  around the colony); a higher-level AI reads the surrounding tiles
  and adapts its production strategy to what the site actually offers.
  Especially true for bonus resource tiles specifically (e.g. Minerals,
  Prime Timber, and similar special resource bonuses) — a strong AI
  should weight these heavily when deciding a settlement's production
  focus, not just treat the underlying terrain type as if the bonus
  resource weren't there.

  Site selection can also weigh defensibility (terrain defense bonus,
  e.g. hills) alongside production potential when choosing where to
  found a settlement in the first place — a higher-level AI is more
  likely to spot and value a defensible site. Larry's own note: he
  doesn't always factor this in himself when picking a site, but
  sometimes does — another case (like the immigration-focus caveat
  earlier) where "strong" means capable of recognizing and weighing
  it, not a rule that must always win out over production potential.

  Not every specialist can be trained or purchased in Europe — some
  unit types can only be obtained by on-the-job experience at the
  colony (the Free-Colonist-training-up-into-an-expert path already
  covered above) or by learning a skill directly from a native
  settlement (a Free Colonist visiting an Indian settlement to learn
  their taught skill, per the settlement info panel's "skill that can
  be learned here" line). A strong AI needs to know which experts fall
  into this Europe-unavailable category and actively pursue the
  colony-training or native-learning path for them, rather than just
  waiting on a Europe recruitment pool that will never offer them. A
  low-level AI trips up on exactly this — it doesn't distinguish
  Europe-available experts from colony-only/native-only ones, so it
  either waits indefinitely on a recruitment pool that will never
  produce them, or otherwise mishandles getting them.

  General pattern once a University exists: it becomes a training hub,
  and a strong AI specifically reserves the unit types that *can't* be
  trained in Europe (the category above — e.g. Seasoned Scout, per the
  Exploration section) to stay there as teachers, rather than sending
  them out to work/adventure. Larry's note: you can have up to 3 of
  each (teacher capacity scales with school tier — University holds 3
  simultaneous teachers, vs. 2 for College and 1 for Schoolhouse).

  Population growth mechanism (Larry recalled this one after the fact,
  filling part of the food/growth gap flagged above; confirmed against
  spec.xml): once a colony's stored food reaches 200, a new Free
  Colonist is automatically "born," consuming that 200 food. A strong
  AI needs to manage food production/storage with this threshold in
  mind — deliberately working toward it as a growth lever, not just
  producing food incidentally and treating a birth as a surprise.
  Confirmed in code (twice, since it initially looked otherwise from
  play experience): food is completely exempt from warehouse capacity
  - no storage cap, no turn-end waste removal - so no warehouse
  building tier is actually *required* for this; it only takes enough
  net food surplus (production minus consumption) to reach 200. Two
  distinct levers a strong AI has for getting a colony to 200, and it
  should recognize both rather than only ever relying on one:
  - **Organic growth**: build up local food production (expert
    farmers/fishermen, good tiles) until it runs a positive surplus
    and 200 is reached on its own over time. Warehouse-tier buildings
    aren't the actual cause here, but tend to correlate with it, since
    a colony developed enough to have built a Warehouse is usually
    also developed enough to be running a real food surplus.
  - **Forced growth**: ship/import surplus food in from elsewhere
    (wagon/ship delivery) to push an underdeveloped colony to 200
    directly, rather than waiting on its own local production - useful
    specifically for a colony too small/young to have a food surplus
    of its own yet. Directly connects to the wagon/shipping-network
    material already in this doc's Auto Shipping section.

  Cost-aware colonist acquisition, a third lever alongside the two
  growth types above: paying to recruit the next immigrant in Europe
  gets progressively more expensive each time you use it - a quirk
  Larry only noticed after a while of play. A weak/newbie AI just
  keeps paying that price regardless of how high it's climbed. A
  strong AI's actual rule is simpler than it might sound: once the
  price gets too high, just stop paying for immigrants (fall back to
  organic immigration via crosses production instead), rather than
  paying an ever-climbing price indefinitely.

  Exception - trained immigrants: the Europe recruitment pool
  sometimes offers a specific already-trained specialist as one of
  the candidates, not just a generic colonist slot. For that case, a
  strong AI doesn't just apply the flat stop-paying rule - it compares
  that candidate's recruitment cost against the cost of training that
  *same* specialty directly (e.g. training a miner costs 600 gold per
  Larry's recall), and takes whichever is cheaper for that specific
  type, rather than treating all recruitment offers identically.

  Even though Warehouse tier doesn't gate food/population growth
  specifically, it still matters a lot for every *other* goods type,
  which are NOT exempt from capacity waste. A low-level AI might not
  prioritize warehouse construction at all, leading to routine waste
  on ordinary production goods (the "!"/"+" warehouse warning badges
  from LarryDGray's Mods would light up constantly on such a colony) -
  a strong AI keeps warehouse capacity ahead of its actual production
  volume, same general pattern as the Armory/Drydock infrastructure
  timing point in the Combat section.

  Capturing another nation's settlement is itself a growth strategy —
  and the population gain isn't just the city's own internal workers.
  Sharper version of Larry's observation: he personally struggles to
  get *enough* citizens to spread out and staff all the work he wants
  done — population is a genuinely scarce, hard-won resource for a
  human player (see the food-threshold and training-pipeline points
  above — growth is slow and deliberate). The current AI, by contrast,
  somehow ends up with *way more* citizens than it needs, and a lot of
  them just hang out outside the city doing nothing — critically,
  that idle group is often mixed with some already-*trained* citizens
  too, not just unspecialized Free Colonists, so it's not merely
  surplus population, it's wasted investment. When you capture that
  city, you typically pick up a real population boost from everything
  stationed outside the gates too, not just the settlement's own
  worker count. A strong AI should factor "this capture would
  meaningfully grow my empire — including whatever's camped outside
  it" into target selection, not just whether the city itself can be
  taken (see Combat/Offence above for the attack-side considerations).
  Larry's own take on the underlying cause: he suspects this is
  actually a **flaw in the current AI** — i.e., not a deliberate
  strategy at all, just the AI mismanaging its own population/
  placement (and possibly having more population to mismanage in the
  first place via the existing difficulty "cheat" grants — free land
  units are a real, spec-confirmed cheat category, see the Cheat
  section near the top of this doc) — rather than something
  intentional worth replicating. If that's right, "smarter" AI here
  might mean *not* leaving citizens (trained ones especially) stranded
  outside its own cities in the first place, while a strong AI still
  knows to exploit the
  opposing AI's version of this flaw via capture. Worth confirming
  directly against the AI code before designing around it either way.

  Separately, a real starvation-avoidance technique for your *own*
  colonies: if a colony's food is trending toward starvation, move
  citizens *out* of it rather than just accepting the loss — a unit
  standing outside a colony doesn't draw on that colony's food supply
  at all ("they live off the land"), and can still be put to
  productive use outside the city (plowing, road-building, etc.)
  instead of sitting idle. This is a real lever for managing
  population against food capacity, not just a fallback.

## Combat: weak → strong strategies

**Important cross-cutting note**: the stock AI has zero awareness of
this repo's own artillery-related mods — Coastal Defence Bonus,
Artillery Support Bonus, Artillery Bombardment (all confirmed earlier
this session: AI decision code never even reads difficulty level, let
alone these bonuses, which didn't exist in stock FreeCol to begin with).
A new/higher-level AI under this design should specifically be aware of
them and factor artillery placement into both defensive positioning
(Coastal Defence Bonus — artillery present on a settlement's tile boosts
its defense) and offensive positioning (Artillery Support Bonus — a
cannon backing up an attacking stack boosts that attack), rather than
placing artillery arbitrarily or not at all. Same blind spot applies to
**Naval Bombardment** (this repo's mod letting a combat-capable ship
attack an undefended coastal settlement directly instead of just being
denied the move) — the stock AI doesn't know this option exists either.
Larry's take: naval combat itself is fairly simple/straightforward
otherwise, so this bombardment-awareness gap may be one of the smaller,
more self-contained pieces of the whole design to add.

Two more specific blind spots the current AI likely has: (1) with the
Artillery Bombardment mod on, a cannon can no longer just walk into an
undefended settlement and capture it outright — it bombards instead now
(random disaster effect: building/unit/goods/production loss) — the AI's
old assumed behavior of "artillery captures undefended settlements" is
simply wrong under this mod, and it probably also doesn't understand
what the bombardment disaster actually accomplishes strategically, since
it's a new mechanic with no stock equivalent. (2) Ship-risk calculus:
the possibility of a ship being sunk/damaged makes attacking with it,
by itself, less attractive as a pure combat choice — but a ship's
attack/bombardment can still be very valuable as *support* for an
actual land capture (softening defenses ahead of a land assault), even
when the ship attacking alone wouldn't be worth the risk on its own. A
strong AI needs to value naval bombardment for its capture-enabling
effect, not just evaluate it as an isolated risk/reward naval fight.

Baseline infrastructure, relevant to both Offence and Defence below: you
need an Armory (→ Magazine → Arsenal) to produce muskets/cannons, and a
Drydock (→ Shipyard) to build ships or naval gunships at all. A weak AI
neglects this or builds it too late/reactively; a strong AI keeps this
infrastructure ahead of need rather than scrambling for it once a threat
or opportunity is already at the door.

Drydock specifically also matters for ship repair: without one, a
damaged ship has to sail all the way back to Europe to be repaired,
costing significant time. A weak AI won't prioritize building a Drydock
early; Larry's own habit is building one fairly soon specifically to
avoid ships being sent to Europe for repairs.

Training Veteran Soldiers (as opposed to just equipping a Free Colonist
with muskets for a basic soldier role) requires a University — Veteran
Soldier is a skill-3 taught profession (schoolhouse → college → university
progression), the highest tier, so it needs the fully-upgraded school
building with a Colonial Regular or Veteran Soldier as the teacher. A
weak AI relies purely on equipping colonists rather than working toward
homegrown Veteran Soldiers via education. Not important early on — the
timing matters: this becomes worth prioritizing specifically once the AI
is actually gearing up for war, not something to rush in the opening game
alongside everything else.

### Offence

*(Larry to fill in — what does a weak AI attacker do vs. a strong one?
When to strike vs. hold back, target selection, use of Artillery
Bombardment / Artillery Support Bonus, stacking/timing attacks, naval vs.
land offense coordination, follow-through after a win, etc.)*

- Weak: Attacks somewhat randomly, more to cause chaos/havoc than
  toward any real objective — low-level AI in particular does this
  kind of scattershot attacking on settlements with no real plan.
- Moderate:
- Strong: To seriously take a fortified settlement, you have to
  constantly scout/spy on it first to see what's actually garrisoned
  inside before committing to an attack, rather than attacking blind —
  and that garrison read is also what tells you the size of force
  you'd actually need to have a real chance of taking the settlement,
  not just whether it's defended at all. Spying shows garrison count,
  and how many horses/guns (equipment) are present. It does NOT show
  whether the colony has **Paul Revere** — the founding father that
  lets an unarmed colonist auto-equip from stored muskets when
  attacked. A settlement that looks undefended on the spy report can
  still fight back if it has Revere and a musket stockpile, and there
  is no way to know that in advance — you only find out by actually
  attacking a settlement that appeared to have no armed units. A
  strong AI needs to treat "no visible garrison" as residual risk, not
  certainty, precisely because of this blind spot. (Confirmed against
  spec: Paul Revere's ability is scoped to `model.role.soldier` only —
  muskets, not horses — so it doesn't auto-equip a dragoon today. Larry
  floated extending it to dragoon too as a possible future mod idea,
  separate from this AI-levels design — not pursuing now.)
  (Cross-reference: this is the exact interaction — hovering the
  population tooltip on a spied colony — that's behind the known,
  still-unresolved `HashMap.getNode()` freeze bug documented in
  [[project_freecol_hashmap_freeze_bug]]/README_MODS.md's "Known,
  unresolved issues" section. Worth keeping in mind that leaning on
  this mechanic more heavily, AI-side or player-side, runs into that
  bug more often until it's fixed.)

  Losing a battle usually demotes a unit (strips its equipment/role —
  e.g. dragoon → soldier → unarmed colonist) rather than killing it
  outright. A strong AI re-arms or re-dragoons that surviving unit
  afterward if the muskets/horses are available, treating it as a
  recoverable asset rather than leaving it stripped and idle.

  Per the cross-cutting artillery note above: a strong AI positions
  artillery on the SAME tile as an attacking stack specifically to
  claim the Artillery Support Bonus, rather than sending cannons in
  unescorted or leaving infantry/cavalry to attack without support.

  An attacking AI also needs to factor in the target's fortification
  level (Stockade/Fort/Fortress) and terrain defense bonus (e.g. hills)
  when deciding whether/how to attack, not just garrison size — the
  same stacking-modifiers-compound-sequentially effect noted earlier
  (Coastal Defence Bonus discussion in the mods memory) means a well-
  fortified settlement on defensible terrain can be a much tougher
  target than its garrison count alone would suggest.

### Defence

*(Larry to fill in — what separates weak vs. strong AI defense? Garrison
sizing, fortification timing, use of Coastal Defence Bonus / Naval
Bombardment resistance, stockade/fort/fortress timing, response speed to
an incoming threat, turtling vs. overextending, etc.)*

- Weak:
- Moderate:
- Strong: Per the cross-cutting artillery note above: a strong AI
  prioritizes keeping artillery stationed at settlements worth
  defending specifically to claim the Coastal Defence Bonus, rather
  than leaving vulnerable settlements with no cannon present at all
  or scattering artillery without regard to which settlements actually
  need the defensive boost most.

  Against an aggressive human (or AI) opponent, for important
  strongholds specifically: position fortified units on better
  defensible terrain *outside* the city, forming an outer defense
  screen around it, and maintain that posture until peace is
  re-established — not relying purely on the garrison inside the
  settlement itself. Larry: "I'm sure there are lots of strategies
  I've never worked out" — this list will keep growing over time,
  not meant to be treated as complete.

  Blockading: a higher-level AI can blockade an enemy settlement, from
  either land or sea, as an offensive tactic — not a distinct coded
  game mechanic, just deliberately positioning units to choke off an
  enemy settlement's movement/access rather than only ever going
  straight for a direct assault.

  More exploratory territory, Larry thinking out loud rather than
  settled design (his own words: "I don't know" at the end of this) —
  worth keeping as a starting point for the noise/randomness mechanism
  rather than a hard rule:
  - Some decisions might be genuinely random even for a strong AI —
    randomly leaning toward one strategic avenue over another isn't
    necessarily a weakness by itself (ties into the noise-injected
    scoring idea from the mechanism section above).
  - Higher levels watch for tactical-advantage opportunities and act
    on them opportunistically (aggressiveness that's reactive to a
    spotted opening, not just a flat aggression dial).
  - Skirmishing (small-scale harassment rather than a real conquest
    attempt) happens at every level to some degree, more or less — not
    purely a low-level-only behavior, but lower-level AI leans on it
    more, largely as random havoc-causing rather than a real tactic.
  - Target selection over time: lower-level AI tends to go after
    smaller/easier settlements first; higher-level AI is more patient
    and watches an enemy's growth for the right opportunity to strike
    a larger, more valuable settlement instead of just taking whatever
    looks easiest right now.

## Trade: weak → strong strategies

*(Larry to fill in — what does a weak AI trader do vs. a strong one?
Choosing what to produce for market vs. sell raw, timing sales against
price crashes, use of Custom House, refining vs. shipping raw goods,
founding-father/market-related choices, use of this repo's Ships Require
Cloth mod, etc.)*

**Coverage gap, same caveat as Auto Shipping below**: everything in this
section so far is about Custom House / Europe market trade specifically.
Larry has explored native trading in depth, but not trading with other
European nations directly (the `DiplomaticTrade`/`TradeContext.TRADE`
mechanic surveyed earlier this session — gated behind Jan de Witt, no
real price/haggling system, and the AI's response to it is currently
just its generic treaty-acceptance heuristic reused, not anything
purpose-built). If foreign-nation trade ends up in scope for this
domain, it'll need the same kind of independent investigation as Auto
Shipping rather than dictated play experience.

- Weak: Builds Custom Houses somewhat randomly (not tied to any
  settlement-selection logic), and only recognizes/exports locally
  produced surplus — treats the Custom House as a purely local
  building rather than part of any wider system. Only exports what's
  produced *in that settlement*, otherwise falls back to shipping
  everything to England regardless of whether a Custom House would
  help.
- Moderate: Prefers putting Custom Houses in large/high-production
  settlements specifically, and automatically sells local surplus
  there. May occasionally route goods in from nearby settlements, but
  not as a designed system.
- Strong: Coastal vs. inland placement isn't really the core strategic
  question — the real concept is **regional export hubs**. You don't
  build a Custom House in every settlement (even where inland ones are
  allowed); you build them in your larger, developed settlements and
  use wagons to funnel goods in from smaller production settlements.
  So the AI models a Custom House settlement as a hub:
  `small producers → wagon network → large hub → Custom House → sale`.
  This requires evaluating several things together: how much
  exportable production exists nearby, transport distance, available
  wagons, storage capacity, settlement size/development, and whether
  another Custom House would actually save enough hauling to justify
  building it. At the top end, the AI also compares the whole system
  against direct shipping to England — "is wagon → Custom House
  actually better than wagon → port → ship → England?" — a decision
  that can shift with distance, wagon availability, ship capacity,
  storage, commodity value, threats at sea, and how urgently cash is
  needed right now.

  This reframes what "valuing Jan de Witt highly" actually means for a
  strong AI. It's not "Custom Houses are convenient" — it's planning a
  whole logistics system around them: getting the ability early lets
  you develop small settlements purely for production without needing
  every settlement to become a fully developed commercial center on
  its own. Concretely, a mediocre AI's founding-father reasoning is
  "de Witt unlocks Custom House = good"; a strong AI's reasoning is
  "I have six producing settlements, two good hub locations, and
  enough wagons — unlocking Custom Houses now removes a major export
  bottleneck." That second kind of reasoning is what would make a
  high-level AI actually feel like an experienced human player, not
  just a better-resourced one.

## Auto shipping / trade routes: weak → strong strategies

*(Larry to fill in — what does a weak AI do with its own trade routes and
inter-colony shipping vs. a strong one? Route efficiency, ship sizing vs.
cargo volume, stop sequencing, reacting to changing colony needs, keeping
ships loaded vs. idle, use of this repo's Send Fleet / Armada mods, etc.
Note from the earlier "Survey trade route / automated shipping
implementation" investigation this session: player-drawn Trade Routes are
a pure human-player feature today — the AI's own goods-shuttling uses a
completely separate mechanism (TransportMission / WishRealizationMission /
GoodsWish) that only moves goods between the AI's own colonies and Europe,
so "smarter shipping" here means improving that AI-only mechanism, not the
player-facing Trade Route feature.)*

**Coverage gap, worth being honest about**: unlike Colony Building and
Combat, Larry doesn't have deep personal experience to draw on here —
his own play has always been manual shipping, he's never really used
automated trade routes himself. His impression is that the old/current
AI doesn't move goods around much if at all, though the "Survey trade
route" investigation above found the AI does have a real (if separate
from the player-facing feature) mechanism for this — how *actively or
effectively* the AI actually uses it wasn't established, just that the
plumbing exists. This domain will likely need more independent code
investigation/design work rather than leaning on dictated play
experience, unlike the domains above.

- Weak: No coordination with the colony-wide trade strategy — wagons
  (if used at all) move somewhat arbitrarily rather than serving a
  designed goods flow.
- Moderate:
- Strong: Directly serves the "regional export hub" strategy from the
  Trade section above — once the strategic layer decides "this
  settlement is my regional export hub," the shipping/wagon layer
  needs to know that and start directing surplus toward it. At the
  high end, the AI assigns wagons specifically to collect surplus from
  satellite/producing settlements and deliver it to the designated hub
  settlement(s). **Important design point: wagon AI and Custom House
  AI can't be independent systems** — they have to share the same
  picture of which settlements are hubs, or the wagon routing has
  nothing coherent to aim at. This is a case where a Trade-domain
  decision (where are my export hubs) and a Shipping-domain decision
  (how do goods get there) are really one decision split across two
  execution layers, not two separate strategies to design in isolation.

## Native interactions: weak → strong strategies

*(Larry to fill in — what does weak vs. strong handling of native
settlements look like? Managing alarm/tension proactively vs. reactively,
missionary use (establishing missions, denouncing rivals, converts as a
colonist source), scouting for tales/gifts, gift-giving to ease relations,
land purchase vs. taking it, responding to native demands, when to fight
vs. appease, etc.)*

- Weak:
- Moderate:
- Strong: If native alarm gets too high (angry), trade with them shuts
  down until it's fixed — the two fixes are getting the founding father
  **Pocahontas** (resets/improves native relations) or establishing
  missions there instead. A strong AI recognizes this and reacts by
  deliberately working the political track of founding fathers to get
  Pocahontas sooner once relations have soured, rather than continuing
  business as usual with a trade route that's now blocked. Note from
  Larry: he only recently learned this interaction himself, from live
  play — a good example of the kind of situational knowledge that's
  genuinely hard to encode as a rule versus something a strong human
  player picks up from experience and reacts to in the moment.

## Founding father selection — implementation difficulty note

General caveat from Larry, worth keeping visible rather than burying it
in one domain section: founding father AI selection may be one of the
harder pieces of this whole design to actually code well. It's not a
static "which fathers are generically good" ranking — it's reactive and
situational (e.g. the Pocahontas-after-native-anger case above, or the
de Witt-for-logistics reasoning in the Trade section). A strong AI's
founding father choices should shift based on the *current state of the
game* (native relations souring, a logistics bottleneck emerging, a
military threat appearing), not just a fixed priority list read top to
bottom. Worth treating founding father selection as its own cross-cutting
concern that reads signals from all the other domains, rather than a
seventh isolated domain bullet-list like the others in this doc.

Every founding father involves trade-offs — value depends on what
strategy is already in motion, not a fixed generic ranking. Example:
**Adam Smith** (unlocks Factory-level buildings) isn't very important
early on, but if the AI is already pushing toward a Custom House /
export-hub strategy (see Trade section), Adam Smith becomes worth taking
when he comes up — factory-tier production synergizes directly with an
established high-throughput export pipeline. A strong AI's evaluation of
a given father should be conditioned on the strategy already underway,
not evaluated as if every game state were the same.

There's also a meta-layer on top of individual father value: you might
deliberately select a mediocre father in a given category just to clear
them out of the way, so a more valuable father from that same branch has
a chance to come up in a future recruitment offer, rather than skipping
recruitment and leaving the weaker one sitting in the pool. A strong AI's
father selection isn't purely "is this candidate good," it can also be
"is taking this candidate now the best way to reach a better one later."
A low-level AI wouldn't do any of this pool-management reasoning — it
just picks the best-looking option from whatever candidates happen to be
on offer *right now*, with no eye toward what selecting them clears out
of the way or opens up down the line.

Category-to-strategy mapping, more examples of the same "value depends on
what you're already pushing toward" principle: Religious-category fathers
help cross production, so they're worth prioritizing specifically when
pursuing an immigration-focused strategy (see the crosses/Church point in
Colony Building); Political-category fathers help boost liberty bell
production, so they matter more when pursuing the rebel%/production-bonus
push (see the Town Hall point in Colony Building). At least one father's
benefit scales with the current tax rate, making that one better to take
*later* in the game once tax is already high, rather than early when tax
is still low and the same benefit would be worth less.

## Exploration: weak → strong strategies

*(Larry to fill in — what does weak vs. strong AI exploration look like?
Scout deployment pace, lost city rumour investigation (risk vs. reward),
map coverage priorities, timing exploration against colonization/expansion
needs, using scouts to find good colony sites ahead of settling, treasure
train handling once found, etc.)*

- Weak: Wanders around somewhat randomly and only accidentally stumbles
  into native settlements/gold gifts as a side effect, rather than
  deliberately seeking them out.
- Moderate:
- Strong: Early-game exploration can pay off big, mostly by meeting
  native tribes via scouting ("speak to chief") — the beads/gold gifts
  received there are typically Larry's initial funding source for an
  extra ship or two and a few trained/purchased experts early on,
  rather than waiting to earn that gold purely through trade. A
  higher-level AI deliberately seeks out villages, or specifically
  seeks rumored higher-yield native cultures/settlements, rather than
  exploring at random and taking whatever gifts happen to turn up.

  Seasoned Scout (the expert scout unit) is a top-tier unit that can't
  be reliably obtained on demand — the only paths are running a Free
  Colonist as a scout and hoping lost city rumours convert it, or pure
  luck via random Europe immigration offering one. Scouts also die
  easily investigating rumours. A higher-level AI is more protective of
  a Seasoned Scout once it has one — not sending it into risky ruins
  itself (using expendable units like petty criminals/indentured
  servants for that instead) — and keeps one stationed back at a
  College or University where it can serve as a teacher, training more
  Seasoned Scouts via education instead of relying on luck for every
  additional one.

## Open questions

- Does every tier need a *unique* behavior set, or do we cluster (e.g.
  bottom two tiers share "baseline", top two share "advanced", middle is
  the transition)?
- Should this apply symmetrically to `EuropeanAIPlayer` and
  `NativeAIPlayer`, or differ (natives might reasonably stay simpler)?
- Any interaction with the existing "cheat" economic handicaps — do we
  keep those as-is alongside real behavior differences, tone them down
  since real skill differences now exist, or leave that decision for later?
- Architectural note worth remembering at implementation time: Trade
  (regional export hub selection) and Auto Shipping (wagon routing) are
  not independent systems — see the Custom House hub discussion in the
  Trade section. Whatever code ends up choosing hub settlements needs
  to expose that decision somewhere the shipping/wagon logic can read
  it, not just make the decision locally within a Trade-only module.
- **To do later, flagged by Larry, not yet designed**: unit movement
  logic (Auto Shipping domain especially, but touches Colony Building
  and Combat too — troop transport, wagon routing) needs to handle
  both cases: with this repo's Caravan/Armada mods active (group
  movement via a leader carrying passengers) and without them (vanilla
  one-unit-per-carrier-slot movement). The AI shouldn't assume the
  mods are always on, since they're individually toggleable client/
  game options. When they ARE active, the AI needs actual judgment
  about *when* forming a Caravan/Armada is worth it for a given trip
  (grouping multiple units to cross a distance together, then
  presumably dispersing again at the destination) rather than either
  never using the mechanic or using it indiscriminately for every
  movement regardless of whether grouping actually helps.
