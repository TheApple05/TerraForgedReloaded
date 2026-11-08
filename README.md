# TerraForged for Minecraft Java 26.2

An independent, unofficial revival of TerraForged for **Fabric and NeoForge**, focused on its terrain, rivers, customization, and use in modern modpacks. This repository contains the Minecraft Java 26.2 port; native Horizon 26.3 source, documentation and validation live in [TerraForged-Horizon](https://github.com/TheApple05/TerraForged-Horizon).

**Development status:** both loaders have passed real packaged client and dedicated-server workflows. The editor creates custom worlds, terrain renders, saves reload, and a normal world can load afterward without inheriting custom settings. The pinned Biomes O' Plenty, GlitchCore and TerraBlender stack also passes its recorded client/server checks. These are experimental candidates; no stable release or complete classic-parity claim is made.

The current download is **dev26**, with safer preset imports/saves, correct legacy-file paths, and preview cleanup after save failure. Both packaged loaders pass fresh real-editor create/copy/delete/import/navigation/Cancel/Done flows, actual custom-world creation/render/save followed by a normal world, packaged regressions, and dedicated-server boot/save/stop checks. [Download dev26](https://github.com/TheApple05/TerraForgedReloaded/releases/tag/v0.1.0-dev.26%2B26.2); [release notes and validation scope](docs/releases/dev26.md). Dev25 was rejected after both real clients reproduced a save-failure preview/cache leak; dev26 fixes it.

The earlier **dev23** restoration candidate completed the broader qualification below, which has not been repeated in full for dev26. All 17 classic variants pass actual generation, saved-holder checks and cold reload on both loaders. The base and active-shader clients, pinned optimizer/content/structure stacks, real client joins and Linux generation/reload also pass. [Complete scoped acceptance](docs/validation/dev23-acceptance.json) binds 104 evidence files and 30 native images to the exact JARs. Both controlled performance matrices pass all 24 fresh trials and eight cold reloads; [measured results](docs/validation/dev23-performance.md) retain every trial and matched control. [Download the previous dev23 prerelease](https://github.com/TheApple05/TerraForged-26.2/releases/tag/v0.1.0-dev.23%2B26.2). Both uploaded JARs were downloaded again and verified against their tested hashes.

![Actual dev23 restored Bryce pillars](docs/validation/images/client-fabric-dev23-pillar-scenes/08-bryce.png)

Actual Minecraft capture: Fabric dev23, seed `8675309`. [Scene evidence](docs/validation/client-fabric-dev23-pillar-scenes.json) records natural locations and verified render/camera readiness. [Visual review](docs/validation/visual-review-dev23.md) distinguishes visible pillars, nearby shader rendering and unfinished distant LOD quality from user visual acceptance.

## Use or build a candidate

Use Minecraft Java **26.2** and **Java 25**, with either Fabric Loader **0.20.1** plus Fabric API **0.161.0+26.2**, or NeoForge **26.2.0.93**. Install the matching loader's JAR. [Installation and world-creation instructions](docs/INSTALLATION.md) cover the editor, preset folders, servers and building from source.

The current package snapshots and SHA-256 values are in [the coherent dev26 build record](docs/validation/dual-loader-dev26-build.json). Dev23 has completed the recorded functional and controlled-performance qualification; dev11 remains historical evidence. Local Fabric JARs are in `build/libs`; NeoForge JARs are in `neoforge/build/libs`. GitHub's [packaging workflow](.github/workflows/package-candidates.yml) retains experimental artifacts. Only the exact hashes in runtime reports have their recorded game validation.

## Verification

The project tests the actual packaged JARs in disposable non-development installations. Recorded checks cover real preset export, active custom generators, editor/preview events, actual Create New World callbacks, feature-holder consistency, custom-to-vanilla world transitions, saves, reloads, and game-owned screenshots. Larger Fabric checks finish 144 full chunks and compare 576 sampled terrain fields/biomes across request orders. Core mathematical fixtures and cache regressions run separately on Linux and Windows; both loaders also package independently on Ubuntu CI.

[Runtime results](docs/validation/runtime-summary.md) distinguish passing checks, retained failures, and unverified scope. They include corrected cache deadlock, UI rendering/input, lazy-holder race, biome-feature lifecycle, and optional TerraBlender integration defects. Sampled terrain agreement does not establish complete block determinism: vanilla-generator controls also show decoration-order effects.

## Project record

- [Porting plan and completion criteria](docs/PORTING.md)
- [Development log](docs/DEVLOG.md)
- [Feature inventory and known classic gaps](docs/FEATURE-PARITY.md)
- [Regression and acceptance protocol](docs/VALIDATION.md)
- [Upstream audit](docs/research/upstream-audit.md)
- [Target toolchain research](docs/research/target-toolchain.md)
- [GitHub work tracker](https://github.com/TheApple05/TerraForged-26.2/issues)

## Original work and licensing

This is a **new repository, not a GitHub fork**, private during initial development. It is not an official TerraForged release.

- [TerraForged](https://github.com/TerraForged/TerraForged), the original mod and terrain engine.
- [ReTerraForged](https://github.com/racoonman2/ReTerraForged), the subsequent modernization effort.

The implementation baseline is ReTerraForged **1.20.1 / 0.0.6**, commit `29239f1f164d19dbfcccaca4a8277d64e784207c`. Original TerraForged **1.16.5 / 0.2.16**, commit `3ada751ac25cd3eecee5d198870a3abb89734f52`, is the classic reference. [The audit](docs/research/upstream-audit.md) explains why newer experimental/stripped branches were not substituted. The [classic restoration decision](docs/decisions/0016-classic-biome-restoration.md) distinguishes authenticated definitions/equations from the modern selector and geology adaptations. Other inherited gaps remain inventoried; a complete classic restoration is not implied by the modern port.

Imported code retains attribution and license notices. New history does not imply original authorship of upstream code. See [source provenance](docs/SOURCE-PROVENANCE.json) and [third-party notices](THIRD_PARTY_NOTICES.md). The root license is MIT, with the retained LGPL-2.1-only language-provider component and its license text included in both packages.
