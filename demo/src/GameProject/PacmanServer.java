package GameProject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PacmanServer {
    private static final int PORT = 5558;
    private static List<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();
    private static List<GameRoom> activeGames = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("👻 Pacman Server Starting...");
        System.out.println("📡 Listening on port: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ New client connected: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;
        private GameRoom gameRoom;
        private int playerNumber; // 1 or 2

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Get player name
                playerName = in.readLine();
                System.out.println("👤 Player joined: " + playerName);

                // Add to waiting list
                waitingPlayers.add(this);

                // Try to match with another player
                matchPlayers();

                // Listen for messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (gameRoom != null) {
                        gameRoom.handleMessage(this, message);
                    }
                }
            } catch (IOException e) {
                System.out.println("❌ Client disconnected: " + playerName);
            } finally {
                cleanup();
            }
        }

        private void matchPlayers() {
            if (waitingPlayers.size() >= 2) {
                ClientHandler player1 = waitingPlayers.remove(0);
                ClientHandler player2 = waitingPlayers.remove(0);

                GameRoom room = new GameRoom(player1, player2);
                activeGames.add(room);
                room.start();
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
                out.flush();
            }
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getPlayerNumber() {
            return playerNumber;
        }

        public void setPlayerNumber(int number) {
            this.playerNumber = number;
        }

        private void cleanup() {
            try {
                waitingPlayers.remove(this);
                if (gameRoom != null) {
                    gameRoom.playerDisconnected(this);
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class GameRoom {
        private ClientHandler player1;
        private ClientHandler player2;
        private Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();
        private Set<String> collectedDots = ConcurrentHashMap.newKeySet();

        // Game state
        private int score1 = 0;
        private int score2 = 0;
        private boolean gameActive = true;

        public GameRoom(ClientHandler p1, ClientHandler p2) {
            this.player1 = p1;
            this.player2 = p2;
            p1.gameRoom = this;
            p2.gameRoom = this;
            p1.setPlayerNumber(1);
            p2.setPlayerNumber(2);

            // Initialize player states
            playerStates.put(p1.getPlayerName(), new PlayerState(1, 1, 0));
            playerStates.put(p2.getPlayerName(), new PlayerState(22, 18, 2));
        }

        public void start() {
            System.out.println("🎮 Pacman Game starting:");
            System.out.println("   Player 1: " + player1.getPlayerName());
            System.out.println("   Player 2: " + player2.getPlayerName());

            // Send start message with player assignments
            player1.sendMessage("START:1:" + player2.getPlayerName());
            player2.sendMessage("START:2:" + player1.getPlayerName());
        }

        public void handleMessage(ClientHandler sender, String message) {
            String[] parts = message.split(":");
            String command = parts[0];

            System.out.println("📥 Message from " + sender.getPlayerName() + ": " + message);

            switch (command) {
                case "MOVE":
                    handleMove(sender, parts);
                    break;
                case "DOT_COLLECTED":
                    handleDotCollected(sender, parts);
                    break;
                case "GHOST_HIT":
                    handleGhostHit(sender, parts);
                    break;
                case "GAME_OVER":
                    handleGameOver(sender, parts);
                    break;
            }
        }

        private void handleMove(ClientHandler sender, String[] parts) {
            // MOVE:x:y:direction
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int direction = Integer.parseInt(parts[3]);

            PlayerState state = playerStates.get(sender.getPlayerName());
            if (state != null) {
                state.x = x;
                state.y = y;
                state.direction = direction;
            }

            // Broadcast to opponent
            ClientHandler opponent = (sender == player1) ? player2 : player1;
            opponent.sendMessage("OPPONENT_MOVE:" + x + ":" + y + ":" + direction);
        }

        private void handleDotCollected(ClientHandler sender, String[] parts) {
            // DOT_COLLECTED:x:y
            String dotKey = parts[1] + "," + parts[2];

            if (collectedDots.add(dotKey)) {
                // First time this dot is collected
                if (sender == player1) {
                    score1 += 10;
                } else {
                    score2 += 10;
                }

                // Notify both players
                player1.sendMessage("DOT_REMOVED:" + parts[1] + ":" + parts[2]);
                player2.sendMessage("DOT_REMOVED:" + parts[1] + ":" + parts[2]);

                // Update scores
                player1.sendMessage("SCORE_UPDATE:" + score1 + ":" + score2);
                player2.sendMessage("SCORE_UPDATE:" + score1 + ":" + score2);

                System.out.println("📊 Scores - " + player1.getPlayerName() + ": " + score1 +
                        " | " + player2.getPlayerName() + ": " + score2);
            }
        }

        private void handleGhostHit(ClientHandler sender, String[] parts) {
            // GHOST_HIT - player hit by ghost
            ClientHandler opponent = (sender == player1) ? player2 : player1;
            opponent.sendMessage("OPPONENT_HIT:" + sender.getPlayerName());
        }

        private void handleGameOver(ClientHandler sender, String[] parts) {
            // GAME_OVER:reason (all_dots or no_lives)
            String reason = parts[1];

            String winner;
            if (reason.equals("all_dots")) {
                winner = score1 > score2 ? player1.getPlayerName() :
                        score2 > score1 ? player2.getPlayerName() : "TIE";
            } else {
                winner = sender == player1 ? player2.getPlayerName() : player1.getPlayerName();
            }

            String finalMessage = "GAME_END:" + winner + ":" + score1 + ":" + score2;
            player1.sendMessage(finalMessage);
            player2.sendMessage(finalMessage);

            System.out.println("🏆 Game ended. Winner: " + winner);
            activeGames.remove(this);
        }

        public void playerDisconnected(ClientHandler player) {
            ClientHandler other = (player == player1) ? player2 : player1;
            if (other != null) {
                other.sendMessage("OPPONENT_DISCONNECTED");
            }
            activeGames.remove(this);
            System.out.println("⚠️ Player " + player.getPlayerName() + " disconnected from game");
        }

        // Player state class
        static class PlayerState {
            int x, y, direction;

            PlayerState(int x, int y, int direction) {
                this.x = x;
                this.y = y;
                this.direction = direction;
            }
        }
    }
}