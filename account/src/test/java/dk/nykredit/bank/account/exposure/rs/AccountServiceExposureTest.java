package dk.nykredit.bank.account.exposure.rs;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import dk.nykredit.bank.account.exposure.rs.model.AccountRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.AccountUpdateRepresentation;
import dk.nykredit.bank.account.exposure.rs.model.AccountsRepresentation;
import dk.nykredit.bank.account.model.Account;
import dk.nykredit.bank.account.persistence.AccountArchivist;
import dk.nykredit.nic.test.rs.UriBuilderFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AccountServiceExposureTest {

    @Mock
    AccountArchivist archivist;

    @InjectMocks
    AccountServiceExposure service;

    @Test
    public void testList() {
        Request request = mock(Request.class);

        UriInfo ui = mock(UriInfo.class);
        when(ui.getBaseUriBuilder()).then(new UriBuilderFactory(URI.create("http://mock")));

        when(archivist.listAccounts())
            .thenReturn(Arrays.asList(new Account("5479", "1", "Checking account"), new Account("5479", "2", "Savings account")));

        Response response = service.list(ui, request);
        AccountsRepresentation accounts = (AccountsRepresentation) response.getEntity();

        assertEquals(2, accounts.getAccounts().size());
        assertEquals("http://mock/accounts", accounts.getSelf().getHref());
    }

    @Test
    public void testGet() {
        Request request = mock(Request.class);

        UriInfo ui = mock(UriInfo.class);
        when(ui.getBaseUriBuilder()).then(new UriBuilderFactory(URI.create("http://mock")));

        when(archivist.getAccount("5479", "1234")).thenReturn(new Account("5479", "1234", "Savings account"));

        AccountRepresentation account = (AccountRepresentation) service.get("5479", "1234", ui, request).getEntity();

        assertEquals("5479", account.getRegNo());
        assertEquals("1234", account.getAccountNo());
        assertEquals("http://mock/accounts/5479-1234", account.getSelf().getHref());
    }

    @Test
    public void testCreate() throws Exception {
        Request request = mock(Request.class);
        UriInfo ui = mock(UriInfo.class);
        when(ui.getBaseUriBuilder()).then(new UriBuilderFactory(URI.create("http://mock")));
        when(ui.getPath()).thenReturn("http://mock");

        AccountUpdateRepresentation accountUpdate = mock(AccountUpdateRepresentation.class);
        when(accountUpdate.getName()).thenReturn("new Account");
        when(accountUpdate.getRegNo()).thenReturn("5479");
        when(accountUpdate.getAccountNo()).thenReturn("12345678");

        when(archivist.findAccount("5479", "12345678")).thenReturn(Optional.empty());

        AccountRepresentation resp = (AccountRepresentation) service.createOrUpdate("5479", "12345678", accountUpdate, ui, request).getEntity();

        assertEquals("new Account", resp.getName());
        assertEquals("5479", resp.getRegNo());
        assertEquals("12345678", resp.getAccountNo());
        assertEquals("http://mock/accounts/5479-12345678", resp.getSelf().getHref());
    }

    @Test
    public void testUpdate() throws Exception {
        Request request = mock(Request.class);
        UriInfo ui = mock(UriInfo.class);
        when(ui.getBaseUriBuilder()).then(new UriBuilderFactory(URI.create("http://mock")));
        when(ui.getPath()).thenReturn("http://mock");

        Account existingAcc = new Account("5479", "12345678", "Savings account");
        when(archivist.findAccount("5479", "12345678")).thenReturn(Optional.of(existingAcc));

        AccountUpdateRepresentation accountUpdate = mock(AccountUpdateRepresentation.class);
        when(accountUpdate.getName()).thenReturn("new name");
        when(accountUpdate.getRegNo()).thenReturn("5479");
        when(accountUpdate.getAccountNo()).thenReturn("12345678");

        AccountRepresentation resp = (AccountRepresentation) service.createOrUpdate("5479", "12345678", accountUpdate, ui, request).getEntity();

        //name of the existing account should be updated
        assertEquals("new name", existingAcc.getName());
        assertEquals("new name", resp.getName());
        assertEquals("5479", resp.getRegNo());
        assertEquals("12345678", resp.getAccountNo());
        assertEquals("http://mock/accounts/5479-12345678", resp.getSelf().getHref());
    }

    @Test(expected = WebApplicationException.class)
    public void testCreateInvalidRequest() throws Exception {
        Request request = mock(Request.class);
        UriInfo ui = mock(UriInfo.class);

        AccountUpdateRepresentation accountUpdate = mock(AccountUpdateRepresentation.class);
        when(accountUpdate.getRegNo()).thenReturn("5479");
        when(accountUpdate.getAccountNo()).thenReturn("12345678");

        service.createOrUpdate("5479", "87654321", accountUpdate, ui, request);
        fail("Should have thrown exception before this step");
    }
}
