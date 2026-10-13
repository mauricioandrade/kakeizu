package dev.mauricioandrade.kakeizu.infrastructure.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Requests after login carry the encrypted session cookie, which Quarkus already verified; this only
 * turns the principal name back into an identity.
 */
@ApplicationScoped
public class AdminSessionIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {

    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request, AuthenticationRequestContext context) {
        if (!AdminIdentityProvider.ADMIN.equals(request.getPrincipal())) {
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }
        return Uni.createFrom().item(AdminIdentityProvider.adminIdentity());
    }
}
