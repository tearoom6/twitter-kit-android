/*
 * Copyright (C) 2015 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.twitter.sdk.android.core.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import io.fabric.sdk.android.Fabric;
import io.fabric.sdk.android.services.persistence.PreferenceStore;
import io.fabric.sdk.android.services.persistence.PreferenceStoreImpl;

class AdvertisingInfoProvider {
    private static final String ADVERTISING_INFO_PREFERENCES = "TwitterAdvertisingInfoPreferences";
    private static final String PREFKEY_LIMIT_AD_TRACKING = "limit_ad_tracking_enabled";
    private static final String PREFKEY_ADVERTISING_ID = "advertising_id";
    private final Context context;
    private final PreferenceStore preferenceStore;

    AdvertisingInfoProvider(Context context) {
        this.context = context.getApplicationContext();
        this.preferenceStore = new PreferenceStoreImpl(context, ADVERTISING_INFO_PREFERENCES);
    }

    /**
     * Returns an AdvertisingInfo using various Providers with different attempts to gain this data
     *
     * This method should not be called on the UI thread as it always does some kind of IO
     * (reading from Shared Preferences) and other slow tasks like binding to a service or using
     * reflection.
     */
    AdvertisingInfo getAdvertisingInfo() {
        AdvertisingInfo infoToReturn;

        infoToReturn = getInfoFromPreferences();
        if (isInfoValid(infoToReturn)) {
            Fabric.getLogger().d(Fabric.TAG, "Using AdvertisingInfo from Preference Store");
            refreshInfoIfNeededAsync(infoToReturn);
            return infoToReturn;
        }

        infoToReturn =  getAdvertisingInfoFromStrategies();
        storeInfoToPreferences(infoToReturn);
        return infoToReturn;
    }

    /**
     * Asynchronously updates the advertising info stored in shared preferences (if it is different
     * than the current info) so subsequent calls to {@link #getInfoFromPreferences()} are up to
     * date.
     */
    private void refreshInfoIfNeededAsync(final AdvertisingInfo advertisingInfo) {
        new Thread(new Runnable() {
            public void run() {
                final AdvertisingInfo infoToStore = getAdvertisingInfoFromStrategies();
                if (!advertisingInfo.equals(infoToStore)) {
                    Fabric.getLogger().d(Fabric.TAG, "Asychronously getting Advertising Info and " +
                            "storing it to preferences");
                    storeInfoToPreferences(infoToStore);
                }
            }
        }).start();
    }

    @SuppressLint("CommitPrefEdits")
    private void storeInfoToPreferences(AdvertisingInfo infoToReturn) {
        if (isInfoValid(infoToReturn)) {
            preferenceStore.save(preferenceStore.edit()
                    .putString(PREFKEY_ADVERTISING_ID, infoToReturn.advertisingId)
                    .putBoolean(PREFKEY_LIMIT_AD_TRACKING, infoToReturn.limitAdTrackingEnabled));
        } else {
            // if we get an invalid advertising info, clear out the previous value since it isn't
            // valid now
            preferenceStore.save(preferenceStore.edit()
                    .remove(PREFKEY_ADVERTISING_ID)
                    .remove(PREFKEY_LIMIT_AD_TRACKING));

        }
    }

    private AdvertisingInfo getInfoFromPreferences() {
        final String advertisingId = preferenceStore.get().getString(PREFKEY_ADVERTISING_ID, "");
        final boolean limitAd = preferenceStore.get().getBoolean(PREFKEY_LIMIT_AD_TRACKING, false);
        return new AdvertisingInfo(advertisingId, limitAd);
    }

    private AdvertisingInfoStrategy getReflectionStrategy() {
        return new AdvertisingInfoReflectionStrategy(context);
    }

    private AdvertisingInfoStrategy getServiceStrategy() {
        return new AdvertisingInfoServiceStrategy(context);
    }

    private boolean isInfoValid(AdvertisingInfo advertisingInfo) {
        return advertisingInfo != null && !TextUtils.isEmpty(advertisingInfo.advertisingId);
    }

    private AdvertisingInfo getAdvertisingInfoFromStrategies() {
        AdvertisingInfo infoToReturn;

        AdvertisingInfoStrategy adInfoStrategy = getReflectionStrategy();
        infoToReturn = adInfoStrategy.getAdvertisingInfo();

        if (!isInfoValid(infoToReturn)) {
            adInfoStrategy = getServiceStrategy();
            infoToReturn = adInfoStrategy.getAdvertisingInfo();

            if (!isInfoValid(infoToReturn)) {
                Fabric.getLogger().d(Fabric.TAG, "AdvertisingInfo not present");
            } else {
                Fabric.getLogger().d(Fabric.TAG, "Using AdvertisingInfo from Service Provider");
            }
        } else {
            Fabric.getLogger().d(Fabric.TAG, "Using AdvertisingInfo from Reflection Provider");
        }

        return infoToReturn;
    }
}
