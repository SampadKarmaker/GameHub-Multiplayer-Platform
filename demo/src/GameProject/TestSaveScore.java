package GameProject;

public class TestSaveScore {
    public static void main(String[] args) {
        // Get database instance
        DatabaseManager db = DatabaseManager.getInstance();

        // Save a test score
        boolean success = db.saveScore(
                "TicTacToe",        // Game name
                "TestPlayer",       // Player name
                100,                // Score
                "solo",             // Game mode
                "easy",             // Difficulty
                60,                 // Play time (seconds)
                true                // Won the game
        );

        if (success) {
            System.out.println("✅ Score saved successfully!");

            // Retrieve and display the score
            System.out.println("\n📊 Top 5 Scores:");
            var topScores = db.getTopScores("TicTacToe", "solo", 5);
            for (GameScore score : topScores) {
                System.out.println(score);
            }

            // Get player stats
            System.out.println("\n📈 Player Statistics:");
            PlayerStats stats = db.getPlayerStats("TestPlayer", "TicTacToe");
            if (stats != null) {
                System.out.println(stats);
            }
        } else {
            System.out.println("❌ Failed to save score!");
        }

        // Close connection
        db.closeConnection();
    }
}


