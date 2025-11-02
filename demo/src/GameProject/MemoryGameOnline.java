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
import java.net.*;
import java.util.*;

public class MemoryGameOnline extends Application {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Stage primaryStage;
    private GridPane grid;
    private Label player1Label, player2Label, statusLabel;
    private TextArea chatArea;
    private TextField chatInput;
    private String playerName;
    private String opponentName;
    private List<String> cardValues;
    private Image backImage;
    private ImageView firstCard = null;
    private int firstCardIndex = -1;
    private String firstCardValue = null;
    private int myScore = 0;
    private int opponentScore = 0;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        showConnectionDialog();
    }

    private void showConnectionDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Connect to Server");
        dialog.setHeaderText("Enter Server Details");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        TextField nameField = new TextField();
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
        Platform.runLater(nameField::requestFocus);

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
            connectToServer(host, port);
        });

        if (!result.isPresent()) {
            Platform.exit();
        }
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(this::listenToServer).start();

            showWaitingScreen();
        } catch (IOException e) {
            showError("Connection Failed", "Could not connect to server: " + e.getMessage());
            Platform.exit();
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
            primaryStage.setTitle("Memory Game Online - Matchmaking");
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
            Platform.runLater(() -> showError("Connection Lost", "Lost connection to server"));
        }
    }

    private void handleServerMessage(String message) {
        String[] parts = message.split(":");
        String command = parts[0];

        switch (command) {
            case "ENTER_NAME":
                out.println(playerName);
                break;

            case "WAITING":
                // Already showing waiting screen
                break;

            case "GAME_START":
                opponentName = parts[1];
                break;

            case "CARD_LAYOUT":
                cardValues = new ArrayList<>(Arrays.asList(parts[1].split(",")));
                startGame();
                break;

            case "STATUS":
                statusLabel.setText(parts[1]);
                break;

            case "CARD_REVEAL":
                // Not used in race mode - each player sees their own cards
                break;

            case "MATCH":
                int matchIdx1 = Integer.parseInt(parts[1]);
                int matchIdx2 = Integer.parseInt(parts[2]);
                handleMatchConfirmed(matchIdx1, matchIdx2);
                break;

            case "NO_MATCH":
                int noMatchIdx1 = Integer.parseInt(parts[1]);
                int noMatchIdx2 = Integer.parseInt(parts[2]);
                handleNoMatch(noMatchIdx1, noMatchIdx2);
                break;

            case "SCORE":
                myScore = Integer.parseInt(parts[1]);
                opponentScore = Integer.parseInt(parts[2]);
                player1Label.setText(playerName + ": " + myScore);
                player2Label.setText(opponentName + ": " + opponentScore);

                // Highlight leader
                if (myScore > opponentScore) {
                    player1Label.setTextFill(Color.YELLOW);
                    player2Label.setTextFill(Color.WHITE);
                } else if (opponentScore > myScore) {
                    player1Label.setTextFill(Color.WHITE);
                    player2Label.setTextFill(Color.YELLOW);
                } else {
                    player1Label.setTextFill(Color.WHITE);
                    player2Label.setTextFill(Color.WHITE);
                }
                break;

            case "CHAT":
                chatArea.appendText(parts[1] + "\n");
                break;

            case "GAME_END":
                showGameEnd(parts[1], parts[2], parts[3]);
                break;

            case "OPPONENT_DISCONNECTED":
                showError("Opponent Disconnected", "Your opponent left the game");
                break;

            case "ERROR":
                statusLabel.setText("❌ " + parts[1]);
                break;
        }
    }

    private void startGame() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Top panel with scores
        HBox topPanel = createTopPanel();
        root.setTop(topPanel);

        // Center: Game grid
        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(20));

        initializeGameGrid();

        root.setCenter(grid);

        // Right panel: Chat
        VBox chatPanel = createChatPanel();
        root.setRight(chatPanel);

        // Bottom panel
        HBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Memory Game Online - " + playerName + " VS " + opponentName);
    }

    private HBox createTopPanel() {
        HBox panel = new HBox(20);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background: linear-gradient(to right, #667eea, #764ba2);");

        player1Label = createPlayerLabel(playerName + ": 0", true);
        player2Label = createPlayerLabel(opponentName + ": 0", false);

        Label vsLabel = new Label(" 🏁 RACE 🏁 ");
        vsLabel.setTextFill(Color.YELLOW);
        vsLabel.setFont(Font.font("System", FontWeight.BOLD, 24));

        panel.getChildren().addAll(player1Label, vsLabel, player2Label);
        return panel;
    }

    private Label createPlayerLabel(String text, boolean active) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 20));
        label.setTextFill(active ? Color.YELLOW : Color.WHITE);
        label.setPadding(new Insets(10, 20, 10, 20));
        label.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-background-radius: 10;");
        return label;
    }

    private VBox createChatPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(20));
        panel.setPrefWidth(250);
        panel.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 0 0 0 2;");

        Label chatTitle = new Label("💬 Chat");
        chatTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(500);
        chatArea.setStyle("-fx-control-inner-background: #f9f9f9;");

        chatInput = new TextField();
        chatInput.setPromptText("Type a message...");
        chatInput.setOnAction(e -> sendChat());

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> sendChat());

        HBox chatInputBox = new HBox(5);
        chatInputBox.getChildren().addAll(chatInput, sendBtn);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        panel.getChildren().addAll(chatTitle, chatArea, chatInputBox);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        return panel;
    }

    private HBox createBottomPanel() {
        HBox panel = new HBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));

        statusLabel = new Label("Waiting for game to start...");
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        panel.getChildren().add(statusLabel);
        return panel;
    }

    private void initializeGameGrid() {
        backImage = new Image(getClass().getResourceAsStream("/images/back.jpg"));

        int index = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                ImageView card = new ImageView(backImage);
                card.setFitWidth(100);
                card.setFitHeight(100);
                card.setStyle("-fx-cursor: hand;");

                DropShadow shadow = new DropShadow();
                shadow.setColor(Color.rgb(0, 0, 0, 0.3));
                card.setEffect(shadow);

                int finalR = r;
                int finalC = c;
                int finalIndex = index;

                card.setOnMouseClicked(e -> handleCardClick(finalR, finalC, finalIndex));
                card.setOnMouseEntered(e -> {
                    if (card.getImage() == backImage) {
                        card.setScaleX(1.05);
                        card.setScaleY(1.05);
                    }
                });
                card.setOnMouseExited(e -> {
                    card.setScaleX(1.0);
                    card.setScaleY(1.0);
                });

                grid.add(card, c, r);
                index++;
            }
        }
    }

    private void handleCardClick(int row, int col, int index) {
        ImageView card = getCardAt(row, col);
        if (card.getImage() != backImage) return; // Already revealed

        String value = cardValues.get(index);

        // Reveal card locally
        Image cardImage = new Image(getClass().getResourceAsStream("/images/" + value + ".jpg"));
        card.setImage(cardImage);

        ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
        st.setFromX(1.0);
        st.setToX(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();

        if (firstCard == null) {
            // First card of pair
            firstCard = card;
            firstCardIndex = index;
            firstCardValue = value;
        } else {
            // Second card - check match on server
            out.println("CHECK_MATCH:" + firstCardIndex + ":" + index + ":" + firstCardValue + ":" + value);
        }
    }

    private void handleMatchConfirmed(int idx1, int idx2) {
        // Match confirmed by server - cards stay revealed
        firstCard = null;
        firstCardIndex = -1;
        firstCardValue = null;

        statusLabel.setText("✅ Match found! Keep going!");
    }

    private void handleNoMatch(int idx1, int idx2) {
        // No match - hide cards after delay
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> {
            getCardByIndex(idx1).setImage(backImage);
            getCardByIndex(idx2).setImage(backImage);
            firstCard = null;
            firstCardIndex = -1;
            firstCardValue = null;
        });
        pause.play();

        statusLabel.setText("❌ No match. Try again!");
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

    private void sendChat() {
        String message = chatInput.getText().trim();
        if (!message.isEmpty()) {
            out.println("CHAT:" + message);
            chatInput.clear();
        }
    }

    private void showGameEnd(String result, String score1, String score2) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");

        switch (result) {
            case "WIN":
                alert.setHeaderText("🏆 Victory! 🏆");
                break;
            case "LOSE":
                alert.setHeaderText("💔 Defeat");
                break;
            case "TIE":
                alert.setHeaderText("🤝 It's a Tie!");
                break;
        }

        alert.setContentText("Final Score:\n" +
                playerName + ": " + score1 + "\n" +
                opponentName + ": " + score2);

        alert.showAndWait();
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