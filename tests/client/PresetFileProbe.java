package raccoonman.reterraforged.client.gui.screen.presetconfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;

public final class PresetFileProbe {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static JsonElement encode(Preset preset) {
        return Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, preset).getOrThrow();
    }

    private static void unchanged(Path file, String expected, String message) throws IOException {
        check(Files.readString(file).equals(expected), message);
        try (var files = Files.list(file.getParent())) {
            check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "Failed save cleans temporary output");
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path root = Path.of(args[0]);
        Path production = Path.of(args[1]).toRealPath();
        for (Class<?> type : List.of(PresetFile.class, PresetListPage.class, Preset.class)) {
            check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(production),
                "Expected packaged class origin: " + type.getName());
        }
        check(SharedConstants.getCurrentVersion().id().equals("26.2"), "Minecraft 26.2 classes");
        Path imports = Files.createDirectory(root.resolve("imports"));
        List<Preset> builtins = List.of(Presets.makeLegacyDefault(), Presets.makeLegacyBeautiful(),
            Presets.makeLegacyHugeBiomes(), Presets.makeLegacyLite(), Presets.makeLegacyVanillaish());
        for (int i = 0; i < builtins.size(); i++) {
            Path file = imports.resolve("preset-" + i + ".json");
            new PresetFile(file, builtins.get(i)).save();
        }
        Files.writeString(imports.resolve("00-broken.json"), "{ invalid");
        Files.writeString(imports.resolve("01-invalid.json"), "{}");
        Files.writeString(imports.resolve("02-empty.json"), "");
        Files.writeString(imports.resolve("03-scalar.json"), "123");
        Files.writeString(imports.resolve("ignored.txt"), "{ invalid");
        Files.createDirectory(imports.resolve("directory.json"));
        List<PresetFile> loaded = PresetFile.list(imports);
        check(loaded.size() == builtins.size(), "Malformed files and unrelated files do not hide valid imports");
        for (int i = 0; i < builtins.size(); i++) {
            check(loaded.get(i).path().equals(imports.resolve("preset-" + i + ".json")), "Imported path retained");
            check(encode(loaded.get(i).preset()).equals(encode(builtins.get(i))), "Complete built-in codec round trip " + i);
        }
        JsonObject legacy = encode(builtins.getFirst()).getAsJsonObject();
        legacy.remove("caves");
        Path legacyFile = imports.resolve("legacy.JSON");
        Files.writeString(legacyFile, legacy.toString());
        loaded = PresetFile.list(imports);
        check(loaded.size() == builtins.size() + 1, "Uppercase JSON suffix retained");
        PresetFile legacyPreset = loaded.stream().filter(file -> file.path().equals(legacyFile)).findFirst().orElseThrow();
        check(legacyPreset.preset().caves() != null, "Legacy omitted caves receive codec defaults");
        check(PresetFile.list(root.resolve("absent")).isEmpty(), "Absent legacy folder is optional");
        check(PresetFile.list(legacyFile).isEmpty(), "Invalid directory path does not abort built-ins/other folders");

        Path config = Files.createDirectory(root.resolve("config"));
        Field configDir = FabricLoaderImpl.class.getDeclaredField("configDir");
        configDir.setAccessible(true);
        configDir.set(FabricLoaderImpl.INSTANCE, config);
        PresetListPage page = new PresetListPage(null);
        Method list = PresetListPage.class.getDeclaredMethod("listPresets", Path.class);
        list.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<PresetListPage.PresetEntry> entries = (List<PresetListPage.PresetEntry>) list.invoke(page, imports);
        PresetListPage.PresetEntry imported = entries.stream().filter(entry -> entry.getName().getString().equals("legacy")).findFirst().orElseThrow();
        check(imported.getPath().equals(legacyFile), "UI entry save/delete targets its actual imported file");
        imported.getPreset().terrain().general.globalVerticalScale = .75F;
        imported.save();
        check(Files.exists(legacyFile.resolveSibling("legacy.JSON.bak")), "Safe replacement retains previous file backup");
        check(Files.readString(legacyFile.resolveSibling("legacy.JSON.bak")).equals(legacy.toString()), "Backup contains exact original bytes");
        check(!Files.exists(config.resolve("reterraforged/presets/legacy.json")), "Editing a legacy file creates no modern-folder shadow");
        Preset saved = PresetFile.list(imports).stream().filter(file -> file.path().equals(legacyFile)).findFirst().orElseThrow().preset();
        check(saved.terrain().general.globalVerticalScale == .75F, "Legacy changes persisted to original path");
        String before = Files.readString(legacyFile);
        try {
            new PresetFile(legacyFile, new Preset(null, null, null, null, null, null, null, null)).save();
            throw new AssertionError("Broken model must fail encoding");
        } catch (RuntimeException expected) {
            unchanged(legacyFile, before, "Codec failure preserves original preset");
        }
        Preset invalidNumber = Presets.makeLegacyDefault();
        invalidNumber.terrain().general.globalVerticalScale = Float.NaN;
        try {
            new PresetFile(legacyFile, invalidNumber).save();
            throw new AssertionError("Nonfinite JSON number must fail saving");
        } catch (IllegalArgumentException expected) {
            unchanged(legacyFile, before, "JSON writer failure preserves original preset");
        }
        Files.delete(imported.getPath());
        check(!Files.exists(legacyFile), "Delete uses original imported path");
        check(Files.exists(imports.resolve("preset-0.json")), "Delete preserves other presets");
        Path longName = imports.resolve("x".repeat(240) + ".json");
        PresetFile longPreset = new PresetFile(longName, builtins.getFirst());
        longPreset.save();
        longPreset.save();
        check(Files.exists(longName), "Valid long imported filenames can be saved and replaced");
        Bootstrap.realStdoutPrintln("PRESET_FILES_QA_JSON=" + new Gson().toJson(Map.of(
            "status", "pass", "assertions", assertions, "minecraft", SharedConstants.getCurrentVersion().id(),
            "scope", "Actual packaged preset codec, file IO and headless list entry save/delete; no rendered client",
            "scenarios", List.of("all_five_builtins_roundtrip", "malformed_and_invalid_import_isolation", "optional_legacy_caves",
                "legacy_source_path_save_delete", "safe_replacement_backup", "encode_failure_preserves_original", "writer_failure_cleans_temp", "long_filename"))));
        Bootstrap.shutdownStdout();
    }
}
