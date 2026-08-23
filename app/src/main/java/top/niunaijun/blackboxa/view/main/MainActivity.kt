package top.niunaijun.blackboxa.view.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.util.AppInstaller
import android.util.Log

class MainActivity : AppCompatActivity() {

    private val TAG = "🔥MAIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
        Log.d(TAG, "✅ App Reuse Geovane iniciado!")
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            }
        }
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INSTALL_PACKAGES,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )
        for (perm in permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1001)
                break
            }
        }
    }

    // ====================== MÉTODO PRINCIPAL ======================
    private fun launchApp(packageName: String) {
        Log.d(TAG, "🚀 Tentando abrir: $packageName")
        val installer = AppInstaller(this)
        val success = installer.launchApk(packageName, 0)
        val msg = if (success) "✅ $packageName aberto com reset!" else "❌ App não encontrado"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ====================== BOTÕES PARA TODOS OS 17 APPS ======================
    fun launchUNITV(view: View) { launchApp("com.unitv") }
    fun launchYOUCINE(view: View) { launchApp("com.youcine") }
    fun launchUNITVNET(view: View) { launchApp("com.unitvnet") }
    fun launchSTV(view: View) { launchApp("com.stv") }
    fun launchSOLT(view: View) { launchApp("com.soltv") }
    fun launchXPRIME(view: View) { launchApp("com.xprime.tv") }
    fun launchBRASTV(view: View) { launchApp("com.brastv") }
    fun launchNOVATV(view: View) { launchApp("com.novatv") }
    fun launchNEXATV(view: View) { launchApp("com.nexatv") }
    fun launchLUPITV(view: View) { launchApp("com.lupitv") }
    fun launchPOTROPLAY(view: View) { launchApp("com.potroplay") }
    fun launchBONITOTV(view: View) { launchApp("com.bonitotv") }
    fun launchTUDOTV(view: View) { launchApp("com.tudotv") }
    fun launchTUDOTVMOBILE(view: View) { launchApp("com.tudotvmobile") }
    fun launchXPRIME2(view: View) { launchApp("com.xprime.tv") }
    fun launchBRASTV2(view: View) { launchApp("com.brastv") }
    fun launchNOVATV2(view: View) { launchApp("com.novatv") }

    // ====================== INSTALAR APK ======================
    fun installApk(view: View) {
        val installer = AppInstaller(this)
        val apkPath = Environment.getExternalStorageDirectory().absolutePath + "/Download/app.apk"
        val success = installer.installApk(apkPath)
        Toast.makeText(this, if (success) "✅ APK instalado!" else "❌ Falha", Toast.LENGTH_SHORT).show()
    }

    // ====================== SUPORTE A CONTROLE REMOTO (TV) ======================
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            currentFocus?.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}