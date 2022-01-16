import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private final HospitalServiceGrpc.HospitalServiceBlockingStub blockingStub;

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public Client(Channel channel) {
        blockingStub = HospitalServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Say hello to server.
     */
    public void greet(String name) {
        logger.info("Will try to greet " + name + " ...");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {

        if (args.length < 2 || args.length == 4 || args.length > 5) {
            System.out.println("USAGE: Client host port [trustCertCollectionFilePath " +
                    "[clientCertChainFilePath clientPrivateKeyFilePath]]\n  Note: clientCertChainFilePath and " +
                    "clientPrivateKeyFilePath are only needed if mutual auth is desired.");
            System.exit(0);
        }

        // If only defaults are necessary, you can use TlsChannelCredentials.create() instead of
        // interacting with the Builder.
        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
        switch (args.length) {
            case 5:
                tlsBuilder.keyManager(new File(args[3]), new File(args[4]));
                // fallthrough
            case 3:
                tlsBuilder.trustManager(new File(args[2]));
                // fallthrough
            default:
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        ManagedChannel channel = Grpc.newChannelBuilderForAddress(host, port, tlsBuilder.build())
                /* Only for using provided test certs. */
                .overrideAuthority("foo.test.google.fr")
                .build();
        try {
            Client client = new Client(channel);
            client.login();
            String id = inputPatientId();

            while(!id.equals("-1")){
                retrievePatientInfo();
            }
            client.greet(host);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
