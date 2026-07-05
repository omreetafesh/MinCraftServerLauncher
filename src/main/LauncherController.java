package main;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class LauncherController {

    // ════════════════════════════════════════════════════════════════
    // STATIC METADATA
    // ════════════════════════════════════════════════════════════════

    // Aikar's optimized JVM flags
    private static final String[] AIKARS_FLAGS = {
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs", "-Daikars.new.flags=true"
    };

    // Diagnostic rules: {pattern, level, summary, explanation}
    private static final List<String[]> DIAGNOSTIC_RULES = Arrays.asList(
        new String[]{"OutOfMemoryError",
            "ERROR", "Out of heap memory",
            "The server ran out of RAM. Increase 'Max Memory' to 4 GB+ in Settings → Performance. Enable Aikar's flags for better GC."},
        new String[]{"Failed to bind to port",
            "ERROR", "Port already in use",
            "Another app is using the server port. Check server.properties → server-port, or close the conflicting application."},
        new String[]{"Address already in use",
            "ERROR", "Port conflict",
            "The port is already bound. Stop other Minecraft servers or change server-port in server.properties (World Config tab)."},
        new String[]{"Unable to access jarfile",
            "ERROR", "JAR file not found",
            "The server JAR was not found. Check Settings → Server, or use ⬇ Download to get one."},
        new String[]{"This crash report has been saved",
            "ERROR", "Crash report generated",
            "The server crashed. Check the crash-reports/ folder in your server directory for details."},
        new String[]{"java.lang.StackOverflowError",
            "ERROR", "Stack overflow",
            "A plugin or game code entered an infinite recursive loop. Disable recently added plugins."},
        new String[]{"Could not load 'plugins/",
            "WARN", "Plugin failed to load",
            "A plugin JAR could not be loaded. It may be incompatible with your server version."},
        new String[]{"Can't keep up!",
            "WARN", "Server lagging (TPS drop)",
            "The server is overloaded. Reduce view-distance in World Config, add more RAM in Performance tab, or enable Aikar's flags."},
        new String[]{"Connection throttled",
            "WARN", "Connections throttled",
            "Too many simultaneous connection attempts. May indicate a DDoS or misconfigured bots."},
        new String[]{"There is insufficient memory for the Java Runtime",
            "ERROR", "Memory allocation blocked by OS",
            "The OS blocked the JVM memory request. Reduce 'Max Memory' in Performance or close other applications."},
        new String[]{"java.net.SocketException",
            "WARN", "Network socket error",
            "A network error occurred. Usually a player disconnecting abruptly — generally harmless."},
        new String[]{"Skipping Entity with id",
            "WARN", "Unknown entity in world",
            "The world contains entities from a plugin no longer installed. They are skipped on load."},
        new String[]{"FAILED TO BIND TO PORT",
            "ERROR", "Port bind failed",
            "Could not open the server port. Check server-port in World Config or close conflicting applications."}
    );

    // Properties form definitions: {key, label, type, default}
    // type: "text" | "bool" | "int" | "choice:opt1,opt2,..."
    // Empty key = section header (label is the header text)
    private static final String[][] PROP_DEFS = {
        {"", "NETWORK", "", ""},
        {"server-port",   "Server Port",                        "int",  "25565"},
        {"server-ip",     "Bind IP (blank = all interfaces)",   "text", ""},
        {"max-players",   "Max Players",                        "int",  "20"},
        {"online-mode",   "Online Mode (verify accounts)",      "bool", "true"},
        {"prevent-proxy-connections", "Block Proxy Connections","bool", "false"},
        {"network-compression-threshold", "Compression Threshold (bytes)", "int", "256"},
        {"enable-query",  "Enable Query Protocol",              "bool", "false"},

        {"", "GAMEPLAY", "", ""},
        {"gamemode",      "Default Gamemode",
            "choice:survival,creative,adventure,spectator", "survival"},
        {"difficulty",    "Difficulty",
            "choice:peaceful,easy,normal,hard",             "easy"},
        {"pvp",           "Player vs Player (PvP)",             "bool", "true"},
        {"allow-flight",  "Allow Flight",                       "bool", "false"},
        {"allow-nether",  "Allow The Nether",                   "bool", "true"},
        {"enable-command-block", "Enable Command Blocks",       "bool", "false"},
        {"spawn-monsters","Spawn Monsters",                     "bool", "true"},
        {"spawn-animals", "Spawn Animals",                      "bool", "true"},
        {"spawn-npcs",    "Spawn Villagers",                    "bool", "true"},
        {"spawn-protection", "Spawn Protection Radius (blocks)","int",  "16"},
        {"white-list",    "Whitelist Enabled",                  "bool", "false"},
        {"enforce-whitelist", "Enforce Whitelist on Join",      "bool", "false"},
        {"hardcore",      "Hardcore Mode",                      "bool", "false"},

        {"", "WORLD", "", ""},
        {"level-name",    "World Folder Name",                  "text", "world"},
        {"level-seed",    "World Seed (blank = random)",        "text", ""},
        {"level-type",    "World Type",
            "choice:minecraft:normal,minecraft:flat,minecraft:large_biomes,minecraft:amplified",
            "minecraft:normal"},
        {"view-distance", "View Distance (chunks)",             "int",  "10"},
        {"simulation-distance", "Simulation Distance (chunks)", "int",  "10"},
        {"generate-structures", "Generate Structures",          "bool", "true"},
        {"max-world-size","Max World Radius (blocks)",          "int",  "29999984"},

        {"", "PERFORMANCE", "", ""},
        {"max-tick-time", "Max Tick Time ms (−1 = unlimited)",  "int",  "60000"},
        {"entity-broadcast-range-percentage", "Entity Broadcast Range %", "int", "100"},
        {"use-native-transport", "Native Transport (Linux)",    "bool", "true"},
        {"sync-chunk-writes", "Sync Chunk Writes",              "bool", "true"},

        {"", "MOTD & APPEARANCE", "", ""},
        {"motd",          "Server Description (MOTD)",          "text", "A Minecraft Server"},
        {"hide-online-players", "Hide Online Player List",      "bool", "false"},

        {"", "RCON", "", ""},
        {"enable-rcon",   "Enable RCON",                        "bool", "false"},
        {"rcon.port",     "RCON Port",                          "int",  "25575"},
        {"rcon.password", "RCON Password",                      "text", ""},
        {"broadcast-rcon-to-ops", "Broadcast RCON to Ops",     "bool", "true"},
    };

    // ════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ════════════════════════════════════════════════════════════════

    // Window management (undecorated mode)
    private Stage  stage;
    private double dragX, dragY;

    private Preferences prefs;
    private String currentProfile = "Default";

    // Server state
    private File    serverDirectory          = null;
    private File    backupDirectory          = null;
    private Process serverProcess;
    private OutputStream serverInput;
    private boolean isRestarting             = false;
    private String  currentJarName           = "server.jar";
    private String  currentMemGB             = "2";
    private boolean currentUseOptimizedFlags = false;
    private long    serverStartMillis;

    // Console data
    private final ObservableList<String> allLogs      = FXCollections.observableArrayList();
    private final FilteredList<String>   filteredLogs = new FilteredList<>(allLogs, s -> true);
    private final ObservableList<String> chatLogs     = FXCollections.observableArrayList();
    private static final int MAX_LOGS = 2000;
    private static final int TRIM_TO  = 1600;

    // Command history
    private final ArrayDeque<String> commandHistory = new ArrayDeque<>();
    private int historyIndex = -1;

    // Players
    private final ObservableList<String> onlinePlayers = FXCollections.observableArrayList();

    // Schedulers / timers
    private ScheduledExecutorService backupScheduler;
    private ScheduledExecutorService restartScheduler;
    private Timeline uptimeClock;
    private Timeline resourceMonitor;
    private volatile boolean restartCountdownActive = false;

    // RCON & system tray
    private RconClient       rconClient;
    private java.awt.TrayIcon trayIcon;

    // Diagnostics dedup
    private final Set<String> loggedDiagnostics = new LinkedHashSet<>();

    // Properties form state
    private Map<String, String>  currentProps = null;
    private final Map<String, Control> propControls = new LinkedHashMap<>();

    // ── Live Performance Charts ───────────────────────────────────────
    private LineChart<Number, Number>  ramChart;
    private LineChart<Number, Number>  tpsChart;
    private AreaChart<Number, Number>  playerChart;
    private final XYChart.Series<Number, Number> ramSeries    = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> tpsSeries    = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> playerSeries = new XYChart.Series<>();
    private Timeline monitorTimeline;
    private int monitorTick = 0;
    private volatile double lastParsedTps = 20.0;
    private static final int CHART_MAX_POINTS = 60; // 5 min at 5-second samples

    // ── Player stats ─────────────────────────────────────────────────
    private final ObservableList<ObservableList<String>> playerStatsData =
        FXCollections.observableArrayList();

    // ─── FXML: Top bar ───────────────────────────────────────────────
    @FXML private Button          startButton, stopButton, restartButton, settingsButton;
    @FXML private Label           statusLabel, uptimeLabel, resourceLabel;
    @FXML private ComboBox<String> profileCombo;

    // ─── FXML: Center layout ─────────────────────────────────────────
    @FXML private VBox settingsPane;        // entire settings panel (tabbed)
    @FXML private VBox consoleAreaView;

    // ─── FXML: Logs tab ──────────────────────────────────────────────
    @FXML private ListView<String> consoleList;
    @FXML private TextField        commandInput, searchField;

    // ─── FXML: Chat tab ──────────────────────────────────────────────
    @FXML private ListView<String> chatList;

    // ─── FXML: Diagnostics tab ───────────────────────────────────────
    @FXML private TextArea diagnosticsArea;

    // ─── FXML: Whitelist / Bans tab ──────────────────────────────────
    @FXML private ListView<String> whitelistView, banListView;
    @FXML private TextField        whitelistAddField, banAddField;

    // ─── FXML: RCON tab ──────────────────────────────────────────────
    @FXML private TextField       rconHostField, rconPortField, rconCommandField;
    @FXML private PasswordField   rconPasswordField;
    @FXML private ListView<String> rconOutputList;
    @FXML private Button          rconConnectButton;
    @FXML private Label           rconStatusLabel;

    // ─── FXML: Macros tab ────────────────────────────────────────────
    @FXML private ListView<String> macrosList;

    // ─── FXML: Settings → Server tab ─────────────────────────────────
    @FXML private TextField serverPathField, serverJarField;

    // ─── FXML: Settings → Performance tab ────────────────────────────
    @FXML private TextField  memoryField;
    @FXML private CheckBox   optimizedFlagsCheck;

    // ─── FXML: Settings → World Config tab ───────────────────────────
    @FXML private VBox       propertiesFormContainer;
    @FXML private ScrollPane propertiesFormScroll;
    @FXML private TextArea   propertiesEditor;
    @FXML private CheckBox   rawEditToggle;

    // ─── FXML: Settings → Backup tab ─────────────────────────────────
    @FXML private TextField        backupPathField, maxBackupsField;
    @FXML private CheckBox         autoBackupCheck;
    @FXML private ListView<String> backupFilesList;

    // ─── FXML: Settings → Automation tab ─────────────────────────────
    @FXML private TextField  crashRestartDelayField, scheduledRestartField;
    @FXML private CheckBox   crashRestartCheck, scheduledRestartCheck;

    // ─── FXML: Monitor tab ───────────────────────────────────────────
    @FXML private VBox monitorContainer;

    // ─── FXML: Player History tab ────────────────────────────────────
    @FXML @SuppressWarnings("unchecked")
    private TableView<ObservableList<String>> playerStatsTable;
    @FXML private Label playerStatsStatus;

    // ─── FXML: Settings → MOTD tab ───────────────────────────────────
    @FXML private TextArea  motdRawField;
    @FXML private TextFlow  motdPreview;
    @FXML private VBox      motdPaletteContainer;

    // ─── FXML: Player sidebar ────────────────────────────────────────
    @FXML private TableView<String> playerTable;
    @FXML private Label             playerCountLabel;
    @FXML private HBox              counterContainer;

    // ════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(LauncherController.class);

        stopButton.setDisable(true);
        restartButton.setDisable(true);
        commandInput.setDisable(true);

        setupConsoleList();
        setupChatList();
        setupWhitelistBanViews();
        setupRconView();
        setupMacrosList();
        setupPlayerTable();
        setupCommandHistory();

        // Persist text-field values automatically
        serverJarField.textProperty().addListener((obs, o, n) ->
            prefs.node("profiles").node(currentProfile).put("serverJar", n));
        memoryField.textProperty().addListener((obs, o, n) ->
            prefs.node("profiles").node(currentProfile).put("maxMemory", n));
        maxBackupsField.textProperty().addListener((obs, o, n) ->
            prefs.node("profiles").node(currentProfile).put("maxBackups", n));
        crashRestartDelayField.textProperty().addListener((obs, o, n) ->
            prefs.put("crashRestartDelay", n));
        scheduledRestartField.textProperty().addListener((obs, o, n) ->
            prefs.put("scheduledRestartTime", n));

        setupProfiles();
        setupMonitoring();
        setupSystemTray();
        setupMonitorCharts();
        setupPlayerStatsTable();
        setupMotdEditor();

        optimizedFlagsCheck.setSelected(prefs.getBoolean("optimizedFlags", false));
        loadMacros();

        appendSystemLog("> System initialized. Open Settings to configure your server.");
    }

    // ─── Console list setup ──────────────────────────────────────────

    private void setupConsoleList() {
        consoleList.setItems(filteredLogs);
        consoleList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) { setText(null); setStyle(""); }
                else { setText(line); setStyle("-fx-font-family:Consolas; -fx-font-size:12; " + logColor(line)); }
            }
        });
        searchField.textProperty().addListener((obs, o, q) -> {
            String lower = q.trim().toLowerCase();
            filteredLogs.setPredicate(lower.isEmpty() ? s -> true : s -> s.toLowerCase().contains(lower));
        });
    }

    private String logColor(String line) {
        if (line.contains("[ERROR]") || line.contains("[SEVERE]") || line.contains("Exception") || line.contains("FAILED"))
            return "-fx-text-fill: #ff4c4c;";
        if (line.contains("[WARN]"))     return "-fx-text-fill: #f57c00;";
        if (line.startsWith(">"))        return "-fx-text-fill: #00ff41;";
        if (line.contains(": <"))        return "-fx-text-fill: #ffffff;";
        return "-fx-text-fill: #4db8ff;";
    }

    private void setupChatList() {
        chatList.setItems(chatLogs);
        chatList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) { setText(null); setStyle(""); }
                else { setText(line); setStyle("-fx-font-family:Consolas; -fx-font-size:12; -fx-text-fill:#ffffff;"); }
            }
        });
    }

    private void setupWhitelistBanViews() {
        whitelistView.setCellFactory(lv -> simpleCell());
        banListView.setCellFactory(lv -> simpleCell());
    }

    private ListCell<String> simpleCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item); setStyle("-fx-font-family:Consolas; -fx-font-size:12; -fx-text-fill:#e0e0e0;"); }
            }
        };
    }

    private void setupRconView() {
        rconOutputList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) { setText(null); setStyle(""); }
                else {
                    setText(line);
                    setStyle("-fx-font-family:Consolas; -fx-font-size:12; -fx-text-fill:" +
                             (line.startsWith(">") ? "#00ff41" : "#4db8ff") + ";");
                }
            }
        });
        rconStatusLabel.setText("Disconnected");
        rconStatusLabel.setStyle("-fx-text-fill:#ff4c4c;");
    }

    private void setupMacrosList() {
        macrosList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item); setStyle("-fx-font-family:Consolas; -fx-font-size:12; -fx-text-fill:#00ff41;"); }
            }
        });
    }

    // ─── Command history ─────────────────────────────────────────────

    private void setupCommandHistory() {
        commandInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP   -> navigateHistory(true);
                case DOWN -> navigateHistory(false);
                default   -> {}
            }
        });
    }

    private void navigateHistory(boolean goOlder) {
        if (commandHistory.isEmpty()) return;
        List<String> hist = new ArrayList<>(commandHistory);
        if (goOlder) {
            historyIndex = Math.min(historyIndex + 1, hist.size() - 1);
        } else {
            historyIndex--;
            if (historyIndex < 0) { historyIndex = -1; commandInput.clear(); return; }
        }
        commandInput.setText(hist.get(historyIndex));
        commandInput.positionCaret(commandInput.getText().length());
    }

    // ─── Profiles ────────────────────────────────────────────────────

    private void setupProfiles() {
        List<String> names = new ArrayList<>();
        try { names.addAll(Arrays.asList(prefs.node("profiles").childrenNames())); }
        catch (BackingStoreException ignored) {}

        if (names.isEmpty()) { migrateToProfile("Default"); names.add("Default"); }
        if (!names.contains("Default")) names.add(0, "Default");
        profileCombo.getItems().setAll(names);

        currentProfile = prefs.get("currentProfile", "Default");
        if (!profileCombo.getItems().contains(currentProfile))
            currentProfile = profileCombo.getItems().get(0);
        profileCombo.setValue(currentProfile);
        loadProfile(currentProfile);

        profileCombo.setOnAction(e -> {
            String selected = profileCombo.getValue();
            if (selected == null || selected.equals(currentProfile)) return;
            if (serverProcess != null && serverProcess.isAlive()) {
                appendSystemLog("> Cannot switch profiles while server is running.");
                Platform.runLater(() -> profileCombo.setValue(currentProfile));
                return;
            }
            saveCurrentProfile();
            currentProfile = selected;
            prefs.put("currentProfile", currentProfile);
            loadProfile(currentProfile);
        });
    }

    private void migrateToProfile(String name) {
        Preferences p = prefs.node("profiles").node(name);
        String sp = prefs.get("serverPath", null), bp = prefs.get("backupPath", null);
        if (sp != null) p.put("serverPath", sp);
        if (bp != null) p.put("backupPath", bp);
        p.put("serverJar",  prefs.get("serverJar",  "server.jar"));
        p.put("maxMemory",  prefs.get("maxMemory",  "2"));
        p.put("maxBackups", prefs.get("maxBackups", "5"));
        p.putBoolean("autoBackupEnabled", prefs.getBoolean("autoBackupEnabled", false));
    }

    private void saveCurrentProfile() {
        Preferences p = prefs.node("profiles").node(currentProfile);
        if (serverDirectory != null) p.put("serverPath", serverDirectory.getAbsolutePath());
        else p.remove("serverPath");
        if (backupDirectory != null) p.put("backupPath", backupDirectory.getAbsolutePath());
        else p.remove("backupPath");
        p.put("serverJar",  serverJarField.getText());
        p.put("maxMemory",  memoryField.getText());
        p.put("maxBackups", maxBackupsField.getText());
        p.putBoolean("autoBackupEnabled", autoBackupCheck.isSelected());
    }

    private void loadProfile(String name) {
        stopBackupScheduler();
        stopRestartScheduler();

        Preferences p = prefs.node("profiles").node(name);
        String sp = p.get("serverPath", null);
        String bp = p.get("backupPath", null);

        serverDirectory = sp != null ? new File(sp) : null;
        backupDirectory = bp != null ? new File(bp) : null;

        serverPathField.setText(sp != null ? sp : "");
        backupPathField.setText(bp != null ? bp : "");
        serverJarField.setText(p.get("serverJar",  "server.jar"));
        memoryField.setText(   p.get("maxMemory",  "2"));
        maxBackupsField.setText(p.get("maxBackups", "5"));

        boolean autoBackup = p.getBoolean("autoBackupEnabled", false);
        autoBackupCheck.setSelected(autoBackup);
        if (autoBackup && backupDirectory != null) startBackupScheduler();

        if (sp != null) {
            appendSystemLog("> Profile '" + name + "' — " + sp);
            loadServerProperties();
            loadWhitelist();
            loadBanList();
        } else {
            appendSystemLog("> Profile '" + name + "' loaded (no server directory set).");
        }

        crashRestartCheck.setSelected(prefs.getBoolean("crashRestart", false));
        crashRestartDelayField.setText(prefs.get("crashRestartDelay", "10"));
        scheduledRestartCheck.setSelected(prefs.getBoolean("scheduledRestart", false));
        scheduledRestartField.setText(prefs.get("scheduledRestartTime", "04:00"));
        optimizedFlagsCheck.setSelected(prefs.getBoolean("optimizedFlags", false));
        rconHostField.setText(prefs.get("rconHost", "localhost"));
        rconPortField.setText(prefs.get("rconPort", "25575"));

        if (prefs.getBoolean("scheduledRestart", false)) startRestartScheduler();
        refreshBackupList();
    }

    @FXML private void handleNewProfile() {
        TextInputDialog dlg = new TextInputDialog("New Server");
        dlg.setTitle("New Profile"); dlg.setHeaderText(null); dlg.setContentText("Profile name:");
        dlg.showAndWait().filter(s -> !s.isBlank()).ifPresent(name -> {
            if (profileCombo.getItems().contains(name)) { appendSystemLog("> Profile '" + name + "' already exists."); return; }
            saveCurrentProfile();
            Preferences p = prefs.node("profiles").node(name);
            p.put("serverJar", "server.jar"); p.put("maxMemory", "2"); p.put("maxBackups", "5");
            profileCombo.getItems().add(name); profileCombo.setValue(name);
            currentProfile = name; prefs.put("currentProfile", name);
            loadProfile(name); appendSystemLog("> Created profile '" + name + "'.");
        });
    }

    @FXML private void handleDeleteProfile() {
        if (profileCombo.getItems().size() <= 1) { appendSystemLog("> Cannot delete the last profile."); return; }
        String toDelete = currentProfile;
        try { prefs.node("profiles").node(toDelete).removeNode(); }
        catch (BackingStoreException e) { appendSystemLog("> Failed to delete: " + e.getMessage()); return; }
        profileCombo.getItems().remove(toDelete);
        String next = profileCombo.getItems().get(0);
        profileCombo.setValue(next); currentProfile = next; prefs.put("currentProfile", next);
        loadProfile(next); appendSystemLog("> Deleted profile '" + toDelete + "'.");
    }

    // ─── Monitoring ──────────────────────────────────────────────────

    private void setupMonitoring() {
        uptimeLabel.setText(""); resourceLabel.setText("");

        uptimeClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (serverProcess != null && serverProcess.isAlive()) {
                long ms = System.currentTimeMillis() - serverStartMillis;
                uptimeLabel.setText(String.format("Uptime %02d:%02d:%02d",
                    ms / 3_600_000, (ms % 3_600_000) / 60_000, (ms % 60_000) / 1_000));
            }
        }));
        uptimeClock.setCycleCount(Timeline.INDEFINITE);

        resourceMonitor = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            try {
                com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                double cpu    = os.getCpuLoad() * 100;
                long usedMB   = (os.getTotalMemorySize() - os.getFreeMemorySize()) / (1024 * 1024);
                long totalMB  = os.getTotalMemorySize() / (1024 * 1024);
                resourceLabel.setText(String.format("CPU %.0f%%  RAM %d/%dMB", cpu, usedMB, totalMB));
            } catch (Exception ex) { resourceLabel.setText(""); }
        }));
        resourceMonitor.setCycleCount(Timeline.INDEFINITE);
        resourceMonitor.play();
    }

    // ─── System tray ─────────────────────────────────────────────────

    private Stage trayMenuStage;

    private void setupSystemTray() {
        if (!java.awt.SystemTray.isSupported()) return;
        try {
            trayIcon = new java.awt.TrayIcon(buildTrayImage(), "Minecraft Server Launcher");
            trayIcon.setImageAutoSize(true);

            trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger())
                        Platform.runLater(() -> showTrayMenu(e.getXOnScreen(), e.getYOnScreen()));
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2)
                        Platform.runLater(LauncherController.this::showMainWindow);
                }
            });

            java.awt.SystemTray.getSystemTray().add(trayIcon);
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {}
    }

    private java.awt.image.BufferedImage buildTrayImage() {
        return Main.drawGrassBlock(256);
    }

    private void showTrayMenu(int screenX, int screenY) {
        if (trayMenuStage == null) {
            trayMenuStage = new Stage();
            trayMenuStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            trayMenuStage.setAlwaysOnTop(true);

            Label statusLbl = new Label();
            statusLbl.setStyle("-fx-text-fill:#555; -fx-font-size:10; " +
                               "-fx-padding:10 16 8 16; -fx-font-family:Consolas;");

            Separator sep1 = new Separator();
            sep1.setStyle("-fx-background-color:#333; -fx-padding:0;");
            Separator sep2 = new Separator();
            sep2.setStyle("-fx-background-color:#333; -fx-padding:0;");

            Button showBtn  = trayMenuBtn("↗  Open Dashboard",  "#00ff41");
            Button startBtn = trayMenuBtn("▶  Start Server",     "#4caf50");
            Button stopBtn  = trayMenuBtn("■  Stop Server",      "#ef5350");
            Button quitBtn  = trayMenuBtn("✕  Quit",             "#888888");

            showBtn.setOnAction(e  -> { trayMenuStage.hide(); showMainWindow(); });
            startBtn.setOnAction(e -> { trayMenuStage.hide(); Platform.runLater(this::handleStartServer); });
            stopBtn.setOnAction(e  -> { trayMenuStage.hide(); Platform.runLater(this::handleStopServer); });
            quitBtn.setOnAction(e  -> { cleanup(); System.exit(0); });

            VBox menu = new VBox(0, statusLbl, sep1, showBtn, startBtn, stopBtn, sep2, quitBtn);
            menu.setStyle(
                "-fx-background-color:#1e1e1e;" +
                "-fx-border-color:#3a3a3a;" +
                "-fx-border-radius:10;" +
                "-fx-background-radius:10;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.7),16,0,0,4);"
            );
            menu.setPrefWidth(190);

            Scene scene = new Scene(menu);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            trayMenuStage.setScene(scene);

            trayMenuStage.focusedProperty().addListener((obs, was, focused) -> {
                if (Boolean.FALSE.equals(focused)) trayMenuStage.hide();
            });

            // keep a reference to update the status label each time
            trayMenuStage.setUserData(statusLbl);
        }

        // Update status before showing
        Label lbl = (Label) trayMenuStage.getUserData();
        boolean running = serverProcess != null && serverProcess.isAlive();
        lbl.setText(running ? "● Server  RUNNING" : "○ Server  OFFLINE");
        lbl.setStyle("-fx-text-fill:" + (running ? "#00cc33" : "#555") +
                     "; -fx-font-size:10; -fx-padding:10 16 8 16; -fx-font-family:Consolas;");

        // Position: show above the click so it doesn't go off-screen
        trayMenuStage.show(); // measure height after first show
        double mh = trayMenuStage.getScene().getRoot().prefHeight(190);
        trayMenuStage.setX(screenX);
        trayMenuStage.setY(screenY - mh - 4);
        trayMenuStage.requestFocus();
    }

    private Button trayMenuBtn(String text, String color) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        String base = "-fx-font-size:12; -fx-alignment:CENTER_LEFT; " +
                      "-fx-padding:9 16 9 16; -fx-cursor:hand; " +
                      "-fx-background-radius:0;";
        b.setStyle("-fx-background-color:transparent; -fx-text-fill:" + color + ";" + base);
        b.setOnMouseEntered(e ->
            b.setStyle("-fx-background-color:#2d2d2d; -fx-text-fill:" + color + ";" + base));
        b.setOnMouseExited(e ->
            b.setStyle("-fx-background-color:transparent; -fx-text-fill:" + color + ";" + base));
        return b;
    }

    // ════════════════════════════════════════════════════════════════
    // FEATURE 1 — LIVE PERFORMANCE CHARTS
    // ════════════════════════════════════════════════════════════════

    private void setupMonitorCharts() {
        if (monitorContainer == null) return;

        ramSeries.setName("RAM");
        tpsSeries.setName("TPS");
        playerSeries.setName("Players");

        // ── RAM chart ──────────────────────────────────────────────
        NumberAxis xRam = timeAxis(); NumberAxis yRam = new NumberAxis();
        yRam.setLabel("MB"); yRam.setAutoRanging(true);
        ramChart = new LineChart<>(xRam, yRam);
        styleChart(ramChart, "System RAM Usage");
        ramChart.setCreateSymbols(false);
        ramChart.getData().add(ramSeries);
        ramChart.setPrefHeight(170); ramChart.setMaxHeight(170);

        // ── TPS chart ──────────────────────────────────────────────
        NumberAxis xTps = timeAxis(); NumberAxis yTps = new NumberAxis(0, 21, 5);
        yTps.setLabel("TPS"); yTps.setAutoRanging(false);
        tpsChart = new LineChart<>(xTps, yTps);
        styleChart(tpsChart, "Server TPS  (20 = perfect)");
        tpsChart.setCreateSymbols(false);
        tpsChart.getData().add(tpsSeries);
        tpsChart.setPrefHeight(160); tpsChart.setMaxHeight(160);

        // ── Player count chart ─────────────────────────────────────
        NumberAxis xPly = timeAxis(); NumberAxis yPly = new NumberAxis();
        yPly.setLabel("Players"); yPly.setAutoRanging(true);
        playerChart = new AreaChart<>(xPly, yPly);
        styleChart(playerChart, "Players Online");
        playerChart.setCreateSymbols(false);
        playerChart.getData().add(playerSeries);
        playerChart.setPrefHeight(140); playerChart.setMaxHeight(140);

        monitorContainer.getChildren().setAll(ramChart, tpsChart, playerChart);
        VBox.setVgrow(ramChart,    Priority.ALWAYS);
        VBox.setVgrow(tpsChart,    Priority.ALWAYS);
        VBox.setVgrow(playerChart, Priority.ALWAYS);
    }

    private NumberAxis timeAxis() {
        NumberAxis ax = new NumberAxis();
        ax.setLabel("seconds"); ax.setAutoRanging(true); ax.setForceZeroInRange(false);
        return ax;
    }

    private void styleChart(Chart chart, String title) {
        chart.setTitle(title); chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setStyle("-fx-background-color:#1a1a1a; -fx-plot-background-color:#1a1a1a;");
    }

    private void startMonitorTimeline() {
        if (monitorTimeline != null) monitorTimeline.stop();
        monitorTick = 0;
        ramSeries.getData().clear(); tpsSeries.getData().clear(); playerSeries.getData().clear();
        monitorTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> updateChartTick()));
        monitorTimeline.setCycleCount(Timeline.INDEFINITE);
        monitorTimeline.play();
    }

    private void stopMonitorTimeline() {
        if (monitorTimeline != null) { monitorTimeline.stop(); monitorTimeline = null; }
    }

    private void updateChartTick() {
        monitorTick += 5;
        // RAM — system used memory via OS bean
        try {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long usedMB = (os.getTotalMemorySize() - os.getFreeMemorySize()) / 1_048_576L;
            chartPoint(ramSeries, monitorTick, usedMB);
        } catch (Exception ignored) {}
        // TPS — from last parsed server log
        chartPoint(tpsSeries,    monitorTick, lastParsedTps);
        // Players
        chartPoint(playerSeries, monitorTick, onlinePlayers.size());
    }

    private void chartPoint(XYChart.Series<Number, Number> s, int x, double y) {
        s.getData().add(new XYChart.Data<>(x, y));
        if (s.getData().size() > CHART_MAX_POINTS) s.getData().remove(0);
    }

    // ════════════════════════════════════════════════════════════════
    // FEATURE 3 — MOTD VISUAL EDITOR
    // ════════════════════════════════════════════════════════════════

    private static final String[] MC_CODES  =
        {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
    private static final String[] MC_HEXES  =
        {"#000000","#0000AA","#00AA00","#00AAAA","#AA0000","#AA00AA",
         "#FFAA00","#AAAAAA","#555555","#5555FF","#55FF55","#55FFFF",
         "#FF5555","#FF55FF","#FFFF55","#FFFFFF"};

    private void setupMotdEditor() {
        if (motdPaletteContainer == null) return;

        // Color swatch rows (8 per row)
        HBox row1 = new HBox(5), row2 = new HBox(5);
        for (int i = 0; i < 16; i++) {
            final String code = "&" + MC_CODES[i];
            String hex = MC_HEXES[i];
            Button b = new Button();
            b.setPrefSize(28, 22);
            b.setStyle("-fx-background-color:" + hex + ";" +
                       "-fx-background-radius:4; -fx-cursor:hand;" +
                       "-fx-border-color:rgba(255,255,255,0.15); -fx-border-radius:4;");
            b.setTooltip(new Tooltip(code + "  " + hex));
            b.setOnAction(e -> insertMotdCode(code));
            (i < 8 ? row1 : row2).getChildren().add(b);
        }

        // Formatting buttons
        HBox fmtRow = new HBox(6);
        String[][] fmts = {
            {"&l","Bold","#e0e0e0"}, {"&o","Italic","#e0e0e0"},
            {"&n","Underline","#e0e0e0"}, {"&m","Strike","#e0e0e0"},
            {"&r","Reset","#ff4c4c"}
        };
        for (String[] f : fmts) {
            final String code = f[0];
            Button b = new Button(f[1]);
            b.setStyle("-fx-background-color:#2d2d2d; -fx-text-fill:" + f[2] + ";" +
                       "-fx-font-size:11; -fx-background-radius:4;" +
                       "-fx-cursor:hand; -fx-padding:3 10 3 10;");
            b.setOnAction(e -> insertMotdCode(code));
            fmtRow.getChildren().add(b);
        }

        Label colLbl = new Label("Colors:");
        colLbl.setStyle("-fx-text-fill:#888; -fx-font-size:11; -fx-padding:0 0 4 0;");
        Label fmtLbl = new Label("Formatting:");
        fmtLbl.setStyle("-fx-text-fill:#888; -fx-font-size:11; -fx-padding:8 0 4 0;");

        motdPaletteContainer.getChildren().setAll(colLbl, row1, row2, fmtLbl, fmtRow);
    }

    private void insertMotdCode(String code) {
        if (motdRawField == null) return;
        int pos  = motdRawField.getCaretPosition();
        String t = motdRawField.getText();
        motdRawField.setText(t.substring(0, pos) + code + t.substring(pos));
        motdRawField.positionCaret(pos + code.length());
        motdRawField.requestFocus();
        renderMotdPreview();
    }

    @FXML private void handleMotdInput() { renderMotdPreview(); }

    private void renderMotdPreview() {
        if (motdPreview == null || motdRawField == null) return;
        motdPreview.getChildren().clear();

        javafx.scene.paint.Color color = javafx.scene.paint.Color.WHITE;
        boolean bold = false, italic = false, ul = false, st = false;
        StringBuilder seg = new StringBuilder();
        String raw = motdRawField.getText();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean isCode = (c == '&' || c == '§') && i + 1 < raw.length();
            if (isCode) {
                if (seg.length() > 0) {
                    motdPreview.getChildren().add(previewText(seg.toString(), color, bold, italic, ul, st));
                    seg.setLength(0);
                }
                char k = Character.toLowerCase(raw.charAt(i + 1));
                color = switch (k) {
                    case '0' -> javafx.scene.paint.Color.web("#000000");
                    case '1' -> javafx.scene.paint.Color.web("#0000AA");
                    case '2' -> javafx.scene.paint.Color.web("#00AA00");
                    case '3' -> javafx.scene.paint.Color.web("#00AAAA");
                    case '4' -> javafx.scene.paint.Color.web("#AA0000");
                    case '5' -> javafx.scene.paint.Color.web("#AA00AA");
                    case '6' -> javafx.scene.paint.Color.web("#FFAA00");
                    case '7' -> javafx.scene.paint.Color.web("#AAAAAA");
                    case '8' -> javafx.scene.paint.Color.web("#555555");
                    case '9' -> javafx.scene.paint.Color.web("#5555FF");
                    case 'a' -> javafx.scene.paint.Color.web("#55FF55");
                    case 'b' -> javafx.scene.paint.Color.web("#55FFFF");
                    case 'c' -> javafx.scene.paint.Color.web("#FF5555");
                    case 'd' -> javafx.scene.paint.Color.web("#FF55FF");
                    case 'e' -> javafx.scene.paint.Color.web("#FFFF55");
                    case 'f' -> javafx.scene.paint.Color.WHITE;
                    default  -> color;
                };
                if (k == 'l') bold = true;
                else if (k == 'o') italic = true;
                else if (k == 'n') ul = true;
                else if (k == 'm') st = true;
                else if (k == 'r') {
                    color = javafx.scene.paint.Color.WHITE;
                    bold = italic = ul = st = false;
                }
                i++;
            } else if (c == '\n') {
                if (seg.length() > 0) {
                    motdPreview.getChildren().add(previewText(seg.toString(), color, bold, italic, ul, st));
                    seg.setLength(0);
                }
                motdPreview.getChildren().add(new Text("\n"));
            } else {
                seg.append(c);
            }
        }
        if (seg.length() > 0)
            motdPreview.getChildren().add(previewText(seg.toString(), color, bold, italic, ul, st));
    }

    private Text previewText(String s, javafx.scene.paint.Color color,
                             boolean bold, boolean italic, boolean ul, boolean st) {
        Text t = new Text(s);
        t.setFill(color);
        t.setFont(Font.font("Consolas",
            bold   ? FontWeight.BOLD   : FontWeight.NORMAL,
            italic ? FontPosture.ITALIC : FontPosture.REGULAR, 14));
        t.setUnderline(ul); t.setStrikethrough(st);
        return t;
    }

    @FXML private void applyMotdToProperties() {
        if (serverDirectory == null || currentProps == null) {
            appendSystemLog("> Set a server directory first."); return;
        }
        String raw = motdRawField.getText().trim();
        String converted = raw.replace("&", "§").replace("\n", "\\n");
        currentProps.put("motd", converted);
        Control ctrl = propControls.get("motd");
        if (ctrl instanceof TextField tf) tf.setText(converted);
        savePropertiesFromForm();
        appendSystemLog("> MOTD applied.");
    }

    @FXML private void clearMotd() {
        if (motdRawField != null) { motdRawField.clear(); renderMotdPreview(); }
    }

    // ════════════════════════════════════════════════════════════════
    // FEATURE 4 — PLAYER HISTORY & STATS
    // ════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void setupPlayerStatsTable() {
        if (playerStatsTable == null) return;
        String[] headers = {"Player","Play Time","Deaths","Kills","Dist. (km)","UUID"};
        for (int i = 0; i < Math.min(headers.length, playerStatsTable.getColumns().size()); i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col =
                (TableColumn<ObservableList<String>, String>) playerStatsTable.getColumns().get(idx);
            col.setCellValueFactory(data ->
                new SimpleStringProperty(idx < data.getValue().size()
                    ? data.getValue().get(idx) : ""));
        }
        playerStatsTable.setItems(playerStatsData);
        playerStatsTable.setPlaceholder(new Label("Click ↻ Refresh to load player data"));
    }

    @FXML private void refreshPlayerStats() {
        if (serverDirectory == null) {
            if (playerStatsStatus != null) playerStatsStatus.setText("No server directory set");
            return;
        }
        if (playerStatsStatus != null) playerStatsStatus.setText("Loading…");
        playerStatsData.clear();

        Thread t = new Thread(() -> {
            List<ObservableList<String>> rows = new ArrayList<>();
            File usercache = new File(serverDirectory, "usercache.json");
            if (!usercache.exists()) {
                Platform.runLater(() -> {
                    if (playerStatsStatus != null)
                        playerStatsStatus.setText("usercache.json not found (start server once)");
                });
                return;
            }
            try {
                String json = Files.readString(usercache.toPath());
                Matcher nm = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                Matcher um = Pattern.compile("\"uuid\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                List<String> names = new ArrayList<>(), uuids = new ArrayList<>();
                while (nm.find()) names.add(nm.group(1));
                while (um.find()) uuids.add(um.group(1));

                File statsDir = new File(serverDirectory, "world/stats");
                for (int i = 0; i < Math.min(names.size(), uuids.size()); i++) {
                    String name = names.get(i), uuid = uuids.get(i);
                    String playTime = "—", deaths = "—", kills = "—", dist = "—";
                    File sf = new File(statsDir, uuid + ".json");
                    if (sf.exists()) {
                        String s = Files.readString(sf.toPath());
                        playTime = ticksToTime(statVal(s, "play_time", "play_one_minute"));
                        deaths   = String.valueOf(statVal(s, "deaths"));
                        kills    = String.valueOf(statVal(s, "player_kills"));
                        long cm  = statVal(s,"walk_one_cm") + statVal(s,"sprint_one_cm")
                                 + statVal(s,"swim_one_cm")  + statVal(s,"fly_one_cm");
                        if (cm > 0) dist = String.format("%.2f", cm / 100_000.0);
                    }
                    rows.add(FXCollections.observableArrayList(
                        name, playTime, deaths, kills, dist, uuid));
                }
                Platform.runLater(() -> {
                    playerStatsData.setAll(rows);
                    if (playerStatsStatus != null)
                        playerStatsStatus.setText(rows.size() + " player(s)");
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (playerStatsStatus != null) playerStatsStatus.setText("Error: " + e.getMessage());
                });
            }
        });
        t.setDaemon(true); t.start();
    }

    private long statVal(String json, String... keys) {
        for (String key : keys) {
            Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
            if (m.find()) try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String ticksToTime(long ticks) {
        if (ticks == 0) return "—";
        long secs = ticks / 20, h = secs / 3600, m = (secs % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m > 0 ? m + "m" : secs + "s";
    }

    // ════════════════════════════════════════════════════════════════
    // WINDOW MANAGEMENT (undecorated)
    // ════════════════════════════════════════════════════════════════

    public void setStage(Stage s) { this.stage = s; }

    @FXML private void handleWindowDrag(MouseEvent e) {
        if (stage != null) { stage.setX(e.getScreenX() + dragX); stage.setY(e.getScreenY() + dragY); }
    }
    @FXML private void handleWindowPress(MouseEvent e) {
        if (stage != null) { dragX = stage.getX() - e.getScreenX(); dragY = stage.getY() - e.getScreenY(); }
    }
    @FXML private void handleMinimize()  { if (stage != null) stage.setIconified(true); }
    @FXML private void handleMaximize()  { if (stage != null) stage.setMaximized(!stage.isMaximized()); }
    @FXML private void handleWinClose()  { handleWindowClose(); }

    private void showMainWindow() {
        if (stage != null) { stage.show(); stage.toFront(); return; }
        Stage s = (Stage) startButton.getScene().getWindow();
        s.show(); s.toFront();
    }

    public void handleWindowClose() {
        if (trayIcon != null) {
            if (stage != null) { stage.hide(); } else {
                ((Stage) startButton.getScene().getWindow()).hide();
            }
            trayIcon.displayMessage("Minecraft Launcher",
                "Still running in the background. Right-click the tray icon to quit.",
                java.awt.TrayIcon.MessageType.INFO);
        } else { cleanup(); Platform.exit(); System.exit(0); }
    }

    public void cleanup() {
        saveCurrentProfile();
        stopBackupScheduler(); stopRestartScheduler();
        if (uptimeClock    != null) uptimeClock.stop();
        if (resourceMonitor!= null) resourceMonitor.stop();
        if (rconClient     != null) rconClient.disconnect();
        if (trayIcon       != null) java.awt.SystemTray.getSystemTray().remove(trayIcon);
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS TOGGLE
    // ════════════════════════════════════════════════════════════════

    @FXML
    private void toggleSettings() {
        boolean goToSettings = consoleAreaView.isVisible();
        consoleAreaView.setVisible(!goToSettings);
        consoleAreaView.setManaged(!goToSettings);
        settingsPane.setVisible(goToSettings);
        settingsPane.setManaged(goToSettings);
        settingsButton.setText(goToSettings ? "🔙 Console" : "⚙ Settings");
        if (goToSettings) {
            loadServerProperties();
            refreshBackupList();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → SERVER TAB
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleSelectServerDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Minecraft Server Folder");
        File sel = dc.showDialog(serverPathField.getScene().getWindow());
        if (sel != null) {
            serverDirectory = sel;
            serverPathField.setText(sel.getAbsolutePath());
            appendSystemLog("> Server folder: " + sel.getAbsolutePath());
            String jar = serverJarField.getText().trim();
            if (jar.isEmpty()) jar = "server.jar";
            if (!new File(sel, jar).exists())
                appendSystemLog("[WARNING] '" + jar + "' not found in folder!");
            loadServerProperties();
            loadWhitelist();
            loadBanList();
        }
    }

    @FXML private void handleClearServerDirectory() {
        serverDirectory = null; serverPathField.clear();
        currentProps = null; buildPropertiesForm();
        appendSystemLog("> Server directory cleared.");
    }

    @FXML private void resetServerJar() { serverJarField.setText("server.jar"); }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → PERFORMANCE TAB
    // ════════════════════════════════════════════════════════════════

    @FXML
    private void handleOptimizedFlagsToggle() {
        boolean enabled = optimizedFlagsCheck.isSelected();
        prefs.putBoolean("optimizedFlags", enabled);
        if (enabled)
            appendSystemLog("> Aikar's optimized JVM flags ENABLED — takes effect on next server start.");
        else
            appendSystemLog("> Standard JVM flags (-Xmx/-Xms only).");
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → WORLD CONFIG TAB (PREMIUM PROPERTIES EDITOR)
    // ════════════════════════════════════════════════════════════════

    private void loadServerProperties() {
        if (serverDirectory == null) {
            currentProps = null;
            buildPropertiesForm();
            propertiesEditor.setText("# Please select a server directory.");
            return;
        }
        File f = new File(serverDirectory, "server.properties");
        if (!f.exists()) {
            currentProps = null;
            buildPropertiesForm();
            propertiesEditor.setText("# server.properties not found — start the server once to generate it.");
            return;
        }
        try {
            String content = Files.readString(f.toPath());
            propertiesEditor.setText(content);
            currentProps = parsePropertiesContent(content);
            buildPropertiesForm();
        } catch (IOException e) {
            propertiesEditor.setText("# Error reading file: " + e.getMessage());
            currentProps = null;
            buildPropertiesForm();
        }
    }

    private Map<String, String> parsePropertiesContent(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1));
        }
        return map;
    }

    private void buildPropertiesForm() {
        propertiesFormContainer.getChildren().clear();
        propControls.clear();

        if (currentProps == null) {
            Label msg = new Label("Select a server directory to edit properties.");
            msg.setStyle("-fx-text-fill:#888888; -fx-padding:24;");
            propertiesFormContainer.getChildren().add(msg);
            return;
        }

        int rowIdx = 0;
        for (String[] def : PROP_DEFS) {
            String key  = def[0];
            String label = def[1];
            String type = def[2];
            String defaultVal = def[3];

            if (key.isEmpty()) {
                // Section header
                HBox header = new HBox(10);
                header.setPadding(new Insets(rowIdx == 0 ? 10 : 18, 14, 6, 14));
                header.setStyle("-fx-background-color:#222222;");
                header.setAlignment(Pos.CENTER_LEFT);

                Label hdr = new Label(label);
                hdr.setStyle("-fx-text-fill:#00ff41; -fx-font-weight:bold; -fx-font-size:10;");

                Region sep = new Region();
                sep.setStyle("-fx-background-color:#333333; -fx-min-height:1; -fx-max-height:1;");
                HBox.setHgrow(sep, Priority.ALWAYS);
                HBox.setMargin(sep, new Insets(0, 0, 0, 6));

                header.getChildren().addAll(hdr, sep);
                propertiesFormContainer.getChildren().add(header);
                continue;
            }

            String currentVal = currentProps.getOrDefault(key, defaultVal);

            Control control;
            if (type.equals("bool")) {
                CheckBox cb = new CheckBox();
                cb.setSelected("true".equalsIgnoreCase(currentVal));
                cb.setStyle("-fx-background-color:transparent;");
                control = cb;
            } else if (type.startsWith("choice:")) {
                String[] choices = type.substring(7).split(",");
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(choices);
                combo.setValue(currentVal);
                if (combo.getValue() == null && choices.length > 0) combo.setValue(choices[0]);
                combo.setPrefWidth(220);
                combo.setStyle("-fx-background-color:#2d2d2d; -fx-border-color:#555; " +
                               "-fx-background-radius:6; -fx-border-radius:6;");
                control = combo;
            } else {
                // "text" or "int"
                TextField tf = new TextField(currentVal);
                tf.setStyle("-fx-background-color:#2d2d2d; -fx-text-fill:white; " +
                            "-fx-border-color:#555; -fx-background-radius:6; -fx-border-radius:6;");
                tf.setPrefWidth(type.equals("int") ? 100 : 280);
                tf.setMaxWidth(type.equals("int") ? 100 : 280);
                control = tf;
            }

            propControls.put(key, control);

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 14, 8, 14));
            row.setStyle("-fx-background-color:" + ((rowIdx % 2 == 0) ? "#1a1a1a" : "#1d1d1d") + ";");

            Label nameLabel = new Label(label);
            nameLabel.setStyle("-fx-text-fill:#cccccc; -fx-min-width:270; -fx-pref-width:270;");

            row.getChildren().addAll(nameLabel, control);
            propertiesFormContainer.getChildren().add(row);
            rowIdx++;
        }
    }

    @FXML
    private void handleSavePropertiesForm() {
        if (serverDirectory == null) return;
        if (rawEditToggle != null && rawEditToggle.isSelected()) {
            // Raw mode: save TextArea directly
            try {
                Files.writeString(new File(serverDirectory, "server.properties").toPath(),
                    propertiesEditor.getText());
                currentProps = parsePropertiesContent(propertiesEditor.getText());
                appendSystemLog("> Properties saved (raw).");
            } catch (IOException e) {
                appendSystemLog("> Error saving: " + e.getMessage());
            }
        } else {
            // Form mode
            savePropertiesFromForm();
        }
    }

    private void savePropertiesFromForm() {
        if (serverDirectory == null || currentProps == null) return;
        for (Map.Entry<String, Control> entry : propControls.entrySet())
            currentProps.put(entry.getKey(), getControlValue(entry.getValue()));

        File f = new File(serverDirectory, "server.properties");
        try {
            if (!f.exists()) {
                StringBuilder sb = new StringBuilder("#Minecraft server properties\n");
                for (Map.Entry<String, String> e : currentProps.entrySet())
                    sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
                Files.writeString(f.toPath(), sb.toString());
            } else {
                List<String> lines = Files.readAllLines(f.toPath());
                List<String> out = new ArrayList<>();
                Set<String> updated = new HashSet<>();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) { out.add(line); continue; }
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String k = trimmed.substring(0, eq).trim();
                        String nv = currentProps.get(k);
                        out.add(nv != null ? k + "=" + nv : line);
                        if (nv != null) updated.add(k);
                    } else out.add(line);
                }
                Files.writeString(f.toPath(), String.join("\n", out) + "\n");
            }
            appendSystemLog("> Properties saved.");
        } catch (IOException e) {
            appendSystemLog("> Error saving properties: " + e.getMessage());
        }
    }

    private String getControlValue(Control ctrl) {
        if (ctrl instanceof CheckBox cb) return cb.isSelected() ? "true" : "false";
        if (ctrl instanceof ComboBox<?> cb) return cb.getValue() != null ? cb.getValue().toString() : "";
        if (ctrl instanceof TextField tf) return tf.getText();
        return "";
    }

    @FXML
    private void handleReloadProperties() {
        loadServerProperties();
        appendSystemLog("> Properties reloaded from disk.");
    }

    @FXML
    private void toggleRawEdit() {
        boolean raw = rawEditToggle.isSelected();
        propertiesFormScroll.setVisible(!raw);
        propertiesFormScroll.setManaged(!raw);
        propertiesEditor.setVisible(raw);
        propertiesEditor.setManaged(raw);
        if (raw && serverDirectory != null) {
            File f = new File(serverDirectory, "server.properties");
            if (f.exists()) {
                try { propertiesEditor.setText(Files.readString(f.toPath())); }
                catch (IOException e) { propertiesEditor.setText("# Error: " + e.getMessage()); }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → BACKUP TAB
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleSelectBackupDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Backup Target Folder");
        File sel = dc.showDialog(backupPathField.getScene().getWindow());
        if (sel != null) {
            backupDirectory = sel;
            backupPathField.setText(sel.getAbsolutePath());
            appendSystemLog("> Backup directory: " + sel.getAbsolutePath());
            refreshBackupList();
        }
    }

    @FXML private void handleClearBackupDirectory() {
        backupDirectory = null; backupPathField.clear();
        if (autoBackupCheck.isSelected()) { autoBackupCheck.setSelected(false); stopBackupScheduler(); }
        if (backupFilesList != null) backupFilesList.getItems().clear();
        appendSystemLog("> Backup directory cleared.");
    }

    @FXML private void handleBackupToggle() {
        if (backupDirectory == null) {
            appendSystemLog("> Error: Set a backup folder first!"); autoBackupCheck.setSelected(false); return;
        }
        if (autoBackupCheck.isSelected()) { appendSystemLog("> Auto-Backups ENABLED."); startBackupScheduler(); }
        else                               { appendSystemLog("> Auto-Backups DISABLED."); stopBackupScheduler(); }
    }

    @FXML private void forceBackup() {
        if (serverDirectory == null || backupDirectory == null) {
            appendSystemLog("> Configure both directories first."); return;
        }
        appendSystemLog("> Manual backup triggered...");
        Thread t = new Thread(this::triggerBackgroundBackup); t.setDaemon(true); t.start();
    }

    @FXML private void openBackupFolder() {
        if (backupDirectory == null) { appendSystemLog("> No backup directory configured."); return; }
        try { new ProcessBuilder("explorer.exe", backupDirectory.getAbsolutePath()).start(); }
        catch (IOException e) { appendSystemLog("> Could not open folder: " + e.getMessage()); }
    }

    @FXML private void refreshBackupList() {
        if (backupFilesList == null) return;
        backupFilesList.getItems().clear();
        if (backupDirectory == null || !backupDirectory.exists()) return;
        File[] zips = backupDirectory.listFiles((d, n) -> n.endsWith(".zip"));
        if (zips == null || zips.length == 0) return;
        Arrays.sort(zips, Comparator.comparing(File::getName).reversed());
        for (File z : zips) backupFilesList.getItems().add(z.getName());
    }

    @FXML private void handleRestoreBackup() {
        String selected = backupFilesList.getSelectionModel().getSelectedItem();
        if (selected == null) { appendSystemLog("> Select a backup ZIP first."); return; }
        if (backupDirectory == null || serverDirectory == null) {
            appendSystemLog("> Configure both directories first."); return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("Restore from: " + selected);
        confirm.setContentText(
            "The current world/ folder will be renamed (not deleted).\n" +
            "The server will be stopped if it is running.\n\nProceed?");
        confirm.showAndWait().filter(b -> b == ButtonType.OK)
            .ifPresent(b -> performRestore(new File(backupDirectory, selected)));
    }

    private void performRestore(File zipFile) {
        Thread t = new Thread(() -> {
            try {
                Platform.runLater(() -> appendSystemLog("> Restore starting: " + zipFile.getName()));
                if (serverProcess != null && serverProcess.isAlive()) {
                    Platform.runLater(() -> { appendSystemLog("> Stopping server..."); sendServerCommand("stop"); });
                    serverProcess.waitFor(30, TimeUnit.SECONDS);
                }
                File currentWorld = new File(serverDirectory, "world");
                if (currentWorld.exists()) {
                    String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                    File renamed = new File(serverDirectory, "world_pre_restore_" + ts);
                    if (currentWorld.renameTo(renamed))
                        Platform.runLater(() -> appendSystemLog("> Renamed existing world to: " + renamed.getName()));
                }
                Platform.runLater(() -> appendSystemLog("> Extracting backup…"));
                extractZip(zipFile, serverDirectory);
                Platform.runLater(() -> appendSystemLog("> Restore complete from: " + zipFile.getName()));
            } catch (Exception e) {
                Platform.runLater(() -> appendSystemLog("> Restore FAILED: " + e.getMessage()));
            }
        });
        t.setDaemon(true); t.start();
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        String destPath = destDir.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (!out.getCanonicalPath().startsWith(destPath + File.separator) &&
                    !out.getCanonicalPath().equals(destPath))
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                if (entry.isDirectory()) { out.mkdirs(); }
                else {
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // Backup engine

    private void startBackupScheduler() {
        stopBackupScheduler();
        backupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r); t.setDaemon(true); return t;
        });
        backupScheduler.scheduleAtFixedRate(this::triggerBackgroundBackup, 24, 24, TimeUnit.HOURS);
    }
    private void stopBackupScheduler() {
        if (backupScheduler != null && !backupScheduler.isShutdown()) backupScheduler.shutdownNow();
    }

    private void triggerBackgroundBackup() {
        if (serverDirectory == null || backupDirectory == null) return;
        if (serverProcess != null && serverProcess.isAlive()) {
            sendServerCommand("save-all");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }
        Platform.runLater(() -> appendSystemLog("> Zipping world folder..."));
        File worldDir = new File(serverDirectory, "world");
        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File zipFile = new File(backupDirectory, "world_backup_" + ts + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipDirectory(worldDir, worldDir.getName(), zos);
            Platform.runLater(() -> { appendSystemLog("> Backup complete: " + zipFile.getName()); refreshBackupList(); });
            cleanOldBackups();
        } catch (Exception e) {
            Platform.runLater(() -> appendSystemLog("> Backup FAILED: " + e.getMessage()));
        }
    }

    private void cleanOldBackups() {
        int max;
        try { max = Integer.parseInt(prefs.node("profiles").node(currentProfile).get("maxBackups", "5")); }
        catch (NumberFormatException e) { max = 5; }
        if (max <= 0) return;
        File[] files = backupDirectory.listFiles((d, n) -> n.startsWith("world_backup_") && n.endsWith(".zip"));
        if (files == null || files.length <= max) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (int i = 0; i < files.length - max; i++) {
            final File f = files[i];
            if (f.delete()) Platform.runLater(() -> appendSystemLog("> Removed old backup: " + f.getName()));
        }
    }

    private void zipDirectory(File folder, String path, ZipOutputStream zos) throws IOException {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) zipDirectory(f, path + "/" + f.getName(), zos);
            else {
                zos.putNextEntry(new ZipEntry(path + "/" + f.getName()));
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → AUTOMATION TAB
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleCrashRestartToggle() {
        prefs.putBoolean("crashRestart", crashRestartCheck.isSelected());
        prefs.put("crashRestartDelay", crashRestartDelayField.getText().trim());
    }

    @FXML private void handleScheduledRestartToggle() {
        boolean enabled = scheduledRestartCheck.isSelected();
        prefs.putBoolean("scheduledRestart", enabled);
        prefs.put("scheduledRestartTime", scheduledRestartField.getText().trim());
        if (enabled) startRestartScheduler();
        else { stopRestartScheduler(); restartCountdownActive = false; }
    }

    private void startRestartScheduler() {
        stopRestartScheduler();
        restartScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r); t.setDaemon(true); return t;
        });
        restartScheduler.scheduleAtFixedRate(this::checkScheduledRestart, 0, 1, TimeUnit.MINUTES);
    }
    private void stopRestartScheduler() {
        if (restartScheduler != null && !restartScheduler.isShutdown()) restartScheduler.shutdownNow();
    }

    private void checkScheduledRestart() {
        try {
            LocalTime target = LocalTime.parse(prefs.get("scheduledRestartTime", "04:00"),
                DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime now = LocalTime.now();
            if (now.getHour() == target.getHour() && now.getMinute() == target.getMinute()) {
                if (!restartCountdownActive) {
                    restartCountdownActive = true;
                    Platform.runLater(() -> {
                        appendSystemLog("> Scheduled restart in 1 minute...");
                        sendServerCommand("say [Server] Restarting in 1 minute...");
                    });
                } else {
                    restartCountdownActive = false;
                    Platform.runLater(() -> {
                        if (serverProcess != null && serverProcess.isAlive()) {
                            appendSystemLog("> Executing scheduled restart.");
                            handleRestartServer();
                        }
                    });
                }
            } else restartCountdownActive = false;
        } catch (DateTimeParseException ignored) {}
    }

    // ════════════════════════════════════════════════════════════════
    // CORE SERVER CONTROLS
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleStartServer() {
        if (serverDirectory == null) { appendSystemLog("> Error: No server directory selected."); return; }
        currentMemGB   = memoryField.getText().trim();
        if (currentMemGB.isEmpty() || !currentMemGB.matches("\\d+")) currentMemGB = "2";
        currentJarName = serverJarField.getText().trim();
        if (currentJarName.isEmpty()) currentJarName = "server.jar";
        currentUseOptimizedFlags = optimizedFlagsCheck.isSelected();

        appendSystemLog("> Starting " + currentJarName + " with " + currentMemGB + "GB"
            + (currentUseOptimizedFlags ? " + Aikar's flags" : "") + "...");
        startButton.setDisable(true); stopButton.setDisable(false);
        restartButton.setDisable(false); commandInput.setDisable(false);
        statusLabel.setText("RUNNING"); statusLabel.setStyle("-fx-text-fill:#00ff41;");
        serverStartMillis = System.currentTimeMillis(); uptimeClock.play();
        lastParsedTps = 20.0; startMonitorTimeline();
        onlinePlayers.clear(); updateCounterUI();
        Thread t = new Thread(this::runServerProcess); t.setDaemon(true); t.start();
    }

    @FXML private void handleStopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            appendSystemLog("> Sending shutdown command...");
            startButton.setDisable(true); stopButton.setDisable(true);
            restartButton.setDisable(true); commandInput.setDisable(true);
            statusLabel.setText(isRestarting ? "RESTARTING" : "STOPPING");
            statusLabel.setStyle("-fx-text-fill:#ffcc00;");
            sendServerCommand("stop");
        }
    }

    @FXML private void handleRestartServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            appendSystemLog("> Initiating restart..."); isRestarting = true; handleStopServer();
        }
    }

    private void runServerProcess() {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-Xmx" + currentMemGB + "G");
        cmd.add("-Xms" + currentMemGB + "G");
        if (currentUseOptimizedFlags) Collections.addAll(cmd, AIKARS_FLAGS);
        cmd.add("-jar"); cmd.add(currentJarName); cmd.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(serverDirectory);
        pb.redirectErrorStream(true);
        try {
            serverProcess = pb.start();
            serverInput   = serverProcess.getOutputStream();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (serverProcess != null && serverProcess.isAlive()) serverProcess.destroy();
            }));

            List<String> recentLines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                final String logLine = line;
                recentLines.add(logLine);
                if (recentLines.size() > 30) recentLines.remove(0);
                Platform.runLater(() -> {
                    addLogLine(logLine);
                    if (logLine.contains(": <")) chatLogs.add(logLine);
                    if (logLine.contains(" joined the game")) {
                        String name = extractPlayerName(logLine, " joined the game");
                        if (name != null && !onlinePlayers.contains(name)) { onlinePlayers.add(name); updateCounterUI(); }
                    } else if (logLine.contains(" left the game")) {
                        String name = extractPlayerName(logLine, " left the game");
                        if (name != null) { onlinePlayers.remove(name); updateCounterUI(); }
                    }
                });
            }

            serverProcess.waitFor();
            final int exitCode = serverProcess.exitValue();
            final List<String> snapshot = new ArrayList<>(recentLines);
            Platform.runLater(() -> {
                uptimeClock.stop(); uptimeLabel.setText(""); stopMonitorTimeline();
                appendSystemLog("> Server process exited (code " + exitCode + ").");
                onlinePlayers.clear(); updateCounterUI();
                if (isRestarting) { isRestarting = false; handleStartServer(); return; }
                boolean graceful = snapshot.stream().anyMatch(l ->
                    l.contains("Stopping server") || l.contains("Saving worlds") || l.contains("Goodbye"));
                boolean wasCrash = !graceful && exitCode != 0;
                if (wasCrash && crashRestartCheck.isSelected()) {
                    int delay;
                    try { delay = Integer.parseInt(crashRestartDelayField.getText().trim()); }
                    catch (NumberFormatException ex) { delay = 10; }
                    final int d = delay;
                    appendSystemLog("> Crash detected! Auto-restarting in " + d + "s...");
                    statusLabel.setText("CRASHED"); statusLabel.setStyle("-fx-text-fill:#ff4c4c;");
                    Thread t = new Thread(() -> {
                        try { Thread.sleep(d * 1000L); } catch (InterruptedException ignored) {}
                        Platform.runLater(this::handleStartServer);
                    }); t.setDaemon(true); t.start();
                } else {
                    startButton.setDisable(false); stopButton.setDisable(true);
                    restartButton.setDisable(true); commandInput.setDisable(true);
                    statusLabel.setText("OFFLINE"); statusLabel.setStyle("-fx-text-fill:#ff4c4c;");
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> appendSystemLog("> Failed to start server: " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WHITELIST & BAN LIST
    // ════════════════════════════════════════════════════════════════

    private List<String> readPlayerJsonFile(String filename) {
        List<String> names = new ArrayList<>();
        if (serverDirectory == null) return names;
        File f = new File(serverDirectory, filename);
        if (!f.exists()) return names;
        try {
            Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(Files.readString(f.toPath()));
            while (m.find()) names.add(m.group(1));
        } catch (IOException ignored) {}
        return names;
    }

    @FXML private void loadWhitelist()            { whitelistView.getItems().setAll(readPlayerJsonFile("whitelist.json")); }
    @FXML private void loadBanList()              { banListView.getItems().setAll(readPlayerJsonFile("banned-players.json")); }

    @FXML private void handleAddToWhitelist() {
        String name = whitelistAddField.getText().trim(); if (name.isEmpty()) return;
        sendServerCommand("whitelist add " + name); appendSystemLog("> Whitelisted: " + name);
        whitelistAddField.clear(); delayedRefresh(this::loadWhitelist);
    }
    @FXML private void handleRemoveFromWhitelist() {
        String sel = whitelistView.getSelectionModel().getSelectedItem();
        if (sel == null) { appendSystemLog("> Select a player first."); return; }
        sendServerCommand("whitelist remove " + sel); appendSystemLog("> Removed: " + sel);
        delayedRefresh(this::loadWhitelist);
    }
    @FXML private void handleAddBan() {
        String name = banAddField.getText().trim(); if (name.isEmpty()) return;
        sendServerCommand("ban " + name); appendSystemLog("> Banned: " + name);
        banAddField.clear(); delayedRefresh(this::loadBanList);
    }
    @FXML private void handlePardon() {
        String sel = banListView.getSelectionModel().getSelectedItem();
        if (sel == null) { appendSystemLog("> Select a player first."); return; }
        sendServerCommand("pardon " + sel); appendSystemLog("> Pardoned: " + sel);
        delayedRefresh(this::loadBanList);
    }

    private void delayedRefresh(Runnable action) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            Platform.runLater(action);
        }); t.setDaemon(true); t.start();
    }

    // ════════════════════════════════════════════════════════════════
    // RCON
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleRconConnect() {
        if (rconClient != null && rconClient.isConnected()) {
            rconClient.disconnect(); rconClient = null;
            rconConnectButton.setText("Connect");
            rconStatusLabel.setText("Disconnected"); rconStatusLabel.setStyle("-fx-text-fill:#ff4c4c;");
            rconOutputList.getItems().add("> Disconnected."); return;
        }
        String host = rconHostField.getText().trim(); String pass = rconPasswordField.getText();
        int port;
        try { port = Integer.parseInt(rconPortField.getText().trim()); } catch (NumberFormatException e) { port = 25575; }
        prefs.put("rconHost", host); prefs.put("rconPort", String.valueOf(port));
        final String fHost = host; final int fPort = port;
        Thread t = new Thread(() -> {
            try {
                RconClient client = new RconClient();
                client.connect(fHost, fPort, pass); rconClient = client;
                Platform.runLater(() -> {
                    rconConnectButton.setText("Disconnect");
                    rconStatusLabel.setText("Connected — " + fHost + ":" + fPort);
                    rconStatusLabel.setStyle("-fx-text-fill:#00ff41;");
                    rconOutputList.getItems().add("> Connected to " + fHost + ":" + fPort);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    rconStatusLabel.setText("Failed: " + ex.getMessage()); rconStatusLabel.setStyle("-fx-text-fill:#ff4c4c;");
                    rconOutputList.getItems().add("> " + ex.getMessage());
                });
            }
        }); t.setDaemon(true); t.start();
    }

    @FXML private void handleRconCommand() {
        String cmd = rconCommandField.getText().trim(); if (cmd.isEmpty()) return;
        rconCommandField.clear();
        if (rconClient == null || !rconClient.isConnected()) { rconOutputList.getItems().add("> Not connected."); return; }
        rconOutputList.getItems().add("> " + cmd);
        Thread t = new Thread(() -> {
            try {
                String resp = rconClient.sendCommand(cmd);
                Platform.runLater(() -> {
                    if (!resp.isBlank()) rconOutputList.getItems().add(resp);
                    rconOutputList.scrollTo(rconOutputList.getItems().size() - 1);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    rconOutputList.getItems().add("> Error: " + ex.getMessage());
                    rconClient = null; rconConnectButton.setText("Connect");
                    rconStatusLabel.setText("Disconnected (error)"); rconStatusLabel.setStyle("-fx-text-fill:#ff4c4c;");
                });
            }
        }); t.setDaemon(true); t.start();
    }

    // ════════════════════════════════════════════════════════════════
    // SMART LOG ANALYZER
    // ════════════════════════════════════════════════════════════════

    private void checkDiagnostics(String line) {
        if (diagnosticsArea == null) return;
        for (String[] rule : DIAGNOSTIC_RULES) {
            if (line.contains(rule[0])) {
                String summary = "[" + rule[1] + "] " + rule[2];
                if (loggedDiagnostics.add(summary)) {
                    String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    diagnosticsArea.appendText(ts + "  " + summary + "\n         → " + rule[3] + "\n\n");
                }
                break;
            }
        }
    }

    @FXML private void clearDiagnostics() {
        diagnosticsArea.clear(); loggedDiagnostics.clear(); appendSystemLog("> Diagnostics cleared.");
    }

    // ════════════════════════════════════════════════════════════════
    // JAR DOWNLOADER
    // ════════════════════════════════════════════════════════════════

    @FXML private void showJarDownloader() {
        if (serverDirectory == null) { appendSystemLog("> Set a server directory first."); return; }
        Stage dialog = new Stage();
        dialog.setTitle("Download Server JAR");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(startButton.getScene().getWindow());
        dialog.setResizable(false);

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton paperRb  = styledRadio("Paper (recommended)", typeGroup);
        RadioButton vanillaRb = styledRadio("Vanilla", typeGroup);
        RadioButton fabricRb  = styledRadio("Fabric", typeGroup);
        paperRb.setSelected(true);
        HBox typeBox = new HBox(18, paperRb, vanillaRb, fabricRb);
        typeBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Click 'Load Versions' first…");
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setStyle("-fx-background-color:#2d2d2d; -fx-text-fill:#e0e0e0;");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE); progressBar.setVisible(false);

        Label statusLbl = new Label("Select a type, load versions, then download.");
        statusLbl.setStyle("-fx-text-fill:#888888; -fx-font-size:11;"); statusLbl.setWrapText(true);

        Button loadBtn = new Button("Load Versions"); Button dlBtn = new Button("⬇ Download");
        dlBtn.setDisable(true);
        loadBtn.setStyle("-fx-background-color:#37474f; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:6; -fx-cursor:hand;");
        dlBtn.setStyle("-fx-background-color:#2e7d32; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:6; -fx-cursor:hand;");

        loadBtn.setOnAction(e -> {
            String type = paperRb.isSelected() ? "paper" : vanillaRb.isSelected() ? "vanilla" : "fabric";
            statusLbl.setText("Fetching version list…"); statusLbl.setStyle("-fx-text-fill:#888888;");
            loadBtn.setDisable(true); dlBtn.setDisable(true); versionCombo.getItems().clear();
            Thread t = new Thread(() -> {
                try {
                    List<String> vers = fetchVersions(type);
                    Platform.runLater(() -> {
                        versionCombo.getItems().setAll(vers);
                        if (!vers.isEmpty()) versionCombo.setValue(vers.get(0));
                        statusLbl.setText("Select a version, then click Download.");
                        statusLbl.setStyle("-fx-text-fill:#00ff41;"); dlBtn.setDisable(false); loadBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLbl.setText("Error: " + ex.getMessage()); statusLbl.setStyle("-fx-text-fill:#ff4c4c;");
                        loadBtn.setDisable(false);
                    });
                }
            }); t.setDaemon(true); t.start();
        });

        dlBtn.setOnAction(e -> {
            String version = versionCombo.getValue(); if (version == null || version.isBlank()) return;
            String type = paperRb.isSelected() ? "paper" : vanillaRb.isSelected() ? "vanilla" : "fabric";
            progressBar.setProgress(-1); progressBar.setVisible(true);
            dlBtn.setDisable(true); loadBtn.setDisable(true);
            statusLbl.setText("Downloading…"); statusLbl.setStyle("-fx-text-fill:#888888;");
            Thread t = new Thread(() -> {
                try {
                    downloadServerJar(type, version, serverDirectory,
                        p -> Platform.runLater(() -> progressBar.setProgress(p)),
                        filename -> Platform.runLater(() -> {
                            progressBar.setProgress(1.0);
                            statusLbl.setText("Downloaded: " + filename); statusLbl.setStyle("-fx-text-fill:#00ff41;");
                            dlBtn.setDisable(false); loadBtn.setDisable(false);
                            serverJarField.setText(filename);
                            appendSystemLog("> Downloaded: " + filename);
                        }));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLbl.setText("Download failed: " + ex.getMessage()); statusLbl.setStyle("-fx-text-fill:#ff4c4c;");
                        progressBar.setVisible(false); dlBtn.setDisable(false); loadBtn.setDisable(false);
                    });
                }
            }); t.setDaemon(true); t.start();
        });

        VBox root = new VBox(14, styledSectionLabel("Download Server JAR"),
            styledDimLabel("Server Type:"), typeBox,
            styledDimLabel("Version:"), versionCombo,
            statusLbl, progressBar, new HBox(10, loadBtn, dlBtn));
        root.setPadding(new Insets(22)); root.setStyle("-fx-background-color:#1e1e1e;"); root.setPrefWidth(480);
        dialog.setScene(new Scene(root)); dialog.show();
    }

    private List<String> fetchVersions(String type) throws Exception {
        List<String> vers = new ArrayList<>();
        switch (type) {
            case "paper" -> {
                String json = fetchJson("https://api.papermc.io/v2/projects/paper");
                int start = json.indexOf("\"versions\"");
                if (start == -1) throw new Exception("No versions in Paper API response.");
                String arr = json.substring(json.indexOf('[', start) + 1, json.indexOf(']', json.indexOf('[', start)));
                Matcher m = Pattern.compile("\"([\\d.]+)\"").matcher(arr);
                while (m.find()) vers.add(m.group(1));
                Collections.reverse(vers);
            }
            case "vanilla" -> {
                String json = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                int pos = 0;
                while (pos < json.length()) {
                    int ob = json.indexOf('{', pos); if (ob == -1) break;
                    int oe = json.indexOf('}', ob);  if (oe == -1) break;
                    String obj = json.substring(ob, oe + 1);
                    if (obj.contains("\"type\":\"release\"") || obj.contains("\"type\": \"release\"")) {
                        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
                        if (m.find()) vers.add(m.group(1));
                    }
                    pos = oe + 1;
                }
            }
            case "fabric" -> {
                String json = fetchJson("https://meta.fabricmc.net/v2/versions/game");
                int pos = 0;
                while (pos < json.length()) {
                    int ob = json.indexOf('{', pos); if (ob == -1) break;
                    int oe = json.indexOf('}', ob);  if (oe == -1) break;
                    String obj = json.substring(ob, oe + 1);
                    if (obj.contains("\"stable\":true") || obj.contains("\"stable\": true")) {
                        Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
                        if (m.find()) vers.add(m.group(1));
                    }
                    pos = oe + 1;
                }
            }
        }
        if (vers.isEmpty()) throw new Exception("No versions from API. Check internet connection.");
        return vers;
    }

    private void downloadServerJar(String type, String version, File destDir,
                                   Consumer<Double> onProgress, Consumer<String> onDone) throws Exception {
        String downloadUrl, filename;
        switch (type) {
            case "paper" -> {
                String buildsJson = fetchJson("https://api.papermc.io/v2/projects/paper/versions/" + version + "/builds");
                Matcher m = Pattern.compile("\"build\"\\s*:\\s*(\\d+)").matcher(buildsJson);
                int latestBuild = 0;
                while (m.find()) latestBuild = Integer.parseInt(m.group(1));
                if (latestBuild == 0) throw new Exception("No builds found for Paper " + version);
                filename    = "paper-" + version + "-" + latestBuild + ".jar";
                downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/" + version
                            + "/builds/" + latestBuild + "/downloads/" + filename;
            }
            case "vanilla" -> {
                String manifest = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                String versionUrl = null;
                int pos = 0;
                while (pos < manifest.length()) {
                    int ob = manifest.indexOf('{', pos); if (ob == -1) break;
                    int oe = manifest.indexOf('}', ob);  if (oe == -1) break;
                    String obj = manifest.substring(ob, oe + 1);
                    if (obj.contains("\"id\":\"" + version + "\"") || obj.contains("\"id\": \"" + version + "\"")) {
                        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
                        if (m.find()) { versionUrl = m.group(1); break; }
                    }
                    pos = oe + 1;
                }
                if (versionUrl == null) throw new Exception("Version " + version + " not in manifest.");
                String versionJson = fetchJson(versionUrl);
                Matcher sm = Pattern.compile("\"server\"\\s*:\\s*\\{").matcher(versionJson);
                if (!sm.find()) throw new Exception("No server JAR for Vanilla " + version);
                int bs = versionJson.indexOf('{', sm.start()), be = versionJson.indexOf('}', bs);
                Matcher um = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(versionJson.substring(bs, be + 1));
                if (!um.find()) throw new Exception("Could not parse server URL for " + version);
                downloadUrl = um.group(1); filename = "minecraft_server." + version + ".jar";
            }
            case "fabric" -> {
                Matcher lm = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(fetchJson("https://meta.fabricmc.net/v2/versions/loader"));
                String loaderVer = lm.find() ? lm.group(1) : "0.16.0";
                Matcher im = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(fetchJson("https://meta.fabricmc.net/v2/versions/installer"));
                String installerVer = im.find() ? im.group(1) : "1.0.1";
                downloadUrl = "https://meta.fabricmc.net/v2/versions/loader/" + version
                            + "/" + loaderVer + "/" + installerVer + "/server/jar";
                filename = "fabric-server-mc." + version + "-loader." + loaderVer + ".jar";
            }
            default -> throw new Exception("Unknown server type: " + type);
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req   = HttpRequest.newBuilder().uri(URI.create(downloadUrl))
                              .header("User-Agent", "MinecraftServerLauncher/1.0").build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode() + " for " + filename);

        long contentLength = resp.headers().firstValueAsLong("content-length").orElse(-1);
        File destFile = new File(destDir, filename);
        try (InputStream is = resp.body(); FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buf = new byte[8192]; long downloaded = 0, lastUpdate = 0; int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n); downloaded += n;
                if (contentLength > 0 && downloaded - lastUpdate > 102_400) {
                    lastUpdate = downloaded; onProgress.accept((double) downloaded / contentLength);
                }
            }
        }
        onDone.accept(filename);
    }

    private String fetchJson(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req   = HttpRequest.newBuilder().uri(URI.create(url))
                              .header("User-Agent", "MinecraftServerLauncher/1.0").build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode() + " from " + url);
        return resp.body();
    }

    // ════════════════════════════════════════════════════════════════
    // COMMAND MACROS
    // ════════════════════════════════════════════════════════════════

    private void loadMacros() {
        if (macrosList == null) return;
        try {
            String[] keys = prefs.node("macros").keys(); Arrays.sort(keys);
            macrosList.getItems().setAll(keys);
        } catch (BackingStoreException e) { appendSystemLog("> Error loading macros: " + e.getMessage()); }
    }

    @FXML private void handleRunMacro() {
        String name = macrosList.getSelectionModel().getSelectedItem();
        if (name == null) { appendSystemLog("> Select a macro."); return; }
        String raw = prefs.node("macros").get(name, ""); if (raw.isBlank()) return;
        int count = 0;
        for (String cmd : raw.split("\n")) { cmd = cmd.trim(); if (!cmd.isEmpty()) { sendServerCommand(cmd); count++; } }
        appendSystemLog("> Ran macro '" + name + "' — " + count + " command(s).");
    }

    @FXML private void handleAddMacro() {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("New Command Macro");
        Label nameLbl = styledSectionLabel("Macro Name:");
        TextField nameField = new TextField();
        nameField.setStyle("-fx-background-color:#2d2d2d; -fx-text-fill:white; -fx-border-color:#555; -fx-background-radius:6; -fx-border-radius:6;");
        Label cmdsLbl = styledDimLabel("Commands (one per line):");
        TextArea cmdsArea = new TextArea(); cmdsArea.setPrefRowCount(6);
        cmdsArea.setPromptText("say Hello!\nweather clear\ntime set day");
        cmdsArea.setStyle("-fx-control-inner-background:#1a1a1a; -fx-text-fill:#4db8ff; -fx-font-family:Consolas; -fx-border-color:#555;");
        VBox content = new VBox(10, nameLbl, nameField, cmdsLbl, cmdsArea);
        content.setPadding(new Insets(16)); content.setStyle("-fx-background-color:#1e1e1e;"); content.setPrefWidth(400);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setStyle("-fx-background-color:#1e1e1e;");
        dlg.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            String name = nameField.getText().trim(); String cmds = cmdsArea.getText().trim();
            if (name.isEmpty() || cmds.isEmpty()) { appendSystemLog("> Name and commands cannot be empty."); return; }
            prefs.node("macros").put(name, cmds); loadMacros(); appendSystemLog("> Saved macro '" + name + "'.");
        });
    }

    @FXML private void handleDeleteMacro() {
        String name = macrosList.getSelectionModel().getSelectedItem();
        if (name == null) { appendSystemLog("> Select a macro to delete."); return; }
        prefs.node("macros").remove(name); macrosList.getItems().remove(name);
        appendSystemLog("> Deleted macro '" + name + "'.");
    }

    // ════════════════════════════════════════════════════════════════
    // PLAYER TABLE
    // ════════════════════════════════════════════════════════════════

    private void setupPlayerTable() {
        playerTable.setItems(onlinePlayers);
        playerTable.setPlaceholder(new Label(""));
        TableColumn<String, String> col = (TableColumn<String, String>) playerTable.getColumns().get(0);
        col.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            private final ImageView iv  = new ImageView();
            private final Label     lbl = new Label();
            private final HBox      box = new HBox(10, iv, lbl);
            { box.setAlignment(Pos.CENTER_LEFT); iv.setFitWidth(24); iv.setFitHeight(24); }

            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { setGraphic(null); setContextMenu(null); }
                else {
                    lbl.setText(name); lbl.setStyle("-fx-text-fill:#e0e0e0;");
                    Image img = new Image("https://minotar.net/helm/" + name + "/24.png", 24, 24, false, false, true);
                    img.errorProperty().addListener((obs, w, err) -> { if (Boolean.TRUE.equals(err)) iv.setImage(null); });
                    iv.setImage(img); setGraphic(box);
                    ContextMenu menu = new ContextMenu(); menu.getStyleClass().add("context-menu");
                    MenuItem kick = new MenuItem("Kick"), ban = new MenuItem("Ban");
                    kick.setOnAction(e -> sendServerCommand("kick " + name));
                    ban.setOnAction( e -> sendServerCommand("ban "  + name));
                    menu.getItems().addAll(kick, ban); setContextMenu(menu);
                }
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleSendCommand() {
        String cmd = commandInput.getText().trim(); if (cmd.isEmpty()) return;
        commandHistory.addFirst(cmd); if (commandHistory.size() > 50) commandHistory.removeLast();
        historyIndex = -1; sendServerCommand(cmd); commandInput.clear();
    }

    private void sendServerCommand(String command) {
        if (serverInput == null) return;
        try { serverInput.write((command + "\n").getBytes()); serverInput.flush(); }
        catch (IOException e) { appendSystemLog("> Command error: " + e.getMessage()); }
    }

    private void addLogLine(String line) {
        allLogs.add(line);
        if (allLogs.size() > MAX_LOGS) allLogs.remove(0, allLogs.size() - TRIM_TO);
        consoleList.scrollTo(filteredLogs.size() - 1);
        checkDiagnostics(line);
        parseTpsFromLog(line);
    }

    private void parseTpsFromLog(String line) {
        // Paper/Spigot /tps output: "TPS from last 1m, 5m, 15m: *20.0*, *20.0*, *20.0*"
        if (!line.contains("TPS from last")) return;
        int colon = line.lastIndexOf(':');
        if (colon < 0) return;
        String after = line.substring(colon + 1).replaceAll("[^0-9.]", " ").trim();
        String[] parts = after.split("\\s+");
        if (parts.length > 0) {
            try { lastParsedTps = Math.min(20.0, Double.parseDouble(parts[0])); }
            catch (NumberFormatException ignored) {}
        }
    }

    private void appendSystemLog(String message) {
        if (Platform.isFxApplicationThread()) addLogLine(message);
        else Platform.runLater(() -> addLogLine(message));
    }

    private void updateCounterUI() {
        counterContainer.setVisible(!onlinePlayers.isEmpty());
        playerCountLabel.setText(String.valueOf(onlinePlayers.size()));
    }

    private String extractPlayerName(String logLine, String action) {
        try {
            int idx = logLine.indexOf("]: ");
            if (idx != -1) {
                int start = idx + 3, end = logLine.indexOf(action);
                if (start < end) return logLine.substring(start, end).trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─── Dialog style helpers ────────────────────────────────────────

    private Label styledSectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#00ff41; -fx-font-weight:bold; -fx-font-size:12;");
        return l;
    }
    private Label styledDimLabel(String text) {
        Label l = new Label(text); l.setStyle("-fx-text-fill:#888888; -fx-font-size:11;"); return l;
    }
    private RadioButton styledRadio(String text, ToggleGroup group) {
        RadioButton rb = new RadioButton(text); rb.setToggleGroup(group);
        rb.setStyle("-fx-text-fill:#e0e0e0;"); return rb;
    }
}
