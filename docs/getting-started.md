# Installation

## Requirements
- Spigot or Paper, Minecraft **1.8.8** or newer
- Java **8** or newer, or whatever your server version needs

Nothing else is needed. The libraries Pig Racing uses are inside the jar. If you use Multiverse, Pig Racing loads after it so your track's worlds are ready.

## Installing
1. Put the Pig Racing jar in your server's `plugins` folder.
2. Start the server. Pig Racing creates its files in `plugins/PigRacing/`:

   | File | What it's for |
   |---|---|
   | `config.yml` | Race rules, the schedule, blocked commands, flight, rings and sounds. See [config.yml](configuration/config.md) |
   | `messages.yml` | Every message, title and action bar. See [messages.yml](configuration/messages.md) |
   | `rewards.yml` | Commands run for finishers. See [rewards.yml](configuration/rewards.md) |
   | `track.yml` | The lobby, start, rings and boundary. Written by the setup commands; you don't need to edit it |

3. [Build a track](building-a-track.md).
4. Run `/race start` to open your first race.

## Changing settings
Edit the files, then run `/race reload`. It reads all four files again. It's best to reload between races rather than during one.

If a setting is missing from one of your files, its default is used, so after an update you don't have to add new settings by hand. To see the new options with their comments, rename your file and restart to get a fresh copy.

## Upgrading from an older version
Older versions kept the track in `config.yml`. On startup Pig Racing moves the lobby, start, rings and boundary into `track.yml` for you, and says so in the console.
