package model.migration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orquestrador do Motor de Migração Seguro.
 *
 * Executa as 5 fases em sequência:
 *   1. Cria o banco de staging (stg_migracao)
 *   2. Carrega o dump .sql no staging (StagingLoader)
 *   3-5. Executa o plano de merge (MergeScriptBuilder)
 *   6. Remove o banco de staging
 *
 * Uso pelo SwingWorker em MigratorPanel:
 * <pre>
 *   MigrationEngine engine = new MigrationEngine(connection, destDb, sqlFile, logger);
 *   engine.configurar(true, true, true, false, false, false, false);
 *   MigrationReport report = engine.executar();
 * </pre>
 */
public class MigrationEngine {

    private static final Logger log = LoggerFactory.getLogger(MigrationEngine.class);
    private static final String STAGING_DB = "stg_migracao";

    // ── Dependências ──────────────────────────────────────────────────────────
    private final Connection      connection;
    private final String          destinoDb;
    private final File            sqlFile;
    private final Consumer<String> logger;

    // ── Opções de migração (padrão = true para produtos, false para o resto) ─
    private boolean migrarProdutos    = true;
    private boolean migrarFornecedores = false;
    private boolean migrarClientes    = false;
    private boolean migrarEstoque     = false;
    private boolean migrarReceber     = false;
    private boolean migrarPagar       = false;
    private boolean migrarPagamento   = false;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param connection Conexão JDBC já aberta (sem banco selecionado, ou com o
     *                   banco destino selecionado — o engine vai gerenciar o
     *                   contexto via USE durante as fases).
     * @param destinoDb  Nome do banco de dados de destino (ex: "lc_sistemas").
     * @param sqlFile    Arquivo .sql de dump simples (TabelasParaImportação.sql).
     * @param logger     Callback chamado a cada mensagem de progresso (para exibir
     *                   na jTextArea1 em tempo real).
     */
    public MigrationEngine(Connection connection, String destinoDb,
            File sqlFile, Consumer<String> logger) {
        this.connection = connection;
        this.destinoDb  = destinoDb;
        this.sqlFile    = sqlFile;
        this.logger     = logger;
    }

    /** Configura quais grupos de tabelas devem ser migrados. */
    public void configurar(
            boolean produtos,
            boolean fornecedores,
            boolean clientes,
            boolean estoque,
            boolean receber,
            boolean pagar,
            boolean pagamento) {
        this.migrarProdutos     = produtos;
        this.migrarFornecedores = fornecedores;
        this.migrarClientes     = clientes;
        this.migrarEstoque      = estoque;
        this.migrarReceber      = receber;
        this.migrarPagar        = pagar;
        this.migrarPagamento    = pagamento;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Execução
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executa o motor completo de migração.
     *
     * @return MigrationReport com o resultado de cada fase.
     */
    public MigrationReport executar() {

        MigrationReport report = new MigrationReport();

        try {
            connection.setAutoCommit(false);

            // ─── Fase 0: Seleciona o banco destino ────────────────────────
            logger.accept("[INÍCIO] Banco destino: " + destinoDb);
            useDatabase(destinoDb);

            // ─── Fase 1: Cria o banco de staging ──────────────────────────
            logger.accept("[FASE 1] Criando banco de staging: " + STAGING_DB + " ...");
            criarStaging();
            report.addFase("[FASE 1] Staging criado: " + STAGING_DB);

            // ─── Fase 1b: Carrega o dump no staging ───────────────────────
            StagingLoader loader = new StagingLoader(connection, STAGING_DB);
            loader.load(sqlFile, logger, report);

            // Após o load, garante que o contexto ativo seja o banco destino
            useDatabase(destinoDb);

            // ─── Fases 2-5: Plano de merge ────────────────────────────────
            logger.accept("[FASES 2-5] Iniciando plano de merge...");
            MergeScriptBuilder builder = new MergeScriptBuilder(
                    connection, logger, report,
                    migrarProdutos, migrarFornecedores, migrarClientes,
                    migrarEstoque, migrarReceber, migrarPagar, migrarPagamento);
            builder.executar();

            // ─── Commit ───────────────────────────────────────────────────
            connection.commit();
            logger.accept("[COMMIT] Transação confirmada.");

            // ─── Fase 6: Remove staging ───────────────────────────────────
            logger.accept("[LIMPEZA] Removendo banco de staging...");
            removerStaging();
            report.addFase("[LIMPEZA] stg_migracao removido.");

            report.setSucesso(true);
            logger.accept("[CONCLUÍDO] Migração segura finalizada com sucesso!");

        } catch (SQLException ex) {
            try { connection.rollback(); } catch (SQLException ignore) {}
            report.addErro("Erro SQL: " + ex.getMessage() + " (código " + ex.getErrorCode() + ")");
            report.setSucesso(false);
            logger.accept("[ERRO] " + ex.getMessage());
            log.error("Erro SQL durante a migração", ex);

        } catch (IOException ex) {
            try { connection.rollback(); } catch (SQLException ignore) {}
            report.addErro("Erro ao ler arquivo SQL: " + ex.getMessage());
            report.setSucesso(false);
            logger.accept("[ERRO] " + ex.getMessage());
            log.error("Erro de I/O ao ler arquivo SQL", ex);

        } finally {
            // Tenta remover staging mesmo em caso de erro
            try { removerStaging(); } catch (Exception ignore) {}
            try { connection.setAutoCommit(true); } catch (SQLException ignore) {}
        }

        return report;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void useDatabase(String db) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE `" + db + "`");
        }
    }

    private void criarStaging() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS=0");
            stmt.execute("DROP DATABASE IF EXISTS `" + STAGING_DB + "`");
            stmt.execute("CREATE DATABASE `" + STAGING_DB +
                    "` /*!40100 DEFAULT CHARACTER SET latin1 */");
        }
    }

    private void removerStaging() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS `" + STAGING_DB + "`");
            stmt.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }
}
