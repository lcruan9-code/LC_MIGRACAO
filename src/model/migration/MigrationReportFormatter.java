package model.migration;

import java.util.Map;

/**
 * Formata um MigrationReport em texto legível para exibição na UI.
 * Separado de MigrationReport para manter o modelo de dados limpo.
 */
public final class MigrationReportFormatter {

    private MigrationReportFormatter() {}

    public static String format(MigrationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("==========================================================\n");
        sb.append("  RELATORIO -- MOTOR DE MIGRACAO SEGURO\n");
        sb.append("==========================================================\n\n");

        sb.append("-- FASES EXECUTADAS ------------------------------------------\n");
        for (String f : report.getFases()) {
            sb.append("  ").append(f).append("\n");
        }

        sb.append("\n-- REGISTROS INSERIDOS ---------------------------------------\n");
        if (report.getInseridos().isEmpty()) {
            sb.append("  (Nenhuma tabela processada)\n");
        } else {
            for (Map.Entry<String, Integer> e : report.getInseridos().entrySet()) {
                sb.append(String.format("  %-30s %d linha(s)\n", e.getKey(), e.getValue()));
            }
        }

        if (!report.getAvisos().isEmpty()) {
            sb.append("\n-- AVISOS ----------------------------------------------------\n");
            for (String a : report.getAvisos()) {
                sb.append("  [AVISO] ").append(a).append("\n");
            }
        }

        if (!report.getErros().isEmpty()) {
            sb.append("\n-- ERROS -----------------------------------------------------\n");
            for (String e : report.getErros()) {
                sb.append("  [ERRO]  ").append(e).append("\n");
            }
        }

        sb.append("\n==========================================================\n");
        sb.append("  RESULTADO: ").append(report.isSucesso() ? "[SUCESSO]" : "[FALHA]").append("\n");
        sb.append("==========================================================\n");
        return sb.toString();
    }
}
