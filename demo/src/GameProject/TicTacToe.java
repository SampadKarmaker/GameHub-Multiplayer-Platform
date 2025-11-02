package GameProject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class TicTacToe extends Application {

    private enum GameMode { SOLO, PASS_AND_PLAY, ONLINE }
    private enum Difficulty { EASY, MEDIUM, HARD }

    private GameMode currentMode;
    private Difficulty difficulty;
    private Stage primaryStage;
    private String currentPlayer = "X";
    private Button[][] buttons = new Button[3][3];
    private boolean isMyTurn = true;
    private Label statusLabel;
    private Label scoreLabel;

    // Auth and Database
    private DatabaseManager dbManager;
    private UserAuthManager authManager;
    private long gameStartTime;

    // Scores
    private int xScore = 0;
    private int oScore = 0;
    private int draws = 0;

    // Online multiplayer
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String mySymbol;
    private String opponentName;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        dbManager = DatabaseManager.getInstance();
        authManager = UserAuthManager.getInstance();

        // Go directly to mode selection - no name dialog
        showModeSelection();
    }

    private void showModeSelection() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #FF6B6B 0%, #4ECDC4 100%);");

        Label title = new Label("❌ Tic-Tac-Toe ⭕");
        title.setFont(Font.font("System", FontWeight.BOLD, 48));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Select Game Mode");
        subtitle.setFont(Font.font("System", 18));
        subtitle.setTextFill(Color.web("#FFFFFF"));

        // Show logged-in user or Guest
        Label playerLabel = new Label("Player: " + authManager.getDisplayName());
        playerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        playerLabel.setTextFill(Color.web("#FFEB3B"));

        VBox buttonBox = new VBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(350);

        Button soloBtn = createMenuButton("🤖 VS Computer", "Challenge the AI");
        Button passPlayBtn = createMenuButton("🎯 Pass & Play", "Local 2-player");
        Button onlineBtn = createMenuButton("🌐 Online Multiplayer", "Play online");
        Button leaderboardBtn = createMenuButton("🏆 Leaderboard", "View top scores");
        Button backBtn = createMenuButton("← Back to Menu", "Return to main menu");

        soloBtn.setOnAction(e -> showDifficultySelection());
        passPlayBtn.setOnAction(e -> startGame(GameMode.PASS_AND_PLAY));
        onlineBtn.setOnAction(e -> showOnlineOptions());
        leaderboardBtn.setOnAction(e -> showLeaderboard());
        backBtn.setOnAction(e -> primaryStage.close());

        buttonBox.getChildren().addAll(soloBtn, passPlayBtn, onlineBtn, leaderboardBtn, backBtn);
        root.getChildren().addAll(title, subtitle, playerLabel, buttonBox);

        Scene scene = new Scene(root, 500, 750);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Tic-Tac-Toe");
        primaryStage.show();
    }

    private void showLeaderboard() {
        Stage leaderboardStage = new Stage();
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background: linear-gradient(to bottom, #FF6B6B 0%, #4ECDC4 100%);");

        Label title = new Label("🏆 Leaderboard");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");

        Tab soloTab = new Tab("VS Computer");
        soloTab.setClosable(false);
        ListView<String> soloList = new ListView<>();
        List<GameScore> soloScores = dbManager.getTopScores("TicTacToe", "vs_computer", 10);
        populateLeaderboard(soloList, soloScores);
        soloTab.setContent(soloList);

        Tab multiTab = new Tab("Multiplayer");
        multiTab.setClosable(false);
        ListView<String> multiList = new ListView<>();
        List<GameScore> multiScores = dbManager.getTopScores("TicTacToe", "multiplayer", 10);
        populateLeaderboard(multiList, multiScores);
        multiTab.setContent(multiList);

        tabPane.getTabs().addAll(soloTab, multiTab);

        Button closeBtn = new Button("Close");
        styleButton(closeBtn);
        closeBtn.setOnAction(e -> leaderboardStage.close());

        vbox.getChildren().addAll(title, tabPane, closeBtn);

        Scene scene = new Scene(vbox, 500, 600);
        leaderboardStage.setScene(scene);
        leaderboardStage.setTitle("Leaderboard");
        leaderboardStage.show();
    }

    private void populateLeaderboard(ListView<String> listView, List<GameScore> scores) {
        int rank = 1;
        for (GameScore score : scores) {
            String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "  ";
            String entry = String.format("%s #%d - %s: %d pts (%s) - %s",
                    medal, rank++, score.getPlayerName(), score.getScore(),
                    score.getDifficulty(), score.isWon() ? "✓ Won" : "✗ Lost");
            listView.getItems().add(entry);
        }
        if (scores.isEmpty()) {
            listView.getItems().add("No scores yet. Be the first to play!");
        }
    }

    private void showDifficultySelection() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #FF6B6B 0%, #4ECDC4 100%);");

        Label title = new Label("Select Difficulty");
        title.setFont(Font.font("System", FontWeight.BOLD, 42));
        title.setTextFill(Color.WHITE);

        VBox buttonBox = new VBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(350);

        Button easyBtn = createMenuButton("😊 Easy", "Random moves");
        Button mediumBtn = createMenuButton("🤔 Medium", "Smart moves");
        Button hardBtn = createMenuButton("😈 Hard", "Unbeatable AI");
        Button backBtn = createMenuButton("← Back", "Return");

        easyBtn.setOnAction(e -> { difficulty = Difficulty.EASY; startGame(GameMode.SOLO); });
        mediumBtn.setOnAction(e -> { difficulty = Difficulty.MEDIUM; startGame(GameMode.SOLO); });
        hardBtn.setOnAction(e -> { difficulty = Difficulty.HARD; startGame(GameMode.SOLO); });
        backBtn.setOnAction(e -> showModeSelection());

        buttonBox.getChildren().addAll(easyBtn, mediumBtn, hardBtn, backBtn);
        root.getChildren().addAll(title, buttonBox);

        Scene scene = new Scene(root, 500, 650);
        primaryStage.setScene(scene);
    }

    private void showOnlineOptions() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Connect to Server");
        dialog.setHeaderText("Enter Server Details");

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("5557");

        gridPane.add(new Label("Host:"), 0, 0);
        gridPane.add(hostField, 1, 0);
        gridPane.add(new Label("Port:"), 0, 1);
        gridPane.add(portField, 1, 1);

        dialog.getDialogPane().setContent(gridPane);
        Platform.runLater(hostField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == connectBtn) {
                return new String[]{hostField.getText(), portField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> connectToServer(authManager.getDisplayName(), data[0], Integer.parseInt(data[1])));
    }

    private void connectToServer(String playerName, String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(playerName);
            new Thread(this::listenToServer).start();
            showWaitingScreen();
        } catch (IOException e) {
            showAlert("Connection Failed", "Could not connect to server: " + e.getMessage());
        }
    }

    private void showWaitingScreen() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #FF6B6B 0%, #4ECDC4 100%);");

        Label title = new Label("Waiting for Opponent...");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));
        title.setTextFill(Color.WHITE);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setStyle("-fx-progress-color: white;");

        root.getChildren().addAll(title, progress);

        Scene scene = new Scene(root, 500, 400);
        Platform.runLater(() -> {
            primaryStage.setScene(scene);
            primaryStage.show();
        });
    }

    private void listenToServer() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
                Platform.runLater(() -> handleServerMessage(finalMessage));
            }
        } catch (IOException e) {
            Platform.runLater(() -> showAlert("Connection Lost", "Lost connection to server"));
        }
    }

    private void handleServerMessage(String message) {
        String[] parts = message.split(":");
        String command = parts[0];

        switch (command) {
            case "SYMBOL":
                mySymbol = parts[1];
                opponentName = parts[2];
                isMyTurn = mySymbol.equals("X");
                Platform.runLater(() -> startGame(GameMode.ONLINE));
                break;
            case "MOVE":
                int row = Integer.parseInt(parts[1]);
                int col = Integer.parseInt(parts[2]);
                String opponentSymbol = parts[3];
                Platform.runLater(() -> {
                    buttons[row][col].setText(opponentSymbol);
                    buttons[row][col].setStyle(buttons[row][col].getStyle() + "-fx-text-fill: " +
                            (opponentSymbol.equals("X") ? "#FF6B6B" : "#4ECDC4") + ";");
                    buttons[row][col].setDisable(true);
                    if (!checkWinner() && !isBoardFull()) {
                        isMyTurn = true;
                        updateStatus();
                    }
                });
                break;
            case "WIN":
                Platform.runLater(() -> showAlert("Game Over", parts[1] + " wins!"));
                break;
            case "DRAW":
                Platform.runLater(() -> {
                    draws++;
                    scoreLabel.setText(getScoreText());
                    showAlert("Game Over", "It's a Draw! 🤝");
                });
                break;
            case "OPPONENT_DISCONNECTED":
                Platform.runLater(() -> showAlert("Opponent Left", "Your opponent disconnected"));
                break;
        }
    }

    private Button createMenuButton(String text, String subtitle) {
        VBox btnContent = new VBox(5);
        btnContent.setAlignment(Pos.CENTER);

        Label mainText = new Label(text);
        mainText.setFont(Font.font("System", FontWeight.BOLD, 18));
        mainText.setTextFill(Color.web("#FF6B6B"));

        Label subText = new Label(subtitle);
        subText.setFont(Font.font("System", 12));
        subText.setTextFill(Color.web("#999999"));

        btnContent.getChildren().addAll(mainText, subText);

        Button btn = new Button();
        btn.setGraphic(btnContent);
        btn.setPrefWidth(350);
        btn.setPrefHeight(80);
        btn.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 5);");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #fff5e6; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 8);"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 5);"));

        return btn;
    }

    private void startGame(GameMode mode) {
        this.currentMode = mode;
        currentPlayer = "X";
        gameStartTime = System.currentTimeMillis();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        VBox topPanel = new VBox(10);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new Insets(20));
        topPanel.setStyle("-fx-background: linear-gradient(to right, #FF6B6B, #4ECDC4);");

        Label modeLabel = new Label(getModeTitle());
        modeLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        modeLabel.setTextFill(Color.WHITE);

        scoreLabel = new Label(getScoreText());
        scoreLabel.setFont(Font.font("System", 16));
        scoreLabel.setTextFill(Color.WHITE);

        topPanel.getChildren().addAll(modeLabel, scoreLabel);

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: #2c3e50;");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                final int r = row;
                final int c = col;

                Button btn = new Button();
                btn.setMinSize(120, 120);
                btn.setMaxSize(120, 120);
                btn.setStyle("-fx-font-size: 48; -fx-font-weight: bold; -fx-background-color: white; " +
                        "-fx-border-radius: 10; -fx-background-radius: 10;");
                btn.setOnAction(e -> handleButtonClick(r, c, btn));
                buttons[r][c] = btn;
                grid.add(btn, c, r);
            }
        }

        root.setCenter(grid);

        VBox bottomPanel = new VBox(10);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(20));

        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        updateStatus();

        HBox buttonPanel = new HBox(15);
        buttonPanel.setAlignment(Pos.CENTER);

        Button resetBtn = new Button("New Game");
        Button menuBtn = new Button("Main Menu");

        styleButton(resetBtn);
        styleButton(menuBtn);

        resetBtn.setOnAction(e -> resetGame());
        menuBtn.setOnAction(e -> {
            if (socket != null) {
                try { socket.close(); } catch (IOException ex) {}
            }
            showModeSelection();
        });

        buttonPanel.getChildren().addAll(resetBtn, menuBtn);
        bottomPanel.getChildren().addAll(statusLabel, buttonPanel);

        root.setTop(topPanel);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 500, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Tic-Tac-Toe - " + getModeTitle());
    }

    private void handleButtonClick(int row, int col, Button btn) {
        if (btn.getText().isEmpty()) {
            if (currentMode == GameMode.ONLINE && !isMyTurn) return;

            String symbolToUse = (currentMode == GameMode.ONLINE) ? mySymbol : currentPlayer;

            btn.setText(symbolToUse);
            btn.setStyle(btn.getStyle() + "-fx-text-fill: " +
                    (symbolToUse.equals("X") ? "#FF6B6B" : "#4ECDC4") + ";");
            btn.setDisable(true);

            if (currentMode == GameMode.ONLINE) {
                out.println("MOVE:" + row + ":" + col + ":" + mySymbol);
                isMyTurn = false;
            }

            if (checkWinner()) {
                handleWin();
            } else if (isBoardFull()) {
                handleDraw();
            } else {
                currentPlayer = currentPlayer.equals("X") ? "O" : "X";
                updateStatus();

                if (currentMode == GameMode.SOLO && currentPlayer.equals("O")) {
                    PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
                    pause.setOnFinished(e -> makeComputerMove());
                    pause.play();
                }
            }
        }
    }

    private void makeComputerMove() {
        int[] move = null;
        switch (difficulty) {
            case HARD: move = getBestMove(); break;
            case MEDIUM: move = Math.random() < 0.7 ? getBestMove() : getRandomMove(); break;
            case EASY: move = getRandomMove(); break;
        }
        if (move != null) handleButtonClick(move[0], move[1], buttons[move[0]][move[1]]);
    }

    private int[] getRandomMove() {
        List<int[]> availableMoves = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (buttons[r][c].getText().isEmpty()) availableMoves.add(new int[]{r, c});
            }
        }
        return availableMoves.isEmpty() ? null : availableMoves.get(new Random().nextInt(availableMoves.size()));
    }

    private int[] getBestMove() {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (buttons[r][c].getText().isEmpty()) {
                    buttons[r][c].setText("O");
                    int score = minimax(false);
                    buttons[r][c].setText("");
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{r, c};
                    }
                }
            }
        }
        return bestMove;
    }

    private int minimax(boolean isMaximizing) {
        String winner = getWinner();
        if (winner != null) return winner.equals("O") ? 10 : -10;
        if (isBoardFull()) return 0;

        if (isMaximizing) {
            int bestScore = Integer.MIN_VALUE;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (buttons[r][c].getText().isEmpty()) {
                        buttons[r][c].setText("O");
                        int score = minimax(false);
                        buttons[r][c].setText("");
                        bestScore = Math.max(score, bestScore);
                    }
                }
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (buttons[r][c].getText().isEmpty()) {
                        buttons[r][c].setText("X");
                        int score = minimax(true);
                        buttons[r][c].setText("");
                        bestScore = Math.min(score, bestScore);
                    }
                }
            }
            return bestScore;
        }
    }

    private boolean checkWinner() { return getWinner() != null; }

    private String getWinner() {
        for (int i = 0; i < 3; i++) {
            if (!buttons[i][0].getText().isEmpty() &&
                    buttons[i][0].getText().equals(buttons[i][1].getText()) &&
                    buttons[i][1].getText().equals(buttons[i][2].getText())) {
                return buttons[i][0].getText();
            }
        }
        for (int i = 0; i < 3; i++) {
            if (!buttons[0][i].getText().isEmpty() &&
                    buttons[0][i].getText().equals(buttons[1][i].getText()) &&
                    buttons[1][i].getText().equals(buttons[2][i].getText())) {
                return buttons[0][i].getText();
            }
        }
        if (!buttons[0][0].getText().isEmpty() &&
                buttons[0][0].getText().equals(buttons[1][1].getText()) &&
                buttons[1][1].getText().equals(buttons[2][2].getText())) {
            return buttons[0][0].getText();
        }
        if (!buttons[0][2].getText().isEmpty() &&
                buttons[0][2].getText().equals(buttons[1][1].getText()) &&
                buttons[1][1].getText().equals(buttons[2][0].getText())) {
            return buttons[0][2].getText();
        }
        return null;
    }

    private boolean isBoardFull() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (buttons[r][c].getText().isEmpty()) return false;
            }
        }
        return true;
    }

    private void handleWin() {
        String winner = currentPlayer;
        if (winner.equals("X")) xScore++; else oScore++;
        scoreLabel.setText(getScoreText());
        saveGameToDatabase(winner);

        String message = currentMode == GameMode.SOLO ?
                (winner.equals("X") ? "You Win! 🎉" : "Computer Wins! 🤖") :
                "Player " + winner + " Wins! 🏆";
        showAlert("Game Over", message);
        if (authManager.isLoggedIn()) showPlayerStats();
        resetGame();
    }

    private void handleDraw() {
        draws++;
        scoreLabel.setText(getScoreText());
        saveGameToDatabase(null);
        showAlert("Game Over", "It's a Draw! 🤝");
        resetGame();
    }

    private void saveGameToDatabase(String winner) {
        int playTime = (int)((System.currentTimeMillis() - gameStartTime) / 1000);
        String gameMode, difficultyStr = "medium";
        boolean playerWon = false;
        int score = 0;

        if (currentMode == GameMode.SOLO) {
            gameMode = "vs_computer";
            difficultyStr = difficulty.toString().toLowerCase();
            playerWon = "X".equals(winner);
            score = playerWon ? 100 : 0;
        } else if (currentMode == GameMode.PASS_AND_PLAY) {
            gameMode = "multiplayer";
            playerWon = winner != null;
            score = playerWon ? 100 : 50;
        } else {
            gameMode = "online";
            playerWon = mySymbol.equals(winner);
            score = playerWon ? 100 : 0;
        }
        if (winner == null) score = 50;

        dbManager.saveScore("TicTacToe", authManager.getDisplayName(), score, gameMode, difficultyStr, playTime, playerWon);
    }

    private void showPlayerStats() {
        PlayerStats stats = dbManager.getPlayerStats(authManager.getDisplayName(), "TicTacToe");
        if (stats != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Your Statistics");
            alert.setHeaderText("📊 " + authManager.getDisplayName() + "'s Stats");
            alert.setContentText(String.format(
                    "Games Played: %d\nBest Score: %d\nAverage Score: %.1f\nWins: %d\nWin Rate: %.1f%%",
                    stats.getGamesPlayed(), stats.getBestScore(), stats.getAvgScore(), stats.getWins(),
                    (stats.getGamesPlayed() > 0 ? (stats.getWins() * 100.0 / stats.getGamesPlayed()) : 0)));
            alert.showAndWait();
        }
    }

    private void resetGame() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                buttons[r][c].setText("");
                buttons[r][c].setDisable(false);
                buttons[r][c].setStyle("-fx-font-size: 48; -fx-font-weight: bold; -fx-background-color: white; " +
                        "-fx-border-radius: 10; -fx-background-radius: 10;");
            }
        }
        currentPlayer = "X";
        isMyTurn = (currentMode != GameMode.ONLINE) || mySymbol.equals("X");
        gameStartTime = System.currentTimeMillis();
        updateStatus();
    }

    private void updateStatus() {
        if (currentMode == GameMode.ONLINE) {
            statusLabel.setText(isMyTurn ? "Your Turn (" + mySymbol + ")" : opponentName + "'s Turn");
        } else if (currentMode == GameMode.SOLO) {
            statusLabel.setText(currentPlayer.equals("X") ? "Your Turn (X)" : "Computer's Turn (O)");
        } else {
            statusLabel.setText("Player " + currentPlayer + "'s Turn");
        }
    }

    private String getModeTitle() {
        switch (currentMode) {
            case SOLO: return "VS Computer (" + difficulty + ")";
            case PASS_AND_PLAY: return "Pass & Play";
            case ONLINE: return "Online: " + mySymbol + " vs " + opponentName;
            default: return "Tic-Tac-Toe";
        }
    }

    private String getScoreText() {
        return "X: " + xScore + "  |  O: " + oScore + "  |  Draws: " + draws;
    }

    private void styleButton(Button btn) {
        btn.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;"));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}