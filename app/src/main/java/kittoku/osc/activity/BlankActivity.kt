package kittoku.osc.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kittoku.osc.preference.applyStoredLocale
import java.io.BufferedOutputStream


internal const val BLANK_ACTIVITY_TYPE_SAVE_CERT = 2

internal const val EXTRA_KEY_TYPE = "TYPE"
internal const val EXTRA_KEY_CERT = "CERT"
internal const val EXTRA_KEY_FILENAME = "FILENAME"

/**
 * Невидимая активити под одну задачу: сохранить сертификат сервера, предложенный
 * в уведомлении, туда, куда укажет пользователь. Своего UI у неё нет — сразу
 * открывается системный диалог создания файла.
 */
class BlankActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyStoredLocale(newBase))
    }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                contentResolver.openOutputStream(uri, "w")?.also { stream ->
                    BufferedOutputStream(stream).use {
                        it.write(intent.getByteArrayExtra(EXTRA_KEY_CERT))
                    }
                }
            }
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras?.getInt(EXTRA_KEY_TYPE) != BLANK_ACTIVITY_TYPE_SAVE_CERT) {
            finish()
            return
        }

        Intent(Intent.ACTION_CREATE_DOCUMENT).also {
            it.addCategory(Intent.CATEGORY_OPENABLE)
            it.setType("application/x-x509-ca-cert")
            it.putExtra(Intent.EXTRA_TITLE, intent.getStringExtra(EXTRA_KEY_FILENAME))

            launcher.launch(it)
        }
    }
}
