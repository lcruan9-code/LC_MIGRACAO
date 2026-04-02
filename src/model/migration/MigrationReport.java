package model.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Armazena o resultado completo de uma execução do Motor de Migração Seguro.
 * Exibido no jTextArea1 ao final da operação.
 */
public class MigrationReport {

    private final List<String> fases = new ArrayList<String>();
    private final Map<String, Integer> inseridos = new LinkedHashMap<String, Integer>();
    private final List<String> avisos = new ArrayList<String>();
    private final List<String> erros  = new ArrayList<String>();
    private boolean sucesso = false;

    // -------------------------------------------------------------------------
    // Registro de progresso
    // -------------------------------------------------------------------------

    public void addFase(String msg) {
        fases.add(msg);
    }

    public void addInserido(String tabela, int qtd) {
        inseridos.put(tabela, qtd);
    }

    public void addAviso(String msg) {
        avisos.add(msg);
    }

    public void addErro(String msg) {
        erros.add(msg);
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    // -------------------------------------------------------------------------
    // Leitura dos dados (para MigrationReportFormatter)
    // -------------------------------------------------------------------------

    public List<String> getFases() {
        return Collections.unmodifiableList(fases);
    }

    public Map<String, Integer> getInseridos() {
        return Collections.unmodifiableMap(inseridos);
    }

    public List<String> getAvisos() {
        return Collections.unmodifiableList(avisos);
    }

    public List<String> getErros() {
        return Collections.unmodifiableList(erros);
    }

    // -------------------------------------------------------------------------
    // Geração do relatório final em texto
    // -------------------------------------------------------------------------

    public String toTexto() {
        return MigrationReportFormatter.format(this);
    }
}
