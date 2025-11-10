package io.pipeline.account.services;

import io.pipeline.repository.account.AccountEvent;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

/**
 * Publisher for account lifecycle events to Kafka.
 * <p>
 * This service publishes account-related events (created, updated, inactivated, reactivated)
 * to a Kafka topic for consumption by other services in the platform. Events are published
 * in protobuf format with Apicurio schema registry validation.
 * <p>
 * The event publisher is designed to be fire-and-forget - failures in event publishing
 * do not prevent the underlying account operations from succeeding. This ensures that
 * core account management remains reliable even if event infrastructure is unavailable.
 * <p>
 * Thread Safety: This class is application-scoped and thread-safe. The MutinyEmitter
 * handles concurrent event publishing internally.
 * <p>
 * <b>Event Format:</b>
 * <ul>
 *   <li>Event ID: Generated hash based on account ID, operation, and timestamp</li>
 *   <li>Kafka Key: Account ID (ensures all events for an account go to same partition)</li>
 *   <li>Topic: account-events</li>
 *   <li>Payload: Protobuf AccountEvent message</li>
 * </ul>
 *
 * @see AccountEvent
 * @see io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata
 */
@ApplicationScoped
public class AccountEventPublisher {

    private static final Logger LOG = Logger.getLogger(AccountEventPublisher.class);

    /**
     * Mutiny emitter for publishing account events to the "account-events" Kafka topic.
     * <p>
     * This emitter is configured via the application.properties reactive messaging settings
     * and connects to the Kafka broker specified in the configuration.
     */
    @Inject
    @Channel("account-events")
    MutinyEmitter<AccountEvent> accountEventEmitter;
    
    /**
     * Publish an account created event to Kafka.
     * <p>
     * This event notifies downstream services that a new account has been created in the system.
     * The event includes the account's name and description for services that need to
     * track account metadata.
     * <p>
     * The event is published asynchronously using fire-and-forget semantics. If publishing fails,
     * an error is logged and a RuntimeException is thrown, but the account creation itself
     * has already succeeded.
     *
     * @param accountId the unique identifier of the created account (used as Kafka message key)
     * @param name the display name of the account
     * @param description optional description of the account (empty string if null)
     * @return Cancellable that can be used to cancel the publishing operation
     * @throws RuntimeException if the event cannot be published to Kafka
     */
    public Cancellable publishAccountCreated(String accountId, String name, String description) {
        try {
            AccountEvent.Created created = AccountEvent.Created.newBuilder()
                .setName(name)
                .setDescription(description != null ? description : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "created"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setCreated(created)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account created event: accountId=%s", accountId);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account created event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account created event", e);
        }
    }
    
    /**
     * Publish an account updated event to Kafka.
     * <p>
     * This event notifies downstream services that an account's metadata (name or description)
     * has been modified. Services that maintain cached account information can use this event
     * to refresh their caches.
     * <p>
     * The event is published asynchronously using fire-and-forget semantics. If publishing fails,
     * an error is logged and a RuntimeException is thrown, but the account update itself
     * has already succeeded.
     *
     * @param accountId the unique identifier of the updated account (used as Kafka message key)
     * @param name the new display name of the account
     * @param description the new description of the account (empty string if null)
     * @return Cancellable that can be used to cancel the publishing operation
     * @throws RuntimeException if the event cannot be published to Kafka
     */
    public Cancellable publishAccountUpdated(String accountId, String name, String description) {
        try {
            AccountEvent.Updated updated = AccountEvent.Updated.newBuilder()
                .setName(name)
                .setDescription(description != null ? description : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "updated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setUpdated(updated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account updated event: accountId=%s", accountId);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account updated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account updated event", e);
        }
    }
    
    /**
     * Publish an account inactivated event to Kafka.
     * <p>
     * This event notifies downstream services that an account has been soft-deleted (inactivated).
     * Services should stop accepting new operations for this account and may need to clean up
     * or archive associated resources.
     * <p>
     * The event includes the reason for inactivation, which can be useful for audit trails
     * and understanding why accounts were disabled.
     * <p>
     * The event is published asynchronously using fire-and-forget semantics. If publishing fails,
     * an error is logged and a RuntimeException is thrown, but the account inactivation itself
     * has already succeeded.
     *
     * @param accountId the unique identifier of the inactivated account (used as Kafka message key)
     * @param reason the reason for inactivation (empty string if null)
     * @return Cancellable that can be used to cancel the publishing operation
     * @throws RuntimeException if the event cannot be published to Kafka
     */
    public Cancellable publishAccountInactivated(String accountId, String reason) {
        try {
            AccountEvent.Inactivated inactivated = AccountEvent.Inactivated.newBuilder()
                .setReason(reason != null ? reason : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "inactivated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setInactivated(inactivated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account inactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account inactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account inactivated event", e);
        }
    }
    
    /**
     * Publish an account reactivated event to Kafka.
     * <p>
     * This event notifies downstream services that a previously inactivated account has been
     * reactivated and is now available for normal operations again. Services that previously
     * stopped accepting operations for this account can resume normal processing.
     * <p>
     * The event includes the reason for reactivation, which can be useful for audit trails
     * and understanding the account lifecycle.
     * <p>
     * The event is published asynchronously using fire-and-forget semantics. If publishing fails,
     * an error is logged and a RuntimeException is thrown, but the account reactivation itself
     * has already succeeded.
     *
     * @param accountId the unique identifier of the reactivated account (used as Kafka message key)
     * @param reason the reason for reactivation (empty string if null)
     * @return Cancellable that can be used to cancel the publishing operation
     * @throws RuntimeException if the event cannot be published to Kafka
     */
    public Cancellable publishAccountReactivated(String accountId, String reason) {
        try {
            AccountEvent.Reactivated reactivated = AccountEvent.Reactivated.newBuilder()
                .setReason(reason != null ? reason : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "reactivated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setReactivated(reactivated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account reactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account reactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account reactivated event", e);
        }
    }
    
    /**
     * Generate a unique event ID for tracking and deduplication.
     * <p>
     * The event ID is generated by hashing the combination of account ID, operation type,
     * and current timestamp in milliseconds. While not cryptographically secure, this
     * provides sufficient uniqueness for event tracking and correlation.
     * <p>
     * The hash ensures the event ID is compact and suitable for logging and debugging.
     * The timestamp component ensures that different events for the same account will
     * have different IDs.
     * <p>
     * <b>Format:</b> {@code String.valueOf(hash(accountId + operation + timestampMillis))}
     *
     * @param accountId the account identifier being operated on
     * @param operation the type of operation (e.g., "created", "updated", "inactivated")
     * @return a string representation of the hash code as the event ID
     */
    private String generateEventId(String accountId, String operation) {
        long timestampMillis = System.currentTimeMillis();
        String input = accountId + operation + timestampMillis;
        return String.valueOf(input.hashCode());
    }
}
