# LagTop

Paper / Purpur / Folia compatible plugin for identifying the five highest-activity loaded chunks.

## Commands

- `/lagtop` — opens the GUI for players; console receives a text report.
- `/lagtop gui` — opens the GUI.
- `/lagtop scan` — reset the scan cursor and immediately start a safe low-rate scan.
- `/lagtop reload` — reload `config.yml`.

Permissions:

- `lagtop.use`
- `lagtop.admin`

## What it measures

Each entry reports:

- Total entities currently observed in the chunk
- Redstone component block count
- Piston count
- Hopper count
- Observer count
- Recent redstone events
- Recent piston activations
- Recent hopper item moves
- Estimated Lag Score

CPU is reported for the Java server process plus overall system CPU load when the JVM exposes those values.

### Important accuracy note

Paper's public API does not expose the exact CPU time spent by each chunk. LagTop therefore does **not** claim that its score is a real per-chunk CPU measurement. The score is an estimate based on observable activity and component/entity density.

Chunk sampling is performed on the owning region using Paper/Folia's region scheduler. Static block counts are collected from a thread-safe `ChunkSnapshot`, and entity counts are sampled from loaded chunks.

## Build

Requires Gradle 9.4.1 and Java 21 for the project build. The produced bytecode targets Java 21.

GitHub Actions uploads the resulting `.jar` as the `LagTop-jar` workflow artifact. When you push a tag such as `v1.0.1`, the same `.jar` is also attached directly to a GitHub Release.
