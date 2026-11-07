package raccoonman.reterraforged.client.gui.screen.presetconfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;

record PresetFile(Path path, Preset preset) {
    static List<PresetFile> list(Path directory) {
        List<PresetFile> presets = new ArrayList<>();
        if (!Files.exists(directory)) return presets;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    var result = Preset.DIRECT_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                    if (result.error().isPresent()) {
                        RTFCommon.LOGGER.error("Unable to load preset {}: {}", path, result.error().get().message());
                    } else {
                        presets.add(new PresetFile(path, result.result().orElseThrow()));
                    }
                } catch (IOException | JsonParseException e) {
                    RTFCommon.LOGGER.error("Unable to load preset {}", path, e);
                }
            }
        } catch (IOException e) {
            RTFCommon.LOGGER.error("Unable to list presets in {}", directory, e);
        }
        return presets;
    }

    void save() throws IOException {
        JsonElement element = Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, this.preset).getOrThrow();
        Path output = this.path.toAbsolutePath();
        Path temporary = Files.createTempFile(output.getParent(), "preset-", ".tmp");
        try {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(temporary))) {
                writer.setSerializeNulls(false);
                writer.setIndent("  ");
                GsonHelper.writeValue(writer, element, null);
            }
            // Keep the previous preset recoverable if writing or replacement fails.
            if (!Util.safeReplaceOrMoveFile(output, temporary, output.resolveSibling(output.getFileName() + ".bak"), false)) {
                throw new IOException("Unable to replace preset " + output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
