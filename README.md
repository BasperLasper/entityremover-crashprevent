# EntityRemoverCrashPrevent

A Paper 26.2-compatible plugin that automatically removes excessive non-named entities to reduce lag and crash risk.

## Features

- Automatic cleanup every 5 minutes by default
- Scans all loaded Bukkit worlds
- Protects players and custom-named entities by default
- Removes primed TNT automatically
- Manual cleanup via /entitycleanup with aliases /ecleanup and /clearentities
- Configurable warning countdowns, world filters, and protected entity types
- Safe server-thread execution and entity validity checks

## Commands

Permissions:

- `entityremover.cleanup` for players
- Console always has access

Commands:

- `/entitycleanup`
- `/ecleanup`
- `/clearentities`

## Release

Published release:

- GitHub Release: https://github.com/BasperLasper/entityremover-crashprevent/releases/tag/v1.0.0
- Download jar: https://github.com/BasperLasper/entityremover-crashprevent/releases/download/v1.0.0/EntityRemoverCrashPrevent-1.0.0.jar

## Build

```bash
mvn clean package
```

The built jar will be created at:

```text
target/EntityRemoverCrashPrevent-1.0.0.jar
```

A copy is also stored in:

```text
releases/EntityRemoverCrashPrevent-1.0.0.jar
```

## Configuration

See the generated `config.yml` for:

- cleanup interval in seconds
- warning times and broadcast control
- world blacklist/whitelist
- protected entity types
- named-entity protection

## License

MIT-style project use, intended for Paper server deployment.
