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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GenJavajetConf {

    private final static Map<String, String> hardCodedException = new HashMap<>();
    static{
        hardCodedException.put("subscriptionsyncentryoffline", "subscriptionsyncentriesoffline");
        hardCodedException.put("subscriptionsyncentryoutlook", "subscriptionsyncentriesoutlook");
        hardCodedException.put("knowledgearticlescategories", "knowledgearticlecategories");
    }

    private final static List<String> neededEntities = new ArrayList<>();

    private final static List<MSCRMEntity> entities = new ArrayList<>();


    private static Properties credentials = new Properties();
    static {
        try{
            credentials.load(new FileInputStream("/tmp/mscrm.cred"));
        }
        catch (Exception e){
            System.out.println("Can't read credentials : " + e.getMessage());
        }
    }


    public final static void main(String[] args) {

        try {
            initNeededEntities();

            ODataClient client = ODataClientFactory.getClient();
            client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);

            ExecutorService service = Executors.newFixedThreadPool(1);
            AuthenticationContext context = new AuthenticationContext("https://login.windows.net/common/oauth2/authorize", false, service);
            Future<AuthenticationResult> authenticationResultFuture = acquireToken(context);
            AuthenticationResult authenticationResult = authenticationResultFuture.get();
            String token = authenticationResult.getAccessToken();


            String serviceUrl = credentials.getProperty("service");
            EdmMetadataRequest metadataRequest = client.getRetrieveRequestFactory().getMetadataRequest(serviceUrl);
            metadataRequest.addCustomHeader(HttpHeader.AUTHORIZATION, "Bearer " + token);
            final Edm edm = metadataRequest.execute().getBody();
            EdmSchema dynCRMSchema = edm.getSchema("Microsoft.Dynamics.CRM");

            Path p = Paths.get("/tmp/notNeeded.out");
            Path pnf = Paths.get("/tmp/neededAndFound.out");


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
                            ent.props.add(new EntityProp(pr, t.getProperty(pr).getType().getName()));
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


        System.out.println("----------------------------------");
        System.out.println("Needed but not computed : " + neededEntities.size());

        Path pnnf = Paths.get("/tmp/neededNotFound.out");
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



        System.out.println("Done.");

    }


    private static String needed(String n) {
        if(neededEntities.remove(n)){
            return n;
        }

        String ns = n+"s";
        if(neededEntities.remove(ns)){
            return ns;
        }

        if(n.endsWith("y")){
            String nies = n.substring(0, n.length() - 1) + "ies";
            if(neededEntities.remove(nies)){
                return nies;
            }
        }

        if(n.endsWith("us")){
            String uses = n + "es";
            if(neededEntities.remove(uses)){
                return uses;
            }
        }

        if(n.endsWith("x")){
            String xes = n + "es";
            if(neededEntities.remove(xes)){
                return xes;
            }
        }

        if(n.endsWith("ss")){
            String sses = n + "es";
            if(neededEntities.remove(sses)){
                return sses;
            }
        }

        if(n.endsWith("s")){
            String coll = n + "collection";
            if(neededEntities.remove(coll)){
                return coll;
            }
        }

        String set = n + "set";
        if(neededEntities.remove(set)){
            return set;
        }

        if(n.endsWith("categories")){
            String scat = n.substring(0, n.length() - "categories".length()) + "scategories";
            if(neededEntities.remove(scat)){
                return scat;
            }
        }

        if(hardCodedException.containsKey(n)){
            if(neededEntities.remove(hardCodedException.get(n))){
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
            Path p = Paths.get("/home/ypiel/dev/Jira/TDI-40979_MSCRM_V9/v9EntitySet_purged.out");
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

    private final static class MSCRMEntity {

        public MSCRMEntity(String name, String pluralName){
            this.name = name;
            this.pluralName = pluralName;
        }

        String name;
        String pluralName;
        List<EntityProp> props = new ArrayList<>();
    }

    private final static class EntityProp {

        public EntityProp(String name, String type){
            this.name = name;
            this.type = type;
        }

        String name;
        String type;
    }

}
