package org.wso2.identity.integration.test.oauth2;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.integration.common.admin.client.AuthenticatorClient;
import org.wso2.identity.integration.test.utils.OAuth2Constant;
import org.apache.catalina.startup.Tomcat;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.identity.oauth.stub.dto.OAuthConsumerAppDTO;
import org.wso2.identity.integration.test.utils.DataExtractUtil;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.identity.integration.test.utils.DataExtractUtil.KeyValue;

/**
 * Created by indunil on 5/27/16.
 */
public class OAuth2ServiceIntrospectionTestCase extends OAuth2ServiceAbstractIntegrationTest {
    private AuthenticatorClient logManger;
    private String adminUsername;
    private String adminPassword;
    private String accessToken;
    private String consumerKey;
    private String consumerSecret;

    private DefaultHttpClient client;
    private Tomcat tomcat;

    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {

        super.init(TestUserMode.SUPER_TENANT_USER);
        logManger = new AuthenticatorClient(backendURL);
        adminUsername = userInfo.getUserName();
        adminPassword = userInfo.getPassword();
        logManger.login(isServer.getSuperTenant().getTenantAdmin().getUserName(),
                isServer.getSuperTenant().getTenantAdmin().getPassword(),
                isServer.getInstance().getHosts().get("default"));

        setSystemproperties();
        client = new DefaultHttpClient();
    }

    @AfterClass(alwaysRun = true)
    public void atEnd() throws Exception {
        deleteApplication();
        removeOAuthApplicationData();
        stopTomcat(tomcat);

        logManger = null;
        consumerKey = null;
        accessToken = null;
    }

    @Test(alwaysRun = true, description = "Deploy playground application")
    public void testDeployPlaygroundApp() {
        try {
            tomcat = getTomcat();
            URL resourceUrl =
                    getClass().getResource(File.separator + "samples" + File.separator +
                            "playground2.war");
            startTomcat(tomcat, OAuth2Constant.PLAYGROUND_APP_CONTEXT_ROOT, resourceUrl.getPath());

        } catch (Exception e) {
            Assert.fail("Playground application deployment failed.", e);
        }
    }

    @Test(groups = "wso2.is", description = "Check Oauth2 application registration", dependsOnMethods = "testDeployPlaygroundApp")
    public void testRegisterApplication() throws Exception {

        OAuthConsumerAppDTO appDto = createApplication();
        Assert.assertNotNull(appDto, "Application creation failed.");

        consumerKey = appDto.getOauthConsumerKey();
        Assert.assertNotNull(consumerKey, "Application creation failed.");

        consumerSecret = appDto.getOauthConsumerSecret();
        Assert.assertNotNull(consumerSecret, "Application creation failed.");

    }

    @Test(groups = "wso2.is", description = "Send authorize user request and get access token", dependsOnMethods = "testRegisterApplication")
    public void testGetAccessToken() throws Exception {
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair(
                "grantType",
                OAuth2Constant.OAUTH2_GRANT_TYPE_CLIENT_CREDENTIALS));
        urlParameters.add(new BasicNameValuePair("consumerKey", consumerKey));
        urlParameters.add(new BasicNameValuePair("consumerSecret", consumerSecret));
        urlParameters.add(new BasicNameValuePair("accessEndpoint",
                OAuth2Constant.ACCESS_TOKEN_ENDPOINT));
        urlParameters.add(new BasicNameValuePair("authorize", OAuth2Constant.AUTHORIZE_PARAM));
        HttpResponse response =
                sendPostRequestWithParameters(client, urlParameters,
                        OAuth2Constant.AUTHORIZED_USER_URL);
        Assert.assertNotNull(response, "Authorization request failed. Authorized response is null");
        EntityUtils.consume(response.getEntity());

        response = sendPostRequest(client, OAuth2Constant.AUTHORIZED_URL);

        Map<String, Integer> keyPositionMap = new HashMap<String, Integer>(1);
        keyPositionMap.put("name=\"accessToken\"", 1);

        List<KeyValue> keyValues =
                DataExtractUtil.extractInputValueFromResponse(response,
                        keyPositionMap);
        Assert.assertNotNull(keyValues, "Access token Key value is null.");
        accessToken = keyValues.get(0).getValue();

        EntityUtils.consume(response.getEntity());
        Assert.assertNotNull(accessToken, "Access token is null.");
    }

    @Test(groups = "wso2.is", description = "Test introspection endpoint", dependsOnMethods = "testGetAccessToken")
    public void testIntrospectionEndpoint() throws Exception {
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("accessToken", accessToken));
        urlParameters.add(new BasicNameValuePair("resource_url", "https://localhost:9443/oauth2/introspect"));

        HttpResponse response = sendPostRequestWithParameters(client, urlParameters, OAuth2Constant.ACCESS_RESOURCES_URL);
        Assert.assertNotNull(response, "Error in calling to introspection endpoint. Response is invalid.");
    }

}