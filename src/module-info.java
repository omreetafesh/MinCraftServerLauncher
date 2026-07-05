module MinCraftServerLauncher {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.prefs;
    requires java.management;   // ManagementFactory
    requires jdk.management;    // com.sun.management.OperatingSystemMXBean (CPU/RAM)
    requires java.desktop;      // java.awt.SystemTray
    requires java.net.http;     // HttpClient for JAR downloader

    opens main to javafx.fxml, javafx.graphics;
    exports main;
}
