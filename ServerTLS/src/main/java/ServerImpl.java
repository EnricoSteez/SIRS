import com.google.protobuf.ByteString;

import javax.annotation.Nullable;
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

    public ServerImpl(){
        try{
            Class.forName("com.mysql.jdbc.Driver");
            con= DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/SIRS","sirs","sirs");
        } catch (ClassNotFoundException|SQLException e) {
            e.printStackTrace();
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
        int hashlength = 128;
        //length of the stored password: saltLength + hashlength
        try {
            statement = con.prepareStatement("SELECT password FROM Users WHERE username=?");
            res = statement.executeQuery();
            if(res.next()) {
                saltPlusHashBlob = res.getBlob("password");
                byte[] saltPlusHash = saltPlusHashBlob.getBytes(0,saltLength);

                byte[] salt = new byte[128];
                byte[] databaseHash = new byte[128];
                System.arraycopy(saltPlusHash,0,salt,0,saltLength); //EXTRACT FIRST 6 BYTES: SALT
                System.arraycopy(saltPlusHash,6,databaseHash,0,hashlength); //REMAINING 128 BYTES: HASH

                byte[] password = passwordBytes.toByteArray();

                byte[] userHash = hash(salt,password);

                if(Arrays.equals(databaseHash, userHash))
                    return LoginReply.Code.SUCCESS;
                else
                    return LoginReply.Code.WRONGPASS;

            } else {
                return LoginReply.Code.UNRECOGNIZED;
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
        byte[] recordToStore = hash(null,password);

        try {
            Blob blob = con.createBlob();
            blob.setBytes(0,recordToStore);
            PreparedStatement statement = con.prepareStatement("INSERT INTO Users (Username, Password, Role) VALUES (?,?,?)");
            statement.setString(1,username);
            statement.setBlob(2,blob);
            statement.setString(3, role.name());
            System.out.println("Registering User: " + username +
                    " with password: " + Arrays.toString(convertToCharArray(blob.getBytes(0, password.length))) +
                    ". ROLE: " + role.name());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private byte[] hash(@Nullable byte[] salt, byte[] password) {
        SecureRandom random = new SecureRandom();

        if(salt == null){
            salt = new byte[16];
            random.nextBytes(salt); //16 BYTES LONG
        }
        char[] passCharArray = convertToCharArray(password);
        KeySpec spec = new PBEKeySpec(passCharArray, salt, 65536, 128);
        byte[] hash;
        SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            hash = factory.generateSecret(spec).getEncoded(); //128 BYTES LONG
            //prepend salt to the generated hash
            byte[] recordToStore = new byte[hash.length + salt.length];
            System.arraycopy(salt,0,recordToStore,0,salt.length);
            System.arraycopy(hash,0,recordToStore,salt.length, hash.length);

            return recordToStore;
        } catch(NoSuchAlgorithmException | InvalidKeySpecException e){
            e.printStackTrace();
        }

        return null;
    }

}
