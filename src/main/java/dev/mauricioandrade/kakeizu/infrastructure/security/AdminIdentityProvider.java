package dev.mauricioandrade.kakeizu.infrastructure.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The only user is "admin"; its password hash comes from configuration, never from code.
 */
@ApplicationScoped
public class AdminIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    static final String ADMIN = "admin";
    private static final Logger LOG = Logger.getLogger(AdminIdentityProvider.class);

    private final String passwordHash;
    private final LoginThrottle throttle;

    public AdminIdentityProvider(@ConfigProperty(name = "kakeizu.admin.password-hash") String passwordHash,
                                 LoginThrottle throttle) {
        this.passwordHash = passwordHash;
        this.throttle = throttle;
    }

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        // PBKDF2 is deliberately slow: keep it off the event loop
        return context.runBlocking(() -> check(request));
    }

    private SecurityIdentity check(UsernamePasswordAuthenticationRequest request) {
        if (throttle.isLocked()) {
            LOG.warn("Login refused: locked after too many failed attempts");
            throw new AuthenticationFailedException();
        }
        boolean passwordMatches = PasswordHasher.verify(request.getPassword().getPassword(), passwordHash);
        if (!passwordMatches || !ADMIN.equals(request.getUsername())) {
            throttle.recordFailure();
            LOG.warn("Failed login attempt");
            throw new AuthenticationFailedException();
        }
        throttle.recordSuccess();
        LOG.info("Admin logged in");
        return adminIdentity();
    }

    static SecurityIdentity adminIdentity() {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(ADMIN))
                .addRole(ADMIN)
                .build();
    }
}
