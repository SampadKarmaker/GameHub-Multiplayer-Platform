package GameProject;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class MainMenu extends Application {

    private Stage primaryStage;
    private UserAuthManager authManager;
    private DatabaseManager dbManager;
    private Label userStatusLabel;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Initialize authentication and database
        authManager = UserAuthManager.getInstance();
        dbManager = DatabaseManager.getInstance();

        // Show login/guest screen first
        showLoginScreen();
    }

    private void showLoginScreen() {
        VBox root = new VBox(25);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        // Title
        Label title = new Label("🎮 GAME HUB");
        title.setFont(Font.font("System", FontWeight.BOLD, 52));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Login to Save Your Scores!");
        subtitle.setFont(Font.font("System", 18));
        subtitle.setTextFill(Color.web("#FFEB3B"));

        // Login Form
        VBox loginForm = createLoginForm();

        // Guest Button
        Button guestBtn = createStyledButton("👤 Play as Guest", "Scores won't be saved");
        guestBtn.setOnAction(e -> {
            authManager.logout(); // Ensure guest mode
            showMainMenu();
        });

        root.getChildren().addAll(title, subtitle, loginForm, guestBtn);

        Scene scene = new Scene(root, 600, 750);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Game Hub - Login");
        primaryStage.show();
    }

    private VBox createLoginForm() {
        VBox form = new VBox(15);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(400);
        form.setPadding(new Insets(30));
        form.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 15;");

        Label formTitle = new Label("Login");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Username field
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setStyle("-fx-font-size: 14; -fx-padding: 10;");

        // Password field
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-font-size: 14; -fx-padding: 10;");

        // Message label for errors/success
        Label messageLabel = new Label();
        messageLabel.setFont(Font.font("System", 12));
        messageLabel.setWrapText(true);

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button loginBtn = new Button("Login");
        Button signupBtn = new Button("Sign Up");

        styleActionButton(loginBtn, "#667eea");
        styleActionButton(signupBtn, "#4ECDC4");

        loginBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();

            LoginResult result = authManager.login(username, password);

            if (result.isSuccess()) {
                messageLabel.setTextFill(Color.GREEN);
                messageLabel.setText("✓ " + result.getMessage());
                // Delay to show success message
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
                pause.setOnFinished(ev -> showMainMenu());
                pause.play();
            } else {
                messageLabel.setTextFill(Color.RED);
                messageLabel.setText("✗ " + result.getMessage());
            }
        });

        signupBtn.setOnAction(e -> showSignupDialog());

        // Allow Enter key to login
        passwordField.setOnAction(e -> loginBtn.fire());

        buttonBox.getChildren().addAll(loginBtn, signupBtn);
        form.getChildren().addAll(
                formTitle,
                usernameField,
                passwordField,
                buttonBox,
                messageLabel
        );

        return form;
    }

    private void showSignupDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Account");
        dialog.setHeaderText("Sign Up for Game Hub");

        // Create form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username (3-50 characters)");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password (min 6 characters)");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm Password");

        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Confirm:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(emailField, 1, 3);
        grid.add(messageLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType signupButtonType = new ButtonType("Sign Up", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signupButtonType, ButtonType.CANCEL);

        // Validate and register
        Button signupButton = (Button) dialog.getDialogPane().lookupButton(signupButtonType);
        signupButton.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();
            String email = emailField.getText();

            // Validate passwords match
            if (!password.equals(confirmPassword)) {
                messageLabel.setTextFill(Color.RED);
                messageLabel.setText("✗ Passwords do not match!");
                e.consume(); // Prevent dialog from closing
                return;
            }

            RegistrationResult result = authManager.register(username, password,
                    email.isEmpty() ? null : email);

            if (result.isSuccess()) {
                messageLabel.setTextFill(Color.GREEN);
                messageLabel.setText("✓ " + result.getMessage());

                // Auto-login after successful registration
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
                pause.setOnFinished(ev -> {
                    authManager.login(username, password);
                    dialog.close();
                    showMainMenu();
                });
                pause.play();
            } else {
                messageLabel.setTextFill(Color.RED);
                messageLabel.setText("✗ " + result.getMessage());
                e.consume(); // Prevent dialog from closing
            }
        });

        dialog.showAndWait();
    }

    private void showMainMenu() {
        VBox root = new VBox(25);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        // Title
        Label title = new Label("🎮 GAME HUB");
        title.setFont(Font.font("System", FontWeight.BOLD, 52));
        title.setTextFill(Color.WHITE);

        // User status
        userStatusLabel = new Label();
        updateUserStatus();
        userStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        userStatusLabel.setTextFill(Color.web("#FFEB3B"));

        // Game buttons
        VBox buttonBox = new VBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(400);

        Button tictactoeBtn = createStyledButton("❌ Tic-Tac-Toe", "Classic 3x3 strategy game");
        Button memoryBtn = createStyledButton("🎴 Memory Game", "Test your memory skills");
        Button pacmanBtn = createStyledButton("👻 Pacman", "Eat dots, avoid ghosts");

        tictactoeBtn.setOnAction(e -> startTicTacToe());
        memoryBtn.setOnAction(e -> startMemoryGame());
        pacmanBtn.setOnAction(e -> startPacman());

        buttonBox.getChildren().addAll(tictactoeBtn, memoryBtn, pacmanBtn);

        // Bottom buttons
        HBox bottomButtons = new HBox(15);
        bottomButtons.setAlignment(Pos.CENTER);

        if (authManager.isLoggedIn()) {
            Button profileBtn = createSmallButton("👤 Profile");
            Button logoutBtn = createSmallButton("🚪 Logout");

            profileBtn.setOnAction(e -> showProfile());
            logoutBtn.setOnAction(e -> {
                authManager.logout();
                showLoginScreen();
            });

            bottomButtons.getChildren().addAll(profileBtn, logoutBtn);
        } else {
            Button loginBtn = createSmallButton("🔐 Login");
            loginBtn.setOnAction(e -> showLoginScreen());
            bottomButtons.getChildren().add(loginBtn);

            // Show guest warning
            Label guestWarning = new Label("⚠️ Playing as Guest - Scores not saved");
            guestWarning.setFont(Font.font("System", FontWeight.BOLD, 14));
            guestWarning.setTextFill(Color.web("#FF6B6B"));
            root.getChildren().add(guestWarning);
        }

        root.getChildren().addAll(title, userStatusLabel, buttonBox, bottomButtons);

        Scene scene = new Scene(root, 600, 750);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Game Hub - Main Menu");
    }

    private void showProfile() {
        if (!authManager.isLoggedIn()) return;

        User user = authManager.getCurrentUser();
        UserStats stats = authManager.getUserStats(user.getUserId());

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("User Profile");
        alert.setHeaderText("👤 " + user.getUsername());

        StringBuilder content = new StringBuilder();
        content.append("User ID: ").append(user.getUserId()).append("\n");
        content.append("Email: ").append(user.getEmail() != null ? user.getEmail() : "Not set").append("\n");
        content.append("Member Since: ").append(user.getCreatedAt()).append("\n\n");

        if (stats != null) {
            content.append("📊 STATISTICS\n");
            content.append("─────────────────\n");
            content.append("Total Games: ").append(stats.getTotalGames()).append("\n");
            content.append("Total Score: ").append(stats.getTotalScore()).append("\n");
            content.append("Total Wins: ").append(stats.getTotalWins()).append("\n");
            content.append(String.format("Win Rate: %.1f%%\n", stats.getWinRate()));
            content.append("Games Played: ").append(stats.getGamesPlayed()).append("\n");
        }

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    private void updateUserStatus() {
        if (authManager.isLoggedIn()) {
            userStatusLabel.setText("Welcome, " + authManager.getDisplayName() + "! 🎯");
        } else {
            userStatusLabel.setText("Playing as Guest 👤");
        }
    }

    private Button createStyledButton(String text, String subtitle) {
        VBox btnContent = new VBox(5);
        btnContent.setAlignment(Pos.CENTER);

        Label mainText = new Label(text);
        mainText.setFont(Font.font("System", FontWeight.BOLD, 20));
        mainText.setTextFill(Color.web("#667eea"));

        Label subText = new Label(subtitle);
        subText.setFont(Font.font("System", 13));
        subText.setTextFill(Color.web("#999999"));

        btnContent.getChildren().addAll(mainText, subText);

        Button btn = new Button();
        btn.setGraphic(btnContent);
        btn.setPrefWidth(400);
        btn.setPrefHeight(90);
        btn.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 5);");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #f0f0ff; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 8);"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 5);"));

        return btn;
    }

    private Button createSmallButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: white; -fx-text-fill: #667eea; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #f0f0ff; -fx-text-fill: #667eea; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: white; -fx-text-fill: #667eea; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;"));
        return btn;
    }

    private void styleActionButton(Button btn, String color) {
        btn.setPrefWidth(120);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-opacity: 0.8;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-opacity: 1.0;"));
    }

    private void startTicTacToe() {
        TicTacToe ttt = new TicTacToe();
        ttt.start(new Stage());
    }

    private void startMemoryGame() {
        MemoryGame mmm = new MemoryGame();
        mmm.start(new Stage());
    }

    private void startPacman() {
        PacmanGame pacman = new PacmanGame();
        pacman.start(new Stage());
    }

    @Override
    public void stop() {
        if (authManager != null) {
            authManager.closeConnection();
        }
        if (dbManager != null) {
            dbManager.closeConnection();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}