package dev.mauricioandrade.kakeizu.infrastructure.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Login form for Quarkus form authentication, which handles POST /j_security_check itself.
 */
@Path("/")
@Produces(MediaType.TEXT_HTML)
public class LoginResource {

    private final Template login;

    public LoginResource(@Location("login.html") Template login) {
        this.login = login;
    }

    @GET
    @Path("login")
    public TemplateInstance form() {
        return login.data("error", false);
    }

    @GET
    @Path("login-error")
    public TemplateInstance error() {
        return login.data("error", true);
    }
}
