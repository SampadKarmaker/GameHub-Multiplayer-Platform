@echo off
echo Starting Game Servers...

start "TicTacToe Server" cmd /k java -cp bin GameProject.TicTacToeServer
timeout /t 2

start "MemoryGame Server" cmd /k java -cp bin GameProject.MemoryGameServer
timeout /t 2

start "Pacman Server" cmd /k java -cp bin GameProject.PacmanServer

echo All servers started!
pause