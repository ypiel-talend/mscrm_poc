package mscrmgen.read;

import javax.xml.bind.annotation.XmlAttribute;

public class COLUMN {

    @XmlAttribute(name="KEY")
    private String KEY;
    @XmlAttribute(name="LENGTH")
    private String LENGTH;
    @XmlAttribute(name="NAME")
    private String NAME;
    @XmlAttribute(name="PATTERN")
    private String PATTERN;


    public String getPATTERN() {
        return PATTERN;
    }

    public String getKEY() {
        return KEY;
    }

    public String getLENGTH() {
        return LENGTH;
    }

    public String getNAME() {
        return NAME;
    }

    public String getTYPE() {
        return TYPE;
    }

    @XmlAttribute(name="TYPE")
    private String TYPE;

}
