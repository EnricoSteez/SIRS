import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private final HospitalServiceGrpc.HospitalServiceBlockingStub blockingStub;
    private static final String signatureAlg = "SHA256withRSA";
    private static Role userRole = Role.ADMIN;
    private int userID = 1;
    private static RSAPrivateKey privateKey;
    private static String certificate;
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
            userID = reply.getUserId();
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

    private void writeMedicalRecord(){
        System.out.println("--------------WRITING MEDICAL RECORD--------------");
        System.out.println("Patient name and surname:");
        String nameSurname = System.console().readLine();
        System.out.println("Patient email:");
        String email = System.console().readLine();
        System.out.println("Patient home address:");
        String homeAddress = System.console().readLine();
        System.out.println("Patient health number:");
        int healthNumber = Integer.parseInt(System.console().readLine());
        System.out.println("Patient health history:");
        String healthHistory = System.console().readLine();
        System.out.println("Patient allergies:");
        String allergies = System.console().readLine();

        PatientInfo patientInfo = PatientInfo.newBuilder()
                .setNameSurname(nameSurname)
                .setEmail(email)
                .setHomeAddress(homeAddress)
                .setHealthNumber(healthNumber)
                .setHealthHistory(healthHistory)
                .setAllergies(allergies)
                .build();


        try {
            ByteString signature = ByteString.copyFrom(RSAOperations.sign(privateKey, patientInfo.toByteArray(), signatureAlg));

            SignatureM signatureM = SignatureM.newBuilder()
                    .setCryptAlgo(signatureAlg)
                    .setNonce(rand.nextInt())
                    .setSignature(signature)
                    .build();



            WritePatientInfoRequest request = WritePatientInfoRequest.newBuilder()
                    .setPatientInfo(patientInfo)
                    .setUserID(userID)
                    .build();

            WritePatientInfoReply reply = blockingStub.writePatientInfo(request);

            boolean successful = reply.getOk();
            if (successful){
                System.out.println("Successfully created patient record!");
            }else{
                System.out.println("Problem creating patient record!");
                //TODO error message acording to type
            }
        } catch (Exception e) {
            System.out.println("Error signing with private key");
        }

    }

    private void registerNewAccount (){
        boolean successfulRegister = false;

        while(!successfulRegister) {
            System.out.println("--------------HELLO ADMIN! HERE YOU CAN REGISTER NEW ACCOUNTS--------------");

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
                    "[7] => WARD_CLERK\n" +
                    "[8] => ADMIN"
            );
            String roleInput = System.console().readLine();
            int selection;
            try {
                 selection = Integer.parseInt(roleInput);
                 if(selection < 1 || selection > 8) {
                     System.out.println("Choose wisely...");
                     continue;
                 }
            } catch (NumberFormatException e) {
                e.printStackTrace();
                System.out.println("Choose wisely...");
                continue;
            }
            //ROLES START FROM ZERO AND SELECTIONS ARE USER-FRIENDLY
            Role role = Role.forNumber(selection-1);

            System.out.println("You have chosen:");
            System.out.println("USERNAME:" + username);
            System.out.println("PASSWORD:" + Arrays.toString(password));
            assert role != null;
            System.out.println("ROLE:" + role.name());


            RegisterRequest request = RegisterRequest.newBuilder()
                    .setUsername(username)
                    .setPassword(ByteString.copyFrom(passwordBytes))
                    .setRole(role)
                    .build();
            System.err.println("Register Request is: " + username + " ~ " + Arrays.toString(password) + " ~ " + role.name());
            RegisterReply reply = blockingStub.register(request);

            successfulRegister = reply.getOk();
            if(successfulRegister)
                System.out.println("ALL GOOD!");
            else
                System.out.println("BAD OUTCOME");
        }
    }

    private void registerCertificate(){
        try {
            System.out.println("Registering certificate");
            String nonce = Integer.toString(rand.nextInt());
            byte[] signedNonce = RSAOperations.sign(privateKey, nonce.getBytes(), signatureAlg);
            SignatureM signature = SignatureM.newBuilder()
                    .setSignature(ByteString.copyFrom(signedNonce))
                    .setCryptAlgo(signatureAlg)
                    .setNonce(rand.nextInt())
                    .build();
            //System.out.println(certificateStr);
            RegisterCertificateRequest request = RegisterCertificateRequest.newBuilder()
            //pass also id
                    .setCertificate(certificate)
                    .setNonce(nonce)
                    .setSignedNonce(signature)
                    .setUserId(userID)
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


        if (args.length < 2 || args.length == 4 || args.length > 5) {
            System.out.println("USAGE: Client host port [trustCertCollectionFilePath " +
                    "[clientCertChainFilePath clientPrivateKeyFilePath]]\n  Note: clientCertChainFilePath and " +
                    "clientPrivateKeyFilePath are only needed if mutual auth is desired.");
            System.exit(0);
        }

        //TODO get from arguments:
        privateKey = RSAOperations.getPrivateKeyFromFile("../Keys/server.key");
        certificate = RSAOperations.readFile("../Keys/server.crt");

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
            if(userRole == Role.ADMIN) //HACK ALERT!! COMMENT THIS LINE IF YOU WANT TO REGISTER ADMIN ACCOUNTS FOR TESTING!
            {
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
                        client.registerNewAccount();
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
                    int countRead = 0;
                    int countWrite = 0;

                    System.out.println("CHOOSE OPTIONS FROM ONLY ONE OF THE GROUPS\n\n");
                    System.out.println("****************************** RETRIEVE PATIENTS INFO ******************************\n");
                    System.out.println("[1] -> Name Surname");
                    System.out.println("[2] -> Personal Information (home address, email, health number)");
                    System.out.println("[3] -> Health Issues");
                    System.out.println("[4] -> Prescribed Medications");
                    System.out.println("[5] -> Health History");
                    System.out.println("[6] -> Allergies");
                    System.out.println("[7] -> Past visits history");
                    System.out.println("[8] -> Lab Results");
                    System.out.println("[9] -> Complete Medical Records");
                    System.out.println("Insert either a list of selections or 9, followed by ENTER.\nDo not insert 9 along with other selections, please:");
                    System.out.println();
                    System.out.println("****************************** UPDATE PATIENTS INFO ******************************\n");
                    System.out.println("[10] -> Name Surname");
                    System.out.println("[11] -> Personal Information (home address, email, health number)");
                    System.out.println("[12] -> Health Issues");
                    System.out.println("[13] -> Prescribed Medications");
                    System.out.println("[14] -> Health History");
                    System.out.println("[15] -> Allergies");
                    System.out.println("[16] -> Past visits history");
                    System.out.println("[17] -> Lab Results");
                    System.out.println("[18] -> Complete Medical Records\n\n");
                    System.out.println("Insert either a list of selections or 18, followed by ENTER.\nDo not insert 9 along with other selections, please:");


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

                            for(int i : selectedNumbers) {
                                if(i>0 && i<10)
                                    countRead++;
                                else if(i>=10 && i<19)
                                    countWrite++;
                                else
                                    legalSelections = false;
                            }
                            if(countRead>0 && countWrite>0) {
                                System.out.println("You cannot read and write at the same time");
                                legalSelections=false;
                            }
                            if((selectedNumbers.contains(9) || selectedNumbers.contains(18)) && selectedNumbers.size()>1){
                                System.out.println("If you select more than a number, the selections must not include 9 nor 18!");
                                legalSelections=false;
                            }
                        }
                    } else { // NO SELECTIONS
                        System.out.println("You must select something");
                        legalSelections = false;
                    }
                    //EVENTUALLY THE USER WILL SELECT SOMETHING VALID, THE REQUEST TO THE SERVER IS THEN MADE
                    if(countRead>0){ //USER CHOSE TO RETRIEVE STUFF
                        PatientInfoReply reply = client.retrievePatientInfo(id, selectedNumbers);
                        if(reply.getPermission())
                            client.printRecords(reply.getRecords());
                        else {
                            System.out.println("PERMISSION DENIED");
                            System.out.println(reply.getPdpAdvice());
                        }
                    } else { //USER CHOSE TO WRITE STUFF, I already checked that there is no intersection
                        //todo PROCEDURE FOR WRITING RECORDS: *** P E D A N T I C ***
                    }

                }
                System.out.println("\n\nInsert another PatientID, -1 to logout");
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
