package com.rudderstack.android.consentfilter.onetrustconsentfilter;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK;
import com.rudderstack.android.sdk.core.RudderServerDestination;
import com.rudderstack.android.sdk.core.consent.RudderConsentFilter;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class RudderOneTrustConsentFilter implements RudderConsentFilter {

    private static final String ONE_TRUST_COOKIE_CATEGORIES_JSON_KEY = "oneTrustCookieCategories";
    private static final String ONE_TRUST_COOKIE_INDIVIDUAL_CATEGORY_JSON_KEY = "oneTrustCookieCategory";
    private static final String ONE_TRUST_CATEGORY_NAME_JSON_KEY = "GroupNameMobile";
    private static final String ONE_TRUST_CATEGORY_ID_JSON_KEY = "CustomGroupId";
    private static final int ONE_TRUST_CONSENT_GIVEN_CONSTANT = 1;
    private static final int ONE_TRUST_CONSENT_NOT_GIVEN_CONSTANT = 0;
    private static final int ONE_TRUST_CONSENT_UNKNOWN_CONSTANT = -1;

    private Map<String, String> oneTrustCategoryIdNameMapping;

    private @NonNull
    OneTrustConsentChecker oneTrustConsentChecker;


    public RudderOneTrustConsentFilter(@NonNull OTPublishersHeadlessSDK oneTrustSdk) {
        updateOneTrustCategoryNameToIdMapping(oneTrustSdk);
        oneTrustConsentChecker = oneTrustSdk::getConsentStatusForGroupId;
    }


    public void setOneTrustConsentChecker(@NonNull OneTrustConsentChecker oneTrustConsentChecker) {
        this.oneTrustConsentChecker = oneTrustConsentChecker;
    }

    private void updateOneTrustCategoryNameToIdMapping(@NonNull OTPublishersHeadlessSDK oneTrustSdk) {
        JSONObject oneTrustSdkDomainGroupData = oneTrustSdk.getDomainGroupData();
        JSONArray categoryGroupArray = parseGroupArrayFromDomainGroupJSONObject(oneTrustSdkDomainGroupData);
        if (categoryGroupArray == null) {
            oneTrustCategoryIdNameMapping = Collections.emptyMap();
            return;
        }
        oneTrustCategoryIdNameMapping = generateOneTrustCategoryNameToIdMappingFromCategoryJsonArray(categoryGroupArray);
    }

    private @NonNull
    Map<String, String> generateOneTrustCategoryNameToIdMappingFromCategoryJsonArray(JSONArray categoryGroupArray) {
        int jsonArraySize = categoryGroupArray.length();
        Map<String, String> categoryNameIdMap = new HashMap<>();
        for (int i = 0; i < jsonArraySize; i++) {
            JSONObject categoryJSONObject = getJSONObjectFromArrayForPosition(categoryGroupArray, i);
            Pair<String, String> categoryNameIdPair = getNameIdPairForCategoryGroupJson(categoryJSONObject);
            if (categoryNameIdPair != null) {
                categoryNameIdMap.put(categoryNameIdPair.first, categoryNameIdPair.second);
            }
        }
        return categoryNameIdMap;
    }

    private @Nullable
    Pair<String, String> getNameIdPairForCategoryGroupJson(JSONObject categoryJSONObject) {
        String categoryName = getStringFromJsonObject(categoryJSONObject, ONE_TRUST_CATEGORY_NAME_JSON_KEY);
        if (categoryName == null)
            return null;
        String categoryId = getStringFromJsonObject(categoryJSONObject, ONE_TRUST_CATEGORY_ID_JSON_KEY);
        if (categoryId == null)
            return null;
        return new Pair<>(categoryName, categoryId);
    }

    private @Nullable
    String getStringFromJsonObject(JSONObject jsonObject, String key) {
        try {
            Object value = jsonObject.get(key);
            if (value instanceof String)
                return (String) value;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private @Nullable
    JSONObject getJSONObjectFromArrayForPosition(JSONArray jsonArray, int position) {
        try {
            Object item = jsonArray.get(position);
            if (item instanceof JSONObject)
                return (JSONObject) item;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private @Nullable
    JSONArray parseGroupArrayFromDomainGroupJSONObject(JSONObject oneTrustSdkDomainGroupData) {
        try {
            return oneTrustSdkDomainGroupData.getJSONArray("Groups");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public Map<String, Boolean> filterConsentedDestinations(List<RudderServerDestination> destinationList) {
        Map<String, Boolean> destinationToConsentMap = new HashMap<>();
        for (RudderServerDestination destination : destinationList) {
            List<String> categoriesForDestination = getCategoriesNamesForDestination(destination);
            if (categoriesForDestination.isEmpty()) {
                destinationToConsentMap.put(destination.getDestinationDefinition().getDisplayName(), true);
            }
            destinationToConsentMap.put(destination.getDestinationDefinition().getDisplayName(),
                    areAllCategoriesConsented(categoriesForDestination));
        }
        return destinationToConsentMap;
    }

    private List<String> getCategoriesNamesForDestination(RudderServerDestination destination) {
        Object destinationConfig = destination.getDestinationConfig();
        if (destinationConfig == null)
            return Collections.emptyList();
        return getOneTrustCookieCategoriesFromConfigObject(destinationConfig);

    }

    private boolean areAllCategoriesConsented(List<String> categoryNameOrIdList) {
        for (String categoryNameOrId : categoryNameOrIdList) {
            if (!isCategoryNameOrIdConsented(categoryNameOrId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCategoryNameOrIdConsented(String categoryNameOrId) {
        int consentStatus = getConsentStatusForCategoryId(categoryNameOrId); //consider as id
        if (consentStatus == ONE_TRUST_CONSENT_UNKNOWN_CONSTANT) { //consider as name
            consentStatus = getConsentStatusForCategoryName(categoryNameOrId);
        }

        switch (consentStatus) {
            case ONE_TRUST_CONSENT_NOT_GIVEN_CONSTANT:
                return false;
            case ONE_TRUST_CONSENT_GIVEN_CONSTANT:
            default:
                return true;
        }
    }

    private int getConsentStatusForCategoryId(String catId) {
        return oneTrustConsentChecker.getConsentStatusForGroupId(catId);
    }

    private int getConsentStatusForCategoryName(String catName) {
        String categoryId = oneTrustCategoryIdNameMapping.get(catName);
        if (categoryId != null)
            return getConsentStatusForCategoryId(categoryId);
        return ONE_TRUST_CONSENT_UNKNOWN_CONSTANT;
    }


    private @NonNull
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

    private @NonNull
    List<String> collectCookieCategoriesFromCategoriesArray(Object[] oneTrustCookieCategoriesList) {
        return collectCookieCategoriesFromCategoriesList(Arrays.asList(oneTrustCookieCategoriesList));
    }

    private @NonNull
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


    @FunctionalInterface
    public interface OneTrustConsentChecker {
        /**
         * Analogous to {@link OTPublishersHeadlessSDK#getConsentStatusForGroupId}
         *
         * @param customGroupId Category ID (eg. C0001) and the method will return the current consent status (integer value)
         * @return 1 = Consent is given
         * 0 = Consent is not given
         * -1 = Consent has not been collected (The SDK is not initialized OR there are no SDKs associated to this category)
         */
        int getConsentStatusForGroupId(String customGroupId);
    }

}
