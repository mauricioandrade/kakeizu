package dev.mauricioandrade.kakeizu.infrastructure.web;

import static io.restassured.RestAssured.given;

/**
 * Logs in through the real form and returns the session cookie value.
 */
final class TestLogin {

    static final String COOKIE = "quarkus-credential";
    static final String DEV_PASSWORD = "kakeizu";

    private TestLogin() {
    }

    static String sessionCookie() {
        return given()
                .redirects().follow(false)
                .formParam("j_username", "admin")
                .formParam("j_password", DEV_PASSWORD)
                .when().post("/j_security_check")
                .then().statusCode(302)
                .extract().cookie(COOKIE);
    }
}
