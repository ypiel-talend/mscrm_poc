package mscrm.gen.olingo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.aad.adal4j.AuthenticationCallback;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import mscrmgen.read.COLUMN;
import mscrmgen.read.CONFIG;
import mscrmgen.read.TABLE;
import mscrmgen.read.UnmarshallFile;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmKeyPropertyRef;
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
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * DON T use this class but prefer mscrm.gen.GenJavaJetConf.
 * This class relies on olingo/odata but I didn't succeeded to retrieve all needed metadata to generate configuration.
 */
public class GenJavajetConfOlingo {


    public static final String OAUTH_2_AUTHORIZE = "https://login.windows.net/common/oauth2/authorize";
    public static final String TMP_NOT_NEEDED_OUT = "/tmp/notNeeded.out";
    public static final String TMP_NEEDED_AND_FOUND_OUT = "/tmp/neededAndFound.out";
    public static final String TMP_NEEDED_NOT_FOUND_OUT = "/tmp/neededNotFound.out";
    public static final String ENTITYSET_V9 = "/home/ypiel/dev/Jira/TDI-40979_MSCRM_V9/v9EntitySet_purged.out";
    public static final String MSCRM_CRED = "/tmp/mscrm.cred";
    public static final String CURRENT_CONFIG_XML = "/home/ypiel/dev/Jira/TDI-40979_MSCRM_V9/tMicrosoftCrmSchemaV8_frmt.xml";

    public static final String TMP_GEN_MSCRMCONFIG_XML = "/tmp/genMSCRMConfig.xml";
    public static final String API_ODATA = "API_2018_ODATA";
    public static final String CRM_VERSION = "CRM_2018";

    private final static Map<String, String> hardCodedException = new HashMap<>();

    static {
        hardCodedException.put("subscriptionsyncentryoffline", "subscriptionsyncentriesoffline");
        hardCodedException.put("subscriptionsyncentryoutlook", "subscriptionsyncentriesoutlook");
        hardCodedException.put("knowledgearticlescategories", "knowledgearticlecategories");
    }

    private final static Map<String, String> datePropertyCache = new HashMap<>();
    private static int nbTotalDate = 0;
    private static int nbNewDate = 0;

    static {
        CONFIG currentConfig = UnmarshallFile.unmarshallConfig(CURRENT_CONFIG_XML);
        List<TABLE> tables = currentConfig.getParameters().get(0).getTables();

        for (TABLE t : tables) {
            boolean v2016 = t.getIF().contains("AND (((AUTH_TYPE=='ONLINE') AND (API_VERSION=='API_2016_ODATA')) OR ((AUTH_TYPE=='ON_PREMISE') AND (MS_CRM_VERSION == 'CRM_2016')))");
            if (!v2016) {
                continue;
            }
            String entity = t.getIF().substring(13, t.getIF().indexOf("'", 14));
            for (COLUMN c : t.getColumns()) {
                if ("id_Date".equals(c.getTYPE())) {
                    datePropertyCache.put(entity + "." + c.getNAME(), c.getPATTERN());
                }
            }

        }
    }

    private final static List<String> neededEntities = new ArrayList<>();

    private final static Parameter entities = new Parameter();


    private static Properties credentials = new Properties();


    private  static List<String> notFoundEntity = new ArrayList<>();


    static {
        try {
            credentials.load(new FileInputStream(MSCRM_CRED));
        } catch (Exception e) {
            System.out.println("Can't read credentials : " + e.getMessage());
        }
    }

    /*private final static ClientEntity getAttributeMetadata(ODataClient client, String token, String entity) {
        try {
            ODataEntityRequest<ClientEntity> service = client.getRetrieveRequestFactory().getPropertyRequest(new URI(credentials.getProperty("service") + "EntityDefinitions(LogicalName='" + entity + "')"));
            service.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            ODataRetrieveResponse<ClientEntity> resp = service.execute();
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("=> getEntityMetadata : " + e.getMessage());
            //e.printStackTrace();
            notFoundEntity.add(entity);
        }
        return null;
    }*/

    private final static ClientEntity getEntityMetadata(ODataClient client, String token, String entity) {
        try {
            //ODataEntityRequest<ClientEntity> service = client.getRetrieveRequestFactory().getEntityRequest(new URI(credentials.getProperty("service") + "EntityDefinitions(LogicalName='" + entity + "')"));
            ODataEntityRequest<ClientEntity> service = client.getRetrieveRequestFactory().getEntityRequest(new URI(credentials.getProperty("service") + "EntityDefinitions(LogicalName='"+entity+"')?$expand=Attributes"));
            service.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            ODataRetrieveResponse<ClientEntity> resp = service.execute();
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("=> getEntityMetadata : " + e.getMessage());
            //e.printStackTrace();
            notFoundEntity.add(entity);
        }
        return null;
    }


    public final static FullEntity getFullEntity(String entity, String token) throws Exception{
        URL url = new URL(credentials.getProperty("service") + "EntityDefinitions(LogicalName='" + entity + "')?$expand=Attributes");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty(HttpHeader.AUTHORIZATION, "Bearer " + token);
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        int code = con.getResponseCode();
        if(code != 200){
            return null;
        }

        /*String json = con.getResponseMessage();
        Reader jsonr = new StringReader(con.getResponseMessage());*/
        BufferedReader jsonr = new BufferedReader(new InputStreamReader(con.getInputStream()));
        final Gson gson = new GsonBuilder().create();
        FullEntity full = gson.fromJson(jsonr, FullEntity.class);

        return  full;
    }

    public static class FullEntity{
        public String LogicalName;
        public String LogicalCollectionName;
        public String EntitySetName;
        public String PrimaryIdAttribute;
        public Set<Attribute> Attributes = new TreeSet<>(new Comparator<Attribute>() {
            @Override
            public int compare(Attribute o1, Attribute o2) {
                return o1.LogicalName.compareTo(o2.LogicalName);
            }
        });
    }

    public static class Attribute{
        public String LogicalName;
        public String SchemaName;
        public boolean IsPrimaryId;
        public boolean IsValidForRead;
        public boolean IsValidForCreate;
        public boolean IsValidForUpdate;
        public boolean IsRetrievable;
        public boolean IsLogical;
        public Description Description;
    }

    public static class Description {
        public UserLocalizedLabel UserLocalizedLabel;
    }

    public static class UserLocalizedLabel {
        public String Label;
    }

    public final static void main(String[] args) {
        try {
            initNeededEntities();

            ODataClient client = ODataClientFactory.getClient();
            client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);

            ExecutorService service = Executors.newFixedThreadPool(1);
            AuthenticationContext context = new AuthenticationContext(OAUTH_2_AUTHORIZE, false, service);
            Future<AuthenticationResult> authenticationResultFuture = acquireToken(context);
            AuthenticationResult authenticationResult = authenticationResultFuture.get();
            String token = authenticationResult.getAccessToken();

            //String serviceUrl = credentials.getProperty("service");
            EdmMetadataRequest metadataRequest = client.getRetrieveRequestFactory().getMetadataRequest(credentials.getProperty("service"));
            metadataRequest.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            final Edm edm = metadataRequest.execute().getBody();
            EdmSchema dynCRMSchema = edm.getSchema("Microsoft.Dynamics.CRM");

            List<EdmEntityType> types = dynCRMSchema.getEntityTypes();
            for (EdmEntityType t : types) {
                String name = t.getName();

                FullEntity fullEntity = getFullEntity(name, token);

                if(fullEntity == null){
                    continue;
                }

                System.out.println("=> " + fullEntity.LogicalName);
                for(Attribute atr : fullEntity.Attributes){
                    System.out.println("\t - " + atr.LogicalName + " / " + atr.IsRetrievable + " / " + atr.IsValidForRead + " / " + ((atr.Description.UserLocalizedLabel == null) ? "x" : atr.Description.UserLocalizedLabel.Label));
                }

                /*ClientEntity meta = getEntityMetadata(client, token, name);

                if(meta != null) {
                    System.out.println("==> " + name + " / " + meta.isReadOnly());
                    List<ClientProperty> properties = meta.getProperties();
                    for (ClientProperty prp : properties) {
                        System.out.println("\t - " + prp.getName());
                    }
                }*/
            }

        } catch (Exception e) {
            System.err.println("==> " + e.getMessage());
            e.printStackTrace();
        }
    }

    public final static void main__(String[] args) {

        try {
            initNeededEntities();

            ODataClient client = ODataClientFactory.getClient();
            client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);

            ExecutorService service = Executors.newFixedThreadPool(1);
            AuthenticationContext context = new AuthenticationContext(OAUTH_2_AUTHORIZE, false, service);
            Future<AuthenticationResult> authenticationResultFuture = acquireToken(context);
            AuthenticationResult authenticationResult = authenticationResultFuture.get();
            String token = authenticationResult.getAccessToken();


            //String serviceUrl = credentials.getProperty("service");
            EdmMetadataRequest metadataRequest = client.getRetrieveRequestFactory().getMetadataRequest(credentials.getProperty("service"));
            metadataRequest.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            final Edm edm = metadataRequest.execute().getBody();
            EdmSchema dynCRMSchema = edm.getSchema("Microsoft.Dynamics.CRM");

            Path p = Paths.get(TMP_NOT_NEEDED_OUT);
            Path pnf = Paths.get(TMP_NEEDED_AND_FOUND_OUT);


            try (BufferedWriter writernf = Files.newBufferedWriter(pnf)) {
                try (BufferedWriter writer = Files.newBufferedWriter(p)) {

                    List<EdmEntityType> types = dynCRMSchema.getEntityTypes();
                    for (EdmEntityType t : types) {
                        String name = t.getName();

                        String neededPlural = needed(name);
                        boolean needed = neededPlural != null;
                        if (!needed) {
                            writer.write(name + "\n");
                            System.err.println("Not needed : " + name);
                            continue;
                        }

                        MSCRMEntity ent = new MSCRMEntity(name, neededPlural);
                        writernf.write(name + "\n");


                        List<String> props = t.getPropertyNames();
                        for (String pr : props) {
                            EntityProp entityProp = new EntityProp(pr, t.getProperty(pr).getType().getName(), name);
                            //System.out.println("=>" + t.getProperty(pr).getType());
                            if ("DateTimeOffset".equals(t.getProperty(pr).getType().getName())) {
                                //System.out.println(t.getProperty(pr).getType() + " / " + t.getProperty(pr).getType().getFullQualifiedName() + " / " + t.getProperty(pr).getType().getKind());
                                String cacheId = neededPlural + "." + pr;
                                String pattern = datePropertyCache.get(cacheId);
                                nbTotalDate++;
                                if (pattern == null) {
                                    nbNewDate++;
                                    System.out.println("Date can't find pattern for : " + cacheId);
                                    if (pr.toLowerCase().endsWith("date") || "anniversary".equals(pr.toLowerCase()) || "birthdate".equals(pr.toLowerCase()) || "duedate".equals(pr.toLowerCase()) || "estimatedclosedate".equals(pr.toLowerCase()) || "actualclosedate".equals(pr.toLowerCase()) || "estimatedclosedate".equals(pr.toLowerCase()) || "finaldecisiondate".equals(pr.toLowerCase()) || "validfromdate".equals(pr.toLowerCase()) || "validtodate".equals(pr.toLowerCase()) || "closedon".equals(pr.toLowerCase()) || "expireson".equals(pr.toLowerCase())) {
                                        entityProp.PATTERN = "&quot;yyyy-MM-dd&quot;";
                                    } else {
                                        entityProp.PATTERN = "&quot;yyyy-MM-dd'T'HH:mm:ss'Z'&quot;";
                                    }
                                } else {
                                    entityProp.PATTERN = pattern;
                                }
                            }
                            ent.props.add(entityProp);
                        }

                        entities.add(ent);
                    }

                } catch (Exception e) {
                    System.out.println("Can't write no needed entities : " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) {
                System.out.println("can't write needed and found : " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("can't write needed not found : " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Cant find pattern for " + nbNewDate + " on total of " + nbTotalDate);

        System.out.println("----------------------------------");
        System.out.println("Needed but not computed : " + neededEntities.size());

        Path pnnf = Paths.get(TMP_NEEDED_NOT_FOUND_OUT);
        try (BufferedWriter writernnf = Files.newBufferedWriter(pnnf)) {
            neededEntities.stream().forEach(e -> {
                System.out.println(e);
                try {
                    writernnf.write(e + "\n");

                } catch (Exception ex) {
                    System.out.println("Exception : " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        } catch (
                Exception e) {
            System.out.println("Exception : " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Generate XML");
        genXML();

        System.out.println("Done.");

    }

    private final static void genXML() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Parameter.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);


            OutputStream os = new FileOutputStream(new File(TMP_GEN_MSCRMCONFIG_XML));
            jaxbMarshaller.marshal(entities, os);

        } catch (Exception e) {
            System.err.println("Can't generate XML : " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static String needed(String n) {
        if (neededEntities.remove(n)) {
            return n;
        }

        String ns = n + "s";
        if (neededEntities.remove(ns)) {
            return ns;
        }

        if (n.endsWith("y")) {
            String nies = n.substring(0, n.length() - 1) + "ies";
            if (neededEntities.remove(nies)) {
                return nies;
            }
        }

        if (n.endsWith("us")) {
            String uses = n + "es";
            if (neededEntities.remove(uses)) {
                return uses;
            }
        }

        if (n.endsWith("x")) {
            String xes = n + "es";
            if (neededEntities.remove(xes)) {
                return xes;
            }
        }

        if (n.endsWith("ss")) {
            String sses = n + "es";
            if (neededEntities.remove(sses)) {
                return sses;
            }
        }

        if (n.endsWith("s")) {
            String coll = n + "collection";
            if (neededEntities.remove(coll)) {
                return coll;
            }
        }

        String set = n + "set";
        if (neededEntities.remove(set)) {
            return set;
        }

        if (n.endsWith("categories")) {
            String scat = n.substring(0, n.length() - "categories".length()) + "scategories";
            if (neededEntities.remove(scat)) {
                return scat;
            }
        }

        if (hardCodedException.containsKey(n)) {
            if (neededEntities.remove(hardCodedException.get(n))) {
                return hardCodedException.get(n);
            }
        }

        return null;
    }


    private static Future<AuthenticationResult> acquireToken(AuthenticationContext context) throws Exception {
        Future<AuthenticationResult> future;
        future = context.acquireToken(credentials.getProperty("resource"), credentials.getProperty("clientid"), credentials.getProperty("user"), credentials.getProperty("password"), (AuthenticationCallback) null);
        return future;
    }


    private static void initNeededEntities() {
        try {
            Path p = Paths.get(ENTITYSET_V9);
            BufferedReader reader = Files.newBufferedReader(p);
            String l = null;
            while ((l = reader.readLine()) != null) {
                neededEntities.add(l);
            }
        } catch (Exception e) {
            System.out.println("Can't read needd entities : " + e.getMessage());
            e.printStackTrace();
        }
    }

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

        public MSCRMEntity(String name, String pluralName) {
            this.name = name;
            this.pluralName = pluralName;

            this.cond = "(ENTITYSETV2018=='" + pluralName + "') AND (((AUTH_TYPE=='ONLINE') AND (API_VERSION=='" + API_ODATA + "')) OR ((AUTH_TYPE=='ON_PREMISE') AND (MS_CRM_VERSION == '" + CRM_VERSION + "')))";
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

        public EntityProp(String name, String type, String entityName) {
            this.NAME = name;
            this.edmType = type;
            this.TYPE = this.convertoToTalendType(type);

            if (name.equals(entityName + "id")) {
                KEY = "true";
            }
        }

        @XmlAttribute(name = "KEY")
        String KEY = "false";
        @XmlAttribute(name = "LENGTH")
        String LENGTH = "0";
        @XmlAttribute(name = "NAME")
        String NAME;
        @XmlAttribute(name = "TYPE")
        String TYPE;
        @XmlAttribute(name = "PATTERN")
        String PATTERN;
        //@XmlAttribute
        String edmType;

        private String convertoToTalendType(String edmType) {
            String t = "UNKNOWN";

            switch (edmType) {
                case "Binary":
                    t = "id_byte[]";
                    break;
                case "Boolean":
                    t = "id_Boolean";
                    break;
                case "DateTimeOffset":
                    t = "id_Date";
                    PATTERN = "&quot;yyyy-MM-dd'T'HH:mm:ss'Z'&quot;";
                    break;
                case "Decimal":
                    t = "id_BigDecimal";
                    break;
                case "Double":
                    t = "id_Double";
                    break;
                case "Guid":
                    t = "id_String";
                    break;
                case "Int32":
                    t = "id_Integer";
                    break;
                case "Int64":
                    t = "id_Long";
                    break;
                case "String":
                    t = "id_String";
                    break;
                case "BooleanManagedProperty":
                    t = "id_String";
                    break;
                default:
                    throw new RuntimeException("Type : BooleanManagedProperty");
            }

            return t;
        }
    }

}
