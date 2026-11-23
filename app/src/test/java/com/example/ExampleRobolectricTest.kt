package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and QR bitmap generation work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DropLink", appName)

        val url = NetworkUtils.buildSessionUrl(
            host = "192.168.1.50",
            port = 8765,
            token = "ABCD1234",
            deviceName = "Pixel 9",
            mode = "receive"
        )
        val parsed = NetworkUtils.parseQrPayload(url)
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed?.host)
        assertEquals(8765, parsed?.port)
        assertEquals("ABCD1234", parsed?.token)

        val qr = NetworkUtils.generateQrBitmap(url, 256)
        assertNotNull(qr)
        assertEquals(256, qr?.width)
    }
}
