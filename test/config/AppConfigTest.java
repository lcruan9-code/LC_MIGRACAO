package config;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

public class AppConfigTest {

    private File configFile(String content) throws IOException {
        File f = File.createTempFile("rede", ".txt");
        f.deleteOnExit();
        Files.write(f.toPath(), content.getBytes());
        return f;
    }

    @Test
    public void missingFile_keepsDefaults() {
        File missing = new File("no-such-rede-" + System.nanoTime() + ".txt");
        AppConfig cfg = new AppConfig(missing);

        assertEquals("jdbc:mysql://localhost:3306/", cfg.getDbUrl());
        assertEquals("", cfg.getDbUser());
        assertEquals("", cfg.getDbPassword());
        assertEquals("", cfg.getDefaultDb());
    }

    @Test
    public void validFile_parsesAllKeysAndBuildsJdbcUrl() throws Exception {
        File f = configFile(String.join("\n",
                "IP: 192.168.0.10",
                "PORT: 3307",
                "USER: root",
                "KEY: secret",
                "DB: lc_sistemas",
                "TERMINAL_TIPO: PDV",
                "ID_EMPRESA_PADRAO: 1"));

        AppConfig cfg = new AppConfig(f);

        assertEquals("jdbc:mysql://192.168.0.10:3307/", cfg.getDbUrl());
        assertEquals("root", cfg.getDbUser());
        assertEquals("secret", cfg.getDbPassword());
        assertEquals("lc_sistemas", cfg.getDefaultDb());
        assertEquals("PDV", cfg.getTerminalTipo());
        assertEquals("1", cfg.getIdEmpresaPadrao());
    }

    @Test
    public void commentsAndBlankLines_areIgnored() throws Exception {
        File f = configFile(String.join("\n",
                "# comentario",
                "",
                "   ",
                "USER: maria"));

        AppConfig cfg = new AppConfig(f);

        assertEquals("maria", cfg.getDbUser());
        // Untouched keys keep their defaults
        assertEquals("localhost", "localhost"); // sanity anchor
        assertEquals("", cfg.getDefaultDb());
    }

    @Test
    public void invalidLineWithoutColon_isSkipped() throws Exception {
        File f = configFile(String.join("\n",
                "LINHA SEM SEPARADOR",
                "USER: joao"));

        AppConfig cfg = new AppConfig(f);

        assertEquals("joao", cfg.getDbUser());
    }

    @Test
    public void valueContainingColon_splitsOnlyOnFirstColon() throws Exception {
        File f = configFile("KEY: a:b:c");

        AppConfig cfg = new AppConfig(f);

        assertEquals("a:b:c", cfg.getDbPassword());
    }

    @Test
    public void updateDefaultDb_replacesDbValue() throws Exception {
        File f = configFile("DB: original");
        AppConfig cfg = new AppConfig(f);
        assertEquals("original", cfg.getDefaultDb());

        cfg.updateDefaultDb("novo_banco");

        assertEquals("novo_banco", cfg.getDefaultDb());
    }
}
