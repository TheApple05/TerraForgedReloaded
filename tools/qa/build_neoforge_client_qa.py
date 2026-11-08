"""Build an explicit NeoForge adapter of the separate, real-screen client QA probe.

The Fabric source remains authoritative and unchanged. Generated Java, compiler
logs, adapter operations and both source hashes are retained under ignored build/.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tests/client-runtime/raccoonman/reterraforged/client/gui/screen/presetconfig/ClientQaMod.java"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def adapt(original: str) -> tuple[str, list[dict]]:
    text = original
    operations: list[dict] = []

    def replace(label: str, old: str, new: str, expected: int | None = 1) -> None:
        nonlocal text
        count = text.count(old)
        if not count or expected is not None and count != expected:
            raise ValueError(f"Adapter anchor changed for {label}: expected {expected}, found {count}")
        text = text.replace(old, new)
        operations.append({"operation": label, "occurrences": count})

    replace("Relocate QA to its own named-module package",
        "package raccoonman.reterraforged.client.gui.screen.presetconfig;",
        "package terraforged.qa.client;\n\n"
        "import raccoonman.reterraforged.client.gui.screen.presetconfig.PresetConfigScreen;\n"
        "import raccoonman.reterraforged.client.gui.screen.presetconfig.WorldSettingsPage;")
    replace("NeoForge mod annotation import", "import net.fabricmc.api.ClientModInitializer;",
        "import net.neoforged.fml.common.Mod;")
    replace("NeoForge client tick imports",
        "import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;",
        "import net.neoforged.neoforge.common.NeoForge;\n"
        "import net.neoforged.neoforge.client.event.ClientTickEvent;")
    replace("NeoForge provenance imports", "import net.fabricmc.loader.api.FabricLoader;",
        "import net.neoforged.fml.ModList;\nimport net.neoforged.fml.loading.FMLLoader;")
    replace("Remove Fabric-only implementation import",
        "import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;\n", "")
    replace("NeoForge test mod entrypoint", "public final class ClientQaMod implements ClientModInitializer {",
        '@Mod("terraforged_client_qa")\npublic final class ClientQaMod {')
    replace("NeoForge constructor initialization", "    @Override\n    public void onInitializeClient() {",
        "    public ClientQaMod() {")
    replace("Register actual NeoForge post-client-tick event",
        "ClientTickEvents.END_CLIENT_TICK.register(this::tick);",
        "NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));")
    replace("Remove obsolete NeoForge client limitation", '"NeoForge client", ', "")
    replace("Use NeoForge's actual preset editor registry",
        "PresetEditor.EDITORS.get(Optional.of(WorldPresets.NORMAL))",
        "net.neoforged.neoforge.client.PresetEditorManager.get(WorldPresets.NORMAL)")
    replace("Reflect package-private preset page type", "page instanceof PresetListPage",
        'page.getClass().getName().equals("raccoonman.reterraforged.client.gui.screen.presetconfig.PresetListPage")')
    replace("Use public widget bound rather than package-private PresetEntry",
        "WidgetList<PresetListPage.PresetEntry>",
        "WidgetList<net.minecraft.client.gui.components.AbstractWidget>", expected=2)
    replace("Reflect built-in entry property in selection callback", "entry.getWidget().isBuiltin()",
        "isBuiltinWidget(entry.getWidget())")
    replace("Invoke actual public button callback via its public base type",
        "first.getWidget().onPress(new MouseButtonInfo(0, 0));",
        "((net.minecraft.client.gui.components.AbstractButton) first.getWidget()).onPress(new MouseButtonInfo(0, 0));")
    replace("Reflect protected preset entry field", "editor.preset.getPreset()", "editorPreset()", expected=None)
    replace("Reflect protected built-in entry property", "editor.preset.isBuiltin()",
        'isBuiltinWidget(field(editor, "preset"))')
    replace("Remove Fabric process-global modifier baseline field",
        "    private int originalGlobalModifierCount;\n", "")
    replace("Explicitly label the inapplicable Fabric-only assertion",
        '                    originalGlobalModifierCount = globalModifierCount();\n'
        '                    report.put("globalModifierBaseline", originalGlobalModifierCount);',
        '                    report.put("fabricGlobalModifierIsolation", "not applicable on NeoForge; native registry-scoped modifiers and custom-to-vanilla context checks are used");')
    replace("Observe native per-world biome modifier registry size",
        "globalCount = globalModifierCount();", "globalCount = nativeModifierRegistrySize(server);")
    replace("Name the actual native registry observation", '"globalModifierCount", globalCount',
        '"nativeBiomeModifierRegistrySize", globalCount')
    replace("Remove the Fabric-only global-list equality assertion",
        '                    require(globalModifierCount() == originalGlobalModifierCount, "World-scoped biome modifiers leaked into Fabric\'s global list");\n', "")
    replace("Describe only retained actual runtime checks",
        "; global modifier count unchanged",
        "; native feature sorter identities and world context checked")
    replace("Inventory native modifier namespaces and assert only this preset's world-scoped entries",
        '    private int globalModifierCount() throws Exception {\n'
        '        return ((List<?>) field(BiomeModificationImpl.INSTANCE, "modifiers")).size();\n'
        '    }',
        '    private int nativeModifierRegistrySize(net.minecraft.server.MinecraftServer server) {\n'
        '        var registry = server.registryAccess().lookupOrThrow(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS);\n'
        '        Map<String, List<String>> namespaces = new java.util.TreeMap<>();\n'
        '        registry.keySet().stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id ->\n'
        '            namespaces.computeIfAbsent(id.getNamespace(), unused -> new ArrayList<>()).add(id.toString()));\n'
        '        List<String> own = namespaces.getOrDefault(RTFCommon.MOD_ID, List.of());\n'
        '        if (vanillaStage) require(own.isEmpty(), "Vanilla world retained TerraForged preset biome modifiers");\n'
        '        else require(!own.isEmpty(), "Custom world is missing its TerraForged biome modifiers");\n'
        '        report.put(vanillaStage ? "vanillaNativeBiomeModifierInventory" : "customNativeBiomeModifierInventory",\n'
        '            Map.of("total", registry.size(), "terraforgedEntries", own.size(), "entriesByNamespace", namespaces,\n'
        '                "otherNamespaceEntriesObserved", registry.size() - own.size(), "probeDoesNotModifyRegistry", true,\n'
        '                "assertion", vanillaStage ? "No reterraforged preset entries" : "TerraForged preset entries present"));\n'
        '        return registry.size();\n'
        '    }')

    start = text.index("    private Map<String, Object> provenance() throws Exception {")
    end = text.index("    private void stage(String name, String detail) {", start)
    text = text[:start] + '''    private Map<String, Object> provenance() throws Exception {
        FMLLoader loader = FMLLoader.getCurrent();
        require(loader.isProduction(), "Client QA requires the packaged production NeoForge launch");
        Path origin = Path.of(RTFCommon.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        require(Files.isRegularFile(origin) && origin.toString().endsWith(".jar"), "Production class did not come from a JAR");
        String sha = sha256(origin);
        String expected = System.getProperty("terraforged.clientqa.expectedModSha256", "");
        if (!expected.isBlank()) require(expected.equalsIgnoreCase(sha), "Unexpected production JAR hash");
        return Map.of("minecraft", SharedConstants.getCurrentVersion().id(), "java", System.getProperty("java.version"),
            "loaderName", "neoforge", "loader", ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString(),
            "modVersion", ModList.get().getModContainerById("reterraforged").orElseThrow().getModInfo().getVersion().toString(),
            "modClassOrigin", origin.toString(), "modSha256", sha, "developmentEnvironment", false);
    }

    private Preset editorPreset() throws Exception {
        return (Preset) invokeNoArgs(field(editor, "preset"), "getPreset");
    }

    private static boolean isBuiltinWidget(Object entry) {
        try { return (boolean) invokeNoArgs(entry, "isBuiltin"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot inspect actual built-in preset entry", failure); }
    }

''' + text[end:]
    operations.append({"operation": "Replace provenance and add narrow preset reflection helpers", "occurrences": 1})
    for forbidden in ["net.fabricmc", "FabricLoader", "BiomeModificationImpl", "PresetListPage.PresetEntry", "editor.preset"]:
        if forbidden in text:
            raise ValueError(f"Unadapted platform/package access remains: {forbidden}")
    return text, operations


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--production-jar", required=True, type=Path)
    parser.add_argument("--classpath-file", type=Path, default=ROOT / "neoforge/build/qa/target/compile-classpath.txt")
    parser.add_argument("--output", type=Path, default=ROOT / "build/qa/neoforge-client-runtime/terraforged-neoforge-client-qa.jar")
    args = parser.parse_args()
    production = args.production_jar.resolve()
    if not production.is_file():
        parser.error("Supply the immutable packaged NeoForge artifact")
    classpath = args.classpath_file.read_text(encoding="utf-8").strip()
    artifacts = [Path(value) for value in classpath.split(os.pathsep)]
    if not classpath or any(not artifact.is_file() for artifact in artifacts):
        parser.error("Resolved NeoForge classpath is absent/stale; refresh writePortClasspath when Gradle is idle")
    source_bytes = SOURCE.read_bytes()
    original = source_bytes.decode("utf-8").replace("\r\n", "\n")
    generated, operations = adapt(original)
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="compile-", dir=output.parent))
    java_source = work / "source/terraforged/qa/client/ClientQaMod.java"
    java_source.parent.mkdir(parents=True)
    java_source.write_text(generated, encoding="utf-8", newline="\n")
    (work / "original-ClientQaMod.java.txt").write_bytes(source_bytes)
    classes = work / "classes"
    classes.mkdir()
    javac = args.java_home.resolve() / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    result = subprocess.run([str(javac), "--release", "25", "-proc:none", "-encoding", "UTF-8",
        "-cp", str(production) + os.pathsep + classpath, "-d", str(classes), str(java_source)],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (work / "compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    report = {"schemaVersion": 1, "status": "compiled" if not result.returncode else "failed-compile",
        "compileExit": result.returncode, "output": str(output), "generatedSource": str(java_source),
        "sharedProbeSource": SOURCE.relative_to(ROOT).as_posix(),
        "sharedProbeSourceSha256Lf": digest(original.encode("utf-8")),
        "sharedProbeUnchangedDuringBuild": SOURCE.read_bytes() == source_bytes,
        "generatedAdapterSha256Lf": digest(generated.encode("utf-8")),
        "productionJar": str(production), "productionJarSha256": digest(production.read_bytes()),
        "adapterOperations": operations,
        "notEvidenceOf": ["Client launch", "Preset editor rendering", "Custom world rendering", "Visual parity", "NeoForge reflection access at runtime"]}
    if not report["sharedProbeUnchangedDuringBuild"]:
        raise RuntimeError("Shared source changed during adapter build; generated snapshot is retained, but no artifact is packaged")
    if result.returncode:
        (work / "build-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(result.stdout + result.stderr, file=sys.stderr)
        print(json.dumps(report, indent=2))
        return result.returncode
    metadata = '''license="MIT"
[[mods]]
modId="terraforged_client_qa"
version="0.1.0"
displayName="TerraForged NeoForge client QA companion"
description="Separate test-only real-screen client QA adapter."
[[dependencies.terraforged_client_qa]]
modId="reterraforged"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="CLIENT"
'''
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/neoforge.mods.toml", metadata)
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nAutomatic-Module-Name: terraforged.client.qa\nImplementation-Title: Test-only TerraForged NeoForge client QA\n\n")
        for file in sorted(classes.rglob("*.class")):
            archive.write(file, file.relative_to(classes).as_posix())
    report.update({"status": "built", "sha256": digest(output.read_bytes())})
    (output.parent / "build-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (work / "build-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"NeoForge client QA adapter error: {exc}", file=sys.stderr)
        raise SystemExit(2)
