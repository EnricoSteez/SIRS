import com.google.protobuf.ByteString;

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
        //TODO IMPLEMENT DATABASE CHECK FOR LOGIN PROCEDURE
        return LoginReply.Code.SUCCESS;
    }

    public PatientInfoReply retrievePatientInfo (int patientID, String whoami, List<Integer> selectionsList) {
        //TODO XACML PERMISSION REQUEST
        //For the moment, let's retrieve all the shit
        //we will figure it out later
        PatientInfoReply info = PatientInfoReply.newBuilder().build();

        return info;
    }
}
