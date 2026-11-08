# TerraForged Revival dev26 - Minecraft 26.2

Experimental Fabric and NeoForge builds with preset-file safety fixes and fresh real-editor validation on both loaders.

## Install

- Minecraft Java **26.2**, **Java 25**.
- **Fabric:** Fabric Loader **0.20.1**, Fabric API **0.161.0+26.2**, and the asset ending in `fabric-mc26.2.jar`.
- **NeoForge:** NeoForge **26.2.0.93** and the asset ending in `neoforge-mc26.2.jar`.

Install only the matching loader JAR. Create New World > World > Customize opens the preset editor. Use new test worlds; classic-world and cross-loader migration remain unverified.

## Fixes

- Malformed or invalid preset files no longer prevent valid and built-in presets from appearing. Non-JSON files are ignored and directory streams are closed.
- Imported presets retain their actual source paths for saving and deleting, including the legacy folder and uppercase JSON extensions.
- Saves finish encoding and temporary output before Minecraft's backup/restore replacement. Existing contents survive encoding/writer failures; long imported filenames work.
- Closing an editor now releases its preview and generation caches even when saving fails. Both actual dev25 clients reproduced the previous leak, so dev25 was rejected and never published.
- Terrain evidence comparisons reject incomplete/nonfinite field records and invalid validation metadata instead of falsely passing.

## Fresh validation and limits

Both exact dev26 packages are tested in disposable production clients. Actual callbacks cover malformed/legacy imports, create/copy/delete, invalid-name disablement, legacy edit/save/back/delete, all eight implemented settings pages forward/back, and injected save failure followed by Cancel. The failure preserves the file, returns to the parent and releases preview/cache ownership. The normal flow then exercises eight real slider changes with preview-digest restoration, Done and asynchronous datapack validation, actual custom-world creation/render/save, and a subsequent vanilla world without custom context/cache leakage. Native framebuffer captures are archived; agent visual smoke review is separate from user visual acceptance.

Both loader builds, core/cache regressions, packaged cache lifecycle checks, Fabric biome-policy/preset-file probes, pillar math and dedicated-server boot/save/stop checks pass. The attached reports and `BUILD.json` identify the exact hashes and scope. `SOURCE-COMMIT.txt` and `RELEASE-COMMIT.txt` identify revisions. Every uploaded asset is downloaded and SHA-256 verified before publication and again through its public URL afterward.

Dev23's full shader/modpack, all-biome, loopback/Linux and controlled-performance matrices are historical evidence for those exact older artifacts and are not repeated here. This release does not establish every control/numeric extreme, every historical schema, full classic visual parity, public multiplayer capacity, migration support, or native Horizon readiness.

## Build this version

Use Java 25 and `-Pmod_version=0.1.0-dev.26+26.2` with the documented loader build commands. This is the repository's default mod version; production packages exclude QA companions and third-party/game resources.
