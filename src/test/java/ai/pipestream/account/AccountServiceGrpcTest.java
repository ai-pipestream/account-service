package ai.pipestream.account;

import ai.pipestream.test.support.WireMockTestResource;
import ai.pipestream.repository.account.v1.AccountServiceGrpc;
import ai.pipestream.repository.account.v1.CreateAccountRequest;
import ai.pipestream.repository.account.v1.GetAccountRequest;
import ai.pipestream.repository.account.v1.InactivateAccountRequest;
import ai.pipestream.repository.account.v1.ListAccountsRequest;
import ai.pipestream.repository.account.v1.StreamAllAccountsRequest;
import ai.pipestream.repository.account.v1.UpdateAccountRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * gRPC integration tests to verify that the active field is properly
 * returned in gRPC responses for both active and inactive accounts.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
public class AccountServiceGrpcTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @BeforeEach
    void cleanupBefore() {
        // Clean up any test accounts from previous runs
        // Note: This is a simple approach - in a real scenario you might want
        // to use a test profile that resets the database
    }

    @Test
    public void testActiveAccountHasActiveField() {
        String testAccountId = "test-active-grpc-" + System.currentTimeMillis();
        
        // Create an active account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Active Test Account")
                .setDescription("Testing active field in gRPC")
                .build();

        var createResponse = accountService.createAccount(createRequest);
        assertThat("Account creation should succeed", createResponse.getCreated(), is(true));
        assertThat("Newly created account should have active=true in response",
                createResponse.getAccount().getActive(), is(true));

        // Get the account and verify active field is present
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var response = accountService.getAccount(getRequest);

        // This is the critical test - the active field should be true
        assertThat("Retrieved active account should have active=true field",
                response.getAccount().getActive(), is(true));
        assertThat("Account ID should match the created account",
                response.getAccount().getAccountId(), equalTo(testAccountId));
        assertThat("Account name should match the created account",
                response.getAccount().getName(), equalTo("Active Test Account"));
    }

    @Test
    public void testInactiveAccountHasActiveField() {
        String testAccountId = "test-inactive-grpc-" + System.currentTimeMillis();
        
        // Create an active account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Inactive Test Account")
                .setDescription("Testing inactive field in gRPC")
                .build();

        var createResponse = accountService.createAccount(createRequest);
        assertThat("Account creation should succeed", createResponse.getCreated(), is(true));
        assertThat("Newly created account should have active=true in response",
                createResponse.getAccount().getActive(), is(true));

        // Inactivate the account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Testing inactive field")
                .build();

        var inactivateResponse = accountService.inactivateAccount(inactivateRequest);
        assertThat("Account inactivation should succeed",
                inactivateResponse.getSuccess(), is(true));

        // Get the inactive account and verify active field is present and false
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var response = accountService.getAccount(getRequest);

        // This is the critical test - the active field should be false
        assertThat("Retrieved inactive account should have active=false field",
                response.getAccount().getActive(), is(false));
        assertThat("Account ID should match the inactivated account",
                response.getAccount().getAccountId(), equalTo(testAccountId));
        assertThat("Account name should match the inactivated account",
                response.getAccount().getName(), equalTo("Inactive Test Account"));
    }

    @Test
    public void testUpdateAccount() {
        String testAccountId = "test-update-grpc-" + System.currentTimeMillis();

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(testAccountId)
            .setName("Original Name")
            .setDescription("Original description")
            .build());

        var updateResponse = accountService.updateAccount(UpdateAccountRequest.newBuilder()
            .setAccountId(testAccountId)
            .setName("Updated Name")
            .setDescription("Updated description")
            .build());

        assertThat("Update response should contain updated name",
                updateResponse.getAccount().getName(), equalTo("Updated Name"));
        assertThat("Update response should contain updated description",
                updateResponse.getAccount().getDescription(), equalTo("Updated description"));

        var fetched = accountService.getAccount(GetAccountRequest.newBuilder()
            .setAccountId(testAccountId)
            .build());

        assertThat("Fetched account should have updated name persisted",
                fetched.getAccount().getName(), equalTo("Updated Name"));
        assertThat("Fetched account should have updated description persisted",
                fetched.getAccount().getDescription(), equalTo("Updated description"));
    }

    @Test
    public void testActiveFieldConsistency() {
        String testAccountId = "test-consistency-" + System.currentTimeMillis();
        
        // Create account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Consistency Test Account")
                .setDescription("Testing active field consistency")
                .build();

        accountService.createAccount(createRequest);

        // Verify active account
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var activeResponse = accountService.getAccount(getRequest);
        assertThat("Initially retrieved account should be active",
                activeResponse.getAccount().getActive(), is(true));

        // Inactivate account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Testing consistency")
                .build();

        accountService.inactivateAccount(inactivateRequest);

        // Verify inactive account
        var inactiveResponse = accountService.getAccount(getRequest);
        assertThat("After inactivation, account active field should be false",
                inactiveResponse.getAccount().getActive(), is(false));

        // Verify other fields are still present
        assertThat("Account ID should remain consistent after inactivation",
                inactiveResponse.getAccount().getAccountId(), equalTo(testAccountId));
        assertThat("Account name should remain consistent after inactivation",
                inactiveResponse.getAccount().getName(), equalTo("Consistency Test Account"));
    }

    @Test
    public void testListAccountsReturnsActiveByDefault() {
        String idPrefix = "grpc-list-default-" + System.currentTimeMillis();

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-active")
            .setName("List Active Account")
            .build());

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-inactive")
            .setName("List Inactive Account")
            .build());

        accountService.inactivateAccount(InactivateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-inactive")
            .setReason("list test")
            .build());

        var response = accountService.listAccounts(ListAccountsRequest.newBuilder().build());

        assertThat("listAccounts() should return the active account by default",
                response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-active")),
                is(true));
        assertThat("listAccounts() should NOT return inactive accounts by default",
                response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-inactive")),
                is(false));
    }

    @Test
    public void testListAccountsSupportsIncludeInactiveAndQuery() {
        String idPrefix = "grpc-list-filter-" + System.currentTimeMillis();

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-one")
            .setName("Marketing")
            .build());

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-two")
            .setName("Marine")
            .build());

        accountService.inactivateAccount(InactivateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-two")
            .setReason("filter test")
            .build());

        var response = accountService.listAccounts(ListAccountsRequest.newBuilder()
            .setQuery("mar")
            .setIncludeInactive(true)
            .build());

        assertThat("Query 'mar' with includeInactive=true should return 2 accounts",
                response.getAccountsCount(), is(2));
        assertThat("Results should include the active account matching 'mar'",
                response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-one")),
                is(true));
        assertThat("Results should include the inactive account matching 'mar' when includeInactive=true",
                response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-two")),
                is(true));
    }

    @Test
    public void testStreamAllAccountsExcludesInactiveByDefault() {
        String idPrefix = "grpc-stream-" + System.currentTimeMillis();

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-active")
            .setName("Stream Active")
            .build());

        accountService.createAccount(CreateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-inactive")
            .setName("Stream Inactive")
            .build());

        accountService.inactivateAccount(InactivateAccountRequest.newBuilder()
            .setAccountId(idPrefix + "-inactive")
            .setReason("stream test")
            .build());

        var iterator = accountService.streamAllAccounts(StreamAllAccountsRequest.newBuilder().build());
        boolean foundActive = false;
        boolean foundInactive = false;
        while (iterator.hasNext()) {
            var account = iterator.next().getAccount();
            if (account.getAccountId().equals(idPrefix + "-active")) {
                foundActive = true;
            }
            if (account.getAccountId().equals(idPrefix + "-inactive")) {
                foundInactive = true;
            }
        }

        assertTrue(foundActive, "streamAllAccounts should include active accounts by default");
        assertFalse(foundInactive, "streamAllAccounts should exclude inactive accounts by default");
    }
}
