package GameProject;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.*;

public class PacmanGame extends Application {

    private enum GameMode { SOLO, VS_COMPUTER, MULTIPLAYER, ONLINE }

    private static final int TILE_SIZE = 25;
    private static final int GRID_WIDTH = 24;
    private static final int GRID_HEIGHT = 20;

    private Canvas canvas;
    private GraphicsContext gc;
    private AnimationTimer gameLoop;
    private Stage primaryStage;
    private GameMode currentMode;
    private DatabaseManager dbManager;
    private UserAuthManager authManager;
    private long gameStartTime;

    // Player 1 (Yellow Pacman)
    private int pacman1X = 1;
    private int pacman1Y = 1;
    private int direction1 = 0;
    private int score1 = 0;
    private int lives1 = 3;

    // Player 2 (Red Pacman - for multiplayer)
    private int pacman2X = GRID_WIDTH - 2;
    private int pacman2Y = GRID_HEIGHT - 2;
    private int direction2 = 2;
    private int score2 = 0;
    private int lives2 = 3;

    private Label scoreLabel;
    private Label livesLabel;
    private Label statusLabel;

    private boolean[][] walls;
    private boolean[][] dots;
    private List<Ghost> ghosts;
    private Random random = new Random();

    private long lastMoveTime = 0;
    private long lastGhostMoveTime = 0;
    private long moveDelay = 150_000_000;
    private long ghostMoveDelay = 200_000_000;

    private boolean gameRunning = false;
    private boolean gameOver = false;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.dbManager = DatabaseManager.getInstance();
        this.authManager = UserAuthManager.getInstance();

        // Go directly to mode selection - no name dialog
        showModeSelection();
    }

    private void showModeSelection() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #1a1a2e 0%, #16213e 100%);");

        Label title = new Label("👻 PACMAN");
        title.setFont(Font.font("System", FontWeight.BOLD, 48));
        title.setTextFill(Color.YELLOW);

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

        Button soloBtn = createMenuButton("🎯 Solo Mode", "Play alone vs ghosts");
        Button vsComputerBtn = createMenuButton("🤖 VS Computer", "Race against AI Pacman");
        Button multiplayerBtn = createMenuButton("👥 Multiplayer", "2 Players on same keyboard");
        Button leaderboardBtn = createMenuButton("🏆 Leaderboard", "View top scores");
        Button exitBtn = createMenuButton("← Exit", "Close game");

        soloBtn.setOnAction(e -> startGame(GameMode.SOLO));
        vsComputerBtn.setOnAction(e -> startGame(GameMode.VS_COMPUTER));
        multiplayerBtn.setOnAction(e -> startGame(GameMode.MULTIPLAYER));
        leaderboardBtn.setOnAction(e -> showLeaderboard());
        exitBtn.setOnAction(e -> primaryStage.close());

        buttonBox.getChildren().addAll(soloBtn, vsComputerBtn, multiplayerBtn, leaderboardBtn, exitBtn);
        root.getChildren().addAll(title, subtitle, playerLabel, buttonBox);

        Scene scene = new Scene(root, 700, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Pacman - Mode Selection");
        primaryStage.show();
    }

    private Button createMenuButton(String text, String subtitle) {
        VBox btnContent = new VBox(5);
        btnContent.setAlignment(Pos.CENTER);

        Label mainText = new Label(text);
        mainText.setFont(Font.font("System", FontWeight.BOLD, 18));
        mainText.setTextFill(Color.YELLOW);

        Label subText = new Label(subtitle);
        subText.setFont(Font.font("System", 12));
        subText.setTextFill(Color.web("#999999"));

        btnContent.getChildren().addAll(mainText, subText);

        Button btn = new Button();
        btn.setGraphic(btnContent);
        btn.setPrefWidth(350);
        btn.setPrefHeight(80);
        btn.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(255,215,0,0.3), 10, 0, 0, 5);");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #16213e; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(255,215,0,0.5), 15, 0, 0, 8);"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(255,215,0,0.3), 10, 0, 0, 5);"));

        return btn;
    }

    private void showLeaderboard() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background: linear-gradient(to bottom, #1a1a2e 0%, #16213e 100%);");

        Label title = new Label("🏆 LEADERBOARD");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));
        title.setTextFill(Color.YELLOW);

        HBox modeSelector = new HBox(15);
        modeSelector.setAlignment(Pos.CENTER);

        Label modeLabel = new Label("Select Game Mode:");
        modeLabel.setFont(Font.font("System", 14));
        modeLabel.setTextFill(Color.WHITE);

        ComboBox<String> modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("SOLO", "VS_COMPUTER", "MULTIPLAYER");
        modeCombo.setValue("SOLO");

        modeSelector.getChildren().addAll(modeLabel, modeCombo);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-control-inner-background: #0f3460;");
        scrollPane.setFitToWidth(true);

        VBox scoreList = new VBox(10);
        scoreList.setStyle("-fx-padding: 10;");

        Runnable updateLeaderboard = () -> {
            String selectedMode = modeCombo.getValue();
            List<GameScore> topScores = dbManager.getTopScores("Pacman", selectedMode, 10);
            scoreList.getChildren().clear();

            if (topScores.isEmpty()) {
                Label noScores = new Label("No scores yet!");
                noScores.setFont(Font.font("System", 16));
                noScores.setTextFill(Color.web("#999999"));
                scoreList.getChildren().add(noScores);
            } else {
                int rank = 1;
                for (GameScore score : topScores) {
                    HBox scoreRow = createScoreRow(rank, score);
                    scoreList.getChildren().add(scoreRow);
                    rank++;
                }
            }
        };

        modeCombo.setOnAction(e -> updateLeaderboard.run());
        updateLeaderboard.run();

        scrollPane.setContent(scoreList);

        Button backBtn = new Button("Back to Menu");
        styleButton(backBtn);
        backBtn.setFocusTraversable(false);
        backBtn.setOnAction(e -> showModeSelection());

        root.getChildren().addAll(title, modeSelector, scrollPane, backBtn);

        Scene scene = new Scene(root, 700, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Pacman - Leaderboard");
    }

    private HBox createScoreRow(int rank, GameScore score) {
        HBox row = new HBox(20);
        row.setPadding(new Insets(12));
        row.setStyle("-fx-background-color: #1a2a4e; -fx-border-color: #0066FF; " +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label rankLabel = new Label(rank + ".");
        rankLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        rankLabel.setTextFill(getRankColor(rank));
        rankLabel.setMinWidth(40);

        Label nameLabel = new Label(score.getPlayerName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        nameLabel.setTextFill(Color.YELLOW);
        nameLabel.setMinWidth(150);

        Label scoreLabel = new Label("Score: " + score.getScore());
        scoreLabel.setFont(Font.font("System", 13));
        scoreLabel.setTextFill(Color.WHITE);
        scoreLabel.setMinWidth(120);

        Label statusLabel = new Label(score.isWon() ? "Won" : "Lost");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(score.isWon() ? Color.LIGHTGREEN : Color.LIGHTCORAL);

        row.getChildren().addAll(rankLabel, nameLabel, scoreLabel, statusLabel);
        return row;
    }

    private Color getRankColor(int rank) {
        if (rank == 1) return Color.web("#FFD700");
        if (rank == 2) return Color.web("#C0C0C0");
        if (rank == 3) return Color.web("#CD7F32");
        return Color.YELLOW;
    }

    private void startGame(GameMode mode) {
        this.currentMode = mode;
        this.gameStartTime = System.currentTimeMillis();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #000000;");

        VBox topPanel = new VBox(10);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new Insets(15));
        topPanel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("👻 PACMAN - " + getModeTitle());
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.YELLOW);

        HBox statsBox = new HBox(40);
        statsBox.setAlignment(Pos.CENTER);

        scoreLabel = new Label(getScoreText());
        scoreLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        scoreLabel.setTextFill(Color.WHITE);

        livesLabel = new Label(getLivesText());
        livesLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        livesLabel.setTextFill(Color.WHITE);

        statsBox.getChildren().addAll(scoreLabel, livesLabel);
        topPanel.getChildren().addAll(title, statsBox);

        canvas = new Canvas(GRID_WIDTH * TILE_SIZE, GRID_HEIGHT * TILE_SIZE);
        gc = canvas.getGraphicsContext2D();

        VBox bottomPanel = new VBox(10);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(15));
        bottomPanel.setStyle("-fx-background-color: #1a1a2e;");

        statusLabel = new Label(getControlsText());
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.web("#aaaaaa"));

        HBox buttonPanel = new HBox(15);
        buttonPanel.setAlignment(Pos.CENTER);

        Button restartBtn = new Button("Restart");
        Button menuBtn = new Button("Main Menu");

        styleButton(restartBtn);
        styleButton(menuBtn);

        restartBtn.setFocusTraversable(false);
        menuBtn.setFocusTraversable(false);

        restartBtn.setOnAction(e -> {
            resetGame();
            canvas.requestFocus();
        });
        menuBtn.setOnAction(e -> {
            if (gameLoop != null) gameLoop.stop();
            showModeSelection();
        });

        buttonPanel.getChildren().addAll(restartBtn, menuBtn);
        bottomPanel.getChildren().addAll(statusLabel, buttonPanel);

        root.setTop(topPanel);
        root.setCenter(canvas);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root);
        setupKeyControls(scene);
        canvas.setFocusTraversable(true);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Pacman - " + getModeTitle());

        initializeGame();
        draw();
        startGameLoop();

        primaryStage.setOnShown(e -> canvas.requestFocus());
    }

    private void initializeGame() {
        walls = new boolean[GRID_HEIGHT][GRID_WIDTH];
        dots = new boolean[GRID_HEIGHT][GRID_WIDTH];
        ghosts = new ArrayList<>();

        createMaze();

        pacman1X = 1;
        pacman1Y = 1;
        pacman2X = GRID_WIDTH - 2;
        pacman2Y = GRID_HEIGHT - 2;
        direction1 = 0;
        direction2 = 2;

        score1 = 0;
        score2 = 0;
        lives1 = 3;
        lives2 = 3;

        dots[pacman1Y][pacman1X] = false;
        if (currentMode != GameMode.SOLO) {
            dots[pacman2Y][pacman2X] = false;
        }

        int ghostCount = currentMode == GameMode.SOLO ? 4 : 6;
        ghosts.add(new Ghost(GRID_WIDTH / 2, GRID_HEIGHT / 2, Color.RED, "Blinky"));
        ghosts.add(new Ghost(GRID_WIDTH / 2 - 1, GRID_HEIGHT / 2, Color.PINK, "Pinky"));
        ghosts.add(new Ghost(GRID_WIDTH / 2 + 1, GRID_HEIGHT / 2, Color.CYAN, "Inky"));
        ghosts.add(new Ghost(GRID_WIDTH / 2, GRID_HEIGHT / 2 + 1, Color.ORANGE, "Clyde"));

        if (ghostCount > 4) {
            ghosts.add(new Ghost(GRID_WIDTH / 2 - 2, GRID_HEIGHT / 2, Color.PURPLE, "Shadow"));
            ghosts.add(new Ghost(GRID_WIDTH / 2 + 2, GRID_HEIGHT / 2, Color.LIME, "Speedy"));
        }

        updateLabels();
    }

    private void createMaze() {
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                walls[y][x] = false;
                dots[y][x] = true;
            }
        }

        for (int x = 0; x < GRID_WIDTH; x++) {
            walls[0][x] = true;
            walls[GRID_HEIGHT - 1][x] = true;
            dots[0][x] = false;
            dots[GRID_HEIGHT - 1][x] = false;
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            walls[y][0] = true;
            walls[y][GRID_WIDTH - 1] = true;
            dots[y][0] = false;
            dots[y][GRID_WIDTH - 1] = false;
        }

        for (int y = 2; y < GRID_HEIGHT - 2; y += 4) {
            for (int x = 2; x < GRID_WIDTH - 2; x += 4) {
                createWallBlock(x, y, 2, 2);
            }
        }

        for (int y = 4; y < GRID_HEIGHT - 4; y += 6) {
            for (int x = 4; x < GRID_WIDTH - 4; x += 2) {
                if (random.nextDouble() < 0.3) {
                    walls[y][x] = true;
                    dots[y][x] = false;
                }
            }
        }

        int centerX = GRID_WIDTH / 2;
        int centerY = GRID_HEIGHT / 2;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                walls[centerY + dy][centerX + dx] = false;
                dots[centerY + dy][centerX + dx] = false;
            }
        }
    }

    private void createWallBlock(int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                if (y + dy < GRID_HEIGHT && x + dx < GRID_WIDTH) {
                    walls[y + dy][x + dx] = true;
                    dots[y + dy][x + dx] = false;
                }
            }
        }
    }

    private void setupKeyControls(Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            KeyCode code = e.getCode();

            if (!gameRunning || gameOver) {
                if (code == KeyCode.SPACE) {
                    resetGame();
                }
                return;
            }

            if (code == KeyCode.RIGHT) {
                direction1 = 0;
                e.consume();
            } else if (code == KeyCode.DOWN) {
                direction1 = 1;
                e.consume();
            } else if (code == KeyCode.LEFT) {
                direction1 = 2;
                e.consume();
            } else if (code == KeyCode.UP) {
                direction1 = 3;
                e.consume();
            }

            if (currentMode == GameMode.MULTIPLAYER) {
                if (code == KeyCode.D) {
                    direction2 = 0;
                    e.consume();
                } else if (code == KeyCode.S) {
                    direction2 = 1;
                    e.consume();
                } else if (code == KeyCode.A) {
                    direction2 = 2;
                    e.consume();
                } else if (code == KeyCode.W) {
                    direction2 = 3;
                    e.consume();
                }
            }
        });
    }

    private void startGameLoop() {
        gameRunning = true;
        gameOver = false;

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastMoveTime >= moveDelay) {
                    updatePacman();
                    lastMoveTime = now;
                }

                if (now - lastGhostMoveTime >= ghostMoveDelay) {
                    updateGhosts();
                    lastGhostMoveTime = now;
                }

                draw();
            }
        };
        gameLoop.start();
    }

    private void updatePacman() {
        if (!gameRunning || gameOver) return;

        movePacman(1);

        if (currentMode == GameMode.VS_COMPUTER) {
            computerMove();
        } else if (currentMode == GameMode.MULTIPLAYER) {
            movePacman(2);
        }

        if (checkWin()) {
            endGame();
        }
    }

    private void movePacman(int player) {
        int currentX = player == 1 ? pacman1X : pacman2X;
        int currentY = player == 1 ? pacman1Y : pacman2Y;
        int direction = player == 1 ? direction1 : direction2;

        int newX = currentX;
        int newY = currentY;

        switch (direction) {
            case 0: newX++; break;
            case 1: newY++; break;
            case 2: newX--; break;
            case 3: newY--; break;
        }

        if (newX >= 0 && newX < GRID_WIDTH && newY >= 0 && newY < GRID_HEIGHT && !walls[newY][newX]) {
            if (player == 1) {
                pacman1X = newX;
                pacman1Y = newY;
            } else {
                pacman2X = newX;
                pacman2Y = newY;
            }

            if (dots[newY][newX]) {
                dots[newY][newX] = false;
                if (player == 1) {
                    score1 += 10;
                } else {
                    score2 += 10;
                }
                updateLabels();
            }
        }
    }

    private void computerMove() {
        int bestDist = Integer.MAX_VALUE;
        int bestDir = direction2;

        for (int dir = 0; dir < 4; dir++) {
            int newX = pacman2X;
            int newY = pacman2Y;

            switch (dir) {
                case 0: newX++; break;
                case 1: newY++; break;
                case 2: newX--; break;
                case 3: newY--; break;
            }

            if (newX >= 0 && newX < GRID_WIDTH && newY >= 0 && newY < GRID_HEIGHT && !walls[newY][newX]) {
                for (int y = 0; y < GRID_HEIGHT; y++) {
                    for (int x = 0; x < GRID_WIDTH; x++) {
                        if (dots[y][x]) {
                            int dist = Math.abs(newX - x) + Math.abs(newY - y);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestDir = dir;
                            }
                        }
                    }
                }
            }
        }

        direction2 = bestDir;
        movePacman(2);
    }

    private void updateGhosts() {
        for (Ghost ghost : ghosts) {
            ghost.move(walls, pacman1X, pacman1Y, pacman2X, pacman2Y, currentMode != GameMode.SOLO);
        }

        for (Ghost ghost : ghosts) {
            if (ghost.x == pacman1X && ghost.y == pacman1Y) {
                loseLife(1);
            }
            if (currentMode != GameMode.SOLO && ghost.x == pacman2X && ghost.y == pacman2Y) {
                loseLife(2);
            }
        }
    }

    private void loseLife(int player) {
        if (player == 1) {
            lives1--;
            pacman1X = 1;
            pacman1Y = 1;
        } else {
            lives2--;
            pacman2X = GRID_WIDTH - 2;
            pacman2Y = GRID_HEIGHT - 2;
        }

        updateLabels();

        if (lives1 <= 0 || (currentMode != GameMode.SOLO && lives2 <= 0)) {
            endGame();
        }
    }

    private boolean checkWin() {
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (dots[y][x]) return false;
            }
        }
        return true;
    }

    private void endGame() {
        gameRunning = false;
        gameOver = true;
        if (gameLoop != null) {
            gameLoop.stop();
        }

        int playTime = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        String message;
        boolean won = false;

        if (currentMode == GameMode.SOLO) {
            won = lives1 > 0;
            message = won ? "🎉 YOU WIN! Score: " + score1 : "💀 GAME OVER! Score: " + score1;
            saveScore(score1, playTime, won);
        } else if (currentMode == GameMode.VS_COMPUTER) {
            if (score1 > score2) {
                message = "🎉 YOU WIN! " + score1 + " vs " + score2;
                won = true;
            } else if (score2 > score1) {
                message = "🤖 COMPUTER WINS! " + score2 + " vs " + score1;
            } else {
                message = "🤝 TIE! Both scored " + score1;
            }
            saveScore(score1, playTime, won);
        } else {
            if (score1 > score2) {
                message = "🎉 PLAYER 1 WINS! " + score1 + " vs " + score2;
                won = true;
            } else if (score2 > score1) {
                message = "🎉 PLAYER 2 WINS! " + score2 + " vs " + score1;
            } else {
                message = "🤝 TIE! Both scored " + score1;
            }
            saveScore(score1, playTime, won);
        }

        statusLabel.setText(message);
    }

    private void saveScore(int finalScore, int playTime, boolean won) {
        String gameMode = currentMode.toString();
        dbManager.saveScore("Pacman", authManager.getDisplayName(), finalScore, gameMode, "Normal", playTime, won);
    }

    private void resetGame() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        initializeGame();
        draw();
        startGameLoop();
    }

    private void draw() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.setFill(Color.web("#0066FF"));
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (walls[y][x]) {
                    gc.fillRoundRect(x * TILE_SIZE + 1, y * TILE_SIZE + 1,
                            TILE_SIZE - 2, TILE_SIZE - 2, 4, 4);
                }
            }
        }

        gc.setFill(Color.WHITE);
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (dots[y][x]) {
                    gc.fillOval(x * TILE_SIZE + 10, y * TILE_SIZE + 10, 5, 5);
                }
            }
        }

        for (Ghost ghost : ghosts) {
            gc.setFill(ghost.color);
            gc.fillOval(ghost.x * TILE_SIZE + 2, ghost.y * TILE_SIZE + 2,
                    TILE_SIZE - 4, TILE_SIZE - 4);
            gc.setFill(Color.WHITE);
            gc.fillOval(ghost.x * TILE_SIZE + 6, ghost.y * TILE_SIZE + 6, 4, 4);
            gc.fillOval(ghost.x * TILE_SIZE + 14, ghost.y * TILE_SIZE + 6, 4, 4);
            gc.setFill(Color.BLACK);
            gc.fillOval(ghost.x * TILE_SIZE + 7, ghost.y * TILE_SIZE + 7, 2, 2);
            gc.fillOval(ghost.x * TILE_SIZE + 15, ghost.y * TILE_SIZE + 7, 2, 2);
        }

        drawPacman(pacman1X, pacman1Y, direction1, Color.YELLOW);

        if (currentMode != GameMode.SOLO) {
            drawPacman(pacman2X, pacman2Y, direction2, Color.RED);
        }
    }

    private void drawPacman(int x, int y, int dir, Color color) {
        gc.setFill(color);
        gc.fillArc(x * TILE_SIZE + 2, y * TILE_SIZE + 2,
                TILE_SIZE - 4, TILE_SIZE - 4,
                dir * 90 + 30, 300, javafx.scene.shape.ArcType.ROUND);
    }

    private String getModeTitle() {
        switch (currentMode) {
            case SOLO: return "Solo Mode";
            case VS_COMPUTER: return "VS Computer";
            case MULTIPLAYER: return "Multiplayer";
            default: return "Pacman";
        }
    }

    private String getScoreText() {
        if (currentMode == GameMode.SOLO) {
            return "Score: " + score1;
        } else {
            return "P1: " + score1 + "  |  P2: " + score2;
        }
    }

    private String getLivesText() {
        if (currentMode == GameMode.SOLO) {
            return "Lives: " + lives1;
        } else {
            return "P1 Lives: " + lives1 + "  |  P2 Lives: " + lives2;
        }
    }

    private String getControlsText() {
        if (currentMode == GameMode.MULTIPLAYER) {
            return "P1: Arrow Keys | P2: WASD";
        } else {
            return "Arrow Keys to Move | Eat all dots!";
        }
    }

    private void updateLabels() {
        scoreLabel.setText(getScoreText());
        livesLabel.setText(getLivesText());
    }

    private void styleButton(Button btn) {
        btn.setStyle("-fx-background-color: #FFD700; -fx-text-fill: black; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #FFA500; -fx-text-fill: black; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #FFD700; -fx-text-fill: black; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;"));
    }

    @Override
    public void stop() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
    }

    class Ghost {
        int x, y;
        int startX, startY;
        Color color;
        String name;

        Ghost(int x, int y, Color color, String name) {
            this.x = x;
            this.y = y;
            this.startX = x;
            this.startY = y;
            this.color = color;
            this.name = name;
        }

        void move(boolean[][] walls, int target1X, int target1Y, int target2X, int target2Y, boolean twoPlayers) {
            int targetX = target1X;
            int targetY = target1Y;

            if (twoPlayers) {
                int dist1 = Math.abs(x - target1X) + Math.abs(y - target1Y);
                int dist2 = Math.abs(x - target2X) + Math.abs(y - target2Y);
                if (dist2 < dist1) {
                    targetX = target2X;
                    targetY = target2Y;
                }
            }

            int dx = Integer.compare(targetX, x);
            int dy = Integer.compare(targetY, y);

            if (dx != 0 && x + dx >= 0 && x + dx < GRID_WIDTH && !walls[y][x + dx]) {
                x += dx;
            }
            else if (dy != 0 && y + dy >= 0 && y + dy < GRID_HEIGHT && !walls[y + dy][x]) {
                y += dy;
            }
            else {
                List<Integer> validDirs = new ArrayList<>();
                if (x + 1 < GRID_WIDTH && !walls[y][x + 1]) validDirs.add(0);
                if (y + 1 < GRID_HEIGHT && !walls[y + 1][x]) validDirs.add(1);
                if (x - 1 >= 0 && !walls[y][x - 1]) validDirs.add(2);
                if (y - 1 >= 0 && !walls[y - 1][x]) validDirs.add(3);

                if (!validDirs.isEmpty()) {
                    int dir = validDirs.get(random.nextInt(validDirs.size()));
                    if (dir == 0) x++;
                    else if (dir == 1) y++;
                    else if (dir == 2) x--;
                    else if (dir == 3) y--;
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}