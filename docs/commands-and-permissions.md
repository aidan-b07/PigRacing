# Commands and permissions

`/race` also works as `/pr`, `/pigrace` and `/pigracing`.

## Players

| Command | What it does |
|---|---|
| `/race join` | Join the race that's taking sign-ups. You're sent to the lobby |
| `/race leave` | Leave the race, at any point |
| `/race next` | Whether a race is open now, or when the next scheduled one opens |
| `/race help`, `/race` | List the commands you can use |

These need no permission.

## Running races

| Command | What it does | Permission |
|---|---|---|
| `/race start` | Open a race for sign-ups | `pigracing.start` |
| `/race forcestart` | Close joining and start now, ignoring `MinPlayers`. Calls the race off if nobody joined | `pigracing.admin` |
| `/race stop` | Stop the current race and send everyone to the lobby. Racers who haven't finished get no rewards | `pigracing.admin` |
| `/race reload` | Read `config.yml`, `messages.yml`, `rewards.yml` and `track.yml` again | `pigracing.admin` |

`/race start` won't open a race if one is already running, or if the track isn't ready. It says what's missing.

## Building the track

All of these need `pigracing.admin`. See [Building a track](building-a-track.md).

| Command | What it does |
|---|---|
| `/race setlobby` | Set the lobby where you stand |
| `/race setstart` | Set the start where you stand, facing where you look |
| `/race ring add [size]` | Add a ring where you stand, facing where you look. Size 1 to 32 |
| `/race ring remove [n]` | Remove ring `n`, or the last ring |
| `/race ring list` | List every ring. Click a line to go to it |
| `/race ring tp <n>` | Go to ring `n` |
| `/race ring clear` | Remove every ring |
| `/race preview` | Show or hide the rings while no race is on |
| `/race boundary pos1`, `pos2` | Mark the corners of the boundary where you stand |
| `/race boundary set` | Save the marked boundary |
| `/race boundary clear` | Remove the boundary |

Rings can't be added or removed while a race is running.

## Permissions

| Permission | Default | What it does |
|---|---|---|
| `pigracing.start` | Op | Use `/race start` |
| `pigracing.admin` | Op | Every admin command above. Includes `pigracing.start` |

Give `pigracing.start` to staff who should be able to open races without changing the track.
