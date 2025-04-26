package nl.svendev.fishingJournal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.svendev.fishingJournal.FishingJournal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class HikariCPManager {
    private final HikariDataSource dataSource;
    private final FishingJournal plugin;

    public HikariCPManager(FishingJournal plugin) {
        this.plugin = plugin;

        try {
            // Explicitly load SQLite driver
            Class.forName("org.sqlite.JDBC");

            // HikariCP configuration
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/fish_stats.db");
            config.setDriverClassName("org.sqlite.JDBC");
            config.setPoolName("FishingJournalPool");
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(30000);
            // Additional recommended settings for SQLite
            config.addDataSourceProperty("foreign_keys", "true");
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");

            // Initialize HikariCP data source
            this.dataSource = new HikariDataSource(config);
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load SQLite JDBC driver", e);
            throw new RuntimeException("Failed to load SQLite JDBC driver", e);
        }
    }

    private void initializeDatabase() {
        // This method ensures the tables are created when the database is initialized
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable foreign key constraints
            stmt.execute("PRAGMA foreign_keys = ON");

            // Create player names table to store usernames
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_names (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Create fish stats table with better constraints
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS emf_fish_stats (" +
                    "fish_id TEXT PRIMARY KEY, " +
                    "global_largest REAL NOT NULL DEFAULT 0, " +
                    "global_largest_owner TEXT, " +
                    "global_smallest REAL NOT NULL DEFAULT 0, " +
                    "global_smallest_owner TEXT, " +
                    "global_most_caught INTEGER NOT NULL DEFAULT 0, " +
                    "global_most_caught_owner TEXT)");

            // Create player stats table with better constraints
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS emf_player_stats (" +
                    "player_uuid TEXT NOT NULL, " +
                    "fish_id TEXT NOT NULL, " +
                    "fish_rarity TEXT NOT NULL, " +
                    "personal_largest REAL NOT NULL, " +
                    "personal_smallest REAL NOT NULL, " +
                    "times_caught INTEGER NOT NULL DEFAULT 1, " +
                    "first_caught TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (player_uuid, fish_id), " +
                    "FOREIGN KEY (fish_id) REFERENCES emf_fish_stats(fish_id) ON DELETE CASCADE)");

            // Create indexes for better performance
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_stats_uuid ON emf_player_stats(player_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_stats_fish_id ON emf_player_stats(fish_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_stats_rarity ON emf_player_stats(fish_rarity)");

            plugin.getLogger().info("Database and tables initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        // Ensure foreign keys are enabled for each connection
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
}
