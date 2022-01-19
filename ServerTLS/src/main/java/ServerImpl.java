import com.google.protobuf.ByteString;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.List;

/**
 * ServerImpl is a class that implements data retrieval methods (APIs)
 * The ServerTls accesses these methods to retrieve the patients information
 * from the underlying DB
 */
public class ServerImpl {

    public String sayHello (String name) {
        return "Ciao " + name;
    }

    public LoginReply.Code tryLogin (String username, ByteString passwordBytes) {
        char[] password = convertToCharArray(passwordBytes.toByteArray());
        //TODO IMPLEMENT DATABASE CHECK FOR LOGIN PROCEDURE with 'username' and 'password'
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

    public boolean registerUser (String username, byte[] password) {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        char[] passCharArray = convertToCharArray(password);
        KeySpec spec = new PBEKeySpec(passCharArray, salt, 65536, 128);
        byte[] hash;
        SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            hash = factory.generateSecret(spec).getEncoded();
        } catch(NoSuchAlgorithmException | InvalidKeySpecException e){
            e.printStackTrace();
            return false;
        }

        //TODO STORE HASH AND SALT IN THE DATABASE
        return true;
    }
}
