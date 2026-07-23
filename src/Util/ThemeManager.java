package Util;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * Versão enxuta do ThemeManager do produto LC, portada para o LC_MIGRACAO.
 *
 * <p>Mantém a mesma paleta e o mesmo {@link #apply()} (FlatLaf + fontes + cores),
 * além dos utilitários de componente usados pela tela ({@link #botaoPrimario},
 * {@link #estilizarScrollPane}, {@link #campoTextoSomenteLeitura}). Os cell
 * renderers de tabela do produto original foram removidos por dependerem de
 * classes de domínio (Receber, Pagar, etc.) que não existem neste projeto.
 */
public final class ThemeManager {

    public enum Tema { CLARO, ESCURO }

    public enum ThemeCores {
        FUNDO_PADRAO, FUNDO_CONTEUDO, FUNDO_CAMPOS,
        ROXO_PRIMARIO, ROXO_SECUNDARIO,
        TEXT, TEXT_SECUNDARIO, SEPARADOR
    }

    private static final String PREF_TEMA = "tema";
    private static Tema temaAtual;

    static {
        String salvo = java.util.prefs.Preferences
                .userNodeForPackage(ThemeManager.class)
                .get(PREF_TEMA, "CLARO");
        try {
            temaAtual = Tema.valueOf(salvo);
        } catch (IllegalArgumentException e) {
            temaAtual = Tema.CLARO;
        }
        carregarCores();
    }

    // ROXO
    public static Color ROXO_PRIMARIO;
    public static Color ROXO_SECUNDARIO;
    private static Color ROXO_HOVER;
    private static Color ROXO_PRESS;
    private static Color ROXO_TERCIARIO;
    private static Color ROXO_QUADRICIARIO;
    private static Color ROXO_BOTAO_HOVER;
    private static Color ROXO_SELECIONADO;
    private static Color ROXO_BORDA;

    // FUNDOS
    public static Color FUNDO_PADRAO;
    private static Color FUNDO_CONTEUDO;
    private static Color FUNDO_ABAS;
    public static Color FUNDO_CAMPOS;
    private static Color FUNDO_DESABILITADO;

    // TEXTOS
    public static Color TEXT;
    public static Color TEXT_SECUNDARIO;
    public static Color TEXT_TERCIARIO;
    private static Color TEXT_DESABILITADO;
    private static Color TEXT_INATIVO;
    public static final Color TEXT_SOBRE_PRIMARIO = new Color(236, 239, 243);

    // BOTÃO
    private static Color BOTAO_PRESS;
    private static Color BOTAO_BORDA;
    private static Color BOTAO_DESABILITADO;

    // SELEÇÃO
    private static Color SELECTION_FG;
    private static Color SELECTION_BG;

    // TABELA / SEPARADORES
    private static Color TABLE_ALT_ROW;
    private static Color TABLE_HEADER_BG;
    public static Color SEPARADOR;

    // CHECKBOX / SCROLL
    private static Color CB_MARCADO;
    private static Color SCROLL_TRACK;
    private static Color SCROLL_THUMB;

    private ThemeManager() {
    }

    public static Tema getTema() {
        return temaAtual;
    }

    public static Color getCor(ThemeCores cor) {
        switch (cor) {
            case FUNDO_PADRAO:    return FUNDO_PADRAO;
            case FUNDO_CONTEUDO:  return FUNDO_CONTEUDO;
            case FUNDO_CAMPOS:    return FUNDO_CAMPOS;
            case ROXO_PRIMARIO:   return ROXO_PRIMARIO;
            case ROXO_SECUNDARIO: return ROXO_SECUNDARIO;
            case TEXT:            return TEXT;
            case TEXT_SECUNDARIO: return TEXT_SECUNDARIO;
            case SEPARADOR:       return SEPARADOR;
            default:              return Color.MAGENTA;
        }
    }

    private static void carregarCores() {
        if (temaAtual == Tema.ESCURO) {
            carregarCoresEscuro();
        } else {
            carregarCoresClaro();
        }
    }

    private static void carregarCoresEscuro() {
        ROXO_PRIMARIO = new Color(85, 60, 150);
        ROXO_SECUNDARIO = new Color(110, 85, 185);
        ROXO_HOVER = new Color(100, 72, 165);
        ROXO_PRESS = new Color(68, 48, 120);
        ROXO_TERCIARIO = new Color(38, 30, 68);
        ROXO_QUADRICIARIO = new Color(32, 25, 58);
        ROXO_BOTAO_HOVER = new Color(48, 38, 82);
        ROXO_SELECIONADO = new Color(55, 44, 95);
        ROXO_BORDA = new Color(62, 50, 108);

        FUNDO_PADRAO = new Color(28, 30, 34);
        FUNDO_CONTEUDO = new Color(33, 35, 41);
        FUNDO_ABAS = new Color(34, 36, 42);
        FUNDO_CAMPOS = new Color(44, 46, 52);
        FUNDO_DESABILITADO = new Color(40, 42, 48);

        TEXT = new Color(210, 212, 216);
        TEXT_SECUNDARIO = new Color(155, 158, 165);
        TEXT_TERCIARIO = new Color(95, 98, 106);
        TEXT_DESABILITADO = new Color(90, 93, 102);
        TEXT_INATIVO = new Color(175, 178, 185);

        BOTAO_PRESS = new Color(58, 60, 68);
        BOTAO_BORDA = new Color(62, 65, 75);
        BOTAO_DESABILITADO = new Color(48, 50, 58);

        SELECTION_FG = new Color(210, 215, 225);
        SELECTION_BG = new Color(72, 58, 110);

        TABLE_ALT_ROW = new Color(42, 44, 52);
        TABLE_HEADER_BG = new Color(38, 40, 47);
        SEPARADOR = new Color(52, 55, 63);

        CB_MARCADO = new Color(155, 130, 210);
        SCROLL_TRACK = new Color(48, 38, 82);
        SCROLL_THUMB = ROXO_SECUNDARIO;
    }

    private static void carregarCoresClaro() {
        ROXO_PRIMARIO = new Color(63, 42, 115);
        ROXO_SECUNDARIO = new Color(92, 68, 164);
        ROXO_HOVER = new Color(78, 55, 135);
        ROXO_PRESS = new Color(52, 36, 95);
        ROXO_TERCIARIO = new Color(222, 228, 245);
        ROXO_QUADRICIARIO = new Color(235, 238, 247);
        ROXO_BOTAO_HOVER = new Color(221, 222, 240);
        ROXO_SELECIONADO = new Color(210, 202, 228);
        ROXO_BORDA = new Color(205, 198, 230);

        FUNDO_PADRAO = new Color(245, 247, 249);
        FUNDO_CONTEUDO = new Color(240, 243, 248);
        FUNDO_ABAS = new Color(240, 242, 244);
        FUNDO_CAMPOS = new Color(255, 255, 255);
        FUNDO_DESABILITADO = new Color(243, 245, 248);

        TEXT = new Color(75, 75, 75);
        TEXT_SECUNDARIO = new Color(120, 120, 120);
        TEXT_TERCIARIO = new Color(190, 190, 190);
        TEXT_DESABILITADO = new Color(155, 160, 170);
        TEXT_INATIVO = new Color(85, 85, 85);

        BOTAO_PRESS = new Color(230, 230, 230);
        BOTAO_BORDA = new Color(168, 176, 190);
        BOTAO_DESABILITADO = new Color(241, 243, 246);

        SELECTION_FG = TEXT;
        SELECTION_BG = ROXO_TERCIARIO;

        TABLE_ALT_ROW = new Color(248, 249, 251);
        TABLE_HEADER_BG = new Color(250, 251, 253);
        SEPARADOR = new Color(220, 223, 228);

        CB_MARCADO = new Color(155, 130, 210);
        SCROLL_TRACK = new Color(175, 165, 205);
        SCROLL_THUMB = ROXO_SECUNDARIO;
    }

    public static void apply() {
        try {
            if (temaAtual == Tema.ESCURO) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }

            Font fontLabel = new Font("Segoe UI", Font.PLAIN, 12);
            Font fontField = new Font("Segoe UI", Font.PLAIN, 13);
            Font fontButton = new Font("Segoe UI", Font.PLAIN, 14);
            Font fontTextArea = new Font("Segoe UI", Font.PLAIN, 15);
            Font fontTable = new Font("Segoe UI", Font.PLAIN, 13);
            Font fontHeader = new Font("Segoe UI", Font.PLAIN, 12);

            UIManager.put("Label.font", fontLabel);
            UIManager.put("TextField.font", fontField);
            UIManager.put("FormattedTextField.font", fontField);
            UIManager.put("PasswordField.font", fontField);
            UIManager.put("ComboBox.font", fontField);
            UIManager.put("Button.font", fontButton);
            UIManager.put("ToggleButton.font", fontButton);
            UIManager.put("TextArea.font", fontTextArea);
            UIManager.put("CheckBox.font", fontLabel);
            UIManager.put("Table.font", fontTable);
            UIManager.put("TableHeader.font", fontHeader);

            UIManager.put("defaultRowHeight", 28);

            UIManager.put("Component.accentColor", ROXO_PRIMARIO);
            UIManager.put("Component.borderColor", BOTAO_BORDA);
            UIManager.put("Component.focusedBorderColor", ROXO_SECUNDARIO);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.focusColor", new Color(0, 0, 0, 0));
            UIManager.put("Component.minimumHeight", 30);

            UIManager.put("TextComponent.caretColor", ROXO_PRIMARIO);
            UIManager.put("TextComponent.focusedBorderColor", ROXO_SECUNDARIO);
            UIManager.put("TextComponent.selectionBackground", SELECTION_BG);
            UIManager.put("TextComponent.selectionForeground", SELECTION_FG);
            UIManager.put("TextComponent.margin", new Insets(2, 5, 2, 5));

            UIManager.put("CheckBox.icon.background", FUNDO_CAMPOS);
            UIManager.put("CheckBox.icon.focusedBackground", CB_MARCADO);
            UIManager.put("CheckBox.icon.hoverBackground", CB_MARCADO);
            UIManager.put("CheckBox.icon.hoverBorderColor", CB_MARCADO);
            UIManager.put("CheckBox.icon.borderColor", BOTAO_BORDA);
            UIManager.put("CheckBox.icon.selectedBackground", ROXO_PRIMARIO);
            UIManager.put("CheckBox.icon.selectedBorderColor", ROXO_SECUNDARIO);
            UIManager.put("CheckBox.icon.checkmarkColor", Color.WHITE);
            UIManager.put("CheckBox.foreground", TEXT);

            UIManager.put("Button.borderColor", BOTAO_BORDA);
            UIManager.put("Button.focusedBorderColor", ROXO_SECUNDARIO);
            UIManager.put("Button.focusColor", ROXO_PRIMARIO);
            UIManager.put("Button.background", FUNDO_CAMPOS);
            UIManager.put("Button.foreground", TEXT);
            UIManager.put("Button.hoverBackground", ROXO_BOTAO_HOVER);
            UIManager.put("Button.pressedBackground", BOTAO_PRESS);
            UIManager.put("Button.disabledBackground", BOTAO_DESABILITADO);
            UIManager.put("Button.disabledText", TEXT_DESABILITADO);

            UIManager.put("TextField.focusedBorderColor", ROXO_SECUNDARIO);
            UIManager.put("TextField.background", FUNDO_CAMPOS);
            UIManager.put("TextField.foreground", TEXT);
            UIManager.put("TextField.disabledBackground", FUNDO_DESABILITADO);
            UIManager.put("TextField.disabledForeground", TEXT_DESABILITADO);

            UIManager.put("ComboBox.background", FUNDO_CAMPOS);
            UIManager.put("ComboBox.selectionBackground", SELECTION_BG);
            UIManager.put("ComboBox.selectionForeground", SELECTION_FG);
            UIManager.put("ComboBox.foreground", TEXT);
            UIManager.put("ComboBox.focusedBorderColor", ROXO_SECUNDARIO);

            UIManager.put("List.background", FUNDO_CAMPOS);
            UIManager.put("List.foreground", TEXT);
            UIManager.put("List.selectionBackground", SELECTION_BG);
            UIManager.put("List.selectionForeground", SELECTION_FG);

            UIManager.put("Table.background", FUNDO_CAMPOS);
            UIManager.put("Table.selectionBackground", SELECTION_BG);
            UIManager.put("Table.selectionForeground", SELECTION_FG);
            UIManager.put("Table.foreground", TEXT);
            UIManager.put("Table.alternateRowColor", TABLE_ALT_ROW);
            UIManager.put("TableHeader.separatorColor", SEPARADOR);
            UIManager.put("TableHeader.foreground", TEXT);
            UIManager.put("TableHeader.background", TABLE_HEADER_BG);

            UIManager.put("Separator.foreground", SEPARADOR);
            UIManager.put("Separator.background", SEPARADOR);
            UIManager.put("Separator.height", 1);

            UIManager.put("ScrollPane.borderColor", BOTAO_BORDA);
            UIManager.put("TextArea.background", FUNDO_CAMPOS);
            UIManager.put("TextArea.foreground", TEXT);
            UIManager.put("Panel.background", FUNDO_PADRAO);
            UIManager.put("RootPane.background", FUNDO_PADRAO);
            UIManager.put("Label.foreground", TEXT);
            UIManager.put("TabbedPane.background", FUNDO_ABAS);

            UIManager.put("Component.arc", 7);
            UIManager.put("ComboBox.arc", 7);
            UIManager.put("Button.arc", 7);
            UIManager.put("TextComponent.arc", 7);
            UIManager.put("ScrollPane.arc", 7);

            System.setProperty("flatlaf.useWindowDecorations", "true");
            UIManager.put("TitlePane.font", new Font("Segoe UI", Font.PLAIN, 18));
            UIManager.put("TitlePane.height", 40);
            UIManager.put("TitlePane.background", FUNDO_PADRAO);
            UIManager.put("TitlePane.foreground", TEXT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Botão de maior impacto da tela (1 por tela, o mais à direita). */
    public static void botaoPrimario(JButton btn) {
        btn.putClientProperty(FlatClientProperties.STYLE,
                "background: rgb(" + ROXO_PRIMARIO.getRed() + "," + ROXO_PRIMARIO.getGreen() + "," + ROXO_PRIMARIO.getBlue() + ");"
                + "foreground: rgb(" + TEXT_SOBRE_PRIMARIO.getRed() + "," + TEXT_SOBRE_PRIMARIO.getGreen() + "," + TEXT_SOBRE_PRIMARIO.getBlue() + ");"
                + "hoverBackground: rgb(" + ROXO_HOVER.getRed() + "," + ROXO_HOVER.getGreen() + "," + ROXO_HOVER.getBlue() + ");"
                + "pressedBackground: rgb(" + ROXO_PRESS.getRed() + "," + ROXO_PRESS.getGreen() + "," + ROXO_PRESS.getBlue() + ");"
        );
    }

    public static void estilizarScrollPane(JScrollPane scrollPane) {
        String track = String.format("rgb(%d,%d,%d)", SCROLL_TRACK.getRed(), SCROLL_TRACK.getGreen(), SCROLL_TRACK.getBlue());
        String thumb = String.format("rgb(%d,%d,%d)", SCROLL_THUMB.getRed(), SCROLL_THUMB.getGreen(), SCROLL_THUMB.getBlue());
        String style = "width: 8;"
                + "thumbArc: 999;"
                + "trackArc: 999;"
                + "track: " + track + ";"
                + "thumb: " + thumb + ";"
                + "hoverThumbColor: " + thumb + ";"
                + "pressedThumbColor: " + thumb + ";";
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
        scrollPane.getHorizontalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
    }

    /** Aplicar em TODO campo não editável. */
    public static void campoTextoSomenteLeitura(JTextField txt) {
        txt.setEditable(false);
        txt.setEnabled(true);
        txt.setCursor(Cursor.getDefaultCursor());
        String bg = rgb(ROXO_TERCIARIO);
        String fg = rgb(TEXT_INATIVO);
        String border = rgb(ROXO_BORDA);
        txt.putClientProperty(FlatClientProperties.STYLE,
                "background: " + bg + ";"
                + "focusedBackground: " + bg + ";"
                + "foreground: " + fg + ";"
                + "borderColor: " + border + ";"
                + "focusedBorderColor: " + border + ";"
        );
        txt.setSelectionColor(ROXO_SELECIONADO);
        txt.setSelectedTextColor(TEXT);
    }

    public static void painelCorPrimaria(JPanel painel) {
        painel.putClientProperty(FlatClientProperties.STYLE, "background: " + rgb(ROXO_PRIMARIO) + ";");
    }

    /**
     * Borda cinza de 1px ao redor da janela — aplicar em TODA janela
     * undecorated (modelo sem barra), para destacá-la do fundo.
     * Uso: {@code ThemeManager.bordaJanela(getRootPane());} no setUX().
     */
    public static void bordaJanela(javax.swing.JRootPane rootPane) {
        rootPane.setBorder(BorderFactory.createLineBorder(BOTAO_BORDA, 1));
    }

    private static String rgb(Color c) {
        return "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
    }
}
