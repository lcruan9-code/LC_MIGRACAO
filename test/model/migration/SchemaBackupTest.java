package model.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.Test;

public class SchemaBackupTest {

    private SchemaBackup backup() {
        return new SchemaBackup("127.0.0.1", "3306", "root", "123456",
                "lc_sistemas", new File("."), msg -> {});
    }

    @Test
    public void nomeArquivo_incluiBancoETimestamp() {
        assertEquals("backup_lc_sistemas_2026-06-30_14-05-01.sql",
                backup().nomeArquivo("2026-06-30_14-05-01"));
    }

    @Test
    public void montarComando_incluiConexaoOpcoesEBancoPorUltimo() {
        File destino = new File("C:/lc/backup_lc_sistemas_x.sql");
        List<String> cmd = backup().montarComando("mysqldump", destino);

        assertEquals("mysqldump", cmd.get(0));
        assertTrue(cmd.contains("--host=127.0.0.1"));
        assertTrue(cmd.contains("--port=3306"));
        assertTrue(cmd.contains("--user=root"));
        assertTrue(cmd.contains("--password=123456"));
        assertTrue(cmd.contains("--single-transaction"));
        assertTrue(cmd.contains("--routines"));
        assertTrue(cmd.contains("--triggers"));
        assertTrue(cmd.contains("--result-file=" + destino.getAbsolutePath()));
        // o nome do banco é sempre o último argumento
        assertEquals("lc_sistemas", cmd.get(cmd.size() - 1));
    }

    @Test
    public void montarComando_bancoNuncaVemAntesDasOpcoes() {
        List<String> cmd = backup().montarComando("mysqldump", new File("x.sql"));
        int idxDb = cmd.indexOf("lc_sistemas");
        int idxResult = cmd.indexOf("--result-file=" + new File("x.sql").getAbsolutePath());
        assertTrue("banco deve vir depois das opções", idxDb > idxResult);
    }
}
