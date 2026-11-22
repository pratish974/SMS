package com.dns.sms.perf

import android.content.ContentProviderOperation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adds 3 test contacts WITH photos for seeded perf numbers (+919000000000..2), to check that contact
 * photos show in the conversation list. Emulator only, opt-in:
 *   adb shell am instrument -w -e contactSeed true -e class com.dns.sms.perf.ContactPhotoSeeder \
 *     com.dns.sms.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class ContactPhotoSeeder {
    private val args = InstrumentationRegistry.getArguments()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seed() {
        assumeTrue("opt-in only", args.getString("contactSeed") == "true")
        assumeTrue("emulator only", Build.HARDWARE.contains("ranchu") || Build.PRODUCT.startsWith("sdk"))
        // The app never asks for WRITE_CONTACTS; borrow the shell's permission for this test only.
        InstrumentationRegistry.getInstrumentation().uiAutomation.adoptShellPermissionIdentity()
        val people = listOf("Asha Patil" to Color.rgb(233, 30, 99), "Ravi Kumar" to Color.rgb(3, 169, 244), "Meera Joshi" to Color.rgb(76, 175, 80))
        people.forEachIndexed { i, (name, color) ->
            val ops = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
                data(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
                data(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+91900000000$i")
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build(),
                data(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo(color, name.first())).build(),
            )
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
    }

    private fun data(mime: String) = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, mime)

    private fun photo(color: Int, letter: Char): ByteArray {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(color)
        c.drawCircle(128f, 100f, 60f, Paint().apply { setColor(Color.WHITE); isAntiAlias = true })
        c.drawText(letter.toString(), 100f, 240f, Paint().apply { setColor(Color.WHITE); textSize = 80f; isAntiAlias = true })
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
