/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.api.command;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.cloudstack.api.ApiServerService;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.auth.APIAuthenticationType;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLPluginConstants;
import org.apache.cloudstack.saml.SAMLProviderMetadata;
import org.apache.cloudstack.saml.SAMLUtils;
import org.apache.cloudstack.utils.security.CertUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.NameIDType;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.impl.AssertionBuilder;
import org.opensaml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml2.core.impl.AuthnStatementBuilder;
import org.opensaml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml2.core.impl.StatusCodeBuilder;
import org.opensaml.saml2.core.impl.SubjectBuilder;

import com.cloud.domain.Domain;
import com.cloud.user.AccountService;
import com.cloud.user.DomainManager;
import com.cloud.user.UserAccountVO;
import com.cloud.user.dao.UserAccountDao;
import com.cloud.utils.HttpUtils;

@RunWith(MockitoJUnitRunner.class)
public class SAML2LoginAPIAuthenticatorCmdTest {

    @Mock
    ApiServerService apiServer;

    @Mock
    SAML2AuthManager samlAuthManager;

    @Mock
    DomainManager domainMgr;

    @Mock
    AccountService accountService;

    @Mock
    UserAccountDao userAccountDao;

    @Mock
    Domain domain;

    @Mock
    HttpSession session;

    @Mock
    HttpServletResponse resp;

    @Mock
    HttpServletRequest req;

    @Mock
    Object _responseObject;

    @Spy
    @InjectMocks
    private SAML2LoginAPIAuthenticatorCmd cmdSpy;

    private Response buildMockResponse() throws Exception {
        Response samlMessage = new ResponseBuilder().buildObject();
        samlMessage.setID("foo");
        samlMessage.setVersion(SAMLVersion.VERSION_20);
        samlMessage.setIssueInstant(new DateTime(0));
        Issuer issuer = new IssuerBuilder().buildObject();
        issuer.setValue("MockedIssuer");
        samlMessage.setIssuer(issuer);
        Status status = new StatusBuilder().buildObject();
        StatusCode statusCode = new StatusCodeBuilder().buildObject();
        statusCode.setValue(StatusCode.SUCCESS_URI);
        status.setStatusCode(statusCode);
        samlMessage.setStatus(status);
        Assertion assertion = new AssertionBuilder().buildObject();
        Subject subject = new SubjectBuilder().buildObject();
        NameID nameID = new NameIDBuilder().buildObject();
        nameID.setValue("SOME-UNIQUE-ID");
        nameID.setFormat(NameIDType.PERSISTENT);
        subject.setNameID(nameID);
        assertion.setSubject(subject);
        AuthnStatement authnStatement = new AuthnStatementBuilder().buildObject();
        authnStatement.setSessionIndex("Some Session String");
        assertion.getAuthnStatements().add(authnStatement);
        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();
        assertion.getAttributeStatements().add(attributeStatement);
        samlMessage.getAssertions().add(assertion);
        return samlMessage;
    }

    @Test
    public void testAuthenticate() throws Exception {
        SAML2LoginAPIAuthenticatorCmd cmd = Mockito.spy(new SAML2LoginAPIAuthenticatorCmd());

        Field apiServerField = SAML2LoginAPIAuthenticatorCmd.class.getDeclaredField("apiServer");
        apiServerField.setAccessible(true);
        apiServerField.set(cmd, apiServer);

        Field managerField = SAML2LoginAPIAuthenticatorCmd.class.getDeclaredField("samlAuthManager");
        managerField.setAccessible(true);
        managerField.set(cmd, samlAuthManager);

        Field accountServiceField = BaseCmd.class.getDeclaredField("_accountService");
        accountServiceField.setAccessible(true);
        accountServiceField.set(cmd, accountService);

        Field domainMgrField = SAML2LoginAPIAuthenticatorCmd.class.getDeclaredField("domainMgr");
        domainMgrField.setAccessible(true);
        domainMgrField.set(cmd, domainMgr);

        Field userAccountDaoField = SAML2LoginAPIAuthenticatorCmd.class.getDeclaredField("userAccountDao");
        userAccountDaoField.setAccessible(true);
        userAccountDaoField.set(cmd, userAccountDao);

        KeyPair kp = CertUtils.generateRandomKeyPair(4096);
        X509Certificate cert = SAMLUtils.generateRandomX509Certificate(kp);

        SAMLProviderMetadata providerMetadata = new SAMLProviderMetadata();
        providerMetadata.setEntityId("random");
        providerMetadata.setSigningCertificate(cert);
        providerMetadata.setEncryptionCertificate(cert);
        providerMetadata.setKeyPair(kp);
        providerMetadata.setSsoUrl("http://test.local");
        providerMetadata.setSloUrl("http://test.local");

        Mockito.lenient().when(session.getAttribute(Mockito.anyString())).thenReturn(null);

        Mockito.lenient().when(domain.getId()).thenReturn(1L);
        Mockito.lenient().when(domainMgr.getDomain(Mockito.anyString())).thenReturn(domain);
        UserAccountVO user = new UserAccountVO();
        user.setId(1000L);
        Mockito.lenient().when(userAccountDao.getUserAccount(Mockito.anyString(), Mockito.anyLong())).thenReturn(user);
        Mockito.lenient().when(apiServer.verifyUser(nullable(Long.class))).thenReturn(false);
        Mockito.when(samlAuthManager.getSPMetadata()).thenReturn(providerMetadata);
        Mockito.when(samlAuthManager.getIdPMetadata(nullable(String.class))).thenReturn(providerMetadata);

        Map<String, Object[]> params = new HashMap<String, Object[]>();

        // SSO redirection test
        cmd.authenticate("command", params, session, InetAddress.getByName("127.0.0.1"), HttpUtils.RESPONSE_TYPE_JSON, new StringBuilder(), req, resp);
        Mockito.verify(resp, Mockito.times(1)).sendRedirect(Mockito.anyString());

        // SSO SAMLResponse verification test, this should throw ServerApiException for auth failure
        params.put(SAMLPluginConstants.SAML_RESPONSE, new String[]{"Some String"});
        Mockito.when(cmd.processSAMLResponse(Mockito.anyString())).thenReturn(buildMockResponse());
        boolean failing = true;
        try {
            cmd.authenticate("command", params, session, InetAddress.getByName("127.0.0.1"), HttpUtils.RESPONSE_TYPE_JSON, new StringBuilder(), req, resp);
        } catch (ServerApiException ignored) {
            failing = false;
        }
        assertFalse("authentication should not have succeeded", failing);
        Mockito.verify(userAccountDao, Mockito.times(0)).getUserAccount(Mockito.anyString(), Mockito.anyLong());
        Mockito.verify(apiServer, Mockito.times(0)).verifyUser(Mockito.anyLong());
    }

    @Test
    public void testGetAPIType() {
        Assert.assertTrue(new SAML2LoginAPIAuthenticatorCmd().getAPIType() == APIAuthenticationType.LOGIN_API);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlBlank() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", " ", false);
        boolean expectServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(true, expectServerApiException, 0, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlNull() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", null, false);
        boolean expectServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(true, expectServerApiException, 0, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlEmpty() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", "", false);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(true, hasThrownServerApiException, 0, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectExternalEntityNullAndUrlNotConfigured() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(null, " ", false);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(true, hasThrownServerApiException, 0, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectExternalEntityNullAndUrlConfigured() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(null, "some.url", true);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(false, hasThrownServerApiException, 1, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlConfigured() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", "some.url", false);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(false, hasThrownServerApiException, 1, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlUserAccountNull() throws IOException {
        configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", "some.url", true);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(null);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(false, hasThrownServerApiException, 1, 1);
    }

    @Test
    public void whenFailToAuthenticateThrowExceptionOrRedirectToUrlTestSaml2FailedLoginRedirectUrlIsUserAuthorized() throws IOException {
        UserAccountVO userAccount = configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl("entity", "some.url", true);
        boolean hasThrownServerApiException = runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(userAccount);
        verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(false, hasThrownServerApiException, 0, 0);
    }

    private void overrideDefaultConfigValue(final ConfigKey configKey, final String name, final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field f = ConfigKey.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(configKey, o);
    }

    @Test
    public void testFailOnSAMLSignatureCheckWhenFalse() throws NoSuchFieldException, IllegalAccessException {
        overrideDefaultConfigValue(SAML2AuthManager.SAMLCheckSignature, "_value", false);
        SAML2LoginAPIAuthenticatorCmd cmd = new SAML2LoginAPIAuthenticatorCmd();
        try {
            cmd.checkAndFailOnMissingSAMLSignature(null);
        } catch(Exception e) {
            Assert.fail("This shouldn't throw any exception");
        }
    }

    @Test(expected = ServerApiException.class)
    public void testFailOnSAMLSignatureCheckWhenTrue() throws NoSuchFieldException, IllegalAccessException {
        overrideDefaultConfigValue(SAML2AuthManager.SAMLCheckSignature, "_value", true);
        SAML2LoginAPIAuthenticatorCmd cmd = new SAML2LoginAPIAuthenticatorCmd();
        cmd.checkAndFailOnMissingSAMLSignature(null);
    }

    private UserAccountVO configureTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(String entity, String configurationValue, Boolean isUserAuthorized)
            throws IOException {
        Mockito.when(samlAuthManager.isUserAuthorized(nullable(Long.class), nullable(String.class))).thenReturn(isUserAuthorized);
        SAML2LoginAPIAuthenticatorCmd.saml2FailedLoginRedirectUrl = new ConfigKey<String>("Advanced", String.class, "saml2.failed.login.redirect.url", configurationValue,
                "The URL to redirect the SAML2 login failed message (the default vaulue is empty).", true);
        UserAccountVO userAccount = new UserAccountVO();
        userAccount.setExternalEntity(entity);
        userAccount.setId(0l);
        return userAccount;
    }

    private void verifyTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(boolean expectServerApiException, boolean hasThrownServerApiException, int timesOfSendRedirect,
            int timesOfConfigDao) throws IOException {
        Mockito.verify(resp, Mockito.times(timesOfSendRedirect)).sendRedirect(Mockito.anyString());
        Assert.assertEquals(expectServerApiException, hasThrownServerApiException);
    }

    private boolean runTestWhenFailToAuthenticateThrowExceptionOrRedirectToUrl(UserAccountVO userAccount) throws IOException {
        try {
            cmdSpy.whenFailToAuthenticateThrowExceptionOrRedirectToUrl(null, "responseType", resp, new IssuerBuilder().buildObject(), userAccount);
        } catch (ServerApiException e) {
            return true;
        }
        return false;
    }

}
