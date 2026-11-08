# Install the development candidate

Use **Minecraft Java 26.2** and **Java 25**. Current candidate: **0.1.0-dev.26+26.2**. Both dev26 packages passed fresh real-editor/custom-world client flows, dedicated-server loading and the [recorded regression checks](releases/dev26.md). Broader shader/modpack and controlled-performance matrices were last run on dev23. It is intended for new test worlds and modpack evaluation. Historical compatibility results are in [dev23 readiness](validation/dev23-readiness.md); [controlled performance qualification](validation/dev23-performance.md) passes on the recorded local workloads.

[Download dev26 for both loaders](https://github.com/TheApple05/TerraForgedReloaded/releases/tag/v0.1.0-dev.26%2B26.2). Release asset filenames explicitly identify the loader and Minecraft version; their bytes match the build record.

## Choose the loader

- Fabric Loader **0.20.1**, Fabric API **0.161.0+26.2**, and `terraforged-revival-0.1.0-dev.26-fabric-mc26.2.jar`.
- NeoForge **26.2.0.93** and `terraforged-revival-0.1.0-dev.26-neoforge-mc26.2.jar`. Fabric API is not needed here.

Put only the matching production JAR in the instance's `mods` directory. Do not distribute QA companions, downloaded compatibility mods, developer fixtures or both loader builds together. The historical `forge/` directory is reference source.

[The coherent package record](validation/dual-loader-dev26-build.json) contains the exact SHA-256 values and build inputs. Both packages retain upstream licenses and notices.

## Create a world

1. Open **Singleplayer > Create New World > World**.
2. Keep the default world type and select **Customize**, opening **Presets & Defaults**.
3. Select **Default**, then **>>** to edit it. The editor includes world, caves, climate, terrain, rivers, filters, structures and miscellaneous settings.
4. Set the seed and settings, then **Done**. Wait for Minecraft's preset validation to finish.
5. Create the world and accept the settings confirmation if shown.

**Classic Biome Variants** independently controls the 17 restored variants. New built-in presets enable it. Older serialized presets lacking the field keep it disabled; enable it explicitly when desired. This is independent of custom biome vegetation. Modern cave biomes and third-party biome ownership are preserved by the selector.

Presets are saved under `config/reterraforged/presets`. **Preset Folder** opens that directory. **Export Pack** creates a datapack ZIP under `config/reterraforged/exports`; **Export Folder** opens it. Preview maps are diagnostics; actual terrain captures are the visual reference.

## Servers and modpacks

Create a new world with a matching-loader client, close it, and copy the complete world folder to a stopped matching-loader server. Set `level-name` to that folder and retain its datapacks and matching dependencies. Use an export made with the intended content stack so its biomes, modifiers and surface rules are represented.

Dev26 dedicated-server checks cover boot, save and clean stop. The broader dev23 dedicated tests cover fresh custom generation and cold reload. Dev23 loopback clients also join the combined BOP/Repurposed Structures/optimization stack on both loaders, synchronize all 17 classic definitions and receive terrain. Those historical joins use one disposable offline QA identity, not public authentication or a multiplayer load test.

Normal worlds remain available without a TerraForged preset. Existing chunks are not regenerated when settings change. Classic-world import and cross-loader world/datapack migration are unverified.

The [pinned compatibility inventory](validation/compatibility-26.2-artifacts.json) and [BOP inventory](validation/modpack-biomesoplenty-26.2-artifacts.json) record exact versions and hashes. Tested stacks include C2ME, Lithium, FerriteCore, BOP, GlitchCore, TerraBlender and Repurposed Structures. C2ME builds are alpha; BOP/GlitchCore builds are beta. ModernFix-mVUS is a separately tested **community port**, not an upstream 26.2 ModernFix release.

Dev23 Sodium/Iris/Distant Horizons with an active Complementary Reimagined shader passed the recorded nearby-render/client flow. Distant LODs were incomplete in brief captures; artifact-free far-distance rendering and FPS are not accepted. See [visual review](validation/visual-review-dev23.md). Each additional pack combination needs its own new-world check.

## Performance settings

`config/reterraforged/performance_internal.conf` is a UTF-8 properties file. It is created when absent. Defaults are `tileSize=3`, `batchCount=6`, and a worker count based on the JVM-visible CPU count. Restart after editing.

Bounds are tile size 1..5, batch count 1..20 and worker count 1..twice the JVM-visible processors, capped at 256. Larger tiles increase memory quadratically. Invalid or unknown values are logged and retain the original file while safe defaults are used. Use the defaults for initial pack checks; measured local budgets are not minimum-hardware promises.

## Build and reproduce

Use a full Java 25 JDK:

```powershell
& .\tools\gradle.ps1 -JavaHome <JDK25> -GradleArgs @('jar','-PexperimentalPackage','-Pmod_version=0.1.0-dev.26+26.2','--console=plain')
& .\tools\gradle.ps1 -JavaHome <JDK25> -GradleArgs @('--project-dir=neoforge','jar','-PexperimentalPackage','-Pmod_version=0.1.0-dev.26+26.2','--console=plain')
```

Fabric outputs to `build/libs`; NeoForge to `neoforge/build/libs`. `tools/qa/package_candidates.py` creates immutable versioned candidates with source snapshots and SHA-256 records. The hosted packaging workflow checks both loaders; production game QA remains separate.

The English provider produces 296 entries. Run `python tools/generate_language.py --java-home <JDK25>` to check the generated asset after a build, adding `--write` only for an intended update.

Report reproducible failures in the [issue tracker](https://github.com/TheApple05/TerraForged-26.2/issues), including loader/mod versions, seed, preset, logs and crash report. The [feature inventory](FEATURE-PARITY.md) retains unsupported classic details, island spawning, broad preset extremes and migration gaps.
