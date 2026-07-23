package model.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import support.FakeJdbc;

public class StagingLoaderTest {

    private static final Charset ISO = Charset.forName("ISO-8859-1");

    private File sqlFile(String content) throws IOException {
        File f = File.createTempFile("dump", ".sql");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(ISO));
        }
        return f;
    }

    private List<String> runLoad(FakeJdbc jdbc, String sql) throws Exception {
        List<String> logs = new ArrayList<String>();
        MigrationReport report = new MigrationReport();
        StagingLoader loader = new StagingLoader(jdbc.connection(), "stg_migracao");
        loader.load(sqlFile(sql), logs::add, report);
        return logs;
    }

    @Test
    public void load_skipsCommentsBlankLinesAndStructuralStatements() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        String sql = String.join("\n",
                "-- a comment",
                "# another comment",
                "",
                "CREATE DATABASE `whatever`;",
                "DROP DATABASE IF EXISTS `whatever`;",
                "USE `olddb`;",
                "LOCK TABLES `produto` WRITE;",
                "DROP TABLE IF EXISTS `produto`;",
                "CREATE TABLE `produto` (id INT);",
                "INSERT INTO `produto` VALUES (1);",
                "UNLOCK TABLES;");

        runLoad(jdbc, sql);

        // Only CREATE TABLE and INSERT survive
        assertEquals(2, jdbc.executed.size());
        for (String stmt : jdbc.executed) {
            assertFalse(stmt.toUpperCase().contains("DROP TABLE"));
            assertFalse(stmt.toUpperCase().startsWith("USE"));
            assertFalse(stmt.toUpperCase().contains("LOCK TABLES"));
        }
    }

    @Test
    public void load_qualifiesCreateInsertAndAlterWithStagingSchema() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        String sql = String.join("\n",
                "CREATE TABLE `produto` (id INT);",
                "INSERT INTO `produto` VALUES (1);",
                "ALTER TABLE `produto` ADD COLUMN nome VARCHAR(10);");

        runLoad(jdbc, sql);

        assertEquals(3, jdbc.executed.size());
        for (String stmt : jdbc.executed) {
            assertTrue("expected staging-qualified table in: " + stmt,
                    stmt.contains("`stg_migracao`.`produto`"));
        }
    }

    @Test
    public void load_qualifiesUnbacktickedTableNames() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        String sql = "CREATE TABLE produto (id INT);\nINSERT INTO produto VALUES (1);";

        runLoad(jdbc, sql);

        assertEquals(2, jdbc.executed.size());
        assertTrue(jdbc.executed.get(0).contains("`stg_migracao`.`produto`"));
        assertTrue(jdbc.executed.get(1).contains("`stg_migracao`.`produto`"));
    }

    @Test
    public void load_treatsSemicolonInsideStringLiteralAsPartOfStatement() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        // The escaped quote (\') and the ';' inside the literal must NOT split it;
        // only the trailing ';' terminates the statement.
        String sql = "INSERT INTO `produto` VALUES ('a\\';b');";

        runLoad(jdbc, sql);

        assertEquals(1, jdbc.executed.size());
        assertTrue(jdbc.executed.get(0).contains("'a\\';b'"));
    }

    @Test
    public void load_treatsSemicolonInsideDoubleQuotedLiteralAsPartOfStatement() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        // Exercises the double-quote branch of the semicolon scanner.
        String sql = "INSERT INTO `produto` VALUES (\"a;b\");";

        runLoad(jdbc, sql);

        assertEquals(1, jdbc.executed.size());
        assertTrue(jdbc.executed.get(0).contains("\"a;b\""));
    }

    @Test
    public void load_handlesEscapedBackslashBeforeTerminator() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        // Trailing path-like value with escaped backslashes then a real ';'.
        String sql = "INSERT INTO `produto` VALUES ('c:\\\\dir\\\\');";

        runLoad(jdbc, sql);

        assertEquals(1, jdbc.executed.size());
    }

    @Test
    public void load_flushesTrailingStatementWithoutSemicolon() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        String sql = "INSERT INTO `produto` VALUES (1)"; // no trailing ';'

        runLoad(jdbc, sql);

        assertEquals(1, jdbc.executed.size());
    }

    @Test
    public void load_recordsFailedStatementsAsAvisosAndContinues() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.failExecute = stmt -> stmt.contains("FAILME");
        jdbc.failErrorCode = 1146;

        List<String> logs = new ArrayList<String>();
        MigrationReport report = new MigrationReport();
        StagingLoader loader = new StagingLoader(jdbc.connection(), "stg_migracao");
        String sql = String.join("\n",
                "INSERT INTO `produto` VALUES (1);",
                "INSERT INTO `FAILME` VALUES (2);",
                "INSERT INTO `produto` VALUES (3);");

        loader.load(sqlFile(sql), logs::add, report);

        // The good statements still ran
        assertEquals(2, jdbc.executed.size());
        // The failure was captured as an aviso with its error code
        assertEquals(1, report.getAvisos().size());
        assertTrue(report.getAvisos().get(0).contains("1146"));
        // A summary fase was recorded
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Staging carregado")));
    }

    @Test
    public void load_emitsProgressLogsForStartAndFinish() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        List<String> logs = runLoad(jdbc, "INSERT INTO `produto` VALUES (1);");

        assertTrue(logs.stream().anyMatch(l -> l.contains("Carregando dump")));
        assertTrue(logs.stream().anyMatch(l -> l.contains("Concluído")));
    }
}
