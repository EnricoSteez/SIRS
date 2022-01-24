import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDPServer {
    private static final Logger logger = Logger.getLogger(PDPServer.class.getName());

    private final int port;
    private Server server;

    public PDPServer(int port) {
        this.port = port;
    }

    /** Start serving requests. */
    private void start () throws IOException {
        server = ServerBuilder.forPort(port)
//                .addService(new XACMLService())
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

    static class AccessControl extends AccessControlServiceGrpc.AccessControlServiceImplBase {
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

        }
    }

}
