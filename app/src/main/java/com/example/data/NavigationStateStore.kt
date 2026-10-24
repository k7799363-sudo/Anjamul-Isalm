package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.LibraryTab

data class PersistedNavigationLocation(
    val tab: LibraryTab = LibraryTab.TRACKS,
    val currentSection: String = LibraryTab.TRACKS.name,
    val currentFolder: String? = null,
    val currentAlbum: String? = null,
    val currentPlaylist: String? = null,
    val containerType: String = "NONE", // FOLDER, ALBUM, ARTIST, PLAYLIST, NONE
    val containerId: String? = null,
    val containerName: String? = null,
    val filterFavoritesOnly: Boolean = false
)

class NavigationStateStore private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "nur_anjam_nav_state_prefs"
        private const val KEY_LAST_TAB = "last_library_tab"
        private const val KEY_CURRENT_SECTION = "current_section"
        private const val KEY_CURRENT_FOLDER = "current_folder"
        private const val KEY_CURRENT_ALBUM = "current_album"
        private const val KEY_CURRENT_PLAYLIST = "current_playlist"
        private const val KEY_CONTAINER_TYPE = "last_container_type"
        private const val KEY_CONTAINER_ID = "last_container_id"
        private const val KEY_CONTAINER_NAME = "last_container_name"
        private const val KEY_FILTER_FAVORITES = "filter_favorites_only"

        @Volatile
        private var INSTANCE: NavigationStateStore? = null

        fun getInstance(context: Context): NavigationStateStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NavigationStateStore(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLocation(
        tab: LibraryTab,
        containerType: String = "NONE",
        containerId: String? = null,
        containerName: String? = null,
        filterFavoritesOnly: Boolean = false,
        currentFolder: String? = null,
        currentAlbum: String? = null,
        currentPlaylist: String? = null
    ): PersistedNavigationLocation {
        val effectiveFolder = currentFolder ?: if (containerType == "FOLDER") containerName else null
        val effectiveAlbum = currentAlbum ?: if (containerType == "ALBUM") containerName else null
        val effectivePlaylist = currentPlaylist ?: if (containerType == "PLAYLIST") containerName else null

        val effectiveTab = when {
            effectiveFolder != null -> LibraryTab.FOLDERS
            effectiveAlbum != null -> LibraryTab.ALBUMS
            effectivePlaylist != null -> LibraryTab.PLAYLISTS
            else -> tab
        }

        val effectiveSection = when {
            effectiveFolder != null -> "FOLDERS"
            effectiveAlbum != null -> "ALBUMS"
            effectivePlaylist != null -> "PLAYLISTS"
            else -> effectiveTab.name
        }

        val effectiveContainerType = when {
            effectiveFolder != null -> "FOLDER"
            effectiveAlbum != null -> "ALBUM"
            effectivePlaylist != null -> "PLAYLIST"
            else -> containerType
        }

        val effectiveContainerName = containerName ?: effectiveFolder ?: effectiveAlbum ?: effectivePlaylist

        prefs.edit()
            .putString(KEY_LAST_TAB, effectiveTab.name)
            .putString(KEY_CURRENT_SECTION, effectiveSection)
            .putString(KEY_CURRENT_FOLDER, effectiveFolder)
            .putString(KEY_CURRENT_ALBUM, effectiveAlbum)
            .putString(KEY_CURRENT_PLAYLIST, effectivePlaylist)
            .putString(KEY_CONTAINER_TYPE, effectiveContainerType)
            .putString(KEY_CONTAINER_ID, containerId)
            .putString(KEY_CONTAINER_NAME, effectiveContainerName)
            .putBoolean(KEY_FILTER_FAVORITES, filterFavoritesOnly)
            .apply()

        return PersistedNavigationLocation(
            tab = effectiveTab,
            currentSection = effectiveSection,
            currentFolder = effectiveFolder,
            currentAlbum = effectiveAlbum,
            currentPlaylist = effectivePlaylist,
            containerType = effectiveContainerType,
            containerId = containerId,
            containerName = effectiveContainerName,
            filterFavoritesOnly = filterFavoritesOnly
        )
    }

    fun getSavedLocation(): PersistedNavigationLocation {
        val currentFolder = prefs.getString(KEY_CURRENT_FOLDER, null)?.takeIf { it.isNotBlank() }
        val currentAlbum = prefs.getString(KEY_CURRENT_ALBUM, null)?.takeIf { it.isNotBlank() }
        val currentPlaylist = prefs.getString(KEY_CURRENT_PLAYLIST, null)?.takeIf { it.isNotBlank() }
        val currentSection = prefs.getString(KEY_CURRENT_SECTION, null)?.takeIf { it.isNotBlank() }
        val lastTab = prefs.getString(KEY_LAST_TAB, null)?.takeIf { it.isNotBlank() }
        val containerType = prefs.getString(KEY_CONTAINER_TYPE, "NONE") ?: "NONE"
        val containerId = prefs.getString(KEY_CONTAINER_ID, null)
        val containerName = prefs.getString(KEY_CONTAINER_NAME, null)
            ?: currentFolder ?: currentAlbum ?: currentPlaylist
        val filterFavorites = prefs.getBoolean(KEY_FILTER_FAVORITES, false)

        val resolvedFolder = currentFolder ?: if (containerType == "FOLDER") containerName else null
        val resolvedAlbum = currentAlbum ?: if (containerType == "ALBUM") containerName else null
        val resolvedPlaylist = currentPlaylist ?: if (containerType == "PLAYLIST") containerName else null

        val resolvedTab = when {
            resolvedFolder != null -> LibraryTab.FOLDERS
            resolvedAlbum != null -> LibraryTab.ALBUMS
            resolvedPlaylist != null -> LibraryTab.PLAYLISTS
            currentSection != null -> {
                LibraryTab.values().find {
                    it.name.equals(currentSection, ignoreCase = true) ||
                    it.title.equals(currentSection, ignoreCase = true)
                } ?: LibraryTab.TRACKS
            }
            lastTab != null -> {
                try {
                    LibraryTab.valueOf(lastTab)
                } catch (_: Exception) {
                    LibraryTab.TRACKS
                }
            }
            else -> LibraryTab.TRACKS
        }

        val resolvedSection = currentSection ?: when (resolvedTab) {
            LibraryTab.FOLDERS -> "FOLDERS"
            LibraryTab.ALBUMS -> "ALBUMS"
            LibraryTab.PLAYLISTS -> "PLAYLISTS"
            LibraryTab.ARTISTS -> "ARTISTS"
            LibraryTab.TRACKS -> "TRACKS"
            else -> resolvedTab.name
        }

        val resolvedContainerType = when {
            resolvedFolder != null -> "FOLDER"
            resolvedAlbum != null -> "ALBUM"
            resolvedPlaylist != null -> "PLAYLIST"
            else -> containerType
        }

        val resolvedContainerName = containerName ?: resolvedFolder ?: resolvedAlbum ?: resolvedPlaylist

        return PersistedNavigationLocation(
            tab = resolvedTab,
            currentSection = resolvedSection,
            currentFolder = resolvedFolder,
            currentAlbum = resolvedAlbum,
            currentPlaylist = resolvedPlaylist,
            containerType = resolvedContainerType,
            containerId = containerId,
            containerName = resolvedContainerName,
            filterFavoritesOnly = filterFavorites
        )
    }

    fun clearSavedLocation() {
        prefs.edit().clear().apply()
    }
}
