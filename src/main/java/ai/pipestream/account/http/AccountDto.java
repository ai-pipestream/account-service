package ai.pipestream.account.http;

import java.time.OffsetDateTime;

/**
 * REST DTO for Account representation.
 * Used for JSON serialization in the Account REST API.
 *
 * @param accountId   unique account identifier
 * @param name        display name
 * @param description optional account description
 * @param active      whether the account is active
 * @param createdAt   creation timestamp
 * @param updatedAt   last update timestamp
 */
public record AccountDto(
        String accountId,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
