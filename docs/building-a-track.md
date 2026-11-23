# Building a track

A track is a lobby, a start, and a loop of rings racers fly through in order. Everything is placed in game by standing where you want it. You need `pigracing.admin`.

## 1. The lobby
Stand where racers should wait and run:

```
/race setlobby
```

Players are sent here when they join, and come back here when they finish, leave or the race ends.

## 2. The start
Stand on the start line, look down the track, and run:

```
/race setstart
```

Every pig spawns here, facing the way you were looking.

## 3. The rings
Rings are checkpoints. Racers have to fly through them in order, and each one passed makes their pig faster.

Stand where the ring should go, look the way racers will fly, and run:

```
/race ring add [size]
```

- The ring is centred above where you stand and faces where you look. Stand at the bottom middle of an opening and look through it, and a ring that matches the opening's width fits it exactly.
- `size` is the ring's width in blocks, from 1 to 32. A size 3 ring fits a 3 x 3 opening. Leave it out to use `Rings.DefaultSize` (3).
- Look up or down to tilt the ring. Look straight up or down for a flat ring racers fly through vertically.
- Racers can fly through a ring from either side.

Add the rings in the order racers should fly them. **Ring 1 is the start and finish line.** Racers start behind it, and after the last ring they fly through ring 1 again to complete a lap. Put it just in front of the start.

### Editing rings

| Command | What it does |
|---|---|
| `/race ring list` | Every ring, with its size, position and direction. Click a line to go there |
| `/race ring tp <n>` | Go to ring `n`, standing where it was placed from |
| `/race ring remove [n]` | Remove ring `n`, or the last ring. Later rings move down a number |
| `/race ring clear` | Remove every ring |

Rings can't be changed while a race is running. Stop it first with `/race stop`.

### Previewing
Rings are only drawn during a race. To see them while building, run:

```
/race preview
```

Run it again to hide them.

## 4. The boundary (optional)
Racers who fly too far are sent back to their last ring. By default that's anyone more than `MaxDistanceFromStart` blocks (150) from the start, or in another world.

For a tighter fit, mark a box around the track instead:

```
/race boundary pos1     stand in one corner
/race boundary pos2     stand in the opposite corner
/race boundary set      save it
```

Racers leaving the box are sent back to their last ring. You're warned if the start is outside the box. `/race boundary clear` removes it and goes back to the distance check.

## Checking it works
`/race start` refuses to open a race if the lobby or start isn't set, their world isn't loaded, or there are no rings, and tells you which. To test on your own, run `/race start`, `/race join`, then `/race forcestart` to skip the join timer and the minimum player count.
