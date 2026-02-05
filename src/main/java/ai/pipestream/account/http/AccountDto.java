package ai.pipestream.account.http;

import java.time.OffsetDateTime;

/**
 * REST DTO for Account representation.
 * Used for JSON serialization in the Account REST API.
 */
public record AccountDto(
        String accountId,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
