package GameProject;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class MemoryGame extends Application {

    private enum GameMode {SOLO, VS_PLAYER, ONLINE}

    private GameMode currentMode;
    private GridPane grid;
    private Label player1Label, player2Label, statusLabel;
    private int player1Score = 0, player2Score = 0;
    private boolean isPlayer1Turn = true;
    private ImageView firstCard = null, secondCard = null;
    private String firstValue, secondValue;
    private List<String> values = new ArrayList<>();
    private Image backImage;
    private int matchedPairs = 0;
    private final int totalPairs = 10;
    private Stage primaryStage;

    private boolean isProcessingCards = false;
    private boolean gameEnded = false;

    private DatabaseManager dbManager;
    private UserAuthManager authManager;
    private long gameStartTime;
    private int totalMoves = 0;

    // Online mode
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerName;
    private String opponentName;
    private Thread messageListener;
    private int firstCardIndex = -1;
    private String firstCardValue = null;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        dbManager = DatabaseManager.getInstance();
        authManager = UserAuthManager.getInstance();

        showModeSelection();
    }

    private void showModeSelection() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label title = new Label("Memory Game");
        title.setFont(Font.font("System", FontWeight.BOLD, 48));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Select Game Mode");
        subtitle.setFont(Font.font("System", 18));
        subtitle.setTextFill(Color.web("#E8E8FF"));

        Label playerLabel = new Label("Player: " + authManager.getDisplayName());
        playerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        playerLabel.setTextFill(Color.web("#FFEB3B"));

        VBox buttonBox = new VBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(350);

        Button soloBtn = createMenuButton("Solo Play", "Test your memory skills");
        Button passPlayBtn = createMenuButton("Pass & Play", "Local 2-player game");
        Button onlineBtn = createMenuButton("Online Multiplayer", "Race with player online");
        Button leaderboardBtn = createMenuButton("Leaderboard", "View top scores");
        Button exitBtn = createMenuButton("Exit", "Close game");

        soloBtn.setOnAction(e -> startGame(GameMode.SOLO, null));
        passPlayBtn.setOnAction(e -> startGame(GameMode.VS_PLAYER, null));
        onlineBtn.setOnAction(e -> showOnlineConnection());
        leaderboardBtn.setOnAction(e -> showLeaderboard());
        exitBtn.setOnAction(e -> primaryStage.close());

        buttonBox.getChildren().addAll(soloBtn, passPlayBtn, onlineBtn, leaderboardBtn, exitBtn);
        root.getChildren().addAll(title, subtitle, playerLabel, buttonBox);

        Scene scene = new Scene(root, 600, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Memory Game - Mode Selection");
        primaryStage.show();
    }

    private void showOnlineConnection() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Connect to Server");
        dialog.setHeaderText("Enter Server Details");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        TextField nameField = new TextField(authManager.getDisplayName());
        nameField.setPromptText("Your Name");
        TextField hostField = new TextField("localhost");
        hostField.setPromptText("Server Address");
        TextField portField = new TextField("5555");
        portField.setPromptText("Port");

        gridPane.add(new Label("Name:"), 0, 0);
        gridPane.add(nameField, 1, 0);
        gridPane.add(new Label("Host:"), 0, 1);
        gridPane.add(hostField, 1, 1);
        gridPane.add(new Label("Port:"), 0, 2);
        gridPane.add(portField, 1, 2);

        dialog.getDialogPane().setContent(gridPane);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                return new String[]{nameField.getText(), hostField.getText(), portField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            playerName = data[0];
            String host = data[1];
            int port = Integer.parseInt(data[2]);
            connectToOnlineServer(host, port);
        });
    }

    private void connectToOnlineServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            messageListener = new Thread(this::listenToServer);
            messageListener.setDaemon(true);
            messageListener.start();

            showWaitingScreen();
        } catch (IOException e) {
            showError("Connection Failed", "Could not connect to server: " + e.getMessage());
            showModeSelection();
        }
    }

    private void showWaitingScreen() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label title = new Label("Waiting for Opponent...");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));
        title.setTextFill(Color.WHITE);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setStyle("-fx-progress-color: white;");

        Label subtitle = new Label("Finding a match for you");
        subtitle.setFont(Font.font("System", 16));
        subtitle.setTextFill(Color.web("#E8E8FF"));

        root.getChildren().addAll(title, progress, subtitle);

        Scene scene = new Scene(root, 500, 400);
        Platform.runLater(() -> {
            primaryStage.setScene(scene);
            primaryStage.setTitle("Memory Game - Waiting for Opponent");
        });
    }

    private void listenToServer() {
        try {
            // Send player name immediately
            out.println(playerName);

            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
                System.out.println("Server: " + finalMessage); // Debug
                Platform.runLater(() -> handleOnlineMessage(finalMessage));
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                if (!gameEnded) {
                    showError("Connection Lost", "Lost connection to server");
                    showModeSelection();
                }
            });
        }
    }

    private void handleOnlineMessage(String message) {
        String[] parts = message.split(":", -1);
        String command = parts[0];

        switch (command) {
            case "WAITING":
                // Already showing waiting screen
                break;

            case "GAME_START":
                if (parts.length >= 3) {
                    opponentName = parts[1];
                    String cardLayout = parts[2];
                    values = new ArrayList<>(Arrays.asList(cardLayout.split(",")));
                    startGame(GameMode.ONLINE, null);
                }
                break;

            case "CARD_FLIPPED":
                // Your own card flipped - already handled locally
                break;

            case "OPPONENT_CARD_FLIPPED":
                if (parts.length >= 3) {
                    Platform.runLater(() -> statusLabel.setText(opponentName + " is playing..."));
                }
                break;

            case "MATCH_SUCCESS":
                // Your match success - cards stay revealed
                firstCard = null;
                firstCardIndex = -1;
                firstCardValue = null;
                isProcessingCards = false;
                statusLabel.setText("✅ Match found! Keep going!");
                break;

            case "NO_MATCH":
                if (parts.length >= 3) {
                    int idx1 = Integer.parseInt(parts[1]);
                    int idx2 = Integer.parseInt(parts[2]);

                    // Hide cards after delay
                    PauseTransition pause = new PauseTransition(Duration.seconds(1));
                    pause.setOnFinished(e -> {
                        getCardByIndex(idx1).setImage(backImage);
                        getCardByIndex(idx2).setImage(backImage);
                        firstCard = null;
                        firstCardIndex = -1;
                        firstCardValue = null;
                        isProcessingCards = false;
                    });
                    pause.play();
                    statusLabel.setText("❌ No match. Try again!");
                }
                break;

            case "OPPONENT_MATCH":
                if (parts.length >= 3) {
                    int idx1 = Integer.parseInt(parts[1]);
                    int idx2 = Integer.parseInt(parts[2]);

                    // Show opponent's matched cards with visual effect
                    ImageView opponentCard1 = getCardByIndex(idx1);
                    ImageView opponentCard2 = getCardByIndex(idx2);

                    if (opponentCard1 != null && opponentCard2 != null && values != null && idx1 < values.size()) {
                        String cardValue = values.get(idx1);
                        opponentCard1.setImage(new Image(getClass().getResourceAsStream("/images/" + cardValue + ".jpg")));
                        opponentCard2.setImage(new Image(getClass().getResourceAsStream("/images/" + cardValue + ".jpg")));

                        // Add green glow effect
                        DropShadow glow = new DropShadow();
                        glow.setColor(Color.LIME);
                        glow.setRadius(20);
                        glow.setSpread(0.6);

                        opponentCard1.setEffect(glow);
                        opponentCard2.setEffect(glow);

                        // Pulse animation
                        ScaleTransition scale1 = new ScaleTransition(Duration.millis(300), opponentCard1);
                        scale1.setFromX(1.0);
                        scale1.setToX(1.15);
                        scale1.setAutoReverse(true);
                        scale1.setCycleCount(2);

                        ScaleTransition scale2 = new ScaleTransition(Duration.millis(300), opponentCard2);
                        scale2.setFromX(1.0);
                        scale2.setToX(1.15);
                        scale2.setAutoReverse(true);
                        scale2.setCycleCount(2);

                        ImageView finalCard1 = opponentCard1;
                        ImageView finalCard2 = opponentCard2;
                        scale1.setOnFinished(e -> {
                            DropShadow normalShadow = new DropShadow();
                            normalShadow.setColor(Color.rgb(0, 0, 0, 0.3));
                            finalCard1.setEffect(normalShadow);
                            finalCard2.setEffect(normalShadow);
                        });

                        scale1.play();
                        scale2.play();
                    }
                }
                statusLabel.setText("🔥 " + opponentName + " found a match!");
                break;

            case "OPPONENT_NO_MATCH":
                if (parts.length >= 3) {
                    int idx1 = Integer.parseInt(parts[1]);
                    int idx2 = Integer.parseInt(parts[2]);

                    // Briefly show opponent's failed attempt
                    ImageView card1 = getCardByIndex(idx1);
                    ImageView card2 = getCardByIndex(idx2);

                    if (card1 != null && card2 != null && values != null && idx1 < values.size() && idx2 < values.size()) {
                        String value1 = values.get(idx1);
                        String value2 = values.get(idx2);

                        card1.setImage(new Image(getClass().getResourceAsStream("/images/" + value1 + ".jpg")));
                        card2.setImage(new Image(getClass().getResourceAsStream("/images/" + value2 + ".jpg")));

                        // Hide after delay
                        PauseTransition pause = new PauseTransition(Duration.seconds(1));
                        pause.setOnFinished(e -> {
                            card1.setImage(backImage);
                            card2.setImage(backImage);
                        });
                        pause.play();
                    }
                }
                statusLabel.setText(opponentName + " missed");
                break;

            case "SCORE":
                if (parts.length >= 3) {
                    player1Score = Integer.parseInt(parts[1]);
                    player2Score = Integer.parseInt(parts[2]);
                    updateScores();
                }
                break;

            case "GAME_END":
                if (parts.length >= 4) {
                    String result = parts[1];
                    int myScore = Integer.parseInt(parts[2]);
                    int oppScore = Integer.parseInt(parts[3]);
                    gameEnded = true;
                    showOnlineGameEnd(result, myScore, oppScore);
                }
                break;

            case "OPPONENT_DISCONNECTED":
                gameEnded = true;
                showError("Opponent Disconnected", "Your opponent left the game");
                Platform.runLater(() -> showModeSelection());
                break;
        }
    }

    private void showOnlineGameEnd(String result, int myScore, int oppScore) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");

        if ("WIN".equals(result)) {
            alert.setHeaderText("🏆 You Won! 🏆");
        } else if ("LOSE".equals(result)) {
            alert.setHeaderText("💔 You Lost");
        } else {
            alert.setHeaderText("🤝 It's a Tie!");
        }

        alert.setContentText("Final Score:\nYou: " + myScore + "\n" + opponentName + ": " + oppScore);

        ButtonType newGameBtn = new ButtonType("New Game");
        ButtonType menuBtn = new ButtonType("Main Menu");
        alert.getButtonTypes().setAll(newGameBtn, menuBtn);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == newGameBtn) {
            showOnlineConnection();
        } else {
            showModeSelection();
        }
    }

    private void showLeaderboard() {
        Stage leaderboardStage = new Stage();
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label title = new Label("🏆 Leaderboard");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");

        Tab soloTab = new Tab("Solo");
        soloTab.setClosable(false);
        ListView<String> soloList = new ListView<>();
        List<GameScore> soloScores = dbManager.getTopScores("MemoryGame", "solo", 10);
        populateLeaderboard(soloList, soloScores);
        soloTab.setContent(soloList);

        Tab multiTab = new Tab("Pass & Play");
        multiTab.setClosable(false);
        ListView<String> multiList = new ListView<>();
        List<GameScore> multiScores = dbManager.getTopScores("MemoryGame", "multiplayer", 10);
        populateLeaderboard(multiList, multiScores);
        multiTab.setContent(multiList);

        Tab onlineTab = new Tab("Online");
        onlineTab.setClosable(false);
        ListView<String> onlineList = new ListView<>();
        List<GameScore> onlineScores = dbManager.getTopScores("MemoryGame", "online", 10);
        populateLeaderboard(onlineList, onlineScores);
        onlineTab.setContent(onlineList);

        tabPane.getTabs().addAll(soloTab, multiTab, onlineTab);

        Button closeBtn = new Button("Close");
        styleButton(closeBtn);
        closeBtn.setOnAction(e -> leaderboardStage.close());

        vbox.getChildren().addAll(title, tabPane, closeBtn);

        Scene scene = new Scene(vbox, 600, 700);
        leaderboardStage.setScene(scene);
        leaderboardStage.setTitle("Leaderboard");
        leaderboardStage.show();
    }

    private void populateLeaderboard(ListView<String> listView, List<GameScore> scores) {
        int rank = 1;
        for (GameScore score : scores) {
            String entry = String.format("#%d - %s: %d pts (%s) - %ds",
                    rank++, score.getPlayerName(), score.getScore(),
                    score.getDifficulty(), score.getPlayTime());
            listView.getItems().add(entry);
        }
        if (scores.isEmpty()) {
            listView.getItems().add("No scores yet. Be the first to play!");
        }
    }

    private Button createMenuButton(String text, String subtitle) {
        VBox btnContent = new VBox(5);
        btnContent.setAlignment(Pos.CENTER);

        Label mainText = new Label(text);
        mainText.setFont(Font.font("System", FontWeight.BOLD, 18));
        mainText.setTextFill(Color.web("#667eea"));

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

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #f0f0ff; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 8);"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 5);"));

        return btn;
    }

    private void startGame(GameMode mode, Object diff) {
        this.currentMode = mode;
        player1Score = 0;
        player2Score = 0;
        matchedPairs = 0;
        isPlayer1Turn = true;
        gameStartTime = System.currentTimeMillis();
        totalMoves = 0;
        isProcessingCards = false;
        gameEnded = false;
        firstCard = null;
        secondCard = null;
        firstCardIndex = -1;
        firstCardValue = null;

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        HBox topPanel = createTopPanel();
        root.setTop(topPanel);

        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(20));

        if (mode != GameMode.ONLINE) {
            initializeGame();
        } else {
            initializeOnlineGame();
        }
        root.setCenter(grid);

        HBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 700, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Memory Game - " + mode);
    }

    private HBox createTopPanel() {
        HBox panel = new HBox(20);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background: linear-gradient(to right, #667eea, #764ba2);");

        if (currentMode == GameMode.SOLO) {
            player1Label = new Label("Matches: 0 / " + totalPairs);
            player1Label.setFont(Font.font("System", FontWeight.BOLD, 24));
            player1Label.setTextFill(Color.WHITE);
            panel.getChildren().add(player1Label);
        } else {
            String player1Name = currentMode == GameMode.ONLINE ? "You" : "Player 1";
            String player2Name = currentMode == GameMode.ONLINE ? (opponentName != null ? opponentName : "Opponent") : "Player 2";

            Color p1Color = currentMode == GameMode.VS_PLAYER ? Color.web("#FF6B6B") : Color.LIME;
            Color p2Color = currentMode == GameMode.VS_PLAYER ? Color.web("#4ECDC4") : Color.WHITE;

            player1Label = createPlayerLabel(player1Name + ": 0", true, p1Color);
            player2Label = createPlayerLabel(player2Name + ": 0", false, p2Color);

            Label vsLabel = new Label(" VS ");
            vsLabel.setTextFill(Color.WHITE);
            vsLabel.setFont(Font.font("System", FontWeight.BOLD, 20));

            panel.getChildren().addAll(player1Label, vsLabel, player2Label);
        }

        return panel;
    }

    private Label createPlayerLabel(String text, boolean active, Color playerColor) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 20));
        label.setTextFill(active ? playerColor : Color.WHITE);
        label.setPadding(new Insets(10, 20, 10, 20));
        label.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-background-radius: 10;");
        return label;
    }

    private HBox createBottomPanel() {
        HBox panel = new HBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));

        String statusText = currentMode == GameMode.SOLO ? "Find all pairs!" :
                currentMode == GameMode.VS_PLAYER ? "Player 1's turn!" : "Your turn!";
        statusLabel = new Label(statusText);
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        Button newGameBtn = new Button("New Game");
        Button menuBtn = new Button("Main Menu");

        styleButton(newGameBtn);
        styleButton(menuBtn);

        newGameBtn.setOnAction(e -> {
            gameEnded = true;
            startGame(currentMode, null);
        });
        menuBtn.setOnAction(e -> {
            gameEnded = true;
            try {
                if (socket != null) socket.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            showModeSelection();
        });

        panel.getChildren().addAll(statusLabel, newGameBtn, menuBtn);
        return panel;
    }

    private void styleButton(Button btn) {
        btn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #5568d3; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;"));
    }

    private void initializeGame() {
        backImage = new Image(getClass().getResourceAsStream("/images/back.jpg"));

        String[] cardList = {"darkness", "double", "fairy", "fighting", "fire",
                "grass", "lightning", "metal", "psychic", "water"};

        values.clear();
        for (String c : cardList) {
            values.add(c);
            values.add(c);
        }
        Collections.shuffle(values);

        int cardIndex = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                String cardValue = values.get(cardIndex);
                ImageView card = new ImageView(backImage);
                card.setFitWidth(100);
                card.setFitHeight(100);
                card.setStyle("-fx-cursor: hand;");

                DropShadow shadow = new DropShadow();
                shadow.setColor(Color.rgb(0, 0, 0, 0.3));
                card.setEffect(shadow);

                final int finalIndex = cardIndex;
                card.setOnMouseClicked(e -> handleCardClick(card, cardValue, finalIndex));
                card.setOnMouseEntered(e -> {
                    if (card.getImage() == backImage && !isProcessingCards && !gameEnded) {
                        card.setScaleX(1.05);
                        card.setScaleY(1.05);
                    }
                });
                card.setOnMouseExited(e -> {
                    card.setScaleX(1.0);
                    card.setScaleY(1.0);
                });

                grid.add(card, c, r);
                cardIndex++;
            }
        }
    }

    private void initializeOnlineGame() {
        backImage = new Image(getClass().getResourceAsStream("/images/back.jpg"));

        int cardIndex = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                ImageView card = new ImageView(backImage);
                card.setFitWidth(100);
                card.setFitHeight(100);
                card.setStyle("-fx-cursor: hand;");

                DropShadow shadow = new DropShadow();
                shadow.setColor(Color.rgb(0, 0, 0, 0.3));
                card.setEffect(shadow);

                final int finalIndex = cardIndex;
                card.setOnMouseClicked(e -> {
                    if (!isProcessingCards && !gameEnded && card.getImage() == backImage) {
                        String value = values.get(finalIndex);
                        handleOnlineCardClick(card, value, finalIndex);
                    }
                });
                card.setOnMouseEntered(e -> {
                    if (card.getImage() == backImage && !isProcessingCards && !gameEnded) {
                        card.setScaleX(1.05);
                        card.setScaleY(1.05);
                    }
                });
                card.setOnMouseExited(e -> {
                    card.setScaleX(1.0);
                    card.setScaleY(1.0);
                });

                grid.add(card, c, r);
                cardIndex++;
            }
        }
    }

    private void handleOnlineCardClick(ImageView card, String value, int index) {
        if (isProcessingCards || gameEnded || card.getImage() != backImage) {
            return;
        }

        revealCard(card, value);

        if (firstCard == null) {
            firstCard = card;
            firstCardIndex = index;
            firstCardValue = value;
            out.println("CARD_CLICK:" + index + ":" + value);
        } else if (card != firstCard) {
            secondCard = card;
            isProcessingCards = true;
            out.println("CARD_CLICK:" + index + ":" + value);
        }
    }

    private void handleCardClick(ImageView card, String value, int index) {
        if (isProcessingCards || gameEnded || card == firstCard || card == secondCard ||
                card.getImage() != backImage) {
            return;
        }

        if (firstCard == null) {
            totalMoves++;
        }

        revealCard(card, value);

        if (firstCard == null) {
            firstCard = card;
            firstValue = value;
        } else if (secondCard == null) {
            secondCard = card;
            secondValue = value;
            isProcessingCards = true;

            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(e -> checkMatch());
            pause.play();
        }
    }

    private void revealCard(ImageView card, String value) {
        card.setImage(new Image(getClass().getResourceAsStream("/images/" + value + ".jpg")));

        ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.1);
        st.setToY(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    private void checkMatch() {
        if (firstCard != null && secondCard != null) {
            if (firstValue.equals(secondValue)) {
                matchedPairs++;

                if (isPlayer1Turn || currentMode == GameMode.SOLO) {
                    player1Score++;
                } else {
                    player2Score++;
                }

                updateScores();

                if (matchedPairs == totalPairs) {
                    gameEnded = true;
                    // Delay showing win message to ensure UI updates
                    PauseTransition winPause = new PauseTransition(Duration.millis(500));
                    winPause.setOnFinished(e -> Platform.runLater(this::showWinMessage));
                    winPause.play();
                } else {
                    if (currentMode == GameMode.SOLO) {
                        statusLabel.setText("✅ Great! Keep going!");
                    } else if (currentMode == GameMode.VS_PLAYER) {
                        statusLabel.setText("✅ Match! " + (isPlayer1Turn ? "Player 1" : "Player 2") + " gets another turn!");
                    }
                    isProcessingCards = false;
                }
            } else {
                hideCards(firstCard, secondCard);
                if (currentMode == GameMode.VS_PLAYER) {
                    switchTurn();
                }
                isProcessingCards = false;
            }

            firstCard = null;
            secondCard = null;
        }
    }

    private void hideCards(ImageView card1, ImageView card2) {
        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> {
            card1.setImage(backImage);
            card2.setImage(backImage);
        });
        pause.play();
    }

    private void switchTurn() {
        if (currentMode != GameMode.SOLO) {
            isPlayer1Turn = !isPlayer1Turn;
            updateTurnIndicator();
        }
    }

    private void updateScores() {
        if (currentMode == GameMode.SOLO) {
            player1Label.setText("Matches: " + player1Score + " / " + totalPairs);
        } else if (currentMode == GameMode.ONLINE) {
            player1Label.setText("You: " + player1Score);
            player2Label.setText(opponentName + ": " + player2Score);
        } else {
            player1Label.setText("Player 1: " + player1Score);
            player2Label.setText("Player 2: " + player2Score);
            updateTurnIndicator();
        }
    }

    private void updateTurnIndicator() {
        if (currentMode == GameMode.VS_PLAYER) {
            player1Label.setTextFill(isPlayer1Turn ? Color.web("#FF6B6B") : Color.WHITE);
            player2Label.setTextFill(!isPlayer1Turn ? Color.web("#4ECDC4") : Color.WHITE);
            statusLabel.setText((isPlayer1Turn ? "Player 1" : "Player 2") + "'s turn!");
        }
    }

    private void showWinMessage() {
        if (gameEnded) {
            saveGameToDatabase();
        }

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("🎉 Game Over");

            if (currentMode == GameMode.SOLO) {
                alert.setHeaderText("🏆 Congratulations!");
                int playTime = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
                int finalScore = Math.max(0, 1000 - (totalMoves * 10) - playTime);
                alert.setContentText("You found all " + totalPairs + " pairs!\n\n" +
                        "Total Moves: " + totalMoves + "\n" +
                        "Time: " + playTime + " seconds\n" +
                        "Score: " + finalScore + " points");
            } else if (currentMode == GameMode.VS_PLAYER) {
                if (player1Score > player2Score) {
                    alert.setHeaderText("🏆 Player 1 Wins!");
                } else if (player2Score > player1Score) {
                    alert.setHeaderText("🏆 Player 2 Wins!");
                } else {
                    alert.setHeaderText("🤝 It's a Tie!");
                }
                int playTime = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
                alert.setContentText("Final Score:\n\n" +
                        "Player 1: " + player1Score + " pairs\n" +
                        "Player 2: " + player2Score + " pairs\n\n" +
                        "Game Time: " + playTime + " seconds");
            }

            ButtonType playAgainBtn = new ButtonType("Play Again", ButtonBar.ButtonData.OK_DONE);
            ButtonType menuBtn = new ButtonType("Main Menu", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(playAgainBtn, menuBtn);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == playAgainBtn) {
                    startGame(currentMode, null);
                } else {
                    showModeSelection();
                }

                // Show player stats if logged in
                if (authManager.isLoggedIn()) {
                    showPlayerStats();
                }
            }
        });
    }

    private void saveGameToDatabase() {
        int playTime = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        String gameMode, difficultyStr = "normal";
        boolean playerWon = false;
        int finalScore;

        if (currentMode == GameMode.SOLO) {
            gameMode = "solo";
            playerWon = true;
            finalScore = Math.max(0, 1000 - (totalMoves * 10) - playTime);
        } else if (currentMode == GameMode.VS_PLAYER) {
            gameMode = "multiplayer";
            playerWon = player1Score >= player2Score;
            finalScore = player1Score * 100;
        } else {
            gameMode = "online";
            playerWon = player1Score > player2Score;
            finalScore = player1Score * 100;
        }

        dbManager.saveScore("MemoryGame", authManager.getDisplayName(), finalScore, gameMode, difficultyStr, playTime, playerWon);
    }

    private void showPlayerStats() {
        PlayerStats stats = dbManager.getPlayerStats(authManager.getDisplayName(), "MemoryGame");
        if (stats != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Your Statistics");
            alert.setHeaderText("📊 " + authManager.getDisplayName() + "'s Memory Stats");
            alert.setContentText(String.format(
                    "Games Played: %d\n" +
                            "Best Score: %d\n" +
                            "Average Score: %.1f\n" +
                            "Wins: %d\n" +
                            "Win Rate: %.1f%%",
                    stats.getGamesPlayed(),
                    stats.getBestScore(),
                    stats.getAvgScore(),
                    stats.getWins(),
                    (stats.getGamesPlayed() > 0 ? (stats.getWins() * 100.0 / stats.getGamesPlayed()) : 0)));
            alert.showAndWait();
        }
    }

    private ImageView getCardByIndex(int index) {
        int row = index / 5;
        int col = index % 5;
        return getCardAt(row, col);
    }

    private ImageView getCardAt(int row, int col) {
        for (var node : grid.getChildren()) {
            Integer nodeRow = GridPane.getRowIndex(node);
            Integer nodeCol = GridPane.getColumnIndex(node);
            if ((nodeRow == null ? 0 : nodeRow) == row && (nodeCol == null ? 0 : nodeCol) == col) {
                return (ImageView) node;
            }
        }
        return null;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        gameEnded = true;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}