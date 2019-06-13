package mscrmgen.read;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "CONFIG")
public class CONFIG {

    @XmlElement(name="PARAMETER")
    private List<PARAMETER> parameters = new ArrayList<>();

    public List<PARAMETER> getParameters() {
        return parameters;
    }
}
