import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.stub.StreamObserver;
import sun.security.krb5.internal.crypto.RsaMd5CksumType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server with TLS enabled.
 */
public class ServerTls {
    private static final Logger logger = Logger.getLogger(ServerTls.class.getName());

    public static final String certificatesPath = "UserCertificates" + File.separator;

    private Server server;
    private final int port;
    private final ServerCredentials creds;
    private static String PDPtarget;

    public ServerTls (int port, ServerCredentials creds, String PDPtarget) {
        this.port = port;
        this.creds = creds;
        ServerTls.PDPtarget = PDPtarget;
    }

    private void start () throws IOException {
        server = Grpc.newServerBuilderForPort(port, creds)
                .addService(new HospitalService())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        logger.setLevel(Level.FINEST);


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

        if (args.length != 4) {
            System.out.println(
                    "USAGE: ServerTls port certChainFilePath privateKeyFilePath PDPaddress:PDPport");
            System.exit(0);
        }

        // If only providing a private key, you can use TlsServerCredentials.create() instead of
        // interacting with the Builder.


        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(new File(args[1]), new File(args[2]));

        tlsBuilder.trustManager(new File(args[3]));
        tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);

        final ServerTls server = new ServerTls(
                Integer.parseInt(args[0]), tlsBuilder.build(), args[4]);

        server.start();
        server.blockUntilShutdown();
    }

    //check permission policy and eventually delegate data retrieval to implementation class
    static class HospitalService extends HospitalServiceGrpc.HospitalServiceImplBase {
        private final ServerImpl serverImpl = ServerImpl.getInstance(PDPtarget);

        //this is a general template to define a method
        //parse request, delegate to implementation and send response
        // the real code of the function should be in the serverImpl class
        @Override
        public void sayHello (HelloRequest request, StreamObserver<HelloReply> responseObserver) {
//          super.sayHello(request, responseObserver);
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
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void retrievePatientInfo (PatientInfoRequest request, StreamObserver<PatientInfoReply> responseObserver) {
//            super.retrievePatientInfo(request, responseObserver);
            PatientInfoReply reply = serverImpl.retrievePatientInfo(request.getPatientID(), request.getRole(), request.getSelectionsList());
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void register (RegisterRequest request, StreamObserver<RegisterReply> responseObserver)  {
//            super.register(request, responseObserver);
            String username = request.getUsername();
            byte[] password = request.getPassword().toByteArray();
            System.err.println("Received password byte[]: " + Arrays.toString(password));
            Role role = request.getRole();
            System.err.println("Received Register Request with: " + username + " ~ " + Arrays.toString(password) + " ~ " + Role.DOCTOR);
            boolean ok = serverImpl.registerUser(username, password, role);
            RegisterReply reply = RegisterReply.newBuilder().setOk(ok).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

        }

        public void registerCertificate(RegisterCertificateRequest request, StreamObserver<RegisterCertificateReply> responseObserver){
            super.registerCertificate(request, responseObserver);

        }
    }


}
