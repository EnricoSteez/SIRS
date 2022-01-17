import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;


/**
 * Server that manages startup/shutdown of a {@code Greeter} server with TLS enabled.
 */
public class ServerTls {
    private static final Logger logger = Logger.getLogger(ServerTls.class.getName());

    private Server server;

    private final int port;
    private final ServerCredentials creds;

    public ServerTls (int port, ServerCredentials creds) {
        this.port = port;
        this.creds = creds;
    }

    private void start () throws IOException {
        server = Grpc.newServerBuilderForPort(port, creds)
                .addService(new Service())
                .build()
                .start();
        logger.info("Server started, listening on " + port);


        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run () {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                ServerTls.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop () {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown () throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main (String[] args) throws IOException, InterruptedException {
        if (args.length < 3 || args.length > 4) {
            System.out.println(
                    "USAGE: ServerTls port certChainFilePath privateKeyFilePath " +
                            "[trustCertCollectionFilePath]\n  Note: You only need to supply trustCertCollectionFilePath if you want " +
                            "to enable Mutual TLS.");
            System.exit(0);
        }

        // If only providing a private key, you can use TlsServerCredentials.create() instead of
        // interacting with the Builder.

        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(new File(args[1]), new File(args[2]));
        if (args.length == 4) {
            tlsBuilder.trustManager(new File(args[3]));
            tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);
        }
        final ServerTls server = new ServerTls(
                Integer.parseInt(args[0]), tlsBuilder.build());
        server.start();
        server.blockUntilShutdown();
    }

    //check permission policy and eventually delegate data retrieval to implementation class
    static class Service extends HospitalServiceGrpc.HospitalServiceImplBase {
        private final ServerImpl serverImpl = new ServerImpl();

        //this is a general template to define a method
        //parse request, delegate to implementation and send response
        // the real code of the function should be in the serverImpl class
        @Override
        public void sayHello (HelloRequest request, StreamObserver<HelloReply> responseObserver) {
//          super.sayHello(request, responseObserver);
            String clientId = Constants.CLIENT_ID_CONTEXT_KEY.get();

            String message = serverImpl.sayHello(request.getName());

            HelloReply reply = HelloReply
                    .newBuilder()
                    .setMessage(message)
                    .build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void login (LoginRequest request, StreamObserver<LoginReply> responseObserver) {
//            super.login(request, responseObserver);
            LoginReply.Code code = serverImpl.tryLogin(request.getUsername(), request.getPassword());
            LoginReply reply = LoginReply.newBuilder().setCode(code).build();

        }

        @Override
        public void retrievePatientInfo (PatientInfoRequest request, StreamObserver<PatientInfoReply> responseObserver) {
//            super.retrievePatientInfo(request, responseObserver);
            PatientInfo info = serverImpl.retrievePatientInfo(request.getPatientID(),request.getRole());
        }
    }

}
