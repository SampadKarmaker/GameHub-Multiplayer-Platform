package GameProject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MemoryGameServer {
    private static final int PORT = 5555;
    private static List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static List<GameRoom> gameRooms = new CopyOnWriteArrayList<>();
    private static Queue<ClientHandler> waitingPlayers = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) {
        System.out.println("Memory Game Server Starting...");
        System.out.println("Listening on port: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;
        private GameRoom gameRoom;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Wait for player name
                playerName = in.readLine();
                System.out.println("Player joined: " + playerName);

                waitingPlayers.add(this);
                out.println("WAITING");

                matchPlayers();

                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + playerName);
            } finally {
                cleanup();
            }
        }

        private synchronized void matchPlayers() {
            if (waitingPlayers.size() >= 2) {
                ClientHandler player1 = waitingPlayers.poll();
                ClientHandler player2 = waitingPlayers.poll();

                if (player1 != null && player2 != null) {
                    GameRoom room = new GameRoom(player1, player2);
                    gameRooms.add(room);
                    room.start();
                }
            }
        }

        private void handleMessage(String message) {
            if (gameRoom != null && gameRoom.isActive()) {
                try {
                    String[] parts = message.split(":", -1);
                    String command = parts[0];

                    switch (command) {
                        case "CARD_CLICK":
                            int index = Integer.parseInt(parts[1]);
                            String value = parts[2];
                            gameRoom.handleCardClick(this, index, value);
                            break;
                        case "CHAT":
                            if (parts.length > 1) {
                                String chatMessage = parts[1];
                                gameRoom.broadcastChat(playerName + ": " + chatMessage);
                            }
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Error handling message: " + message + " - " + e.getMessage());
                }
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

        private void cleanup() {
            try {
                waitingPlayers.remove(this);
                clients.remove(this);
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
        private List<String> cardDeck;
        private int player1Score = 0;
        private int player2Score = 0;
        private Set<Integer> matchedIndices = new HashSet<>();
        private boolean gameActive = true;
        private long gameStartTime;

        private int player1FirstFlip = -1;
        private int player1SecondFlip = -1;
        private String player1FirstValue = "";
        private String player1SecondValue = "";

        private int player2FirstFlip = -1;
        private int player2SecondFlip = -1;
        private String player2FirstValue = "";
        private String player2SecondValue = "";

        private boolean player1Checking = false;
        private boolean player2Checking = false;

        public GameRoom(ClientHandler p1, ClientHandler p2) {
            this.player1 = p1;
            this.player2 = p2;
            p1.gameRoom = this;
            p2.gameRoom = this;
            initializeGame();
        }

        private void initializeGame() {
            String[] cardList = {"darkness", "double", "fairy", "fighting", "fire",
                    "grass", "lightning", "metal", "psychic", "water"};

            cardDeck = new ArrayList<>();
            for (String c : cardList) {
                cardDeck.add(c);
                cardDeck.add(c);
            }

            Collections.shuffle(cardDeck);
        }

        public void start() {
            gameStartTime = System.currentTimeMillis();
            System.out.println("Race starting: " + player1.getPlayerName() + " vs " + player2.getPlayerName());

            // Send game start with opponent name and card layout
            String cardLayout = String.join(",", cardDeck);
            player1.sendMessage("GAME_START:" + player2.getPlayerName() + ":" + cardLayout);
            player2.sendMessage("GAME_START:" + player1.getPlayerName() + ":" + cardLayout);

            // Send initial score
            broadcastScore();
        }

        public void handleCardClick(ClientHandler player, int index, String value) {
            if (!gameActive || matchedIndices.contains(index)) {
                return;
            }

            boolean isPlayer1 = (player == player1);
            handlePlayerCardClick(index, value, isPlayer1);
        }

        private synchronized void handlePlayerCardClick(int index, String value, boolean isPlayer1) {
            if (isPlayer1) {
                if (player1Checking) {
                    return;
                }

                if (player1FirstFlip == -1) {
                    player1FirstFlip = index;
                    player1FirstValue = value;
                    player1.sendMessage("CARD_FLIPPED:" + index + ":" + value);
                    player2.sendMessage("OPPONENT_CARD_FLIPPED:" + index + ":" + value);
                } else if (player1SecondFlip == -1 && index != player1FirstFlip) {
                    player1SecondFlip = index;
                    player1SecondValue = value;
                    player1.sendMessage("CARD_FLIPPED:" + index + ":" + value);
                    player2.sendMessage("OPPONENT_CARD_FLIPPED:" + index + ":" + value);

                    player1Checking = true;
                    new Thread(() -> checkMatchDelayed(true)).start();
                }
            } else {
                if (player2Checking) {
                    return;
                }

                if (player2FirstFlip == -1) {
                    player2FirstFlip = index;
                    player2FirstValue = value;
                    player2.sendMessage("CARD_FLIPPED:" + index + ":" + value);
                    player1.sendMessage("OPPONENT_CARD_FLIPPED:" + index + ":" + value);
                } else if (player2SecondFlip == -1 && index != player2FirstFlip) {
                    player2SecondFlip = index;
                    player2SecondValue = value;
                    player2.sendMessage("CARD_FLIPPED:" + index + ":" + value);
                    player1.sendMessage("OPPONENT_CARD_FLIPPED:" + index + ":" + value);

                    player2Checking = true;
                    new Thread(() -> checkMatchDelayed(false)).start();
                }
            }
        }

        private void checkMatchDelayed(boolean isPlayer1) {
            try {
                Thread.sleep(1000);

                if (isPlayer1) {
                    if (player1FirstValue.equals(player1SecondValue)) {
                        player1Score++;
                        matchedIndices.add(player1FirstFlip);
                        matchedIndices.add(player1SecondFlip);

                        player1.sendMessage("MATCH_SUCCESS:" + player1FirstFlip + ":" + player1SecondFlip);
                        player2.sendMessage("OPPONENT_MATCH:" + player1FirstFlip + ":" + player1SecondFlip);

                        broadcastScore();

                        if (matchedIndices.size() == 20) {
                            endGame();
                        }
                    } else {
                        player1.sendMessage("NO_MATCH:" + player1FirstFlip + ":" + player1SecondFlip);
                        player2.sendMessage("OPPONENT_NO_MATCH:" + player1FirstFlip + ":" + player1SecondFlip);
                    }

                    player1FirstFlip = -1;
                    player1SecondFlip = -1;
                    player1FirstValue = "";
                    player1SecondValue = "";
                    player1Checking = false;
                } else {
                    if (player2FirstValue.equals(player2SecondValue)) {
                        player2Score++;
                        matchedIndices.add(player2FirstFlip);
                        matchedIndices.add(player2SecondFlip);

                        player2.sendMessage("MATCH_SUCCESS:" + player2FirstFlip + ":" + player2SecondFlip);
                        player1.sendMessage("OPPONENT_MATCH:" + player2FirstFlip + ":" + player2SecondFlip);

                        broadcastScore();

                        if (matchedIndices.size() == 20) {
                            endGame();
                        }
                    } else {
                        player2.sendMessage("NO_MATCH:" + player2FirstFlip + ":" + player2SecondFlip);
                        player1.sendMessage("OPPONENT_NO_MATCH:" + player2FirstFlip + ":" + player2SecondFlip);
                    }

                    player2FirstFlip = -1;
                    player2SecondFlip = -1;
                    player2FirstValue = "";
                    player2SecondValue = "";
                    player2Checking = false;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void broadcastScore() {
            // Send score to each player with THEIR score first, opponent's score second
            player1.sendMessage("SCORE:" + player1Score + ":" + player2Score);
            player2.sendMessage("SCORE:" + player2Score + ":" + player1Score);
        }

        private synchronized void endGame() {
            if (!gameActive) {
                return;
            }

            gameActive = false;
            long gameDuration = (System.currentTimeMillis() - gameStartTime) / 1000;

            if (player1Score > player2Score) {
                player1.sendMessage("GAME_END:WIN:" + player1Score + ":" + player2Score + ":" + gameDuration);
                player2.sendMessage("GAME_END:LOSE:" + player2Score + ":" + player1Score + ":" + gameDuration);
            } else if (player2Score > player1Score) {
                player1.sendMessage("GAME_END:LOSE:" + player1Score + ":" + player2Score + ":" + gameDuration);
                player2.sendMessage("GAME_END:WIN:" + player2Score + ":" + player1Score + ":" + gameDuration);
            } else {
                player1.sendMessage("GAME_END:TIE:" + player1Score + ":" + player2Score + ":" + gameDuration);
                player2.sendMessage("GAME_END:TIE:" + player2Score + ":" + player1Score + ":" + gameDuration);
            }

            System.out.println("Game finished in " + gameDuration + "s");
            gameRooms.remove(this);
        }

        public void broadcastChat(String message) {
            player1.sendMessage("CHAT:" + message);
            player2.sendMessage("CHAT:" + message);
        }

        public void playerDisconnected(ClientHandler player) {
            gameActive = false;
            if (player == player1) {
                player2.sendMessage("OPPONENT_DISCONNECTED");
            } else {
                player1.sendMessage("OPPONENT_DISCONNECTED");
            }
            gameRooms.remove(this);
        }

        public boolean isActive() {
            return gameActive;
        }
    }
}