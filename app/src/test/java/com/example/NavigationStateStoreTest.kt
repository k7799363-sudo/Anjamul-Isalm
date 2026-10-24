package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NavigationStateStore
import com.example.model.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationStateStoreTest {

    private lateinit var context: Context
    private lateinit var store: NavigationStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = NavigationStateStore.getInstance(context)
        store.clearSavedLocation()
    }

    @Test
    fun testDefaultLocationIsTracks() {
        val location = store.getSavedLocation()
        assertEquals(LibraryTab.TRACKS, location.tab)
        assertEquals("TRACKS", location.currentSection)
        assertNull(location.currentFolder)
        assertNull(location.currentAlbum)
        assertNull(location.currentPlaylist)
    }

    @Test
    fun testFolderPersistenceAndRestore_BengaliRelax() {
        // User navigates to Bengali Relax folder
        val saved = store.saveLocation(
            tab = LibraryTab.FOLDERS,
            containerType = "FOLDER",
            containerName = "Bengali Relax",
            currentFolder = "Bengali Relax"
        )

        assertEquals(LibraryTab.FOLDERS, saved.tab)
        assertEquals("FOLDERS", saved.currentSection)
        assertEquals("Bengali Relax", saved.currentFolder)
        assertNull(saved.currentAlbum)
        assertNull(saved.currentPlaylist)

        // Simulate app kill (cleared from Recents) and reopen:
        // New instance reading from SharedPreferences
        val restored = store.getSavedLocation()

        assertEquals("Bengali Relax", restored.currentFolder)
        assertEquals(LibraryTab.FOLDERS, restored.tab)
        assertEquals("FOLDERS", restored.currentSection)
        assertEquals("FOLDER", restored.containerType)
        assertEquals("Bengali Relax", restored.containerName)
    }

    @Test
    fun testAlbumPersistenceAndRestore() {
        store.saveLocation(
            tab = LibraryTab.ALBUMS,
            containerType = "ALBUM",
            containerName = "Mishary Alafasy",
            currentAlbum = "Mishary Alafasy"
        )

        val restored = store.getSavedLocation()
        assertEquals("Mishary Alafasy", restored.currentAlbum)
        assertEquals(LibraryTab.ALBUMS, restored.tab)
        assertEquals("ALBUMS", restored.currentSection)
        assertNull(restored.currentFolder)
    }

    @Test
    fun testPlaylistPersistenceAndRestore() {
        store.saveLocation(
            tab = LibraryTab.PLAYLISTS,
            containerType = "PLAYLIST",
            containerName = "Evening Adhkar",
            currentPlaylist = "Evening Adhkar"
        )

        val restored = store.getSavedLocation()
        assertEquals("Evening Adhkar", restored.currentPlaylist)
        assertEquals(LibraryTab.PLAYLISTS, restored.tab)
        assertEquals("PLAYLISTS", restored.currentSection)
        assertNull(restored.currentFolder)
    }
}
