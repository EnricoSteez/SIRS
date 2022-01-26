//import com.att.research.xacml.api.pdp.PDPEngine;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
/*import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Result;
import org.ow2.authzforce.core.pdp.api.XmlUtils;
import org.ow2.authzforce.core.pdp.api.io.PdpEngineInoutAdapter;
import org.ow2.authzforce.core.pdp.api.io.XacmlJaxbParsingUtils;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.ow2.authzforce.core.pdp.impl.io.PdpEngineAdapters;*/
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDPServer {
    private static final Logger logger = Logger.getLogger(PDPServer.class.getName());

    private final int port;
    private Server server;
    private static Balana balana;
    private static PDP pdp;
    //private static PDPEngine pdpEngine;
    //private static PdpEngineInoutAdapter<Request, Response> authzforcePdp;


    public PDPServer(int port) {
        this.port = port;

//        BALANA
        initBalana();
        pdp = getPDPNewInstance();

////        AT&T (THROWS ERROR)
//        PDPEngineFactory factory = null;
//        try {
//            factory = PDPEngineFactory.newInstance();
//            pdpEngine = factory.newEngine();
//        } catch (FactoryException e) {
//            e.printStackTrace();
//            System.exit(0);
//        }

//        AUTHZFORCE (CAN'T LOCATE THE CONFIGURATION)
//        try {
//            PdpEngineConfiguration conf = PdpEngineConfiguration.getInstance("classpath:resources/pdpconfig.xml");
//            authzforcePdp = PdpEngineAdapters.newXacmlJaxbInoutAdapter(conf);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private static void initBalana() {
        try {
            // using file based policy repository. so set the policy location as system property
            String policyLocation = (new File(".")).getCanonicalPath() + File.separator + "resources";
            System.setProperty(FileBasedPolicyFinderModule.POLICY_DIR_PROPERTY, policyLocation);
        } catch (IOException e) {
            System.err.println("Can not locate policy repository");
        }
        // create default instance of Balana
        balana = Balana.getInstance();
    }

    /**
     * Returns a new PDP instance with new XACML policies
     *
     * @return a  PDP instance
     */
    private static PDP getPDPNewInstance(){
//        PDPConfig pdpConfig = balana.getPdpConfig();
//        // registering new attribute finder. so default PDPConfig is needed to change
//        AttributeFinder attributeFinder = pdpConfig.getAttributeFinder();
//        List<AttributeFinderModule> finderModules = attributeFinder.getModules();
//        finderModules.add(new MedicalRecordsAttributeFinderModule());
//        attributeFinder.setModules(finderModules);
//        return new PDP(new PDPConfig(attributeFinder, pdpConfig.getPolicyFinder(), null, true));
//
//        I THINK THAT DEFAULT CONFIG IS FINE IN THIS CASE (???)
        return new PDP(balana.getPdpConfig());
    }

    /** Start serving requests. */
    private void start () throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new AccessControlService())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        logger.setLevel(Level.INFO);
    }

    /** Stop serving requests and shutdown resources. */
    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        final PDPServer server = new PDPServer(8980);

        server.start();
        server.blockUntilShutdown();
    }

    static class AccessControlService extends AccessControlServiceGrpc.AccessControlServiceImplBase {
        @Override
        public void dummyValidationForTesting (DummyValidationRequest request, StreamObserver<DummyValidationReply> responseObserver) {
//            super.dummyValidationForTesting(request, responseObserver);
            DummyValidationReply reply = DummyValidationReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void validateAccess (AccessControlRequest request, StreamObserver<AccessControlReply> responseObserver) {
//            super.validateAccess(request, responseObserver);

////           ****************************** BALANA ******************************

            String balanaEvaluation = pdp.evaluate(request.getXacmlRequest());
            System.out.println("EVALUATED REQUEST.\nBALANA OUTCOME: ");
            System.out.println(balanaEvaluation);

////           ****************************** AT&T ******************************
//            Response attResponse = null;
//            Request pepRequest = null;
//            try {
//                pepRequest = DOMRequest.load(request.getXacmlRequest());
//                attResponse = pdpEngine.decide(pepRequest);
//            } catch (DOMStructureException|PDPException e) {
//                e.printStackTrace();
//            }
//            assert attResponse != null;

//            System.out.println("AT&T OUTCOME: ");
//            System.out.println(attResponse);

////          ******************************  AUTHZFORCE ******************************
//            Response authzforceEvaluation = null;
//            try {
//                XmlUtils.XmlnsFilteringParser xacmlParserFactory = XacmlJaxbParsingUtils.getXacmlParserFactory(false).getInstance();
//                InputSource source = new InputSource(request.getXacmlRequest());
//                Object authzforceRequest = xacmlParserFactory.parse(source);
//                if(authzforceRequest instanceof Request) {
//                    authzforceEvaluation = authzforcePdp.evaluate((Request) authzforceRequest);
//                }
//            } catch (JAXBException e) {
//                e.printStackTrace();
//            }
//
//            assert authzforceEvaluation != null;

//
//            System.out.println("EVALUATED REQUEST.\nAUTHZFORCE OUTCOME: ");
//            System.out.println(authzforceEvaluation);

            AccessControlReply reply = AccessControlReply.newBuilder().setXacmlReply(balanaEvaluation).build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

}
