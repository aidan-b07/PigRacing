# How a race works

## Opening
A race opens when an admin runs `/race start` or a [scheduled race](configuration/config.md#schedule) comes up. Only one race runs at a time.

Everyone online gets a join message. Clicking any line of it joins, as does `/race join`. The message is repeated as joining closes, at the times in `Settings.AnnounceAt`.

## Waiting
Racers are sent to the lobby. The action bar shows how many have joined, how many are needed and how long is left. `/race leave` gets out at any point.

When `Settings.JoinTime` (60 seconds) is up:
- with nobody signed up, the race is called off
- with fewer than `Settings.MinPlayers` (2), it's cancelled and everyone is told why
- otherwise, it starts

`/race forcestart` closes joining straight away and starts with whoever has joined, however few.

## The countdown
Each racer is put on a saddled pig at the start. The pigs stay put while the countdown titles run (5 to 1 by default), then **GO!**

## Flying
- The pig flies the way its rider looks. By default racers hold **W** to move forward; set `Flight.RequireForwardKey: false` to fly forward on its own.
- **Tap space to flap.** Each tap pushes the pig up; gravity pulls it back down. Holding space doesn't keep it climbing, so height comes from timing taps.
- Each ring passed adds speed, up to `Flight.MaxSpeed`.
- Racers can punch each other's pigs to knock them off course.

The action bar shows the lap, the ring and the time left. Each racer's next ring is highlighted with a denser, faster animation.

## Crashing
A pig that touches the ground (anything but carpet, by default) has crashed. The racer is sent back just past their last ring, or to the start, and loses all their speed. The pig hovers for a moment so they can get going again.

Flying out of bounds does the same: too far from the start, or outside the [boundary](building-a-track.md#4-the-boundary-optional) if one is set.

## Laps and finishing
Flying through ring 1 again after the last ring completes a lap. Racers get a title for each lap, and a **FINAL LAP** title before the last.

After the last lap the racer's place and time is announced to the server. They're sent back to the lobby and their [rewards](configuration/rewards.md) are given there.

## The end
The race ends when every racer has finished or left. The results board, with each finisher's place and time, is sent to everyone, and `Settings.EndDelay` (5) seconds later the race is over.

If `Settings.TimeLimit` (300 seconds) runs out first, racers are warned as it nears. When it's up, finishers keep their places and rewards, everyone still flying is marked **DNF**, and all of them are sent back to the lobby. Set it to `0` for no limit.

## While racing
- Racers can't get off their pig, drop items or take damage (`Settings.ProtectRacers`).
- Commands are limited by the `Commands` section of `config.yml`. `/race` always works so anyone can leave.
- A racer who logs out is taken out of the race.
- `/race stop` ends the race at once and sends everyone to the lobby. Racers who haven't finished get no rewards.
