import com.google.protobuf.ByteString;

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

    public PatientInfo retrievePatientInfo (String patientID, String whoami) {
        //TODO XACML PERMISSION REQUEST
        //For the moment, let's retrieve all the shit
        //we will figure it out later
        PatientInfo info = PatientInfo.newBuilder()
                .setId(1111)
                .setName("Enrico")
                .setSurname("Giorio")
                .setAddress("Alameda Doma Afonso Henriques 23")
                .build();
        return info;
    }
}
