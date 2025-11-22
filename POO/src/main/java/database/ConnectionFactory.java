package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {

    // Configurações do banco de dados
    private static final String DB_URL = "jdbc:mysql://localhost:3306/login_db?serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "993549889";

    // Instância única da conexão
    private static Connection conexao = null;

    // Método interno que cria a conexão
    private static Connection getInstance() throws SQLException {
        if (conexao == null || conexao.isClosed()) {
            conexao = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("✅ Conectado ao banco de dados com sucesso!");
        }
        return conexao;
    }

    // Método público para obter a conexão
    public static Connection getConnection() throws SQLException {
        return getInstance();
    }

    // Método opcional para fechar a conexão (boa prática)
    public static void closeConnection() {
        if (conexao != null) {
            try {
                conexao.close();
                System.out.println("🔒 Conexão com o banco encerrada.");
            } catch (SQLException e) {
                System.err.println("Erro ao fechar a conexão: " + e.getMessage());
            }
        }
    }
}
