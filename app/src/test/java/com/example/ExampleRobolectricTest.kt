package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NurAnjam", appName)
  }

  @Test
  fun `bundled audio assets are present and readable`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.assets.open("audio/surah_fatihah.mp3").use { input ->
      val bytesRead = input.available()
      assertTrue("Surah Fatihah MP3 asset should be readable", bytesRead > 50000)
    }

    context.assets.open("audio/ambient/ambient_rain.wav").use { input ->
      val bytesRead = input.available()
      assertTrue("Ambient rain asset should be readable", bytesRead > 50000)
    }
  }
}
