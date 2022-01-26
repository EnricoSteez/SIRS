import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * ServerImpl is a class that implements data retrieval methods (APIs)
 * The ServerTls accesses these methods to retrieve the patients' information
 * from the underlying DB
 */
public class ServerImpl {
    private Connection con;
    private static ServerImpl instance = null;
    private static AccessControlServiceGrpc.AccessControlServiceBlockingStub blockingStub;
    private static DocumentBuilder builder;
    private final Map<Integer,String> MedicalRecordContent = new HashMap<Integer,String>();

    public static ServerImpl getInstance(String target) {
        if(instance == null)
            instance = new ServerImpl(target);
        return instance;
    }

    private ServerImpl(String target){
        //THIS IS JUST A MAPPING FOR THE USER SELECTION TO PUT IN THE PDP REQUEST
        MedicalRecordContent.put(1,"NameSurname");
        MedicalRecordContent.put(2,"PersonalData");
        MedicalRecordContent.put(3,"Problems");
        MedicalRecordContent.put(4,"Medications");
        MedicalRecordContent.put(5,"HealthHistory");
        MedicalRecordContent.put(6,"Allergies");
        MedicalRecordContent.put(7,"VisitsHistory");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            System.err.println("ERROR IN BUILDING DOCUMENT FACTORY");
            System.exit(0);
        }

        try{
            System.setProperty("javax.net.ssl.trustStore", "../Keys/DBKeys/dbtruststore");
            System.setProperty("javax.net.ssl.trustStorePassword", "password");

            Class.forName("com.mysql.jdbc.Driver");
            con= DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/main","root","ga38");
            
            // sirs is the database name, root is the user and last parameter is the password: use null if no password is set!!
        } catch (ClassNotFoundException|SQLException e) {
            System.err.println("ERROR CONNECTING TO THE DATABASE, CHECK THE DRIVE MANAGER.CONNECTION PARAMETERS");
            e.printStackTrace();
            System.exit(0);
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();

        blockingStub = AccessControlServiceGrpc.newBlockingStub(channel);
    }
    public String sayHello (String name) {
        return "Ciao " + name;
    }

    public LoginReply login (String username, ByteString passwordBytes) {
        PreparedStatement statement = null;
        ResultSet res = null;
        Blob saltPlusHashBlob = null;
        int saltLength = 16;
        int hashLength = 16;
        LoginReply.Builder reply = LoginReply.newBuilder();

        //length of the stored password: saltLength (16B) + hashlength (16B)
        try {
            statement = con.prepareStatement("SELECT password FROM Users WHERE username=?");
            statement.setString(1,username);
            res = statement.executeQuery();
            if(res.next()) {
                //------------------------------ RETRIEVE STORED PASSWORD ------------------------------
                saltPlusHashBlob = res.getBlob("password");
                byte[] saltPlusHash = saltPlusHashBlob.getBytes(1,saltLength+hashLength);
                System.err.println("LOGGING IN User: " + username);
                System.err.println("UserPass: " + Arrays.toString(passwordBytes.toByteArray()));

                System.err.println("Retrieved salt plus hash: " + Arrays.toString(saltPlusHash));

                //------------------------------ SEPARATE SALT AND HASH ------------------------------
                byte[] salt = new byte[16];
                byte[] databaseHash = new byte[16];
                System.arraycopy(saltPlusHash,0,salt,0,saltLength); //EXTRACT FIRST 16 BYTES: SALT
                System.err.println("Extracted salt: " + Arrays.toString(salt));
                System.arraycopy(saltPlusHash,16,databaseHash,0,hashLength); //REMAINING 16 BYTES: HASH
                System.err.println("Extracted hash: " + Arrays.toString(databaseHash));

                //----------------------- HASH THE USER PASSWORD WITH THE DATABASE RETRIEVED SALT ---------------------
                byte[] password = passwordBytes.toByteArray();
                byte[] userHash = hash(salt,password);
                System.err.println("UserPass hashed with Database Salt: " + Arrays.toString(userHash));

                //------------------------------ CHECK FOR MATCH ------------------------------
                if(Arrays.equals(databaseHash, userHash)) {
                    statement = con.prepareStatement("SELECT CustomerId, Role FROM Users WHERE username=?");
                    statement.setString(1,username);
                    res = statement.executeQuery();
                    res.next();
                    Role role = Role.valueOf(res.getString("Role"));
                    int id = res.getInt("CustomerId");
                    reply.setCode(LoginReply.Code.SUCCESS)
                            .setUserId(id)
                            .setRole(role);
                }else {
                    reply.setCode(LoginReply.Code.WRONGPASS);
                }
            } else {
                reply.setCode(LoginReply.Code.WRONGUSER);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //here SIRS is database name, sirs is username and password
        //TODO @Daniel PLEASE figure out how to connect with SSL (if you need to add anything to the code)

        return reply.build();
    }

    public PatientInfoReply retrievePatientInfo (int patientID, Role whoami, List<Integer> selectionsList) {
        String xacmlRequest = createRequestString(whoami, selectionsList, "read");
        System.out.println("CREATING ACCESS REQUEST STRING:");
        System.out.println(xacmlRequest);

//      --------------------------- PDP ACCESS CONTROL REQUEST ---------------------------
        AccessControlRequest request = AccessControlRequest.newBuilder().setXacmlRequest(xacmlRequest).build();
        AccessControlReply reply = blockingStub.validateAccess(request);
        String xacmlReply = reply.getXacmlReply();
        System.out.println("RECEIVED XACML REPLY:");
        System.out.println(xacmlReply);

        PatientInfoReply.Builder patientInfoReply = getAccessControlOutcome(xacmlReply);
        //this builder that I got from the outcome has already the permission bit and, eventually, the advice, set.
        //now I just need to add the actual records if the decision was Permit

        if(patientInfoReply.getPermission()) { //IF PERMIT
            //TODO RETRIEVE MEDICAL RECORDS OF PATIENT patientID FROM DATABASE AND ADD TO REPLY
//            patientInfoReply.setRecords() ...;

            //TEMPORARY:
            LocalDate date = LocalDate.now();
            patientInfoReply.setRecords(
                    MedicalRecords.newBuilder()
                            .setAllergies("Dog's hair")
                            .setPatientId(patientID)
                            .setHealthHistory("Heart attack on 10/10/2010")
                            .setNameSurname("Enrico Giorio")
                            .setPersonalData(PersonalData.newBuilder()
                                    .setEmail("enrico.giorio@tecnico.ulisboa.pt").build())
                            .setMedications(1,"Paracetamol")
                            .setMedications(2,"Nixar")
                            .setVisitsHistory(1, VisitDate.newBuilder()
                                    .setDay(date.getDayOfMonth())
                                    .setMonth(date.getMonthValue())
                                    .setYear(date.getYear())
                                    .build() )
            );
        } //else do nothing, the rest of the reply is already set
        //IF PERMISSION IS DENIED, THE PERMISSION BIT AND THE ADVICE ARE ALREADY SET BY THE getAccessControlOutcome() FUNCTION
        return patientInfoReply.build();
    }

    public boolean registerCertificate(int userId, String certificate, byte[] nonce, SignatureM signature){

        try {
            //check if signature matches certificate
            Certificate cert = RSAOperations.getCertificateFromString(certificate);
            boolean certMatches = RSAOperations.verify(cert.getPublicKey(), nonce, signature.getSignature().getBytes(), signature.getCryptAlgo());
            if(!certMatches){
                return false;
            }

            //register certificate to userId
            RSAOperations.writeFile(certificate, ServerTls.certificatesPath + userId + ".crt");

        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean registerUser (String username, byte[] password, Role role) {

        //------------------------------ CHECK IF USERNAME ALREADY EXISTS ------------------------------
        try {
            PreparedStatement statement = con.prepareStatement("SELECT * from Users WHERE Username = ?");
            statement.setString(1,username);
            ResultSet res = statement.executeQuery();

            if(res.next()) {
                System.out.println("USERNAME " + username + " ALREADY EXISTS, SKIPPED");
                return false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        //------------------------------ GENERATE SALT ------------------------------
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt); //16 BYTES LONG

        //------------------------------ HASH ------------------------------
        byte[] passHash = hash(salt,password);
        if(passHash == null)
        {
            System.err.println("Failed to hash password " + Arrays.toString(password) + " with salt " + Arrays.toString(salt));
            return false;
        }

        //------------------------------ PREPEND ------------------------------
        byte[] recordToStore = new byte[passHash.length + salt.length];
        System.arraycopy(salt,0,recordToStore,0,salt.length);
        System.arraycopy(passHash,0,recordToStore,salt.length, passHash.length);

        System.err.println("After prepending the salt to the hash, the final record is: " + Arrays.toString(recordToStore));

        //------------------------------ STORE ------------------------------
        try {
            Blob blob = con.createBlob();
            blob.setBytes(1,recordToStore);
            PreparedStatement statement = con.prepareStatement("INSERT INTO Users (Username, Password, Role) VALUES (?,?,?)");
            statement.setString(1,username);
            statement.setBlob(2,blob);
            statement.setString(3, role.name());

            System.err.println("Registering User: " + username +
                    " with passwordBytes: " + Arrays.toString(password) +
                    " maps to plaintext: " + Arrays.toString(convertToCharArray(password)) +
                    ". ROLE: " + role.name());

            System.err.println("The hashed password I will store is: " + Arrays.toString(blob.getBytes(1, recordToStore.length)));

            if(statement.executeUpdate() == 1)
                return true;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     *
     * @param xacmlReply an XML-formatted string coming from the PDP
     * @return a PatientInfoReply.Builder with the permission bit and, in case permission is denied, the advice, already set.
     */
    private PatientInfoReply.Builder getAccessControlOutcome(String xacmlReply) {
        PatientInfoReply.Builder res = PatientInfoReply.newBuilder();

        try {
//          ------------------------------ PARSE DECISION OUTCOME ------------------------------
            Document document = builder.parse(new InputSource(new StringReader(xacmlReply)));
            Element rootElement = document.getDocumentElement();

            Node decision = rootElement.getElementsByTagName("Decision").item(0); //ONLY ONE
            String decisionValue = decision.getNodeValue();
            System.out.println("PARSED DECISION IS: " + decisionValue);

            if(decisionValue.equals("Permit")) {
                res.setPermission(true);
            }
            else { //ANYTHING OTHER THAN PERMIT IS DENY
                Node advice = rootElement.getElementsByTagName("Advice").item(0); //ONLY ONE
                String adviceMessage = advice.getFirstChild().getNodeValue();
                System.out.println("ADVICE IS: " + adviceMessage);
                res.setPermission(false).setPdpAdvice(adviceMessage);
            }

        } catch (SAXException|IOException e) {
            e.printStackTrace();
            System.err.println("ERROR CREATING A DOCUMENT OUT OF THE XACML REPLY");
            System.exit(0);
        }

        return res;
    }

    private static char[] convertToCharArray(final byte[] source) {
        if (source == null) {
            return null;
        }
        final char[] result = new char[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = (char) source[i];
        }
        return result;
    }

    private byte[] hash(byte[] salt, byte[] password) {
        char[] passCharArray = convertToCharArray(password);
//        System.err.println("The plaintext password is: " + Arrays.toString(passCharArray));
        KeySpec spec = new PBEKeySpec(passCharArray, salt, 65536, 128);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            //prepend salt to the generated hash
//            System.err.println("The generated hash is: " + Arrays.toString(hash));
            return factory.generateSecret(spec).getEncoded();
        } catch(NoSuchAlgorithmException | InvalidKeySpecException e){
            e.printStackTrace();
        }

        return null;
    }

    private String createRequestString (Role whoami, List<Integer> selectionsList, String action) {
        StringBuilder request = new StringBuilder(
                "<Request xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" CombinedDecision=\"false\" ReturnPolicyIdList=\"false\">" +
                "     <Attributes Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\">" +
                "          <Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:subject:subject-id\" IncludeInResult=\"false\">" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + whoami + "</AttributeValue>" +
                "          </Attribute>" +
                "     </Attributes>" +
                "     <Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\">");
        //APPEND ALL ATTRIBUTE VALUES OF CATEGORY RESOURCE
        if(selectionsList.get(0) == 8) { //PUT ALL FIELDS
            for(String field : MedicalRecordContent.values()) {
                request.append("          <Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" IncludeInResult=\"false\">" +
                        "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + field + "</AttributeValue>" +
                        "          </Attribute>");
            }
        }
        else {
            for (int info : selectionsList) {
                request.append("          <Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" IncludeInResult=\"false\">" +
                        "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + MedicalRecordContent.get(info) + "</AttributeValue>" +
                        "          </Attribute>");
            }
        }

        request.append("</Attributes>");

        request.append("     <Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\">" +
                "          <Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" IncludeInResult=\"false\">" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + action + "</AttributeValue>" +
                "          </Attribute>" +
                "     </Attributes>" +
                "</Request>");

        return request.toString();
    }

}
