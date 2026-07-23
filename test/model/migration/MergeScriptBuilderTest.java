package model.migration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import support.FakeJdbc;

public class MergeScriptBuilderTest {

    private MergeScriptBuilder builder(FakeJdbc jdbc, MigrationReport report,
            boolean produtos, boolean fornecedores, boolean clientes,
            boolean estoque, boolean receber, boolean pagar, boolean pagamento) {
        List<String> logs = new ArrayList<String>();
        return new MergeScriptBuilder(jdbc.connection(), logs::add, report,
                produtos, fornecedores, clientes, estoque, receber, pagar, pagamento);
    }

    @Test
    public void executar_withAllFlagsOff_runsSessionSetupAndRecordsSuccessFase() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, false, false, false, false, false, false, false).executar();

        assertTrue(jdbc.executed.stream()
                .anyMatch(s -> s.contains("group_concat_max_len")));
        // Modo de importação tolerante ativado (sem STRICT) para não abortar em truncamento
        assertTrue(jdbc.executed.stream()
                .anyMatch(s -> s.replace(" ", "").contains("sql_mode='")));
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Plano de merge executado com sucesso")));
        // Nothing migrated → no inserted rows recorded
        assertTrue(report.getInseridos().isEmpty());
    }

    @Test
    public void executar_withProdutos_butMissingStagingTables_recordsAvisos() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(); // intForQuery defaults to 0 → tables "don't exist"
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        // Lookup tables were probed and reported as missing
        assertTrue(report.getAvisos().stream()
                .anyMatch(a -> a.contains("categoria") && a.contains("não encontrada")));
        assertTrue(report.getAvisos().stream()
                .anyMatch(a -> a.contains("produto") && a.contains("não encontrada")));
        // Still finished the plan
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Plano de merge executado com sucesso")));
    }

    @Test
    public void executar_withFornecedoresAndClientes_probesThoseTables() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, false, true, true, false, false, false, false).executar();

        assertTrue(report.getAvisos().stream()
                .anyMatch(a -> a.contains("fornecedor") && a.contains("não encontrada")));
        assertTrue(report.getAvisos().stream()
                .anyMatch(a -> a.contains("cliente") && a.contains("não encontrada")));
    }

    @Test
    public void exec_swallowsKnownSqlErrorCodesAsAvisos() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.failExecute = s -> s.contains("group_concat_max_len");
        jdbc.failErrorCode = 1146; // table not found → treated as warning

        MigrationReport report = new MigrationReport();
        builder(jdbc, report, false, false, false, false, false, false, false).executar();

        assertTrue(report.getAvisos().stream()
                .anyMatch(a -> a.contains("1146")));
    }

    @Test(expected = SQLException.class)
    public void exec_rethrowsUnknownSqlErrorCodes() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.failExecute = s -> s.contains("group_concat_max_len");
        jdbc.failErrorCode = 9999; // unexpected → must propagate

        builder(jdbc, new MigrationReport(), false, false, false, false, false, false, false)
                .executar();
    }

    @Test
    public void executar_withAllTablesPresent_runsFullPlanAndRecordsInserts() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        // Every probe (COUNT(*) ...) returns 1 → all staging tables "exist".
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        // All groups enabled → exercises lookup, fornecedor/cliente, produto,
        // estoque, receber and pagar code paths.
        builder(jdbc, report, true, true, true, true, true, true, true).executar();

        // No table was reported as missing (all exist in staging)
        assertTrue(report.getAvisos().stream().noneMatch(a -> a.contains("não encontrada")));
        // Inserted counts recorded for representative tables across the phases
        assertTrue(report.getInseridos().containsKey("categoria"));
        assertTrue(report.getInseridos().containsKey("fornecedor"));
        assertTrue(report.getInseridos().containsKey("cliente"));
        assertTrue(report.getInseridos().containsKey("produto (novos)"));
        assertTrue(report.getInseridos().containsKey("estoque (novos)"));
        assertTrue(report.getInseridos().containsKey("receber (novos)"));
        assertTrue(report.getInseridos().containsKey("pagar (novos)"));
        // 'pagamento' (formas de pagamento) is intentionally NOT migrated
        assertFalse(report.getInseridos().containsKey("pagamento (novos)"));
        assertTrue(report.getAvisos().stream().anyMatch(a -> a.contains("Formas de pagamento")));
        // Prepared-statement plumbing actually emitted
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.startsWith("PREPARE")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.startsWith("EXECUTE")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.startsWith("DEALLOCATE")));
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Plano de merge executado com sucesso")));
    }

    @Test
    public void executar_withTablesPresent_appliesOffsetAndAutoIncrementForProduto() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        // Produto uses the 100000 base offset → AUTO_INCREMENT reset to that base
        assertTrue(jdbc.executed.stream()
                .anyMatch(s -> s.contains("`produto` AUTO_INCREMENT = 100000")));
        // OFFSET session var initialised for produto
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@OFF_produto")));
    }

    private static int indexOfFirst(List<String> stmts, String needle) {
        for (int i = 0; i < stmts.size(); i++) {
            if (stmts.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void grupotributacao_remapsInternalFksBeforeItsDedup() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        // As FKs internas do grupotributacao (ncm/cest/cst) são remapeadas
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@hascol_grupotributacao_id_ncm")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@hascol_grupotributacao_id_cest")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@hascol_grupotributacao_id_cst")));

        // O remap precisa acontecer ANTES da criação do _map_grupotributacao (insert/dedup)
        int idxRemap = indexOfFirst(jdbc.executed, "@hascol_grupotributacao_id_ncm");
        int idxDedup = indexOfFirst(jdbc.executed, "CREATE TABLE `stg_migracao`.`_map_grupotributacao`");
        assertTrue("remap deve preceder o dedup do grupotributacao",
                idxRemap >= 0 && idxDedup >= 0 && idxRemap < idxDedup);
    }

    @Test
    public void reformaTributaria_isMigratedWhenStagingHasGrupotributacaocclasstrib() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1; // todas as tabelas existem
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        // Captura do max id antes do dedup do grupo
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@gtrib_maxid_antes")));
        // INSERT da reforma copiando para grupos novos, remapeando FKs
        assertTrue(jdbc.executed.stream().anyMatch(s ->
                s.contains("INSERT INTO `grupotributacaocclasstrib`")
                && s.contains("_map_grupotributacao")
                && s.contains("TRIM(dc.cclasstrib) = TRIM(g.cclasstrib)")
                && s.contains("mg.id_nova > @gtrib_maxid_antes")));
        // Relatório registra a reforma migrada
        assertTrue(report.getInseridos().containsKey("grupotributacaocclasstrib (reforma)"));
    }

    @Test
    public void reformaTributaria_isSkippedWhenStagingHasNoGrupotributacaocclasstrib() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        // grupotributacaocclasstrib NÃO existe no staging; demais tabelas existem
        jdbc.intForQuery = sql -> sql.contains("grupotributacaocclasstrib") ? 0 : 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        assertFalse(jdbc.executed.stream()
                .anyMatch(s -> s.contains("INSERT INTO `grupotributacaocclasstrib`")));
        assertFalse(report.getInseridos().containsKey("grupotributacaocclasstrib (reforma)"));
        // O plano segue normalmente
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Plano de merge executado com sucesso")));
    }

    @Test
    public void fornecedor_isDedupedUsingCnpjCpfColumn() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, false, true, false, false, false, false, false).executar();

        // fornecedor deve detectar a coluna 'cnpj_cpf' (e não 'cpf_cnpj')
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("COLUMN_NAME='cnpj_cpf'")));
        assertFalse(jdbc.executed.stream().anyMatch(s -> s.contains("COLUMN_NAME='cpf_cnpj'")));
        // o join dinâmico usa a coluna correta
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("TRIM(s.cnpj_cpf)")));
    }

    @Test
    public void cliente_isDedupedUsingCpfCnpjColumnAndExcludesSystemIds() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, false, false, true, false, false, false, false).executar();

        // cliente continua usando 'cpf_cnpj'
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("COLUMN_NAME='cpf_cnpj'")));
        // ids de sistema 1 (CLIENTE FINAL) e 2 (SAIDAS) são excluídos
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("NOT IN (1,2)")));
    }

    @Test
    public void produto_remapsExtraUnidadeAndFornecedorForeignKeys() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        // produtos + fornecedores migrados
        builder(jdbc, report, true, true, false, false, false, false, false).executar();

        // FKs adicionais de unidade são remapeadas
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("id_unidadeatacado2")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("id_unidadeatacado3")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("id_unidadeatacado4")));
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("id_unidadeembalagem")));
        // FK produto→fornecedor remapeada quando fornecedores migram
        assertTrue(jdbc.executed.stream().anyMatch(s -> s.contains("@hascol_produto_id_fornecedor")));
    }

    @Test
    public void grupotributacao_normalizesIdCestZeroToCest0000000DoDestino() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        int idxNorm = indexOfFirst(jdbc.executed, "UPDATE `stg_migracao`.`grupotributacao` g");
        assertTrue("normalização de id_cest=0 do grupo deve ser emitida", idxNorm >= 0);
        assertTrue(jdbc.executed.get(idxNorm).contains("TRIM(cest)='0000000'"));
        assertTrue(jdbc.executed.get(idxNorm).contains("WHERE g.id_cest = 0"));

        // Ordem: depois do remap de id_cest do grupo e antes do dedup (_map_grupotributacao)
        int idxRemap = indexOfFirst(jdbc.executed, "@hascol_grupotributacao_id_cest");
        int idxDedup = indexOfFirst(jdbc.executed, "CREATE TABLE `stg_migracao`.`_map_grupotributacao`");
        assertTrue("normalização após o remap do id_cest do grupo",
                idxRemap >= 0 && idxRemap < idxNorm);
        assertTrue("normalização antes do dedup do grupotributacao",
                idxDedup >= 0 && idxNorm < idxDedup);
    }

    @Test
    public void produto_normalizesIdCestZeroToCest0000000DoDestino() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        // Normalização presente: id_cest=0 → cest '0000000' do destino (por código)
        int idxNorm = indexOfFirst(jdbc.executed, "WHERE p.id_cest = 0");
        assertTrue("normalização de id_cest=0 deve ser emitida", idxNorm >= 0);
        assertTrue(jdbc.executed.get(idxNorm).contains("TRIM(cest)='0000000'"));

        // Ordem: depois do remap de id_cest e antes do insert de produto (OFFSET)
        int idxRemapCest = indexOfFirst(jdbc.executed, "@hascol_produto_id_cest");
        int idxInsert    = indexOfFirst(jdbc.executed, "@OFF_produto");
        assertTrue("normalização deve vir após o remap do id_cest",
                idxRemapCest >= 0 && idxRemapCest < idxNorm);
        assertTrue("normalização deve vir antes do insert de produto",
                idxInsert >= 0 && idxNorm < idxInsert);
    }

    @Test
    public void produto_doesNotRemapFornecedorFkWhenFornecedoresNotMigrated() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.intForQuery = sql -> 1;
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, true, false, false, false, false, false, false).executar();

        assertFalse(jdbc.executed.stream().anyMatch(s -> s.contains("@hascol_produto_id_fornecedor")));
    }

    @Test
    public void executar_withEstoqueFlag_butTablesMissing_doesNotRecordInserts() throws Exception {
        FakeJdbc jdbc = new FakeJdbc();
        MigrationReport report = new MigrationReport();

        builder(jdbc, report, false, false, false, true, true, true, true).executar();

        // estoque/receber/pagar/pagamento gated behind tabelaExisteNoStaging → all skipped
        assertFalse(report.getInseridos().containsKey("estoque (novos)"));
        assertTrue(report.getFases().stream()
                .anyMatch(f -> f.contains("Plano de merge executado com sucesso")));
    }
}
