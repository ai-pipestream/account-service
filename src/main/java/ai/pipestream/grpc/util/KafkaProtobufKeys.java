package ai.pipestream.grpc.util;

import ai.pipestream.repository.account.v1.AccountEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Type-safe utility for generating deterministic Kafka keys from Protobuf
 * events.
 * <p>
 * This ensures that every event type uses the correct identity field for its
 * Kafka partition key,
 * guaranteeing correct log compaction.
 */
public class KafkaProtobufKeys {

    private KafkaProtobufKeys() {
        // Utility class
    }

    /**
     * Generates a deterministic UUID key for AccountEvent based on Account ID.
     *
     * @param event The account event containing an account ID
     * @return A deterministic UUID generated from the account ID
     */
    public static UUID uuid(AccountEvent event) {
        return uuidFrom(event.getAccountId());
    }

    private static UUID uuidFrom(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null for Kafka key generation");
        }
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }
}
