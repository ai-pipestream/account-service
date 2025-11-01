package io.pipeline.account;

import io.pipeline.grpc.wiremock.MockServiceTestResource;
import io.pipeline.repository.account.AccountServiceGrpc;
import io.pipeline.repository.account.CreateAccountRequest;
import io.pipeline.repository.account.GetAccountRequest;
import io.pipeline.repository.account.InactivateAccountRequest;
import io.pipeline.repository.account.ListAccountsRequest;
import io.pipeline.repository.account.UpdateAccountRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gRPC integration tests to verify that the active field is properly
 * returned in gRPC responses for both active and inactive accounts.
 */
@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
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
        assertTrue(createResponse.getCreated());
        assertTrue(createResponse.getAccount().getActive(), "Created account should be active");

        // Get the account and verify active field is present
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var account = accountService.getAccount(getRequest);
        
        // This is the critical test - the active field should be true
        assertTrue(account.getActive(), "Active field should be true for active account");
        assertEquals(testAccountId, account.getAccountId());
        assertEquals("Active Test Account", account.getName());
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
        assertTrue(createResponse.getCreated());
        assertTrue(createResponse.getAccount().getActive(), "Created account should be active");

        // Inactivate the account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Testing inactive field")
                .build();

        var inactivateResponse = accountService.inactivateAccount(inactivateRequest);
        assertTrue(inactivateResponse.getSuccess(), "Inactivation should succeed");

        // Get the inactive account and verify active field is present and false
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var account = accountService.getAccount(getRequest);
        
        // This is the critical test - the active field should be false
        assertFalse(account.getActive(), "Active field should be false for inactive account");
        assertEquals(testAccountId, account.getAccountId());
        assertEquals("Inactive Test Account", account.getName());
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

        assertEquals("Updated Name", updateResponse.getAccount().getName());
        assertEquals("Updated description", updateResponse.getAccount().getDescription());

        var fetched = accountService.getAccount(GetAccountRequest.newBuilder()
            .setAccountId(testAccountId)
            .build());

        assertEquals("Updated Name", fetched.getName());
        assertEquals("Updated description", fetched.getDescription());
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

        var activeAccount = accountService.getAccount(getRequest);
        assertTrue(activeAccount.getActive());

        // Inactivate account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Testing consistency")
                .build();

        accountService.inactivateAccount(inactivateRequest);

        // Verify inactive account
        var inactiveAccount = accountService.getAccount(getRequest);
        assertFalse(inactiveAccount.getActive(), "Active field should be false for inactive accounts");
        
        // Verify other fields are still present
        assertEquals(testAccountId, inactiveAccount.getAccountId());
        assertEquals("Consistency Test Account", inactiveAccount.getName());
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

        assertTrue(response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-active")));
        assertFalse(response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-inactive")));
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

        assertEquals(2, response.getAccountsCount());
        assertTrue(response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-one")));
        assertTrue(response.getAccountsList().stream().anyMatch(a -> a.getAccountId().equals(idPrefix + "-two")));
    }
}
