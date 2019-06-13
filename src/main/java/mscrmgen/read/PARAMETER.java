package mscrmgen.read;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

public class PARAMETER {

    @XmlAttribute(name="NAME")
    private String NAME;
    @XmlAttribute(name="FIELD")
    private String FIELD;
    @XmlAttribute(name="ROW")
    private String NUM_ROW;
    @XmlAttribute(name="REQUIERED")
    private String REQUIRED;

    public String getNAME() {
        return NAME;
    }

    public String getFIELD() {
        return FIELD;
    }

    public String getNUM_ROW() {
        return NUM_ROW;
    }

    public String getREQUIRED() {
        return REQUIRED;
    }

    public List<TABLE> getTables() {
        return tables;
    }

    @XmlElement(name="TABLE")
    private List<TABLE> tables = new ArrayList<>();

}
