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

public class MigrationEngineTest {

    private static final Charset ISO = Charset.forName("ISO-8859-1");

    private File sqlFile(String content) throws IOException {
        File f = File.createTempFile("engine-dump", ".sql");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(ISO));
        }
        return f;
    }

    private MigrationEngine engine(FakeJdbc jdbc, File sql, List<String> logs) {
        return new MigrationEngine(jdbc.connection(), "lc_destino", sql, logs::add);
    }

    @Test
    public void executar_happyPath_commitsAndReportsSuccess() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        List<String> logs = new ArrayList<String>();
        File sql = sqlFile("CREATE TABLE `produto` (id INT);\nINSERT INTO `produto` VALUES (1);");

        MigrationEngine eng = engine(jdbc, sql, logs);
        eng.configurar(true, false, false, false, false, false, false);
        MigrationReport report = eng.executar();

        assertTrue(report.isSucesso());
        assertTrue(report.getErros().isEmpty());
        assertEquals(1, jdbc.commits);
        assertTrue(report.getFases().stream().anyMatch(f -> f.contains("Staging criado")));
        assertTrue(report.getFases().stream().anyMatch(f -> f.contains("stg_migracao removido")));
        // autoCommit restored at the end
        assertEquals(Boolean.TRUE, jdbc.lastAutoCommit);
    }

    @Test
    public void executar_sqlFailure_rollsBackAndReportsError() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.failExecute = s -> s.startsWith("USE ");
        jdbc.failErrorCode = 1044;
        List<String> logs = new ArrayList<String>();
        File sql = sqlFile("INSERT INTO `produto` VALUES (1);");

        MigrationReport report = engine(jdbc, sql, logs).executar();

        assertFalse(report.isSucesso());
        assertTrue(jdbc.rollbacks >= 1);
        assertTrue(report.getErros().stream().anyMatch(e -> e.contains("Erro SQL")));
        assertEquals(0, jdbc.commits);
    }

    @Test
    public void executar_missingSqlFile_reportsIoError() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        List<String> logs = new ArrayList<String>();
        File missing = new File("this-file-does-not-exist-" + System.nanoTime() + ".sql");

        MigrationReport report = engine(jdbc, missing, logs).executar();

        assertFalse(report.isSucesso());
        assertTrue(report.getErros().stream()
                .anyMatch(e -> e.contains("Erro ao ler arquivo SQL")));
        assertTrue(jdbc.rollbacks >= 1);
    }

    @Test
    public void executar_emitsStartLogWithDestinationDatabase() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        List<String> logs = new ArrayList<String>();
        File sql = sqlFile("INSERT INTO `produto` VALUES (1);");

        engine(jdbc, sql, logs).executar();

        assertTrue(logs.stream().anyMatch(l -> l.contains("lc_destino")));
    }
}
