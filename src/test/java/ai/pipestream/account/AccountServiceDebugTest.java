package ai.pipestream.account;

import ai.pipestream.test.support.WireMockTestResource;
import ai.pipestream.repository.account.v1.AccountServiceGrpc;
import ai.pipestream.repository.account.v1.CreateAccountRequest;
import ai.pipestream.repository.account.v1.GetAccountRequest;
import ai.pipestream.repository.account.v1.InactivateAccountRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to understand the protobuf serialization behavior
 * for the active field in both active and inactive accounts.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
public class AccountServiceDebugTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @BeforeEach
    void cleanupBefore() {
        // Clean up any test accounts from previous runs
    }

    @Test
    public void debugActiveFieldSerialization() {
        String testAccountId = "debug-test-" + System.currentTimeMillis();
        
        // Create an active account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Debug Test Account")
                .setDescription("Debugging active field serialization")
                .build();

        var createResponse = accountService.createAccount(createRequest);
        System.out.println("=== CREATE RESPONSE ===");
        System.out.println("Created: " + createResponse.getCreated());
        System.out.println("Account Active: " + createResponse.getAccount().getActive());
        System.out.println("Account toString: " + createResponse.getAccount().toString());
        System.out.println();

        // Get the active account
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var activeResponse = accountService.getAccount(getRequest);
        System.out.println("=== ACTIVE ACCOUNT ===");
        System.out.println("Active: " + activeResponse.getAccount().getActive());
        System.out.println("toString: " + activeResponse.getAccount().toString());
        System.out.println();

        // Inactivate the account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Debug test")
                .build();

        var inactivateResponse = accountService.inactivateAccount(inactivateRequest);
        System.out.println("=== INACTIVATE RESPONSE ===");
        System.out.println("Success: " + inactivateResponse.getSuccess());
        System.out.println("Message: " + inactivateResponse.getMessage());
        System.out.println();

        // Get the inactive account
        var inactiveResponse = accountService.getAccount(getRequest);
        System.out.println("=== INACTIVE ACCOUNT ===");
        System.out.println("Active: " + inactiveResponse.getAccount().getActive());
        System.out.println("toString: " + inactiveResponse.getAccount().toString());
        System.out.println();

        // Verify the behavior
        assertTrue(activeResponse.getAccount().getActive(), "Active account should have active=true");
        assertFalse(inactiveResponse.getAccount().getActive(), "Inactive account should have active=false");
        
        // The key test: both should have the active field accessible
        // (even if it's not shown in grpcurl JSON output)
        assertNotNull(activeResponse.getAccount().getActive());
        assertNotNull(inactiveResponse.getAccount().getActive());
    }
}