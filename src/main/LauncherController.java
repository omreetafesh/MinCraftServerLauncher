package main;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
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
import javafx.animation.Animation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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

    // JAR downloader — PaperMC sunset its v2 API (HTTP 410); v3 "Fill" asks for a UA with a contact URL
    private static final String PAPER_API  = "https://fill.papermc.io/v3/projects/paper";
    private static final String USER_AGENT =
        "MinecraftServerLauncher/1.0 (https://github.com/omreetafesh/MinCraftServerLauncher)";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build();

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
            "Could not open the server port. Check server-port in World Config or close conflicting applications."},
        new String[]{"UnsupportedClassVersionError",
            "ERROR", "Java version too old",
            "This server JAR needs a newer Java. Minecraft 1.20.5+ needs Java 21, and newer versions may need more. "
            + "Install a newer Java from adoptium.net, or pick one in Settings → Performance → Java."},
        new String[]{"You need to agree to the EULA",
            "WARN", "EULA not accepted",
            "Minecraft won't start until eula.txt says eula=true. Press Start and accept the EULA when asked."}
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
    private boolean updatingProfileCombo = false;

    // Server state
    private File    serverDirectory          = null;
    private File    backupDirectory          = null;
    // volatile: written by the server thread, read by the FX thread and the shutdown hook
    private volatile Process      serverProcess;
    private volatile OutputStream serverInput;
    private boolean isRestarting             = false;
    private boolean quitAfterStop            = false; // exit the app once the server has stopped
    private Timeline quitTimeout;
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
    private static final int MAX_CHAT = 500;
    private final java.util.concurrent.ConcurrentLinkedQueue<String> pendingLines =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean drainScheduled =
        new java.util.concurrent.atomic.AtomicBoolean();

    // Crash handling: stop auto-restarting a server that keeps dying right after boot
    private static final long QUICK_CRASH_MS    = 120_000;
    private static final int  MAX_QUICK_CRASHES = 3;
    private int quickCrashCount = 0;
    private PauseTransition pendingCrashRestart;

    private volatile boolean eulaRequired = false; // server printed "You need to agree to the EULA"
    private String currentJavaExe = "java";
    private boolean launchPending = false; // Start pressed, process not created yet
    private final Map<String, Image> headCache = new HashMap<>();

    // Command history
    private final ArrayDeque<String> commandHistory = new ArrayDeque<>();
    private int historyIndex = -1;

    // Players
    private final ObservableList<String> onlinePlayers = FXCollections.observableArrayList();

    // Schedulers / timers
    private ScheduledExecutorService backupScheduler;
    private final java.util.concurrent.locks.ReentrantLock backupLock = new java.util.concurrent.locks.ReentrantLock();
    private volatile CountDownLatch saveLatch;   // released when the server logs "Saved the game"
    private boolean restoreInProgress = false;
    private Timeline      restartTimeline;
    private LocalDateTime nextScheduledRestart;
    private boolean       restartWarningSent;
    private Clock         clock = Clock.systemDefaultZone(); // swappable so tests can fake the time
    private Timeline uptimeClock;
    private Timeline resourceMonitor;

    // RCON & system tray
    private RconClient       rconClient;
    private java.awt.TrayIcon trayIcon;

    // Diagnostics dedup
    private final Set<String> loggedDiagnostics = new LinkedHashSet<>();

    // Properties form state
    private Map<String, String>  currentProps = null;   // decoded values
    private String  propsText = null;                   // working copy of server.properties (incl. unsaved raw edits)
    private java.nio.charset.Charset propsCharset = java.nio.charset.StandardCharsets.UTF_8;
    private final Map<String, Control> propControls = new LinkedHashMap<>();

    // ── Live Performance Charts ───────────────────────────────────────
    private LineChart<Number, Number>  ramChart;
    private LineChart<Number, Number>  tpsChart;
    private AreaChart<Number, Number>  playerChart;
    private final XYChart.Series<Number, Number> ramSeries    = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> tpsSeries    = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> playerSeries = new XYChart.Series<>();
    private Timeline monitorTimeline;
    private Label    ramValueLabel, tpsValueLabel, playerValueLabel;
    private int monitorTick = 0;
    // NaN = no real sample yet. TPS only appears in the log when someone runs /tps (Paper) or
    // /tick query (vanilla), so the chart must not invent a flat "20".
    private volatile double lastParsedTps = Double.NaN;
    private static final int CHART_MAX_POINTS = 60; // 5 min at 5-second samples

    // ── Player stats ─────────────────────────────────────────────────
    private final ObservableList<ObservableList<String>> playerStatsData =
        FXCollections.observableArrayList();

    // ─── FXML: Shell (sidebar, header, page stack) ──────────────────
    @FXML private StackPane       appShell;
    @FXML private Pane            backLayer, frontLayer;
    @FXML private BorderPane      rootPane;
    @FXML private VBox            sidebar;
    @FXML private ToggleGroup     navGroup;
    @FXML private StackPane       pageStack;
    @FXML private Label           pageTitle, pageSubtitle;
    @FXML private ImageView       logoView;
    @FXML private Label           cpuLabel, ramLabel;
    @FXML private ProgressBar     cpuBar, ramBar;

    // ─── FXML: Header ────────────────────────────────────────────────
    @FXML private Button          startButton, stopButton, restartButton;
    @FXML private Label           statusLabel, uptimeLabel;
    @FXML private ComboBox<String> profileCombo;

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
    @FXML private TextField  memoryField, javaPathField;
    @FXML private Label      javaDetectedLabel;
    @FXML private CheckBox   optimizedFlagsCheck;
    @FXML private CheckBox   effectsCheck;
    @FXML private TabPane    settingsTabs;

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
    @FXML private ImageView motdIconView;

    // ─── FXML: Online players panel ──────────────────────────────────
    @FXML private VBox             playersRail;
    @FXML private ListView<String> playerList;
    @FXML private Label            playerCountLabel;
    @FXML private HBox             counterContainer;

    // ════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(LauncherController.class);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServerOnShutdown, "server-shutdown"));

        stopButton.setDisable(true);
        restartButton.setDisable(true);
        commandInput.setDisable(true);

        setupConsoleList();
        setupChatList();
        setupWhitelistBanViews();
        setupRconView();
        setupMacrosList();
        setupPlayerList();
        setupCommandHistory();
        logoView.setImage(Main.bufToFx(AppIcon.draw(68)));
        motdIconView.setImage(logoView.getImage());
        // Buttons keep their full label; text fields next to them shrink instead (no "Re…" buttons).
        // runLater: Settings tab contents only join the scene once the window is shown.
        Platform.runLater(() -> {
            for (javafx.scene.Node n : rootPane.lookupAll(".button")) ((Region) n).setMinWidth(Region.USE_PREF_SIZE);
        });

        // Persist text-field values automatically
        serverJarField.textProperty().addListener((obs, o, n) -> {
            prefs.node("profiles").node(currentProfile).put("serverJar", n);
            refreshJavaHint(); // a different JAR may need a different Java
        });
        memoryField.textProperty().addListener((obs, o, n) ->
            prefs.node("profiles").node(currentProfile).put("maxMemory", n));
        javaPathField.textProperty().addListener((obs, o, n) -> {
            prefs.node("profiles").node(currentProfile).put("javaPath", n.trim());
            refreshJavaHint();
        });
        maxBackupsField.textProperty().addListener((obs, o, n) ->
            prefs.node("profiles").node(currentProfile).put("maxBackups", n));
        crashRestartDelayField.textProperty().addListener((obs, o, n) ->
            prefs.put("crashRestartDelay", n));
        scheduledRestartField.textProperty().addListener((obs, o, n) -> onRestartTimeEdited(n));

        setupProfiles();
        setupMonitoring();
        setupSystemTray();
        setupMonitorCharts();
        setupPlayerStatsTable();
        setupMotdEditor();

        optimizedFlagsCheck.setSelected(prefs.getBoolean("optimizedFlags", false));
        loadMacros();
        setupMotion();

        showPage("console");
        appendSystemLog(serverDirectory == null
            ? "> Welcome! Open Settings → Server to choose your server folder."
            : "> Ready. Press Start to launch the server.");
    }

    // ─── Console list setup ──────────────────────────────────────────

    // ─── Lists (colors come from the .log-* classes in style.css) ────

    private static final List<String> LOG_CLASSES =
        List.of("log-error", "log-warn", "log-system", "log-chat", "log-info", "log-cmd");

    /** A ListCell whose text color is picked per line by {@code classifier}. */
    private static ListCell<String> coloredCell(java.util.function.Function<String, String> classifier) {
        return new ListCell<>() {
            @Override protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll(LOG_CLASSES);
                if (empty || line == null) { setText(null); return; }
                setText(line);
                getStyleClass().add(classifier.apply(line));
            }
        };
    }

    private void setupConsoleList() {
        consoleList.setItems(filteredLogs);
        consoleList.setCellFactory(lv -> coloredCell(LauncherController::logClass));
        consoleList.setPlaceholder(emptyState("console", "No output yet", "Start the server to see its console here."));
        searchField.textProperty().addListener((obs, o, q) -> {
            String lower = q.trim().toLowerCase();
            filteredLogs.setPredicate(lower.isEmpty() ? s -> true : s -> s.toLowerCase().contains(lower));
        });
    }

    /** Color class for a console line. Matches both "[Server thread/WARN]" (vanilla) and "[12:00:00 WARN]" (Paper). */
    static String logClass(String line) {
        if (line.contains("ERROR]") || line.contains("SEVERE]") || line.startsWith("[ERROR]") || line.contains("Exception")
                || line.contains("FAILED") || line.contains("Error:") || line.contains("Error "))
            return "log-error";
        if (line.contains("WARN]"))      return "log-warn";
        if (line.startsWith("› "))       return "log-cmd";     // a command you typed
        if (line.startsWith(">"))        return "log-system";  // launcher messages
        if (line.contains(": <") || line.contains("] <")) return "log-chat";
        return "log-info";
    }

    @FXML private void handleClearConsole() { allLogs.clear(); }

    // "[18:04:05] [Server thread/INFO]: <Notch> hi"  or  "[18:04:05 INFO]: <Notch> hi"
    // (Minecraft 26+ may put "Player chat: " / "[Not Secure] " before the name)
    private static final Pattern CHAT_LINE =
        Pattern.compile("^\\[(\\d{1,2}:\\d{2})(?::\\d{2})?[^\\]]*\\](?: ?\\[[^\\]]*\\])*: "
            + "(?:Player chat: )?(?:\\[Not Secure\\] )?<([^>]+)> (.*)$");

    private void setupChatList() {
        chatList.setItems(chatLogs);
        chatList.setPlaceholder(emptyState("chat", "No messages yet", "Chat from players shows up here."));
        // Rendered like a chat app: skin head, name, time, message
        chatList.setCellFactory(lv -> new ListCell<>() {
            private final ImageView head = new ImageView();
            private final Label name = styled("", "chat-name"), time = styled("", "chat-time"), text = styled("", "chat-text");
            private final HBox row;
            {
                head.setFitWidth(28); head.setFitHeight(28); head.setSmooth(false);
                Rectangle clip = new Rectangle(28, 28); clip.setArcWidth(10); clip.setArcHeight(10);
                head.setClip(clip);
                text.setWrapText(true);
                VBox body = new VBox(2, new HBox(8, name, time), text);
                HBox.setHgrow(body, Priority.ALWAYS);
                row = new HBox(10, head, body);
                row.getStyleClass().add("chat-row");
            }
            @Override protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                setText(null);
                if (empty || line == null) { setGraphic(null); return; }
                Matcher m = CHAT_LINE.matcher(line);
                if (!m.find()) { setGraphic(null); setText(line); return; }
                time.setText(m.group(1));
                name.setText(m.group(2));
                text.setText(m.group(3));
                head.setImage(headFor(m.group(2)));
                text.maxWidthProperty().bind(lv.widthProperty().subtract(80));
                setGraphic(row);
            }
        });
    }

    private void setupWhitelistBanViews() {
        whitelistView.setPlaceholder(emptyState("check", "Whitelist is empty", "Add a player name below."));
        banListView.setPlaceholder(emptyState("ban", "No banned players", "Ban someone by name below."));
    }

    private void setupRconView() {
        rconOutputList.setCellFactory(lv -> coloredCell(l -> l.startsWith(">") ? "log-cmd" : "log-info"));
        rconOutputList.setPlaceholder(emptyState("plug", "Not connected", "Connect above, then send commands below."));
        setRconStatus("Disconnected", "bad");
    }

    private void setRconStatus(String text, String kind) {
        rconStatusLabel.setText(text);
        rconStatusLabel.getStyleClass().removeAll("status-text-good", "status-text-bad", "status-text-busy");
        rconStatusLabel.getStyleClass().add("status-text-" + kind);
    }

    private void setupMacrosList() {
        macrosList.setPlaceholder(emptyState("zap", "No macros yet", "Click “New macro” to save a list of commands."));
        // Name on top, the macro's commands underneath
        macrosList.setCellFactory(lv -> new ListCell<>() {
            private final Label name = styled("", "macro-name"), cmds = styled("", "macro-cmds", "mono");
            private final VBox box = new VBox(3, name, cmds);
            @Override protected void updateItem(String macro, boolean empty) {
                super.updateItem(macro, empty);
                setText(null);
                if (empty || macro == null) { setGraphic(null); return; }
                name.setText(macro);
                cmds.setText(String.join("  ·  ", prefs.node("macros").get(macro, "").lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList()));
                setGraphic(box);
            }
        });
    }

    /** Icon + two lines of text, shown inside empty lists. */
    private static VBox emptyState(String icon, String title, String subtitle) {
        Label t = new Label(title);    t.getStyleClass().add("empty-title");
        Label s = new Label(subtitle); s.getStyleClass().add("empty-sub"); s.setWrapText(true);
        VBox box = new VBox(new Icon(icon, 34), t, s);
        box.getStyleClass().add("empty-state");
        return box;
    }

    // ─── Navigation ──────────────────────────────────────────────────

    private static final Map<String, String[]> PAGES = Map.of(
        "console",     new String[]{"Console",          "Live server output and commands"},
        "chat",        new String[]{"Chat",             "What players are saying"},
        "access",      new String[]{"Whitelist & Bans", "Control who can join"},
        "monitor",     new String[]{"Performance",      "Memory, TPS and players over the last 5 minutes"},
        "history",     new String[]{"Player History",   "Stats for everyone who has played"},
        "diagnostics", new String[]{"Diagnostics",      "Problems spotted in the server log"},
        "rcon",        new String[]{"Remote Console",   "Send commands to a server over RCON"},
        "macros",      new String[]{"Macros",           "Run several commands with one click"},
        "settings",    new String[]{"Settings",         "Configure this server profile"});

    @FXML private void handleNav(javafx.event.ActionEvent e) {
        ToggleButton b = (ToggleButton) e.getSource();
        b.setSelected(true); // clicking the page you're on must not un-select it
        showPage((String) b.getUserData());
    }

    private void showPage(String id) {
        javafx.scene.Node appearing = null;
        for (javafx.scene.Node page : pageStack.getChildren()) {
            boolean show = id.equals(page.getId());
            if (show && !page.isVisible()) appearing = page;
            page.setVisible(show);
        }
        String[] title = PAGES.get(id);
        pageTitle.setText(title[0]);
        pageSubtitle.setText(title[1]);
        for (Toggle t : navGroup.getToggles()) if (id.equals(t.getUserData())) t.setSelected(true);
        boolean settings = id.equals("settings");
        boolean railAppears = !settings && !playersRail.isVisible();
        playersRail.setVisible(!settings);   // settings forms get the full width
        playersRail.setManaged(!settings);
        if (appearing != null && stage != null && stage.isShowing()) {
            Motion.enter(appearing, 0, 14, 420, 0);
            Motion.enter(pageTitle.getParent(), 0, 6, 360, 0);
            if (railAppears) Motion.enter(playersRail, 18, 0, 460, 60);
        }
        if (settings) {
            loadServerProperties();
            refreshBackupList();
            refreshJavaHint(); // the JAR may have been replaced since (e.g. a new download)
        }
    }

    // ─── Header status pill ──────────────────────────────────────────

    private static final List<String> STATUS_CLASSES =
        List.of("status-offline", "status-starting", "status-online", "status-busy", "status-crashed");

    /** state: offline | starting | online | busy | crashed (see .status-* in style.css). */
    private void setStatus(String text, String state) {
        boolean changed = !statusLabel.getStyleClass().contains("status-" + state);
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(STATUS_CLASSES);
        statusLabel.getStyleClass().add("status-" + state);
        // A ring ripples out of the dot while the server is alive (or coming up)
        if (state.equals("online") || state.equals("starting")) {
            if (statusRipple.getStatus() != Animation.Status.RUNNING) statusRipple.play();
        } else {
            statusRipple.stop();
            statusRing.setOpacity(0);
        }
        if (changed && stage != null && stage.isShowing()) Motion.pop(statusLabel, 1.07);
    }

    // ─── Motion ──────────────────────────────────────────────────────
    // Effects.java draws the light (aurora, cursor glow, card spotlight); Motion.java the small
    // transitions. Hover/press animations live in style.css ("transition: all …").

    private Effects    effects;
    private final Region    statusRing   = new Region();
    private final Animation statusRipple = Motion.ripple(statusRing);
    private final StackPane navIndicator = new StackPane();
    /** Players who just joined — their row slides in once, the first time it is drawn. */
    private final Set<String> freshPlayers = new HashSet<>();

    private void setupMotion() {
        effects = new Effects(appShell, backLayer, frontLayer);
        effectsCheck.setSelected(prefs.getBoolean("uiEffects", true));
        effects.setEnabled(effectsCheck.isSelected());

        // One highlight behind the selected nav item that glides to the next one
        Region bar = new Region();
        bar.getStyleClass().add("nav-indicator-bar");
        StackPane.setAlignment(bar, Pos.CENTER_LEFT);
        navIndicator.getChildren().add(bar);
        navIndicator.getStyleClass().add("nav-indicator");
        navIndicator.setManaged(false);
        navIndicator.setMouseTransparent(true);
        sidebar.getChildren().add(0, navIndicator); // first child = drawn behind the buttons
        navGroup.selectedToggleProperty().addListener((obs, o, n) -> placeNavIndicator(true));
        sidebar.heightProperty().addListener((obs, o, n) -> Platform.runLater(() -> placeNavIndicator(false)));

        statusRing.getStyleClass().add("status-ring");
        statusLabel.setGraphic(new StackPane(statusRing, statusLabel.getGraphic()));

        settingsTabs.getSelectionModel().selectedItemProperty().addListener((obs, o, tab) -> {
            if (tab != null && tab.getContent() != null) Motion.enter(tab.getContent(), 0, 10, 380, 0);
        });
    }

    private void placeNavIndicator(boolean animate) {
        if (!(navGroup.getSelectedToggle() instanceof ToggleButton b) || b.getHeight() <= 0) return;
        // Layout coordinates (not scene ones), so the intro's slide-in offsets don't throw it off
        javafx.scene.Node nav = b.getParent();
        double y = nav.getLayoutY() + b.getLayoutY();
        navIndicator.resize(b.getWidth(), b.getHeight());
        navIndicator.setLayoutX(nav.getLayoutX() + b.getLayoutX());
        if (animate && stage != null && stage.isShowing()) Motion.tween(navIndicator.layoutYProperty(), y, 460);
        else navIndicator.setLayoutY(y);
    }

    /** The window's entrance: sidebar items cascade in, header and content rise into place. */
    private void playIntro() {
        placeNavIndicator(false);
        List<javafx.scene.Node> side = new ArrayList<>();
        for (javafx.scene.Node n : sidebar.getChildren()) {
            if (n == navIndicator) continue;
            if (n.getStyleClass().contains("nav")) side.addAll(((Pane) n).getChildren());
            else side.add(n);
        }
        Motion.stagger(side, -18, 0, 80, 26);
        Motion.enter(navIndicator, -18, 0, 600, 260);
        Motion.enter(rootPane.lookup(".header"), 0, -10, 560, 60);
        Motion.enter(rootPane.lookup(".body"), 0, 22, 700, 160);
        Motion.enter(backLayer, 0, 0, 1400, 0);
    }

    private void updateEffectsPaused() {
        effects.setPaused(stage.isIconified() || !stage.isShowing());
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

        // Only create "Default" on a fresh install — re-adding it every launch undid deleting it
        if (names.isEmpty()) { migrateToProfile("Default"); names.add("Default"); }
        profileCombo.getItems().setAll(names);

        currentProfile = prefs.get("currentProfile", names.get(0));
        if (!profileCombo.getItems().contains(currentProfile))
            currentProfile = profileCombo.getItems().get(0);
        selectProfileInCombo(currentProfile);
        loadProfile(currentProfile);

        profileCombo.setOnAction(e -> {
            if (updatingProfileCombo) return; // our own setValue()/remove(), not the user
            String selected = profileCombo.getValue();
            if (selected == null || selected.equals(currentProfile)) return;
            if (serverIsBusy("switch profiles")) {
                Platform.runLater(() -> selectProfileInCombo(currentProfile));
                return;
            }
            switchToProfile(selected);
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
        p.put("javaPath",   javaPathField.getText().trim());
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
        javaPathField.setText(p.get("javaPath", ""));
        refreshJavaHint();

        boolean autoBackup = p.getBoolean("autoBackupEnabled", false);
        autoBackupCheck.setSelected(autoBackup);
        if (autoBackup && backupDirectory != null) startBackupScheduler();

        // Always reload, so a profile without a folder doesn't show the previous profile's settings
        loadServerProperties();
        loadWhitelist();
        loadBanList();
        appendSystemLog(sp != null ? "> Profile '" + name + "' — " + sp
                                   : "> Profile '" + name + "' loaded (no server directory set).");

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

    /** Sets the combo's value without triggering its onAction (which would save/load profiles again). */
    private void selectProfileInCombo(String name) {
        updatingProfileCombo = true;
        try { profileCombo.setValue(name); } finally { updatingProfileCombo = false; }
    }

    private void switchToProfile(String name) {
        saveCurrentProfile();
        currentProfile = name;
        prefs.put("currentProfile", name);
        selectProfileInCombo(name);
        loadProfile(name);
    }

    @FXML private void handleNewProfile() {
        if (serverIsBusy("create a profile")) return;
        TextInputDialog dlg = themed(new TextInputDialog("New Server"));
        dlg.setTitle("New Profile"); dlg.setHeaderText(null); dlg.setContentText("Profile name:");
        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(name -> {
            // Preferences node names can't contain '/' and are limited to 80 characters
            if (name.contains("/") || name.length() > Preferences.MAX_NAME_LENGTH) {
                appendSystemLog("> Profile names can't contain '/' or be longer than "
                    + Preferences.MAX_NAME_LENGTH + " characters."); return;
            }
            if (profileCombo.getItems().contains(name)) { appendSystemLog("> Profile '" + name + "' already exists."); return; }
            Preferences p = prefs.node("profiles").node(name);
            p.put("serverJar", "server.jar"); p.put("maxMemory", "2"); p.put("maxBackups", "5");
            updatingProfileCombo = true;
            try { profileCombo.getItems().add(name); } finally { updatingProfileCombo = false; }
            switchToProfile(name);
            appendSystemLog("> Created profile '" + name + "'.");
        });
    }

    @FXML private void handleDeleteProfile() {
        if (profileCombo.getItems().size() <= 1) { appendSystemLog("> Cannot delete the last profile."); return; }
        if (serverIsBusy("delete a profile")) return;
        String toDelete = currentProfile;
        Alert confirm = themed(new Alert(Alert.AlertType.CONFIRMATION,
            "This removes the launcher's settings for '" + toDelete + "'. Your server files are not touched.",
            ButtonType.OK, ButtonType.CANCEL));
        confirm.setTitle("Delete Profile"); confirm.setHeaderText("Delete profile '" + toDelete + "'?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String next = profileCombo.getItems().stream().filter(n -> !n.equals(toDelete)).findFirst().orElseThrow();
        // Switch away first, so nothing (save-on-switch, field listeners) writes to the node after we delete it
        currentProfile = next;
        prefs.put("currentProfile", next);
        updatingProfileCombo = true;
        try {
            profileCombo.getItems().remove(toDelete);
            profileCombo.setValue(next);
        } finally { updatingProfileCombo = false; }
        try { prefs.node("profiles").node(toDelete).removeNode(); prefs.flush(); }
        catch (BackingStoreException e) { appendSystemLog("> Failed to delete: " + e.getMessage()); }
        loadProfile(next);
        appendSystemLog("> Deleted profile '" + toDelete + "'.");
    }

    // ─── Monitoring ──────────────────────────────────────────────────

    private void setupMonitoring() {
        uptimeLabel.setText("");

        uptimeClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (serverProcess != null && serverProcess.isAlive()) {
                long ms = System.currentTimeMillis() - serverStartMillis;
                uptimeLabel.setText(String.format("%02d:%02d:%02d",
                    ms / 3_600_000, (ms % 3_600_000) / 60_000, (ms % 60_000) / 1_000));
            }
        }));
        uptimeClock.setCycleCount(Timeline.INDEFINITE);

        resourceMonitor = new Timeline(new KeyFrame(Duration.seconds(2), e -> updateSystemStats()));
        resourceMonitor.setCycleCount(Timeline.INDEFINITE);
        resourceMonitor.play();
        updateSystemStats();
    }

    /** The CPU / RAM bars at the bottom of the sidebar (whole PC, not just the server). */
    private void updateSystemStats() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpu   = Math.max(0, os.getCpuLoad()); // -1 until the first sample exists
            long   total = os.getTotalMemorySize(), used = total - os.getFreeMemorySize();
            double ram   = total > 0 ? (double) used / total : 0;
            cpuLabel.setText(String.format("%.0f%%", cpu * 100));
            ramLabel.setText(String.format("%.1f / %.0f GB", used / 1_073_741_824.0, total / 1_073_741_824.0));
            setLoad(cpuBar, cpu);
            setLoad(ramBar, ram);
        } catch (Exception ignored) {}
    }

    private static void setLoad(ProgressBar bar, double value) {
        Motion.tween(bar.progressProperty(), value, 900); // glide, don't jump, every 2 s
        bar.getStyleClass().removeAll("stat-high", "stat-crit");
        if (value >= 0.9) bar.getStyleClass().add("stat-crit");
        else if (value >= 0.75) bar.getStyleClass().add("stat-high");
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

    /** Tray image in several sizes, so Windows picks a crisp one for its display scaling (16 px at 100%, 24 at 150%…). */
    private java.awt.Image buildTrayImage() {
        java.awt.Image[] variants = java.util.stream.IntStream.of(16, 20, 24, 32, 40, 48, 64)
            .mapToObj(AppIcon::draw).toArray(java.awt.Image[]::new);
        return new java.awt.image.BaseMultiResolutionImage(variants);
    }

    private void showTrayMenu(int screenX, int screenY) {
        if (trayMenuStage == null) {
            trayMenuStage = new Stage();
            trayMenuStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            trayMenuStage.setAlwaysOnTop(true);

            Region dot = new Region();
            dot.getStyleClass().add("status-dot");
            Label statusLbl = new Label("", dot);
            statusLbl.getStyleClass().addAll("status-pill", "tray-status");

            Button showBtn  = trayMenuBtn("Open dashboard", "server");
            Button startBtn = trayMenuBtn("Start server",   "play");
            Button stopBtn  = trayMenuBtn("Stop server",    "stop");
            Button quitBtn  = trayMenuBtn("Quit launcher",  "close");
            quitBtn.getStyleClass().add("tray-item-danger");

            showBtn.setOnAction(e  -> { trayMenuStage.hide(); showMainWindow(); });
            startBtn.setOnAction(e -> { trayMenuStage.hide(); Platform.runLater(this::handleStartServer); });
            stopBtn.setOnAction(e  -> { trayMenuStage.hide(); Platform.runLater(this::handleStopServer); });
            quitBtn.setOnAction(e  -> { trayMenuStage.hide(); requestQuit(); });

            Region div = new Region();
            div.getStyleClass().add("tray-divider");
            VBox.setMargin(div, new Insets(4, 6, 4, 6));
            VBox menu = new VBox(statusLbl, showBtn, startBtn, stopBtn, div, quitBtn);
            menu.getStyleClass().add("tray-menu");
            menu.setPrefWidth(210);

            // Transparent padding around the menu so its drop shadow isn't clipped
            StackPane shadowRoom = new StackPane(menu);
            shadowRoom.setStyle("-fx-background-color: transparent; -fx-padding: 14;");
            Scene scene = new Scene(shadowRoom);
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
        // The tray menu mirrors the header's status pill
        Label lbl = (Label) trayMenuStage.getUserData();
        lbl.setText("Server " + statusLabel.getText().toLowerCase());
        lbl.getStyleClass().removeAll(STATUS_CLASSES);
        statusLabel.getStyleClass().stream().filter(STATUS_CLASSES::contains).findFirst()
            .ifPresent(c -> lbl.getStyleClass().add(c));

        // Position: show above the click so it doesn't go off-screen
        trayMenuStage.show(); // measure height after first show
        double mh = trayMenuStage.getScene().getRoot().prefHeight(-1);
        trayMenuStage.setX(screenX - 14);
        trayMenuStage.setY(screenY - mh + 10);
        trayMenuStage.requestFocus();
        Motion.enter(trayMenuStage.getScene().getRoot(), 0, 10, 300, 0);
    }

    private Button trayMenuBtn(String text, String icon) {
        Button b = new Button(text, new Icon(icon, 15));
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("tray-item");
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

        ramChart = new LineChart<>(timeAxis(), new NumberAxis());
        styleChart(ramChart, "chart-ram");
        ramChart.getData().add(ramSeries);

        NumberAxis yTps = new NumberAxis(0, 20, 5);
        yTps.setAutoRanging(false);
        tpsChart = new LineChart<>(timeAxis(), yTps);
        styleChart(tpsChart, "chart-tps");
        tpsChart.getData().add(tpsSeries);

        NumberAxis yPly = new NumberAxis();
        yPly.setMinorTickVisible(false);
        yPly.setTickUnit(1);
        playerChart = new AreaChart<>(timeAxis(), yPly);
        styleChart(playerChart, "chart-player");
        playerChart.getData().add(playerSeries);

        ramValueLabel = new Label("–"); tpsValueLabel = new Label("–"); playerValueLabel = new Label("0");
        monitorContainer.getChildren().setAll(
            chartCard("Memory in use", "Whole PC", ramValueLabel, "MB", ramChart),
            chartCard("Server TPS", "Sampled when someone runs “tps” (Paper) or “tick query” (vanilla) · 20 is perfect",
                      tpsValueLabel, "TPS", tpsChart),
            chartCard("Players online", "Updated every 5 seconds", playerValueLabel, "online", playerChart));
    }

    /** A card with a title, a big live number, and the chart under it. */
    private static VBox chartCard(String title, String note, Label value, String unit, XYChart<?, ?> chart) {
        Label t = new Label(title);  t.getStyleClass().add("card-title");
        Label n = new Label(note);   n.getStyleClass().add("muted");
        value.getStyleClass().add("chart-value");
        Label u = new Label(unit);   u.getStyleClass().add("chart-unit");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(8, new VBox(2, t, n), spacer, value, u);
        head.setAlignment(Pos.BOTTOM_LEFT);
        chart.setPrefHeight(190);
        chart.setMinHeight(170);
        VBox card = new VBox(head, chart);
        card.getStyleClass().addAll("card", "chart-card");
        return card;
    }

    private NumberAxis timeAxis() {
        NumberAxis ax = new NumberAxis();
        ax.setAutoRanging(true); ax.setForceZeroInRange(false); ax.setMinorTickVisible(false);
        ax.setTickLabelFormatter(new javafx.util.StringConverter<>() {   // 95 → "1:35"
            @Override public String toString(Number n) { int s = n.intValue(); return (s / 60) + ":" + String.format("%02d", s % 60); }
            @Override public Number fromString(String s) { return 0; }
        });
        return ax;
    }

    private void styleChart(XYChart<?, ?> chart, String styleClass) {
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.getStyleClass().add(styleClass);
        if (chart instanceof LineChart<?, ?> lc) lc.setCreateSymbols(false);
        if (chart instanceof AreaChart<?, ?> ac) ac.setCreateSymbols(false);
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
            ramValueLabel.setText(String.format("%,d", usedMB));
        } catch (Exception ignored) {}
        // TPS — from last parsed server log
        if (!Double.isNaN(lastParsedTps)) {
            chartPoint(tpsSeries, monitorTick, lastParsedTps);
            tpsValueLabel.setText(String.format("%.1f", lastParsedTps));
        }
        // Players
        chartPoint(playerSeries, monitorTick, onlinePlayers.size());
        playerValueLabel.setText(String.valueOf(onlinePlayers.size()));
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

        // All 16 Minecraft colors in one row of swatches
        HBox colors = new HBox();
        colors.getStyleClass().add("motd-swatch-row");
        for (int i = 0; i < 16; i++) {
            final String code = "&" + MC_CODES[i];
            Button b = new Button();
            b.getStyleClass().add("motd-swatch");
            b.setStyle("-fx-background-color:" + MC_HEXES[i] + ";"); // the swatch IS the data, so inline is right here
            b.setTooltip(new Tooltip(code + "   " + MC_HEXES[i]));
            b.setOnAction(e -> insertMotdCode(code));
            colors.getChildren().add(b);
        }

        HBox formats = new HBox();
        formats.getStyleClass().add("motd-swatch-row");
        String[][] fmts = {{"&l", "Bold"}, {"&o", "Italic"}, {"&n", "Underline"}, {"&m", "Strike"}, {"&r", "Reset"}};
        for (String[] f : fmts) {
            final String code = f[0];
            Button b = new Button(f[1]);
            b.getStyleClass().add("chip");
            if (code.equals("&r")) b.getStyleClass().add("chip-danger");
            b.setOnAction(e -> insertMotdCode(code));
            formats.getChildren().add(b);
        }

        Label colLbl = new Label("COLORS");     colLbl.getStyleClass().add("caption");
        Label fmtLbl = new Label("FORMATTING"); fmtLbl.getStyleClass().add("caption");
        motdPaletteContainer.getChildren().setAll(colLbl, colors, fmtLbl, formats);
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

        javafx.scene.paint.Color color = MOTD_DEFAULT; // the server list shows uncolored MOTD text in gray
        boolean bold = false, italic = false, ul = false, st = false;
        StringBuilder seg = new StringBuilder();
        String raw = motdRawField.getText();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            // Only a real format code counts — "Tom & Jerry" must keep its "& "
            boolean isCode = (c == '&' || c == '§') && i + 1 < raw.length()
                && MOTD_CODES.indexOf(Character.toLowerCase(raw.charAt(i + 1))) >= 0;
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
                // In Java Edition a color code also clears bold/italic/etc.
                if ("0123456789abcdef".indexOf(k) >= 0) bold = italic = ul = st = false;
                if (k == 'l') bold = true;
                else if (k == 'o') italic = true;
                else if (k == 'n') ul = true;
                else if (k == 'm') st = true;
                else if (k == 'r') {
                    color = MOTD_DEFAULT;
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

    private static final String MOTD_CODES = "0123456789abcdefklmnor";
    private static final javafx.scene.paint.Color MOTD_DEFAULT = javafx.scene.paint.Color.web("#AAAAAA");

    @FXML private void applyMotdToProperties() {
        if (serverDirectory == null || currentProps == null) {
            appendSystemLog("> Set a server directory (and start the server once) first."); return;
        }
        // Only &+code becomes §; a plain "&" stays. The § is written as § when saving.
        String motd = motdRawField.getText().strip().replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
        Control ctrl = propControls.get("motd");
        if (ctrl instanceof TextField tf) tf.setText(motd.replace("\n", "\\n"));
        savePropertiesFromForm();
        appendSystemLog("> MOTD applied.");
    }

    /** Shows the server's current MOTD in the editor, with § codes turned back into & codes. */
    private void loadMotdEditor() {
        if (motdRawField == null) return;
        String motd = currentProps == null ? null : currentProps.get("motd");
        motdRawField.setText(motd == null ? "" : motd.replace('§', '&'));
        renderMotdPreview();
        // Preview with the server's real icon when it has one (server-icon.png, 64×64)
        File icon = serverDirectory == null ? null : new File(serverDirectory, "server-icon.png");
        motdIconView.setImage(icon != null && icon.isFile()
            ? new Image(icon.toURI().toString(), 48, 48, true, false) : logoView.getImage());
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

                File statsDir = new File(new File(serverDirectory, levelName()), "stats");
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
                        if (cm > 0) dist = String.format("%.2f km", cm / 100_000.0);
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
            // Stat files (1.13+) namespace every key: "minecraft:deaths": 3 — the old pattern needed a bare "deaths"
            Matcher m = Pattern.compile("\"(?:minecraft:)?" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
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

    public void setStage(Stage s) {
        this.stage = s;
        s.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, e -> playIntro()); // also when restored from the tray
        s.iconifiedProperty().addListener((obs, o, n) -> updateEffectsPaused());
        s.showingProperty().addListener((obs, o, n) -> updateEffectsPaused());
    }

    // Window dragging only starts from the title bar's empty space / labels — not from its buttons,
    // which used to move the window (or, with double-click-to-maximize, toggle it) when clicked.
    private boolean dragging = false;

    @FXML private void handleWindowPress(MouseEvent e) {
        dragging = stage != null && e.isPrimaryButtonDown() && isTitleBarBackground(e);
        if (!dragging) return;
        if (e.getClickCount() == 2) { toggleMaximize(); dragging = false; return; }
        dragX = stage.getX() - e.getScreenX(); dragY = stage.getY() - e.getScreenY();
    }

    @FXML private void handleWindowDrag(MouseEvent e) {
        if (!dragging) return;
        if (isWindowMaximized()) {
            // Dragging a maximized window restores it under the cursor, like a normal Windows title bar
            double ratio = (e.getScreenX() - stage.getX()) / stage.getWidth();
            toggleMaximize();
            dragX = -ratio * stage.getWidth();
            dragY = stage.getY() - e.getScreenY();
        }
        stage.setX(e.getScreenX() + dragX); stage.setY(e.getScreenY() + dragY);
    }

    private static boolean isTitleBarBackground(MouseEvent e) {
        javafx.scene.Node n = e.getPickResult().getIntersectedNode();
        while (n != null && n != e.getSource()) {
            if (n instanceof Control && !(n instanceof Label) && !(n instanceof Separator)) return false;
            n = n.getParent();
        }
        return true;
    }

    @FXML private void handleMinimize()  { if (stage != null) stage.setIconified(true); }
    @FXML private void handleMaximize()  { toggleMaximize(); }

    // Stage.setMaximized() on an undecorated window covers the whole screen, taskbar included.
    // Fill the screen's *visual* bounds (minus the taskbar) ourselves instead.
    private javafx.geometry.Rectangle2D restoreBounds;

    public boolean isWindowMaximized() { return restoreBounds != null; }

    private void toggleMaximize() {
        if (stage == null) return;
        if (restoreBounds != null) {
            stage.setX(restoreBounds.getMinX());   stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth()); stage.setHeight(restoreBounds.getHeight());
            restoreBounds = null;
        } else {
            restoreBounds = new javafx.geometry.Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            javafx.geometry.Rectangle2D vb = javafx.stage.Screen
                .getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                .stream().findFirst().orElse(javafx.stage.Screen.getPrimary()).getVisualBounds();
            stage.setX(vb.getMinX()); stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth()); stage.setHeight(vb.getHeight());
        }
    }
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
        } else requestQuit();
    }

    /** Quit the launcher. A running server is stopped cleanly first so the world gets saved. */
    private void requestQuit() {
        if (serverProcess == null || !serverProcess.isAlive()) { exitApp(); return; }
        if (quitAfterStop) return; // already stopping

        Alert confirm = themed(new Alert(Alert.AlertType.CONFIRMATION));
        confirm.setTitle("Quit");
        confirm.setHeaderText("The server is still running");
        confirm.setContentText("It will be stopped and the world saved before the launcher closes.");
        ButtonType stopAndQuit = new ButtonType("Stop Server & Quit", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(stopAndQuit, ButtonType.CANCEL);
        if (stage != null && stage.isShowing()) confirm.initOwner(stage);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == stopAndQuit) beginQuitAfterStop();
    }

    private void beginQuitAfterStop() {
        quitAfterStop = true;
        isRestarting  = false;
        showMainWindow(); // let the user watch the shutdown, even when quitting from the tray
        appendSystemLog("> Stopping server before quitting — saving world...");
        handleStopServer();
        // Safety net: if the server hangs, let the user decide instead of waiting forever.
        // runLater because showAndWait() is not allowed inside an animation callback.
        quitTimeout = new Timeline(new KeyFrame(Duration.seconds(60),
            e -> Platform.runLater(this::askForceQuit)));
        quitTimeout.play();
    }

    private void askForceQuit() {
        Process p = serverProcess;
        if (p == null || !p.isAlive()) return;
        Alert a = themed(new Alert(Alert.AlertType.WARNING));
        a.setTitle("Server not responding");
        a.setHeaderText("The server has not stopped after 60 seconds");
        a.setContentText("Force quitting kills it immediately — any unsaved world changes will be lost.");
        ButtonType keepWaiting = new ButtonType("Keep Waiting", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType forceQuit   = new ButtonType("Force Quit",   ButtonBar.ButtonData.OK_DONE);
        a.getButtonTypes().setAll(keepWaiting, forceQuit);
        if (stage != null && stage.isShowing()) a.initOwner(stage);
        if (a.showAndWait().orElse(keepWaiting) == forceQuit) {
            appendSystemLog("> Force-killing server.");
            p.destroyForcibly();
            exitApp();
        } else quitTimeout.playFromStart();
    }

    private void exitApp() {
        if (quitTimeout != null) quitTimeout.stop();
        cleanup(); Platform.exit(); System.exit(0);
    }

    /**
     * Shutdown hook — last resort for when the launcher dies with the server still up
     * (Windows logoff, process killed). Asks the server to save and stop; a hard kill would lose
     * world data, and leaving it running would orphan a hidden server holding the port.
     */
    private void stopServerOnShutdown() {
        Process p = serverProcess;
        if (p == null || !p.isAlive()) return;
        try {
            OutputStream in = serverInput;
            if (in != null) { in.write("stop\n".getBytes()); in.flush(); }
        } catch (IOException ignored) {}
        try { if (!p.waitFor(20, TimeUnit.SECONDS)) p.destroyForcibly(); }
        catch (InterruptedException e) { p.destroyForcibly(); }
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
    // SETTINGS → SERVER TAB
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleSelectServerDirectory() {
        if (serverIsBusy("change the server folder")) return;
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Minecraft Server Folder");
        File sel = dc.showDialog(serverPathField.getScene().getWindow());
        if (sel != null) {
            serverDirectory = sel;
            serverPathField.setText(sel.getAbsolutePath());
            prefs.node("profiles").node(currentProfile).put("serverPath", sel.getAbsolutePath()); // save now, not only on exit
            appendSystemLog("> Server folder: " + sel.getAbsolutePath());
            String jar = serverJarField.getText().trim();
            if (jar.isEmpty()) jar = "server.jar";
            if (!new File(sel, jar).exists()) {
                File[] jars = sel.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jars != null && jars.length == 1) {
                    serverJarField.setText(jars[0].getName());
                    appendSystemLog("> Using the server JAR found in the folder: " + jars[0].getName());
                } else {
                    appendSystemLog("[WARN] '" + jar + "' not found in this folder — set the JAR name below, "
                        + "or use ⬇ Download Server JAR.");
                }
            }
            refreshJavaHint();
            loadServerProperties();
            loadWhitelist();
            loadBanList();
        }
    }

    @FXML private void handleClearServerDirectory() {
        if (serverIsBusy("change the server folder")) return;
        serverDirectory = null; serverPathField.clear();
        prefs.node("profiles").node(currentProfile).remove("serverPath");
        loadServerProperties();
        whitelistView.getItems().clear(); banListView.getItems().clear();
        appendSystemLog("> Server directory cleared.");
    }

    /** True (and explains why) if the server is running, so settings it depends on must not change. */
    private boolean serverIsBusy(String action) {
        if (launchPending || (serverProcess != null && serverProcess.isAlive())) {
            appendSystemLog("> Stop the server before you " + action + ".");
            return true;
        }
        return false;
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

    @FXML
    private void handleEffectsToggle() {
        prefs.putBoolean("uiEffects", effectsCheck.isSelected());
        effects.setEnabled(effectsCheck.isSelected());
    }

    // ════════════════════════════════════════════════════════════════
    // SETTINGS → WORLD CONFIG TAB (PREMIUM PROPERTIES EDITOR)
    // ════════════════════════════════════════════════════════════════

    private void loadServerProperties() {
        if (serverDirectory == null) {
            currentProps = null; propsText = null;
            buildPropertiesForm();
            propertiesEditor.setText("# Please select a server directory.");
            return;
        }
        File f = new File(serverDirectory, "server.properties");
        if (!f.exists()) {
            currentProps = null; propsText = null;
            buildPropertiesForm();
            propertiesEditor.setText("# server.properties not found — start the server once to generate it.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            propsCharset = propertiesCharset(bytes);
            propsText    = new String(bytes, propsCharset);
            propertiesEditor.setText(propsText);
            currentProps = parsePropertiesContent(propsText);
            buildPropertiesForm();
            loadMotdEditor();
        } catch (IOException e) {
            propertiesEditor.setText("# Error reading file: " + e.getMessage());
            currentProps = null; propsText = null;
            buildPropertiesForm();
        }
    }

    /** server.properties may be UTF-8 (newer servers) or ISO-8859-1 (classic Java .properties). */
    private static java.nio.charset.Charset propertiesCharset(byte[] bytes) {
        try {
            java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return java.nio.charset.StandardCharsets.UTF_8;
        } catch (java.nio.charset.CharacterCodingException e) {
            return java.nio.charset.StandardCharsets.ISO_8859_1;
        }
    }

    private static String readPropertiesFile(File f) throws IOException {
        byte[] bytes = Files.readAllBytes(f.toPath());
        return new String(bytes, propertiesCharset(bytes));
    }

    /** key → value, with .properties escapes (\\n, \\u00A7, \\\\ …) decoded. */
    private static Map<String, String> parsePropertiesContent(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq).trim(), unescapeProperty(line.substring(eq + 1).stripLeading()));
        }
        return map;
    }

    private static String unescapeProperty(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                        catch (NumberFormatException e) { sb.append('u'); }
                    } else sb.append('u');
                }
                default -> sb.append(n); // \\ → \, \: → :, \= → =
            }
        }
        return sb.toString();
    }

    /**
     * Escapes a value for server.properties. Non-ASCII (like the § in colored MOTDs) becomes \\uXXXX,
     * which every Minecraft version reads correctly regardless of the file's charset.
     */
    private static String escapePropertyValue(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (i == 0 && c == ' ') sb.append("\\ ");
                    else if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04X", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Applies the form's values to a server.properties text. Only lines whose value actually changed
     * are rewritten (comments, order and unknown keys stay as they were). Keys the file lacks — common
     * with older server versions — are appended if the user changed them from the default.
     */
    private String mergeFormInto(String text) {
        Map<String, String> form = new LinkedHashMap<>();
        for (Map.Entry<String, Control> e : propControls.entrySet()) form.put(e.getKey(), getControlValue(e.getValue()));

        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : text.split("\\R", -1)) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("!") && eq > 0) {
                String k = t.substring(0, eq).trim();
                seen.add(k);
                String nv = form.get(k);
                if (nv != null && !nv.equals(unescapeProperty(t.substring(eq + 1).stripLeading()))) {
                    out.add(k + "=" + escapePropertyValue(nv));
                    continue;
                }
            }
            out.add(line);
        }
        int insertAt = !out.isEmpty() && out.get(out.size() - 1).isEmpty() ? out.size() - 1 : out.size();
        for (String[] def : PROP_DEFS) {
            String key = def[0], v = form.get(key);
            if (key.isEmpty() || seen.contains(key) || v == null || v.equals(def[3])) continue;
            out.add(insertAt++, key + "=" + escapePropertyValue(v));
        }
        return String.join(nl, out);
    }

    private void buildPropertiesForm() {
        propertiesFormContainer.getChildren().clear();
        propControls.clear();

        if (currentProps == null) {
            propertiesFormContainer.getChildren().add(emptyState("globe", "Nothing to edit yet",
                serverDirectory == null ? "Choose your server folder in the Server tab first."
                                        : "Start the server once — it creates server.properties."));
            return;
        }

        // One card per section (Network, Gameplay, …); each setting is a row with its key underneath
        VBox section = null;
        for (String[] def : PROP_DEFS) {
            String key  = def[0];
            String label = def[1];
            String type = def[2];
            String defaultVal = def[3];

            if (key.isEmpty()) {
                Label hdr = new Label(label);
                hdr.getStyleClass().add("props-section-title");
                section = new VBox(hdr);
                section.getStyleClass().add("props-section");
                propertiesFormContainer.getChildren().add(section);
                continue;
            }

            String currentVal = currentProps.getOrDefault(key, defaultVal);

            Control control;
            if (type.equals("bool")) {
                CheckBox cb = new CheckBox();
                cb.setSelected("true".equalsIgnoreCase(currentVal));
                control = cb;
            } else if (type.startsWith("choice:")) {
                String[] choices = type.substring(7).split(",");
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(choices);
                combo.setValue(currentVal);
                if (combo.getValue() == null && choices.length > 0) combo.setValue(choices[0]);
                combo.setPrefWidth(240);
                control = combo;
            } else {
                // "text" or "int"
                TextField tf = new TextField(currentVal.replace("\n", "\\n"));
                if (type.equals("int")) tf.getStyleClass().add("number-field");
                tf.setPrefWidth(type.equals("int") ? 110 : 300);
                tf.setMaxWidth(type.equals("int") ? 110 : 300);
                control = tf;
            }

            propControls.put(key, control);

            Label nameLabel = new Label(label);
            Label keyLabel = new Label(key);
            keyLabel.getStyleClass().addAll("muted", "mono");
            VBox names = new VBox(1, nameLabel, keyLabel);
            names.getStyleClass().add("props-label");

            HBox row = new HBox(names, control);
            row.getStyleClass().add("props-row");
            if (section == null) { section = new VBox(); section.getStyleClass().add("props-section"); propertiesFormContainer.getChildren().add(section); }
            if (section.getChildren().size() == 1) row.getStyleClass().add("first"); // no divider under the title
            section.getChildren().add(row);
        }
    }

    @FXML
    private void handleSavePropertiesForm() {
        if (serverDirectory == null) return;
        if (rawEditToggle != null && rawEditToggle.isSelected()) {
            if (propsText == null) { appendSystemLog("> No server.properties loaded — start the server once to create it."); return; }
            String text = fromEditor(propertiesEditor.getText());
            if (writePropertiesFile(text, "> Properties saved (raw).")) {
                // Rebuild the form too, or a later form save would write its stale values over these edits
                propsText = text;
                currentProps = parsePropertiesContent(text);
                buildPropertiesForm();
                loadMotdEditor();
            }
        } else {
            savePropertiesFromForm();
        }
    }

    private void savePropertiesFromForm() {
        if (serverDirectory == null || currentProps == null || propsText == null) {
            appendSystemLog("> No server.properties loaded — start the server once to create it.");
            return;
        }
        String merged = mergeFormInto(propsText);
        if (writePropertiesFile(merged, "> Properties saved.")) {
            propsText = merged;
            currentProps = parsePropertiesContent(merged);
            propertiesEditor.setText(merged);
        }
    }

    private boolean writePropertiesFile(String text, String successMsg) {
        try {
            Files.writeString(new File(serverDirectory, "server.properties").toPath(), text, propsCharset);
            appendSystemLog(successMsg + (serverProcess != null && serverProcess.isAlive()
                ? " Restart the server to apply." : ""));
            return true;
        } catch (IOException e) {
            appendSystemLog("[ERROR] Could not save server.properties: " + e.getMessage());
            return false;
        }
    }

    private String getControlValue(Control ctrl) {
        if (ctrl instanceof CheckBox cb) return cb.isSelected() ? "true" : "false";
        if (ctrl instanceof ComboBox<?> cb) return cb.getValue() != null ? cb.getValue().toString() : "";
        // Text fields are single-line, so newlines (e.g. a two-line MOTD) are shown as a literal \n
        if (ctrl instanceof TextField tf) return tf.getText().replace("\\n", "\n");
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
        if (propsText == null) return;
        // Carry unsaved edits across the switch instead of silently dropping them
        if (raw) {
            propsText = mergeFormInto(propsText);
            propertiesEditor.setText(propsText);
        } else {
            propsText = fromEditor(propertiesEditor.getText());
            currentProps = parsePropertiesContent(propsText);
            buildPropertiesForm();
        }
    }

    /** TextArea turns \r\n into \n — put the file's own line endings back before saving. */
    private String fromEditor(String editorText) {
        String text = editorText.replace("\r\n", "\n");
        return propsText != null && propsText.contains("\r\n") ? text.replace("\n", "\r\n") : text;
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
            prefs.node("profiles").node(currentProfile).put("backupPath", sel.getAbsolutePath());
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
        prefs.node("profiles").node(currentProfile).putBoolean("autoBackupEnabled", autoBackupCheck.isSelected());
        if (autoBackupCheck.isSelected()) { appendSystemLog("> Auto-Backups ENABLED (every 24 hours)."); startBackupScheduler(); }
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
        if (restoreInProgress) { appendSystemLog("> A restore is already running."); return; }
        Alert confirm = themed(new Alert(Alert.AlertType.CONFIRMATION));
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("Restore from: " + selected);
        confirm.setContentText(
            "The current world folders will be renamed (not deleted).\n" +
            "The server will be stopped if it is running.\n\nProceed?");
        confirm.showAndWait().filter(b -> b == ButtonType.OK)
            .ifPresent(b -> performRestore(new File(backupDirectory, selected)));
    }

    private void performRestore(File zipFile) {
        restoreInProgress = true;
        Process running = serverProcess != null && serverProcess.isAlive() ? serverProcess : null;
        appendSystemLog("> Restore starting: " + zipFile.getName());
        if (running != null) { appendSystemLog("> Stopping server..."); sendServerCommand("stop"); }

        Thread t = new Thread(() -> {
            List<File[]> renamed = new ArrayList<>(); // {original, renamedTo} — for rollback
            try {
                if (running != null && !running.waitFor(120, TimeUnit.SECONDS))
                    throw new IOException("the server did not stop within 2 minutes — nothing was changed.");

                // Move aside every top-level folder the backup will recreate (world, world_nether, ...)
                String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                for (String name : zipTopLevelFolders(zipFile)) {
                    File current = new File(serverDirectory, name);
                    if (!current.exists()) continue;
                    File aside = new File(serverDirectory, name + "_pre_restore_" + ts);
                    if (!current.renameTo(aside)) {
                        // Never extract on top of a live world — that silently merges two worlds
                        throw new IOException("could not rename '" + name
                            + "' (is a file in it open in another program?) — nothing was changed.");
                    }
                    renamed.add(new File[]{current, aside});
                    Platform.runLater(() -> appendSystemLog("> Renamed existing " + name + " to: " + aside.getName()));
                }
                Platform.runLater(() -> appendSystemLog("> Extracting backup…"));
                extractZip(zipFile, serverDirectory);
                Platform.runLater(() -> appendSystemLog("> Restore complete from: " + zipFile.getName()));
            } catch (Exception e) {
                // Put the original folders back if we failed before/while extracting
                for (File[] r : renamed) {
                    deleteRecursively(r[0]);
                    r[1].renameTo(r[0]);
                }
                Platform.runLater(() -> appendSystemLog("[ERROR] Restore FAILED: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> restoreInProgress = false);
            }
        });
        t.setDaemon(true); t.start();
    }

    /** Names of the top-level folders inside a backup ZIP (e.g. world, world_nether, world_the_end). */
    private static Set<String> zipTopLevelFolders(File zipFile) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                int slash = n.indexOf('/');
                if (slash > 0) names.add(n.substring(0, slash));
            }
        }
        if (names.isEmpty()) throw new IOException("the backup ZIP is empty.");
        return names;
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        f.delete();
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

    /** Runs on a background thread (manual "Backup Now" or the 24 h scheduler). */
    private void triggerBackgroundBackup() {
        File serverDir = serverDirectory, backupDir = backupDirectory;
        if (serverDir == null || backupDir == null) return;
        if (!backupLock.tryLock()) { appendSystemLog("> A backup is already running."); return; }
        try {
            // level-name plus Paper/Spigot's separate Nether and End folders
            List<File> worlds = new ArrayList<>();
            String level = levelName();
            for (String suffix : new String[]{"", "_nether", "_the_end"}) {
                File w = new File(serverDir, level + suffix);
                if (w.isDirectory()) worlds.add(w);
            }
            if (worlds.isEmpty()) {
                appendSystemLog("[ERROR] Backup skipped: no '" + level + "' folder found — start the server once to create the world.");
                return;
            }

            boolean running = serverProcess != null && serverProcess.isAlive();
            if (running) {
                // Pause autosave and flush everything to disk, so no chunk is half-written while we zip
                CountDownLatch saved = new CountDownLatch(1);
                saveLatch = saved;
                sendServerCommand("save-off");
                sendServerCommand("save-all flush");
                try {
                    if (!saved.await(120, TimeUnit.SECONDS))
                        appendSystemLog("[WARN] Server did not confirm the save — backing up anyway.");
                } catch (InterruptedException ignored) {}
                saveLatch = null;
            }

            String names = worlds.stream().map(File::getName).reduce((a, b) -> a + ", " + b).orElse("");
            appendSystemLog("> Zipping " + names + "...");
            String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File zipFile  = new File(backupDir, "world_backup_" + ts + ".zip");
            File partFile = new File(backupDir, "world_backup_" + ts + ".zip.part");
            try {
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(partFile))) {
                    for (File w : worlds) zipDirectory(w, w.getName(), zos);
                }
                Files.move(partFile.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                appendSystemLog("> Backup complete: " + zipFile.getName()
                    + " (" + (zipFile.length() / 1_048_576) + " MB)");
                // Prune only after a backup succeeded (never lose old backups because a new one failed),
                // and refresh the list afterwards so it shows what is really left on disk
                cleanOldBackups(backupDir);
                Platform.runLater(this::refreshBackupList);
            } catch (Exception e) {
                partFile.delete();
                appendSystemLog("[ERROR] Backup FAILED: " + e.getMessage());
            } finally {
                if (running) sendServerCommand("save-on");
            }
        } finally {
            backupLock.unlock();
        }
    }

    /** level-name from server.properties on disk (the world folder name), default "world". */
    private String levelName() {
        if (serverDirectory != null) {
            File f = new File(serverDirectory, "server.properties");
            try {
                String v = parsePropertiesContent(readPropertiesFile(f)).get("level-name");
                if (v != null && !v.isBlank()) return v.trim();
            } catch (IOException ignored) {}
        }
        return "world";
    }

    /**
     * Deletes the oldest world_backup_*.zip files so only the newest "Keep the newest N" remain
     * (0 or less = keep everything). The timestamp in the name sorts oldest-first.
     */
    private void cleanOldBackups(File dir) {
        int keep;
        try { keep = Integer.parseInt(prefs.node("profiles").node(currentProfile).get("maxBackups", "5").trim()); }
        catch (NumberFormatException e) { keep = 5; }
        if (keep <= 0) return;
        File[] files = dir.listFiles((d, n) -> n.startsWith("world_backup_") && n.endsWith(".zip"));
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        int removed = 0;
        for (int i = 0; i < files.length - keep; i++) {
            File f = files[i];
            try {
                Files.delete(f.toPath());
                removed++;
                appendSystemLog("> Removed old backup: " + f.getName());
            } catch (IOException e) {
                // Used to fail silently, so backups piled up without anyone noticing
                appendSystemLog("[WARN] Could not delete old backup " + f.getName() + ": " + e.getMessage());
            }
        }
        if (removed > 0) appendSystemLog("> Kept the newest " + keep + " backups (removed " + removed + ").");
    }

    private void zipDirectory(File folder, String path, ZipOutputStream zos) throws IOException {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) zipDirectory(f, path + "/" + f.getName(), zos);
            // session.lock is held open by a running server on Windows — copying it fails the whole backup
            else if (f.getName().equals("session.lock")) continue;
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
        if (enabled && parseRestartTime(scheduledRestartField.getText()) == null) {
            appendSystemLog("> Invalid restart time '" + scheduledRestartField.getText().trim()
                + "' — use 24-hour HH:mm, e.g. 04:00.");
            scheduledRestartCheck.setSelected(false);
            enabled = false;
        }
        prefs.putBoolean("scheduledRestart", enabled);
        if (enabled) startRestartScheduler();
        else if (restartTimeline != null) { stopRestartScheduler(); appendSystemLog("> Scheduled restart disabled."); }
    }

    /** Saves the edited time, flags it red while invalid, and reschedules live once it is valid. */
    private void onRestartTimeEdited(String text) {
        prefs.put("scheduledRestartTime", text.trim());
        boolean valid = parseRestartTime(text) != null;
        scheduledRestartField.getStyleClass().remove("field-invalid");
        if (!valid) scheduledRestartField.getStyleClass().add("field-invalid");
        if (valid && restartTimeline != null) startRestartScheduler();
    }

    private static LocalTime parseRestartTime(String text) {
        try { return LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("H:mm")); }
        catch (DateTimeParseException e) { return null; }
    }

    private void startRestartScheduler() {
        stopRestartScheduler();
        LocalTime time = parseRestartTime(prefs.get("scheduledRestartTime", "04:00"));
        if (time == null) return;
        nextScheduledRestart = nextOccurrence(time, LocalDateTime.now(clock));
        restartWarningSent   = false;
        appendSystemLog("> Next scheduled restart: "
            + nextScheduledRestart.format(DateTimeFormatter.ofPattern("EEE HH:mm")));
        // Poll every 10 s against a fixed target time. (A once-a-minute check drifts and can
        // skip or double-hit a minute — the old version never got to its restart step.)
        restartTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> scheduledRestartTick()));
        restartTimeline.setCycleCount(Timeline.INDEFINITE);
        restartTimeline.play();
    }

    private void stopRestartScheduler() {
        if (restartTimeline != null) { restartTimeline.stop(); restartTimeline = null; }
    }

    private static LocalDateTime nextOccurrence(LocalTime time, LocalDateTime now) {
        LocalDateTime t = now.toLocalDate().atTime(time);
        return t.isAfter(now) ? t : t.plusDays(1);
    }

    /** Warns players 1 minute before the scheduled time, then restarts at that time. */
    private void scheduledRestartTick() {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean running = serverProcess != null && serverProcess.isAlive();

        if (!restartWarningSent && !now.isBefore(nextScheduledRestart.minusMinutes(1))) {
            restartWarningSent = true;
            if (running && now.isBefore(nextScheduledRestart)) {
                appendSystemLog("> Scheduled restart in 1 minute...");
                sendServerCommand("say [Server] Restarting in 1 minute...");
            }
        }
        if (now.isBefore(nextScheduledRestart)) return;

        if (now.isAfter(nextScheduledRestart.plusMinutes(5))) {
            // The PC was asleep through the restart time — don't restart at a random later moment
            if (running) appendSystemLog("> Missed the scheduled restart at "
                + nextScheduledRestart.toLocalTime() + " (computer was asleep) — skipping.");
        } else if (running) {
            appendSystemLog("> Executing scheduled restart.");
            handleRestartServer();
        }
        nextScheduledRestart = nextOccurrence(nextScheduledRestart.toLocalTime(), now);
        restartWarningSent   = false;
    }

    // ════════════════════════════════════════════════════════════════
    // CORE SERVER CONTROLS
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleStartServer() {
        // A manual Start replaces any crash auto-restart that is still counting down
        if (pendingCrashRestart != null) { pendingCrashRestart.stop(); pendingCrashRestart = null; }
        // The tray menu can call this too, so guard against launching a second server
        if (launchPending || (serverProcess != null && serverProcess.isAlive())) {
            appendSystemLog("> Server is already running."); return;
        }
        if (restoreInProgress) { appendSystemLog("> Wait for the backup restore to finish first."); setOfflineUI(); return; }
        if (serverDirectory == null) {
            appendSystemLog("[ERROR] No server folder selected — choose one in Settings → Server."); setOfflineUI(); return;
        }
        if (!serverDirectory.isDirectory()) {
            appendSystemLog("[ERROR] Server folder not found: " + serverDirectory.getAbsolutePath()
                + " — it may have been moved or deleted. Pick it again in Settings → Server.");
            setOfflineUI(); return;
        }

        // ── Pre-start checks: catch the common first-run problems before launching ──
        String jarName = serverJarField.getText().trim();
        if (jarName.isEmpty()) jarName = "server.jar";
        if (!new File(serverDirectory, jarName).isFile()) {
            appendSystemLog("[ERROR] '" + jarName + "' was not found in the server folder. Use ⬇ Download Server JAR "
                + "in Settings → Server, or fix the JAR name there.");
            setOfflineUI(); return;
        }
        JavaChoice java = chooseJava(new File(serverDirectory, jarName));
        if (java.error() != null) {
            appendSystemLog("[ERROR] " + java.error());
            setOfflineUI(); return;
        }
        String mem = memoryField.getText().trim();
        if (!mem.matches("\\d+") || Integer.parseInt(mem) < 1) {
            appendSystemLog("> Memory setting '" + mem + "' is not a whole number of GB — using 2 GB.");
            mem = "2";
        }
        long totalMB = totalSystemMemoryMB();
        if (totalMB > 0 && Long.parseLong(mem) * 1024 > totalMB) {
            appendSystemLog("[ERROR] Max Memory is " + mem + " GB but this PC only has " + (totalMB / 1024)
                + " GB of RAM. Lower it in Settings → Performance.");
            setOfflineUI(); return;
        }
        if (!eulaAccepted() && !askToAcceptEula(false)) { setOfflineUI(); return; }

        currentJarName = jarName;
        currentMemGB   = mem;
        currentJavaExe = java.exe();
        currentUseOptimizedFlags = optimizedFlagsCheck.isSelected();
        launchPending  = true;

        appendSystemLog("> Starting " + currentJarName + " with " + currentMemGB + "GB"
            + (currentUseOptimizedFlags ? " + Aikar's flags" : "") + "...");
        appendSystemLog("> Using Java " + (java.version() > 0 ? java.version() + " " : "") + "— " + currentJavaExe);
        startButton.setDisable(true); stopButton.setDisable(false);
        restartButton.setDisable(false); commandInput.setDisable(false);
        setStatus("Starting", "starting");
        serverStartMillis = System.currentTimeMillis(); uptimeClock.play();
        lastParsedTps = Double.NaN; startMonitorTimeline();
        onlinePlayers.clear(); updateCounterUI();
        Thread t = new Thread(this::runServerProcess); t.setDaemon(true); t.start();
    }

    @FXML private void handleStopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            appendSystemLog("> Sending shutdown command...");
            startButton.setDisable(true); stopButton.setDisable(true);
            restartButton.setDisable(true); commandInput.setDisable(true);
            setStatus(isRestarting ? "Restarting" : "Stopping", "busy");
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
        cmd.add(currentJavaExe);
        cmd.add("-Xmx" + currentMemGB + "G");
        cmd.add("-Xms" + currentMemGB + "G");
        // Make the server print UTF-8 so player names / chat with accents aren't garbled (ignored by Java < 18)
        cmd.add("-Dstdout.encoding=UTF-8"); cmd.add("-Dstderr.encoding=UTF-8");
        if (currentUseOptimizedFlags) Collections.addAll(cmd, AIKARS_FLAGS);
        cmd.add("-jar"); cmd.add(currentJarName); cmd.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(serverDirectory);
        pb.redirectErrorStream(true);
        try {
            serverProcess = pb.start();
        } catch (IOException e) {
            Platform.runLater(() -> onServerFailedToStart(e));
            return;
        }
        serverInput = serverProcess.getOutputStream();
        Platform.runLater(() -> launchPending = false);

        List<String> recentLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                recentLines.add(line);
                if (recentLines.size() > 30) recentLines.remove(0);
                CountDownLatch latch = saveLatch;
                if (latch != null && line.contains("Saved the game")) latch.countDown();
                if (line.contains("You need to agree to the EULA")) eulaRequired = true;
                queueServerLine(line);
            }
        } catch (IOException e) {
            // Output pipe broke — still wait for the process below so the UI never gets stuck
            Platform.runLater(() -> appendSystemLog("[WARN] Lost server output: " + e.getMessage()));
        }

        int code;
        try { code = serverProcess.waitFor(); }
        catch (InterruptedException e) { code = -1; }
        final int exitCode = code;
        final List<String> snapshot = new ArrayList<>(recentLines);
        Platform.runLater(() -> {
            serverInput = null;
            resetRunningIndicators();
            appendSystemLog("> Server process exited (code " + exitCode + ").");
            if (quitAfterStop) { exitApp(); return; }
            if (isRestarting) { isRestarting = false; handleStartServer(); return; }
            setOfflineUI();
            if (eulaRequired) { eulaRequired = false; askToAcceptEula(true); return; }

            boolean graceful = snapshot.stream().anyMatch(l ->
                l.contains("Stopping server") || l.contains("Saving worlds") || l.contains("Goodbye"));
            if (graceful || exitCode == 0) { quickCrashCount = 0; return; }

            setStatus("Crashed", "crashed");
            String fatal = fatalStartupError(snapshot);
            if (fatal != null) {
                // Wrong Java, bad memory setting, missing JAR… restarting would fail identically
                quickCrashCount = 0;
                appendSystemLog("[ERROR] " + fatal);
                if (crashRestartCheck.isSelected())
                    appendSystemLog("> Auto-restart skipped — it would fail the same way until this is fixed.");
                return;
            }
            boolean quick = System.currentTimeMillis() - serverStartMillis < QUICK_CRASH_MS;
            quickCrashCount = quick ? quickCrashCount + 1 : 1;
            if (!crashRestartCheck.isSelected()) {
                appendSystemLog("[ERROR] The server crashed — see the Diagnostics tab for hints.");
                return;
            }
            if (quickCrashCount > MAX_QUICK_CRASHES) {
                // Restarting a server that dies on boot just loops forever — stop and let the user fix it
                appendSystemLog("[ERROR] The server crashed " + MAX_QUICK_CRASHES + " times in a row right after "
                    + "starting — auto-restart paused. Check the Diagnostics tab, fix the problem, then press Start.");
                quickCrashCount = 0;
                return;
            }
            int delay;
            try { delay = Math.max(1, Integer.parseInt(crashRestartDelayField.getText().trim())); }
            catch (NumberFormatException ex) { delay = 10; }
            appendSystemLog("> Crash detected! Auto-restarting in " + delay + "s... (press Start to restart now)");
            pendingCrashRestart = new PauseTransition(Duration.seconds(delay));
            pendingCrashRestart.setOnFinished(e -> {
                pendingCrashRestart = null;
                // runLater: handleStartServer may show a dialog, which isn't allowed inside an animation callback
                if (crashRestartCheck.isSelected()) Platform.runLater(this::handleStartServer);
            });
            pendingCrashRestart.play();
        });
    }

    /**
     * Recognises a server that could not boot for a reason a restart won't fix, from its last output
     * lines. Returns a plain-English explanation, or null for an ordinary crash.
     */
    private String fatalStartupError(List<String> lines) {
        String all = String.join("\n", lines);
        if (all.contains("UnsupportedClassVersionError")) {
            // "...class file version 69.0), this version of the Java Runtime only recognizes ... up to 65.0"
            Matcher need = Pattern.compile("class file version (\\d+)").matcher(all);
            Matcher have = Pattern.compile("up to (\\d+)").matcher(all);
            if (need.find() && have.find()) {
                int n = Integer.parseInt(need.group(1)) - 44, h = Integer.parseInt(have.group(1)) - 44;
                return "This server needs Java " + n + " or newer, but it was started with Java " + h + ". "
                    + "Install Java " + n + "+ from https://adoptium.net, or pick a newer Java in "
                    + "Settings → Performance → Java (or click Auto there).";
            }
            return "This server needs a newer Java version. Install a newer Java from https://adoptium.net, "
                + "or pick one in Settings → Performance → Java.";
        }
        if (all.contains("Unable to access jarfile"))
            return "Java could not open the server JAR — check the JAR name in Settings → Server.";
        if (all.contains("Could not reserve enough space") || all.contains("Invalid maximum heap size")
                || all.contains("Invalid initial heap size"))
            return "Java could not reserve " + currentMemGB + " GB of memory — lower Max Memory in Settings → Performance.";
        if (all.contains("Unrecognized VM option"))
            return "This Java doesn't support one of the JVM flags — try turning off Aikar's flags in Settings → Performance.";
        if (all.contains("Could not create the Java Virtual Machine"))
            return "Java failed to start — see the lines above for the reason.";
        return null;
    }

    // ─── Server output → UI, in batches ──────────────────────────────
    // One Platform.runLater per log line floods the FX thread (a server prints thousands of lines while
    // booting) and freezes the UI. Lines are queued here and drained in one pass per UI pulse instead.

    private void queueServerLine(String line) {
        pendingLines.add(line);
        if (drainScheduled.compareAndSet(false, true)) Platform.runLater(this::drainServerLines);
    }

    private void drainServerLines() {
        drainScheduled.set(false);
        List<String> batch = new ArrayList<>();
        for (String l; (l = pendingLines.poll()) != null; ) batch.add(l);
        if (batch.isEmpty()) return;

        boolean follow = isConsoleAtBottom();
        boolean playersChanged = false, booted = false;
        for (String line : batch) {
            if (line.contains("> ") && CHAT_LINE.matcher(line).find()) chatLogs.add(line);
            playersChanged |= trackPlayers(line);
            checkDiagnostics(line);
            parseTpsFromLog(line);
            booted |= line.contains("Done (") && line.contains(")! For help");
        }
        // "Starting" → "Online" once the server says it's ready for players
        if (booted && statusLabel.getStyleClass().contains("status-starting")) setStatus("Online", "online");
        allLogs.addAll(batch);
        trimLogs();
        if (chatLogs.size() > MAX_CHAT) chatLogs.remove(0, chatLogs.size() - MAX_CHAT);
        if (playersChanged) updateCounterUI();
        if (follow) scrollConsoleToBottom();
    }

    // Anchored to the log prefix ("[12:00:00 INFO]: " / "[12:00:00] [Server thread/INFO]: "), so a chat
    // message such as "<Bob> Alex joined the game" can't add a fake player. Minecraft 26+ prefixes
    // these with "System chat: ".
    private static final Pattern JOIN_LEAVE = Pattern.compile(
        "^(?:\\[[^\\]]*\\] ?)+: (?:System chat: )?(\\.?\\w{1,16}) (joined|left) the game\\s*$");

    /** Updates the online-player list from a join/leave line; returns true if it changed. */
    private boolean trackPlayers(String line) {
        if (!line.contains(" the game")) return false;
        Matcher m = JOIN_LEAVE.matcher(line);
        if (!m.find()) return false;
        String name = m.group(1);
        if (m.group(2).equals("joined")) {
            if (onlinePlayers.contains(name)) return false;
            // Give a head that failed last time (network blip, rate limit, new skin) another chance
            Image old = headCache.get(name);
            if (old != null && old.isError()) headCache.remove(name);
            freshPlayers.add(name);
            onlinePlayers.add(name);
            return true;
        }
        return onlinePlayers.remove(name);
    }

    /** The java process could not be launched at all — undo the "RUNNING" state and say why. */
    private void onServerFailedToStart(IOException e) {
        launchPending = false;
        isRestarting = false;
        resetRunningIndicators();
        setOfflineUI();
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("error=2") || msg.contains("No such file"))
            appendSystemLog("[ERROR] Failed to start: Java was not found. Minecraft servers need Java "
                + "installed (Java 21+ for current versions) — get it from https://adoptium.net, "
                + "then click Start again.");
        else
            appendSystemLog("[ERROR] Failed to start server: " + msg);
    }

    // ─── Java detection ──────────────────────────────────────────────

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String  JAVA_EXE = WINDOWS ? "java.exe" : "java";

    /** The Java chosen for a server JAR: its path and major version, or an explanation of why none fits. */
    private record JavaChoice(String exe, int version, String error) {}

    /**
     * Picks the Java to run this server JAR with. A path set in Settings → Performance wins (but is
     * checked against the JAR's requirement). Otherwise every Java on this PC is considered and the
     * closest version that is new enough is used. (Taking simply the first java on the PATH picked up
     * e.g. Eclipse's bundled Java 21 for a server that needs Java 25.)
     */
    private JavaChoice chooseJava(File jar) {
        int need = jar != null && jar.isFile() ? requiredJavaFor(jar) : 0;
        String configured = javaPathField.getText().trim();
        if (!configured.isEmpty()) {
            File f = new File(configured);
            if (f.isDirectory()) f = new File(f, "bin" + File.separator + JAVA_EXE); // a JDK folder works too
            if (!f.isFile())
                return new JavaChoice(null, 0, "The Java path in Settings → Performance does not exist: " + configured);
            int v = javaVersionOf(f);
            if (need > 0 && v > 0 && v < need)
                return new JavaChoice(null, v, "This server needs Java " + need + " or newer, but the Java set in "
                    + "Settings → Performance is Java " + v + ". Choose a newer one there, or click Auto.");
            return new JavaChoice(f.getAbsolutePath(), v, null);
        }

        List<File> all = javaCandidates();
        if (all.isEmpty())
            return new JavaChoice(null, 0, "Java was not found. Minecraft servers need Java installed (Java "
                + (need > 0 ? need : 21) + "+ for this server) — get it from https://adoptium.net, "
                + "or set the Java path in Settings → Performance.");
        File best = null; int bestV = 0;
        if (need > 0) {
            // The oldest Java that is new enough — closest to what Mojang built and tested the server with
            for (File f : all) {
                int v = javaVersionOf(f);
                if (v >= need && (best == null || v < bestV)) { best = f; bestV = v; }
            }
            if (best == null) {
                int newest = all.stream().mapToInt(LauncherController::javaVersionOf).max().orElse(0);
                return new JavaChoice(null, newest, "This server needs Java " + need + " or newer, but the newest "
                    + "Java on this PC is Java " + newest + ". Install Java " + need + "+ from https://adoptium.net, "
                    + "then press Start again.");
            }
        } else {
            // Requirement unknown: the newest Java runs the widest range of server versions
            for (File f : all) {
                int v = javaVersionOf(f);
                if (best == null || v > bestV) { best = f; bestV = v; }
            }
        }
        return new JavaChoice(best.getAbsolutePath(), bestV, null);
    }

    /** Every java executable on this PC: JAVA_HOME, the PATH, then the usual install folders. */
    private static List<File> javaCandidates() {
        Map<String, File> found = new LinkedHashMap<>(); // keyed by path to drop duplicates
        String home = System.getenv("JAVA_HOME");
        if (home != null && !home.isBlank()) addIfExists(found, new File(home, "bin" + File.separator + JAVA_EXE));
        String path = System.getenv("PATH");
        if (path != null)
            for (String dir : path.split(File.pathSeparator))
                if (!dir.isBlank()) addIfExists(found, new File(dir.trim(), JAVA_EXE));
        if (WINDOWS) {
            String[] vendors = {"Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft",
                                "Amazon Corretto", "Semeru", "OpenJDK"};
            for (String root : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
                if (root == null) continue;
                for (String vendor : vendors) {
                    File[] installs = new File(root, vendor).listFiles(File::isDirectory);
                    if (installs != null)
                        for (File dir : installs) addIfExists(found, new File(dir, "bin" + File.separator + JAVA_EXE));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void addIfExists(Map<String, File> found, File exe) {
        if (exe.isFile()) found.putIfAbsent(exe.getAbsolutePath().toLowerCase(), exe);
    }

    private static final Map<String, Integer> JAVA_VERSIONS = new ConcurrentHashMap<>();

    /** Major version of a java executable (8, 17, 21, 25…), or 0 if it can't be determined. Cached. */
    private static int javaVersionOf(File exe) {
        return JAVA_VERSIONS.computeIfAbsent(exe.getAbsolutePath().toLowerCase(), k -> probeJavaVersion(exe));
    }

    private static int probeJavaVersion(File exe) {
        // Fast path: every JDK/JRE has a "release" file:  JAVA_VERSION="21.0.10"
        File release = new File(exe.getAbsoluteFile().getParentFile().getParentFile(), "release");
        if (release.isFile()) {
            try {
                for (String l : Files.readAllLines(release.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1))
                    if (l.startsWith("JAVA_VERSION=")) return javaMajor(l.substring(13).replace("\"", "").trim());
            } catch (IOException ignored) {}
        }
        // Stub launchers (e.g. Oracle's ...\Common Files\Oracle\Java\javapath) have none — ask the exe
        try {
            Process p = new ProcessBuilder(exe.getAbsolutePath(), "-version").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor(5, TimeUnit.SECONDS);
            Matcher m = Pattern.compile("version \"([^\"]+)\"").matcher(out);
            if (m.find()) return javaMajor(m.group(1));
        } catch (Exception ignored) {}
        return 0;
    }

    /** "1.8.0_511" → 8, "21.0.10" → 21, "26" → 26. */
    private static int javaMajor(String version) {
        Matcher m = Pattern.compile("^(\\d+)(?:\\.(\\d+))?").matcher(version);
        if (!m.find()) return 0;
        int major = Integer.parseInt(m.group(1));
        return major == 1 && m.group(2) != null ? Integer.parseInt(m.group(2)) : major;
    }

    /** The Java version a server JAR needs, or 0 if it doesn't say. */
    private static int requiredJavaFor(File jar) {
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar)) {
            // Vanilla and Paper JARs carry Mojang's version.json:  "java_version": 25
            ZipEntry vj = jf.getEntry("version.json");
            if (vj != null) {
                try (InputStream in = jf.getInputStream(vj)) {
                    Matcher m = Pattern.compile("\"java_version\"\\s*:\\s*(\\d+)")
                        .matcher(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    if (m.find()) return Integer.parseInt(m.group(1));
                }
            }
            // Fabric's server launcher only records the Minecraft version:  game-version=26.3
            ZipEntry ip = jf.getEntry("install.properties");
            if (ip != null) {
                Properties p = new Properties();
                try (InputStream in = jf.getInputStream(ip)) { p.load(in); }
                return javaForMinecraft(p.getProperty("game-version", ""));
            }
        } catch (IOException ignored) {}
        return 0;
    }

    /** Mojang's minimum Java for a Minecraft version, or 0 if the version isn't recognised. */
    private static int javaForMinecraft(String mc) {
        Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(mc.trim());
        if (!m.find()) return 0;
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
        int c = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        if (a >= 26) return 25;                          // year-numbered releases (26.1+)
        if (a != 1)  return 0;
        if (b > 20 || (b == 20 && c >= 5)) return 21;    // 1.20.5 – 1.21.x
        if (b >= 18) return 17;                          // 1.18 – 1.20.4
        if (b == 17) return 16;
        return 8;
    }

    private File currentJarFile() {
        if (serverDirectory == null) return null;
        String jar = serverJarField.getText().trim();
        return new File(serverDirectory, jar.isEmpty() ? "server.jar" : jar);
    }

    private void refreshJavaHint() {
        if (javaDetectedLabel == null) return;
        File jar = currentJarFile();
        int need = jar != null && jar.isFile() ? requiredJavaFor(jar) : 0;
        JavaChoice c = chooseJava(jar);
        String needs = need > 0 ? "  (" + jar.getName() + " needs Java " + need + "+)" : "";
        String text;
        if (c.error() != null) text = "✖ " + c.error();
        else if (javaPathField.getText().isBlank()) text = "Auto-detected: Java " + c.version() + " — " + c.exe() + needs;
        else text = "✔ Using Java " + (c.version() > 0 ? c.version() : "(unknown version)") + needs;
        setHint(javaDetectedLabel, text, c.error() != null ? "error" : javaPathField.getText().isBlank() ? null : "ok");
    }

    @FXML private void handleBrowseJava() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select java" + (WINDOWS ? ".exe" : ""));
        if (WINDOWS) fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("java.exe", "java.exe"));
        File sel = fc.showOpenDialog(javaPathField.getScene().getWindow());
        if (sel != null) javaPathField.setText(sel.getAbsolutePath());
    }

    @FXML private void handleAutoJava() { javaPathField.clear(); }

    private static long totalSystemMemoryMB() {
        try {
            return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / 1_048_576L;
        } catch (Exception e) { return -1; }
    }

    // ─── EULA ────────────────────────────────────────────────────────

    private boolean eulaAccepted() {
        File f = new File(serverDirectory, "eula.txt");
        if (!f.isFile()) return false;
        try {
            return Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1).stream()
                .anyMatch(l -> l.trim().replace(" ", "").equalsIgnoreCase("eula=true"));
        } catch (IOException e) { return false; }
    }

    /**
     * Minecraft refuses to start until eula.txt says eula=true, which used to mean a failed first run
     * and hand-editing a file. Asks the user once; on accept writes eula.txt (and optionally starts).
     */
    private boolean askToAcceptEula(boolean startAfter) {
        Alert a = themed(new Alert(Alert.AlertType.CONFIRMATION));
        a.setTitle("Minecraft EULA");
        a.setHeaderText("Accept the Minecraft End User License Agreement?");
        Hyperlink link = new Hyperlink("https://aka.ms/MinecraftEULA");
        link.setOnAction(e -> openInBrowser("https://aka.ms/MinecraftEULA"));
        Label text = new Label("Mojang requires every server owner to agree to the EULA before the server "
            + "can run. Accepting sets eula=true in eula.txt.");
        text.setWrapText(true);
        a.getDialogPane().setContent(new VBox(8, text, link));
        ButtonType accept = new ButtonType("I Accept", ButtonBar.ButtonData.OK_DONE);
        a.getButtonTypes().setAll(accept, ButtonType.CANCEL);
        if (stage != null && stage.isShowing()) a.initOwner(stage);
        if (a.showAndWait().orElse(ButtonType.CANCEL) != accept) {
            appendSystemLog("> EULA not accepted — the server cannot start until it is.");
            return false;
        }
        try {
            writeEulaAccepted();
            appendSystemLog("> EULA accepted (eula.txt updated).");
        } catch (IOException e) {
            appendSystemLog("[ERROR] Could not write eula.txt: " + e.getMessage());
            return false;
        }
        if (startAfter) handleStartServer();
        return true;
    }

    private void writeEulaAccepted() throws IOException {
        File f = new File(serverDirectory, "eula.txt");
        List<String> out = new ArrayList<>();
        boolean replaced = false;
        if (f.isFile()) {
            for (String l : Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
                if (l.trim().toLowerCase().startsWith("eula")) { out.add("eula=true"); replaced = true; }
                else out.add(l);
            }
        } else {
            out.add("#By changing the setting below to TRUE you are indicating your agreement to our EULA "
                + "(https://aka.ms/MinecraftEULA).");
        }
        if (!replaced) out.add("eula=true");
        Files.write(f.toPath(), out, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private void openInBrowser(String url) {
        Thread t = new Thread(() -> {
            try { java.awt.Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception e) { appendSystemLog("> Open this link in your browser: " + url); }
        });
        t.setDaemon(true); t.start();
    }

    private void resetRunningIndicators() {
        uptimeClock.stop(); uptimeLabel.setText(""); stopMonitorTimeline();
        onlinePlayers.clear(); updateCounterUI();
    }

    private void setOfflineUI() {
        startButton.setDisable(false); stopButton.setDisable(true);
        restartButton.setDisable(true); commandInput.setDisable(true);
        setStatus("Offline", "offline");
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

    // These go through the server console (it looks up the player's UUID), so the server must be running.
    // Success is only reported if the command was actually sent.

    @FXML private void handleAddToWhitelist() {
        String name = playerNameFrom(whitelistAddField); if (name == null) return;
        if (sendServerCommand("whitelist add " + name)) {
            appendSystemLog("> Whitelisted: " + name);
            whitelistAddField.clear(); delayedRefresh(this::loadWhitelist);
        }
    }
    @FXML private void handleRemoveFromWhitelist() {
        String sel = whitelistView.getSelectionModel().getSelectedItem();
        if (sel == null) { appendSystemLog("> Select a player first."); return; }
        if (sendServerCommand("whitelist remove " + sel)) {
            appendSystemLog("> Removed: " + sel); delayedRefresh(this::loadWhitelist);
        }
    }
    @FXML private void handleAddBan() {
        String name = playerNameFrom(banAddField); if (name == null) return;
        if (sendServerCommand("ban " + name)) {
            appendSystemLog("> Banned: " + name);
            banAddField.clear(); delayedRefresh(this::loadBanList);
        }
    }
    @FXML private void handlePardon() {
        String sel = banListView.getSelectionModel().getSelectedItem();
        if (sel == null) { appendSystemLog("> Select a player first."); return; }
        if (sendServerCommand("pardon " + sel)) {
            appendSystemLog("> Pardoned: " + sel); delayedRefresh(this::loadBanList);
        }
    }

    /** The trimmed name in the field, or null (with a message) if it can't be a Minecraft name. */
    private String playerNameFrom(TextField field) {
        String name = field.getText().trim();
        if (name.isEmpty()) return null;
        if (!name.matches("\\.?\\w{1,16}")) {
            appendSystemLog("> '" + name + "' is not a valid player name (letters, digits and _ only, max 16).");
            return null;
        }
        return name;
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
            setRconStatus("Disconnected", "bad");
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
                    setRconStatus("Connected — " + fHost + ":" + fPort, "good");
                    rconOutputList.getItems().add("> Connected to " + fHost + ":" + fPort);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setRconStatus("Failed: " + ex.getMessage(), "bad");
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
                    setRconStatus("Disconnected (error)", "bad");
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
        dialog.setTitle("Download a server");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(startButton.getScene().getWindow());
        dialog.getIcons().setAll(stage != null ? stage.getIcons() : List.of());
        dialog.setResizable(false);

        // Server type as three selectable cards
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton paperRb   = new RadioButton("Paper");
        RadioButton vanillaRb = new RadioButton("Vanilla");
        RadioButton fabricRb  = new RadioButton("Fabric");
        HBox typeBox = new HBox(10,
            choiceCard(paperRb,   "Fast, with plugins · recommended", typeGroup),
            choiceCard(vanillaRb, "Official Mojang server", typeGroup),
            choiceCard(fabricRb,  "Lightweight mod loader", typeGroup));
        paperRb.setSelected(true);

        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Loading versions…");
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setVisibleRowCount(12);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE); progressBar.setVisible(false);
        progressBar.managedProperty().bind(progressBar.visibleProperty()); // no empty gap until a download starts

        Label statusLbl = styled("", "hint");
        statusLbl.setWrapText(true);

        Button dlBtn     = styledButton("Download", "download", "btn-primary");
        Button cancelBtn = styledButton("Close", null, "btn-ghost");
        dlBtn.setDisable(true);
        cancelBtn.setOnAction(e -> dialog.close());

        // Versions load by themselves when the window opens and whenever the type changes
        Runnable loadVersions = () -> {
            String type = paperRb.isSelected() ? "paper" : vanillaRb.isSelected() ? "vanilla" : "fabric";
            setHint(statusLbl, "Fetching the version list…", null);
            dlBtn.setDisable(true); versionCombo.getItems().clear();
            versionCombo.setPromptText("Loading versions…");
            Thread t = new Thread(() -> {
                try {
                    List<String> vers = fetchVersions(type);
                    Platform.runLater(() -> {
                        versionCombo.getItems().setAll(vers);
                        if (!vers.isEmpty()) versionCombo.setValue(vers.get(0));
                        setHint(statusLbl, vers.size() + " versions available — the newest is selected.", null);
                        dlBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        versionCombo.setPromptText("Couldn't load versions");
                        setHint(statusLbl, "Error: " + ex.getMessage(), "error");
                    });
                }
            }); t.setDaemon(true); t.start();
        };
        typeGroup.selectedToggleProperty().addListener((o, a, b) -> { if (b != null) loadVersions.run(); });

        dlBtn.setOnAction(e -> {
            String version = versionCombo.getValue(); if (version == null || version.isBlank()) return;
            String type = paperRb.isSelected() ? "paper" : vanillaRb.isSelected() ? "vanilla" : "fabric";
            progressBar.setProgress(-1); progressBar.setVisible(true);
            dlBtn.setDisable(true);
            setHint(statusLbl, "Downloading…", null);
            Thread t = new Thread(() -> {
                try {
                    downloadServerJar(type, version, serverDirectory,
                        p -> Platform.runLater(() -> progressBar.setProgress(p)),
                        filename -> Platform.runLater(() -> {
                            progressBar.setProgress(1.0);
                            setHint(statusLbl, "Downloaded " + filename + " — it's now set as your server JAR.", "ok");
                            dlBtn.setDisable(false);
                            serverJarField.setText(filename);
                            appendSystemLog("> Downloaded: " + filename);
                        }));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        setHint(statusLbl, "Download failed: " + ex.getMessage(), "error");
                        progressBar.setVisible(false); dlBtn.setDisable(false);
                    });
                }
            }); t.setDaemon(true); t.start();
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancelBtn, dlBtn);
        VBox root = new VBox(
            styled("Download a server", "sheet-title"),
            styled("The JAR goes straight into " + serverDirectory.getName() + " and is checksum-verified.", "muted"),
            styled("SERVER TYPE", "caption"), typeBox,
            styled("VERSION", "caption"), versionCombo,
            progressBar, statusLbl, buttons);
        VBox.setMargin(buttons, new Insets(6, 0, 0, 0));
        root.getStyleClass().add("sheet");
        root.setPrefWidth(620);
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web("#131922")); // no white flash while it fades in
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dialog.setScene(scene);
        root.setOpacity(0);
        dialog.setOnShown(e -> Motion.dialogIn(root));
        dialog.show();
        loadVersions.run();
    }

    /** One selectable card (radio + description) for the downloader's server-type choice. */
    private static VBox choiceCard(RadioButton rb, String description, ToggleGroup group) {
        rb.setToggleGroup(group);
        rb.getStyleClass().add("choice-title");
        Label desc = styled(description, "muted");
        desc.setWrapText(true);
        VBox card = new VBox(4, rb, desc);
        card.setPrefWidth(180);
        card.getStyleClass().add("choice-card");
        card.setOnMouseClicked(e -> rb.setSelected(true)); // the whole card is clickable
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        rb.selectedProperty().addListener((o, was, sel) -> {
            card.getStyleClass().remove("choice-selected");
            if (sel) card.getStyleClass().add("choice-selected");
        });
        return card;
    }

    /** kind: null (neutral) | "ok" | "error". */
    private static void setHint(Label label, String text, String kind) {
        label.setText(text);
        label.getStyleClass().removeAll("hint-ok", "hint-error");
        if (kind != null) label.getStyleClass().add("hint-" + kind);
    }

    private List<String> fetchVersions(String type) throws Exception {
        List<String> vers = new ArrayList<>();
        switch (type) {
            case "paper" -> {
                // v3 groups versions newest-first: {"26.3":["26.3","26.3-rc-3"],"1.21":[...]}
                String json = fetchJson(PAPER_API);
                int start = json.indexOf("\"versions\"");
                if (start == -1) throw new Exception("No versions in Paper API response.");
                Matcher arr = Pattern.compile("\\[([^\\]]*)\\]").matcher(json.substring(start));
                while (arr.find()) {
                    // Only plain release numbers — skips -rc / -pre builds
                    Matcher m = Pattern.compile("\"(\\d+(?:\\.\\d+)*)\"").matcher(arr.group(1));
                    while (m.find()) vers.add(m.group(1));
                }
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
        String downloadUrl, filename, expectedSha256 = null;
        switch (type) {
            case "paper" -> {
                String build = fetchJson(PAPER_API + "/versions/" + version + "/builds/latest");
                int dl = build.indexOf("\"server:default\"");
                if (dl == -1) throw new Exception("No server download for Paper " + version);
                String obj = build.substring(dl);
                filename       = jsonString(obj, "name");
                downloadUrl    = jsonString(obj, "url");
                expectedSha256 = jsonString(obj, "sha256");
                if (filename == null || downloadUrl == null)
                    throw new Exception("Could not parse Paper download for " + version);
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
                // Newest *stable* loader/installer — the first entry in these lists can be a beta
                String loaderVer = firstStableVersion(fetchJson("https://meta.fabricmc.net/v2/versions/loader"));
                String installerVer = firstStableVersion(fetchJson("https://meta.fabricmc.net/v2/versions/installer"));
                if (loaderVer == null || installerVer == null)
                    throw new Exception("Could not find a stable Fabric loader/installer.");
                downloadUrl = "https://meta.fabricmc.net/v2/versions/loader/" + version
                            + "/" + loaderVer + "/" + installerVer + "/server/jar";
                filename = "fabric-server-mc." + version + "-loader." + loaderVer + ".jar";
            }
            default -> throw new Exception("Unknown server type: " + type);
        }

        // The name comes from a remote API — never let it point outside the server folder
        if (!filename.matches("[\\w.+\\-]+\\.jar"))
            throw new Exception("Refusing suspicious file name: " + filename);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(downloadUrl))
                              .header("User-Agent", USER_AGENT).build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode() + " for " + filename);

        // Download to a .part file first so a failed download never leaves a broken JAR behind
        long contentLength = resp.headers().firstValueAsLong("content-length").orElse(-1);
        File destFile = new File(destDir, filename);
        File partFile = new File(destDir, filename + ".part");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        try {
            try (InputStream is = resp.body(); FileOutputStream fos = new FileOutputStream(partFile)) {
                byte[] buf = new byte[8192]; long downloaded = 0, lastUpdate = 0; int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n); sha256.update(buf, 0, n); downloaded += n;
                    if (contentLength > 0 && downloaded - lastUpdate > 102_400) {
                        lastUpdate = downloaded; onProgress.accept((double) downloaded / contentLength);
                    }
                }
            }
            if (expectedSha256 != null
                    && !HexFormat.of().formatHex(sha256.digest()).equalsIgnoreCase(expectedSha256))
                throw new Exception("Checksum mismatch — download was corrupted, please retry.");
            Files.move(partFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            partFile.delete(); // no-op after a successful move
        }
        onDone.accept(filename);
    }

    private String fetchJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                              .header("User-Agent", USER_AGENT).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode() + " from " + url);
        return resp.body();
    }

    /** "version" of the first object in a Fabric meta list that has "stable": true. */
    private static String firstStableVersion(String json) {
        Matcher obj = Pattern.compile("\\{[^{}]*\\}").matcher(json);
        while (obj.find()) {
            String o = obj.group();
            if (o.matches("(?s).*\"stable\"\\s*:\\s*true.*")) {
                String v = jsonString(o, "version");
                if (v != null) return v;
            }
        }
        return null;
    }

    /** First {@code "key": "value"} string value in the JSON fragment, or null. */
    private static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
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
        for (String cmd : raw.split("\n")) {
            cmd = cmd.trim();
            if (cmd.isEmpty()) continue;
            if (!sendServerCommand(cmd)) return; // server not running — already explained in the log
            count++;
        }
        appendSystemLog("> Ran macro '" + name + "' — " + count + " command(s).");
    }

    @FXML private void handleAddMacro() {
        Dialog<ButtonType> dlg = themed(new Dialog<>());
        dlg.setTitle("New macro");
        dlg.setHeaderText("New command macro");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Morning reset");
        TextArea cmdsArea = new TextArea(); cmdsArea.setPrefRowCount(6);
        cmdsArea.setPromptText("say Good morning!\nweather clear\ntime set day");
        cmdsArea.getStyleClass().add("code-area");
        VBox content = new VBox(8, styled("Name", "field-label"), nameField,
                                   styled("Commands — one per line, without the /", "field-label"), cmdsArea);
        content.setPrefWidth(440);
        dlg.getDialogPane().setContent(content);
        ButtonType save = new ButtonType("Save macro", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Platform.runLater(nameField::requestFocus);
        dlg.showAndWait().filter(r -> r == save).ifPresent(r -> {
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

    @SuppressWarnings("unchecked") // the FXML column is declared without type arguments
    private static final double AVATAR = 32;
    private static final String[] AVATAR_COLORS =
        {"#0ea5e9", "#8b5cf6", "#f59e0b", "#10b981", "#ef4444", "#ec4899", "#14b8a6", "#6366f1"};

    private void setupPlayerList() {
        playerList.setItems(onlinePlayers);
        playerList.setPlaceholder(emptyState("users", "Nobody online", "Players show up here as they join."));
        playerList.setCellFactory(lv -> new ListCell<>() {
            // A colored initial sits under the skin head, so the row looks right while the head
            // loads — and stays tidy for names the skin services don't know (offline / Bedrock).
            private final Label     letter = new Label();
            private final ImageView head   = new ImageView();
            private final StackPane avatar = new StackPane(letter, head);
            private final Label     name   = new Label();
            private final Label     sub    = new Label("Online");
            private final HBox      row    = new HBox(avatar, new VBox(1, name, sub));
            {
                head.setFitWidth(AVATAR); head.setFitHeight(AVATAR);
                head.setSmooth(false); // pixel art: keep it crisp
                Rectangle clip = new Rectangle(AVATAR, AVATAR);
                clip.setArcWidth(12); clip.setArcHeight(12);
                head.setClip(clip);
                avatar.getStyleClass().add("avatar");
                letter.getStyleClass().add("avatar-letter");
                name.getStyleClass().add("player-name");
                sub.getStyleClass().add("player-sub");
                row.getStyleClass().add("player-row");
            }

            @Override protected void updateItem(String player, boolean empty) {
                super.updateItem(player, empty);
                setText(null);
                if (empty || player == null) { setGraphic(null); setContextMenu(null); return; }
                name.setText(player);
                String base = player.startsWith(".") ? player.substring(1) : player;
                letter.setText(base.isEmpty() ? "?" : base.substring(0, 1).toUpperCase());
                avatar.setStyle("-fx-background-color:" + AVATAR_COLORS[Math.floorMod(player.hashCode(), AVATAR_COLORS.length)] + ";");
                head.setImage(headFor(player));
                setGraphic(row);
                if (freshPlayers.remove(player)) Motion.enter(row, 22, 0, 520, 0); // just joined
                else Motion.settle(row); // a recycled cell must not stay half-faded

                MenuItem kick = new MenuItem("Kick", new Icon("close", 14));
                MenuItem ban  = new MenuItem("Ban", new Icon("ban", 14));
                ban.getStyleClass().add("danger-item");
                kick.setOnAction(e -> sendServerCommand("kick " + player));
                ban.setOnAction(e -> sendServerCommand("ban " + player));
                setContextMenu(new ContextMenu(kick, ban));
            }
        });
    }

    // ─── Skin heads ──────────────────────────────────────────────────
    // Tried in order; if one is down or rate-limits us, the next is used.
    private static final String[] HEAD_SOURCES = {
        "https://minotar.net/helm/%s/64.png",
        "https://mc-heads.net/avatar/%s/64"
    };

    /** The player's skin head (cached). A failed download is NOT cached forever — see trackPlayers(). */
    private Image headFor(String player) {
        return headCache.computeIfAbsent(player, p -> loadHead(p, 0));
    }

    private Image loadHead(String player, int source) {
        String lookup = player.startsWith(".") ? player.substring(1) : player; // Floodgate's Bedrock prefix
        String url = String.format(HEAD_SOURCES[source],
            java.net.URLEncoder.encode(lookup, java.nio.charset.StandardCharsets.UTF_8));
        Image img = new Image(url, 64, 64, true, false, true);
        Runnable onError = () -> {
            if (headCache.get(player) != img) return; // superseded
            if (source + 1 < HEAD_SOURCES.length) headCache.put(player, loadHead(player, source + 1));
            playerList.refresh(); // shows the next source, or just the colored initial if all failed
        };
        img.errorProperty().addListener((obs, was, failed) -> { if (failed) Platform.runLater(onError); });
        if (img.isError()) Platform.runLater(onError);
        return img;
    }

    // ════════════════════════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════════════════════════

    @FXML private void handleSendCommand() {
        String cmd = commandInput.getText().trim(); if (cmd.isEmpty()) return;
        commandHistory.addFirst(cmd); if (commandHistory.size() > 50) commandHistory.removeLast();
        historyIndex = -1;
        if (sendServerCommand(cmd)) commandInput.clear();
    }

    /**
     * Writes a command to the server console. Returns false (and says so in the log) when the server
     * is not running, so callers never report success for a command that went nowhere.
     * Synchronized: the backup thread and the FX thread can both send commands.
     */
    private synchronized boolean sendServerCommand(String command) {
        Process p = serverProcess; OutputStream in = serverInput;
        if (p == null || !p.isAlive() || in == null) {
            appendSystemLog("> Server is not running — '" + command + "' was not sent.");
            return false;
        }
        try {
            in.write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            in.flush();
            return true;
        } catch (IOException e) {
            appendSystemLog("> Command error: " + e.getMessage());
            return false;
        }
    }

    /** Adds one launcher-generated line (not server output) to the console. */
    private void addLogLine(String line) {
        boolean follow = isConsoleAtBottom();
        allLogs.add(line);
        trimLogs();
        if (follow) scrollConsoleToBottom();
    }

    private void trimLogs() {
        if (allLogs.size() > MAX_LOGS) allLogs.remove(0, allLogs.size() - TRIM_TO);
    }

    /** True when the console is scrolled to the end — only then do new lines auto-scroll it. */
    private boolean isConsoleAtBottom() {
        for (javafx.scene.Node n : consoleList.lookupAll(".scroll-bar"))
            if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                return !sb.isVisible() || sb.getValue() >= sb.getMax() - 0.001;
        return true;
    }

    private void scrollConsoleToBottom() {
        if (!filteredLogs.isEmpty()) consoleList.scrollTo(filteredLogs.size() - 1);
    }

    private void parseTpsFromLog(String line) {
        // Paper/Spigot /tps: "TPS from last 1m, 5m, 15m: *20.0*, *20.0*, *20.0*"
        if (line.contains("TPS from last")) {
            int colon = line.lastIndexOf(':');
            if (colon < 0) return;
            String[] parts = line.substring(colon + 1).replaceAll("[^0-9.]", " ").trim().split("\\s+");
            try { lastParsedTps = Math.min(20.0, Double.parseDouble(parts[0])); }
            catch (NumberFormatException ignored) {}
            return;
        }
        // Vanilla 1.20.3+ /tick query: "Average time per tick: 12.3ms (Target: 50.0ms)"
        Matcher m = TICK_QUERY.matcher(line);
        if (m.find()) {
            try {
                double mspt = Double.parseDouble(m.group(1)), target = Double.parseDouble(m.group(2));
                if (mspt > 0) lastParsedTps = Math.min(1000.0 / target, 1000.0 / mspt);
            } catch (NumberFormatException ignored) {}
        }
    }
    private static final Pattern TICK_QUERY =
        Pattern.compile("Average time per tick: ([\\d.]+)ms \\(Target: ([\\d.]+)ms\\)");

    private void appendSystemLog(String message) {
        if (Platform.isFxApplicationThread()) addLogLine(message);
        else Platform.runLater(() -> addLogLine(message));
    }

    private void updateCounterUI() {
        String count = String.valueOf(onlinePlayers.size());
        if (!count.equals(playerCountLabel.getText()) && stage != null && stage.isShowing())
            Motion.pop(counterContainer, 1.2);
        playerCountLabel.setText(count);
        counterContainer.getStyleClass().remove("count-zero");
        if (onlinePlayers.isEmpty()) counterContainer.getStyleClass().add("count-zero");
        if (playerValueLabel != null) playerValueLabel.setText(String.valueOf(onlinePlayers.size()));
    }

    // ─── Dialog style helpers ────────────────────────────────────────

    private static Label styled(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    private static Button styledButton(String text, String icon, String... classes) {
        Button b = new Button(text, icon == null ? null : new Icon(icon, 15));
        b.getStyleClass().add("btn");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().addAll(classes);
        return b;
    }

    /** Gives a dialog the app's dark theme, its icon and owner — instead of the default light look. */
    private <D extends Dialog<?>> D themed(D dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dialog.setGraphic(null); // drops the old blue "?" / "!" bubbles
        if (dialog.getOwner() == null && stage != null && stage.isShowing()) dialog.initOwner(stage);
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage s && stage != null)
            s.getIcons().setAll(stage.getIcons());
        if (pane.getScene() != null && pane.getScene().getWindow() != null) {
            // Content fades/zooms in; the window behind it is surface-colored, so there's no white flash
            pane.getScene().setFill(javafx.scene.paint.Color.web("#131922"));
            javafx.stage.Window w = pane.getScene().getWindow();
            w.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, e -> pane.setOpacity(0));
            w.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN,   e -> Motion.dialogIn(pane));
        }
        return dialog;
    }
}
