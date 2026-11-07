# Development log

Dates use America/Los_Angeles. This log records outcomes and evidence, not an assertion that the entire port works.

## 2026-11-03 — project initiation

- User authorized implementation, an independent GitHub repository, and ongoing documentation.
- Workspace was empty. Created a fresh Git history and private repository `TheApple05/TerraForged-26.2`; GitHub's creation response reports `fork: false`.
- Verified Mojang's version manifest lists `26.2` as a release and does not list `1.26.2`. User explicitly confirmed the 26.2 target.
- Cloned both upstream repositories under ignored `.reference/` for branch and history inspection.
- Started independent upstream/feature and target/toolchain audits.
- Original default branch README warns that it is stripped back. Early inspection found experimental terrain overrides in ReTerraForged's 1.20.2 branch; choosing a baseline requires source inspection rather than taking the newest branch automatically.
- Used the authorized DeepSeek Delegator for a bounded regression-checklist draft. Codex reviewed and adapted that draft into the validation protocol. Worker output is not evidence of passing tests.

### Baseline selection and import

- Selected ReTerraForged 1.20.1 / 0.0.6 at `29239f1f164d19dbfcccaca4a8277d64e784207c` after inspecting all branches. Imported the complete tree except upstream README and ignore rules in commit `b593342`.
- Original TerraForged 0.2.x at `3ada751ac25cd3eecee5d198870a3abb89734f52` declares 1.16.5 / 0.2.16 and serves as the classic reference. Its Engine dependency must be pinned when constructing the original runtime reference.
- Rejected the 1.20.2 plateau override and mostly disabled preset patching as the preservation baseline. The newer 1.21.10 `datagen-rework` omits important hydrology/configuration paths and changes licensing; no code from that branch was imported.
- Preserved original/revival MIT notices and the file-level LGPL-2.1-only LanguageProvider notice, with component license text and source provenance.
- Created GitHub issues [#1](https://github.com/TheApple05/TerraForged-26.2/issues/1) through [#4](https://github.com/TheApple05/TerraForged-26.2/issues/4) for reference parity, 26.2 generation migration, customization, and release validation.
- Chose Fabric as the first integration target using Java 25. Forge source remains in history/tree as a reference and is not advertised as supported on 26.2.

### First algorithm characterization

- Implemented dependency-free compilation of four unchanged production utility classes, with frozen reference outputs independently extracted from the pinned upstream commit.
- Passed 2,167 records in four separate Java 25 processes (serial twice, shuffled, four-worker parallel); the integrator reran verification successfully. Fixture SHA-256: `8070efecb62e736c3d22c64e8e22922ac468c0350804d3f8e0bfe615f3d35dc5`.
- Verified that a hash-kernel mutation in a temporary copy is rejected. No production algorithm change was made.
- Recorded inherited `NoiseUtil.floor(-1.0f) == -2` semantics rather than silently changing deterministic generation.
- Added explicitly scoped Linux/Windows CI for these kernel checks. CI outcome is separate from the verified local result.

Next: finish the full-source target compilation diagnostic, then migrate actual 26.2 API adapters using that evidence.

### Full source migration

- Pinned and resolved Java 25, Gradle 9.5.1, Fabric Loom 1.17.21, Loader 0.20.1, Fabric API 0.161.0+26.2 and target compile-only dependencies. Regenerated the wrapper and checked its published checksum.
- Recorded successive coherent full-source inventories: 432 errors in 388 sources initially, then 251 errors, then 8 errors after component integration, followed by **zero errors in all 393 active source files**. Error counts are compiler-pass inventories, not feature-parity percentages. All source roots remain included.
- Implemented explicit Fabric platform bindings, resource/bootstrap migration, map codec dispatch, density child traversal, registry cloning/export context, dimensions, vegetation/template/provider APIs, client extraction rendering and preview texture lifetime, and current chunk lifecycle hooks.
- Enabled a new reviewed target access widener; current non-obfuscated Loom requires the `official` namespace. The legacy access widener remains reference-only. Target sources were generated into ignored caches for signature review.
- The density adapters pass 235 assertions using actual 26.2 classes; the integrator independently reran them successfully. Observed inherited serialization defects are documented separately from arithmetic preservation.
- The migrated legacy random patch feature matches actual Minecraft 1.20.1 execution in 216 cases, including 6,948 nested placements, success flags, and final RNG state. Nine dimension-height configurations encode correctly; default complete dimension data matches actual 26.2 vanilla data.
- Adapted tree-soil eligibility explicitly because the modern vanilla dirt tag narrowed. Restored per-biome decorator identifier lookup and invalidated both feature caches after biome modifications.
- Migrated all editor pages and live-preview source APIs. Actual UI appearance, controls, texture updates, world creation and dedicated-server behavior remain runtime gates.
- Experimental packaging includes component license notices and requires `-PexperimentalPackage` for isolated validation. No release is being announced.

Next: audit effective runtime injections, boot the exact packaged artifact in a disposable production server, exercise real preset export/custom generation and save/reload, and inspect the client visually.

### Packaged generation and runtime corrections

- User clarified that practical 26.2 modpack use is the priority, with both Fabric and NeoForge required. Started a loader-neutral service boundary and an independent NeoForge module; neither a compile nor Fabric runtime result will be labeled as NeoForge validation.
- Reviewed effective 26.2 constructor, chunk-stage, spawn, reload, and optional TerraBlender injection signatures. Added scoped spawn-search context cleanup and 36 assertions, retaining the inherited unsupported islands preference in the feature inventory.
- Installed official game/loader dependencies into disposable production QA instances and verified downloaded hashes. The actual Fabric package boots and exports a custom preset datapack using loaded server registries.
- The first custom generation attempt hit the watchdog. Preserved the failure and identified a production cache self-deadlock caused by ForkJoin cooperative waiting while holding a non-reentrant lock. Corrected `CacheEntry` publication; 312 assertions reproduce the original failure and validate shared future reads, concurrency, failure propagation, and closing.
- Fixed a real slider event regression introduced by the API migration. Retained the failing report and passed 29 actual event assertions after correction.
- Packaged corrected dev.2 (`94471bb542a308982bfb29c7340ed14dbf3494922cfd9f935c5f32b0485e80bd`). Two fresh custom-world generation runs and one save/reload run pass under the normal watchdog, with clean saves and process exit. Fresh runs differ at one tree-canopy column on a chunk boundary; terrain cell fields match. Broader checks are investigating decoration completion rather than treating the aggregate digests as equal.
- Production client review was deliberately aborted after two desktop capture failures. The client had reached its native window; no visual acceptance is claimed. Added a separate actual-game screenshot companion for continued review.
- Recorded the 17 classic custom biome IDs absent from the selected revival baseline, distinguishing inherited gaps from current port failures. See [feature inventory](FEATURE-PARITY.md) and [runtime summary](validation/runtime-summary.md).

## 2026-11-04 — both loaders and real client workflows

- Compiled and packaged complete common sources on Fabric and NeoForge. NeoForge uses native registry events, datapack codecs, biome modifiers, and editor registration through a single loader-owned service provider. Its first exact production package booted, saved, and stopped cleanly. Custom runtime checks continue separately.
- Expanded Fabric generation to 144 full chunks and 576 sampled columns across positive/negative tile boundaries. Raw terrain fields/classifications and biomes match across opposite request orders, and both worlds retain exact recorded digests after reload. Post-feature block differences include trees, coral, dripstone, and intersecting ores/stone variants. An explicit vanilla-generator control shows similar order effects; this does not establish every difference's cause or full block determinism.
- Game-owned screenshots found a real preset-screen double-blur crash after desktop capture was unavailable. Removed the redundant background extraction. The actual preview renders, and eight real slider regenerations return to the initial height digest after resolving lazy noise holders serially before parallel work.
- Generated all 277 English translations using the actual production provider and added a reproducible generation/check tool. Fixed float narrowing that displayed continent scale 3000 as 2999; 30 slider assertions now pass.
- Real integrated world loading exposed stale feature sorting. Independent inspection found direct Fabric feature mutations bypassed its native rebuild flag, plus registry-bound modifiers appended to a global list across worlds. Corrections and client validation are in progress.
- The first optional TerraBlender test exposed an actual Mixin priority rejection, retained in its runtime report. Corrected the reviewed priority and scheduled a new exact-package check.
- Pushed implementation history and evidence to the independent GitHub repository and updated the generation/release issues. No release is announced while these gates remain open.

### Representative modpack stack and final dev11 candidate

- Downloaded exact 26.2 Fabric/NeoForge Biomes O' Plenty 26.2.0.0.28 beta and GlitchCore 26.2.0.0.0 beta files from their official published versions, verified published SHA1/SHA512 and recorded SHA-256. Actual JAR metadata additionally requires TerraBlender even where marketplace dependency lists omit it.
- Full-stack export exposed a real upstream TerraBlender 26.2.0.0.2 registration mismatch: `NamespacedSurfaceRuleSource.CODEC.codec()` was registered where 26.2 expects its MapCodec identity. Preserved both loader failures. A first synthetic-method wildcard correction failed its strict injection requirement; exact Sponge selector inspection explained why. The successful correction targets the original public Registry registration overload with exact registry/identifier/type guards, retaining the original surface graph. See decisions 0013/0014.
- Dev10 complete-stack exports wrote 2,745 Fabric / 2,749 NeoForge files, retaining all 68 registered BOP biome entries and 59 overworld-source members. Both loaders generated actual BOP prairie/overgrown-greens samples and saved/reloaded with matching per-world digests. Every consumed pack file hash is checked; cross-loader surface/biome differences are retained rather than normalized away.
- Completed actual base and content-stack clients on both loaders: four measured label/hint widths, eight preview callbacks and restored height digest, real Done/export/reload, actual Create New World seed 123456789, rendering/saving, then vanilla seed 8675309 in the same process. Base feature identity checks cover 2,702/2,587 references; content stacks cover 5,450/5,217 across 114 possible biomes. Native checks preserve other mods' modifier namespaces.
- A stricter immediate-Done probe reproduced a normal interactive race in dev10: Create World became visible before the applied preset existed. Guarded editor close so vanilla retains its loading/error/confirmation screen. Both exact dev11 loader clients now observe GenericMessageScreen while pending, then actual readiness and successful world creation; both content-stack flows also pass. Only this client screen class differs between dev10/dev11; all backend class bytes match.
- Exact dev11 content-stack dedicated generation/reload passes on both loaders under the normal watchdog, using verified same-loader qualified exports from dev10. The new production artifact hashes, class origins, exit codes, biome observations, serializer identity and persisted digests are checked. See [final acceptance](validation/dev11-acceptance.json).
- Bounded scene searches sampled 256 direct heightmap points before generating selected actual areas. Confirmed filtered mountain, river and coast classifications and verified rendered camera poses. Mountain/river captures are useful; clear shoreline framing remains unverified. QA camera/focus/menu-overlay limitations remain explicit, including particular Neo captures that do not support a surface-appearance verdict.
- Kept failed runtime attempts, expected baseline failures, QA driver mistakes and corrected runs separate. Captured validation records now preserve exact bytes through Git so report/image hashes are not changed by line-ending conversion. Reproducible QA companions and launchers remain separate from the production packages.
- Added independent Ubuntu packaging for both loaders and Linux/Windows kernel/cache CI; successful runs are recorded separately from local game runtime. No existing accounts, launcher profiles, user worlds, global Java or machine settings were changed.
- The result is a working experimental modern port in the recorded scope. Stable-release gates remain open for complete classic features, every preset/control extreme, reference visual/user acceptance, multiplayer, additional mod combinations, Linux game runtime and measured performance budgets.

### Seventeen restored variants and dev23 modpack qualification

- Restored every classic biome definition with explicit original parents/tags, independent settings and a modern regional selector. Fixed registered-holder feature cycles, selected-dimension reloads, actual grass footing tags, filtered cold-biome queries and stronghold ring-search cost. Both exact loader corpora now generate/save/cold-reload all17 actual variants and positive Bryce/stone pillars. Decisions0016-0022 retain the rejected candidates and counterexamples.
- Corrected NeoForge exports to derive original parent biome data, preventing native modifiers from being baked and applied twice. The formerly failing combined BOP/Repurposed Structures/optimizer case now passes; decision0023 records the API boundary.
- Passed both real base and active-shader clients, all three pinned compatibility cases per loader, actual combined-stack client/dedicated joins, Linux generation/reload, cache ownership/scope/reentry checks and native structure-column comparisons. Archived30 byte-exact captures and retained incomplete far-LOD visual acceptance as an explicit limitation.
- Passed both controlled matrices:24 fresh trials plus eight cold reloads, three trials per generator/loader/stack, no compiler interference, normal watchdog and no budget violation. Exact dev23 acceptance binds104 evidence files and30 images. The tested JAR bytes are assembled together for one prerelease with requirements/checksums; no QA or third-party binaries are redistributed.
- Kept the Horizon 26.3 prototype separate from the Java 26.2 release. Its historical milestones remain in the [pre-split log](https://github.com/TheApple05/TerraForged-26.2/blob/c7a477a4a272ef34df9d3f55a95f185f264b40ec/docs/DEVLOG.md); ongoing native development is maintained in [TerraForged-Horizon](https://github.com/TheApple05/TerraForged-Horizon).

### Native port repository split

The native Horizon 26.3 source, documentation and evidence are maintained in [TerraForged-Horizon](https://github.com/TheApple05/TerraForged-Horizon). The [pre-split development log](https://github.com/TheApple05/TerraForged-26.2/blob/c7a477a4a272ef34df9d3f55a95f185f264b40ec/docs/DEVLOG.md) preserves the historical native numerical milestones. Java production source and published release artifacts are unchanged by this repository split.

### 2026-11-07 issue follow-through

- Reconciled open trackers #1, #3, #4 and #29 against their owning Java/native sources. Rechecked the archived dev23 acceptance hashes for 104 reports, 30 images and both production JARs; this verifies retained evidence integrity, not a fresh game run. Corrected stale feature-inventory statements about the completed dev23 compatibility/performance matrices.
- Reproduced malformed-preset list failure, lost legacy-file identity and file truncation on failed encoding against the existing dev24 package in disposable files. Preset scans now isolate bad files, close directory streams and ignore non-JSON files; imported entries retain their actual paths. Saves encode and finish a temporary file before using Minecraft's backup/restore replacement helper.
- Hardened terrain comparisons to require complete finite raw field data, valid coordinates/classifications/bounds and an actual boolean validation flag. Negative tests reject formerly false-passing empty records while preserving signed-zero comparison and separately reported post-feature differences. Full original-pipeline/visual parity, exhaustive controls/imports and migration remain open; native completion belongs to the separate repository.
- Validation: both loader packages compile; the exact Fabric candidate passes 31 real-codec/file/entry assertions with unchanged JAR identity. Five comparator tests, 2,167 core records across four JVMs and 312 cache assertions pass. CI runs the packaged preset probe and comparator regressions. This change has no fresh rendered-client or world-generation acceptance.
