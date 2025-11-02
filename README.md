# 🎮 Game Hub – Multiplayer Gaming Platform

> **A full-stack Java-based multiplayer game platform** demonstrating networking, concurrency, and database integration through classic games like **Tic-Tac-Toe**, **Memory Game**, and **Pacman**.

---

## 📘 Overview

**Game Hub** is a comprehensive multiplayer gaming platform built using **Java**, **JavaFX**, and **MySQL**.  
It brings together multiple games under a single user-friendly interface, showcasing **socket programming**, **multithreading**, and **database management** concepts.

Each game supports:
- 🧠 Solo play  
- 👥 Local multiplayer  
- 🌐 Online multiplayer (real-time via client-server communication)

---

## 🚀 Features

### 🧩 User Authentication
- Secure **login/registration** with **SHA-256 password hashing**
- **Guest mode** (play without saving scores)
- User profile with **game statistics**, total wins, and win rate

### 🎲 Game Collection

#### **1. Tic-Tac-Toe**
- Modes: Solo (AI), Local 2-player, Online multiplayer  
- AI implemented using the **Minimax algorithm**  
- Dynamic leaderboard and responsive UI

#### **2. Memory Game**
- Card-matching puzzle with randomization via **Fisher-Yates Shuffle**  
- Solo, Local, and Online play supported  
- Real-time sync for multiplayer card flips and matches

#### **3. Pacman**
- Classic maze game with **ghost AI**  
- Solo, Vs Computer, and 2-player local modes  
- Live scoring and win tracking

---

## ⚙️ Implementation Details

### 🧵 **Multithreading**
- Each client and game session runs on a separate thread  
- Thread-safe operations to avoid data races  
- Server manages multiple connections using a thread pool

### 🌐 **Socket Programming**
- Built on **TCP Sockets** for reliable real-time data exchange  
- Dedicated ports per game:
  - Tic-Tac-Toe → `5557`
  - Memory Game → `5558`
  - Pacman → `5559`
- Real-time synchronization of moves, turns, and states between players

### 🗄️ **Database Management**
- **MySQL + JDBC** backend  
- Secure queries with **Prepared Statements**  
- Separate tables for users, game scores, and leaderboards  
- Automatically updated global statistics  

---

## 🧠 Key Algorithms

| Algorithm | Purpose |
|------------|----------|
| **Minimax** | AI for Tic-Tac-Toe |
| **Fisher-Yates Shuffle** | Randomizes Memory Game cards |
| **Ghost Pathfinding** | Controls ghost AI in Pacman |
| **SHA-256 Hashing** | Encrypts user passwords securely |

---

## 🧰 Technologies Used

| Category | Tools / Frameworks |
|-----------|--------------------|
| **Languages** | Java, SQL |
| **Frameworks** | JavaFX, JDBC, Java Socket API |
| **Database** | MySQL |
| **Tools** | IntelliJ IDEA, XAMPP, phpMyAdmin, Git |
| **Design Patterns** | MVC, Singleton, Observer, Factory, Strategy |

---

## 🏗️ Architecture Overview

**Three-tier Architecture:**
1. **Presentation Layer:** JavaFX GUI  
2. **Logic Layer:** Game logic, AI, and networking  
3. **Data Layer:** MySQL database via JDBC  

**Concurrency:**  
- Separate threads for clients and game sessions  
- Thread synchronization for consistent state  

**Security:**  
- SHA-256 password encryption  
- SQL injection prevention  
- Input validation  

---

## 🧾 Installation Guide

### 🔧 Prerequisites
- Java 11 or higher  
- MySQL & XAMPP installed  
- IntelliJ IDEA (or any Java IDE)

### ⚙️ Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/SampadKarmaker/GameHub-Multiplayer-Platform.git
   cd GameHub-Multiplayer-Platform
   ```
2. Set up the MySQL database:
   - Import the provided `.sql` file (if available).
   - Configure your DB credentials in the project.
3. Run the **server** file (Java main class).
4. Run the **client** files (on same or different PCs).
5. Enjoy playing!

---

## 🖼️ Screenshots

Screenshots include:
- Login Page  
- Main Menu  
- Game Mode Selections  
- Tic-Tac-Toe (Solo, Multiplayer, Leaderboard)  
- Memory Game (Card Matching, Multiplayer)  
- Pacman (AI and Multiplayer modes)



## 🧑‍💻 Team IOS 3.0

| Name | ID |
|------|----|
| **Sampad Karmaker** | 0112230534 |
| **Dipto Ghosh Hridoy** | 0112230532 |
| **Moloy Halder** | 0112230533 |
| **Mashrat Fardin** | 11213041 |

---

## 🏁 Conclusion

**Game Hub** combines entertainment with education — a platform that demonstrates how advanced OOP concepts like **multithreading**, **networking**, and **database management** can power a secure, responsive, and interactive system.  

This project reflects the team’s collaboration, creativity, and technical expertise in **Java full-stack development**.
