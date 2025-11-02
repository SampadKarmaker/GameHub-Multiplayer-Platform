package GameProject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TicTacToeServer {
    private static final int PORT = 5557;
    private static List<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();
    private static List<GameRoom> activeGames = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("🎮 Tic-Tac-Toe Server Starting...");
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
        private String mySymbol; // This player's assigned symbol

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

        public String getMySymbol() {
            return mySymbol;
        }

        public void setMySymbol(String symbol) {
            this.mySymbol = symbol;
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
        private ClientHandler player1; // Always X
        private ClientHandler player2; // Always O
        private String[][] board = new String[3][3];
        private ClientHandler currentTurnPlayer; // Track which player's turn it is

        public GameRoom(ClientHandler p1, ClientHandler p2) {
            this.player1 = p1;
            this.player2 = p2;
            p1.gameRoom = this;
            p2.gameRoom = this;

            // Assign symbols
            p1.setMySymbol("X");
            p2.setMySymbol("O");

            // X (player1) always goes first
            currentTurnPlayer = player1;

            // Initialize board
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    board[i][j] = "";
                }
            }
        }

        public void start() {
            System.out.println("🎮 Game starting:");
            System.out.println("   Player 1: " + player1.getPlayerName() + " (X)");
            System.out.println("   Player 2: " + player2.getPlayerName() + " (O)");
            System.out.println("   First turn: " + player1.getPlayerName());

            player1.sendMessage("SYMBOL:X:" + player2.getPlayerName());
            player2.sendMessage("SYMBOL:O:" + player1.getPlayerName());
        }

        public void handleMessage(ClientHandler sender, String message) {
            String[] parts = message.split(":");
            String command = parts[0];

            if (command.equals("MOVE")) {
                int row = Integer.parseInt(parts[1]);
                int col = Integer.parseInt(parts[2]);
                String symbol = parts[3];

                System.out.println("📥 Received move from " + sender.getPlayerName() + ": " + symbol + " at [" + row + "," + col + "]");

                // Check if it's this player's turn
                if (sender != currentTurnPlayer) {
                    System.out.println("   ❌ REJECTED: Not " + sender.getPlayerName() + "'s turn (current: " + currentTurnPlayer.getPlayerName() + ")");
                    sender.sendMessage("ERROR:Not your turn");
                    return;
                }

                // Validate symbol matches player's assigned symbol
                if (!symbol.equals(sender.getMySymbol())) {
                    System.out.println("   ❌ REJECTED: Wrong symbol. " + sender.getPlayerName() + " should use " + sender.getMySymbol() + " but sent " + symbol);
                    sender.sendMessage("ERROR:Wrong symbol");
                    return;
                }

                // Validate cell is empty
                if (!board[row][col].isEmpty()) {
                    System.out.println("   ❌ REJECTED: Cell [" + row + "," + col + "] already occupied");
                    sender.sendMessage("ERROR:Cell occupied");
                    return;
                }

                // Move is valid - make it
                board[row][col] = symbol;
                System.out.println("   ✅ Move accepted");

                // Switch turns
                currentTurnPlayer = (currentTurnPlayer == player1) ? player2 : player1;
                System.out.println("   ➡️ Next turn: " + currentTurnPlayer.getPlayerName() + " (" + currentTurnPlayer.getMySymbol() + ")");

                // Notify opponent
                ClientHandler opponent = (sender == player1) ? player2 : player1;
                opponent.sendMessage("MOVE:" + row + ":" + col + ":" + symbol);

                // Check for winner
                if (checkWinner(symbol)) {
                    System.out.println("🏆 Winner: " + sender.getPlayerName() + " (" + symbol + ")");
                    player1.sendMessage("WIN:" + sender.getPlayerName());
                    player2.sendMessage("WIN:" + sender.getPlayerName());
                    activeGames.remove(this);
                } else if (isBoardFull()) {
                    System.out.println("🤝 Game ended in a draw");
                    player1.sendMessage("DRAW");
                    player2.sendMessage("DRAW");
                    activeGames.remove(this);
                }
            }
        }

        private boolean checkWinner(String symbol) {
            // Check rows
            for (int i = 0; i < 3; i++) {
                if (board[i][0].equals(symbol) &&
                        board[i][1].equals(symbol) &&
                        board[i][2].equals(symbol)) {
                    return true;
                }
            }

            // Check columns
            for (int i = 0; i < 3; i++) {
                if (board[0][i].equals(symbol) &&
                        board[1][i].equals(symbol) &&
                        board[2][i].equals(symbol)) {
                    return true;
                }
            }

            // Check diagonals
            if (board[0][0].equals(symbol) &&
                    board[1][1].equals(symbol) &&
                    board[2][2].equals(symbol)) {
                return true;
            }

            if (board[0][2].equals(symbol) &&
                    board[1][1].equals(symbol) &&
                    board[2][0].equals(symbol)) {
                return true;
            }

            return false;
        }

        private boolean isBoardFull() {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

        public void playerDisconnected(ClientHandler player) {
            ClientHandler other = (player == player1) ? player2 : player1;
            if (other != null) {
                other.sendMessage("OPPONENT_DISCONNECTED");
            }
            activeGames.remove(this);
            System.out.println("⚠️ Player " + player.getPlayerName() + " disconnected from game");
        }
    }
}