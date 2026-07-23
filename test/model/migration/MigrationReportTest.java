package model.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MigrationReportTest {

    @Test
    public void newReport_startsEmptyAndNotSucesso() {
        MigrationReport r = new MigrationReport();

        assertFalse(r.isSucesso());
        assertTrue(r.getFases().isEmpty());
        assertTrue(r.getInseridos().isEmpty());
        assertTrue(r.getAvisos().isEmpty());
        assertTrue(r.getErros().isEmpty());
    }

    @Test
    public void addFase_accumulatesInOrder() {
        MigrationReport r = new MigrationReport();
        r.addFase("um");
        r.addFase("dois");

        List<String> fases = r.getFases();
        assertEquals(2, fases.size());
        assertEquals("um", fases.get(0));
        assertEquals("dois", fases.get(1));
    }

    @Test
    public void addInserido_keepsInsertionOrderAndValue() {
        MigrationReport r = new MigrationReport();
        r.addInserido("produto", 42);
        r.addInserido("cliente", 7);

        Map<String, Integer> inseridos = r.getInseridos();
        assertEquals(2, inseridos.size());
        assertEquals(Integer.valueOf(42), inseridos.get("produto"));
        assertEquals(Integer.valueOf(7), inseridos.get("cliente"));
        // LinkedHashMap preserves order
        assertEquals("produto", inseridos.keySet().iterator().next());
    }

    @Test
    public void addInserido_sameTableOverwritesPreviousCount() {
        MigrationReport r = new MigrationReport();
        r.addInserido("produto", 1);
        r.addInserido("produto", 99);

        assertEquals(1, r.getInseridos().size());
        assertEquals(Integer.valueOf(99), r.getInseridos().get("produto"));
    }

    @Test
    public void addAviso_andAddErro_accumulate() {
        MigrationReport r = new MigrationReport();
        r.addAviso("cuidado");
        r.addErro("falhou");

        assertEquals(1, r.getAvisos().size());
        assertEquals("cuidado", r.getAvisos().get(0));
        assertEquals(1, r.getErros().size());
        assertEquals("falhou", r.getErros().get(0));
    }

    @Test
    public void setSucesso_togglesFlag() {
        MigrationReport r = new MigrationReport();
        r.setSucesso(true);
        assertTrue(r.isSucesso());
        r.setSucesso(false);
        assertFalse(r.isSucesso());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getFases_isUnmodifiable() {
        new MigrationReport().getFases().add("x");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getInseridos_isUnmodifiable() {
        new MigrationReport().getInseridos().put("x", 1);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getAvisos_isUnmodifiable() {
        new MigrationReport().getAvisos().add("x");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getErros_isUnmodifiable() {
        new MigrationReport().getErros().add("x");
    }

    @Test
    public void toTexto_delegatesToFormatter() {
        MigrationReport r = new MigrationReport();
        r.addFase("[FASE 1] ok");
        String texto = r.toTexto();

        assertTrue(texto.contains("RELATORIO"));
        assertTrue(texto.contains("[FASE 1] ok"));
    }
}
