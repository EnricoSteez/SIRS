import com.google.protobuf.ByteString;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

/**
 * ServerImpl is a class that implements data retrieval methods (APIs)
 * The ServerTls accesses these methods to retrieve the patients information
 * from the underlying DB
 */
public class ServerImpl {
    private Connection con;
    private static ServerImpl instance = null;

    public static ServerImpl getInstance () {
        if(instance == null)
            instance = new ServerImpl();
        return instance;
    }

    private ServerImpl(){
        try{
            Class.forName("com.mysql.jdbc.Driver");
            con= DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/sirs","root",null);
            // sirs is the database name, root is the user and last parameter is the password: use null if no password is set!!
        } catch (ClassNotFoundException|SQLException e) {
            System.err.println("ERROR CONNECTING TO THE DATABASE, CHECK THE DRIVEMANAGER.GETCONNECTION PARAMETERS");
            e.printStackTrace();
            System.exit(0);
        }
    }
    public String sayHello (String name) {
        return "Ciao " + name;
    }

    public LoginReply.Code tryLogin (String username, ByteString passwordBytes) {
        PreparedStatement statement = null;
        ResultSet res = null;
        Blob saltPlusHashBlob = null;
        int saltLength = 16;
        int hashLength = 16;
        //length of the stored password: saltLength + hashlength
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
                    return LoginReply.Code.SUCCESS;
                }else {
                    return LoginReply.Code.WRONGPASS;
                }
            } else {
                return LoginReply.Code.WRONGUSER;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //here SIRS is database name, sirs is username and password
        //TODO @Daniel PLEASE figure out how to connect with SSL (if you need to add anything to the code)

        return LoginReply.Code.SUCCESS;
    }

    public PatientInfoReply retrievePatientInfo (int patientID, Role whoami, List<Integer> selectionsList) {
        //TODO XACML PERMISSION REQUEST
        //For the moment, let's retrieve all the shit
        //we will figure it out later
        PatientInfoReply info = PatientInfoReply.newBuilder().build();

        return info;
    }

    public static char[] convertToCharArray(final byte[] source) {
        if (source == null) {
            return null;
        }
        final char[] result = new char[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = (char) source[i];
        }
        return result;
    }

    public boolean registerUser (String username, byte[] password, Role role) {

        //------------------------------ CHECK IF USERNAME ALREADY EXISTS ------------------------------
        try {
            PreparedStatement statement = con.prepareStatement("SELECT * from Users WHERE Username = ?");
            statement.setString(1,username);
            ResultSet res = statement.executeQuery();

            if(res.next())
                return false;

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

}
