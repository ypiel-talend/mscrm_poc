package mscrmgen.read;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;

public class UnmarshallFile {



    public static CONFIG unmarshallConfig(String xml){
        CONFIG config = null;
        try {
            File file = new File(xml);
            JAXBContext jaxbContext = JAXBContext.newInstance(CONFIG.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            config = (CONFIG) unmarshaller.unmarshal(file);
        }
        catch (Exception e){
            System.err.println("Cant unmarshall : " + e.getMessage());
            e.printStackTrace();
        }

        return config;
    }

}
