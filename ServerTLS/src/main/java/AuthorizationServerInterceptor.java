import io.grpc.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

public class AuthorizationServerInterceptor implements ServerInterceptor {

    private final JwtParser parser = Jwts.parser().setSigningKey(Constants.JWT_SIGNING_KEY);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String value = metadata.get(Constants.AUTHORIZATION_METADATA_KEY);

        Status status;
        if (value == null) {
            status = Status.UNAUTHENTICATED.withDescription("Authorization token is missing");
        } else if (!value.startsWith(Constants.BEARER_TYPE)) {
            status = Status.UNAUTHENTICATED.withDescription("Unknown authorization type");
        } else {
            try {
                String token = value.substring(Constants.BEARER_TYPE.length()).trim();
                Jws<Claims> claims = parser.parseClaimsJws(token);
                Context ctx = Context.current().withValue(Constants.CLIENT_ID_CONTEXT_KEY, claims.getBody().getSubject());
                return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
            } catch (Exception e) {
                status = Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e);
            }
        }

        serverCall.close(status, metadata);
        return new ServerCall.Listener() {
            // noop
        };
    }


}
