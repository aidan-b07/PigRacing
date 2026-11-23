# messages.yml

Everything players read: chat messages, titles and the action bar.

## Formatting
- Colour codes use `&`, e.g. `&d` for pink, `&l` for bold.
- `{prefix}` is replaced by the `Prefix` setting at the top of the file (`&d&lPigRacing &8| &f`).
- A line starting with `{center}` is centred in chat.
- Settings written as a list send one line per entry. An empty entry (`""`) sends a blank line.

Run `/race reload` after changing anything.

## Chat

### Joining
| Message | When it's sent | Placeholders |
|---|---|---|
| `Chat.JoinRace` | To everyone while a race takes sign-ups. Clicking any line joins | `{time}`, `{seconds}` ("second"/"seconds"), `{players}`, `{laps}` |
| `Chat.JoinHover` | Hover text on the join message | |
| `Chat.Joined` | To a player who joins | |
| `Chat.JoinedBroadcast`, `Chat.LeftBroadcast` | To everyone as players join or leave before the start | `{player}`, `{amount}` |
| `Chat.Leave` | To a player who leaves | |
| `Chat.NotEnoughPlayers` | To the racers, when too few joined | `{min}` |
| `Chat.RaceCancelled` | To everyone, when too few joined | `{min}` |
| `Chat.RaceVoided` | To everyone, when nobody is racing | |

### Racing
| Message | When it's sent | Placeholders |
|---|---|---|
| `Chat.LapComplete` | To a racer after each lap but the last | `{lap}`, `{total}`, `{time}` |
| `Chat.CompleteBroadcast` | To everyone when a racer finishes | `{player}`, `{position}`, `{time}` |
| `Chat.TimeWarning` | To racers as the time limit nears | `{time}` (as `1:00`), `{seconds}` |
| `Chat.TimeUp`, `Chat.NobodyFinished` | To everyone when the time limit runs out | |
| `Chat.OutOfBounds` | To a racer sent back for flying out of bounds | |
| `Chat.DismountMessage` | To a racer trying to get off their pig | |
| `Chat.CommandBlocked` | To a racer using a blocked command | |

### Results
`Chat.RaceOver` is the results board, sent to everyone at the end. Its `{results}` line becomes one `Chat.ResultLine` per finisher (`{position}`, `{player}`, `{time}`), followed by a `Chat.DidNotFinishLine` (`{player}`) for each racer still flying when time ran out.

### /race next
| Message | When it's sent | Placeholders |
|---|---|---|
| `Chat.NextRace` | The next scheduled race | `{in}` (e.g. `12 minutes`), `{at}` (e.g. `18:00`) |
| `Chat.NextRaceOpen` | A race is taking sign-ups now | |
| `Chat.NextRaceRunning` | A race is underway | |
| `Chat.NextRaceNone` | No race is scheduled | |

### Help and admin
`Chat.Help` is `/race help`, in four parts: `Header`, `Player` (shown to everyone), `Admin` (shown to `pigracing.admin`) and `Footer`. `{label}` is the command as typed, so `/pr help` shows `/pr` commands.

`Chat.Admin` holds the replies to the admin and setup commands. Each one lists its placeholders in a comment above it in the file.

## Titles
| Title | When it's shown | Placeholders |
|---|---|---|
| `Titles.Countdown` | One title per second before the start. The highest number sets the countdown's length | |
| `Titles.CountdownSubtitle` | Under each countdown title | |
| `Titles.Go`, `Titles.GoSubtitle` | At the start | |
| `Titles.Lap`, `Titles.LapSubtitle` | After each lap but the last two | `{lap}`, `{total}`, `{time}` |
| `Titles.FinalLap`, `Titles.FinalLapSubtitle` | Going into the last lap | `{lap}`, `{total}` |
| `Titles.Crashed`, `Titles.CrashedSubtitle` | After a crash | |
| `Titles.Finished`, `Titles.FinishedSubtitle` | On finishing | `{position}`, `{time}` |
| `Titles.TimeUp`, `Titles.TimeUpSubtitle` | To racers still flying when time runs out | |

To make the countdown 3 seconds, remove the `5` and `4` entries from `Titles.Countdown`.

## Action bar
| Setting | When it's shown | Placeholders |
|---|---|---|
| `ActionBar.Waiting` | In the lobby before the start | `{players}`, `{min}`, `{time}` |
| `ActionBar.Starting` | During the countdown | |
| `ActionBar.Racing` | While racing, with a time limit | `{lap}`, `{total}`, `{ring}`, `{rings}`, `{speed}`, `{time}`, `{remaining}` |
| `ActionBar.RacingNoLimit` | While racing, with no time limit | The same |
| `ActionBar.Finished` | After finishing, while others race | `{position}`, `{time}` |

In the racing bars, `{ring}` is rings passed this lap out of `{rings}`, `{speed}` is the pig's top speed in blocks per second, `{time}` is the race time so far and `{remaining}` the time left.
