# rewards.yml

Prizes for racers who finish, as commands run from the console.

```yaml
Places:
  1:
    - "give {player} diamond 3"
    - "say {player} won the pig race in {time}!"
  2:
    - "give {player} gold_ingot 5"
  3:
    - "give {player} iron_ingot 5"

AllFinishers:
  - "give {player} cooked_porkchop 8"
```

| Setting | Default | What it does |
|---|---|---|
| `Places` | `{}` (none) | Commands for each finishing place. List as many places as you like; places not listed get nothing extra |
| `AllFinishers` | `[]` (none) | Commands for everyone who finishes, run after their place's commands |

Write commands without the slash (a leading `/` is ignored). They run from the console, so any plugin's commands work: economy, crates, ranks and so on.

## Placeholders

| Placeholder | Becomes |
|---|---|
| `{player}` | The racer's name |
| `{uuid}` | Their UUID |
| `{position}` | Their place as a number: `1`, `2`, `3` |
| `{place}` | Their place as a word: `1st`, `2nd`, `3rd` |
| `{time}` | Their race time |
| `{racers}` | How many raced |

## When rewards are given
- Each racer's commands run as soon as they finish, once they're back in the lobby, so any items land there.
- Racers who leave, log out, or haven't finished when the time limit runs out or the race is stopped get nothing.
- A command that fails or doesn't exist is logged in the console, and the rest still run.

Run `/race reload` after changing this file.
