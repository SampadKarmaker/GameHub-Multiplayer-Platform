package GameProject;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

public class WordGame extends Application {

    private Button[][] gridButtons = new Button[5][5];

    @Override
    public void start(Stage primaryStage) {
        GridPane grid = new GridPane();

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                final int r = row;  // Final copy of row
                final int c = col;  // Final copy of col

                Button btn = new Button();
                btn.setMinSize(50, 50);
                btn.setStyle("-fx-font-size: 16;");
                btn.setOnAction((ActionEvent e) -> handleGridButtonClick(r, c));
                gridButtons[r][c] = btn;
                grid.add(btn, c, r);  // Using final variables r and c
            }
        }

        Button showLeaderboardButton = new Button("Show Leaderboard");
        showLeaderboardButton.setOnAction(e -> showLeaderboard());

        grid.add(showLeaderboardButton, 2, 5);

        Scene scene = new Scene(grid, 300, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Word Game");
        primaryStage.show();
    }

    private void handleGridButtonClick(int row, int col) {
        // Logic to handle button clicks and form words
    }

    private void showLeaderboard() {
        // Show the leaderboard for Word Game
    }

    public static void main(String[] args) {
        launch(args);
    }
}
