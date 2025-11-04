package model;

import config.AppConfig;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe responsável por gerenciar a conexão com o banco de dados
 * e executar scripts SQL em um banco MySQL.
 */
public class DatabaseManager {

    private AppConfig appConfig;
    private Connection connection;
    private String currentDatabase;

    public DatabaseManager(AppConfig appConfig) {
        this.appConfig = appConfig;
        loadJdbcDriver();
    }

    /**
     * Carrega o driver JDBC do MySQL.
     */
    private void loadJdbcDriver() {
        try {
            Class.forName("com.mysql.jdbc.Driver"); 
            System.out.println("Driver JDBC carregado com sucesso.");
        } catch (ClassNotFoundException e) {
            System.err.println("Erro ao carregar o driver JDBC. Verifique se o mysql-connector está no classpath.");
            throw new RuntimeException(e);
        }
    }

    /**
     * Estabelece a conexão com o banco de dados.
     */
    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) return;

        connection = DriverManager.getConnection(
                appConfig.getDbUrl(),
                appConfig.getDbUser(),
                appConfig.getDbPassword()
        );
        System.out.println("Conectado ao servidor MySQL com sucesso.");
    }

    /**
     * Desconecta do banco de dados.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Conexão encerrada.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Seleciona o banco de dados alvo.
     */
    public void selectDatabase(String dbName) throws SQLException {
        if (connection == null || connection.isClosed()) connect();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE `" + dbName + "`");
            currentDatabase = dbName;
            System.out.println("Banco selecionado: " + dbName);
        }
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    /**
     * Lista os bancos de dados disponíveis (excluindo os padrões do sistema).
     */
    public List<String> getAllDatabases() throws SQLException {
        List<String> databases = new ArrayList<>();
        connect();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String db = rs.getString(1);
                if (!db.equalsIgnoreCase("information_schema") &&
                        !db.equalsIgnoreCase("mysql") &&
                        !db.equalsIgnoreCase("performance_schema") &&
                        !db.equalsIgnoreCase("sys")) {
                    databases.add(db);
                }
            }
        }
        return databases;
    }

    /**
     * Executa o script SQL a partir do arquivo.
     */
    public void executeSqlScript(File sqlFile, String dbName) throws SQLException, IOException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Conexão não ativa.");
        }

        selectDatabase(dbName); // Garante banco selecionado

        connection.setAutoCommit(false);
        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile));
             Statement stmt = connection.createStatement()) {

            StringBuilder sqlCommand = new StringBuilder();
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Ignorar comentários
                if (line.startsWith("--") || line.startsWith("#") || line.isEmpty()) continue;

                sqlCommand.append(line).append(" ");

                if (line.endsWith(";")) {
                    String command = sqlCommand.toString().trim();
                    if (command.endsWith(";")) command = command.substring(0, command.length() - 1);
                    if (!command.isEmpty()) {
                        System.out.println("Executando linha " + lineNumber + ": " + command);
                        stmt.execute(command);
                    }
                    sqlCommand.setLength(0); // Reset comando
                }
            }

            connection.commit();
            System.out.println("Script executado com sucesso e commit realizado.");
        } catch (SQLException | IOException e) {
            System.err.println("Erro ao executar script. Realizando rollback.");
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Lista as tabelas do banco de dados atual.
     */
    public void listTables(String dbName) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE `" + dbName + "`");
            ResultSet rs = stmt.executeQuery("SHOW TABLES");

            System.out.println("Tabelas no banco " + dbName + ":");
            while (rs.next()) {
                System.out.println(" - " + rs.getString(1));
            }

        } catch (SQLException e) {
            System.err.println("Erro ao listar tabelas: " + e.getMessage());
        }
    }
}
