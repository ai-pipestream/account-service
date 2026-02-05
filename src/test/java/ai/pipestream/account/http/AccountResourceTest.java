package ai.pipestream.account.http;

import ai.pipestream.repository.account.v1.CreateAccountRequest;
import ai.pipestream.test.support.WireMockTestResource;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * REST API integration tests for AccountResource.
 * Uses REST Assured to verify the REST endpoints exposed for frontend consumption.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class AccountResourceTest {

    @GrpcClient("account-manager")
    ai.pipestream.repository.account.v1.AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @Test
    void listAccountsReturnsEmptyOrExistingAccounts() {
        given()
            .when()
                .get("/api/accounts")
            .then()
                .statusCode(200)
                .body("accounts", notNullValue())
                .body("totalCount", notNullValue());
    }

    @Test
    void getAccountReturnsCreatedAccount() {
        String accountId = "rest-test-" + System.currentTimeMillis();

        // Create via gRPC
        accountService.createAccount(CreateAccountRequest.newBuilder()
                .setAccountId(accountId)
                .setName("REST API Test Account")
                .setDescription("Created for REST Assured test")
                .build());

        // Verify via REST
        given()
            .when()
                .get("/api/accounts/" + accountId)
            .then()
                .statusCode(200)
                .body("accountId", equalTo(accountId))
                .body("name", equalTo("REST API Test Account"))
                .body("description", equalTo("Created for REST Assured test"))
                .body("active", equalTo(true))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    void getAccountReturns404ForUnknown() {
        given()
            .when()
                .get("/api/accounts/non-existent-account-id")
            .then()
                .statusCode(404);
    }

    @Test
    void listAccountsIncludesCreatedAccount() {
        String accountId = "rest-list-test-" + System.currentTimeMillis();

        accountService.createAccount(CreateAccountRequest.newBuilder()
                .setAccountId(accountId)
                .setName("List Test Account")
                .build());

        // Query by accountId prefix (search matches accountId and name)
        given()
            .when()
                .get("/api/accounts?query=rest-list")
            .then()
                .statusCode(200)
                .body("accounts.accountId", hasItem(accountId))
                .body("accounts.find { it.accountId == '" + accountId + "' }.name", equalTo("List Test Account"));
    }
}
