package io.pipeline.account;

import io.pipeline.grpc.wiremock.MockServiceTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.pipeline.repository.account.AccountServiceGrpc;
import io.pipeline.repository.account.CreateAccountRequest;
import io.pipeline.repository.account.GetAccountRequest;
import io.pipeline.repository.account.InactivateAccountRequest;
import io.quarkus.grpc.GrpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class AccountServiceTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @Test
    public void testCreateAccount() {
        String testAccountId = "test-account-" + System.currentTimeMillis();
        
        var request = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Account")
                .setDescription("Test account for unit testing")
                .build();

        var response = accountService.createAccount(request);
        
        assertTrue(response.getCreated());
        assertEquals(testAccountId, response.getAccount().getAccountId());
        assertEquals("Test Account", response.getAccount().getName());
        assertEquals("Test account for unit testing", response.getAccount().getDescription());
        assertTrue(response.getAccount().getActive());
    }

    @Test
    public void testGetAccount() {
        String testAccountId = "test-get-account-" + System.currentTimeMillis();
        
        // First create an account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Get Account")
                .setDescription("Test account for get testing")
                .build();
        accountService.createAccount(createRequest);

        // Then get it
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var account = accountService.getAccount(getRequest);
        
        assertEquals(testAccountId, account.getAccountId());
        assertEquals("Test Get Account", account.getName());
        assertEquals("Test account for get testing", account.getDescription());
        assertTrue(account.getActive());
    }

    @Test
    public void testInactivateAccount() {
        String testAccountId = "test-inactivate-account-" + System.currentTimeMillis();
        
        // First create an account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Inactivate Account")
                .setDescription("Test account for inactivate testing")
                .build();
        accountService.createAccount(createRequest);

        // Then inactivate it
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Unit test inactivation")
                .build();

        var response = accountService.inactivateAccount(inactivateRequest);
        
        assertTrue(response.getSuccess());
        assertEquals("Account inactivated successfully", response.getMessage());
    }
}
