/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.room.Room
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.view.MenuItem
import android.view.View
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.LandingPage
import com.duckduckgo.app.browser.BrowserViewModel.Command.Navigate
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacymonitor.db.NetworkPercent
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.store.TermsOfServiceStore
import com.duckduckgo.app.settings.db.AppConfigurationDao
import com.duckduckgo.app.settings.db.AppConfigurationEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class BrowserViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var lastNetworkLeaderboardEntry: NetworkLeaderboardEntry? = null

    private val testNetworkLeaderboardDao: NetworkLeaderboardDao = object : NetworkLeaderboardDao {
        override fun insert(leaderboardEntry: NetworkLeaderboardEntry) {
            lastNetworkLeaderboardEntry = leaderboardEntry
        }

        override fun networkPercents(): LiveData<Array<NetworkPercent>> {
            return MutableLiveData<Array<NetworkPercent>>()
        }
    }

    @Mock
    lateinit var mockQueryObserver: Observer<String>

    @Mock
    lateinit var mockCommandObserver: Observer<Command>

    @Mock
    lateinit var mockTermsOfServiceStore: TermsOfServiceStore

    @Mock
    lateinit var mockSettingsStore: SettingsDataStore

    @Mock
    lateinit var mockAutoCompleteApi: AutoCompleteApi

    @Mock
    private lateinit var bookmarksDao: BookmarksDao

    @Mock
    private lateinit var mockLongPressHandler: LongPressHandler

    @Mock
    private lateinit var mockOmnibarConverter: OmnibarEntryConverter

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    private lateinit var db: AppDatabase
    private lateinit var appConfigurationDao: AppConfigurationDao

    private lateinit var testee: BrowserViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        appConfigurationDao = db.appConfigurationDao()

        testee = BrowserViewModel(
                queryUrlConverter = mockOmnibarConverter,
                duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
                termsOfServiceStore = mockTermsOfServiceStore,
                trackerNetworks = TrackerNetworks(),
                privacyMonitorRepository = PrivacyMonitorRepository(),
                networkLeaderboardDao = testNetworkLeaderboardDao,
                autoCompleteApi = mockAutoCompleteApi,
                appSettingsPreferencesStore = mockSettingsStore,
                bookmarksDao = bookmarksDao,
                longPressHandler = mockLongPressHandler,
                appConfigurationDao = appConfigurationDao)

        testee.url.observeForever(mockQueryObserver)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockOmnibarConverter.convertQueryToUri(any())).thenReturn(Uri.parse("duckduckgo.com"))

    }

    @After
    fun after() {
        testee.onCleared()
        db.close()
        testee.url.removeObserver(mockQueryObserver)
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenSubmittedQueryHasWhitespaceItIsTrimmed() {
        testee.onUserSubmittedQuery(" nytimes.com ")
        verify(mockOmnibarConverter).isWebUrl("nytimes.com")
        assertEquals("nytimes.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlPresentThenAddBookmarkButtonEnabled() {
        testee.urlChanged("www.example.com")
        assertTrue(testee.viewState.value!!.canAddBookmarks)
    }

    @Test
    fun whenNoUrlThenAddBookmarkButtonDisabled() {
        testee.urlChanged(null)
        assertFalse(testee.viewState.value!!.canAddBookmarks)
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdated() {
        testee.addBookmark("A title", "www.example.com")
        verify(bookmarksDao).insert(BookmarkEntity(title = "A title", url = "www.example.com"))
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        testee.trackerDetected(TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", TrackerNetwork("Network1", "www.tracker.com"), false))
        assertNotNull(lastNetworkLeaderboardEntry)
        assertEquals(lastNetworkLeaderboardEntry!!.domainVisited, "www.example.com")
        assertEquals(lastNetworkLeaderboardEntry!!.networkName, "Network1")
    }

    @Test
    fun whenEmptyInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("")
        verify(mockQueryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenBlankInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("     ")
        verify(mockQueryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenNonEmptyInputThenQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("foo")
        verify(mockQueryObserver).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsLoadingThenViewStateIsUpdated() {
        testee.loadingStarted()
        assertTrue(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewHasFinishedLoadingThenViewStateIsUpdated() {
        testee.loadingFinished()
        assertFalse(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlLostFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenNoOmnibarTextEverEnteredThenViewStateHasNull() {
        assertNull(testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        testee.urlChanged("duckduckgo.com")
        assertEquals("duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenUrlRewrittenToContainQuery() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        assertEquals("test", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlNotContainingQueryThenFullUrlShown() {
        testee.urlChanged("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithNonDuckDuckGoUrlThenFullUrlShown() {
        testee.urlChanged("http://example.com")
        assertEquals("http://example.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenSharedTextReceivedThenNavigationTriggered() {
        testee.onSharedTextReceived("http://example.com")
        verify(mockCommandObserver, times(2)).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is Navigate)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged(0)
        assertEquals(0, testee.viewState.value!!.progress)

        testee.progressChanged(50)
        assertEquals(50, testee.viewState.value!!.progress)

        testee.progressChanged(100)
        assertEquals(100, testee.viewState.value!!.progress)
    }

    @Test
    fun whenUserDismissesKeyboardBeforeBrowserShownThenShouldNavigateToLandingPage() {
        testee.userDismissedKeyboard()
        verify(mockCommandObserver).onChanged(ArgumentMatchers.any(LandingPage::class.java))
    }

    @Test
    fun whenUserDismissesKeyboardAfterBrowserShownThenShouldNotNavigateToLandingPage() {
        testee.urlChanged("")
        verify(mockCommandObserver, never()).onChanged(ArgumentMatchers.any(LandingPage::class.java))
    }

    @Test
    fun whenUserDismissesKeyboardBeforeBrowserShownThenShouldConsumeBackButtonEvent() {
        assertTrue(testee.userDismissedKeyboard())
    }

    @Test
    fun whenUserDismissesKeyboardAfterBrowserShownThenShouldNotConsumeBackButtonEvent() {
        testee.urlChanged("")
        assertFalse(testee.userDismissedKeyboard())
    }

    @Test
    fun whenLoadingStartedThenPrivacyGradeIsCleared() {
        testee.loadingStarted()
        assertNull(testee.privacyGrade.value)
    }

    @Test
    fun whenUrlChangedThenPrivacyGradeIsReset() {
        testee.urlChanged("https://example.com")
        assertEquals(PrivacyGrade.B, testee.privacyGrade.value)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        testee.urlChanged("https://example.com")
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, false))
        }
        assertEquals(PrivacyGrade.C, testee.privacyGrade.value)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedAfterConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.urlChanged((""))
        assertTrue(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedBeforeConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.urlChanged((""))
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigNotDownloadedThenPrivacyGradeIsNotShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusThenFireButtonIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertTrue(testee.viewState.value!!.showAutoCompleteSuggestions)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertFalse(testee.viewState.value!!.showAutoCompleteSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showAutoCompleteSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showAutoCompleteSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryThenHideKeyboardCommandNotIssued() {
        testee.onUserSubmittedQuery("")
        verify(mockCommandObserver, never()).onChanged(Mockito.any(Command.HideKeyboard.javaClass))
    }

    @Test
    fun whenEnteringNonEmptyQueryThenHideKeyboardCommandIssued() {
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.value == Command.HideKeyboard)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenViewStateUpdatedWithFullScreenFlag() {
        val stubView = View(InstrumentationRegistry.getTargetContext())
        testee.goFullScreen(stubView)
        assertTrue(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenEnterFullScreenCommandIssued() {
        val stubView = View(InstrumentationRegistry.getTargetContext())
        testee.goFullScreen(stubView)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowFullScreen)
    }

    @Test
    fun whenNotifiedLeavingFullScreenThenViewStateUpdatedWithFullScreenFlagDisabled() {
        testee.exitFullScreen()
        assertFalse(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenViewModelInitialisedThenFullScreenFlagIsDisabled() {
        assertFalse(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenUserSelectsDownloadImageOptionFromContextMenuThenDownloadFileCommandIssued() {
        whenever(mockLongPressHandler.userSelectedMenuItem(anyString(), any()))
                .thenReturn(LongPressHandler.RequiredAction.DownloadFile("example.com"))

        val mockMenuItem : MenuItem = mock()
        testee.userSelectedItemFromLongPressMenu("example.com", mockMenuItem)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DownloadImage)

        val lastCommand = commandCaptor.lastValue as Command.DownloadImage
        assertEquals("example.com", lastCommand.url)
    }
}
