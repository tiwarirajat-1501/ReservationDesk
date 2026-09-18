package dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * DBConnection.java — Singleton JDBC Connection Manager
 *
 * Manages the SQLite database connection, loads application settings
 * from configuration files, and executes database migrations on startup.
 */
public class DBConnection {

    // ─── Singleton Instance ───────────────────────────────
    private static DBConnection instance;

    // ─── JDBC Connection ──────────────────────────────────
    private Connection connection;

    // ─── Configuration Map (loaded from app.config) ───────
    private Map<String, String> config;

    // ─── Default values ──────────────────────────────────
    private String dbPath            = "reservation_desk.db";
    private int    lockTimeoutSec    = 60;
    private int    gridRows          = 6;
    private int    gridCols          = 5;
    private int    simAgents         = 3;

    /**
     * Private constructor for singleton pattern.
     */
    private DBConnection() {
        config = new HashMap<>();
        loadConfig();       // Step 1: Read app.config
        connect();          // Step 2: Open JDBC connection
        initializeTables(); // Step 3: Execute schema.sql
    }

    /**
     * Parses configuration key-value properties from config/app.config.
     */
    private void loadConfig() {
        File configFile = new File("config/app.config");
        if (!configFile.exists()) {
            System.out.println("[CONFIG] app.config not found at '"
                    + configFile.getAbsolutePath() + "'. Using defaults.");
            return;
        }

        // ► BufferedReader wraps FileReader for efficient line-by-line reading
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Parse key=value
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }
            System.out.println("[CONFIG] Loaded " + config.size() + " settings from app.config");
        } catch (IOException e) {
            System.err.println("[CONFIG] Error reading app.config: " + e.getMessage());
        }

        // Apply parsed values (with safe defaults)
        dbPath         = config.getOrDefault("db.path", dbPath);
        lockTimeoutSec = parseIntSafe(config.get("lock.timeout.seconds"), lockTimeoutSec);
        gridRows       = parseIntSafe(config.get("grid.rows"), gridRows);
        gridCols       = parseIntSafe(config.get("grid.cols"), gridCols);
        simAgents      = parseIntSafe(config.get("sim.agents"), simAgents);

        System.out.println("[CONFIG] DB path: " + dbPath
                + " | Lock timeout: " + lockTimeoutSec + "s"
                + " | Grid: " + gridRows + "×" + gridCols
                + " | Sim agents: " + simAgents);
    }

    /**
     * Initializes the SQLite JDBC driver and establishes a database connection.
     */
    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Enable foreign key enforcement
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            System.out.println("[DB] Connected to SQLite database: " + dbPath);
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] SQLite JDBC driver not found! "
                    + "Ensure sqlite-jdbc-*.jar is in the lib/ folder.");
        } catch (SQLException e) {
            System.err.println("[DB] Connection failed: " + e.getMessage());
        }
    }

    /**
     * Executes initial table setup scripts from schema.sql.
     */
    private void initializeTables() {
        if (connection == null) return;

        File schemaFile = new File("sql/schema.sql");
        if (!schemaFile.exists()) {
            System.out.println("[DB] schema.sql not found. Creating tables inline...");
            createTablesInline();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(schemaFile));
             Statement stmt = connection.createStatement()) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.isEmpty()) continue;

                sqlBuilder.append(line).append(" ");

                if (line.endsWith(";")) {
                    stmt.execute(sqlBuilder.toString());
                    sqlBuilder.setLength(0);
                }
            }
            System.out.println("[DB] Tables initialised from schema.sql");

        } catch (IOException | SQLException e) {
            System.err.println("[DB] Schema init error: " + e.getMessage());
            System.out.println("[DB] Falling back to inline table creation...");
            createTablesInline();
        }
    }

    /**
     * Fallback table creation if schema.sql is missing or unreadable.
     */
    private void createTablesInline() {
        if (connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS vehicles ("
                    + "vehicle_id TEXT PRIMARY KEY,"
                    + "vehicle_type TEXT NOT NULL,"
                    + "route TEXT NOT NULL,"
                    + "departure TEXT NOT NULL,"
                    + "total_seats INTEGER NOT NULL,"
                    + "base_fare REAL NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS bookings ("
                    + "booking_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "vehicle_id TEXT NOT NULL,"
                    + "seat_number INTEGER NOT NULL,"
                    + "passenger TEXT NOT NULL,"
                    + "fare_paid REAL NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'CONFIRMED',"
                    + "booked_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),"
                    + "FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS waitlist ("
                    + "wait_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "vehicle_id TEXT NOT NULL,"
                    + "passenger TEXT NOT NULL,"
                    + "priority INTEGER NOT NULL DEFAULT 0,"
                    + "added_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),"
                    + "FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id))");

            // Insert sample data
            stmt.execute("INSERT OR IGNORE INTO vehicles VALUES "
                    + "('AI-101','FLIGHT','Delhi -> Mumbai','2026-09-15 06:00',30,4500.00)");
            stmt.execute("INSERT OR IGNORE INTO vehicles VALUES "
                    + "('AI-202','FLIGHT','Bangalore -> Chennai','2026-09-15 08:30',30,3200.00)");
            stmt.execute("INSERT OR IGNORE INTO vehicles VALUES "
                    + "('RAJ-001','TRAIN','Bhopal -> Delhi','2026-09-15 22:00',30,850.00)");
            stmt.execute("INSERT OR IGNORE INTO vehicles VALUES "
                    + "('SHA-002','TRAIN','Mumbai -> Pune','2026-09-15 14:15',30,450.00)");

            System.out.println("[DB] Tables created inline with default data.");
        } catch (SQLException e) {
            System.err.println("[DB] Inline table creation failed: " + e.getMessage());
        }
    }

    /**
     * Thread-safe singleton accessor.
     */
    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * Returns the live JDBC Connection for use by BookingDAO.
     */
    public Connection getConnection() {
        return connection;
    }

    /* ── Configuration Getters ─── */
    public int    getLockTimeoutSec() { return lockTimeoutSec; }
    public int    getGridRows()      { return gridRows;       }
    public int    getGridCols()      { return gridCols;       }
    public int    getSimAgents()     { return simAgents;      }

    /**
     * Gracefully closes the database connection.
     * Called when the application shuts down.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error closing connection: " + e.getMessage());
        }
        instance = null; // Allow re-creation if needed
    }

    /* ── Utility ─── */
    private int parseIntSafe(String value, int defaultVal) {
        if (value == null) return defaultVal;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
