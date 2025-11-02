package GameProject;

import java.sql.Connection;
import java.sql.DriverManager;

public class TestConnection {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL Driver loaded successfully!");

            Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/game_hub",
                    "root",
                    ""
            );
            System.out.println("Connected to database!");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}