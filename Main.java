package com.kiet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    private static final String URL = setting("DB_URL", "jdbc:mysql://localhost:3306/ecommerce_db");
    private static final String USER = setting("DB_USER", "root");
    private static final String PASSWORD = setting("DB_PASSWORD", "root");
    private static final String DATABASE = "ecommerce_db";

    public static void main(String[] args) {
        try (Connection connection = connectToDatabase(); Scanner input = new Scanner(System.in)) {
            System.out.println("Connected to ecommerce_db.");
            insertStarterOrders(connection);
            runMenu(connection, input);
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            System.err.println("Check that MySQL is running and DB_URL, DB_USER, and DB_PASSWORD are correct.");
        }
    }

    private static Connection connectToDatabase() throws SQLException {
        int schemeEnd = URL.indexOf("://");
        if (schemeEnd < 0) {
            throw new SQLException("DB_URL must be a MySQL JDBC URL, for example jdbc:mysql://localhost:3306/ecommerce_db");
        }
        int pathStart = URL.indexOf('/', schemeEnd + 3);
        int optionsStart = URL.indexOf('?', schemeEnd + 3);
        int authorityEnd = pathStart < 0 ? URL.length() : pathStart;
        if (optionsStart >= 0 && optionsStart < authorityEnd) authorityEnd = optionsStart;
        String serverUrl = URL.substring(0, authorityEnd);
        String options = optionsStart < 0 ? "" : URL.substring(optionsStart);

        try (Connection server = DriverManager.getConnection(serverUrl + options, USER, PASSWORD);
             var statement = server.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DATABASE);
        }

        Connection connection = DriverManager.getConnection(serverUrl + "/" + DATABASE + options, USER, PASSWORD);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Orders (" +
                    "order_id INT PRIMARY KEY, " +
                    "customer_name VARCHAR(100) NOT NULL, " +
                    "product_name VARCHAR(100) NOT NULL, " +
                    "quantity INT NOT NULL, " +
                    "unit_price DOUBLE NOT NULL, " +
                    "status VARCHAR(30) NOT NULL)");
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void insertStarterOrders(Connection connection) throws SQLException {
        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE order_id = order_id";
        Object[][] orders = {
                {1, "Asha Patel", "Wireless Mouse", 2, 24.99, "Pending"},
                {2, "Ravi Kumar", "Keyboard", 1, 59.50, "Shipped"},
                {3, "Mina Shah", "USB-C Hub", 3, 32.00, "Cancelled"}
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] order : orders) {
                statement.setInt(1, (int) order[0]);
                statement.setString(2, (String) order[1]);
                statement.setString(3, (String) order[2]);
                statement.setInt(4, (int) order[3]);
                statement.setDouble(5, (double) order[4]);
                statement.setString(6, (String) order[5]);
                statement.executeUpdate();
            }
        }
    }

    private static void runMenu(Connection connection, Scanner input) {
        while (true) {
            System.out.println("\nOrder Management\n1. Add an order\n2. List matching orders\n" +
                    "3. Increase order quantity\n4. Update order status\n5. Show order value and delete cancelled order\n0. Exit");
            int choice = readInt(input, "Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> addOrder(connection, input);
                    case 2 -> listMatchingOrders(connection, input);
                    case 3 -> increaseQuantity(connection, input);
                    case 4 -> updateStatus(connection, input);
                    case 5 -> showValueAndDeleteCancelled(connection, input);
                    case 0 -> { System.out.println("Goodbye."); return; }
                    default -> System.out.println("Choose an option from 0 to 5.");
                }
            } catch (SQLException e) {
                System.out.println("Operation failed: " + e.getMessage());
            }
        }
    }

    private static void addOrder(Connection connection, Scanner input) throws SQLException {
        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, readInt(input, "Order ID: "));
            statement.setString(2, readText(input, "Customer name: "));
            statement.setString(3, readText(input, "Product name: "));
            statement.setInt(4, readPositiveInt(input, "Quantity: "));
            statement.setDouble(5, readPositiveDouble(input, "Unit price: "));
            statement.setString(6, readText(input, "Status (Pending, Shipped, Cancelled): "));
            statement.executeUpdate();
            System.out.println("Order inserted.");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) System.out.println("That order ID already exists.");
            else throw e;
        }
    }

    private static void listMatchingOrders(Connection connection, Scanner input) throws SQLException {
        String status = readText(input, "Status to search for: ");
        int minimumQuantity = readInt(input, "Minimum quantity (greater than): ");
        String sql = "SELECT order_id, customer_name, product_name, quantity, unit_price, status FROM Orders " +
                "WHERE status = ? AND quantity > ? ORDER BY unit_price DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, minimumQuantity);
            try (ResultSet rows = statement.executeQuery()) {
                printOrders(rows);
            }
        }
    }

    private static void increaseQuantity(Connection connection, Scanner input) throws SQLException {
        int id = readInt(input, "Order ID: ");
        int amount = readPositiveInt(input, "Quantity to add: ");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?")) {
            statement.setInt(1, amount);
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) System.out.println("No order exists with that ID.");
            else System.out.println("Order quantity updated.");
        }
    }

    private static void updateStatus(Connection connection, Scanner input) throws SQLException {
        int id = readInt(input, "Order ID: ");
        String status = readText(input, "New status (Pending, Shipped, Cancelled): ");
        if (!status.equalsIgnoreCase("Pending") && !status.equalsIgnoreCase("Shipped") &&
                !status.equalsIgnoreCase("Cancelled")) {
            System.out.println("Invalid status. Use Pending, Shipped, or Cancelled.");
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE Orders SET status = ? WHERE order_id = ?")) {
            statement.setString(1, normalizeStatus(status));
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) System.out.println("No order exists with that ID.");
            else {
                System.out.println("Updated order:");
                showOrder(connection, id);
            }
        }
    }

    private static String normalizeStatus(String status) {
        return switch (status.toLowerCase()) {
            case "pending" -> "Pending";
            case "shipped" -> "Shipped";
            default -> "Cancelled";
        };
    }

    private static void showValueAndDeleteCancelled(Connection connection, Scanner input) throws SQLException {
        int id = readInt(input, "Order ID: ");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id, customer_name, product_name, quantity, unit_price, status, " +
                        "(quantity * unit_price) AS total_value FROM Orders WHERE order_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    System.out.println("No order exists with that ID.");
                    return;
                }
                printOrder(row);
                System.out.printf("Total order value: %.2f%n", row.getDouble("total_value"));
                if (row.getString("status").equalsIgnoreCase("Cancelled")) {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM Orders WHERE order_id = ? AND status = ?")) {
                        delete.setInt(1, id);
                        delete.setString(2, "Cancelled");
                        if (delete.executeUpdate() > 0) System.out.println("Cancelled order deleted.");
                    }
                } else {
                    System.out.println("Order was not deleted because its status is not Cancelled.");
                }
            }
        }
    }

    private static void showOrder(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id, customer_name, product_name, quantity, unit_price, status FROM Orders WHERE order_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) { printOrders(rows); }
        }
    }

    private static void printOrders(ResultSet rows) throws SQLException {
        boolean found = false;
        while (rows.next()) { printOrder(rows); found = true; }
        if (!found) System.out.println("No matching orders found.");
    }

    private static void printOrder(ResultSet row) throws SQLException {
        System.out.printf("ID: %d | Customer: %s | Product: %s | Quantity: %d | Unit price: %.2f | Status: %s%n",
                row.getInt("order_id"), row.getString("customer_name"), row.getString("product_name"),
                row.getInt("quantity"), row.getDouble("unit_price"), row.getString("status"));
    }

    private static int readInt(Scanner input, String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(input.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Enter a whole number."); }
        }
    }

    private static int readPositiveInt(Scanner input, String prompt) {
        while (true) {
            int value = readInt(input, prompt);
            if (value > 0) return value;
            System.out.println("Enter a number greater than zero.");
        }
    }

    private static double readPositiveDouble(Scanner input, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(input.nextLine().trim());
                if (Double.isFinite(value) && value > 0) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a valid price greater than zero.");
        }
    }

    private static String readText(Scanner input, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This value cannot be empty.");
        }
    }
}
