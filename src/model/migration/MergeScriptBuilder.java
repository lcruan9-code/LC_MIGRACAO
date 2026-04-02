package model.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.function.Consumer;

/**
 * Constrói e executa o plano completo de merge seguro:
 *
 *   Fase 2 – Tabelas lookup (categoria, ncm, etc.)  → AutoInc + Dedup por nome
 *   Fase 3 – Remap de FKs das tabelas-pai nas tabelas-filhas dentro do staging
 *   Fase 4 – Produto (OFFSET por id)
 *   Fase 5 – Tabelas dependentes (estoque, receber, pagar, pagamento)
 *
 * LC_EXTRACAO_TABELAS_MIGRACAO, adaptada para execução direta via JDBC.
 *
 * Cada bloco SQL gerado usa PREPARE/EXECUTE/DEALLOCATE para lidar com colunas
 * dinâmicas (detectadas via INFORMATION_SCHEMA em tempo de execução).
 */
public class MergeScriptBuilder {

    private static final String STG = "stg_migracao";

    // Opções de tabelas a migrar (configuráveis pela UI)
    private final boolean migrarProdutos;
    private final boolean migrarFornecedores;
    private final boolean migrarClientes;
    private final boolean migrarEstoque;
    private final boolean migrarReceber;
    private final boolean migrarPagar;
    private final boolean migrarPagamento;

    private final Connection      connection;
    private final Consumer<String> logger;
    private final MigrationReport  report;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    public MergeScriptBuilder(
            Connection connection,
            Consumer<String> logger,
            MigrationReport report,
            boolean migrarProdutos,
            boolean migrarFornecedores,
            boolean migrarClientes,
            boolean migrarEstoque,
            boolean migrarReceber,
            boolean migrarPagar,
            boolean migrarPagamento) {

        this.connection       = connection;
        this.logger           = logger;
        this.report           = report;
        this.migrarProdutos   = migrarProdutos;
        this.migrarFornecedores = migrarFornecedores;
        this.migrarClientes   = migrarClientes;
        this.migrarEstoque    = migrarEstoque;
        this.migrarReceber    = migrarReceber;
        this.migrarPagar      = migrarPagar;
        this.migrarPagamento  = migrarPagamento;
    }

    // -------------------------------------------------------------------------
    // Execução principal
    // -------------------------------------------------------------------------

    public void executar() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            exec(stmt, "SET SESSION group_concat_max_len = 1000000");
            executarTabelasLookup(stmt);
            executarFornecedoresEClientes(stmt);
            executarProdutos(stmt);
            executarDependentes(stmt);
            report.addFase("[FASES 2-5] Plano de merge executado com sucesso.");
        }
    }

    // ─── FASE 2: Tabelas lookup ───────────────────────────────────────────────

    private void executarTabelasLookup(Statement stmt) throws SQLException {
        if (!migrarProdutos) return;

        logger.accept("[FASE 2] Processando tabelas de referência (lookup)...");

        execAutoIncDedup(stmt, "categoria", "_map_categoria",
                "TRIM(d.nome)=TRIM(s.nome)", false, null);

        execUpdate(stmt,
            "UPDATE `" + STG + "`.`subcategoria` sc" +
            " JOIN `" + STG + "`.`_map_categoria` mc ON mc.id_antiga = sc.id_categoria" +
            " SET sc.id_categoria = mc.id_nova" +
            " WHERE sc.id_categoria IS NOT NULL");

        execAutoIncDedup(stmt, "subcategoria", "_map_subcategoria",
                "d.id_categoria = s.id_categoria AND TRIM(d.nome)=TRIM(s.nome)", false, null);
        execAutoIncDedup(stmt, "fabricante",   "_map_fabricante",
                "TRIM(d.nome)=TRIM(s.nome)", false, null);
        execAutoIncDedup(stmt, "unidade",      "_map_unidade",
                "TRIM(d.descricao)=TRIM(s.descricao)", false, null);
        execAutoIncDedup(stmt, "cst",          "_map_cst",
                "TRIM(d.codigotributario)=TRIM(s.codigotributario)", false, null);
        execAutoIncDedup(stmt, "ncm",          "_map_ncm",
                "TRIM(d.codigo)=TRIM(s.codigo)", false, null);
        execAutoIncDedup(stmt, "cest",         "_map_cest",
                "TRIM(d.cest)=TRIM(s.cest)", false, null);
        execAutoIncDedup(stmt, "grupotributacao", "_map_grupotributacao",
                "TRIM(d.nome)=TRIM(s.nome) AND TRIM(d.uf)=TRIM(s.uf)", false, null);
    }

    // ─── FASE 2b: Fornecedor e Cliente ────────────────────────────────────────

    private void executarFornecedoresEClientes(Statement stmt) throws SQLException {
        if (migrarFornecedores) {
            execClienteFornecedorDinamico(stmt, "fornecedor", "_map_fornecedor",
                    true, "WHERE s.id NOT IN (1,2)", "@JOIN_FORNECEDOR");
        }
        if (migrarClientes) {
            execClienteFornecedorDinamico(stmt, "cliente", "_map_cliente",
                    true, "WHERE s.id <> 1", "@JOIN_CLIENTE");
        }
    }

    // ─── FASE 3+4: Remap de FKs e inserção de produto ────────────────────────

    private void executarProdutos(Statement stmt) throws SQLException {
        if (!migrarProdutos) return;

        logger.accept("[FASE 3] Remapeando FKs nas tabelas de referência do produto...");
        remapFkSeExistir(stmt, "produto", "id_categoria",       "_map_categoria");
        remapFkSeExistir(stmt, "produto", "id_subcategoria",    "_map_subcategoria");
        remapFkSeExistir(stmt, "produto", "id_fabricante",      "_map_fabricante");
        remapFkSeExistir(stmt, "produto", "id_unidade",         "_map_unidade");
        remapFkSeExistir(stmt, "produto", "id_cst",             "_map_cst");
        remapFkSeExistir(stmt, "produto", "id_ncm",             "_map_ncm");
        remapFkSeExistir(stmt, "produto", "id_cest",            "_map_cest");
        remapFkSeExistir(stmt, "produto", "id_grupotributacao", "_map_grupotributacao");

        logger.accept("[FASE 4] Inserindo produtos com OFFSET de ID...");
        execAppendOffset(stmt, "produto", "_map_produto", true, 100000L, null);
    }

    // ─── FASE 5: Tabelas dependentes ─────────────────────────────────────────

    private void executarDependentes(Statement stmt) throws SQLException {
        if (migrarEstoque && tabelaExisteNoStaging(stmt, "estoque")) {
            logger.accept("[FASE 5] Processando estoque...");
            remapFkSeExistir(stmt, "estoque",       "id_produto", "_map_produto");
            remapFkSeExistir(stmt, "estoquesaldo",  "id_produto", "_map_produto");
            remapFkSeExistir(stmt, "ajusteestoque", "id_produto", "_map_produto");
            execAppendOffset(stmt, "estoque",       "_map_estoque",       true, 0L, null);
            execAppendOffset(stmt, "estoquesaldo",  "_map_estoquesaldo",  true, 0L, null);
            execAppendOffset(stmt, "ajusteestoque", "_map_ajusteestoque", true, 0L, null);
        }

        if (migrarReceber && tabelaExisteNoStaging(stmt, "receber")) {
            logger.accept("[FASE 5] Processando contas a receber...");
            if (migrarClientes) remapFkSeExistir(stmt, "receber", "id_cliente", "_map_cliente");
            execAppendOffset(stmt, "receber", "_map_receber", true, 0L, null);
        }

        if (migrarPagar && tabelaExisteNoStaging(stmt, "pagar")) {
            logger.accept("[FASE 5] Processando contas a pagar...");
            if (migrarFornecedores) remapFkSeExistir(stmt, "pagar", "id_fornecedor", "_map_fornecedor");
            execAppendOffset(stmt, "pagar", "_map_pagar", true, 0L, null);
        }

        if (migrarPagamento && tabelaExisteNoStaging(stmt, "pagamento")) {
            logger.accept("[FASE 5] Processando pagamentos...");
            remapFkSeExistir(stmt, "pagamento", "id_receber", "_map_receber");
            remapFkSeExistir(stmt, "pagamento", "id_pagar",   "_map_pagar");
            execAppendOffset(stmt, "pagamento", "_map_pagamento", true, 0L, null);
        }
    }

    // =========================================================================
    // Blocos de SQL — port fiel do ExtrairTabelasSelecionadasDumpSql.java
    // =========================================================================

    /**
     * Tabelas de lookup: insere registros que ainda não existem no destino
     * (match por chave natural), depois cria a tabela _map_ para remapear IDs.
     */
    private void execAutoIncDedup(Statement stmt, String tabela, String mapTable,
            String joinNatural, boolean forceEmpresa1, String whereStagingExtra)
            throws SQLException {

        if (!tabelaExisteNoStaging(stmt, tabela)) {
            report.addAviso("Tabela '" + tabela + "' não encontrada no staging — pulada.");
            return;
        }

        logger.accept("  → dedup: " + tabela);

        String where = (whereStagingExtra != null && !whereStagingExtra.trim().isEmpty())
                ? " " + whereStagingExtra : "";

        // Colunas comuns entre staging e destino (exceto id)
        exec(stmt, "SET @cols_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE()" +
                "   AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        exec(stmt, "SET @sel_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('s.`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE()" +
                "   AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        if (forceEmpresa1) {
            exec(stmt, "SET @sel_common_" + tabela + " := REPLACE(@sel_common_" + tabela +
                    ", 's.`id_empresa`', '1 AS `id_empresa`')");
        }

        String extraAnd = "";
        if (whereStagingExtra != null && !whereStagingExtra.trim().isEmpty()) {
            String w = whereStagingExtra.trim();
            if (w.toUpperCase().startsWith("WHERE")) w = w.substring(5).trim();
            extraAnd = " AND " + w.replace("'", "''");
        }

        // Força MySQL a continuar os inserts exatamente do próximo ID disponível (se a tabela sofreu DELETEs antes)
        exec(stmt, "ALTER TABLE `" + tabela + "` AUTO_INCREMENT = 1");

        exec(stmt, "SET @sql_ins_" + tabela + " := IF(@cols_common_" + tabela +
                " IS NULL OR @cols_common_" + tabela + " = ''," +
                "  'SELECT ''ERRO: " + tabela + " sem colunas comuns'''," +
                "  CONCAT(" +
                "    'INSERT INTO `" + tabela + "` (', @cols_common_" + tabela + ", ') '," +
                "    'SELECT ', @sel_common_" + tabela + ", ' '," +
                "    'FROM `" + STG + "`.`" + tabela + "` s '," +
                "    'LEFT JOIN `" + tabela + "` d ON " + joinNatural.replace("'", "''") + " '," +
                "    'WHERE d.id IS NULL" + extraAnd + "'" +
                "  )" +
                ")");
        exec(stmt, "PREPARE stmt_ins_" + tabela + " FROM @sql_ins_" + tabela);
        exec(stmt, "EXECUTE stmt_ins_" + tabela);
        exec(stmt, "DEALLOCATE PREPARE stmt_ins_" + tabela);

        // Conta inseridos
        int inserted = queryInt(stmt,
                "SELECT COUNT(*) FROM `" + STG + "`.`_map_" + tabela + "` LIMIT 1", 0);

        // Cria tabela de mapeamento (id_staging → id_destino)
        exec(stmt, "DROP TABLE IF EXISTS `" + STG + "`.`" + mapTable + "`");
        exec(stmt, "CREATE TABLE `" + STG + "`.`" + mapTable + "` (" +
                "  id_antiga BIGINT NOT NULL," +
                "  id_nova   BIGINT NOT NULL," +
                "  PRIMARY KEY (id_antiga), KEY (id_nova)" +
                ") ENGINE=InnoDB");

        exec(stmt, "INSERT INTO `" + STG + "`.`" + mapTable + "` (id_antiga, id_nova)" +
                " SELECT s.id AS id_antiga, MIN(d.id) AS id_nova" +
                " FROM `" + STG + "`.`" + tabela + "` s" +
                " JOIN `" + tabela + "` d ON " + joinNatural + where +
                " GROUP BY s.id");

        inserted = queryInt(stmt,
                "SELECT COUNT(*) FROM `" + STG + "`.`" + mapTable + "`", 0);
        report.addInserido(tabela, inserted);
        logger.accept("     " + tabela + " → " + inserted + " mapeados.");
    }

    // -------------------------------------------------------------------------

    /**
     * Fornecedor / Cliente: dedup dinâmico — usa CPF/CNPJ se existir, senão nome.
     */
    private void execClienteFornecedorDinamico(Statement stmt, String tabela,
            String mapTable, boolean forceEmpresa1, String whereStagingExtra,
            String joinVar) throws SQLException {

        if (!tabelaExisteNoStaging(stmt, tabela)) {
            report.addAviso("Tabela '" + tabela + "' não encontrada no staging — pulada.");
            return;
        }

        logger.accept("  → dedup dinâmico: " + tabela);

        String varHasCpfStg = "@hascpf_" + tabela + "_stg";
        String varHasCpfDst = "@hascpf_" + tabela + "_dst";

        exec(stmt, "SET " + varHasCpfStg + " := (" +
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS" +
                " WHERE TABLE_SCHEMA='" + STG + "' AND TABLE_NAME='" + tabela +
                "' AND COLUMN_NAME='cpf_cnpj')");

        exec(stmt, "SET " + varHasCpfDst + " := (" +
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS" +
                " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + tabela +
                "' AND COLUMN_NAME='cpf_cnpj')");

        exec(stmt, "SET " + joinVar + " := IF((" + varHasCpfStg + " > 0) AND (" + varHasCpfDst + " > 0)," +
                "  '((NULLIF(TRIM(s.cpf_cnpj),\\'\\') IS NOT NULL AND NULLIF(TRIM(d.cpf_cnpj),\\'\\') = NULLIF(TRIM(s.cpf_cnpj),\\'\\')) OR (NULLIF(TRIM(s.cpf_cnpj),\\'\\') IS NULL AND TRIM(d.nome)=TRIM(s.nome)))'," +
                "  '(TRIM(d.nome)=TRIM(s.nome))'" +
                ")");

        // Colunas comuns
        exec(stmt, "SET @cols_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE() AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        exec(stmt, "SET @sel_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('s.`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE() AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        if (forceEmpresa1) {
            exec(stmt, "SET @sel_common_" + tabela + " := REPLACE(@sel_common_" + tabela +
                    ", 's.`id_empresa`', '1 AS `id_empresa`')");
        }

        String extraAnd = "";
        String where    = "";
        if (whereStagingExtra != null && !whereStagingExtra.trim().isEmpty()) {
            String w = whereStagingExtra.trim();
            where = " " + w;
            if (w.toUpperCase().startsWith("WHERE")) {
                extraAnd = " AND " + w.substring(5).trim().replace("'", "''");
            }
        }

        // Força MySQL a continuar os inserts exatamente do próximo ID disponível
        exec(stmt, "ALTER TABLE `" + tabela + "` AUTO_INCREMENT = 1");

        exec(stmt, "SET @sql_ins_" + tabela + " := IF(@cols_common_" + tabela +
                " IS NULL OR @cols_common_" + tabela + " = ''," +
                " 'SELECT ''ERRO: " + tabela + " sem colunas comuns'''," +
                " CONCAT(" +
                "   'INSERT INTO `" + tabela + "` (', @cols_common_" + tabela + ", ') '," +
                "   'SELECT ', @sel_common_" + tabela + ", ' '," +
                "   'FROM `" + STG + "`.`" + tabela + "` s '," +
                "   'LEFT JOIN `" + tabela + "` d ON ', " + joinVar + ", ' '," +
                "   'WHERE d.id IS NULL" + extraAnd + "'" +
                " )" +
                ")");
        exec(stmt, "PREPARE stmt_ins_" + tabela + " FROM @sql_ins_" + tabela);
        exec(stmt, "EXECUTE stmt_ins_" + tabela);
        exec(stmt, "DEALLOCATE PREPARE stmt_ins_" + tabela);

        // Mapa
        exec(stmt, "DROP TABLE IF EXISTS `" + STG + "`.`" + mapTable + "`");
        exec(stmt, "CREATE TABLE `" + STG + "`.`" + mapTable + "` (" +
                "  id_antiga BIGINT NOT NULL, id_nova BIGINT NOT NULL," +
                "  PRIMARY KEY (id_antiga), KEY (id_nova)) ENGINE=InnoDB");

        exec(stmt, "SET @sql_map_" + tabela + " := CONCAT(" +
                " 'INSERT INTO `" + STG + "`.`" + mapTable + "` (id_antiga, id_nova) '," +
                " 'SELECT s.id AS id_antiga, MIN(d.id) AS id_nova '," +
                " 'FROM `" + STG + "`.`" + tabela + "` s '," +
                " 'JOIN `" + tabela + "` d ON ', " + joinVar + ", ' " +
                where.replace("'", "\\'") + " '," +
                " 'GROUP BY s.id'" +
                ")");
        exec(stmt, "PREPARE stmt_map_" + tabela + " FROM @sql_map_" + tabela);
        exec(stmt, "EXECUTE stmt_map_" + tabela);
        exec(stmt, "DEALLOCATE PREPARE stmt_map_" + tabela);

        int mapeados = queryInt(stmt,
                "SELECT COUNT(*) FROM `" + STG + "`.`" + mapTable + "`", 0);
        report.addInserido(tabela, mapeados);
        logger.accept("     " + tabela + " → " + mapeados + " mapeados.");
    }

    // -------------------------------------------------------------------------

    /**
     * Tabelas com ID próprio (produto, receber, pagar, estoque...):
     * calcula OFFSET = MAX(id) no destino e insere novos registros com id_nova.
     */
    private void execAppendOffset(Statement stmt, String tabela, String mapTable,
            boolean forceEmpresa1, long baseOffsetId, String whereStagingExtra) throws SQLException {

        if (!tabelaExisteNoStaging(stmt, tabela)) {
            report.addAviso("Tabela '" + tabela + "' não encontrada no staging — pulada.");
            return;
        }

        logger.accept("  → append offset: " + tabela);

        // OFF = MAX(id) no destino (baseOffsetId - 1 se vazia e baseOffsetId > 0)
        long offsetStart = (baseOffsetId > 0) ? (baseOffsetId - 1) : 0;
        exec(stmt, "SET @OFF_" + tabela + " := (SELECT GREATEST(IFNULL(MAX(id)," + offsetStart + "), " + offsetStart + ") FROM `" + tabela + "`)");

        // Mapa: id_antiga → id_nova = id_antiga + OFFSET
        exec(stmt, "DROP TABLE IF EXISTS `" + STG + "`.`" + mapTable + "`");
        exec(stmt, "CREATE TABLE `" + STG + "`.`" + mapTable + "` (" +
                "  id_antiga BIGINT NOT NULL, id_nova BIGINT NOT NULL," +
                "  PRIMARY KEY (id_antiga), KEY (id_nova)) ENGINE=InnoDB");

        String where = (whereStagingExtra != null && !whereStagingExtra.trim().isEmpty())
                ? " " + whereStagingExtra : "";

        // Calcula a nova ID gerando as IDs sequencialmente sem saltos (mesmo se o staging tiver saltado IDs)
        exec(stmt, "INSERT INTO `" + STG + "`.`" + mapTable + "` (id_antiga, id_nova)" +
                " SELECT s.id AS id_antiga, (@OFF_" + tabela + " := @OFF_" + tabela + " + 1) AS id_nova" +
                " FROM `" + STG + "`.`" + tabela + "` s" + where + " ORDER BY s.id");

        // Colunas comuns
        exec(stmt, "SET @cols_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE() AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        exec(stmt, "SET @sel_common_" + tabela + " := (" +
                "SELECT GROUP_CONCAT(CONCAT('s.`', d.COLUMN_NAME, '`') ORDER BY d.ORDINAL_POSITION)" +
                " FROM INFORMATION_SCHEMA.COLUMNS d" +
                " JOIN INFORMATION_SCHEMA.COLUMNS s" +
                "   ON s.TABLE_SCHEMA='" + STG + "' AND s.TABLE_NAME='" + tabela + "'" +
                "  AND s.COLUMN_NAME=d.COLUMN_NAME" +
                " WHERE d.TABLE_SCHEMA=DATABASE() AND d.TABLE_NAME='" + tabela + "'" +
                "   AND d.COLUMN_NAME <> 'id')");

        if (forceEmpresa1) {
            exec(stmt, "SET @sel_common_" + tabela + " := REPLACE(@sel_common_" + tabela +
                    ", 's.`id_empresa`', '1 AS `id_empresa`')");
        }

        exec(stmt, "SET @sql_ins_" + tabela + " := IF(@cols_common_" + tabela +
                " IS NULL OR @cols_common_" + tabela + " = ''," +
                " 'SELECT ''ERRO: " + tabela + " sem colunas comuns'''," +
                " CONCAT(" +
                "  'INSERT INTO `" + tabela + "` (`id`, ', @cols_common_" + tabela + ", ') '," +
                "  'SELECT m.id_nova, ', @sel_common_" + tabela + ", ' '," +
                "  'FROM `" + STG + "`.`" + tabela + "` s '," +
                "  'JOIN `" + STG + "`.`" + mapTable + "` m ON m.id_antiga = s.id '," +
                "  'LEFT JOIN `" + tabela + "` d ON d.id = m.id_nova '," +
                "  'WHERE d.id IS NULL'" +
                " )" +
                ")");
        exec(stmt, "PREPARE stmt_ins_" + tabela + " FROM @sql_ins_" + tabela);
        exec(stmt, "EXECUTE stmt_ins_" + tabela);
        exec(stmt, "DEALLOCATE PREPARE stmt_ins_" + tabela);

        // Conta inseridos (linhas no mapa que não existiam no destino antes)
        int inserted = queryInt(stmt,
                "SELECT COUNT(*) FROM `" + STG + "`.`" + mapTable + "`", 0);
        report.addInserido(tabela + " (novos)", inserted);
        logger.accept("     " + tabela + " → " + inserted + " inseridos.");

        // Garante que o banco de dados auto atualize seu auto increment adequadamente após inserirmos os IDs de forma explícita
        long autoIncReset = (baseOffsetId > 0) ? baseOffsetId : 1;
        exec(stmt, "ALTER TABLE `" + tabela + "` AUTO_INCREMENT = " + autoIncReset);
    }

    // -------------------------------------------------------------------------

    /**
     * Atualiza uma coluna FK dentro do staging usando a tabela _map_
     * correspondente, somente se a coluna existir na tabela filho.
     */
    private void remapFkSeExistir(Statement stmt, String tabela, String colunaFk,
            String mapTable) throws SQLException {

        String varHasCol = "@hascol_" + tabela + "_" + colunaFk;

        exec(stmt, "SET " + varHasCol + " := (" +
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS" +
                " WHERE TABLE_SCHEMA='" + STG + "'" +
                "   AND TABLE_NAME='" + tabela + "'" +
                "   AND COLUMN_NAME='" + colunaFk + "')");

        exec(stmt, "SET @sql_remap_" + tabela + "_" + colunaFk + " := IF(" + varHasCol + " > 0," +
                " CONCAT(" +
                "  'UPDATE `" + STG + "`.`" + tabela + "` t '," +
                "  'JOIN `" + STG + "`.`" + mapTable + "` m ON m.id_antiga = t.`" + colunaFk + "` '," +
                "  'SET t.`" + colunaFk + "` = m.id_nova '," +
                "  'WHERE t.`" + colunaFk + "` IS NOT NULL'" +
                " )," +
                " 'SELECT 1'" +
                ")");

        exec(stmt, "PREPARE stmt_remap_" + tabela + "_" + colunaFk +
                " FROM @sql_remap_" + tabela + "_" + colunaFk);
        exec(stmt, "EXECUTE stmt_remap_" + tabela + "_" + colunaFk);
        exec(stmt, "DEALLOCATE PREPARE stmt_remap_" + tabela + "_" + colunaFk);
    }

    // =========================================================================
    // Utilitários
    // =========================================================================

    private void exec(Statement stmt, String sql) throws SQLException {
        try {
            stmt.execute(sql);
        } catch (SQLException ex) {
            // Erros de "tabela não encontrada" no staging são tratados como avisos
            if (ex.getErrorCode() == 1146 || ex.getErrorCode() == 1064) {
                report.addAviso("SQL ignorado (" + ex.getErrorCode() + "): "
                        + sql.substring(0, Math.min(sql.length(), 100)));
            } else {
                throw ex;
            }
        }
    }

    private void execUpdate(Statement stmt, String sql) throws SQLException {
        try {
            stmt.execute(sql);
        } catch (SQLException ex) {
            report.addAviso("Update ignorado: " + ex.getMessage());
        }
    }

    private boolean tabelaExisteNoStaging(Statement stmt, String tabela) {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES" +
                " WHERE TABLE_SCHEMA='" + STG + "' AND TABLE_NAME='" + tabela + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException ex) {
            return false;
        }
    }

    private int queryInt(Statement stmt, String sql, int defaultVal) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : defaultVal;
        } catch (SQLException ex) {
            return defaultVal;
        }
    }
}
