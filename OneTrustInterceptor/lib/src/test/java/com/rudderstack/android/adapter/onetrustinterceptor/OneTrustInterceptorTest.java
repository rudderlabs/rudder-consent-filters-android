package com.rudderstack.android.adapter.onetrustinterceptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;

import com.google.gson.Gson;
import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK;
import com.rudderstack.android.sdk.core.RudderMessage;
import com.rudderstack.android.sdk.core.RudderMessageBuilder;
import com.rudderstack.android.sdk.core.RudderOption;
import com.rudderstack.android.sdk.core.RudderServerConfigSource;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class OneTrustInterceptorTest {
    private OneTrustInterceptor oneTrustInterceptor;

    private final List<String> acceptedCategoryIds = Arrays.asList("cat_id_1", "cat_id_2",
            "cat_id_3", "cat_id_4", "cat_id_5");

    private final List<String> rejectedCategoryIds = Arrays.asList("cat_id_6", "cat_id_7",
            "cat_id_8", "cat_id_9", "cat_id_10");

    //accepted destination ids from server config json
    //1z7xfZC2slTjIjvNMiZKix3p8qp, 2KawPBvasYylQCVK4TIpGxPP9WW,1z7xfZC2slTjIjvNMiZKix3p8qy
    //rejected destination ids
    //2KawPBvasYylQCVK4TIpGxPP9WM, 2Kb0uWtwcvl1mUv1IBc5a8YhuM6, 2KX4GMCpQiCfjVoTwnF1ZsWaJlN,
    //2KX4GMCpQiCfjVoTwnF1ZsWaJlQ

    private RudderServerConfigSource rudderServerConfigSource;
    private final Gson gson = new Gson();
    OTPublishersHeadlessSDK otPublishersHeadlessSDK;


    @Before
    public void initialise() {
        mockOtPublishersSdkConsent();
        oneTrustInterceptor = new OneTrustInterceptor(otPublishersHeadlessSDK);
        rudderServerConfigSource = getRudderServerConfigSource();
    }

    private void mockOtPublishersSdkConsent() {
        otPublishersHeadlessSDK = Mockito.mock(OTPublishersHeadlessSDK.class);
        Mockito.when(otPublishersHeadlessSDK.getConsentStatusForGroupId(anyString()))
                .thenAnswer((Answer<Integer>) invocation -> {
                            Object argument = invocation.getArgument(0);
                            if (argument == null)
                                return -1;
                            if (acceptedCategoryIds.contains(argument))
                                return 1;
                            if (rejectedCategoryIds.contains(argument))
                                return 0;
                            return -1;
                        }
                );
        Mockito.when(otPublishersHeadlessSDK.getDomainGroupData())
                .thenAnswer((Answer<JSONObject>) invocation -> new JSONObject(jsonFromTestResourceFile("OnetrustDomainGroupData.json")));
    }

    private RudderServerConfigSource getRudderServerConfigSource() {
        String serverConfigJson = jsonFromTestResourceFile("ServerConfig.json");
        return gson.fromJson(serverConfigJson, RudderServerConfigSource.class);
    }

    private String jsonFromTestResourceFile(String filename) {
        try {
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
            return getStringFromInputStream(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //    @Throws(IOException::class)
    private String getStringFromInputStream(InputStream stream) throws IOException {
        int n = 0;
        char[] buffer = new char[1024 * 4];
        InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        StringWriter writer = new StringWriter();
        int read = 0;
        while ((read = reader.read(buffer)) != -1) {
            n = read;
            writer.write(buffer, 0, n);

        }
        return writer.toString();
    }

    //accepted destination ids from server config json
    //1z7xfZC2slTjIjvNMiZKix3p8qp, 2KawPBvasYylQCVK4TIpGxPP9WW,1z7xfZC2slTjIjvNMiZKix3p8qy
    //rejected destination ids
    //2KawPBvasYylQCVK4TIpGxPP9WM, 2Kb0uWtwcvl1mUv1IBc5a8YhuM6, 2KX4GMCpQiCfjVoTwnF1ZsWaJlN,
    //2KX4GMCpQiCfjVoTwnF1ZsWaJlQ
    @Test
    public void testInterceptWithTwoAcceptedCategoryIds() {
        RudderOption option = new RudderOption();
        option.putIntegration("All", true); //this will be there by default

        RudderMessage message = new RudderMessageBuilder()
                .setUserId("u-1")
                .setRudderOption(option)
                .build();
        RudderMessage updatedMessage = oneTrustInterceptor.intercept(rudderServerConfigSource, message);
        assertThat(updatedMessage.getUserId(), is("u-1"));
        assertThat(message.getIntegrations(), allOf(
                hasEntry("All", false),
                hasEntry("Amplitude", true),
                hasEntry("Adroll", true),
                hasEntry("Rocksalt", true)
        ));
    }


    @Test
    public void testInterceptorWithTwoAcceptedCategoriesAllTrue(){
        RudderOption option = new RudderOption();
        option.putIntegration("All", true); //this will be there by default
        option.putIntegration("Amplitude", false);
        option.putIntegration("Adroll", false);

        RudderMessage message = new RudderMessageBuilder()
                .setUserId("u-1")
                .setRudderOption(option)
                .build();
        RudderMessage updatedMessage = oneTrustInterceptor.intercept(rudderServerConfigSource, message);
        assertThat(updatedMessage.getUserId(), is("u-1"));
        assertThat(message.getIntegrations(), allOf(
                hasEntry("All", false),
                hasEntry("Amplitude", false),
                hasEntry("Adroll", false),
                hasEntry("Rocksalt", true)
        ));
    }
    @Test
    public void testInterceptorWithTwoAcceptedCategoriesAllFalse(){
        RudderOption option = new RudderOption();
        option.putIntegration("All", false); //this will be there by default
        option.putIntegration("Rocksalt", true);

        RudderMessage message = new RudderMessageBuilder()
                .setUserId("u-1")
                .setRudderOption(option)
                .build();
        RudderMessage updatedMessage = oneTrustInterceptor.intercept(rudderServerConfigSource, message);
        assertThat(updatedMessage.getUserId(), is("u-1"));
        assertThat(message.getIntegrations(), allOf(
                hasEntry("All", false),
                hasEntry("Rocksalt", true)
        ));
    }
    @Test
    public void testInterceptorWithTwoRejectedCategories(){
        RudderOption option = new RudderOption();
        option.putIntegration("All", false); //this will be there by default
        option.putIntegration("CreamRoll", true);
        option.putIntegration("Appcues", true);

        RudderMessage message = new RudderMessageBuilder()
                .setUserId("u-1")
                .setRudderOption(option)
                .build();
        RudderMessage updatedMessage = oneTrustInterceptor.intercept(rudderServerConfigSource, message);
        assertThat(updatedMessage.getUserId(), is("u-1"));
        assertThat(message.getIntegrations(), allOf(
                hasEntry("All", false),
                hasEntry("CreamRoll", false),
                hasEntry("Appcues", false)
        ));
    }

}