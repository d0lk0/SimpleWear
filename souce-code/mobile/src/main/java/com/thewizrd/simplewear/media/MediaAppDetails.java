/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thewizrd.simplewear.media;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.util.TypedValue;

import androidx.media.MediaBrowserServiceCompat;

import com.thewizrd.shared_resources.utils.ImageUtils;

import java.util.List;
import java.util.Objects;

/**
 * Stores details about a media app.
 */
public class MediaAppDetails implements Parcelable {
    public final String packageName;
    public final String appName;
    public final Bitmap icon;
    public final MediaSessionCompat.Token sessionToken;
    public final ComponentName componentName;
    public boolean supportsAutomotive = false;
    public boolean supportsAuto = false;

    public final ComponentName searchableActivityComponentName;

    public MediaAppDetails(PackageItemInfo info, PackageManager pm, Resources resources,
                           MediaSession.Token token) {
        packageName = info.packageName;
        appName = info.loadLabel(pm).toString();

        Drawable appIcon = info.loadIcon(pm);
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.getDisplayMetrics());
        icon = ImageUtils.bitmapFromDrawable(appIcon, size, size);

        if (token != null) {
            // If we have a MediaSession Token, then we don't need to connect to the
            // MediaBrowserService implementation, so componentName is null.
            componentName = null;
            sessionToken = MediaSessionCompat.Token.fromToken(token);
        } else {
            // If we don't have a MediaSession Token, then we need to connect to the
            // MediaBrowserService implementation.
            componentName = new ComponentName(info.packageName, info.name);
            sessionToken = null;
        }

        searchableActivityComponentName = findPlayFromSearchActivity(pm, packageName);

        try {
            FeatureInfo[] features = pm.getPackageInfo(
                    packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures;

            supportsAutomotive = false;
            if (features != null) {
                for (FeatureInfo f : features) {
                    if (f.name != null && f.name.equals("android.hardware.type.automotive")) {
                        supportsAutomotive = true;
                        break;
                    }
                }
            }

            Bundle metaData = pm.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA).metaData;

            if (metaData != null) {
                if (metaData.containsKey("com.google.android.gms.car.application")) {
                    supportsAuto = true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("MediaAppDetails", "package name not found" + packageName);
        }
    }

    public MediaAppDetails(PackageItemInfo info, PackageManager pm, Resources resources) {
        this(info, pm, resources, null);
    }

    private ComponentName findPlayFromSearchActivity(PackageManager pm, String packageName) {
        ComponentName searchableComponent = null;
        List<ResolveInfo> searchableActivities = findResolveInfo(packageName, pm, MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo sInfo : searchableActivities) {
            if (Objects.equals(sInfo.activityInfo.packageName, packageName)) {
                searchableComponent = new ComponentName(sInfo.activityInfo.packageName, sInfo.activityInfo.name);
                break;
            }
        }

        return searchableComponent;
    }

    /**
     * Helper function to get the service info for the packagemanager for a given package.
     */
    public static ServiceInfo findServiceInfo(String packageName, PackageManager pm) {
        final Intent mediaBrowserIntent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        final List<ResolveInfo> services =
                pm.queryIntentServices(mediaBrowserIntent,
                        PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info : services) {
            if (info.serviceInfo.packageName.equals(packageName)) {
                return (info.serviceInfo);
            }
        }
        return null;
    }

    public static List<ResolveInfo> findResolveInfo(
            String packageName, PackageManager pm, String action) {
        return findResolveInfo(packageName, pm, action, 0);
    }

    public static List<ResolveInfo> findResolveInfo(
            String packageName, PackageManager pm, String action, int flags) {
        if (packageName != null) {
            Intent prefsIntent = new Intent(action);
            prefsIntent.setPackage(packageName);

            return pm.queryIntentActivities(prefsIntent, 0);
        }
        return null;
    }

    private MediaAppDetails(final Parcel parcel) {
        packageName = parcel.readString();
        appName = parcel.readString();
        icon = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        sessionToken = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        componentName = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        supportsAuto = parcel.readInt() == 1;
        supportsAutomotive = parcel.readInt() == 1;
        searchableActivityComponentName = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(appName);
        dest.writeParcelable(icon, flags);
        dest.writeParcelable(sessionToken, flags);
        dest.writeParcelable(componentName, flags);
        dest.writeInt(supportsAuto ? 1 : 0);
        dest.writeInt(supportsAutomotive ? 1 : 0);
        dest.writeParcelable(searchableActivityComponentName, flags);
    }

    public static final Parcelable.Creator<MediaAppDetails> CREATOR =
            new Parcelable.Creator<MediaAppDetails>() {

                public MediaAppDetails createFromParcel(Parcel source) {
                    return new MediaAppDetails(source);
                }

                public MediaAppDetails[] newArray(int size) {
                    return new MediaAppDetails[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaAppDetails that = (MediaAppDetails) o;

        if (!packageName.equals(that.packageName)) return false;
        return appName.equals(that.appName);
    }

    @Override
    public int hashCode() {
        int result = packageName.hashCode();
        result = 31 * result + appName.hashCode();
        return result;
    }
}
