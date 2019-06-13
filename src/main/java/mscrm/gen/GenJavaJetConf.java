package mscrm.gen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.aad.adal4j.AuthenticationCallback;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmSchema;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * I use this class to generate tMicrosoftInput/output_java.xml of javajet connectors.
 * The configuration is not the same for input, output/insert or ouput/update. Set the GEN_ACTION attribute first.
 * This class retrieve entities from Odata/Olingo and the do direct http call to retrieve json to get metadata.
 */
public class GenJavaJetConf {

    public enum ACTION{
        READ, INSERT, UPDATE;
    }

    public final static ACTION GEN_ACTION = ACTION.UPDATE;

    public static final String OAUTH_2_AUTHORIZE = "https://login.windows.net/common/oauth2/authorize";

    public static final String TMP_GEN_MSCRMCONFIG_XML = "/tmp/genMSCRMConfig.xml";
    public static final String TMP_GEN_MSCRMCONFIG_ENTITIES = "/tmp/genMSCRMEntitiesConfig.xml";
    public static final String API_ODATA = "API_2018_ODATA";
    public static final String CRM_VERSION = "CRM_2018";



    public static final String MSCRM_CRED = "/tmp/mscrm.cred";
    private static Properties credentials = new Properties();
    static {
        try {
            credentials.load(new FileInputStream(MSCRM_CRED));
        } catch (Exception e) {
            System.out.println("Can't read credentials : " + e.getMessage());
        }
    }

    private static List<String> notFoundEntity = new ArrayList<>();


    public final static FullEntity getFullEntity(String entity, String token) throws Exception {
        URL url = new URL(credentials.getProperty("service") + "EntityDefinitions(LogicalName='" + entity + "')?$expand=Attributes");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty(HttpHeader.AUTHORIZATION, "Bearer " + token);
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        int code = con.getResponseCode();
        if (code != 200) {
            return null;
        }

        BufferedReader jsonr = new BufferedReader(new InputStreamReader(con.getInputStream()));
        final Gson gson = new GsonBuilder().create();
        FullEntity full = gson.fromJson(jsonr, FullEntity.class);

        return full;
    }


    public final static void main(String[] args) {
        try {
            System.out.println("Generate for action : " + GEN_ACTION);

            ODataClient client = ODataClientFactory.getClient();
            client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);

            ExecutorService service = Executors.newFixedThreadPool(1);
            AuthenticationContext context = new AuthenticationContext(OAUTH_2_AUTHORIZE, false, service);
            Future<AuthenticationResult> authenticationResultFuture = acquireToken(context);
            AuthenticationResult authenticationResult = authenticationResultFuture.get();
            String token = authenticationResult.getAccessToken();

            // Retrieve entity list
            EdmMetadataRequest metadataRequest = client.getRetrieveRequestFactory().getMetadataRequest(credentials.getProperty("service"));
            metadataRequest.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            final Edm edm = metadataRequest.execute().getBody();
            EdmSchema dynCRMSchema = edm.getSchema("Microsoft.Dynamics.CRM");

            Parameter config = new Parameter();

            Path path = Paths.get(TMP_GEN_MSCRMCONFIG_ENTITIES);
            try (BufferedWriter entitiesWriter = Files.newBufferedWriter(path)) {
                int ccc = 1;
                List<EdmEntityType> types = dynCRMSchema.getEntityTypes();
                for (EdmEntityType t : types) {
                    String name = t.getName();

                    // For each entity retrieve metadata
                    FullEntity fullEntity = getFullEntity(name, token);

                    if (fullEntity == null || fullEntity.IsCustomEntity) {
                        continue;
                    }

                    MSCRMEntity e = new MSCRMEntity(name, fullEntity.EntitySetName, GEN_ACTION);
                    for (Attribute atr : fullEntity.Attributes) {
                        //System.out.println("\t - " + atr.LogicalName + " / " + atr.IsRetrievable + " / " + atr.IsValidForRead + " / " + ((atr.Description.UserLocalizedLabel == null) ? "x" : atr.Description.UserLocalizedLabel.Label));

                        try {
                            boolean excludeActions = (GEN_ACTION == ACTION.READ && !atr.IsValidForRead) ||  (GEN_ACTION == ACTION.INSERT && !atr.IsValidForCreate) || (GEN_ACTION == ACTION.UPDATE && !atr.IsValidForUpdate);
                            boolean isIdInUpd =  GEN_ACTION == ACTION.UPDATE && atr.IsPrimaryId && !atr.IsLogical;
                            boolean isIdIns = GEN_ACTION == ACTION.INSERT && atr.IsPrimaryId && !atr.IsLogical;
                            if (!isIdInUpd  && (isIdIns || atr.isInternalUse() || atr.isExcludedType() || atr.IsCustomAttribute || (!atr.IsPrimaryId && atr.IsLogical) || excludeActions || atr.AttributeOf != null)) {
                                //System.out.println("- Internal/virtual/entityName " + name + "." + atr.LogicalName);
                            } else {
                                EntityProp p = new EntityProp(atr.LogicalName, atr.AttributeType, "" + (atr.IsPrimaryId && !atr.IsLogical));
                                e.props.add(p);
                            }
                        } catch (Exception ex) {
                            System.err.println("Cant add prop " + name + "." + atr.LogicalName + " : " + ex.getMessage());
                        }
                    }
                    //System.out.println("+ " + fullEntity.LogicalName);
                    System.out.printf(".");
                    ccc++;
                    if (ccc > 50) {
                        System.out.println();
                        ccc = 1;
                    }
                    config.add(e);

                    // Gen entity list configuration for closed list
                    entitiesWriter.write("<ITEM NAME=\""+fullEntity.EntitySetName+"\" VALUE=\""+fullEntity.EntitySetName+"\" />\n");
                }
            } catch (Exception e) {
                System.err.println("Can't write entities list : " + e.getMessage());
            }

            // Gen schema configuration
            genXML(config);

            System.out.println("Done.");

        } catch (Exception e) {
            System.err.println("==> " + e.getMessage());
            e.printStackTrace();
        }
    }

    private final static void genXML(Parameter config) {
        try {
            System.out.println("Gen XML...");
            JAXBContext jaxbContext = JAXBContext.newInstance(Parameter.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);


            OutputStream os = new FileOutputStream(new File(TMP_GEN_MSCRMCONFIG_XML));
            jaxbMarshaller.marshal(config, os);

        } catch (Exception e) {
            System.err.println("Can't generate XML : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Future<AuthenticationResult> acquireToken(AuthenticationContext context) throws Exception {
        Future<AuthenticationResult> future;
        future = context.acquireToken(credentials.getProperty("resource"), credentials.getProperty("clientid"), credentials.getProperty("user"), credentials.getProperty("password"), (AuthenticationCallback) null);
        return future;
    }

    /*
     * JSon MS CRM input
     */

    public static class FullEntity {
        public String LogicalName;
        public String LogicalCollectionName;
        public String EntitySetName;
        public String PrimaryIdAttribute;
        public boolean IsCustomEntity;
        public Set<Attribute> Attributes = new TreeSet<>(new Comparator<Attribute>() {
            @Override
            public int compare(Attribute o1, Attribute o2) {
                return o1.LogicalName.compareTo(o2.LogicalName);
            }
        });
    }

    public static class Attribute {
        public String LogicalName;
        public String SchemaName;
        public boolean IsPrimaryId;
        public boolean IsValidForRead;
        public boolean IsValidForCreate;
        public boolean IsValidForUpdate;
        public boolean IsRetrievable;
        public boolean IsLogical;
        public boolean IsCustomAttribute;
        public String AttributeOf;
        public Description Description;
        public String AttributeType;

        public boolean isInternalUse() {
            if (Description.UserLocalizedLabel == null) {
                return false;
            }

            if ("For internal use only.".equals(Description.UserLocalizedLabel.Label)) {
                return true;
            }

            return false;
        }

        public boolean isExcludedType() {
            return "EntityName".equals(AttributeType) || "Virtual".equals(AttributeType) || "PartyList".equals(AttributeType);
        }

    }

    public static class Description {
        public UserLocalizedLabel UserLocalizedLabel;
    }

    public static class UserLocalizedLabel {
        public String Label;
    }


    /*
     * XML Output class JAXB
     */
    @XmlRootElement(name = "PARAMETER")
    private final static class Parameter {

        @XmlElement(name = "TABLE")
        private final static List<MSCRMEntity> entities = new ArrayList<>();

        public void add(MSCRMEntity e) {
            entities.add(e);
        }

    }


    private final static class MSCRMEntity {

        public MSCRMEntity() {
        }

        public MSCRMEntity(String name, String pluralName, ACTION action) {
            this.name = name;
            this.pluralName = pluralName;

            this.cond = "(ENTITYSETV2018=='" + pluralName + "') AND (((AUTH_TYPE=='ONLINE') AND (API_VERSION=='" + API_ODATA + "')) OR ((AUTH_TYPE=='ON_PREMISE') AND (MS_CRM_VERSION == '" + CRM_VERSION + "')))";
            if(action == ACTION.INSERT){
                this.cond += " AND (ACTION=='insert')";
            }
            else if(action == ACTION.UPDATE){
                this.cond += " AND (ACTION=='update')";
            }
        }

        @XmlAttribute(name = "IF")
        String cond;

        String name;
        String pluralName;

        @XmlElement(name = "COLUMN")
        Set<EntityProp> props = new TreeSet<>(new Comparator<EntityProp>() {
            @Override
            public int compare(EntityProp o1, EntityProp o2) {
                if (o1.NAME.startsWith("_")) {
                    if (o2.NAME.startsWith("_")) {
                        return o1.NAME.compareTo(o2.NAME);
                    }
                    return 1;
                } else if (o2.NAME.startsWith("_")) {
                    return -1;
                }
                return o1.NAME.compareTo(o2.NAME);
            }
        });
    }

    private final static class EntityProp {

        public EntityProp() {
        }

        public EntityProp(String name, String type, String key) throws Exception {
            this.NAME = name;
            this.edmType = type;
            this.TYPE = this.convertoToTalendType(type);
            this.KEY = key;
        }

        @XmlAttribute(name = "KEY")
        String KEY = "false";
        @XmlAttribute(name = "LENGTH")
        String LENGTH = "0";
        @XmlAttribute(name = "NAME")
        String NAME;
        @XmlAttribute(name = "TYPE")
        String TYPE;
        @XmlAttribute(name = "DBTYPE")
        String DBTYPE;
        @XmlAttribute(name = "PATTERN")
        String PATTERN;
        //@XmlAttribute
        String edmType;

        private void setDbType(String type){
            if(GEN_ACTION == ACTION.READ){
                return;
            }
            DBTYPE = type;
        }

        private String convertoToTalendType(String edmType) throws Exception {
            String t = "UNKNOWN";

            switch (edmType) {
                case "Binary":
                    t = "id_byte[]";
                    setDbType("Binary");
                    break;
                case "Boolean":
                    t = "id_Boolean";
                    setDbType("Boolean");
                    break;
                case "DateTime":
                case "DateTimeOffset":
                    t = "id_Date";
                    if (NAME.toLowerCase().endsWith("date") || "anniversary".equals(NAME.toLowerCase()) || "birthdate".equals(NAME.toLowerCase()) || "duedate".equals(NAME.toLowerCase()) || "estimatedclosedate".equals(NAME.toLowerCase()) || "actualclosedate".equals(NAME.toLowerCase()) || "estimatedclosedate".equals(NAME.toLowerCase()) || "finaldecisiondate".equals(NAME.toLowerCase()) || "validfromdate".equals(NAME.toLowerCase()) || "validtodate".equals(NAME.toLowerCase()) || "closedon".equals(NAME.toLowerCase()) || "expireson".equals(NAME.toLowerCase())) {
                        this.PATTERN = "\"yyyy-MM-dd\"";
                    } else {
                        this.PATTERN = "\"yyyy-MM-dd'T'HH:mm:ss'Z'\"";
                    }
                    setDbType("DateTimeOffset");
                    break;
                case "Decimal":
                case "Money":
                    t = "id_BigDecimal";
                    setDbType("Decimal");
                    break;
                case "Double":
                    t = "id_Double";
                    setDbType("Double");
                    break;
                case "Uniqueidentifier":
                case "Guid":
                    t = "id_String";
                    setDbType("Guid");
                    break;
                case "Picklist":
                case "Status":
                case "State":
                case "Integer":
                case "Int32":
                    t = "id_Integer";
                    setDbType("Int32");
                    break;
                case "BigInt":
                case "Int64":
                    t = "id_Long";
                    setDbType("Int64");
                    break;
                case "ManagedProperty":
                case "Memo":
                case "String":
                    t = "id_String";
                    setDbType("String");
                    break;
                case "BooleanManagedProperty":
                    t = "id_String";
                    setDbType("String");
                    break;
                case "Customer":
                case "Lookup":
                case "Owner":
                    this.NAME = "_" + this.NAME + "_value";
                    t = "id_String";
                    setDbType("Guid");
                    break;
                default:
                    throw new Exception("Unknown type : " + edmType);
            }

            return t;
        }
    }
}
