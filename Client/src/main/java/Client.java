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

    private static HospitalServiceGrpc.HospitalServiceBlockingStub blockingStub;
    private static final String signatureAlg = "SHA256withRSA";
    private static Role userRole = Role.ADMIN;
    private static int userID = 1;
    private static String sessionToken = "";
    private static RSAPrivateKey privateKey;
    private static String certificate;
    private static Random rand = new Random();
//    private static String loggedUser = null;

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public Client(Channel channel) {
        blockingStub = HospitalServiceGrpc.newBlockingStub(channel);
        logger.setLevel(Level.FINEST);
    }

    public static void printErrorMessage(ErrorType type){
        switch (type){
            case SQL_ERROR:
                System.out.println("ERROR: DATABASE ERROR");
                break;
            case NOT_AUTHORIZED:
                System.out.println("ERROR: USER IS NOT AUTHORIZED TO DO THIS ACTION");
                break;
            case NOT_LOGGED_IN:
                System.out.println("ERROR: USER IS NOT LOGGED IN");
                break;
            case PATIENT_DOES_NOT_EXIST:
                System.out.println("ERROR: PATIENT DOES NOT EXIST");
                break;
            case USER_ALREADY_EXISTS:
                System.out.println("ERROR: USER WITH NAME ALREADY EXISTS");
                break;
            case HASH_FAIL:
                System.out.println("ERROR: FAILED WITH HASHING");
                break;
            case UNKNOWN:
                System.out.println("ERROR: UNKNOWN ERROR");
                break;
            case SIGNATURE_DOESNT_MATCH:
                System.out.println("ERROR: SIGNATURE DOES NOT MATCH CERTIFICATE");
                break;
            case CERTIFICATE_NOT_VALID:
                System.out.println("ERROR: CERTIFICATE IS NOT VALID");
                break;

        }
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
            sessionToken = reply.getToken();
        }
        return reply.getCode();
    }

    private PatientInfoReply retrievePatientInfo (int id, List<Integer> selectedNumbers) {

        System.err.println("About to send request for Patient info containing:");
        System.err.println("Patient ID:" + id);
        System.err.println("My ROLE:" + userRole);
        System.err.println("Selections:" + Arrays.toString(selectedNumbers.toArray()));

        PatientInfoRequest.Builder request = PatientInfoRequest.newBuilder()
                .setToken(sessionToken)
                .setPatientId(id)
                .addAllSelections(selectedNumbers);

//        for(int i = 0 ; i < selectedNumbers.size() ; i++) {
//            request.setSelections(i+1, selectedNumbers.get(i));
//        }

        PatientInfoReply reply = blockingStub.retrievePatientInfo(request.build());

        return reply;
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
                    .setToken(sessionToken)
                    .setChosenRole(role)
                    .build();
            System.err.println("Register Request is: " + username + " ~ " + Arrays.toString(password) + " ~ " + role.name());
            RegisterReply reply = blockingStub.register(request);

            successfulRegister = reply.getOk();
            if(successfulRegister)
                System.out.println("ALL GOOD!");
            else{
                System.out.println("BAD OUTCOME");
                printErrorMessage(reply.getErrorType());
            }

        }
    }

    //checks if has valid certificate in server
    //if not will register new one
    private void manageCertificate(){
        CheckCertificateRequest request = CheckCertificateRequest.newBuilder()
                .setToken(sessionToken)
                .build();
        CheckCertificateReply reply = blockingStub.checkCertificate(request);
        if(reply.getValid()){
            if(!certificate.equals(reply.getCertificate())){
                registerCertificate();
            }
        }else{
            registerCertificate();
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
                    .setCertificate(certificate)
                    .setNonce(nonce)
                    .setSignedNonce(signature)
                    .setToken(sessionToken)
                    .build();
            RegisterCertificateReply reply = blockingStub.registerCertificate(request);

            if(reply.getOk()){
                System.out.println("Successfully registered certificate");
            }else{
                System.out.println("There was a problem registering the certificate");
                printErrorMessage(reply.getErrorType());
            }


        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Problem reading certificate or private key");
        }
    }

    public static void main(String[] args) throws Exception {


        if (args.length < 2 || args.length == 4 || args.length > 5) {
            System.out.println("USAGE: Client host port trustCertCollectionFilePath ");
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
                    case SQLERROR:
                        System.out.println("There was an database error!");
                        break;
                    case SUCCESS:
                        System.out.println("Welcome!!");
                        client.manageCertificate();
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
            System.out.println("Insert PatientID to retrieve info, -1 to logout, 0 if you wish to create a new patient record");
            int id;
            while(true) {
                try {
                    id = Integer.parseInt(System.console().readLine());
                } catch (Exception e) {
                    continue;
                }
                break;
            }

            while (id >= 0){ //DO STUFF UNTIL LOGOUT
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
                    System.out.println("[10] -> Change Name Surname");
                    System.out.println("[11] -> Change Personal Information (home address, email, health number)");
                    System.out.println("[12] -> Change Health Problems");
                    System.out.println("[13] -> Change Prescribed Medications");
                    System.out.println("[14] -> Add new Health History record");
                    System.out.println("[15] -> Add new Allergy");
                    System.out.println("[16] -> Add Clinical Visit record");
                    System.out.println("[17] -> Add Lab Result");
                    System.out.println("Insert a single number, followed by ENTER. (you can only write a single field at a time)");

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
                                else if(i>=10 && i<18)
                                    countWrite++;
                                else {
                                    legalSelections = false;
                                    break;
                                }
                            }
                            if(countRead>0 && countWrite>0) {
                                System.out.println("You cannot read and write at the same time");
                                legalSelections=false;
                                continue;
                            }
                            if(countWrite>1){
                                System.out.println("You can only write one field at a time { for now ;) }");
                                legalSelections=false;
                                continue;
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
                        if(reply.getOk())
                            client.printRecords(reply.getRecords());
                        else if(!reply.getPermission()){
                            System.out.println("PERMISSION DENIED");
                            System.out.println(reply.getPdpAdvice());
                        }else{
                            printErrorMessage(reply.getErrorType());
                        }
                    } else { //USER CHOSE TO WRITE STUFF, I already checked that there is no intersection between read and write choices
                        int selection = selectedNumbers.get(0); //THERE IS ONLY ONE IF I'M HERE
                        WritePatientInfoRequest request = null;
                        try {
                            request = createRequest(selection, id);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        assert request != null;
                        WritePatientInfoReply reply = blockingStub.writePatientInfo(request);
                        boolean successful = reply.getOk();
                        if(successful)
                            System.out.println("WRITE PERFORMED ON PATIENT WITH ID: " + reply.getPatientId() + "!");
                        else{
                            System.out.println("SERVER-SIDE ERROR OCCURRED, CHECK SERVER LOGS");
                            printErrorMessage(reply.getErrorType());
                        }
                    }

                }
                System.out.println("\n\nInsert another PatientID, -1 to logout");
                try {
                    id = Integer.parseInt(System.console().readLine());
                } catch (NumberFormatException e){
                    id = -1;
                }
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static WritePatientInfoRequest createRequest (int selection, int patientID) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
        ByteString signature = null;
        WritePatientInfoRequest.Builder request = WritePatientInfoRequest.newBuilder();

        switch (selection) {
            case 10:
                System.out.println("Insert Name:");
                String name = System.console().readLine();
                System.out.println("Insert Surname:");
                String surname = System.console().readLine();
                String nameSurname = name + surname;
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, nameSurname.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setNameSurname(nameSurname);
                break;
            case 11:
                System.out.println("Insert Home Address:");
                String homeAddress = System.console().readLine();
                System.out.println("Insert Email:");
                String email = System.console().readLine();
                System.out.println("Insert Health Number:");
                int healthNumber = Integer.parseInt(System.console().readLine());
                PersonalData personalData = PersonalData.newBuilder()
                        .setEmail(email)
                        .setHealthNumber(healthNumber)
                        .setHomeAddress(homeAddress)
                        .build();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, personalData.toByteArray(), signatureAlg));
                request.setPersonalData(personalData);
                break;
            case 12:
                System.out.println("Write updated Health Problems:");
                String problems = System.console().readLine();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, problems.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setProblems(problems);
                break;
            case 13:
                System.out.println("Write updated Medications:");
                String medications = System.console().readLine();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, medications.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setMedications(medications);
                break;
            case 14:
                System.out.println("Insert new Health History record:");
                String healthRecord = System.console().readLine();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, healthRecord.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setHealthHistoryRecord(healthRecord);
                break;
            case 15:
                System.out.println("Insert new Allergy:");
                String allergy = System.console().readLine();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, allergy.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setAllergy(allergy);
                break;
            case 16:
                System.out.println("Insert new Clinical Visit:");
                System.out.println("Year:");
                int year = Integer.parseInt(System.console().readLine());
                System.out.println("Month:");
                int month = Integer.parseInt(System.console().readLine());
                System.out.println("Day:");
                int day = Integer.parseInt(System.console().readLine());
                VisitDate date = VisitDate.newBuilder()
                        .setYear(year)
                        .setMonth(month)
                        .setDay(day)
                        .build();

                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, date.toByteArray(), signatureAlg));
                request.setVisit(date);
                break;
            case 17:
                System.out.println("Insert new Lab Result:");
                String labResult = System.console().readLine();
                signature = ByteString.copyFrom(RSAOperations.sign(privateKey, labResult.getBytes(StandardCharsets.UTF_8), signatureAlg));
                request.setLabResult(labResult);
                break;
        }
        assert signature != null;

        SignatureM signatureM = SignatureM.newBuilder()
                .setCryptAlgo(signatureAlg)
                .setNonce(rand.nextInt())
                .setSignature(signature)
                .build();

        request.setSignature(signatureM);
        request.setToken(sessionToken);
        request.setPatientID(patientID);

        return request.build();
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
