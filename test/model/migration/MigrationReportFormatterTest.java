package model.migration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigrationReportFormatterTest {

    @Test
    public void emptyReport_showsNoTableProcessedAndFalha() {
        String out = MigrationReportFormatter.format(new MigrationReport());

        assertTrue(out.contains("RELATORIO -- MOTOR DE MIGRACAO SEGURO"));
        assertTrue(out.contains("(Nenhuma tabela processada)"));
        assertTrue(out.contains("[FALHA]"));
        assertFalse(out.contains("[SUCESSO]"));
        // No avisos / erros sections when none were added
        assertFalse(out.contains("-- AVISOS"));
        assertFalse(out.contains("-- ERROS"));
    }

    @Test
    public void successReport_showsSucessoMarker() {
        MigrationReport r = new MigrationReport();
        r.setSucesso(true);

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("[SUCESSO]"));
        assertFalse(out.contains("[FALHA]"));
    }

    @Test
    public void report_listsFasesInOrder() {
        MigrationReport r = new MigrationReport();
        r.addFase("[FASE 1] Staging criado");
        r.addFase("[LIMPEZA] removido");

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("[FASE 1] Staging criado"));
        assertTrue(out.contains("[LIMPEZA] removido"));
        assertTrue(out.indexOf("[FASE 1]") < out.indexOf("[LIMPEZA]"));
    }

    @Test
    public void report_formatsInsertedCountsWithLabel() {
        MigrationReport r = new MigrationReport();
        r.addInserido("produto", 123);

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("produto"));
        assertTrue(out.contains("123 linha(s)"));
        assertFalse(out.contains("(Nenhuma tabela processada)"));
    }

    @Test
    public void report_rendersAvisosSectionWhenPresent() {
        MigrationReport r = new MigrationReport();
        r.addAviso("tabela X pulada");

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("-- AVISOS"));
        assertTrue(out.contains("[AVISO] tabela X pulada"));
    }

    @Test
    public void report_rendersErrosSectionWhenPresent() {
        MigrationReport r = new MigrationReport();
        r.addErro("conexao perdida");

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("-- ERROS"));
        assertTrue(out.contains("[ERRO]  conexao perdida"));
    }

    @Test
    public void report_withEverything_containsAllSections() {
        MigrationReport r = new MigrationReport();
        r.addFase("[FASE 1] ok");
        r.addInserido("cliente", 5);
        r.addAviso("aviso 1");
        r.addErro("erro 1");
        r.setSucesso(true);

        String out = MigrationReportFormatter.format(r);

        assertTrue(out.contains("-- FASES EXECUTADAS"));
        assertTrue(out.contains("-- REGISTROS INSERIDOS"));
        assertTrue(out.contains("-- AVISOS"));
        assertTrue(out.contains("-- ERROS"));
        assertTrue(out.contains("[SUCESSO]"));
    }
}
