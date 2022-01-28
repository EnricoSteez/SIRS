import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ReadPropertyFile {
    String propertiesPath = "config.properties";
    Properties prop = new Properties();
    FileInputStream ip;

    public ReadPropertyFile(){
        try {
            ip = new FileInputStream(propertiesPath);
            prop.load(ip);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String key){
        return prop.getProperty(key);
    }
}
