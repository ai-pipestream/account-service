package ai.pipestream.account.services;

import ai.pipestream.apicurio.registry.protobuf.UuidKeyExtractor;
import ai.pipestream.repository.v1.account.AccountEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Key extractor for AccountEvent messages.
 * Uses the accountId as the UUID key.
 */
@ApplicationScoped
public class AccountEventUuidKeyExtractor implements UuidKeyExtractor<AccountEvent> {

    @Override
    public UUID extractKey(AccountEvent event) {
        String accountId = event.getAccountId();
        if (accountId == null || accountId.isEmpty()) {
             // Or throw?
             return UUID.randomUUID(); // Fallback to random if empty? Or fail?
             // Let's assume ID is present.
        }
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException e) {
            // Fallback for non-UUID strings (e.g. test IDs or legacy IDs)
            // Generate a deterministic UUID based on the string.
            return UUID.nameUUIDFromBytes(accountId.getBytes());
        }
    }
}
