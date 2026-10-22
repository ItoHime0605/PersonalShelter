# Personal Shelter stage 10 — NeoForge event integration

- Added server-side BlockEvent.BreakEvent / EntityPlaceEvent / EntityMultiPlaceEvent boundary rejection for owner-specific dimensions.
- Land level is read from that dimension's SavedData, never client-provided.
- Non-shelter worlds are not affected.
- **Not complete**: fluid movement, piston extension, explosion, player/entity movement, ender pearls, teleporters, one-slot GUI, and crash-safe upgrade rollback still need hooks.
- **Not compiled against NeoForge**; event signatures must be verified against the actual 1.21.1 dependencies before installation.
