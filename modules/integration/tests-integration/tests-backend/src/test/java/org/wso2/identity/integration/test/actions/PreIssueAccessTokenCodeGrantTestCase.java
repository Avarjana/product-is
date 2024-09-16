/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.actions;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.mocks.MockServer;
import org.wso2.identity.integration.test.oauth2.dataprovider.model.ApplicationConfig;
import org.wso2.identity.integration.test.oauth2.dataprovider.model.TokenScopes;
import org.wso2.identity.integration.test.oauth2.dataprovider.model.UserClaimConfig;
import org.wso2.identity.integration.test.rest.api.server.action.management.v1.model.ActionModel;
import org.wso2.identity.integration.test.rest.api.server.action.management.v1.model.AuthenticationType;
import org.wso2.identity.integration.test.rest.api.server.action.management.v1.model.Endpoint;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.ApplicationResponseModel;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.OpenIDConnectConfiguration;
import org.wso2.identity.integration.test.rest.api.server.roles.v2.model.Audience;
import org.wso2.identity.integration.test.rest.api.server.roles.v2.model.Permission;
import org.wso2.identity.integration.test.rest.api.server.roles.v2.model.RoleV2;
import org.wso2.identity.integration.test.rest.api.user.common.model.Email;
import org.wso2.identity.integration.test.rest.api.user.common.model.ListObject;
import org.wso2.identity.integration.test.rest.api.user.common.model.Name;
import org.wso2.identity.integration.test.rest.api.user.common.model.PatchOperationRequestObject;
import org.wso2.identity.integration.test.rest.api.user.common.model.RoleItemAddGroupobj;
import org.wso2.identity.integration.test.rest.api.user.common.model.UserObject;
import org.wso2.identity.integration.test.restclients.SCIM2RestClient;
import org.wso2.identity.integration.test.utils.CarbonUtils;
import org.wso2.identity.integration.test.utils.DataExtractUtil;
import org.wso2.identity.integration.test.utils.OAuth2Constant;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.wso2.identity.integration.test.utils.OAuth2Constant.ACCESS_TOKEN_ENDPOINT;
import static org.wso2.identity.integration.test.utils.OAuth2Constant.AUTHORIZATION_HEADER;
import static org.wso2.identity.integration.test.utils.OAuth2Constant.AUTHORIZE_ENDPOINT_URL;
import static org.wso2.identity.integration.test.utils.OAuth2Constant.OAUTH2_GRANT_TYPE_AUTHORIZATION_CODE;

/**
 * Integration test class for testing the pre issue access token flow with  authorization code grant.
 * This test case extends {@link ActionsBaseTestCase} and focuses on scenarios related
 * to scopes and claims modifications through an external service.
 */
public class PreIssueAccessTokenCodeGrantTestCase extends ActionsBaseTestCase {

    private static final String USERS = "users";
    private static final String USERNAME_PROPERTY = "username";
    private static final String PASSWORD_PROPERTY = "password";
    private static final String TEST_USER = "test_user";
    private static final String ADMIN_WSO2 = "Admin@wso2";
    private static final String TEST_USER_FIRST_NAME = "test_user_given_name";
    private static final String REPLACED_FIRST_NAME = "replaced_given_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_GMAIL_COM = "test.user@gmail.com";
    private static final String EXTERNAL_SERVICE_NAME = "TestExternalService";
    private static final String EXTERNAL_SERVICE_URI = "http://localhost:8587/test/action";
    private static final String PRE_ISSUE_ACCESS_TOKEN_API_PATH = "preIssueAccessToken";
    private static final String APPLICATION_AUDIENCE = "APPLICATION";
    private static final String TEST_ROLE_APPLICATION = "test_role_application";
    private static final String INTERNAL_ORG_USER_MANAGEMENT_LIST = "internal_org_user_mgt_list";
    private static final String INTERNAL_ORG_USER_MANAGEMENT_VIEW = "internal_org_user_mgt_view";
    private static final String INTERNAL_ORG_USER_MANAGEMENT_CREATE = "internal_org_user_mgt_create";
    private static final String INTERNAL_ORG_USER_MANAGEMENT_UPDATE = "internal_org_user_mgt_update";
    private static final String INTERNAL_ORG_USER_MANAGEMENT_DELETE = "internal_org_user_mgt_delete";
    private static final String CUSTOM_SCOPE_1 = "test_custom_scope_1";
    private static final String CUSTOM_SCOPE_2 = "test_custom_scope_2";
    private static final String CUSTOM_SCOPE_3 = "test_custom_scope_3";
    private static final String NEW_SCOPE_1 = "new_test_custom_scope_1";
    private static final String NEW_SCOPE_2 = "new_test_custom_scope_2";
    private static final String NEW_SCOPE_3 = "new_test_custom_scope_3";
    private static final String NEW_SCOPE_4 = "replaced_scope";
    private static final String CUSTOM_OIDC_SCOPE = "test_custom_oidc_scope";
    private static final String SCIM2_USERS_API = "/o/scim2/Users";
    private static final String ACTIONS_API = "/api/server/v1/actions";
    private static final String APPLICATION_MANAGEMENT_API = "/api/server/v1/applications";
    private static final String API_RESOURCE_MANAGEMENT_API = "/api/server/v1/api-resources";
    private static final String MOCK_SERVER_ENDPOINT = "/test/action";

    private CloseableHttpClient client;
    private SCIM2RestClient scim2RestClient;
    private List<String> permissions = new ArrayList<>();
    private TokenScopes appTokenScopes;
    private String sessionDataKey;
    private String authorizationCode;
    private String accessToken;
    private String clientId;
    private String clientSecret;
    private String actionId;
    private String applicationId;
    private String domainAPIId;
    private String userId;
    private String roleId;
    private JWTClaimsSet jwtClaims;

    /**
     * Initializes Test environment and sets up necessary configurations.
     *
     * @throws Exception If an error occurs during initialization
     */
    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {

        super.init(TestUserMode.TENANT_USER);

        Lookup<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create()
                .register(CookieSpecs.DEFAULT, new RFC6265CookieSpecProvider())
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.DEFAULT)
                .build();
        client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieSpecRegistry(cookieSpecRegistry)
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    protected boolean isRedirectable(String method) {

                        return false;
                    }
                }).build();

        scim2RestClient = new SCIM2RestClient(serverURL, tenantInfo);

        List<String> customScopes = Arrays.asList(CUSTOM_SCOPE_1, CUSTOM_SCOPE_2, CUSTOM_SCOPE_3);

        applicationId = createOIDCAppWithClaims();

        if (!CarbonUtils.isLegacyAuthzRuntimeEnabled()) {
            authorizeSystemAPIs(applicationId, new ArrayList<>(Arrays.asList(SCIM2_USERS_API, ACTIONS_API,
                    APPLICATION_MANAGEMENT_API, API_RESOURCE_MANAGEMENT_API)));
        }
        domainAPIId = createDomainAPI(EXTERNAL_SERVICE_NAME, EXTERNAL_SERVICE_URI, customScopes);
        authorizeDomainAPIs(applicationId, domainAPIId, customScopes);

        addUserWithRole(applicationId, customScopes);

        MockServer.createMockServer(MOCK_SERVER_ENDPOINT);
        actionId = createPreIssueAccessTokenAction();
    }

    @AfterClass(alwaysRun = true)
    public void atEnd() throws Exception {

        deleteAction(PRE_ISSUE_ACCESS_TOKEN_API_PATH, actionId);
        deleteRole(roleId);
        deleteApp(applicationId);
        deleteDomainAPI(domainAPIId);
        scim2RestClient.deleteUser(userId);
        MockServer.shutDownMockServer();
        restClient.closeHttpClient();
        scim2RestClient.closeHttpClient();
        actionsRestClient.closeHttpClient();
        client.close();
        authorizationCode = null;
        accessToken = null;
        jwtClaims = null;
    }

    @Test(groups = "wso2.is", description = "Initiate authorize request")
    public void testSendAuthorizeRequest() throws Exception {

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("response_type", OAuth2Constant.OAUTH2_GRANT_TYPE_CODE));
        urlParameters.add(new BasicNameValuePair("client_id", clientId));
        urlParameters.add(new BasicNameValuePair("redirect_uri", OAuth2Constant.CALLBACK_URL));

        permissions.addAll(appTokenScopes.getRequestedScopes());
        urlParameters.add(new BasicNameValuePair("scope", String.join(" ", permissions)));

        HttpResponse response = sendPostRequestWithParameters(client, urlParameters,
                getTenantQualifiedURL(AUTHORIZE_ENDPOINT_URL, tenantInfo.getDomain()));

        Header locationHeader = response.getFirstHeader(OAuth2Constant.HTTP_RESPONSE_HEADER_LOCATION);
        assertNotNull(locationHeader, "Location header expected for authorize request is not available");
        EntityUtils.consume(response.getEntity());

        response = sendGetRequest(client, locationHeader.getValue());

        Map<String, Integer> keyPositionMap = new HashMap<>(1);
        keyPositionMap.put("name=\"sessionDataKey\"", 1);
        List<DataExtractUtil.KeyValue> keyValues = DataExtractUtil.extractDataFromResponse(response, keyPositionMap);
        assertNotNull(keyValues, "SessionDataKey key value is null");

        sessionDataKey = keyValues.get(0).getValue();
        assertNotNull(sessionDataKey, "Session data key is null");
        EntityUtils.consume(response.getEntity());
    }

    @Test(groups = "wso2.is", description = "Perform login", dependsOnMethods = "testSendAuthorizeRequest")
    public void testSendLoginPost() throws Exception {

        HttpResponse response = sendLoginPostForCustomUsers(client, sessionDataKey, TEST_USER, ADMIN_WSO2);

        Header locationHeader = response.getFirstHeader(OAuth2Constant.HTTP_RESPONSE_HEADER_LOCATION);
        assertNotNull(locationHeader, "Location header expected post login is not available.");
        EntityUtils.consume(response.getEntity());

        response = sendGetRequest(client, locationHeader.getValue());
        locationHeader = response.getFirstHeader(OAuth2Constant.HTTP_RESPONSE_HEADER_LOCATION);
        assertNotNull(locationHeader, "Redirection URL to the application with authorization code is null.");
        EntityUtils.consume(response.getEntity());

        authorizationCode = getAuthorizationCodeFromURL(locationHeader.getValue());
        assertNotNull(authorizationCode);
    }

    @Test(groups = "wso2.is", description = "Retrieve access token", dependsOnMethods = "testSendLoginPost")
    public void testGetAccessTokenWithAuthCodeGrant() throws Exception {

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("code", authorizationCode));
        urlParameters.add(new BasicNameValuePair("grant_type", OAUTH2_GRANT_TYPE_AUTHORIZATION_CODE));
        urlParameters.add(new BasicNameValuePair("redirect_uri", OAuth2Constant.CALLBACK_URL));
        urlParameters.add(new BasicNameValuePair("client_id", clientId));

        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(AUTHORIZATION_HEADER,
                OAuth2Constant.BASIC_HEADER + " " + getBase64EncodedString(clientId, clientSecret)));
        headers.add(new BasicHeader("Content-Type", "application/x-www-form-urlencoded"));
        headers.add(new BasicHeader("User-Agent", OAuth2Constant.USER_AGENT));

        HttpResponse response = sendPostRequest(client, headers, urlParameters,
                getTenantQualifiedURL(ACCESS_TOKEN_ENDPOINT, tenantInfo.getDomain()));
        assertNotNull(response, "Failed to receive a response for access token request.");

        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        JSONObject jsonResponse = new JSONObject(responseString);

        assertTrue(jsonResponse.has("access_token"), "Access token not found in the token response.");
        accessToken = jsonResponse.getString("access_token");
        assertNotNull(accessToken, "Access token is null.");

        jwtClaims = extractJwtClaims(accessToken);
        assertNotNull(jwtClaims);
    }

    @Test(groups = "wso2.is", description = "Verify that the access token contains the updated scopes " +
            "after action execution", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenScopeOperations() throws Exception {

        String[] scopes = jwtClaims.getStringClaim("scope").split("\\s+");

        Assert.assertTrue(ArrayUtils.contains(scopes, NEW_SCOPE_1));
        Assert.assertTrue(ArrayUtils.contains(scopes, NEW_SCOPE_2));
        Assert.assertTrue(ArrayUtils.contains(scopes, NEW_SCOPE_3));
        Assert.assertTrue(ArrayUtils.contains(scopes, NEW_SCOPE_4));
        Assert.assertFalse(ArrayUtils.contains(scopes, CUSTOM_SCOPE_3)); // replace - 7
        Assert.assertFalse(ArrayUtils.contains(scopes, INTERNAL_ORG_USER_MANAGEMENT_CREATE)); // remove - 8
    }

    @Test(groups = "wso2.is", description = "Verify that the access token contains the updated OIDC claims " +
            "after action execution", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenOIDCScopeOperations() throws Exception {

        String firstName = jwtClaims.getStringClaim("given_name");
        String lastName = jwtClaims.getStringClaim("family_name");
        Assert.assertEquals(firstName, REPLACED_FIRST_NAME);
        Assert.assertNotEquals(firstName, TEST_USER_FIRST_NAME);
        Assert.assertNotEquals(lastName, TEST_USER_LAST_NAME);
    }

    @Test(groups = "wso2.is", description = "Verify that the access token contains the updated 'aud' claims " +
            "after action execution", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenAUDClaimOperations() throws Exception {

        String[] audValueArray = jwtClaims.getStringArrayClaim("aud");

        Assert.assertTrue(ArrayUtils.contains(audValueArray, "zzz1.com"));
        Assert.assertTrue(ArrayUtils.contains(audValueArray, "zzz2.com"));
        Assert.assertTrue(ArrayUtils.contains(audValueArray, "zzz3.com"));
        Assert.assertTrue(ArrayUtils.contains(audValueArray, "zzzR.com"));
        Assert.assertFalse(ArrayUtils.contains(audValueArray, clientId));
    }

    @Test(groups = "wso2.is", description = "Verify the presence of the specified custom string claim in the access " +
            "token", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenStringClaimAddOperation() throws Exception {

        String claimStr = jwtClaims.getStringClaim("custom_claim_string_1");
        Assert.assertEquals(claimStr, "testCustomClaim1");
    }

    @Test(groups = "wso2.is", description = "Verify the presence of the specified custom number claim in the access " +
            "token", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenNumberClaimAddOperation() throws Exception {

        Number claimValue = jwtClaims.getIntegerClaim("custom_claim_number_1");
        Assert.assertEquals(claimValue, 78);
    }

    @Test(groups = "wso2.is", description = "Verify the presence of the specified custom boolean claim in the access " +
            "token", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenBooleanClaimAddOperation() throws Exception {

        Boolean claimValue = jwtClaims.getBooleanClaim("custom_claim_boolean_1");
        Assert.assertTrue(claimValue);
    }

    @Test(groups = "wso2.is", description = "Verify the presence of the specified custom string array claim in the " +
            "access token", dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenStringArrayClaimAddOperation()
            throws Exception {

        String[] expectedClaimArrayInToken = {"TestCustomClaim1", "TestCustomClaim2", "TestCustomClaim3"};

        String[] addedClaimArrayToToken = jwtClaims.getStringArrayClaim("custom_claim_string_array_1");
        Assert.assertEquals(addedClaimArrayToToken, expectedClaimArrayInToken);
    }

    @Test(groups = "wso2.is", description = "Verify the replacement of the 'expires_in' claim in the access token",
            dependsOnMethods = "testGetAccessTokenWithAuthCodeGrant")
    public void testTokenExpiresInClaimReplaceOperation() throws Exception {

        Date exp = jwtClaims.getDateClaim("exp");
        Date iat = jwtClaims.getDateClaim("iat");
        long expiresIn = (exp.getTime() - iat.getTime()) / 1000;

        Assert.assertEquals(expiresIn, 7200);
    }

    /**
     * Get authorization code from the provided URL.
     *
     * @param location Location header
     * @return Authorization code
     */
    private String getAuthorizationCodeFromURL(String location) {

        URI uri = URI.create(location);
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8).stream()
                .filter(param -> "code".equals(param.getName()))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates an action for pre-issuing an access token with basic authentication.
     *
     * @return ID of the created action
     * @throws IOException If an error occurred while creating the action
     */
    private String createPreIssueAccessTokenAction() throws IOException {

        AuthenticationType authenticationType = new AuthenticationType();
        authenticationType.setType(AuthenticationType.TypeEnum.BASIC);
        Map<String, Object> authProperties = new HashMap<>();
        authProperties.put(USERNAME_PROPERTY, TEST_USER);
        authProperties.put(PASSWORD_PROPERTY, ADMIN_WSO2);
        authenticationType.setProperties(authProperties);

        Endpoint endpoint = new Endpoint();
        endpoint.setUri(EXTERNAL_SERVICE_URI);
        endpoint.setAuthentication(authenticationType);

        ActionModel actionModel = new ActionModel();
        actionModel.setName("Access Token Pre Issue");
        actionModel.setDescription("This is a test pre issue access token type");
        actionModel.setEndpoint(endpoint);

        return createAction(PRE_ISSUE_ACCESS_TOKEN_API_PATH, actionModel);
    }

    /**
     * Extracts the JWT claims set from a given JWT token.
     *
     * @param jwtToken JWT token from which claims are to be extracted
     * @return JWTClaimsSet extracted from the provided JWT token
     * @throws ParseException If there is an error in parsing the JWT token
     */
    private JWTClaimsSet extractJwtClaims(String jwtToken) throws ParseException {

        SignedJWT signedJWT = SignedJWT.parse(jwtToken);
        return signedJWT.getJWTClaimsSet();
    }

    /**
     * Adds a user with a role and specific permissions based on custom scopes.
     *
     * @param appID        Application ID to which the role is associated
     * @param customScopes The custom scopes based on which permissions are added
     * @throws JSONException If there is an error in processing JSON
     * @throws IOException   If there is an IO exception during user or role creation
     */
    private void addUserWithRole(String appID, List<String> customScopes) throws Exception {
        // Creates roles
        List<Permission> userPermissions = addPermissions(customScopes);
        Audience roleAudience = new Audience(APPLICATION_AUDIENCE, appID);
        RoleV2 role = new RoleV2(roleAudience, TEST_ROLE_APPLICATION, userPermissions, Collections.emptyList());
        roleId = addRole(role);

        // Creates user
        UserObject userInfo = new UserObject();
        userInfo.setUserName(TEST_USER);
        userInfo.setPassword(ADMIN_WSO2);
        userInfo.setName(new Name().givenName(TEST_USER_FIRST_NAME));
        userInfo.getName().setFamilyName(TEST_USER_LAST_NAME);
        userInfo.addEmail(new Email().value(TEST_USER_GMAIL_COM));
        userId = scim2RestClient.createUser(userInfo);

        // Assigns role to the created user
        RoleItemAddGroupobj rolePatchReqObject = new RoleItemAddGroupobj();
        rolePatchReqObject.setOp(RoleItemAddGroupobj.OpEnum.ADD);
        rolePatchReqObject.setPath(USERS);
        rolePatchReqObject.addValue(new ListObject().value(userId));
        scim2RestClient.updateUserRole(new PatchOperationRequestObject().addOperations(rolePatchReqObject), roleId);
    }

    /**
     * Adds permissions based on the provided custom scopes.
     *
     * @param customScopes A list of custom scopes to add as permissions
     * @return A list of permissions including both predefined and custom scope-based permissions
     */
    private List<Permission> addPermissions(List<String> customScopes) {

        List<Permission> userPermissions = new ArrayList<>();
        Collections.addAll(permissions,
                INTERNAL_ORG_USER_MANAGEMENT_CREATE,
                INTERNAL_ORG_USER_MANAGEMENT_LIST,
                INTERNAL_ORG_USER_MANAGEMENT_VIEW,
                INTERNAL_ORG_USER_MANAGEMENT_UPDATE,
                INTERNAL_ORG_USER_MANAGEMENT_DELETE
                          );
        permissions.addAll(customScopes);
        permissions.forEach(permission -> userPermissions.add(new Permission(permission)));

        return userPermissions;
    }

    private String createOIDCAppWithClaims() throws Exception {

        List<UserClaimConfig> userClaimConfigs = Arrays.asList(
                new UserClaimConfig.Builder().localClaimUri("http://wso2.org/claims/givenname").
                        oidcClaimUri("given_name").build(),
                new UserClaimConfig.Builder().localClaimUri("http://wso2.org/claims/lastname").
                        oidcClaimUri("family_name").build()
                                                              );

        ApplicationConfig applicationConfig = new ApplicationConfig.Builder()
                .claimsList(userClaimConfigs)
                .grantTypes(new ArrayList<>(Collections.singleton(OAUTH2_GRANT_TYPE_AUTHORIZATION_CODE)))
                .tokenType(ApplicationConfig.TokenType.JWT)
                .expiryTime(3600)
                .skipConsent(true)
                .build();

        appTokenScopes = new TokenScopes.Builder().requestedScopes(Arrays.asList("openid", "profile",
                        CUSTOM_OIDC_SCOPE))
                .grantedScopes(Arrays.asList("openid", "profile")).build();

        ApplicationResponseModel application = addApplication(applicationConfig);
        String applicationId = application.getId();

        OpenIDConnectConfiguration oidcConfig = getOIDCInboundDetailsOfApplication(applicationId);
        clientId = oidcConfig.getClientId();
        clientSecret = oidcConfig.getClientSecret();

        return applicationId;
    }
}

