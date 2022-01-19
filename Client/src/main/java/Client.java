import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private final HospitalServiceGrpc.HospitalServiceBlockingStub blockingStub;

    private static Role userRole = null;
    private static String loggedUser = null;

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

    /**
     *
     * @return {@code 0} if login is successful;
     * {@code -1} otherwise
     * @param host is the IP of the server
     */
    private LoginReply.Code login (String host) {
        System.out.println("Username:");
        String username = System.console().readLine();
        System.out.println("Password:");
        char[] password = System.console().readPassword();

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(ByteString.copyFrom(toBytes(password)))
                .build();
        LoginReply reply = blockingStub.login(request);

        if(reply.getCode() == LoginReply.Code.SUCCESS) {
            userRole = reply.getRole();
            loggedUser = username;
        }

        return reply.getCode();
    }

    private MedicalRecords retrievePatientInfo (int id, List<Integer> selectedNumbers) {
        PatientInfoRequest request = PatientInfoRequest.newBuilder()
                .setPatientID(id)
                .setRole(userRole).build();
        PatientInfoReply reply = blockingStub.retrievePatientInfo(request);
        return reply.getRecords();
    }



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
            client.greet(host);

            LoginReply.Code loginCode = LoginReply.Code.UNRECOGNIZED;
            //LOGIN ONLY ONCE, TO LOG WITH A DIFFERENT USER, JUST QUIT AND RERUN THE CLIENT FOR SIMPLICITY
            while (!loginCode.equals(LoginReply.Code.SUCCESS)) { //REPEAT LOGIN UNTIL SUCCESSFUL
                loginCode = client.login(host);
                switch (loginCode) {
                    case WRONGPASS:
                        System.out.println("Incorrect Password");
                    case UNRECOGNIZED:
                        System.out.println("You are not registered!");
                    case SUCCESS:
                        System.out.println("Welcome!!");
                }
            }
            //AFTER SUCCESSFUL LOGIN, A USER INTERACTION LOOP STARTS UNTIL LOGOUT
            System.out.println("Insert PatientID to retrieve info, -1 to logout");
            int id;
            try {
                id = Integer.parseInt(System.console().readLine());
            } catch (Exception e){
                id = -1;
            }
            while (id > 0){ //DO STUFF UNTIL LOGOUT

                boolean legalSelections = false;
                List<Integer> selectedNumbers = new ArrayList<Integer>();

                while (!legalSelections) { //REPEAT SELECTION UNTIL VALID
                    legalSelections = true;

                    System.out.println("Select the information you would like to retrieve:");
                    System.out.println("Options:");
                    System.out.println("[1] -> Name Surname");
                    System.out.println("[2] -> Personal Information (home address, email, health number)");
                    System.out.println("[3] -> Health Issues");
                    System.out.println("[4] -> Prescribed Medications");
                    System.out.println("[5] -> Health History");
                    System.out.println("[6] -> Allergies");
                    System.out.println("[7] -> Past visits history");
                    System.out.println("[8] -> Complete Medical Records");
                    System.out.println("Insert either a list of selections or 8, followd by ENTER.\nDo not insert 8 along with other selections, please:");
                    String selections = System.console().readLine();
                    StringTokenizer tokenizer = new StringTokenizer(selections);

                    if (tokenizer.countTokens() > 0) {
                        while (tokenizer.hasMoreTokens()) {
                            String token = tokenizer.nextToken();
                            try {
                                int oneSelection = Integer.parseInt(token);
                                selectedNumbers.add(oneSelection);
                            } catch (Exception badSelectionFormat) {
                                legalSelections = false;
                                System.out.println("'" + token + "' is not a valid selction! Select again...");
                            }
                        }
                    } else { // NO SELECTIONS
                        System.out.println("You must select something");
                        legalSelections = false;
                    }
                    //EVENTUALLY THE USER WILL SELECT SOMETHING VALID, THE REQUEST TO THE SERVER IS THEN MADE
                    MedicalRecords patientRecords = client.retrievePatientInfo(id, selectedNumbers);
                    client.printRecords(patientRecords);
                }
                System.out.println("Insert another PatientID to retrieve info, -1 to logout");
                try {
                    id = Integer.parseInt(System.console().readLine());
                } catch (Exception e){
                    id = -1;
                }
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return bytes;
    }

    private void printRecords(MedicalRecords records){
        StringBuilder builder = new StringBuilder();
        for(Object o : records.getAllFields().values()){
            builder.append(o.toString());
            builder.append("\n");
        }

        System.out.println(builder);
    }




}
