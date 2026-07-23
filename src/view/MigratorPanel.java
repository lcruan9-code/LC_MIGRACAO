package view;

import com.formdev.flatlaf.FlatLightLaf;
import config.AppConfig;
import model.DatabaseManager;
import model.migration.MigrationEngine;
import model.migration.MigrationReport;
import model.migration.SchemaBackup;
import Util.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MigratorPanel — JFrame único com tela + lógica de migração.
 * Substitui MainAppFrame + MigratorPanel antigos.
 *
 * ── CORES (edite aqui para mudar o visual) ──────────────────────────────────
 *   COR_PRIMARIA = #5C2380 (roxo LC Sistemas)
 * ── ATALHOS DO DESIGN TAB ────────────────────────────────────────────────────
 *   jPanelHeader = faixa roxa do topo (arrastável)
 *   jComboBox1   = seletor de banco destino
 *   jTextFieldFilePath = caminho do arquivo carregado
 *   jButton3     = Carregar Arquivo
 *   jButton2     = Executar Migração (primário)
 *   jButton1     = Sair
 *   jTextArea1   = log de execução
 */
public class MigratorPanel extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(MigratorPanel.class);

    // ── Dependências ─────────────────────────────────────────────────────────
    private AppConfig       appConfig;
    private DatabaseManager dbManager;
    private File            loadedSqlFile;
    private File            currentTempRarDir;

    // ── Arrastar janela (undecorated) ─────────────────────────────────────────
    private int mouseX, mouseY;


    // ── Entrada da aplicação ──────────────────────────────────────────────────
    public static void main(String[] args) {
        aplicarTema();
        EventQueue.invokeLater(() -> new MigratorPanel().setVisible(true));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Construtor
    // ────────────────────────────────────────────────────────────────────────
    public MigratorPanel() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setTitle("Migração de Dados");
        setUndecorated(true); // modelo sem barra: título fonte 17 + separador fazem o papel de cabeçalho

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { sairMigracao(); }
        });

        initComponents();
        setUX();
        setLocationRelativeTo(null);

        // Sem barra de título: arrastar pela área do título e espaços vazios
        addDragListeners(jLabel2);
        addDragListeners((JComponent) getContentPane());

        // Inicializa banco
        appConfig = new AppConfig();
        dbManager = new DatabaseManager(appConfig);
        inicializarConexao();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Estilização pós-initComponents (fora do código gerado)
    // ────────────────────────────────────────────────────────────────────────
    private void setUX() {
        ThemeManager.botaoPrimario(jButton2);      // botão primário (mais à direita)
        ThemeManager.estilizarScrollPane(jScrollPane1);
        ThemeManager.bordaJanela(getRootPane());   // borda cinza da janela sem barra
    }

    // ────────────────────────────────────────────────────────────────────────
    // Arrastar janela sem barra de título
    // ────────────────────────────────────────────────────────────────────────
    private void addDragListeners(JComponent c) {
        c.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // offset do clique em relação ao canto da janela (independe do componente)
                mouseX = e.getXOnScreen() - getX();
                mouseY = e.getYOnScreen() - getY();
            }
        });
        c.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                setLocation(e.getXOnScreen() - mouseX, e.getYOnScreen() - mouseY);
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // initComponents — gerado/gerenciado pelo NetBeans Design tab
    // ────────────────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel2 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jLabelFilePath1 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox();
        jLabelFilePath = new javax.swing.JLabel();
        jTextFieldFilePath = new javax.swing.JTextField();
        jButton3 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        jLabel2.setFont(new java.awt.Font("Segoe UI", 0, 17)); // NOI18N
        jLabel2.setText("Migração de Dados");

        jLabelFilePath1.setText("Banco destino:");

        jComboBox1.setMaximumRowCount(12);
        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "SELECIONE" }));

        jLabelFilePath.setText("Arquivo SQL:");

        jTextFieldFilePath.setEditable(false);

        jButton3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/imagens/zoom.png"))); // NOI18N
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        jButton1.setText("Sair");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setBackground(new java.awt.Color(63, 42, 115));
        jButton2.setForeground(new java.awt.Color(236, 239, 243));
        jButton2.setText("Executar");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jSeparator1)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabelFilePath1, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox1, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabelFilePath, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextFieldFilePath)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 576, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelFilePath1)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelFilePath)
                    .addComponent(jTextFieldFilePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // ────────────────────────────────────────────────────────────────────────
    // Event handlers (GEN-FIRST / GEN-LAST)
    // ────────────────────────────────────────────────────────────────────────

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        sairMigracao();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        executarMigracao();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        carregarArquivo();
    }//GEN-LAST:event_jButton3ActionPerformed

    // ────────────────────────────────────────────────────────────────────────
    // Inicialização do banco
    // ────────────────────────────────────────────────────────────────────────

    private void inicializarConexao() {
        try {
            dbManager.connect();
            populateDatabaseComboBox(dbManager.getAllDatabases());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Erro ao conectar: " + e.getMessage() +
                "\nVerifique as configurações em 'rede.txt'.",
                "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void populateDatabaseComboBox(List<String> databases) {
        jComboBox1.removeAllItems();
        jComboBox1.addItem("SELECIONE");
        if (databases != null) for (String db : databases) jComboBox1.addItem(db);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Migração
    // ────────────────────────────────────────────────────────────────────────

    private void executarMigracao() {
        if (loadedSqlFile == null) {
            JOptionPane.showMessageDialog(this, "Carregue um arquivo SQL antes de executar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String selectedDb = (String) jComboBox1.getSelectedItem();
        if (selectedDb == null || selectedDb.equals("SELECIONE")) {
            JOptionPane.showMessageDialog(this, "Selecione um banco de destino.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String finalDb   = selectedDb;
        final File   finalFile = loadedSqlFile;
        final MigratorPanel self = this;

        // Aviso: será feito um backup completo antes de migrar
        int opcao = JOptionPane.showConfirmDialog(this,
                "Antes da migração será gerado um BACKUP COMPLETO do banco '" + finalDb + "'\n"
                + "na pasta do sistema. Dependendo do tamanho, pode levar alguns minutos.\n\n"
                + "Deseja continuar?",
                "Backup antes da migração", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (opcao != JOptionPane.OK_OPTION) {
            jTextArea1.append("[CANCELADO] Operação cancelada pelo usuário (backup não realizado).\n");
            return;
        }

        setControlesHabilitados(false);
        jTextArea1.setText("[BACKUP] Preparando backup do banco '" + finalDb + "'...\n");

        // Popup modal com barra de progresso (indeterminada) durante backup + migração
        final JDialog progresso = new JDialog(this, "Processando", true);
        final JLabel lblStatus = new JLabel("Gerando backup do banco de destino...");
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        JPanel painel = new JPanel(new BorderLayout(10, 12));
        painel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        painel.add(lblStatus, BorderLayout.NORTH);
        painel.add(barra, BorderLayout.CENTER);
        progresso.getContentPane().add(painel);
        progresso.pack();
        progresso.setSize(Math.max(380, progresso.getWidth()), progresso.getHeight());
        progresso.setLocationRelativeTo(this);
        progresso.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        SwingWorker<MigrationReport, String> worker = new SwingWorker<MigrationReport, String>() {
            @Override protected MigrationReport doInBackground() throws Exception {
                // ── 1) Backup completo do schema de destino (ponto de restauração) ──
                File jarLoc = new File(MigratorPanel.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                File pastaLc = jarLoc.getParentFile(); // pasta do lc_migracao.jar (pasta da LC)
                String carimbo = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                SchemaBackup backup = new SchemaBackup(
                        appConfig.getDbHost(), appConfig.getDbPort(),
                        appConfig.getDbUser(), appConfig.getDbPassword(),
                        finalDb, pastaLc, msg -> publish(msg));
                backup.executar(carimbo); // lança exceção se falhar → aborta a migração

                // ── 2) Migração propriamente dita ──
                publish("[MIGRAÇÃO] Iniciando merge seguro...");
                Connection conn = dbManager.getConnection();
                MigrationEngine engine = new MigrationEngine(conn, finalDb, finalFile, msg -> publish(msg));
                engine.configurar(true, true, true, true, true, true, true);
                return engine.executar();
            }
            @Override protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    jTextArea1.append(msg + "\n");
                    jTextArea1.setCaretPosition(jTextArea1.getDocument().getLength());
                    if (msg.startsWith("[BACKUP]")) {
                        lblStatus.setText("Gerando backup do banco de destino...");
                    } else if (msg.startsWith("[MIGRAÇÃO]") || msg.startsWith("[FASE") || msg.startsWith("[FASES")) {
                        lblStatus.setText("Migrando dados...");
                    }
                }
            }
            @Override protected void done() {
                progresso.dispose();
                setControlesHabilitados(true);
                try {
                    MigrationReport report = get();
                    jTextArea1.append("\n" + report.toTexto());
                    jTextArea1.setCaretPosition(jTextArea1.getDocument().getLength());
                    if (report.isSucesso()) {
                        JOptionPane.showMessageDialog(self, "Migração concluída com sucesso!\nVeja o log para detalhes.", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(self, "Migração com erros. Verifique o log.", "Atenção", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
                    jTextArea1.append("\n[ERRO FATAL] " + causa.getMessage() + "\n");
                    JOptionPane.showMessageDialog(self,
                            "Falha antes/durante a migração (migração abortada):\n" + causa.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progresso.setVisible(true); // modal: fecha em done()
    }

    private void setControlesHabilitados(boolean on) {
        jButton1.setEnabled(on); jButton2.setEnabled(on);
        jButton3.setEnabled(on); jComboBox1.setEnabled(on);
        setCursor(on ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Carregar arquivo SQL / RAR
    // ────────────────────────────────────────────────────────────────────────

    private void carregarArquivo() {
        try { cleanPreviousTempRarDir(); } catch (Exception e) { /* ignora */ }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Selecionar Arquivo SQL ou RAR");

        // Abre direto na pasta Downloads do Windows
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.exists()) fc.setCurrentDirectory(downloads);

        // Filtro combinado como padrão (ambos visíveis ao mesmo tempo)
        FileNameExtensionFilter filtroPadrao = new FileNameExtensionFilter(
                "Arquivos SQL e RAR (*.sql, *.rar)", "sql", "rar");
        fc.addChoosableFileFilter(filtroPadrao);
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Arquivos SQL (*.sql)", "sql"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Arquivos RAR (*.rar)", "rar"));
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileFilter(filtroPadrao); // seleciona o combinado por padrão

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            loadedSqlFile = null; jTextFieldFilePath.setText(""); return;
        }

        File file = fc.getSelectedFile();
        jTextFieldFilePath.setText(file.getAbsolutePath());
        String nome = file.getName().toLowerCase();

        if (nome.endsWith(".rar"))      handleRarFileSelection(file);
        else if (nome.endsWith(".sql")) loadSqlFileToTextArea(file);
        else {
            JOptionPane.showMessageDialog(this, "Selecione .sql ou .rar.", "Tipo inválido", JOptionPane.WARNING_MESSAGE);
            loadedSqlFile = null; jTextFieldFilePath.setText("");
        }
        if (loadedSqlFile == null) jTextFieldFilePath.setText("");
    }

    private void loadSqlFileToTextArea(File sqlFile) {
        try {
            long kb = sqlFile.length() / 1024;
            StringBuilder sb = new StringBuilder();
            sb.append("Arquivo : ").append(sqlFile.getName()).append("\n");
            sb.append("Tamanho : ").append(kb).append(" KB\n");
            sb.append("\n--- Prévia (60 linhas) ---\n");
            try (BufferedReader r = new BufferedReader(new FileReader(sqlFile))) {
                String line; int n = 0;
                while ((line = r.readLine()) != null && n++ < 60) sb.append(line).append("\n");
            }
            sb.append("\n[... arquivo completo processado durante a migração ...]");
            jTextArea1.setText(sb.toString());
            jTextArea1.setCaretPosition(0);
            loadedSqlFile = sqlFile;
            JOptionPane.showMessageDialog(this, "Arquivo carregado: " + sqlFile.getName() + " (" + kb + " KB)", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            loadedSqlFile = null; jTextFieldFilePath.setText("");
        }
    }

    private void handleRarFileSelection(File rarFile) {
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rar_temp_").toFile();
            tempDir.deleteOnExit();
            this.currentTempRarDir = tempDir;

            File jarLoc = new File(MigratorPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File libDir = new File(jarLoc.getParentFile(), "lib");
            String os = System.getProperty("os.name").toLowerCase();
            String rarExec = os.contains("win") ? "Rar.exe" : "rar";
            File rarExeFile = new File(libDir, rarExec);
            String rarPath = rarExeFile.exists() ? rarExeFile.getAbsolutePath() : rarExec;

            ProcessBuilder pb = new ProcessBuilder(rarPath, "x", "-p-", "-o+", rarFile.getAbsolutePath(), tempDir.getAbsolutePath());
            pb.redirectErrorStream(true); pb.directory(libDir);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
            }
            if (proc.waitFor() != 0) {
                JOptionPane.showMessageDialog(this, "Erro ao descompactar RAR:\n" + out.toString().substring(0, Math.min(out.length(), 300)), "Erro RAR", JOptionPane.ERROR_MESSAGE);
                loadedSqlFile = null; jTextFieldFilePath.setText(""); return;
            }

            List<String> sqlFiles = new ArrayList<>();
            listSqlFilesInDirectory(tempDir, sqlFiles, "");
            if (sqlFiles.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nenhum .sql encontrado no RAR.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                loadedSqlFile = null; jTextFieldFilePath.setText(""); return;
            }

            JComboBox<String> combo = new JComboBox<>(sqlFiles.toArray(new String[0]));
            JPanel sel = new JPanel(); sel.add(new JLabel("Selecione o SQL:")); sel.add(combo);
            if (JOptionPane.showConfirmDialog(this, sel, "SQL no RAR", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                String rel = (String) combo.getSelectedItem();
                File sql = new File(tempDir, rel.replace("\\", File.separator));
                if (!sql.exists()) {
                    JOptionPane.showMessageDialog(this, "Arquivo não encontrado após extração.", "Erro", JOptionPane.ERROR_MESSAGE);
                    loadedSqlFile = null; jTextFieldFilePath.setText(""); return;
                }
                loadSqlFileToTextArea(sql);
                jTextFieldFilePath.setText(rarFile.getAbsolutePath() + " → " + rel);
            } else { loadedSqlFile = null; jTextFieldFilePath.setText(""); }
        } catch (IOException | InterruptedException | URISyntaxException ex) {
            JOptionPane.showMessageDialog(this, "Erro RAR: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            loadedSqlFile = null; jTextFieldFilePath.setText("");
        }
    }

    private void listSqlFilesInDirectory(File dir, List<String> lista, String pai) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) listSqlFilesInDirectory(f, lista, pai + f.getName() + File.separator);
            else if (f.getName().toLowerCase().endsWith(".sql")) lista.add(pai + f.getName());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Sair / utilitários
    // ────────────────────────────────────────────────────────────────────────

    private void sairMigracao() {
        if (JOptionPane.showConfirmDialog(this, "Deseja realmente sair?", "Confirmar Saída", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            cleanPreviousTempRarDir();
            if (dbManager != null) dbManager.disconnect();
            dispose();
            System.exit(0);
        }
    }

    private void cleanPreviousTempRarDir() {
        if (currentTempRarDir != null && currentTempRarDir.exists()) deleteDirectory(currentTempRarDir);
        currentTempRarDir = null;
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) { File[] f = dir.listFiles(); if (f != null) for (File x : f) deleteDirectory(x); }
        dir.delete();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tema FlatLaf — LC Sistemas
    // ────────────────────────────────────────────────────────────────────────

    public static void aplicarTema() {
        try {
            ThemeManager.apply();
        } catch (Exception ex) { log.warn("Falha ao aplicar tema", ex); }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabelFilePath;
    private javax.swing.JLabel jLabelFilePath1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextFieldFilePath;
    // End of variables declaration//GEN-END:variables
}
