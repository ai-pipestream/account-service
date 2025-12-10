package ai.pipestream.account;

import ai.pipestream.account.util.WireMockTestResource;
import ai.pipestream.repository.v1.account.AccountEvent;
import ai.pipestream.repository.v1.account.AccountServiceGrpc;
import ai.pipestream.repository.v1.account.CreateAccountRequest;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
public class AccountEventPublisherTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @Inject
    TestConsumer testConsumer;

    @BeforeEach
    void setup() {
        testConsumer.clear();
    }

    @Test
    public void testAccountCreatedEventIsPublished() {
        // ARRANGE: Create a unique ID for this specific test run
        String testAccountId = "test-kafka-create-" + System.currentTimeMillis();

        // ACT: Call the gRPC endpoint which should produce the message
        var request = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Kafka Test Account")
                .build();
        accountService.createAccount(request);

        // ASSERT: Use Awaitility to poll until we find our specific message
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> testConsumer.hasReceived(testAccountId));

        // Perform final assertions
        AccountEvent foundEvent = testConsumer.getEvent(testAccountId);
        assertNotNull(foundEvent);
        assertTrue(foundEvent.hasCreated(), "Event should be a Created event");
        assertEquals("Kafka Test Account", foundEvent.getCreated().getName());
        assertEquals(testAccountId, foundEvent.getAccountId());
    }

    @ApplicationScoped
    public static class TestConsumer {
        private final List<AccountEvent> received = new CopyOnWriteArrayList<>();

        @Incoming("test-account-events")
        public void consume(AccountEvent event) {
            received.add(event);
        }

        public boolean hasReceived(String accountId) {
            return received.stream()
                    .anyMatch(event -> event.getAccountId().equals(accountId));
        }

        public AccountEvent getEvent(String accountId) {
            return received.stream()
                    .filter(event -> event.getAccountId().equals(accountId))
                    .findFirst()
                    .orElse(null);
        }

        public void clear() {
            received.clear();
        }
    }
}