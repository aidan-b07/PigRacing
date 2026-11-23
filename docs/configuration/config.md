# config.yml

How races play: the rules, the schedule, which commands racers can use, flight, the rings and sounds. The track itself is in `track.yml`, set by the [setup commands](../building-a-track.md).

Speeds are in blocks per tick. There are 20 ticks in a second, so `0.4` is 8 blocks a second. Run `/race reload` after changing anything.

## Settings

| Setting | Default | What it does |
|---|---|---|
| `Settings.Laps` | `3` | Laps to finish the race |
| `Settings.JoinTime` | `60` | Seconds players have to join after the race opens |
| `Settings.AnnounceAt` | `[30, 10, 5, 4, 3, 2, 1]` | Seconds left when the join message is sent again |
| `Settings.MinPlayers` | `2` | Racers needed when joining closes, or the race is cancelled. `/race forcestart` ignores it |
| `Settings.TimeLimit` | `300` | Seconds from GO to finish. `0` for no limit |
| `Settings.TimeWarnings` | `[60, 30, 10, 5, 4, 3, 2, 1]` | Seconds left when racers are warned |
| `Settings.EndDelay` | `5` | Seconds between the last racer finishing and the race closing |
| `Settings.MaxDistanceFromStart` | `150` | Racers further than this from the start, or in another world, go back to their last ring. `0` turns it off. Ignored when a [boundary](../building-a-track.md#4-the-boundary-optional) is set |
| `Settings.ProtectRacers` | `true` | Racers can't be hurt while racing |
| `Settings.GroundGraceTicks` | `60` | Ticks a pig can sit on the ground at the start or after respawning before it counts as a crash |
| `Settings.SafeGroundBlocks` | `["*CARPET"]` | Blocks pigs can land on without crashing. `*` matches anything, so `*CARPET` covers every carpet |

## Schedule

Races that open by themselves, as if an admin ran `/race start`.

```yaml
Schedule:
  Enabled: false
  EveryMinutes: 30
  Times: ["18:00", "21:00"]
  MinOnline: 2
```

| Setting | Default | What it does |
|---|---|---|
| `Schedule.Enabled` | `false` | Turns scheduled races on |
| `Schedule.EveryMinutes` | `30` | Open a race every this many minutes, counted from when the server starts. `0` for none |
| `Schedule.Times` | `["18:00", "21:00"]` | Also open one at these times of day, 24-hour clock, server time. `[]` for none |
| `Schedule.MinOnline` | `2` | Players that have to be online for a scheduled race to open |

A slot is skipped if a race is already running, too few players are online, or the track isn't set up. Players can see when the next race opens with `/race next`.

## Commands

Which commands players can use while they're in a race. `/race` always works, so anyone can leave.

```yaml
Commands:
  Waiting:
    Mode: blacklist
    List: [spawn, home, warp, tp, tpa, tpaccept, back, fly]
  Racing:
    Mode: whitelist
    List: [msg, tell, w, r, reply, me]
```

| Setting | What it does |
|---|---|
| `Commands.Waiting` | For racers in the lobby before the start, or finished and waiting for the others |
| `Commands.Racing` | For racers on a pig |
| `Mode` | `whitelist`: only the listed commands work. `blacklist`: everything but them works |
| `List` | Command names without the slash. `plugin:command` forms are matched by the part after the colon |

By default, waiting racers can't teleport away, and flying racers can only chat privately.

## Flight

Flappy-bird style: tap space to flap upwards, and gravity pulls the pig back down. Holding space doesn't keep it climbing. The pig flies the way its rider looks.

| Setting | Default | What it does |
|---|---|---|
| `Flight.BaseSpeed` | `0.4` | Forward speed with no rings passed |
| `Flight.SpeedPerCheckpoint` | `0.03` | Extra speed for each ring passed since the pig last crashed |
| `Flight.MaxSpeed` | `0.9` | The fastest a pig can go |
| `Flight.RequireForwardKey` | `true` | `true`: hold W to fly forward. `false`: the pig always flies forward |
| `Flight.JumpStrength` | `0.42` | Upward speed a flap gives. `0.42` rises about 1.5 blocks |
| `Flight.JumpCooldownTicks` | `3` | Ticks after a flap before the next one counts |
| `Flight.Gravity` | `0.06` | Downward speed added every tick |
| `Flight.MaxFallSpeed` | `0.6` | The fastest a pig can fall |
| `Flight.RespawnHoverTicks` | `30` | Ticks a pig holds its height after respawning, to give the racer time to get going |
| `Flight.KnockbackStrength` | `1.0` | How hard punching another racer's pig knocks it |
| `Flight.KnockbackFade` | `0.7` | How quickly that knock fades, from `0` (at once) to `0.95` (slowly) |

For a gentler race, lower `Gravity` and `MaxSpeed`. For a harder one, raise `SpeedPerCheckpoint` so crashing costs more.

## Rings

Rings are drawn in animated particles. Every ring has comets orbiting its rim; the ring a racer needs next also gets a dense pulsing outline, faster comets and a ripple shrinking to its centre.

| Setting | Default | What it does |
|---|---|---|
| `Rings.Enabled` | `true` | Draw the rings. With `false`, they still work but can't be seen |
| `Rings.DefaultSize` | `3` | Size used by `/race ring add` when none is given |
| `Rings.Interval` | `2` | Ticks between redraws. Lower is smoother but sends more particles |
| `Rings.ViewDistance` | `32` | Only rings this close to a player are drawn for them |
| `Rings.Color` | `#FF55FF` | The first of the two colours everything alternates between |
| `Rings.SecondColor` | `#FFFFFF` | The second colour |

**Every ring**

| Setting | Default | What it does |
|---|---|---|
| `Rings.OutlineSpacing` | `0` | Blocks between outline particles. `0` for no outline |
| `Rings.Comets` | `4` | Comets orbiting the rim |
| `Rings.CometLength` | `2` | Particles per comet, head included |
| `Rings.SpinSpeed` | `0.12` | How fast the comets orbit |

**The next ring**

| Setting | Default | What it does |
|---|---|---|
| `Rings.NextOutlineSpacing` | `0.5` | Blocks between outline particles |
| `Rings.NextComets` | `4` | Comets orbiting the rim |
| `Rings.NextCometLength` | `4` | Particles per comet |
| `Rings.NextSpinSpeed` | `0.25` | How fast the comets orbit |
| `Rings.Ripple` | `true` | Draw the ripple |
| `Rings.RippleSpeed` | `0.05` | How far in the ripple moves each redraw. `0.05` is one ripple every 20 redraws |
| `Rings.RippleSpacing` | `0.7` | Blocks between ripple particles |

On 1.8 every particle is its own packet, which is why the defaults are kept low. Raise them carefully, and check for lag with a full race.

## Sounds

Each sound has a `sound`, `volume` and `pitch`.

| Setting | When it plays | Default |
|---|---|---|
| `Sounds.Countdown` | Each second of the countdown | `BLOCK_NOTE_BLOCK_BASS` |
| `Sounds.Go` | At GO | `BLOCK_NOTE_BLOCK_PLING` |
| `Sounds.Checkpoint` | Passing a ring | `ENTITY_PLAYER_LEVELUP` |
| `Sounds.Lap` | Completing a lap | `ENTITY_PLAYER_LEVELUP`, higher pitch |
| `Sounds.TimeWarning` | Each time warning | `BLOCK_NOTE_BLOCK_HAT` |
| `Sounds.Crash` | Crashing, and when time runs out | `ENTITY_VILLAGER_DEATH` |

Use modern Minecraft sound names; older servers play the closest match. Set `sound: none` for silence.
