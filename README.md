# Botany Bay Sponge Plugin

Recreates RuneScape's **Botany Bay** spectacle inside SpongeForge. Administrators
can summon a public trial for a suspected botter while the online community votes
on the outcome. After the countdown the winning punishment is announced so you
can roleplay the verdict just like the original minigame event.

## Features

- `/botanybay ban <player> [reason]` bans a suspect and adds them to the Botany
  Bay queue for later judgment.
- `/botanybay start [player] [reason]` begins a trial for a specific suspect or
  automatically draws the next name from the waiting queue.
- Players vote for *Execution*, *Pillory*, or *Release* via `/botanybay vote`
or by clicking the interactive chat prompts.
- `/botanybay status` summarizes the remaining time and the current vote tally.
- `/botanybay cancel` lets staff abort a trial early.
- Automatically ends the event if the accused logs out.
- `/botanybay set npc` stores the tile where condemned suspects materialize as NPCs.
- `/botanybay setzone` (or `/botanybay set zone`) lets owners outline the arena that restricts voting.
- `/botanybay set bansign` links a sign that automatically displays the latest ban reason for onlookers.
- Condemned suspects reappear as frozen NPCs using their skin at the configured spawn point.
- Votes are only accepted from players standing inside the defined Botany Bay arena.

## Building

```bash
./gradlew build
```

The first invocation downloads the Gradle wrapper JAR on demand so you don't need
to commit the binary in version control. If the environment blocks outbound
network access, generate the wrapper locally by running `gradle wrapper` and copy
`gradle/wrapper/gradle-wrapper.jar` into the project before building.

The plugin JAR is produced in `build/libs/`.

## Installation

1. Copy the generated JAR into the `mods/` folder of your SpongeForge server.
2. Grant the following permissions through your permission plugin of choice:
   - `botanybay.command.start`
   - `botanybay.command.ban`
   - `botanybay.command.vote`
   - `botanybay.command.status`
   - `botanybay.command.cancel`
   - `botanybay.command.setnpc`
   - `botanybay.command.setzone`
   - `botanybay.command.setbansign`
3. Reload or restart the server and orchestrate your next Botany Bay spectacle!
