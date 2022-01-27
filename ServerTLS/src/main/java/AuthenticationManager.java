import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;

public class AuthenticationManager {
    public class TokenValue{
        public int userId;
        public Role userRole;
        public LocalDateTime time;
        public TokenValue(int userId, Role userRole){
            this.userId = userId;
            this.userRole = userRole;
            this.time = LocalDateTime.now();
        }

        public boolean isValid(){
            return time.isBefore(LocalDateTime.now().plusMinutes(AuthenticationManager.MAX_SESSION_MINUTES));
        }
    }

    private static final SecureRandom secureRandom = new SecureRandom(); //threadsafe
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder(); //threadsafe
    public static final int MAX_SESSION_MINUTES = 120;
    private HashMap<String, TokenValue> map = new HashMap<>();

    public static String generateNewToken() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    public TokenValue getUser(String token){
        if(map.containsKey(token)){
            TokenValue tv = map.get(token);
            if(tv.isValid()){
                return map.get(token);
            }else{
                map.remove(token);
            }
        }
        return null;
    }

    public String logUser(int userId, Role userRole){
        TokenValue tv = new TokenValue(userId, userRole);
        String token = generateNewToken();
        map.put(token, tv);
        return token;
    }

    public void logoutUser(String token){
        map.remove(token);
    }
}
