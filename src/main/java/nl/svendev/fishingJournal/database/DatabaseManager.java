package nl.svendev.fishingJournal.database;

import nl.svendev.fishingJournal.FishingJournal;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {
    private final HikariCPManager hikariManager;
    private final FishingJournal plugin;

    public DatabaseManager(HikariCPManager hikariManager, FishingJournal plugin) {
        this.hikariManager = hikariManager;
        this.plugin = plugin;
    }

    public CompletableFuture<Void> updatePlayerName(UUID playerUuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikariManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO player_names (uuid, player_name) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET player_name = ?, last_updated = CURRENT_TIMESTAMP")) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, playerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update player name", e);
            }
        });
    }

    public CompletableFuture<Void> updateFishStats(UUID playerUuid, String fishId, String rarity, double size) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikariManager.getConnection()) {
                // First ensure the fish record exists
                initializeFishRecordSync(conn, fishId);

                // Then update player name if available
                String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
                if (playerName != null) {
                    updatePlayerName(playerUuid, playerName).join();
                }

                // Update player stats
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO emf_player_stats (player_uuid, fish_id, fish_rarity, personal_largest, personal_smallest) " +
                                "VALUES (?, ?, ?, ?, ?) " +
                                "ON CONFLICT(player_uuid, fish_id) DO UPDATE SET " +
                                "times_caught = times_caught + 1, " +
                                "personal_largest = CASE WHEN ? > personal_largest THEN ? ELSE personal_largest END, " +
                                "personal_smallest = CASE WHEN ? < COALESCE(personal_smallest, ?) THEN ? ELSE personal_smallest END")) {

                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, fishId);
                    stmt.setString(3, rarity);
                    stmt.setDouble(4, size);
                    stmt.setDouble(5, size);
                    stmt.setDouble(6, size); // For personal_largest comparison
                    stmt.setDouble(7, size); // For personal_largest update
                    stmt.setDouble(8, size); // For personal_smallest comparison
                    stmt.setDouble(9, size); // Default value for COALESCE
                    stmt.setDouble(10, size); // For personal_smallest update
                    stmt.executeUpdate();
                }

                updateGlobalStats(conn, playerUuid, fishId, size);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update fish stats", e);
                throw new RuntimeException("Failed to update fish stats", e);
            }
        });
    }

    private void updateGlobalStats(Connection conn, UUID playerUuid, String fishId, double size) throws SQLException {
        // Ensure the fish exists in global stats
        initializeFishRecordSync(conn, fishId);

        // Get player name from our dedicated table
        String playerName = getPlayerName(conn, playerUuid);

        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE emf_fish_stats SET " +
                        "global_largest = CASE WHEN global_largest IS NULL OR ? > global_largest THEN ? ELSE global_largest END, " +
                        "global_largest_owner = CASE WHEN global_largest IS NULL OR ? > global_largest THEN ? ELSE global_largest_owner END, " +
                        "global_smallest = CASE WHEN global_smallest IS NULL OR global_smallest = 0 OR ? < global_smallest THEN ? ELSE global_smallest END, " +
                        "global_smallest_owner = CASE WHEN global_smallest IS NULL OR global_smallest = 0 OR ? < global_smallest THEN ? ELSE global_smallest_owner END " +
                        "WHERE fish_id = ?")) {

            stmt.setDouble(1, size);  // largest check
            stmt.setDouble(2, size);  // largest update
            stmt.setDouble(3, size);  // largest_owner check
            stmt.setString(4, playerName); // largest_owner update

            stmt.setDouble(5, size);  // smallest check (check size < smallest)
            stmt.setDouble(6, size);  // smallest update
            stmt.setDouble(7, size);  // smallest_owner check
            stmt.setString(8, playerName); // smallest_owner update

            stmt.setString(9, fishId); // fish_id WHERE

            stmt.executeUpdate();
        }

        // Update most caught stats
        updateMostCaughtStats(conn, fishId);
    }

    private String getPlayerName(Connection conn, UUID playerUuid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT player_name FROM player_names WHERE uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("player_name");
            }
        }
        return playerUuid.toString(); // Fallback to UUID if name not found
    }

    private void updateMostCaughtStats(Connection conn, String fishId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE emf_fish_stats SET " +
                        "global_most_caught = (SELECT MAX(times_caught) FROM emf_player_stats WHERE fish_id = ?), " +
                        "global_most_caught_owner = (SELECT p.player_name FROM emf_player_stats ps " +
                        "LEFT JOIN player_names p ON ps.player_uuid = p.uuid " +
                        "WHERE ps.fish_id = ? ORDER BY ps.times_caught DESC LIMIT 1) " +
                        "WHERE fish_id = ?")) {

            stmt.setString(1, fishId);
            stmt.setString(2, fishId);
            stmt.setString(3, fishId);
            stmt.executeUpdate();
        }
    }

    public CompletableFuture<Set<String>> getPlayerFishRarities(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> rarities = new HashSet<>();
            try (Connection conn = hikariManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT DISTINCT fish_rarity FROM emf_player_stats WHERE player_uuid = ?")) {

                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String rarity = rs.getString("fish_rarity");
                    if (rarity != null) {
                        rarities.add(rarity);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch player fish rarities", e);
                throw new RuntimeException("Failed to fetch player fish rarities", e);
            }
            return rarities;
        });
    }

    public CompletableFuture<Map<String, Map<String, Object>>> getFishStatsByRarity(UUID playerUuid, String rarity) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> fishStats = new HashMap<>();
            try (Connection conn = hikariManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT ps.fish_id, ps.personal_largest, ps.personal_smallest, " +
                                 "ps.times_caught, ps.first_caught, fs.global_largest, " +
                                 "fs.global_largest_owner, fs.global_smallest, fs.global_smallest_owner, " +
                                 "fs.global_most_caught, fs.global_most_caught_owner " +
                                 "FROM emf_player_stats ps " +
                                 "LEFT JOIN emf_fish_stats fs ON ps.fish_id = fs.fish_id " +
                                 "WHERE ps.player_uuid = ? AND ps.fish_rarity = ?")) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, rarity);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("personal_largest", rs.getDouble("personal_largest"));
                    stats.put("personal_smallest", rs.getDouble("personal_smallest"));
                    stats.put("times_caught", rs.getInt("times_caught"));
                    stats.put("first_caught", rs.getTimestamp("first_caught"));

                    stats.put("global_largest", rs.getObject("global_largest", Double.class));
                    stats.put("global_largest_owner", rs.getString("global_largest_owner"));
                    stats.put("global_smallest", rs.getObject("global_smallest", Double.class));
                    stats.put("global_smallest_owner", rs.getString("global_smallest_owner"));
                    stats.put("global_most_caught", rs.getObject("global_most_caught", Integer.class));
                    stats.put("global_most_caught_owner", rs.getString("global_most_caught_owner"));

                    fishStats.put(rs.getString("fish_id"), stats);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch fish stats by rarity", e);
                throw new RuntimeException("Failed to fetch fish stats by rarity", e);
            }
            return fishStats;
        });
    }

    public CompletableFuture<Void> initializeFishRecord(String fishId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikariManager.getConnection()) {
                initializeFishRecordSync(conn, fishId);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize fish record", e);
            }
        });
    }

    private void initializeFishRecordSync(Connection conn, String fishId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO emf_fish_stats (fish_id, global_largest, global_smallest, global_most_caught) " +
                        "SELECT ?, 0, 0, 0 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM emf_fish_stats WHERE fish_id = ?)")) {

            stmt.setString(1, fishId);
            stmt.setString(2, fishId);
            stmt.executeUpdate();
        }
    }
}
