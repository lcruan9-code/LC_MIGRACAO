package model.migration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

/**
 * Carrega um arquivo .sql de dump simples (padrão mysqldump 5.5) para o banco
 * de staging, qualificando todas as referências de tabelas com o prefixo do
 * banco de staging.
 *
 * Trata corretamente:
 *   - Comandos MySQL condicionais  /*!40101 ... * /
 *   - LOCK TABLES / UNLOCK TABLES  (ignorados)
 *   - DROP TABLE / USE / CREATE DATABASE  (ignorados)
 *   - Statements multi-linha acumulados até o ';' final
 *   - Charset ISO-8859-1 (padrão dos dumps LC)
 */
public class StagingLoader {

    private static final Charset DUMP_CHARSET = Charset.forName("ISO-8859-1");
    private static final int    MAX_LOG_SQL   = 120; // chars para preview no log

    private final Connection connection;
    private final String     stagingDb;

    public StagingLoader(Connection connection, String stagingDb) {
        this.connection = connection;
        this.stagingDb  = stagingDb;
    }

    // -------------------------------------------------------------------------
    // Ponto de entrada
    // -------------------------------------------------------------------------

    /**
     * Lê o arquivo SQL, qualifica tabelas para o staging e executa statement a
     * statement.
     */
    public void load(File sqlFile, Consumer<String> logger, MigrationReport report)
            throws IOException, SQLException {

        logger.accept("[FASE 1] Carregando dump no banco de staging: " + stagingDb + " ...");

        int stmtCount = 0;
        int errorCount = 0;

        try (InputStreamReader isr = new InputStreamReader(
                    new FileInputStream(sqlFile), DUMP_CHARSET);
             BufferedReader br = new BufferedReader(isr);
             Statement stmt = connection.createStatement()) {

            StringBuilder buf = new StringBuilder(4096);
            String line;

            while ((line = br.readLine()) != null) {

                String trimmed = line.trim();

                // ── Linhas completamente ignoradas ────────────────────────
                if (trimmed.isEmpty())                            continue;
                if (trimmed.startsWith("--"))                    continue;
                if (trimmed.startsWith("#"))                     continue;
                if (startsWithIgnoreCase(trimmed, "CREATE DATABASE")) continue;
                if (startsWithIgnoreCase(trimmed, "DROP DATABASE"))   continue;
                if (startsWithIgnoreCase(trimmed, "USE "))            continue;
                if (startsWithIgnoreCase(trimmed, "LOCK TABLES"))     continue;
                if (startsWithIgnoreCase(trimmed, "UNLOCK TABLES"))   continue;
                if (startsWithIgnoreCase(trimmed, "DROP TABLE"))      continue;

                // ── Qualifica referências de tabela para o staging ────────
                line = qualifyLine(line);

                buf.append(line).append('\n');

                // ── Detecta fim de statement ──────────────────────────────
                if (endsWithSemicolon(trimmed)) {
                    String sql = buf.toString().trim();
                    buf.setLength(0);

                    if (sql.isEmpty()) continue;

                    // Remove o ';' final para o execute()
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1).trim();
                    }
                    if (sql.isEmpty()) continue;

                    try {
                        stmt.execute(sql);
                        stmtCount++;
                    } catch (SQLException ex) {
                        errorCount++;
                        String preview = sql.length() > MAX_LOG_SQL
                                ? sql.substring(0, MAX_LOG_SQL) + "..." : sql;
                        report.addAviso("[FASE 1] Stmt ignorado ("
                                + ex.getErrorCode() + "): " + preview);
                    }
                }
            }

            // Flush de eventual statement sem ';' no final
            String remaining = buf.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.endsWith(";")) {
                    remaining = remaining.substring(0, remaining.length() - 1).trim();
                }
                if (!remaining.isEmpty()) {
                    try {
                        stmt.execute(remaining);
                        stmtCount++;
                    } catch (SQLException ex) {
                        errorCount++;
                        report.addAviso("[FASE 1] Stmt final ignorado: " + ex.getMessage());
                    }
                }
            }
        }

        logger.accept("[FASE 1] Concluído: " + stmtCount + " statements executados"
                + (errorCount > 0 ? ", " + errorCount + " ignorados." : "."));
        report.addFase("[FASE 1] Staging carregado — " + stmtCount + " statements, "
                + errorCount + " ignorados.");
    }

    // -------------------------------------------------------------------------
    // Qualificação de nomes de tabela
    // -------------------------------------------------------------------------

    /**
     * Substitui referências simples de tabela (CREATE TABLE `x`, INSERT INTO
     * `x`) pela versão qualificada com o banco de staging.
     */
    private String qualifyLine(String line) {

        // CREATE TABLE `nome`  ou  CREATE TABLE nome
        line = line.replaceAll(
                "(?i)(CREATE\\s+TABLE\\s+`)([^`]+)(`)",
                "$1" + stagingDb + "`.`$2$3");
        line = line.replaceAll(
                "(?i)(CREATE\\s+TABLE\\s+)([A-Za-z0-9_]+)(\\s*\\()",
                "$1`" + stagingDb + "`.`$2`$3");

        // INSERT INTO `nome`  ou  INSERT INTO nome
        line = line.replaceAll(
                "(?i)(INSERT\\s+INTO\\s+`)([^`]+)(`\\s*)",
                "$1" + stagingDb + "`.`$2$3");
        line = line.replaceAll(
                "(?i)(INSERT\\s+INTO\\s+)([A-Za-z0-9_]+)(\\s*)",
                "$1`" + stagingDb + "`.`$2`$3");

        // ALTER TABLE `nome`  ou  ALTER TABLE nome
        line = line.replaceAll(
                "(?i)(ALTER\\s+TABLE\\s+`)([^`]+)(`)",
                "$1" + stagingDb + "`.`$2$3");
        line = line.replaceAll(
                "(?i)(ALTER\\s+TABLE\\s+)([A-Za-z0-9_]+)(\\s+)",
                "$1`" + stagingDb + "`.`$2`$3");

        return line;
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * Verifica se a linha termina com ';', ignorando comentários inline e
     * strings ('...' ou "...").
     *
     * Trata sequências de escape: \' dentro de string simples, \" dentro de
     * string dupla e \\ em ambos os contextos.
     */
    private static boolean endsWithSemicolon(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        char last = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // Pula o próximo caractere quando é um escape dentro de string
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if ((inSingle && next == '\'') || (inDouble && next == '"') || next == '\\') {
                    i++;
                    continue;
                }
            }
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            if (!inSingle && !inDouble) last = c;
        }
        return last == ';';
    }
}
