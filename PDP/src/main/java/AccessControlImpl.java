public class AccessControlImpl {
    private AccessControlImpl instance;

    private AccessControlImpl(){

    }

    public AccessControlImpl getInstance () {
        if(instance==null)
            instance = new AccessControlImpl();
        return instance;
    }
}
