# Better Settlement Roads

## Native fixture verification

Each Gradle invocation selects a fresh nonshipping `build/gametest/<uuid>` directory in the actual
ForgeGradle GameTest RunConfig; structure copies and native log readers use that same directory.
Ordinary developer `run`, visual/data roots and prior fixtures are never reset, relocated or merged.
`verifyFast` covers isolated-directory rejection, absent-only settings preparation, existing-settings
preservation and synthetic ERROR/FATAL rejection. `verifyFull` prepares defaults-only
`server.properties` when absent and rejects ERROR/FATAL in both actual native latest/debug logs,
rather than relying on test counts. Retain complete build trees/evidence across handoffs.

## Playtest logging

`bc.playtest.better_settlement_roads.network` observes rebuild/clear outcomes from already
computed plans; `.placement` separates segment markers from actual block/wall write counts.
A marked segment with zero writes is not reported as every block placed or automatic repair.
Rebuild, placement and clear carry native dimension/game tick: the existing network is one saved
state per dimension, with no native revision ID to invent. No diagnostic counter/ledger is added.
No additional scans, chunk loads or per-block stream is introduced. DEBUG/TRACE share 120/minute
(burst 20), 256 keys and 2,048-character records, with 30-second summaries. Startup INFO reports
loaded version/mode/limits; unavailable source identity is `UNKNOWN`. Stop/disconnect clears
transient keys without rearming process allowances. Pure emitter/contracts supplement native
placement/idempotence tests, not representative natural-world or visual acceptance.

## Scope

Deterministic, scenario-driven settlement road planning for Forge 1.20.1.

This repository contains the mod implementation and its execution docs:

- [SPEC.md](SPEC.md)
- [TESTPLAN.md](TESTPLAN.md)
- [docs/DEBUG_SCENARIOS.md](docs/DEBUG_SCENARIOS.md)
- [docs/VISUAL_CHECKLIST.md](docs/VISUAL_CHECKLIST.md)
- [CHANGELOG.md](CHANGELOG.md)

The current design bias is:

- deterministic synthetic scenes over natural worldgen
- world-state assertions over screenshots
- planning separated from placement
- persistence via Overworld `SavedData`
- data-driven compatibility via tags and config

Current implementation slice:

- mod metadata, code namespace, and resources now consistently use `better_settlement_roads`
- legacy `fissionreactor` sources, assets, tests, and dependency baggage have been removed
- core planner/domain/storage scaffolding exists in `src/main/kotlin`
- deterministic synthetic scenarios now drive both debug commands and GameTests
- supported route planning avoids water with a dry detour when one exists and otherwise omits the connection
- placement renders three-wide dirt paths in grassy biomes, gravel roads in non-grassy biomes, and sparse coarse-dirt edges
- bridge planning and placement code remains dormant experimental scaffolding; `allow_water_bridges` defaults to false and must remain false for supported use
- Production roads build once per stable segment ID. A rebuild retains completed IDs even when source structures leave the observed area; a later edit or missing road is not repaired. Placement replaces only a narrow natural-ground list, skips block entities and unloaded footprint chunks, and checks the observed state again before writing. This protects ordinary construction but cannot distinguish player-placed dirt from generated dirt. Existing persisted `test_landmark` nodes are retained for operator review but do not enter production planning; ordinary magenta concrete no longer creates new landmarks. No automatic deletion of old debug nodes is performed.
- deterministic JVM tests cover clustering, rings, dry-route selection, palette choice, chunk indexing, and save-data round trips; isolated bridge tests deliberately enable the unsupported flag
- in-world GameTests retain isolated bridge-placement scaffolding alongside supported dry routing and rerun-idempotence coverage; they do not establish working bridge support
- debug commands register `/better_settlement_roads debug spawn_scenario`, `plan_here`, `place_here`, and `clear_here`

Immediate implementation entry points:

- `./gradlew verifyFast`
- `./gradlew verifyFull`
- `./gradlew runData`

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and release artifact: `better-settlement-roads`
- Mod ID and resource namespace: `better_settlement_roads`
- Java package: `com.bettercontent.bettersettlementroads`
- Validation: `./gradlew verifyFull`

This normalization is a clean break. Worlds, configuration files, and integrations created for earlier identities are not migrated or aliased.

### Coverage gate regression

`./gradle/verify-coverage-gate.sh` checks the production coverage gate, then confirms
that an empty report scope and an unmet 100% coverage requirement fail. It restores
the normal report afterward and retains diagnostic logs under
`build/coverage-gate-regression/`. The production thresholds are unchanged.
