package model;

import config.AppConfig;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private AppConfig appConfig;
    private Connection connection;
    private String currentDatabase; // Para manter o controle do DB selecionado

    public DatabaseManager(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.currentDatabase = null; // Nenhum DB selecionado inicialmente
        loadJdbcDriver();
    }

    private void loadJdbcDriver() {
        try {
            
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("Driver JDBC do MySQL (com.mysql.jdbc.Driver) carregado.");
        } catch (ClassNotFoundException e) {
            System.err.println("Erro: Driver JDBC do MySQL não encontrado. Certifique-se de que o JAR do MySQL Connector/J (versão compatível com MySQL 5.x) está no classpath.");
            e.printStackTrace();           
            throw new RuntimeException("Driver JDBC do MySQL não encontrado.", e);
        }
    }

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            System.out.println("Já conectado ao servidor MySQL.");
            return;
        }
        try {
            // appConfig.getDbUrl() deve fornecer a URL completa, por exemplo: "jdbc:mysql://localhost:3306/"
            connection = DriverManager.getConnection(
                    appConfig.getDbUrl(),
                    appConfig.getDbUser(),
                    appConfig.getDbPassword()
            );
            System.out.println("Conexão com o servidor MySQL estabelecida.");
        } catch (SQLException e) {
            System.err.println("Erro ao conectar ao servidor MySQL: " + e.getMessage());
            throw e; // Relança a exceção para que a UI possa lidar com ela
        }
    }

    // Método para desconectar do banco de dados
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Conexão com o banco de dados fechada.");
                this.currentDatabase = null; // Reseta o DB atual ao desconectar
            } catch (SQLException e) {
                System.err.println("Erro ao fechar a conexão com o banco de dados: " + e.getMessage());
            }
        }
    }

    // Método para obter a lista de todos os bancos de dados no servidor
    public List<String> getAllDatabases() throws SQLException {
        List<String> databases = new ArrayList<>();
        if (connection == null || connection.isClosed()) {
            connect(); // Garante que a conexão está aberta para listar DBs
        }

        String sql = "SHOW DATABASES";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String dbName = rs.getString(1);
                // Excluir bancos de dados padrão do sistema
                if (!dbName.equalsIgnoreCase("information_schema") &&
                    !dbName.equalsIgnoreCase("mysql") &&
                    !dbName.equalsIgnoreCase("performance_schema") &&
                    !dbName.equalsIgnoreCase("sys")) {
                    databases.add(dbName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar bancos de dados: " + e.getMessage());
            throw e;
        }
        return databases;
    }

    // Método para selecionar um banco de dados específico para operações futuras
    public void selectDatabase(String dbName) throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect(); // Garante que a conexão está aberta
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("USE `" + dbName + "`"); // Usar ` para nomes com caracteres especiais
            this.currentDatabase = dbName;
            System.out.println("Banco de dados '" + dbName + "' selecionado.");
        } catch (SQLException e) {
            System.err.println("Erro ao selecionar o banco de dados '" + dbName + "': " + e.getMessage());
            throw e;
        }
    }
    
    public String getCurrentDatabase() {
        return currentDatabase;
    }

    /**
     * tratando comentários e executando comandos linha a linha.
     * @param sqlFile O arquivo SQL a ser executado.
     * @param targetDatabaseName O nome do banco de dados onde o script será executado.
     * @throws SQLException Se ocorrer um erro durante a execução SQL.
     * @throws IOException Se ocorrer um erro ao ler o arquivo.
     */
    public void executeSqlScript(File sqlFile, String targetDatabaseName) throws SQLException, IOException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Conexão com o banco de dados não está ativa.");
        }
        if (targetDatabaseName == null || targetDatabaseName.isEmpty()) {
            throw new IllegalArgumentException("O nome do banco de dados de destino não pode ser nulo ou vazio.");
        }

        // Garante que estamos no banco de dados de destino antes de executar o script
        // Isso é crucial para que os DROP/CREATE/INSERT do script afetem o DB correto.
        try (Statement stmt = connection.createStatement()) {
            System.out.println("Definindo banco de dados de sessão para: " + targetDatabaseName);
            stmt.execute("USE `" + targetDatabaseName + "`");
            this.currentDatabase = targetDatabaseName; // Atualiza o DB atual
            connection.setAutoCommit(false); // Inicia uma transação para atomicidade

            StringBuilder sqlStatementBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();

                    // Ignora linhas vazias
                    if (line.isEmpty()) {
                        continue;
                    }

                    // Ignora comentários de linha única (--, #)
                    if (line.startsWith("--") || line.startsWith("#")) {
                        continue;
                    }

                    // Ignora comentários de bloco completos (/* ... */)
                    if (line.startsWith("/*") && line.endsWith("*/")) {
                        continue;
                    }
                    
                    // Tratamento de diretivas MySQL específicas (/*!...*/) que devem ser executadas
                    // ou passadas como parte de um comando. Para linhas completas, executamos diretamente.
                    if (line.startsWith("/*!") && line.endsWith("*/;")) {
                        try {
                            System.out.println("L" + lineNumber + ": Executando diretiva MySQL: " + line);
                            stmt.execute(line);
                        } catch (SQLException e) {
                            System.err.println("Erro na L" + lineNumber + " ao executar diretiva MySQL: " + line + " - " + e.getMessage());
                            throw new SQLException("Erro na linha " + lineNumber + ": " + e.getMessage(), e);
                        }
                        continue;
                    }

                    sqlStatementBuilder.append(line).append("\n"); // Adiciona a linha e um newline

                    // Se a linha termina com ';', assume que é o fim de um comando SQL
                    if (line.endsWith(";")) {
                        String sql = sqlStatementBuilder.toString().trim();
                        // Remove o último ponto e vírgula, pois Statement.execute espera um comando sem ele.
                        // Alguns comandos SQL permitem o ;, mas para consistência e segurança, removemos.
                        if (sql.endsWith(";")) {
                             sql = sql.substring(0, sql.length() - 1);
                        }
                        
                        if (!sql.isEmpty()) {
                            try {
                                System.out.println("L" + lineNumber + ": Executando comando:\n" + sql);
                                stmt.execute(sql);
                                System.out.println("Comando executado com sucesso.");
                            } catch (SQLException e) {
                                // Se um erro ocorrer, reverte a transação e lança a exceção
                                connection.rollback(); 
                                System.err.println("Erro na L" + lineNumber + " ao executar comando SQL: " + sql + " - " + e.getMessage());
                                throw new SQLException("Erro na linha " + lineNumber + ": " + e.getMessage(), e);
                            }
                        }
                        sqlStatementBuilder.setLength(0); // Limpa o construtor para o próximo comando
                    }
                }
                String remainingSql = sqlStatementBuilder.toString().trim();
                if (!remainingSql.isEmpty()) {
                    // Remove o ; final se houver (mesma lógica de antes)
                    if (remainingSql.endsWith(";")) {
                        remainingSql = remainingSql.substring(0, remainingSql.length() - 1);
                    }
                    if (!remainingSql.isEmpty()) {
                        try {
                            System.out.println("Executando comando restante: " + remainingSql);
                            stmt.execute(remainingSql);
                            System.out.println("Comando restante executado com sucesso.");
                        } catch (SQLException e) {
                            connection.rollback();
                            System.err.println("Erro ao executar comando SQL restante: " + remainingSql + " - " + e.getMessage());
                            throw new SQLException("Erro ao executar comando restante: " + e.getMessage(), e);
                        }
                    }
                }
                connection.commit(); // Confirma todas as operações se tudo correu bem
                System.out.println("Script SQL executado com sucesso no DB: " + targetDatabaseName);

            } catch (IOException | SQLException e) {
                // Captura exceções de IO e SQL que podem ocorrer durante a leitura/execução
                try {
                    if (connection != null) connection.rollback(); // Garante o rollback em caso de qualquer erro
                } catch (SQLException rbEx) {
                    System.err.println("Erro ao fazer rollback: " + rbEx.getMessage());
                }
                throw e; // Relança a exceção original
            } finally {
                connection.setAutoCommit(true); // Retorna ao modo auto-commit padrão
            }
        }
    }
}