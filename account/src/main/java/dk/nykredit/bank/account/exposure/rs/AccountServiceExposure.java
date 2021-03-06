package dk.nykredit.bank.account.exposure.rs;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import dk.nykredit.bank.account.exposure.rs.model.AccountRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.AccountSparseRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.AccountUpdateRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.AccountsRepresentation;
import dk.nykredit.bank.account.model.Account;
import dk.nykredit.bank.account.persistence.AccountArchivist;
import dk.nykredit.nic.core.logging.LogDuration;
import dk.nykredit.nic.rs.EntityResponseBuilder;
import dk.nykredit.nic.rs.error.ErrorRepresentation;
import dk.nykredit.time.CurrentTime;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposing account as REST service
 * <p>
 * - the example includes the use of content-type versioning.
 * This Accounts (a list) representation shows a situation with a projection for accounts in a
 * list view. The example implementation is rather rudimentary and is only aimed at serving as
 * a really simple example for the implementation of the HATEOAS/HAL example.
 * <p>
 * In the example an information logging is used to show which versions and content-types are
 * requested and accepted from the consumer side, this should not be done as an info.logging in
 * a real system, that should be collected by a statistics function, so you would know the exact
 * consequences of removing support for an older version and thus not keep growing your service.
 * <p>
 * Please note that the content types added for the specific versions do require support for the
 * <b>content-type media-range parameter</b> in order to be unique, see more concrete advice in
 * the versioned resources.
 */
@Stateless
@Path("/accounts")
@PermitAll
@DeclareRoles("advisor")
@Api(value = "/accounts", tags = {"accounts"})
public class AccountServiceExposure {
    private static final String CONCEPT_NAME = "account";
    private static final String CONCEPT_VERSION = "2.0.0";
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceExposure.class);


    @EJB
    private AccountArchivist archivist;

    @GET
    @Produces({"application/hal+json"})
    @ApiOperation(value = "lists accounts", response = AccountsRepresentation.class,
            authorizations = {@Authorization(value = "oauth", scopes = {
                                    @AuthorizationScope(scope = "advisor", description = "allows getting every account")
                            }
                    )
            },
            produces = "application/hal+json, application/hal+json;concept=accountoverview;v=1",
            notes = "List all accounts in a default projection, which is AccountOverview version 1" +
                    "Supported projections and versions are: " +
                    "AccountOverview in version 1 " +
                    "The Accept header for the default version is application/hal+json;concept=AccountOverview;v=1.0.0.... " +
                    "The format for the default version is {....}", nickname = "listAccounts")
    public Response list(@Context UriInfo uriInfo, @Context Request request) {
        return listServiceGeneration1Version1(uriInfo, request);
    }

    @GET
    @Path("{regNo}-{accountNo}")
    @Produces({"application/hal+json"})
    @ApiOperation(value = "gets the information from a single account", response = AccountRepresentation.class,
            authorizations = {@Authorization(value = "oauth", scopes = {
                    @AuthorizationScope(scope = "customer", description = "allows getting own account"),
                    @AuthorizationScope(scope = "advisor", description = "allows getting every account")})
            },
            produces = "application/hal+json, application/hal+json;concept=account;v=1, application/hal+json;concept=account;v=2",
            notes = "obtain a single account back in a default projection, which is Account version 2" +
                    " Supported projections and versions are:" +
                    " AccountSparse in version1 and Account in version 2" +
                    " The format of the default version is .... ", nickname = "getAccount")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No account found.")
    })
    public Response get(@PathParam("regNo") @Pattern(regexp = "^[0-9]{4}$") String regNo,
                        @PathParam("accountNo") @Pattern(regexp = "^[0-9]+$") String accountNo,
                        @Context UriInfo uriInfo, @Context Request request) {
        LOGGER.info("Default version of account collected");
        return getServiceGeneration1Version2(regNo, accountNo, uriInfo, request);
    }

    @PUT
    @RolesAllowed("advisor")
    @Path("{regNo}-{accountNo}")
    @Produces({"application/hal+json"})
    @Consumes(MediaType.APPLICATION_JSON)
    @LogDuration(limit = 50)
    @ApiOperation(value = "Create new or update existing account", response = AccountRepresentation.class,
            authorizations = {@Authorization( value = "oauth",scopes = {
                    @AuthorizationScope( scope = "customer", description = "allows getting own account"),
                    @AuthorizationScope( scope = "system", description = "allows getting coOwned account"),
                    @AuthorizationScope( scope = "advisor", description = "allows getting every account")})
            },
            notes = "PUT is used to create a new account from scratch and may be used to alter the name of the account",
            consumes = "application/json",
            produces = "application/hal+json, application/hal+json;concept=account;v=1, application/hal+json;concept=account;v=2",
            nickname = "updateAccount")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Could not update or create the account", response = ErrorRepresentation.class),
            @ApiResponse(code = 201, message = "New Account Created", response = AccountRepresentation.class,
                    responseHeaders = {
                            @ResponseHeader(name = "Location", description = "a link to the created resource"),
                            @ResponseHeader(name = "Content-Type", description = "a link to the created resource"),
                            @ResponseHeader(name = "X-Log-Token", description = "an ide for reference purposes in logs etc")
                    })
    })
    public Response createOrUpdate(@PathParam("regNo") @Pattern(regexp = "^[0-9]{4}$") String regNo,
                                   @PathParam("accountNo") @Pattern(regexp = "^[0-9]+$") String accountNo,
                                   @ApiParam(value = "account") @Valid AccountUpdateRepresentation account,
                                   @Context UriInfo uriInfo, @Context Request request) {
        if (!regNo.equals(account.getRegNo()) || !accountNo.equals(account.getAccountNo())) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        Optional<Account> acc = archivist.findAccount(regNo, accountNo);
        Account a;
        if (acc.isPresent()) {
            a = acc.get();
            a.setName(account.getName());
        } else {
            a = new Account(regNo, accountNo, account.getName());
        }
        archivist.save(a);

        CacheControl cc = new CacheControl();
        int maxAge = 30;
        cc.setMaxAge(maxAge);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("concept", "account");
        parameters.put("v", "2");

        return Response.created(URI.create(uriInfo.getPath()))
                .entity(new AccountRepresentation(a, uriInfo))
                .cacheControl(cc).expires(Date.from(CurrentTime.now().plusSeconds(maxAge)))
                .status(201)
                .type(EntityResponseBuilder.getMediaType(parameters, true))
                .build();
    }

    @GET
    @Produces({"application/hal+json;concept=accountoverview;v=1", "application/hal+json+accountoverview+1"})
    @LogDuration(limit = 50)
    public Response listServiceGeneration1Version1(@Context UriInfo uriInfo, @Context Request request) {
        List<Account> accounts = archivist.listAccounts();
        return new EntityResponseBuilder<>(accounts, list -> new AccountsRepresentation(list, uriInfo))
                .maxAge(10)
                .build(request);
    }

    @GET
    @Path("{regNo}-{accountNo}")
    @Produces({"application/hal+json;concept=account;v=1", "application/hal+json+account+1"})
    @LogDuration(limit = 50)
    /**
     * If you are running a JEE container that inhibits the creation of resources, because it does
     * not support the specification of the Accept header and thus does not support the media-range
     * parameters, a simple producer has to be annotated and if the
     * "application/hal+json;concept=Account;v=1.0.0" is removed and replaced with
     * "{"application/hal+json+account+1" then the endpoint will work with versioning.
     * The correct content-type controlled by the Accept header is "application/hal+json;concept=Account;v=1.0.0"
     */

    public Response getServiceGeneration1Version1(@PathParam("regNo") @Pattern(regexp = "^[0-9]{4}$") String regNo,
                                                  @PathParam("accountNo") @Pattern(regexp = "^[0-9]+$") String accountNo,
                                                  @Context UriInfo uriInfo, @Context Request request) {
        Account account = archivist.getAccount(regNo, accountNo);
        LOGGER.info("Usage - application/hal+json;concept=account;v=1");
        return new EntityResponseBuilder<>(account, acc -> new AccountSparseRepresentation(acc, uriInfo))
                .name("account")
                .version("1")
                .maxAge(120)
                .build(request);
    }

    @GET
    @Path("{regNo}-{accountNo}")
    @Produces({"application/hal+json;concept=account;v=2", "application/hal+json+account+2"})
    @LogDuration(limit = 50)
    /**
     * If you are running a JEE container that inhibits the creation of resources, because it does
     * not support the specification of the Accept header and thus does not support the media-range
     * parameters, a simple producer has to be annotated and if the
     * "application/hal+json;concept=Account;v=2.0.0" is removed and replaced with
     * "{"application/hal+json+account+2" then the endpoint will work with versioning.
     * The correct content-type controlled by the Accept header is "application/hal+json;concept=Account;v=2.0.0"
     */
    public Response getServiceGeneration1Version2(@PathParam("regNo") @Pattern(regexp = "^[0-9]{4}$") String regNo,
                                                  @PathParam("accountNo") @Pattern(regexp = "^[0-9]+$") String accountNo,
                                                  @Context UriInfo uriInfo, @Context Request request) {
        Account account = archivist.getAccount(regNo, accountNo);
        LOGGER.info("Usage - application/hal+json;concept=account;v=2");
        return new EntityResponseBuilder<>(account, acc -> new AccountRepresentation(acc, acc.getTransactions(), uriInfo))
                .name("account")
                .version("2")
                .maxAge(60)
                .build(request);
    }

}
