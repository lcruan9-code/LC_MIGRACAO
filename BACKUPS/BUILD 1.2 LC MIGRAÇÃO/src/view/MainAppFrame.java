package view;

import config.AppConfig;
import model.DatabaseManager;

import javax.swing.*;
import java.sql.SQLException;
import java.util.List;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainAppFrame extends JFrame {

    private AppConfig appConfig;
    private DatabaseManager dbManager;
    private MigratorPanel migratorPanel;




    public MainAppFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
        setResizable(false);                                 
        setUndecorated(true);                                 

        addWindowListener(new WindowAdapter() {               
            @Override
            public void windowClosing(WindowEvent e) {        
                JOptionPane.showMessageDialog(MainAppFrame.this,
                    "Para sair da aplicação, por favor, use o botão 'Cancelar'.",
                    "Aviso de Fechamento", JOptionPane.INFORMATION_MESSAGE);
            }
        });
      
        initComponents(); 

        appConfig = new AppConfig();
        dbManager = new DatabaseManager(appConfig);

        migratorPanel = new MigratorPanel(dbManager, this);
        jTabbedPane1.addTab("Migração SQL", migratorPanel); 

        setTitle("LC_MIGRACAO - Ferramenta de Migração de Banco de Dados");
        // setSize(800, 600); // Exemplo de tamanho fixo 
        setLocationRelativeTo(null); // Centraliza a janela na tela

        // Tenta conectar ao DB e popular o ComboBox ao iniciar
        initializeDatabaseConnectionAndUI(); 
    }

    
    private void initializeDatabaseConnectionAndUI() { 
        try {
            dbManager.connect(); 
            List<String> databases = dbManager.getAllDatabases(); 
            migratorPanel.populateDatabaseComboBox(databases); 
// AVISO REMOVIDO PARA DIMINUIR CLICKS       
//            JOptionPane.showMessageDialog(this,
//                "Conexão com o servidor MySQL estabelecida com sucesso.",
//                "Conexão", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Erro ao conectar ou carregar bancos de dados: " + e.getMessage() +
                "\nVerifique as configurações em 'rede.txt' e o status do seu servidor MySQL.",
                "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace(); 
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this,
                "Erro fatal na inicialização: " + e.getMessage() +
                "\nCertifique-se de que o JAR do MySQL Connector/J está corretamente adicionado ao projeto.",
                "Erro Fatal", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace(); 
            System.exit(1); 
        }
    }

    
    public AppConfig getAppConfig() { // Linha 93
        return appConfig;
    }


  
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        jLabel1.setBackground(new java.awt.Color(0, 0, 0));
        jLabel1.setFont(new java.awt.Font("Arial", 1, 18)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("LC Migração");
        jLabel1.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jLabel1.setOpaque(true);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 638, Short.MAX_VALUE)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 182, Short.MAX_VALUE)
                .addGap(1, 1, 1))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    
 public static void main(String args[]) {
        try {
           
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
                               
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            System.err.println("Erro ao configurar o Look and Feel: " + e.getMessage());
        }
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainAppFrame().setVisible(true);
            }
        });
    }
 
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTabbedPane jTabbedPane1;
    // End of variables declaration//GEN-END:variables
}
