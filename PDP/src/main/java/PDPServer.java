import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDPServer {
    private static final Logger logger = Logger.getLogger(PDPServer.class.getName());

    private final int port;
    private Server server;
    private static Balana balana;
    private static PDP pdp;


    public PDPServer(int port) {
        this.port = port;
        initBalana();
        pdp = getPDPNewInstance();
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
            String evaluation = pdp.evaluate(request.getXacmlRequest());
            System.out.println("EVALUATED REQUEST. OUTCOME: ");
            System.out.println(evaluation);
        }
    }

}
