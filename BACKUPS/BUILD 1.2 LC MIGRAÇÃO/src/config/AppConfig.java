package config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties; 

public class AppConfig {
    private Properties props;
    private static final String CONFIG_FILE = "rede.txt"; 

    public AppConfig() {
        props = new Properties();
        loadConfig();
    }

    private void loadConfig() {
        props.setProperty("IP", "localhost");
        props.setProperty("PORT", "3306");
        props.setProperty("USER", "");
        props.setProperty("KEY", "");
        props.setProperty("DB", ""); // Banco de dados padrão
        props.setProperty("TERMINAL_TIPO", "");
        props.setProperty("ID_EMPRESA_PADRAO", "");

        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) { // Ignora linhas vazias ou comentários
                    continue;
                }
                
                String[] parts = line.split(":", 2); // Divide em no máximo 2 partes no primeiro ':'
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    props.setProperty(key, value); // Armazena a chave e o valor como estão no arquivo
                } else {
                    System.err.println("Linha inválida no rede.txt (formato esperado KEY:SENHA): " + line);
                }
            }
            System.out.println("Configurações carregadas com sucesso de " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Erro ao carregar o arquivo de configuração '" + CONFIG_FILE + "': " + e.getMessage());
            System.err.println("Certifique-se de que o arquivo 'rede.txt' está na raiz do projeto (ou no diretório de execução).");
            // Se o arquivo não puder ser lido, os valores padrão definidos no início serão usados.
        }
    }

    // Métodos para obter os valores, construindo a URL JDBC dinamicamente
    public String getDbUrl() {
        String ip = props.getProperty("IP");
        String port = props.getProperty("PORT");
        String dbName = props.getProperty("DB"); // DB padrão do arquivo rede.txt
        
        // Se o DB padrão estiver vazio, conecte-se apenas ao servidor.
        if (dbName == null || dbName.isEmpty()) {
            return "jdbc:mysql://" + ip + ":" + port + "/";
        } else {
            return "jdbc:mysql://" + ip + ":" + port + "/" + dbName;
        }
    }

    public String getDbUser() {
        return props.getProperty("USER");
    }

    public String getDbPassword() {
        return props.getProperty("KEY"); 
    }

    
    public String getDefaultDb() {
        return props.getProperty("DB");
    }
    
    // Método para atualizar o banco de dados padrão (em memória para o AppConfig)
    
    public void updateDefaultDb(String newDefaultDb) {
        props.setProperty("DB", newDefaultDb); // Atualiza a propriedade 'DB'
    }
    
    public String getTerminalTipo() {
        return props.getProperty("TERMINAL_TIPO");
    }

    public String getIdEmpresaPadrao() {
        return props.getProperty("ID_EMPRESA_PADRAO");
    }
}