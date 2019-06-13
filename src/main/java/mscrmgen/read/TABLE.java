package mscrmgen.read;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

public class TABLE {

    @XmlAttribute(name="IF")
    private String IF;

    public String getIF() {
        return IF;
    }

    public List<COLUMN> getColumns() {
        return columns;
    }

    @XmlElement(name="COLUMN")
    List<COLUMN> columns = new ArrayList<>();

}
