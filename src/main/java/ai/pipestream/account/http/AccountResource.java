package ai.pipestream.account.http;

import ai.pipestream.account.entity.Account;
import ai.pipestream.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST API for account management.
 * Provides read-only endpoints for use by web frontends and API consumers.
 * <p>
 * OpenAPI documentation is automatically published at /q/openapi when using pipestream-server.
 */
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts", description = "Account management operations")
@SuppressWarnings("java:S6813") // CDI field injection
public class AccountResource {

    /** Creates a new AccountResource (CDI managed). */
    public AccountResource() {}

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    @Inject
    AccountRepository accountRepository;

    /**
     * Lists accounts with optional filtering and pagination.
     *
     * @param includeInactive whether to include inactive accounts
     * @param pageSize        number of results per page
     * @param pageToken       opaque token for the next page
     * @param query           optional search query
     * @return paginated account list
     */
    @GET
    @Operation(summary = "List accounts", description = "Returns a paginated list of accounts, excluding inactive by default.")
    @APIResponse(responseCode = "200", description = "List of accounts")
    public AccountListResponse listAccounts(
            @QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive,
            @QueryParam("pageSize") @DefaultValue("50") int pageSize,
            @QueryParam("pageToken") String pageToken,
            @QueryParam("query") String query) {

        int size = pageSize > 0 ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int offset = 0;
        if (pageToken != null && !pageToken.isBlank()) {
            try {
                offset = Math.max(0, Integer.parseInt(pageToken.trim()));
            } catch (NumberFormatException ignored) {
                // ignore invalid token
            }
        }

        String q = query != null ? query.trim() : "";
        var accounts = accountRepository.listAccounts(q, includeInactive, size + 1, offset);
        long totalCount = accountRepository.countAccounts(q, includeInactive);

        String nextPageToken = null;
        if (accounts.size() > size) {
            accounts = accounts.subList(0, size);
            nextPageToken = String.valueOf(offset + size);
        }

        List<AccountDto> dtos = accounts.stream()
                .map(AccountResource::toDto)
                .toList();

        return new AccountListResponse(dtos, (int) Math.min(totalCount, Integer.MAX_VALUE), nextPageToken);
    }

    /**
     * Returns a single account by ID.
     *
     * @param accountId the account identifier
     * @return the account, or 404 if not found
     */
    @GET
    @Path("/{accountId}")
    @Operation(summary = "Get account", description = "Returns a single account by ID.")
    @APIResponse(responseCode = "200", description = "Account found")
    @APIResponse(responseCode = "404", description = "Account not found")
    public Response getAccount(@PathParam("accountId") String accountId) {
        Account account = accountRepository.findByAccountId(accountId);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toDto(account)).build();
    }

    private static AccountDto toDto(Account a) {
        return new AccountDto(
                a.accountId,
                a.name,
                a.description != null ? a.description : "",
                a.active != null ? a.active : false,
                a.createdAt,
                a.updatedAt
        );
    }

    /**
     * Response wrapper for the list endpoint.
     *
     * @param accounts      page of account DTOs
     * @param totalCount    total number of matching accounts
     * @param nextPageToken token for fetching the next page, or {@code null}
     */
    public record AccountListResponse(List<AccountDto> accounts, int totalCount, String nextPageToken) {}
}
