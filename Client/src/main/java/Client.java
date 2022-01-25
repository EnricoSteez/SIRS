import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private final HospitalServiceGrpc.HospitalServiceBlockingStub blockingStub;
    private static final String signatureAlg = "SHA256withRSA";
    private static Role userRole = null;
    private Random rand = new Random();
//    private static String loggedUser = null;

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public Client(Channel channel) {
        blockingStub = HospitalServiceGrpc.newBlockingStub(channel);
        logger.setLevel(Level.FINEST);
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
     *
     * @return {@code 0} if login is successful;
     * {@code -1} otherwise
     */
    private LoginReply.Code login () {
        System.out.println("------------------------------ LOGIN TO PROCEED ------------------------------");
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
//            loggedUser = username;
            String userID = reply.getUserId();
        }
        return reply.getCode();
    }

    private PatientInfoReply retrievePatientInfo (int id, List<Integer> selectedNumbers) {

        System.err.println("About to send request for Patient info containing:");
        System.err.println("Patient ID:" + id);
        System.err.println("My ROLE:" + userRole);
        System.err.println("Selections:" + Arrays.toString(selectedNumbers.toArray()));

        PatientInfoRequest.Builder request = PatientInfoRequest.newBuilder()
                .setPatientID(id)
                .setRole(userRole)
                .addAllSelections(selectedNumbers);

//        for(int i = 0 ; i < selectedNumbers.size() ; i++) {
//            request.setSelections(i+1, selectedNumbers.get(i));
//        }

        PatientInfoReply reply = blockingStub.retrievePatientInfo(request.build());
        return reply;
    }

    private void register (){
        boolean successfulRegister = false;

        while(!successfulRegister) {
            System.out.println("--------------HELLO ADMIN! HERE YOU CAN REGISTER ONE OR MORE NEW ACCOUNTS--------------");

            System.out.println("Username:");
            String username = System.console().readLine();
            System.out.println("Password:");
            char[] password = System.console().readPassword();
            byte[] passwordBytes = toBytes(password);
//            System.err.println("Byte converted password: " + Arrays.toString(passwordBytes));
            System.out.println("Role:");
            System.out.println(
                    "[1] => LAB_EMPLOYEE\n" +
                    "[2] => DOCTOR\n" +
                    "[3] => NURSE\n" +
                    "[4] => PATIENT_SERVICES_ASSISTANT\n" +
                    "[5] => CLINICAL_ASSISTANT\n" +
                    "[6] => PORTER_VOLUNTEER\n" +
                    "[7] => WARD_CLERK\n"
            );
            String roleInput = System.console().readLine();
            int selection;
            try {
                 selection = Integer.parseInt(roleInput);
                 if(selection < 1 || selection > 7) {
                     System.out.println("Choose wisely...");
                     continue;
                 }
            } catch (NumberFormatException e) {
                e.printStackTrace();
                System.out.println("Choose wisely...");
                continue;
            }
            Role role = Role.forNumber(selection);

            RegisterRequest request = RegisterRequest.newBuilder()
                    .setUsername(username)
                    .setPassword(ByteString.copyFrom(passwordBytes))
                    .setRole(role)
                    .build();
            System.err.println("Register Request is: " + username + " ~ " + Arrays.toString(password) + " ~ " + Role.DOCTOR);
            RegisterReply reply = blockingStub.register(request);

            successfulRegister = reply.getOk();
        }
    }

    private void registerCertificate(String certificatePath, String privateKeyPath){
        try {
            System.out.println("Registering certificate");
            String certificateStr = RSAOperations.readFile(certificatePath);
            String nonce = Integer.toString(rand.nextInt());
            RSAPrivateKey privateKey = RSAOperations.getPrivateKeyFromFile(privateKeyPath);
            String signedNonce = RSAOperations.sign(privateKey, nonce, signatureAlg);
            SignatureM signature = SignatureM.newBuilder()
                    .setSignature(signedNonce)
                    .setCryptAlgo(signatureAlg)
                    .setNonce(rand.nextInt())
                    .build();
            //System.out.println(certificateStr);
            RegisterCertificateRequest request = RegisterCertificateRequest.newBuilder()
            //pass also id
                    .setCertificate(certificateStr)
                    .setNonce(nonce)
                    .setSignedNonce(signature)
                    .build();
            RegisterCertificateReply reply = blockingStub.registerCertificate(request);

            if(reply.getOk()){
                System.out.println("Successfully registered certificate");
            }else{
                System.out.println("There was a problem registering the certificate");
            }


        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Problem reading certificate or private key");
        }
    }

    public static void main(String[] args) throws Exception {

//        registerCertificate("../Keys/server.crt");
    }

    public static void main2(String[] args) throws Exception {

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
//                .overrideAuthority("foo.test.google.fr")
                .build();
        try {
            Client client = new Client(channel);
            client.greet(host);
            boolean retry = true;
            int nextOp=0;

//          ************************************************** LOGIN **************************************************

            LoginReply.Code loginCode = LoginReply.Code.UNRECOGNIZED;
            //LOGIN ONLY ONCE, TO LOG WITH A DIFFERENT USER, JUST QUIT AND RERUN THE CLIENT FOR SIMPLICITY
            while (!loginCode.equals(LoginReply.Code.SUCCESS)) { //REPEAT LOGIN UNTIL SUCCESSFUL
                loginCode = client.login();
                switch (loginCode) {
                    case WRONGPASS:
                        System.out.println("Incorrect Password");
                        break;
                    case WRONGUSER:
                        System.out.println("You are not registered!");
                        break;
                    case SUCCESS:
                        System.out.println("Welcome!!");
                        break;
                }
            }
//          ************************************************** REGISTER ACCOUNTS [ADMIN ONLY] **************************************************
            if(userRole == Role.ADMIN){
                while(true) {
                    System.out.println("Options:");
                    System.out.println("[1] -> REGISTER NEW EMPLOYEE");
                    System.out.println("[2] -> Head to Patients' Medical Records");
                    String choice = System.console().readLine();
                    try {
                        nextOp = Integer.parseInt(choice);
                    } catch (NumberFormatException e) {
                        System.out.println();
                        System.out.println(choice + "It's easy to choose, there are just two options...");
                        continue;
                    }

                    if(nextOp == 1)
                        client.register();
                    else if(nextOp == 2)
                        break;
                    else
                        System.out.println(choice + "It's easy to choose, there are just two options...");
                }
            }
//          ************************************************** PATIENTS' MEDICAL RECORDS RETRIEVAL **************************************************

            //AFTER SUCCESSFUL LOGIN, A USER INTERACTION LOOP STARTS UNTIL LOGOUT
            System.out.println("Insert PatientID to retrieve info, -1 to logout");
            int id;
            while(true) {
                try {
                    id = Integer.parseInt(System.console().readLine());
                } catch (Exception e) {
                    continue;
                }
                break;
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
                    System.out.println("Insert either a list of selections or 8, followed by ENTER.\nDo not insert 8 along with other selections, please:");
                    String selections = System.console().readLine();
                    StringTokenizer tokenizer = new StringTokenizer(selections);

                    if (tokenizer.countTokens() > 0) {
                        while (tokenizer.hasMoreTokens()) {
                            String token = tokenizer.nextToken();
                            try {
                                int oneSelection = Integer.parseInt(token);
                                selectedNumbers.add(oneSelection);
                            } catch (Exception e) {
                                e.printStackTrace();
                                legalSelections = false;
                                System.out.println("'" + token + "' is not a valid selection! Select again...");
                            }
                            if(selectedNumbers.contains(8) && selectedNumbers.size()>1){
                                System.out.println("If you select more than a number, the selections must not include 8!");
                                legalSelections=false;
                            }
                        }
                    } else { // NO SELECTIONS
                        System.out.println("You must select something");
                        legalSelections = false;
                    }
                    //EVENTUALLY THE USER WILL SELECT SOMETHING VALID, THE REQUEST TO THE SERVER IS THEN MADE
                    PatientInfoReply reply = client.retrievePatientInfo(id, selectedNumbers);
                    if(reply.getPermission())
                        client.printRecords(reply.getRecords());
                    else {
                        System.out.println("PERMISSION DENIED");
                        System.out.println(reply.getPdpAdvice());
                    }
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
        System.out.println("Received Medical Records:");

        StringBuilder builder = new StringBuilder();
        for(Object o : records.getAllFields().values()){
            builder.append(o.toString());
            builder.append("\n");
        }

        System.out.println(builder);
    }




}
