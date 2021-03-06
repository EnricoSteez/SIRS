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
    public static final ReadPropertyFile config = new ReadPropertyFile();

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

    private static void checkCertificatesDir(){
        File directory = new File(certificatesPath);
        if (! directory.exists()){
            directory.mkdir();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main (String[] args) throws IOException, InterruptedException {

        if (args.length != 2) {
            System.out.println(
                    "USAGE: ServerTls port PDPaddress:PDPport");
            System.exit(0);
        }

        //check if it is needed to create a Certificates Directory
        checkCertificatesDir();


        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(new File(config.getProperty("certificate_path")), new File(config.getProperty("private_key_path")));

        final ServerTls server = new ServerTls(
                Integer.parseInt(args[0]), tlsBuilder.build(), args[1]);

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
            LoginReply reply = serverImpl.login(request.getUsername(), request.getPassword());
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void retrievePatientInfo (PatientInfoRequest request, StreamObserver<PatientInfoReply> responseObserver) {
//            super.retrievePatientInfo(request, responseObserver);
            AuthenticationManager.TokenValue tv = serverImpl.authManager.getUser(request.getToken());
            if(tv != null){
                Role role = tv.userRole;
                PatientInfoReply reply = serverImpl.retrievePatientInfo(request.getPatientId(), role, request.getSelectionsList());
                responseObserver.onNext(reply);
            }else{
                PatientInfoReply reply = PatientInfoReply.newBuilder()
                        .setPermission(false)
                        .setErrorType(ErrorType.NOT_LOGGED_IN)
                        .build();

                responseObserver.onNext(reply);
            }
            responseObserver.onCompleted();

        }

        @Override
        public void register (RegisterRequest request, StreamObserver<RegisterReply> responseObserver)  {
//            super.register(request, responseObserver);
            AuthenticationManager.TokenValue tv = serverImpl.authManager.getUser(request.getToken());

            if(tv != null) {
                //TODO do with PDP
                if(tv.userRole != Role.ADMIN){
                    RegisterReply reply = RegisterReply.newBuilder()
                            .setOk(false)
                            .setErrorType(ErrorType.NOT_AUTHORIZED)
                            .build();
                    responseObserver.onNext(reply);
                }else{
                    String username = request.getUsername();
                    byte[] password = request.getPassword().toByteArray();
                    System.err.println("Received password byte[]: " + Arrays.toString(password));
                    System.err.println("Received Register Request with: " + username + " ~ " + Arrays.toString(password) + " ~ " + Role.DOCTOR);
                    RegisterReply reply = serverImpl.registerUser(username, password, request.getChosenRole());
                    responseObserver.onNext(reply);
                }

            }else{
                RegisterReply reply = RegisterReply.newBuilder()
                        .setOk(false)
                        .setErrorType(ErrorType.NOT_LOGGED_IN)
                        .build();
                responseObserver.onNext(reply);
            }

            responseObserver.onCompleted();

        }

        @Override
        public void registerCertificate(RegisterCertificateRequest request, StreamObserver<RegisterCertificateReply> responseObserver){
            //super.registerCertificate(request, responseObserver);
            AuthenticationManager.TokenValue tv = serverImpl.authManager.getUser(request.getToken());
            if(tv != null){
                int userId = tv.userId;
                RegisterCertificateReply reply = serverImpl.registerCertificate(userId, request.getCertificate(),
                        request.getNonce().getBytes(), request.getSignedNonce());
                responseObserver.onNext(reply);
            }else{
                RegisterCertificateReply reply = RegisterCertificateReply.newBuilder()
                        .setOk(false)
                        .setErrorType(ErrorType.NOT_LOGGED_IN)
                        .build();
                responseObserver.onNext(reply);
            }
            responseObserver.onCompleted();

        }

        @Override
        public void writePatientInfo(WritePatientInfoRequest request, StreamObserver<WritePatientInfoReply> responseObserver) {
            //super.writePatientInfo(request, responseObserver);
            AuthenticationManager.TokenValue tv = serverImpl.authManager.getUser(request.getToken());
            System.out.println("\nWriting patient info:");
            if(tv != null){
                int userId = tv.userId;
                Role role = tv.userRole;
                WritePatientInfoReply reply = serverImpl.writePatientInfo(userId,role, request);
                responseObserver.onNext(reply);
            }else{
                WritePatientInfoReply reply = WritePatientInfoReply.newBuilder()
                        .setOk(false)
                        .setErrorType(ErrorType.NOT_LOGGED_IN)
                        .build();
                responseObserver.onNext(reply);
            }
            responseObserver.onCompleted();

        }

        @Override
        public void checkCertificate(CheckCertificateRequest request, StreamObserver<CheckCertificateReply> responseObserver){
            AuthenticationManager.TokenValue tv = serverImpl.authManager.getUser(request.getToken());
            if(tv != null){
                int userId = tv.userId;
                CheckCertificateReply reply = serverImpl.checkCertificate(userId, request);
                responseObserver.onNext(reply);
            }else{
                CheckCertificateReply reply = CheckCertificateReply.newBuilder()
                        .setValid(false)
                        .setErrorType(ErrorType.NOT_LOGGED_IN)
                        .build();
                responseObserver.onNext(reply);
            }
            responseObserver.onCompleted();
        }
    }


}
