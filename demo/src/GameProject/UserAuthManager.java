package GameProject;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class UserAuthManager {
    private static UserAuthManager instance;
    private Connection connection;
    private User currentUser = null; // null means guest mode

    private UserAuthManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/game_hub",
                    "root",
                    ""
            );
            createUsersTable();
            System.out.println("UserAuth connected successfully!");
        } catch (Exception e) {
            System.err.println("UserAuth connection failed!");
            e.printStackTrace();
        }
    }

    public static UserAuthManager getInstance() {
        if (instance == null) {
            instance = new UserAuthManager();
        }
        return instance;
    }

    // Create users table if it doesn't exist
    private void createUsersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id INT PRIMARY KEY AUTO_INCREMENT," +
                "username VARCHAR(50) UNIQUE NOT NULL," +
                "password_hash VARCHAR(64) NOT NULL," +
                "email VARCHAR(100)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_login TIMESTAMP," +
                "INDEX idx_username (username))";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Users table ready!");
        } catch (SQLException e) {
            System.err.println("Error creating users table: " + e.getMessage());
        }
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    // Register a new user
    public RegistrationResult register(String username, String password, String email) {
        // Validation
        if (username == null || username.trim().isEmpty()) {
            return new RegistrationResult(false, "Username cannot be empty");
        }
        if (username.length() < 3) {
            return new RegistrationResult(false, "Username must be at least 3 characters");
        }
        if (username.length() > 50) {
            return new RegistrationResult(false, "Username must be less than 50 characters");
        }
        if (password == null || password.length() < 6) {
            return new RegistrationResult(false, "Password must be at least 6 characters");
        }

        // Check if username already exists
        if (usernameExists(username)) {
            return new RegistrationResult(false, "Username already taken");
        }

        String sql = "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.setString(2, hashPassword(password));
            pstmt.setString(3, email != null ? email.trim() : null);

            pstmt.executeUpdate();
            System.out.println("User registered: " + username);
            return new RegistrationResult(true, "Registration successful!");

        } catch (SQLException e) {
            System.err.println("Registration error: " + e.getMessage());
            return new RegistrationResult(false, "Registration failed: " + e.getMessage());
        }
    }

    // Login user
    public LoginResult login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return new LoginResult(false, "Username cannot be empty", null);
        }
        if (password == null || password.isEmpty()) {
            return new LoginResult(false, "Password cannot be empty", null);
        }

        String sql = "SELECT user_id, username, email, created_at FROM users " +
                "WHERE username = ? AND password_hash = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.setString(2, hashPassword(password));

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at")
                );

                // Update last login
                updateLastLogin(user.getUserId());

                // Set current user
                this.currentUser = user;

                System.out.println("User logged in: " + username);
                return new LoginResult(true, "Login successful!", user);
            } else {
                return new LoginResult(false, "Invalid username or password", null);
            }

        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
            return new LoginResult(false, "Login failed: " + e.getMessage(), null);
        }
    }

    // Check if username exists
    private boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking username: " + e.getMessage());
        }

        return false;
    }

    // Update last login timestamp
    private void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating last login: " + e.getMessage());
        }
    }

    // Logout current user
    public void logout() {
        this.currentUser = null;
        System.out.println("User logged out");
    }

    // Get current logged-in user
    public User getCurrentUser() {
        return currentUser;
    }

    // Check if user is logged in
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    // Check if playing as guest
    public boolean isGuest() {
        return currentUser == null;
    }

    // Get username for display (or "Guest")
    public String getDisplayName() {
        return isLoggedIn() ? currentUser.getUsername() : "Guest";
    }

    // Get user statistics
    public UserStats getUserStats(int userId) {
        String sql = "SELECT " +
                "COUNT(*) as total_games, " +
                "SUM(score) as total_score, " +
                "SUM(CASE WHEN won = TRUE THEN 1 ELSE 0 END) as total_wins, " +
                "COUNT(DISTINCT game_name) as games_played " +
                "FROM game_scores WHERE player_name = " +
                "(SELECT username FROM users WHERE user_id = ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new UserStats(
                        rs.getInt("total_games"),
                        rs.getInt("total_score"),
                        rs.getInt("total_wins"),
                        rs.getInt("games_played")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting user stats: " + e.getMessage());
        }

        return null;
    }

    // Close connection
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("UserAuth connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}

// User class
class User {
    private int userId;
    private String username;
    private String email;
    private Timestamp createdAt;

    public User(int userId, String username, String email, Timestamp createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Timestamp getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "User: " + username + " (ID: " + userId + ")";
    }
}

// Login result class
class LoginResult {
    private boolean success;
    private String message;
    private User user;

    public LoginResult(boolean success, String message, User user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public User getUser() { return user; }
}

// Registration result class
class RegistrationResult {
    private boolean success;
    private String message;

    public RegistrationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}

// User statistics class
class UserStats {
    private int totalGames;
    private int totalScore;
    private int totalWins;
    private int gamesPlayed;

    public UserStats(int totalGames, int totalScore, int totalWins, int gamesPlayed) {
        this.totalGames = totalGames;
        this.totalScore = totalScore;
        this.totalWins = totalWins;
        this.gamesPlayed = gamesPlayed;
    }

    public int getTotalGames() { return totalGames; }
    public int getTotalScore() { return totalScore; }
    public int getTotalWins() { return totalWins; }
    public int getGamesPlayed() { return gamesPlayed; }
    public double getWinRate() {
        return totalGames > 0 ? (totalWins * 100.0 / totalGames) : 0;
    }
}