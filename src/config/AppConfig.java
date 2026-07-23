package config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private Properties props;
    private static final String CONFIG_FILE = "rede.txt";

    public AppConfig() {
        props = new Properties();
        applyDefaults();
        readFrom(getConfigFile());
    }

    /**
     * Construtor de teste: carrega a configuração a partir de um arquivo
     * explícito, sem depender da localização do .jar em execução.
     */
    AppConfig(File configFile) {
        props = new Properties();
        applyDefaults();
        readFrom(configFile);
    }

    /**
     * Localiza o rede.txt no mesmo diretório do .jar em execução.
     * Fallback para o diretório corrente se não for possível determinar o jar.
     */
    private File getConfigFile() {
        try {
            File jarFile = new File(AppConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return new File(jarFile.getParentFile(), CONFIG_FILE);
        } catch (URISyntaxException e) {
            log.warn("Não foi possível determinar o diretório do .jar, usando diretório corrente.");
            return new File(CONFIG_FILE);
        }
    }

    private void applyDefaults() {
        props.setProperty("IP", "localhost");
        props.setProperty("PORT", "3306");
        props.setProperty("USER", "");
        props.setProperty("KEY", "");
        props.setProperty("DB", "");
        props.setProperty("TERMINAL_TIPO", "");
        props.setProperty("ID_EMPRESA_PADRAO", "");
    }

    private void readFrom(File configFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    props.setProperty(key, value);
                } else {
                    log.warn("Linha inválida no rede.txt (formato esperado KEY:VALOR): {}", line);
                }
            }
            log.info("Configurações carregadas de {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Erro ao carregar '{}': {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    // Métodos para obter os valores, construindo a URL JDBC dinamicamente
    public String getDbUrl() {
        String ip = props.getProperty("IP");
        String port = props.getProperty("PORT");
       
        
       
        return "jdbc:mysql://" + ip + ":" + port + "/";
    }

    public String getDbHost() {
        return props.getProperty("IP");
    }

    public String getDbPort() {
        return props.getProperty("PORT");
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
    
    
    public void updateDefaultDb(String newDefaultDb) {
        props.setProperty("DB", newDefaultDb);
    }
    
    public String getTerminalTipo() {
        return props.getProperty("TERMINAL_TIPO");
    }

    public String getIdEmpresaPadrao() {
        return props.getProperty("ID_EMPRESA_PADRAO");
    }
}
