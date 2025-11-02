package GameProject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/game_hub";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connected successfully!");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed!");
            e.printStackTrace();
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // UPDATED: Save score only if user is logged in
    public boolean saveScore(String gameName, String playerName, int score,
                             String gameMode, String difficulty, int playTime, boolean won) {

        // Check if user is logged in
        UserAuthManager authManager = UserAuthManager.getInstance();
        if (!authManager.isLoggedIn()) {
            System.out.println("Guest mode - Score not saved");
            return false; // Guest mode, don't save
        }

        // Use the logged-in username
        String username = authManager.getCurrentUser().getUsername();

        String sql = "INSERT INTO game_scores (game_name, player_name, score, game_mode, difficulty, play_time, won) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, gameName);
            pstmt.setString(2, username); // Use logged-in username
            pstmt.setInt(3, score);
            pstmt.setString(4, gameMode);
            pstmt.setString(5, difficulty);
            pstmt.setInt(6, playTime);
            pstmt.setBoolean(7, won);

            int rowsAffected = pstmt.executeUpdate();
            System.out.println("Score saved successfully for user: " + username);
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Error saving score: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Get top scores for a specific game and mode
    public List<GameScore> getTopScores(String gameName, String gameMode, int limit) {
        List<GameScore> scores = new ArrayList<>();
        String sql = "SELECT * FROM game_scores WHERE game_name = ? AND game_mode = ? " +
                "ORDER BY score DESC, played_at DESC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, gameName);
            pstmt.setString(2, gameMode);
            pstmt.setInt(3, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                GameScore score = new GameScore(
                        rs.getInt("score_id"),
                        rs.getString("game_name"),
                        rs.getString("player_name"),
                        rs.getInt("score"),
                        rs.getString("game_mode"),
                        rs.getString("difficulty"),
                        rs.getInt("play_time"),
                        rs.getBoolean("won"),
                        rs.getTimestamp("played_at")
                );
                scores.add(score);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching scores: " + e.getMessage());
            e.printStackTrace();
        }

        return scores;
    }

    // Get player statistics
    public PlayerStats getPlayerStats(String playerName, String gameName) {
        String sql = "SELECT COUNT(*) as games_played, " +
                "MAX(score) as best_score, " +
                "AVG(score) as avg_score, " +
                "SUM(CASE WHEN won = TRUE THEN 1 ELSE 0 END) as wins " +
                "FROM game_scores WHERE player_name = ? AND game_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, gameName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new PlayerStats(
                        playerName,
                        gameName,
                        rs.getInt("games_played"),
                        rs.getInt("best_score"),
                        rs.getDouble("avg_score"),
                        rs.getInt("wins")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error fetching player stats: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Get all scores for a player
    public List<GameScore> getPlayerScores(String playerName) {
        List<GameScore> scores = new ArrayList<>();
        String sql = "SELECT * FROM game_scores WHERE player_name = ? ORDER BY played_at DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                GameScore score = new GameScore(
                        rs.getInt("score_id"),
                        rs.getString("game_name"),
                        rs.getString("player_name"),
                        rs.getInt("score"),
                        rs.getString("game_mode"),
                        rs.getString("difficulty"),
                        rs.getInt("play_time"),
                        rs.getBoolean("won"),
                        rs.getTimestamp("played_at")
                );
                scores.add(score);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching player scores: " + e.getMessage());
            e.printStackTrace();
        }

        return scores;
    }

    // Close connection
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// GameScore data class
class GameScore {
    private int scoreId;
    private String gameName;
    private String playerName;
    private int score;
    private String gameMode;
    private String difficulty;
    private int playTime;
    private boolean won;
    private Timestamp playedAt;

    public GameScore(int scoreId, String gameName, String playerName, int score,
                     String gameMode, String difficulty, int playTime, boolean won, Timestamp playedAt) {
        this.scoreId = scoreId;
        this.gameName = gameName;
        this.playerName = playerName;
        this.score = score;
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.playTime = playTime;
        this.won = won;
        this.playedAt = playedAt;
    }

    public int getScoreId() { return scoreId; }
    public String getGameName() { return gameName; }
    public String getPlayerName() { return playerName; }
    public int getScore() { return score; }
    public String getGameMode() { return gameMode; }
    public String getDifficulty() { return difficulty; }
    public int getPlayTime() { return playTime; }
    public boolean isWon() { return won; }
    public Timestamp getPlayedAt() { return playedAt; }

    @Override
    public String toString() {
        return String.format("%s - %s: %d points (%s)",
                playerName, gameName, score, gameMode);
    }
}

// PlayerStats data class
class PlayerStats {
    private String playerName;
    private String gameName;
    private int gamesPlayed;
    private int bestScore;
    private double avgScore;
    private int wins;

    public PlayerStats(String playerName, String gameName, int gamesPlayed,
                       int bestScore, double avgScore, int wins) {
        this.playerName = playerName;
        this.gameName = gameName;
        this.gamesPlayed = gamesPlayed;
        this.bestScore = bestScore;
        this.avgScore = avgScore;
        this.wins = wins;
    }

    public String getPlayerName() { return playerName; }
    public String getGameName() { return gameName; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getBestScore() { return bestScore; }
    public double getAvgScore() { return avgScore; }
    public int getWins() { return wins; }

    @Override
    public String toString() {
        return String.format("%s (%s): %d games, Best: %d, Avg: %.1f, Wins: %d",
                playerName, gameName, gamesPlayed, bestScore, avgScore, wins);
    }
}