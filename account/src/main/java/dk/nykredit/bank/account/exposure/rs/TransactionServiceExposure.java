package dk.nykredit.bank.account.exposure.rs;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import dk.nykredit.api.capabilities.Element;
import dk.nykredit.api.capabilities.Interval;
import dk.nykredit.api.capabilities.Sort;
import dk.nykredit.bank.account.exposure.rs.model.TransactionRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.TransactionUpdateRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.TransactionsRepresentation;
import dk.nykredit.bank.account.model.Account;
import dk.nykredit.bank.account.model.Event;
import dk.nykredit.bank.account.model.Transaction;
import dk.nykredit.bank.account.persistence.AccountArchivist;
import dk.nykredit.nic.rs.EntityResponseBuilder;
import dk.nykredit.nic.core.logging.LogDuration;
import dk.nykredit.time.CurrentTime;
import io.swagger.annotations.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * REST exposure of account transactions.
 */
@Stateless
@PermitAll
@DeclareRoles("tx-system")
@Path("/accounts/{regNo}-{accountNo}/transactions")
@Api(value = "/accounts/{regNo}-{accountNo}/transactions",
     tags = {"immutable", "transactions"},
     description = "The Transaction resource lets you interact with simple example transaction resource" +
        " linked to an account. A transaction contains an amount and a text accompanying the movement" +
                " on the account. The example is created to examplify simple use of HATEOAS/HAL ")
public class TransactionServiceExposure {
    private static final String CONCEPT_NAME = "transaction";
    private static final String CONCEPT_VERSION = "1.0.0";

    @EJB
    private AccountArchivist archivist;

    @GET
    @Produces({ "application/hal+json" })
    @ApiOperation(
            value = "obtain all transactions on account for a given account", response = TransactionsRepresentation.class,
            tags = {"sort", "elements","interval","transactions"},
            nickname = "listTransactions"
    )
    public Response list(@PathParam("regNo") String regNo, @PathParam("accountNo") String accountNo,
                         @QueryParam("sort") String sort, @QueryParam("elements") String elements,
                         @QueryParam("interval") String interval,
                         @Context UriInfo uriInfo, @Context Request request) {

        return listTransactionsSG1V1(regNo, accountNo, sort, elements, interval, uriInfo, request);
    }

    @GET
    @Path("{id}")
    @Produces({ "application/hal+json" })
    @LogDuration(limit = 50)
    @ApiOperation(
            value = "obtain the individual single transaction from an account", response = TransactionRepresentation.class,
            nickname = "getTransaction")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No transaction found.")
    })
    public Response get(@PathParam("regNo") String regNo, @PathParam("accountNo") String accountNo, @PathParam("id") String id,
                        @Context UriInfo uriInfo, @Context Request request) {
        return getSG1V1(regNo, accountNo, id, uriInfo, request);
    }

    @PUT
    @Path("{id}")
    @RolesAllowed("tx-system")
    @Produces({ "application/hal+json" })
    @Consumes(MediaType.APPLICATION_JSON)
    @LogDuration(limit = 50)
    @ApiOperation(value = "creates a single transaction on an account", response = TransactionRepresentation.class,
                  nickname = "setTransaction")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Could create the new transaction"),
            @ApiResponse(code = 201, message = "New transaction created.")
    })
    public Response set(@PathParam("regNo") String regNo, @PathParam("accountNo") String accountNo, @PathParam("id") String id,
                        @Valid TransactionUpdateRepresentation tx,
                        @Context UriInfo uriInfo, @Context Request request) {

        Optional<Account> acc = archivist.findAccount(regNo, accountNo);
        Account a;
        Transaction t;
        if (acc.isPresent()) {
            a = acc.get();
            try {
                t = new Transaction(a, new BigDecimal(tx.getAmount()), tx.getDescription());
                a.addTransaction(t.getDescription(), t.getAmount());
                archivist.save(a);

                CacheControl cc = new CacheControl();
                int maxAge = 30;
                cc.setMaxAge(maxAge);

                Map<String, String> parameters = new HashMap<>();
                parameters.put("concept", "transaction");
                parameters.put("v", "1.0.0");
                TransactionRepresentation transaction = new TransactionRepresentation(t, uriInfo);
                Response response = Response.created(URI.create(uriInfo.getPath()))
                        .entity(transaction)
                        .cacheControl(cc).expires(Date.from(CurrentTime.now().toInstant().plusSeconds(maxAge)))
                        .status(201)
                        .type(EntityResponseBuilder.getMediaType(parameters, true))
                        .build();
                Event newTX = new Event(new URI(uriInfo.getPath()), Event.getCategory(accountNo, regNo), "new transaction on account " + regNo + "-" + accountNo);
                archivist.save(newTX);
                return response;
            } catch (URISyntaxException e) {
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            } catch (NumberFormatException nfe) {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            } catch (RuntimeException e) {
                throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
            }
        }
        throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
    }

    @GET
    @Produces({"application/hal+json;concept=transactionoverview;v=1","application/hal+json+transactionoverview+1" })
    @LogDuration(limit = 50)
    /**
     * If you are running a JEE container that inhibits the creation of resources, because it does
     * not support the specification of the Accept header and thus does not support the media-range
     * parameters, a simple producer has to be annotated and if the
     * "application/hal+json;concept=TransactionOverview;v=1.0.0" is removed and replaced with
     * "{"application/hal+json+transactionoverview+1" then the endpoint vil work with versioning.
     * The correct content-type controlled by the Accept header is "application/hal+json;concept=Transaction;v=1.0.0"
     */
    public Response listTransactionsSG1V1(@PathParam("regNo") String regNo, @PathParam("accountNo") String accountNo,
                                          @QueryParam("sort") String sort, @QueryParam("elements") String elements,
                                          @QueryParam("interval") String interval,
                                          @Context UriInfo uriInfo, @Context Request request) {
        List<Sort> sortAs = Sort.getSortings(sort);
        Optional<Element> elementSet = Element.getElement(elements);
        Optional<Interval> withIn = Interval.getInterval(interval);
        List<Transaction> transactions = archivist.getTransactions(regNo, accountNo, elementSet, withIn, sortAs);
        return new EntityResponseBuilder<>(transactions, txs -> new TransactionsRepresentation(regNo, accountNo, transactions, uriInfo))
                .maxAge(10)
                .build(request);
    }

    @GET
    @Path("{id}")
    @Produces({"application/hal+json;concept=transaction;v=1","application/hal+json+transaction+1" })
    @LogDuration(limit = 50)
    /**
     * If you are running a JEE container that inhibits the creation of resources, because it does
     * not support the specification of the Accept header and thus does not support the media-range
     * parameters, a simple producer has to be annotated and if the
     * "application/hal+json;concept=Transaction;v=1.0.0" is removed and replaced with
     * "{"application/hal+json+transaction+1" then the endpoint vil work with versioning.
     * The correct content-type controlled by the Accept header is "application/hal+json;concept=Transaction;v=1.0.0"
     */
    public Response getSG1V1(@PathParam("regNo") String regNo, @PathParam("accountNo") String accountNo, @PathParam("id") String id,
                        @Context UriInfo uriInfo, @Context Request request) {
        Transaction transaction = archivist.getTransaction(regNo, accountNo, id);
        return new EntityResponseBuilder<>(transaction, t -> new TransactionRepresentation(t, uriInfo))
                .maxAge(7 * 24 * 60 * 60)
                .name(CONCEPT_NAME)
                .version(CONCEPT_VERSION)
                .build(request);
    }
}