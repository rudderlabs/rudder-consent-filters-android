package com.rudderstack.android.adapter.onetrustinterceptor;

import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK;
import com.rudderstack.android.sdk.core.RudderContext;
import com.rudderstack.android.sdk.core.RudderMessage;
import com.rudderstack.android.sdk.core.RudderMessageBuilder;
import com.rudderstack.android.sdk.core.RudderOption;
import com.rudderstack.android.sdk.core.RudderServerConfigSource;
import com.rudderstack.android.sdk.core.RudderServerDestination;
import com.rudderstack.android.sdk.core.consent.ConsentInterceptor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kotlin.Pair;

public final class OneTrustInterceptor implements ConsentInterceptor {

    private static final String ONE_TRUST_COOKIE_CATEGORIES_JSON_KEY = "oneTrustCookieCategories";
    private static final String ONE_TRUST_COOKIE_INDIVIDUAL_CATEGORY_JSON_KEY = "oneTrustCookieCategory";
    private static final String ONE_TRUST_CATEGORY_NAME_JSON_KEY = "GroupNameMobile";
    private static final String ONE_TRUST_CATEGORY_ID_JSON_KEY = "OptanonGroupId";
    private static final int ONE_TRUST_CONSENT_GIVEN_CONSTANT = 1;
    private static final int ONE_TRUST_CONSENT_NOT_GIVEN_CONSTANT = 0;
    private static final int ONE_TRUST_CONSENT_UNKNOWN_CONSTANT = -1;
    private final @NotNull
    OTPublishersHeadlessSDK oneTrustSdk;
    private final Map<String,String> oneTrustCategoryIdNameMapping;

    //    private
    public OneTrustInterceptor(@NotNull OTPublishersHeadlessSDK oneTrustSdk) {
        this.oneTrustSdk = oneTrustSdk;
        oneTrustCategoryIdNameMapping = createOneTrustCategoryNameToIdMapping(oneTrustSdk);
    }

    private Map<String, String> createOneTrustCategoryNameToIdMapping(@NotNull OTPublishersHeadlessSDK oneTrustSdk) {
        JSONObject oneTrustSdkDomainGroupData = oneTrustSdk.getDomainGroupData();
        JSONArray categoryGroupArray = parseGroupArrayFromDomainGroupJSONObject(oneTrustSdkDomainGroupData);
        if(categoryGroupArray == null)
            return Collections.emptyMap();
        return generateOneTrustCategoryNameToIdMappingFromCategoryJsonArray(categoryGroupArray);
    }

    private @NotNull
    Map<String, String> generateOneTrustCategoryNameToIdMappingFromCategoryJsonArray(JSONArray categoryGroupArray) {
        int jsonArraySize = categoryGroupArray.length();
        Map<String, String> categoryNameIdMap = new HashMap<>();
        for (int i = 0; i < jsonArraySize; i++) {
            JSONObject categoryJSONObject = getJSONObjectFromArrayForPosition(categoryGroupArray, i);
            Pair<String,String> categoryNameIdPair = getNameIdPairForCategoryGroupJson(categoryJSONObject);
            if(categoryNameIdPair != null){
                categoryNameIdMap.put(categoryNameIdPair.getFirst(), categoryNameIdPair.getSecond());
            }
        }
        return categoryNameIdMap;
    }

    private @Nullable
    Pair<String, String> getNameIdPairForCategoryGroupJson(JSONObject categoryJSONObject) {
        String categoryName = getStringFromJsonObject(categoryJSONObject, ONE_TRUST_CATEGORY_NAME_JSON_KEY);
        if(categoryName == null)
            return null;
        String categoryId = getStringFromJsonObject(categoryJSONObject, ONE_TRUST_CATEGORY_ID_JSON_KEY);
        if (categoryId == null)
            return null;
        return new Pair<>(categoryName, categoryId);
    }
    private @Nullable
    String getStringFromJsonObject(JSONObject jsonObject, String key){
        try {
            Object value = jsonObject.get(key);
            if(value instanceof String)
                return (String) value;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private @Nullable JSONObject getJSONObjectFromArrayForPosition(JSONArray jsonArray, int position){
        try {
            Object item = jsonArray.get(position);
            if(item instanceof JSONObject)
                return (JSONObject) item;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private @Nullable JSONArray parseGroupArrayFromDomainGroupJSONObject(JSONObject oneTrustSdkDomainGroupData) {
        try {
            return oneTrustSdkDomainGroupData.getJSONArray("Groups");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Intercepts the message before being dumped to the destinations.
     *
     * @param rudderServerConfigSource The {@link com.rudderstack.android.sdk.core.RudderServerConfigSource}
     *                                 represents the source config from Rudderstack Web app
     * @param rudderMessage            The message that is to be intercepted
     * @return The new message that will be used to proceed towards dumping to destinations
     */
    @Override
    public RudderMessage intercept(final RudderServerConfigSource rudderServerConfigSource,
                                   RudderMessage rudderMessage) {
        List<RudderServerDestination> allDestinations = rudderServerConfigSource.getDestinations();
        List<RudderServerDestination> destinations =
                filterDestinationsThroughMessageIntegrations(allDestinations, rudderMessage.getIntegrations());
        if (destinations.isEmpty()) {
            return rudderMessage;
        }
        List<RudderServerDestination> consentedDestinations = filterConsentedDestinations(destinations);

        return buildUpdatedMessageWithFilteredDestinations(rudderMessage, consentedDestinations);

    }

    private @NotNull
    List<RudderServerDestination> filterDestinationsThroughMessageIntegrations(List<RudderServerDestination>
                                                                                               allDestinations, Map<String, Object> integrations) {
        if (allDestinations == null || allDestinations.isEmpty())
            return Collections.emptyList();
        boolean isAllEnabled = areAllIntegrationsEnabled(integrations);
        List<RudderServerDestination> integrationsAllowedDestinations = new ArrayList<>();
        for (RudderServerDestination destination : allDestinations) {
            if (isDestinationAllowedInIntegrations(destination, integrations, isAllEnabled)) {
                integrationsAllowedDestinations.add(destination);
            }
        }
        return integrationsAllowedDestinations;
    }

    private boolean isDestinationAllowedInIntegrations(RudderServerDestination destination, Map<String, Object> integrations, boolean isAllEnabled) {
        String destinationDefinitionName = destination.getDestinationDefinition().getDisplayName();
        Object isDestinationEnabled = integrations.get(destinationDefinitionName);
        if(isDestinationEnabled instanceof Boolean)
            return (boolean) isDestinationEnabled;
        return isAllEnabled;
    }

    private boolean areAllIntegrationsEnabled(Map<String, Object> integrations) {
        if (!integrations.containsKey("All"))
            return true;
        Object isAllEnabled = integrations.get("All");
        if (!(isAllEnabled instanceof Boolean))
            return true;
        return (boolean) isAllEnabled;

    }

    private RudderMessage buildUpdatedMessageWithFilteredDestinations(RudderMessage oldMessage,
                                                                      List<RudderServerDestination> consentedDestinations) {
        RudderOption newOptions = mimicRudderOptionFromMessageWithConsentedDestinations(oldMessage,
                consentedDestinations);
        return RudderMessageBuilder.from(oldMessage)
                .setRudderOption(newOptions)
                .build();
    }

    private RudderOption mimicRudderOptionFromMessageWithConsentedDestinations(RudderMessage oldMessage,
                                                                               List<RudderServerDestination> consentedDestinations) {
        RudderOption newOptions = new RudderOption();
        updateRudderOptionWithMessageCustomContexts(newOptions, oldMessage);
        updateRudderOptionWithMessageExternalIds(newOptions, oldMessage);
        updateRudderOptionWithConsentedMessageIntegrations(newOptions, oldMessage, consentedDestinations);

        return newOptions;
    }

    private void updateRudderOptionWithConsentedMessageIntegrations(RudderOption option, RudderMessage message,
                                                                    List<RudderServerDestination> consentedDestinations) {
        Map<String, Object> newIntegrationsMap = updatedMessageIntegrationMapWithConsentedDestinations(message, consentedDestinations);
        if (newIntegrationsMap.isEmpty())
            return;

        setAllIntegrationsToDefaultValue(newIntegrationsMap, false);
        allowAllConsentedDestinationsInIntegrations(newIntegrationsMap, consentedDestinations);
        updateRudderOptionWithNewIntegrations(option, newIntegrationsMap);
    }

    private Map<String, Object> updatedMessageIntegrationMapWithConsentedDestinations(RudderMessage message,
                                                                                      List<RudderServerDestination> consentedDestinations) {
        Map<String, Object> messageIntegrations = message.getIntegrations();
        if (messageIntegrations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> newIntegrationsMap = new LinkedHashMap<>(messageIntegrations);
        setAllIntegrationsToDefaultValue(newIntegrationsMap, false);
        allowAllConsentedDestinationsInIntegrations(newIntegrationsMap, consentedDestinations);

        return newIntegrationsMap;
    }

    private void updateRudderOptionWithNewIntegrations(RudderOption option, Map<String, Object> newIntegrationsMap) {
        for (Map.Entry<String, Object> newIntegrationEntry : newIntegrationsMap.entrySet()) {
            Object integrationEntryValue = newIntegrationEntry.getValue();
            if (integrationEntryValue instanceof Boolean) {
                option.putIntegration(newIntegrationEntry.getKey(), (boolean) integrationEntryValue);
            }
        }
    }

    private void setAllIntegrationsToDefaultValue(Map<String, Object> integrationsMap, boolean defaultValue) {
        for (String integrationName : integrationsMap.keySet()) {
            integrationsMap.put(integrationName, defaultValue);
        }
    }

    private void allowAllConsentedDestinationsInIntegrations(Map<String, Object> integrationsMap,
                                                             List<RudderServerDestination> consentedDestinations) {
        for (RudderServerDestination consentedDestination : consentedDestinations) {
            integrationsMap.put(consentedDestination.getDestinationDefinition().getDisplayName(), true);
        }
    }


    private void updateRudderOptionWithMessageExternalIds(RudderOption option, RudderMessage message) {
        Map<String, Object> messageExternalIds = getExternalIdsFromMessage(message);
        if (messageExternalIds.isEmpty())
            return;
        updateRudderOptionWithExternalIds(option, messageExternalIds);
    }

    private Map<String, Object> getExternalIdsFromMessage(RudderMessage message) {
        RudderContext context = message.getContext();
        if(context == null)
            return Collections.emptyMap();
        List<Map<String, Object>> externalIdsPairList = context.getExternalIds();
        if (externalIdsPairList == null || externalIdsPairList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> collectedExternalIdsPair = new HashMap<>();
        for (Map<String, Object> externalIdPair : externalIdsPairList) {
            collectedExternalIdsPair.putAll(externalIdPair);
        }
        return collectedExternalIdsPair;
    }

    private void updateRudderOptionWithExternalIds(RudderOption option, Map<String, Object> externalIds) {
        for (Map.Entry<String, Object> externalIdPair : externalIds.entrySet()) {
            Object externalIdValue = externalIdPair.getValue();
            if (externalIdValue instanceof String) {
                option.putExternalId(externalIdPair.getKey(), (String) externalIdPair.getValue());
            }
        }
    }


    private void updateRudderOptionWithMessageCustomContexts(RudderOption option, RudderMessage message) {
        RudderContext rudderContext = message.getContext();
        if(rudderContext == null)
            return;
        Map<String, Object> extractedCustomContexts = rudderContext.customContextMap;
        if (extractedCustomContexts == null || extractedCustomContexts.isEmpty()) {
            return;
        }
        setCustomContextsToOption(option, extractedCustomContexts);
    }

    private void setCustomContextsToOption(RudderOption option, Map<String, Object> customContexts) {
        for (Map.Entry<String, Object> customContextEntry : customContexts.entrySet()) {
            Object customContextValue = customContextEntry.getValue();
            try {
                option.putCustomContext(customContextEntry.getKey(), (Map<String, Object>) customContextValue);
            } catch (Exception e) {
                //ignore
            }
        }
    }


    private List<RudderServerDestination> filterConsentedDestinations(List<RudderServerDestination> destinations) {
        List<RudderServerDestination> filteredDestinations = new ArrayList<>();
        for (RudderServerDestination destination : destinations) {
            List<String> categoriesForDestination = getCategoriesNamesForDestination(destination);
            if (categoriesForDestination.isEmpty()) {
                continue;
            }
            if (areAllCategoriesConsented(categoriesForDestination)) {
                filteredDestinations.add(destination);
            }
        }
        return filteredDestinations;
    }

    private boolean areAllCategoriesConsented(List<String> categoryNameOrIdList) {
        for (String categoryNameOrId: categoryNameOrIdList) {
            if (! isCategoryNameOrIdConsented(categoryNameOrId)){
                return false;
            }
        }
        return true;
    }
    private boolean isCategoryNameOrIdConsented(String categoryNameOrId){
        int consentStatus = getConsentStatusForCategoryId(categoryNameOrId); //consider as id
        if (consentStatus == ONE_TRUST_CONSENT_UNKNOWN_CONSTANT){ //consider as name
            consentStatus = getConsentStatusForCategoryName(categoryNameOrId);
        }

        switch (consentStatus){
            case ONE_TRUST_CONSENT_GIVEN_CONSTANT: return true;
            case ONE_TRUST_CONSENT_NOT_GIVEN_CONSTANT:
            default: return false;
        }
    }
    private int getConsentStatusForCategoryId(String catId){
        return oneTrustSdk.getConsentStatusForGroupId(catId);
    }
    private int getConsentStatusForCategoryName(String catName){
        String categoryId = oneTrustCategoryIdNameMapping.get(catName);
        if (categoryId != null)
            return getConsentStatusForCategoryId(categoryId);
        return ONE_TRUST_CONSENT_UNKNOWN_CONSTANT;
    }

    private List<String> getCategoriesNamesForDestination(RudderServerDestination destination) {
        Object destinationConfig = destination.getDestinationConfig();
        if (destinationConfig == null)
            return Collections.emptyList();
        return getOneTrustCookieCategoriesFromConfigObject(destinationConfig);

    }

    private @NotNull
    List<String> getOneTrustCookieCategoriesFromConfigObject(Object destinationConfig) {
        if (!(destinationConfig instanceof Map)) {
            return Collections.emptyList();
        }
        Object oneTrustCookieCategoriesList = ((Map) destinationConfig).get(ONE_TRUST_COOKIE_CATEGORIES_JSON_KEY);
        if ((oneTrustCookieCategoriesList instanceof Iterable)) {
            return collectCookieCategoriesFromCategoriesList((Iterable) oneTrustCookieCategoriesList);
        } else if (oneTrustCookieCategoriesList != null &&
                oneTrustCookieCategoriesList.getClass().isArray()) {
            return collectCookieCategoriesFromCategoriesArray((Object[]) oneTrustCookieCategoriesList);

        }
        return Collections.emptyList();

    }

    private @NotNull
    List<String> collectCookieCategoriesFromCategoriesArray(Object[] oneTrustCookieCategoriesList) {
        return collectCookieCategoriesFromCategoriesList(Arrays.asList(oneTrustCookieCategoriesList));
    }

    private @NotNull
    List<String> collectCookieCategoriesFromCategoriesList(Iterable oneTrustCookieCategoriesList) {
        final List<String> cookieCategoriesList = new ArrayList<>();
        for (Object oneTrustCategoryMap : oneTrustCookieCategoriesList
        ) {
            updateCookieCategoryListFromCategoryMap(cookieCategoriesList, oneTrustCategoryMap);
        }
        return cookieCategoriesList;
    }

    private void updateCookieCategoryListFromCategoryMap(List<String> cookieCategoriesList, Object oneTrustCategoryMap) {
        if (oneTrustCategoryMap instanceof Map) {
            Object cookieCategory = ((Map) oneTrustCategoryMap).get(ONE_TRUST_COOKIE_INDIVIDUAL_CATEGORY_JSON_KEY);
            if (cookieCategory instanceof String) {
                cookieCategoriesList.add((String) cookieCategory);
            }
        }
    }


}
