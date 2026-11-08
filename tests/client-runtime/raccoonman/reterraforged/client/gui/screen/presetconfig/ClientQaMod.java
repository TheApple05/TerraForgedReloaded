package raccoonman.reterraforged.client.gui.screen.presetconfig;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.client.data.RTFTranslationKeys;
import raccoonman.reterraforged.client.gui.widget.Slider;
import raccoonman.reterraforged.client.gui.widget.WidgetList;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.platform.ConfigUtil;
import raccoonman.reterraforged.registries.RTFRegistries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Separate client-only test mod: all captures come from Minecraft's real framebuffer. */
public final class ClientQaMod implements ClientModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();
    private static final long SEED = 123456789L;
    private static final int SETTLE_TICKS = 30;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> stages = new ArrayList<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private String phase = "title";
    private int ticks;
    private boolean busy, finished;
    private long started = System.nanoTime();
    private Path output;
    private CreateWorldScreen parent;
    private PresetConfigScreen config;
    private WorldSettingsPage editor;
    private CompletableFuture<Map<String, Object>> cameraSetup;
    private final String qaWorld = System.getProperty("terraforged.clientqa.world", "");
    private final List<Map<String, Object>> sliderChanges = new ArrayList<>();
    private String initialHeightDigest;
    private BackupConfirmScreen handledBackup;
    private final String vanillaWorld = System.getProperty("terraforged.clientqa.vanillaWorld", "");
    private boolean vanillaStage;
    private int originalGlobalModifierCount;
    private final boolean createFromUi = Boolean.getBoolean("terraforged.clientqa.createFromUi");
    private Screen handledCreationWarning;
    private String createdWorldFolder;
    private int cacheBaseline, previewCacheCount;
    private final List<Integer> previewCacheCounts = new ArrayList<>();
    private final String loopbackAddress = System.getProperty("terraforged.clientqa.loopbackAddress", "");
    private static final String CREATED_PRESET = "QA Created Preset";
    private static final String LEGACY_PRESET = "QA Legacy Import";
    private static final List<String> EDITOR_PAGES = List.of("WorldSettingsPage", "CaveSettingsPage", "ClimateSettingsPage",
        "TerrainSettingsPage", "RiverSettingsPage", "FilterSettingsPage", "StructureSettingsPage", "MiscellaneousPage");
    private Path malformedPreset, invalidPreset, legacyPreset, failurePreset;
    private String failurePresetOriginal;
    private boolean regressionComplete, walkingPages, walkingBack;
    private int walkIndex, walkthroughCacheCount;
    private final List<Map<String, Object>> editorNavigation = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        output = Path.of(System.getProperty("terraforged.clientqa.output", "qa-output")).toAbsolutePath().normalize();
        report.put("schemaVersion", 1);
        report.put("status", "failed");
        report.put("startedAt", Instant.now().toString());
        report.put("stages", stages);
        report.put("screenshots", screenshots);
        report.put("preset", "Legacy default, selected through the actual preset-list callback");
        report.put("previewSeed", SEED);
        report.put("sliderChanges", sliderChanges);
        report.put("notTested", List.of("Every control/numeric extreme", "All historical preset schemas", "Other mod combinations", "NeoForge client", "Visual acceptance by user"));
        report.put("editorNavigation", editorNavigation);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (finished || busy) return;
        try {
            if (System.nanoTime() - started > 420_000_000_000L) throw new IllegalStateException("Client QA exceeded its 420-second bound at " + phase);
            if (client.gui.overlay() != null) return;
            switch (phase) {
                case "title" -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) { ticks = 0; return; }
                    if (++ticks < SETTLE_TICKS) return;
                    Files.createDirectories(output);
                    report.put("runtime", provenance());
                    cacheBaseline = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    report.put("cacheBaseline", cacheBaseline);
                    originalGlobalModifierCount = globalModifierCount();
                    report.put("globalModifierBaseline", originalGlobalModifierCount);
                    stage("title-ready", "Real title screen and production artifact verified");
                    capture(client, "01-title.png", !loopbackAddress.isBlank() ? "connect-loopback" : Boolean.getBoolean("terraforged.clientqa.worldOnly") ? "open-copy" : "open-create");
                }
                case "connect-loopback" -> {
                    require(loopbackAddress.matches("127\\.0\\.0\\.1:[0-9]{1,5}"), "Network QA is restricted to the disposable loopback server");
                    var address = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(loopbackAddress);
                    var data = new net.minecraft.client.multiplayer.ServerData("TerraForged disposable QA", loopbackAddress, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                    phase = "network-loading"; ticks = 0;
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), client, address, data, false, null);
                    stage("loopback-connect-requested", "Actual multiplayer connection requested to " + loopbackAddress);
                }
                case "network-loading" -> {
                    if (client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen disconnected) throw new IllegalStateException("Loopback connection failed: " + disconnected.getTitle().getString());
                    if (client.level == null || client.player == null || client.getConnection() == null) return;
                    require(client.getSingleplayerServer() == null, "Expected a dedicated-server connection");
                    if (!client.level.hasChunkAt(client.player.blockPosition())) return;
                    if (++ticks < 200) return;
                    var biomes = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
                    var variants = biomes.keySet().stream().filter(id -> id.getNamespace().equals("reterraforged")).map(Object::toString).sorted().toList();
                    require(variants.size() == 17, "Dedicated server did not synchronize all 17 custom biome definitions");
                    var position = client.player.blockPosition();
                    var chunk = client.level.getChunkAt(position);
                    int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ());
                    require(top > client.level.getMinY() + 1, "Dedicated-server terrain did not arrive at client spawn");
                    var surface = chunk.getBlockState(new net.minecraft.core.BlockPos(position.getX(), top, position.getZ()));
                    require(!surface.isAir(), "Dedicated-server surface column is empty");
                    report.put("networkTerrainRendering", Map.of("address", loopbackAddress, "dedicatedServer", true,
                        "synchronizedVariants", variants, "surfaceHeight", top, "surfaceBlock", surface.toString(),
                        "biome", client.level.getBiome(position).unwrapKey().orElseThrow().identifier().toString(),
                        "position", List.of(position.getX(), position.getY(), position.getZ()),
                        "connectionOpen", client.getConnection().getConnection().isConnected()));
                    client.options.pauseOnLostFocus = false;
                    client.gui.setScreen(null);
                    client.mouseHandler.releaseMouse();
                    stage("loopback-world-received", "Real dedicated-server registry synchronization and chunk reception passed");
                    capture(client, "08-loopback-terrain.png", "disconnect-loopback");
                }
                case "disconnect-loopback" -> {
                    client.disconnectWithSavingScreen();
                    stage("loopback-disconnected", "Actual multiplayer disconnect completed");
                    finish(client, true, null);
                }
                case "open-copy" -> openQaWorld(client);
                case "open-create" -> {
                    phase = "create"; ticks = 0; busy = true;
                    try { CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen())); }
                    finally { busy = false; }
                }
                case "create" -> {
                    if (!(client.gui.screen() instanceof CreateWorldScreen actual)) return;
                    parent = actual;
                    if (++ticks < SETTLE_TICKS) return;
                    stage("create-world-ready", "Actual CreateWorldScreen loaded its real world-creation resources");
                    capture(client, "02-create-world.png", "open-list");
                }
                case "open-list" -> {
                    if (malformedPreset == null) preparePresetFixtures(client);
                    var factory = PresetEditor.EDITORS.get(Optional.of(WorldPresets.NORMAL));
                    require(factory != null, "Production preset editor was not registered for the normal world preset");
                    var registeredScreen = factory.createEditScreen(parent, parent.getUiState().getSettings());
                    require(registeredScreen instanceof PresetConfigScreen, "Registered production factory did not create the TerraForged editor");
                    config = (PresetConfigScreen) registeredScreen;
                    config.setSeed(SEED);
                    client.gui.setScreen(config);
                    phase = "list"; ticks = 0;
                }
                case "list" -> {
                    require(client.gui.screen() == config, "Actual preset-list screen was replaced unexpectedly");
                    if (++ticks < SETTLE_TICKS) return;
                    Object page = field(config, "currentPage");
                    require(page instanceof PresetListPage, "PresetConfigScreen did not initialize its actual preset list");
                    WidgetList<?> left = (WidgetList<?>) field(page, "left");
                    report.put("presetListRows", left.children().size());
                    require(left.children().size() >= 5, "Expected built-in preset rows are missing");
                    report.put("presetListTextFit", verifyListTextFit(client, page));
                    stage("preset-list-ready", left.children().size() + " actual list rows rendered");
                    if (!regressionComplete) {
                        long builtins = left.children().stream().filter(row -> {
                            try { return (boolean) invokeNoArgs(row.getWidget(), "isBuiltin"); }
                            catch (Exception failure) { throw new IllegalStateException(failure); }
                        }).count();
                        require(builtins == 5 && left.children().size() == 6, "Malformed imports hid a built-in or valid legacy preset");
                        require(Files.readString(malformedPreset).equals("{ invalid"), "Malformed input was unexpectedly rewritten");
                        require(Files.readString(invalidPreset).equals("{}"), "Invalid schema input was unexpectedly rewritten");
                        report.put("importRecovery", Map.of("builtins", builtins, "validLegacyRows", 1, "malformedJsonSkipped", true,
                            "invalidSchemaSkipped", true, "invalidFilesPreserved", true));
                    }
                    capture(client, regressionComplete ? "03z-preset-list-after-regressions.png" : "03-preset-list.png",
                        regressionComplete ? "open-editor" : "preset-create");
                }
                case "preset-create" -> {
                    Object page = field(config, "currentPage");
                    EditBox input = (EditBox) field(page, "input");
                    input.setValue("../QA Invalid");
                    require(!((Button) field(page, "createPreset")).active, "Unsafe filename enabled preset creation");
                    input.setValue(CREATED_PRESET);
                    press((Button) field(page, "createPreset"));
                    require(Files.isRegularFile(ConfigUtil.rtf("presets").resolve(CREATED_PRESET + ".json")), "Create callback did not save preset");
                    require(input.getValue().isEmpty(), "Create callback did not clear name input");
                    stage("preset-created-through-button", "Actual Create callback saved a disposable preset; invalid name stayed disabled");
                    capture(client, "03a-preset-created.png", "preset-copy");
                }
                case "preset-copy" -> {
                    selectPreset(CREATED_PRESET);
                    Object page = field(config, "currentPage");
                    press((Button) field(page, "copyPreset"));
                    Path original = ConfigUtil.rtf("presets").resolve(CREATED_PRESET + ".json");
                    Path copy = ConfigUtil.rtf("presets").resolve(CREATED_PRESET + " (1).json");
                    require(Files.readString(copy).equals(Files.readString(original)), "Copy callback did not preserve preset settings");
                    stage("preset-copied-through-button", "Actual Copy callback created a distinct matching preset");
                    capture(client, "03b-preset-copied.png", "preset-delete");
                }
                case "preset-delete" -> {
                    selectPreset(CREATED_PRESET + " (1)");
                    press((Button) field(field(config, "currentPage"), "deletePreset"));
                    require(!Files.exists(ConfigUtil.rtf("presets").resolve(CREATED_PRESET + " (1).json")), "Delete callback did not remove copy");
                    selectPreset(CREATED_PRESET);
                    press((Button) field(field(config, "currentPage"), "deletePreset"));
                    require(!Files.exists(ConfigUtil.rtf("presets").resolve(CREATED_PRESET + ".json")), "Delete callback did not remove original");
                    selectPreset(LEGACY_PRESET);
                    Object selected = invokeNoArgs(selectedPreset(), "getPath");
                    require(legacyPreset.equals(selected), "Actual legacy entry lost its original save/delete path");
                    press(config.nextButton);
                    phase = "legacy-editor"; ticks = 0;
                }
                case "legacy-editor" -> {
                    if (++ticks < SETTLE_TICKS) return;
                    Object page = field(config, "currentPage");
                    require(page instanceof WorldSettingsPage, "Legacy selection did not open the real world-settings page");
                    Slider slider = (Slider) field(page, "continentScale");
                    MouseButtonEvent event = new MouseButtonEvent(slider.getX() + 4 + (slider.getWidth() - 8) * .50,
                        slider.getY() + slider.getHeight() / 2.0, new MouseButtonInfo(0, 0));
                    slider.onClick(event, false); slider.onRelease(event);
                    stage("legacy-preset-edited", "Actual imported legacy preset and slider callback rendered successfully");
                    capture(client, "03c-legacy-editor.png", "legacy-back");
                }
                case "legacy-back" -> {
                    Object oldPreview = field(field(config, "currentPage"), "preview");
                    press(config.previousButton);
                    require(Boolean.TRUE.equals(field(oldPreview, "closed")), "Back callback left legacy preview open");
                    require(Files.isRegularFile(legacyPreset.resolveSibling(legacyPreset.getFileName() + ".bak")), "Legacy edit did not retain previous bytes");
                    require(!Files.exists(ConfigUtil.rtf("presets").resolve(LEGACY_PRESET + ".json")), "Legacy edit created a wrong-folder shadow");
                    selectPreset(LEGACY_PRESET);
                    Preset saved = (Preset) invokeNoArgs(selectedPreset(), "getPreset");
                    require(saved.world().continent.continentScale != 3000, "Back callback did not save edited legacy settings");
                    press((Button) field(field(config, "currentPage"), "deletePreset"));
                    require(!Files.exists(legacyPreset), "Delete callback did not remove the original legacy file");
                    require(raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount() == cacheBaseline, "Legacy editor retained generation caches");
                    report.put("presetFileCallbacks", Map.of("create", true, "copy", true, "delete", true, "legacyEditSavedOriginal", true,
                        "legacyDeleteRemovedOriginal", true, "legacyBackupRetained", true, "invalidNameDisabled", true));
                    stage("legacy-preset-back-save-delete", "Actual Back saved the imported source; actual Delete removed it without a shadow preset");
                    failurePreset = ConfigUtil.rtf("presets").resolve("QA Save Failure.json");
                    failurePresetOriginal = Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, Presets.makeLegacyDefault()).getOrThrow().toString();
                    Files.writeString(failurePreset, failurePresetOriginal, StandardOpenOption.CREATE_NEW);
                    walkingPages = true;
                    openEditor(client);
                }
                case "walk-pages" -> {
                    if (++ticks < SETTLE_TICKS) return;
                    Object page = field(config, "currentPage");
                    require(client.gui.screen() == config && page.getClass().getSimpleName().equals(EDITOR_PAGES.get(walkIndex)), "Editor page navigation reached an unexpected page");
                    Object preview = field(page, "preview");
                    require(!Boolean.TRUE.equals(field(preview, "closed")) && field(preview, "tile") != null, "Current editor page has no live generated preview");
                    int cacheCount = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    if (walkthroughCacheCount == 0) walkthroughCacheCount = cacheCount;
                    require(cacheCount == walkthroughCacheCount && cacheCount > cacheBaseline, "Page navigation leaked preview generation caches");
                    editorNavigation.add(Map.of("page", EDITOR_PAGES.get(walkIndex), "direction", walkingBack ? "back" : "forward", "previewCacheCount", cacheCount));
                    stage("editor-page-" + (walkingBack ? "back-" : "forward-") + walkIndex, EDITOR_PAGES.get(walkIndex) + " rendered with a live preview");
                    if (walkingBack) { phase = "walk-next"; ticks = 0; }
                    else capture(client, "03d-page-" + walkIndex + "-" + EDITOR_PAGES.get(walkIndex) + ".png", "walk-next");
                }
                case "walk-next" -> {
                    Object oldPreview = field(field(config, "currentPage"), "preview");
                    if (!walkingBack && walkIndex == EDITOR_PAGES.size() - 1) {
                        require(!config.nextButton.active, "Last implemented settings page unexpectedly enables Next");
                        walkingBack = true;
                    }
                    if (walkingBack) {
                        press(config.previousButton);
                        walkIndex--;
                    } else {
                        press(config.nextButton);
                        walkIndex++;
                    }
                    require(Boolean.TRUE.equals(field(oldPreview, "closed")), "Navigation did not close the prior preview");
                    phase = walkIndex < 0 ? "save-failure-open" : "walk-pages"; ticks = 0;
                }
                case "save-failure-open" -> {
                    require(field(config, "currentPage").getClass().getSimpleName().equals("PresetListPage"), "Back from world settings did not restore preset list");
                    require(raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount() == cacheBaseline, "Returning to list retained preview caches");
                    selectPreset("QA Save Failure");
                    press(config.nextButton);
                    phase = "save-failure-editor"; ticks = 0;
                }
                case "save-failure-editor" -> {
                    if (++ticks < SETTLE_TICKS) return;
                    Object page = field(config, "currentPage");
                    Preset preset = (Preset) invokeNoArgs(field(page, "preset"), "getPreset");
                    preset.terrain().general.globalVerticalScale = Float.NaN;
                    stage("save-failure-injected", "Invalid JSON number was injected into the disposable in-memory model after preview creation");
                    capture(client, "03e-save-failure-before-cancel.png", "save-failure-cancel");
                }
                case "save-failure-cancel" -> {
                    Object oldPreview = field(field(config, "currentPage"), "preview");
                    var before = parent.getUiState().getSettings();
                    press(config.cancelButton);
                    boolean unchanged = Files.readString(failurePreset).equals(failurePresetOriginal);
                    boolean closed = Boolean.TRUE.equals(field(oldPreview, "closed"));
                    int retained = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    boolean parentVisible = client.gui.screen() == parent && parent.getUiState().getSettings() == before;
                    report.put("saveFailureRecovery", Map.of("injectedFailure", "Nonfinite model value rejected by JSON writer", "actualCancelButton", true,
                        "fileUnchanged", unchanged, "previewClosed", closed, "cacheCount", retained, "cacheBaseline", cacheBaseline, "parentVisible", parentVisible));
                    stage("save-failure-cancel-observed", "File unchanged=" + unchanged + "; preview closed=" + closed + "; active caches=" + retained);
                    require(unchanged && closed && retained == cacheBaseline && parentVisible, "Preset save failure did not preserve file and close preview resources on actual Cancel");
                    Files.delete(malformedPreset); Files.delete(invalidPreset);
                    Files.delete(failurePreset);
                    Files.delete(legacyPreset.resolveSibling(legacyPreset.getFileName() + ".bak"));
                    report.put("presetEditorCancel", Map.of("actualCancelButton", true, "returnedToParent", true, "settingsUnchanged", true, "previewCachesReleased", true));
                    regressionComplete = true; walkingPages = false;
                    stage("editor-navigation-cancelled", "All eight implemented pages and Back/Cancel callbacks passed; disposable fixtures removed");
                    capture(client, "03f-cancel-returned-to-parent.png", "open-list");
                }
                case "open-editor" -> openEditor(client);
                case "editor" -> {
                    require(client.gui.screen() == config, "Preset editor screen was replaced unexpectedly");
                    if (++ticks < SETTLE_TICKS) return;
                    var evidence = previewEvidence();
                    report.put("previewBefore", evidence);
                    previewCacheCount = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    require(previewCacheCount > cacheBaseline, "Preview has no owned river caches");
                    previewCacheCounts.add(previewCacheCount);
                    report.put("previewCacheCounts", previewCacheCounts);
                    initialHeightDigest = evidence.get("heightSha256").toString();
                    Map<String, String> translations = new LinkedHashMap<>();
                    for (String key : List.of(RTFTranslationKeys.GUI_WORLD_SETTINGS_TITLE, RTFTranslationKeys.GUI_SLIDER_CONTINENT_SCALE, RTFTranslationKeys.GUI_BUTTON_SEED, RTFTranslationKeys.GUI_LABEL_PREVIEW_AREA)) {
                        String text = Component.translatable(key).getString();
                        translations.put(key, text);
                        if (Boolean.getBoolean("terraforged.clientqa.requireTranslations")) require(!text.equals(key), "Packaged production UI translation missing: " + key);
                    }
                    report.put("visibleTranslations", translations);
                    stage("preset-editor-ready", "Real legacy preset editor generated and uploaded its preview texture");
                    capture(client, "04-preset-editor-before.png", "change-slider");
                }
                case "change-slider" -> {
                    Slider slider = (Slider) field(editor, "continentScale");
                    require(slider.getWidth() > 8 && slider.getHeight() > 0, "Actual continent-scale slider has invalid bounds");
                    int before = editor.preset.getPreset().world().continent.continentScale;
                    Tile oldTile = previewTile();
                    MouseButtonEvent event = new MouseButtonEvent(slider.getX() + slider.getWidth() * 0.70, slider.getY() + slider.getHeight() / 2.0, new MouseButtonInfo(0, 0));
                    busy = true;
                    try { slider.onClick(event, false); slider.onRelease(event); }
                    finally { busy = false; }
                    int after = editor.preset.getPreset().world().continent.continentScale;
                    require(before != after, "Real slider input failed to update the actual preset setting");
                    require(previewTile() != oldTile, "Real slider callback failed to regenerate the preview tile");
                    report.put("sliderChange", Map.of("setting", "world.continent.continentScale", "before", before, "after", after, "input", "Actual AbstractSliderButton.onClick(MouseButtonEvent,false) and onRelease"));
                    sliderChanges.add(Map.of("before", before, "after", after, "preview", previewEvidence()));
                    verifyPreviewCacheCount();
                    phase = "changed"; ticks = 0;
                }
                case "changed" -> {
                    if (++ticks < SETTLE_TICKS) return;
                    report.put("previewAfter", previewEvidence());
                    stage("slider-preview-updated", "Actual setting changed and a replacement preview tile was rendered");
                    capture(client, "05-preset-editor-after.png", "repeat-sliders");
                }
                case "repeat-sliders" -> {
                    Slider slider = (Slider) field(editor, "continentScale");
                    int before = editor.preset.getPreset().world().continent.continentScale;
                    Tile oldTile = previewTile();
                    boolean restoreBaseline = sliderChanges.size() == 7;
                    double fraction = restoreBaseline ? (3000.25 - slider.getMin()) / (slider.getMax() - slider.getMin()) : 0.15 + (sliderChanges.size() % 4) * 0.20;
                    MouseButtonEvent event = new MouseButtonEvent(slider.getX() + 4 + (slider.getWidth() - 8) * fraction, slider.getY() + slider.getHeight() / 2.0, new MouseButtonInfo(0, 0));
                    busy = true;
                    try { slider.onClick(event, false); slider.onRelease(event); }
                    finally { busy = false; }
                    int after = editor.preset.getPreset().world().continent.continentScale;
                    require(before != after && previewTile() != oldTile, "Repeated real slider callback failed to replace setting/preview");
                    var evidence = previewEvidence();
                    sliderChanges.add(Map.of("before", before, "after", after, "preview", evidence));
                    verifyPreviewCacheCount();
                    if (sliderChanges.size() >= 8) {
                        require(after == 3000 && initialHeightDigest.equals(evidence.get("heightSha256")), "Restoring original setting did not restore exact preview height digest");
                        report.put("restoredBaselinePreviewDigest", initialHeightDigest);
                        stage("repeated-slider-previews", "Eight actual setting changes each regenerated a finite nonconstant 65536-cell preview");
                        phase = "close"; ticks = 0;
                    }
                }
                case "close" -> {
                    config.doneButton.onPress(new MouseButtonInfo(0, 0));
                    boolean appliedReady = parent.getUiState().getSettings().worldgenLoadContext().lookupOrThrow(RTFRegistries.PRESET).get(Preset.KEY).isPresent();
                    Screen immediateScreen = client.gui.screen();
                    boolean safeScreen = appliedReady || immediateScreen != parent && immediateScreen != config;
                    report.put("immediatePostDone", Map.of("appliedPresetReady", appliedReady, "screenClass", immediateScreen == null ? "null" : immediateScreen.getClass().getName(), "parentVisible", immediateScreen == parent, "editorVisible", immediateScreen == config, "safeScreenOwnership", safeScreen, "observedAt", Instant.now().toString()));
                    stage("post-done-screen-observed", "Applied preset ready=" + appliedReady + "; parent visible=" + (immediateScreen == parent));
                    require(safeScreen, "World creation became interactive immediately after Done before preset datapack validation completed");
                    require(Boolean.TRUE.equals(field(field(editor, "preview"), "closed")), "Production preview did not close its texture resources");
                    int cachesAfterEditor = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    require(cachesAfterEditor == cacheBaseline, "Closed editor retained generator caches");
                    report.put("cacheCountAfterEditorClose", cachesAfterEditor);
                    stage("preset-editor-closed", "Production close path released the actual preview resources");
                    phase = "preset-applied"; ticks = 0;
                }
                case "preset-applied" -> {
                    Screen waitingScreen = client.gui.screen();
                    if (waitingScreen instanceof ConfirmScreen confirmation) throw new IllegalStateException("Preset datapack validation opened a failure/confirmation screen: " + confirmation.getTitle().getString());
                    if (waitingScreen != parent) {
                        require(waitingScreen instanceof GenericMessageScreen, "Unexpected screen while waiting for actual preset application: " + (waitingScreen == null ? "null" : waitingScreen.getClass().getName()));
                        return;
                    }
                    var presets = parent.getUiState().getSettings().worldgenLoadContext().lookupOrThrow(RTFRegistries.PRESET);
                    if (presets.get(Preset.KEY).isEmpty()) return;
                    if (++ticks < SETTLE_TICKS) return;
                    Preset applied = presets.getOrThrow(Preset.KEY).value();
                    require(applied.world().continent.continentScale == 3000, "Actual Done callback applied the wrong preset settings");
                    require(applied.miscellaneous().classicBiomeVariants, "New built-in preset unexpectedly disabled classic variants");
                    require(parent.getUiState().getSettings().selectedDimensions().overworld().getBiomeSource() instanceof raccoonman.reterraforged.world.worldgen.biome.ClassicBiomeSource,
                        "Reloaded world-creation dimensions retained the previous biome source");
                    require(parent.getUiState().getSettings().options().seed() == SEED, "Actual Done callback changed the selected seed");
                    report.put("presetApplication", Map.of("presetKey", Preset.KEY.identifier().toString(), "continentScale", applied.world().continent.continentScale, "seed", SEED, "actualDoneButton", true, "realDatapackReload", true));
                    stage("preset-datapack-applied", "Actual Done button exported the preset pack and reloaded it into the real world-creation context");
                    if (createFromUi) {
                        parent.getUiState().setName("TerraForged Client Qa");
                        createdWorldFolder = parent.getUiState().getTargetFolder();
                        var create = parent.children().stream().filter(child -> child instanceof Button button && button.getMessage().equals(Component.translatable("selectWorld.create"))).findFirst().orElseThrow();
                        phase = "world-loading"; ticks = 0;
                        busy = true;
                        try { ((Button) create).onPress(new MouseButtonInfo(0, 0)); }
                        finally { busy = false; }
                        stage("new-world-create-button", "Activated actual Create New World button with applied TerraForged datapack and selected seed");
                    } else if (qaWorld.isBlank()) {
                        report.put("terrainRendering", "not requested");
                        finish(client, true, null);
                    } else {
                        openQaWorld(client);
                    }
                }
                case "world-loading" -> {
                    if (client.gui.screen() instanceof ConfirmScreen warning &&
                        (warning.getTitle().equals(Component.translatable("selectWorld.warning.experimental.title")) || warning.getTitle().equals(Component.translatable("selectWorld.warning.deprecated.title")))) {
                        if (warning == handledCreationWarning) return;
                        handledCreationWarning = warning;
                        var proceed = warning.children().stream().filter(child -> child instanceof Button button && button.getMessage().equals(CommonComponents.GUI_YES)).findFirst().orElseThrow();
                        stage("new-world-experimental-warning", "Selected actual Yes callback for new isolated QA world");
                        busy = true;
                        try { ((Button) proceed).onPress(new MouseButtonInfo(0, 0)); }
                        finally { busy = false; }
                        return;
                    }
                    if (client.gui.screen() instanceof BackupConfirmScreen backup) {
                        if (backup == handledBackup) return;
                        handledBackup = backup;
                        var proceed = backup.children().stream().filter(child -> child instanceof Button button && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton"))).findFirst().orElseThrow();
                        stage("copied-world-experimental-warning", "Selected real Skip Backup and Load callback for disposable copied world");
                        busy = true;
                        try { ((Button) proceed).onPress(new MouseButtonInfo(0, 0)); }
                        finally { busy = false; }
                        return;
                    }
                    if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
                    if (++ticks < 60) return;
                    var server = client.getSingleplayerServer();
                    var uuid = client.player.getUUID();
                    cameraSetup = server.submit(() -> {
                        var level = server.overworld();
                        var randomState = (RTFRandomState) (Object) level.getChunkSource().randomState();
                        if (vanillaStage) {
                            require(randomState.preset() == null && randomState.generatorContext() == null, "Vanilla world inherited a TerraForged preset/context from the prior world");
                        } else {
                            require(randomState.preset() != null && randomState.generatorContext() != null && randomState.generatorContext().cache != null, "Integrated world has no active TerraForged generation context");
                            var source = level.getChunkSource().getGenerator().getBiomeSource();
                            require(randomState.preset().miscellaneous().classicBiomeVariants && source instanceof raccoonman.reterraforged.world.worldgen.biome.ClassicBiomeSource,
                                "Created world is missing its enabled classic biome source");
                            var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
                            for (var variant : raccoonman.reterraforged.world.worldgen.biome.ClassicBiomes.VARIANTS) {
                                require(source.possibleBiomes().contains(registry.getOrThrow(variant.key())), "Created world cannot select " + variant.key());
                            }
                            if (createFromUi) require(level.getSeed() == SEED, "Actual new custom world has the wrong selected seed");
                        }
                        Map<String, Object> sorter;
                        int globalCount;
                        try { sorter = verifyFeatureSorter(level.getChunkSource().getGenerator()); globalCount = globalModifierCount(); }
                        catch (Exception failure) { throw new IllegalStateException("Feature sorter consistency check failed", failure); }
                        var player = server.getPlayerList().getPlayer(uuid);
                        require(player != null, "Synthetic QA player missing from real integrated server");
                        int cameraX = player.getBlockX();
                        int cameraZ = player.getBlockZ();
                        level.getChunk(cameraX >> 4, cameraZ >> 4);
                        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cameraX, cameraZ);
                        require(height > level.getMinY() + 1, "QA camera surface query returned an empty/unloaded column");
                        int cameraHeight = Integer.getInteger("terraforged.clientqa.cameraHeight", 48);
                        require(cameraHeight >= 8 && cameraHeight <= 128, "QA camera height must stay between 8 and 128 blocks");
                        player.setGameMode(GameType.SPECTATOR);
                        player.teleportTo(level, cameraX + 0.5, height + (double) cameraHeight, cameraZ + 0.5, Set.of(), -135.0F, 25.0F, true);
                        return Map.<String, Object>of("world", vanillaStage ? vanillaWorld : createFromUi ? createdWorldFolder : qaWorld, "customGeneratorActive", !vanillaStage, "surfaceHeight", height, "camera", List.of(cameraX + 0.5, height + (double) cameraHeight, cameraZ + 0.5, -135.0, 25.0), "mode", "spectator", "copiedWorldOnly", vanillaStage || !createFromUi, "createdThroughActualUi", !vanillaStage && createFromUi, "featureSorter", sorter, "worldSeed", level.getSeed(), "globalModifierCount", globalCount);
                    });
                    client.options.pauseOnLostFocus = false;
                    client.gui.setScreen(null);
                    phase = "terrain"; ticks = 0;
                }
                case "terrain" -> {
                    if (!cameraSetup.isDone()) return;
                    require(client.level != null && client.player != null, "Integrated level disappeared before terrain capture");
                    @SuppressWarnings("unchecked")
                    List<Number> requestedPose = (List<Number>) cameraSetup.join().get("camera");
                    client.mouseHandler.releaseMouse();
                    net.minecraft.client.KeyMapping.releaseAll();
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    client.setCameraEntity(client.player);
                    client.player.setPos(requestedPose.get(0).doubleValue(), requestedPose.get(1).doubleValue(), requestedPose.get(2).doubleValue());
                    client.player.setYRot(requestedPose.get(3).floatValue());
                    client.player.setXRot(requestedPose.get(4).floatValue());
                    client.player.yRotO = requestedPose.get(3).floatValue();
                    client.player.xRotO = requestedPose.get(4).floatValue();
                    int settleTicks = Integer.getInteger("terraforged.clientqa.terrainSettleTicks", 200);
                    require(settleTicks >= 60 && settleTicks <= 1200, "QA terrain settling must stay between 60 and 1200 ticks");
                    if (++ticks < settleTicks) return;
                    if (Boolean.getBoolean("terraforged.clientqa.requireRenderReady")) {
                        int radius = Math.min(6, Math.max(2, client.options.renderDistance().get() - 2));
                        int centerX = client.player.getBlockX() >> 4, centerZ = client.player.getBlockZ() >> 4;
                        int loaded = 0, expected = (radius * 2 + 1) * (radius * 2 + 1);
                        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) {
                            if (client.level.getChunkSource().getChunk(centerX + x, centerZ + z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) loaded++;
                        }
                        boolean meshed = client.levelRenderer.hasRenderedAllSections();
                        report.put(vanillaStage ? "vanillaRenderReadiness" : "customRenderReadiness", Map.of("innerRadius", radius,
                            "loadedInnerChunks", loaded, "expectedInnerChunks", expected, "rendererComplete", meshed,
                            "totalLoadedChunks", client.level.getChunkSource().getLoadedChunksCount(), "ticksAfterCamera", ticks));
                        if (loaded != expected || !meshed) return;
                    }
                    require(globalModifierCount() == originalGlobalModifierCount, "World-scoped biome modifiers leaked into Fabric's global list");
                    var observedPose = client.gameRenderer.mainCamera();
                    require(Math.abs(observedPose.xRot() - requestedPose.get(4).floatValue()) < 0.1F
                        && Math.abs(observedPose.yRot() - requestedPose.get(3).floatValue()) < 0.1F, "Rendered terrain camera drifted from the recorded pose");
                    report.put(vanillaStage ? "vanillaRenderedCamera" : "customRenderedCamera", List.of(observedPose.position().x,
                        observedPose.position().y, observedPose.position().z, observedPose.yRot(), observedPose.xRot()));
                    report.put(vanillaStage ? "vanillaTerrainRendering" : "terrainRendering", cameraSetup.join());
                    int caches = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
                    report.put(vanillaStage ? "cacheCountInVanillaAfterCustom" : "cacheCountInCustomWorld", caches);
                    if (vanillaStage) require(caches == cacheBaseline, "Closed custom world retained generator caches in the next vanilla world");
                    verifyShaderPipeline(vanillaStage ? "vanillaShaderPipeline" : "customShaderPipeline");
                    report.put("terrainCaptureSettings", Map.of("renderDistance", client.options.renderDistance().get(), "settleTicks", settleTicks));
                    stage(vanillaStage ? "vanilla-world-rendered" : "custom-world-rendered", "Real integrated world remained loaded for " + settleTicks + " ticks after camera setup; global modifier count unchanged");
                    capture(client, vanillaStage ? "07-vanilla-after-custom.png" : "06-custom-terrain.png", "world-close");
                }
                case "world-close" -> {
                    busy = true;
                    try { client.disconnectWithSavingScreen(); }
                    finally { busy = false; }
                    stage("copied-world-saved", "Actual integrated-world disconnect and save completed");
                    if (!vanillaStage && !vanillaWorld.isBlank()) {
                        vanillaStage = true;
                        handledBackup = null;
                        client.gui.setScreen(new TitleScreen());
                        phase = "open-copy"; ticks = 0;
                    } else {
                        finish(client, true, null);
                    }
                }
                default -> throw new IllegalStateException("Unknown client QA phase: " + phase);
            }
        } catch (Throwable failure) {
            finish(client, false, failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void openEditor(Minecraft client) throws Exception {
        Object page = field(config, "currentPage");
        WidgetList<PresetListPage.PresetEntry> list = (WidgetList<PresetListPage.PresetEntry>) field(page, "left");
        var first = list.children().stream().filter(entry -> entry.getWidget().isBuiltin()).findFirst().orElseThrow();
        list.setSelected(first);
        first.getWidget().onPress(new MouseButtonInfo(0, 0));
        require(config.nextButton.active, "Preset selection callback did not enable navigation");
        busy = true;
        try { config.nextButton.onPress(new MouseButtonInfo(0, 0)); }
        finally { busy = false; }
        Object selected = field(config, "currentPage");
        require(selected instanceof WorldSettingsPage, "Actual next-page callback did not open world settings");
        editor = (WorldSettingsPage) selected;
        require(editor.preset.isBuiltin(), "QA must edit a transient built-in copy without writing user presets");
        require(client.gui.screen() == config, "Actual preset editor is not the visible screen");
        phase = walkingPages ? "walk-pages" : "editor"; ticks = 0;
    }

    private void preparePresetFixtures(Minecraft client) throws Exception {
        Path game = client.gameDirectory.toPath().toAbsolutePath().normalize();
        Path modern = ConfigUtil.rtf("presets").toAbsolutePath().normalize();
        Path legacy = ConfigUtil.legacy("presets").toAbsolutePath().normalize();
        require(output.startsWith(game) && modern.startsWith(game) && legacy.startsWith(game), "Preset regression fixtures require the disposable game/config directories");
        Files.createDirectories(modern); Files.createDirectories(legacy);
        require(!Files.exists(modern.resolve(CREATED_PRESET + ".json")) && !Files.exists(modern.resolve(CREATED_PRESET + " (1).json")), "Disposable preset fixture names already exist");
        malformedPreset = modern.resolve("QA Malformed Import.json");
        invalidPreset = modern.resolve("QA Invalid Import.json");
        legacyPreset = legacy.resolve(LEGACY_PRESET + ".json");
        Files.writeString(malformedPreset, "{ invalid", StandardOpenOption.CREATE_NEW);
        Files.writeString(invalidPreset, "{}", StandardOpenOption.CREATE_NEW);
        var json = Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, Presets.makeLegacyDefault()).getOrThrow().getAsJsonObject();
        json.remove("caves");
        Files.writeString(legacyPreset, json.toString(), StandardOpenOption.CREATE_NEW);
        report.put("presetRegressionFixtures", Map.of("malformedJson", malformedPreset.toString(), "invalidSchema", invalidPreset.toString(),
            "legacyJson", legacyPreset.toString(), "legacyOptionalCavesOmitted", true));
    }

    private static void press(Button button) {
        require(button.active, "Actual button is unexpectedly disabled: " + button.getMessage().getString());
        button.onPress(new MouseButtonInfo(0, 0));
    }

    @SuppressWarnings("unchecked")
    private Object selectedPreset() throws Exception {
        WidgetList<AbstractWidget> list = (WidgetList<AbstractWidget>) field(field(config, "currentPage"), "left");
        return list.getSelected().getWidget();
    }

    @SuppressWarnings("unchecked")
    private void selectPreset(String name) throws Exception {
        WidgetList<AbstractWidget> list = (WidgetList<AbstractWidget>) field(field(config, "currentPage"), "left");
        var row = list.children().stream().filter(entry -> entry.getWidget().getMessage().getString().equals(name)).findFirst().orElseThrow();
        list.setSelected(row);
        ((AbstractButton) row.getWidget()).onPress(new MouseButtonInfo(0, 0));
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var method = type.getDeclaredMethod(name);
                if (!method.trySetAccessible()) throw new IllegalStateException("Production helper is not accessible to QA: " + type.getName() + "." + name);
                return method.invoke(target);
            } catch (NoSuchMethodException absent) { /* Continue through the actual class hierarchy. */ }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private void openQaWorld(Minecraft client) {
        String world = vanillaStage ? vanillaWorld : qaWorld;
        require(!world.isBlank(), "worldOnly QA requires a copied QA world");
        phase = "world-loading"; ticks = 0; busy = true;
        try { client.createWorldOpenFlows().openWorld(world, () -> finish(client, false, new IllegalStateException("Copied QA world opening was cancelled"))); }
        finally { busy = false; }
    }

    private Tile previewTile() throws Exception {
        Object preview = field(editor, "preview");
        return (Tile) field(preview, "tile");
    }

    private int globalModifierCount() throws Exception {
        return ((List<?>) field(BiomeModificationImpl.INSTANCE, "modifiers")).size();
    }

    private static List<Map<String, Object>> verifyListTextFit(Minecraft client, Object page) throws Exception {
        List<Map<String, Object>> measurements = new ArrayList<>();
        EditBox input = (EditBox) field(page, "input");
        Component hint = (Component) field(input, "hint");
        require(hint != null, "Actual preset input hint is absent");
        int hintWidth = client.font.width(hint);
        int inputInnerWidth = input.getInnerWidth();
        require(input.getWidth() > 0 && hintWidth <= inputInnerWidth, "Actual preset input hint exceeds its rendered inner width");
        measurements.add(Map.of("field", "input.hint", "text", hint.getString(), "fontWidth", hintWidth, "innerWidth", inputInnerWidth, "widgetWidth", input.getWidth(), "widthRule", "Actual EditBox.getInnerWidth()", "fits", true));
        for (String name : List.of("openPresetFolder", "openExportFolder", "exportAsDatapack")) {
            Button button = (Button) field(page, name);
            Component message = button.getMessage();
            int textWidth = client.font.width(message);
            int innerWidth = button.getWidth() - 4;
            require(innerWidth > 0 && textWidth <= innerWidth, "Actual list button label exceeds its rendered inner width: " + name);
            measurements.add(Map.of("field", name, "text", message.getString(), "fontWidth", textWidth, "innerWidth", innerWidth, "widgetWidth", button.getWidth(), "widthRule", "Actual AbstractButton label margin of 2 pixels on both sides", "fits", true));
        }
        return measurements;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> verifyFeatureSorter(ChunkGenerator generator) throws Exception {
        generator.validate();
        var supplier = (Supplier<List<FeatureSorter.StepFeatureData>>) field(generator, "featuresPerStep");
        var getter = (Function<Holder<Biome>, BiomeGenerationSettings>) field(generator, "generationSettingsGetter");
        List<FeatureSorter.StepFeatureData> sorted = supplier.get();
        int references = 0;
        for (var biome : generator.getBiomeSource().possibleBiomes()) {
            var features = getter.apply(biome).features();
            for (int step = 0; step < features.size(); step++) {
                for (var feature : features.get(step)) {
                    require(step < sorted.size(), "Biome has features beyond the generator sorter steps");
                    var data = sorted.get(step);
                    int index = data.indexMapping().applyAsInt(feature.value());
                    require(index >= 0 && index < data.features().size() && data.features().get(index) == feature.value(), "Current biome feature is absent from the generator's feature sorter: " + biome + " step " + step);
                    references++;
                }
            }
        }
        require(references > 0, "Generator feature sorter check inspected no biome features");
        long variants = generator.getBiomeSource().possibleBiomes().stream().filter(biome -> biome.unwrapKey()
            .map(key -> key.identifier().getNamespace().equals("reterraforged")).orElse(false)).count();
        Map<String, Long> namespaces = generator.getBiomeSource().possibleBiomes().stream().collect(java.util.stream.Collectors.groupingBy(
            biome -> biome.unwrapKey().orElseThrow().identifier().getNamespace(), java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        if (Boolean.getBoolean("terraforged.clientqa.requireBop")) require(namespaces.getOrDefault("biomesoplenty", 0L) == 59,
            "Pinned Biomes O' Plenty overworld family lost possible-biome membership: " + namespaces);
        return Map.of("possibleBiomes", generator.getBiomeSource().possibleBiomes().size(), "steps", sorted.size(), "featureReferencesChecked", references,
            "identityConsistent", true, "sourceClass", generator.getBiomeSource().getClass().getName(), "classicVariants", variants, "possibleBiomeNamespaces", namespaces);
    }

    private Map<String, Object> previewEvidence() throws Exception {
        Object preview = field(editor, "preview");
        Tile tile = (Tile) field(preview, "tile");
        require(tile != null, "Production preview has no generated tile");
        var texture = (net.minecraft.client.renderer.texture.DynamicTexture) field(preview, "texture");
        require(texture.getPixels() != null, "Production preview image was not allocated");
        int[] count = {0};
        float[] range = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        tile.iterate((cell, x, z) -> {
            require(Float.isFinite(cell.height), "Preview terrain has nonfinite height");
            range[0] = Math.min(range[0], cell.height); range[1] = Math.max(range[1], cell.height); count[0]++;
            int bits = Float.floatToRawIntBits(cell.height);
            digest.update((byte) (bits >>> 24)); digest.update((byte) (bits >>> 16)); digest.update((byte) (bits >>> 8)); digest.update((byte) bits);
        });
        require(count[0] > 0 && range[1] > range[0], "Production preview terrain is empty or constant");
        return Map.of("textureId", field(preview, "textureId").toString(), "textureWidth", texture.getPixels().getWidth(), "textureHeight", texture.getPixels().getHeight(), "cells", count[0], "minHeight", range[0], "maxHeight", range[1], "continentScale", editor.preset.getPreset().world().continent.continentScale, "seed", SEED, "heightSha256", HexFormat.of().formatHex(digest.digest()));
    }

    private void capture(Minecraft client, String name, String next) throws Exception {
        Path file = output.resolve("screenshots").resolve(name);
        require(!Files.exists(file), "Refusing stale screenshot output; use a fresh isolated QA directory: " + file);
        busy = true;
        int width = client.gameRenderer.mainRenderTarget().width;
        int height = client.gameRenderer.mainRenderTarget().height;
        Screenshot.grab(output.toFile(), name, client.gameRenderer.mainRenderTarget(), 1, message -> client.execute(() -> {
            try {
                require(Files.isRegularFile(file) && Files.size(file) > 0, "Minecraft Screenshot API did not save " + file + ": " + message.getString());
                screenshots.add(Map.of("path", file.toString(), "sha256", sha256(file), "bytes", Files.size(file), "width", width, "height", height, "screenClass", client.gui.screen() == null ? "in-world" : client.gui.screen().getClass().getName(), "captureApi", "net.minecraft.client.Screenshot.grab"));
                stage("screenshot-" + name, file.toString());
                phase = next; ticks = 0; busy = false;
            } catch (Throwable failure) { finish(client, false, failure); }
        }));
    }

    private Map<String, Object> provenance() throws Exception {
        FabricLoader loader = FabricLoader.getInstance();
        require(!loader.isDevelopmentEnvironment(), "Client QA requires the packaged production launch");
        Path origin = Path.of(RTFCommon.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        require(Files.isRegularFile(origin) && origin.toString().endsWith(".jar"), "Production class did not come from a JAR");
        String sha = sha256(origin);
        String expected = System.getProperty("terraforged.clientqa.expectedModSha256", "");
        if (!expected.isBlank()) require(expected.equalsIgnoreCase(sha), "Unexpected production JAR hash");
        return Map.of("minecraft", SharedConstants.getCurrentVersion().id(), "java", System.getProperty("java.version"), "loader", loader.getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString(), "modVersion", loader.getModContainer("reterraforged").orElseThrow().getMetadata().getVersion().getFriendlyString(), "modClassOrigin", origin.toString(), "modSha256", sha, "developmentEnvironment", false);
    }

    private void stage(String name, String detail) {
        Map<String, Object> stage = Map.of("name", name, "detail", detail, "at", Instant.now().toString());
        stages.add(stage);
        try {
            Files.createDirectories(output);
            Map<String, Object> progress = new LinkedHashMap<>(report);
            progress.put("status", "running"); progress.put("phase", phase);
            Files.writeString(output.resolve("progress.json"), JSON.toJson(progress) + "\n", StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Unable to persist client QA progress", failure);
        }
        System.out.println("TERRAFORGED_CLIENT_QA_STAGE_JSON=" + COMPACT.toJson(stage));
    }

    private void finish(Minecraft client, boolean passed, Throwable failure) {
        if (finished) return;
        finished = true;
        report.put("status", passed ? "passed" : "failed");
        report.put("phase", phase);
        report.put("finishedAt", Instant.now().toString());
        report.put("cleanStopRequested", true);
        if (failure != null) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            report.put("error", Map.of("message", failure.toString(), "stackTrace", trace.toString()));
        }
        try { Files.createDirectories(output); Files.writeString(output.resolve("result.json"), JSON.toJson(report) + "\n", StandardCharsets.UTF_8); }
        catch (Exception writeFailure) { report.put("status", "failed"); report.put("resultWriteError", writeFailure.toString()); }
        System.out.println("TERRAFORGED_CLIENT_QA_RESULT_JSON=" + COMPACT.toJson(report));
        client.stop();
    }

    private void verifyShaderPipeline(String key) throws Exception {
        if (!Boolean.getBoolean("terraforged.clientqa.requireShader")) return;
        Class<?> apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object api = apiType.getMethod("getInstance").invoke(null);
        boolean active = (boolean) apiType.getMethod("isShaderPackInUse").invoke(api);
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
        String name = (String) iris.getMethod("getCurrentPackName").invoke(null);
        Object pipeline = iris.getMethod("getPipelineManager").invoke(null);
        Object pack = iris.getMethod("getCurrentPack").invoke(null);
        require(active && pack instanceof Optional<?> selected && selected.isPresent(), "Requested shader pack is not active while terrain is rendered");
        require(name.equals(System.getProperty("terraforged.clientqa.expectedShaderName")), "Wrong shader pack is active: " + name);
        report.put(key, Map.of("shaderPackActive", true, "packName", name, "pipelineManagerClass", pipeline.getClass().getName()));
    }

    private void verifyPreviewCacheCount() {
        int count = raccoonman.reterraforged.concurrent.cache.CacheManager.activeCacheCount();
        previewCacheCounts.add(count);
        require(count == previewCacheCount, "Repeated preview generation retained old generator caches");
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException absent) { /* Continue through the actual class hierarchy. */ }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
