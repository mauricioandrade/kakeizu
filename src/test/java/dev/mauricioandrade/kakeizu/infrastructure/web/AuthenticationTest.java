package dev.mauricioandrade.kakeizu.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthenticationTest {

    @Test
    void everythingRedirectsToLoginWithoutASession() {
        given().redirects().follow(false)
                .when().get("/")
                .then().statusCode(302).header("Location", endsWith("/login"));
        given().redirects().follow(false)
                .when().post("/receipts")
                .then().statusCode(302).header("Location", endsWith("/login"));
        given().redirects().follow(false)
                .when().get("/summary")
                .then().statusCode(302).header("Location", endsWith("/login"));
    }

    @Test
    void loginPageIsPublic() {
        given().when().get("/login")
                .then().statusCode(200).body(containsString("j_security_check"));
    }

    @Test
    void wrongPasswordGoesToTheErrorPageWithoutASession() {
        given().redirects().follow(false)
                .formParam("j_username", "admin")
                .formParam("j_password", "wrong")
                .when().post("/j_security_check")
                .then().statusCode(302)
                .header("Location", endsWith("/login-error"))
                .header("Set-Cookie", nullValue());
    }

    @Test
    void rightPasswordGivesAHttpOnlyStrictSessionCookie() {
        given().redirects().follow(false)
                .formParam("j_username", "admin")
                .formParam("j_password", TestLogin.DEV_PASSWORD)
                .when().post("/j_security_check")
                .then().statusCode(302)
                .header("Location", endsWith("/"))
                .header("Set-Cookie", containsStringIgnoringCase("HttpOnly"))
                .header("Set-Cookie", containsString("SameSite=Strict"));
    }

    @Test
    void sessionCookieOpensTheApp() {
        given().redirects().follow(false)
                .cookie(TestLogin.COOKIE, TestLogin.sessionCookie())
                .when().get("/")
                .then().statusCode(200).body(containsString("Read receipt"));
    }

    @Test
    void stylesheetAndScriptArePublicSoTheLoginPageRenders() {
        given().redirects().follow(false).when().get("/css/app.css").then().statusCode(200);
        given().redirects().follow(false).when().get("/js/htmx-2.0.11.min.js").then().statusCode(200);
    }

    @Test
    void tamperedSessionCookieIsRefused() {
        given().redirects().follow(false)
                .cookie(TestLogin.COOKIE, "forged" + TestLogin.sessionCookie())
                .when().get("/")
                .then().statusCode(302).header("Location", endsWith("/login"));
    }
}
