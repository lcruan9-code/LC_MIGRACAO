package model.migration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gera um backup completo (estrutura + dados) de um banco MySQL via mysqldump,
 * gravando um arquivo .sql na pasta informada (pasta do .jar da LC).
 *
 * <p>Executado ANTES da migração como ponto de restauração. Se o mysqldump não
 * for encontrado ou falhar, {@link #executar(String)} lança exceção — cabe ao
 * chamador abortar a migração.
 */
public class SchemaBackup {

    private final String host;
    private final String port;
    private final String user;
    private final String password;
    private final String database;
    private final File    backupDir;
    private final Consumer<String> logger;

    public SchemaBackup(String host, String port, String user, String password,
            String database, File backupDir, Consumer<String> logger) {
        this.host      = host;
        this.port      = port;
        this.user      = user;
        this.password  = password;
        this.database  = database;
        this.backupDir = backupDir;
        this.logger    = logger;
    }

    /** Nome do arquivo de backup com timestamp (ex.: backup_lc_sistemas_2026-06-30_14-05-01.sql). */
    String nomeArquivo(String timestamp) {
        return "backup_" + database + "_" + timestamp + ".sql";
    }

    /** Monta a linha de comando do mysqldump. */
    List<String> montarComando(String mysqldumpPath, File destino) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(mysqldumpPath);
        cmd.add("--host=" + host);
        cmd.add("--port=" + port);
        cmd.add("--user=" + user);
        cmd.add("--password=" + password);
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--result-file=" + destino.getAbsolutePath());
        cmd.add(database);
        return cmd;
    }

    /**
     * Executa o backup.
     *
     * @param timestamp carimbo de data/hora já formatado (injetado para testabilidade).
     * @return arquivo .sql gerado.
     */
    public File executar(String timestamp) throws IOException, InterruptedException {
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Não foi possível criar/acessar a pasta de backup: " + backupDir);
        }
        File destino = new File(backupDir, nomeArquivo(timestamp));

        String mysqldump = localizarMysqldump();
        if (mysqldump == null) {
            throw new IOException("mysqldump não encontrado. Instale o MySQL Server "
                    + "ou garanta que o mysqldump esteja no PATH.");
        }

        logger.accept("[BACKUP] Gerando backup completo de '" + database + "' em "
                + destino.getName() + " ...");

        ProcessBuilder pb = new ProcessBuilder(montarComando(mysqldump, destino));
        Process proc = pb.start();

        // O dump vai para --result-file; aqui só coletamos stderr para diagnóstico.
        StringBuilder erros = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.toLowerCase().contains("using a password")) {
                    erros.append(line).append('\n');
                }
            }
        }

        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("Falha ao gerar backup (mysqldump código " + exit + "): "
                    + erros.toString().trim());
        }

        logger.accept("[BACKUP] Concluído: " + destino.getAbsolutePath());
        return destino;
    }

    /**
     * Procura o mysqldump nas instalações padrão do MySQL no Windows; se não
     * achar, cai para o "mysqldump" do PATH.
     */
    private String localizarMysqldump() {
        String[] bases = {
            System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)")
        };
        for (String base : bases) {
            if (base == null) continue;
            File mysqlDir = new File(base, "MySQL");
            File[] versoes = mysqlDir.listFiles();
            if (versoes == null) continue;
            for (File versao : versoes) {
                File exe = new File(versao, "bin" + File.separator + "mysqldump.exe");
                if (exe.isFile()) {
                    return exe.getAbsolutePath();
                }
            }
        }
        return "mysqldump"; // fallback: PATH
    }
}
