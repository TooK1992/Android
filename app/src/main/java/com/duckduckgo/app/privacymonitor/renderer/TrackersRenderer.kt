/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.renderer

import android.content.Context
import android.support.annotation.DrawableRes
import com.duckduckgo.app.browser.R


class TrackersRenderer {

    fun trackersText(context: Context, trackerCount: Int, allTrackersBlocked: Boolean): String {
        val resource = if (allTrackersBlocked) R.plurals.trackerBlocked else R.plurals.trackersFound
        return context.resources.getQuantityString(resource, trackerCount, trackerCount)
    }

    fun networksText(context: Context, networkCount: Int, allTrackersBlocked: Boolean): String {
        val resource = if (allTrackersBlocked) R.plurals.networksBlocked else R.plurals.networksFound
        return context.resources.getQuantityString(resource, networkCount, networkCount)
    }

    fun majorNetworksText(context: Context, networkCount: Int, allTrackersBlocked: Boolean): String {
        val resource = if (allTrackersBlocked) R.plurals.majorNetworksBlocked else R.plurals.majorNetworksFound
        return context.resources.getQuantityString(resource, networkCount, networkCount)
    }

    @DrawableRes
    fun networksBanner(allTrackersBlocked: Boolean): Int {
        return if (allTrackersBlocked) R.drawable.networks_banner_good else R.drawable.networks_banner_bad
    }

    @DrawableRes
    fun networksIcon(allTrackersBlocked: Boolean): Int {
        return if (allTrackersBlocked) R.drawable.networks_icon_good else R.drawable.networks_icon_bad
    }

    @DrawableRes
    fun networkPillIcon(context: Context, networkName: String): Int? {
        return networkIcon(context, networkName, "network_pill_")
    }

    @DrawableRes
    fun networkLogoIcon(context: Context, networkName: String): Int? {
        return networkIcon(context, networkName, "network_logo_")
    }

    private fun networkIcon(context: Context, networkName: String, prefix: String): Int? {
        val drawable = "$prefix$networkName"
                .replace(" ", "")
                .replace(".", "")
                .toLowerCase()
        val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
        return if (resource != 0) resource else null
    }

    fun percentage(percent: Float?): String? {
        percent ?: return ""
        val to100 = (percent * 100).toInt()
        return "$to100%"
    }

    @DrawableRes
    fun successFailureIcon(count: Int): Int = when (count) {
        0 -> R.drawable.icon_success
        else -> R.drawable.icon_fail
    }
    
}
