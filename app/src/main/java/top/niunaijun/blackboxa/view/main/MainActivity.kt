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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
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
            Manifest.permission.REQUEST_INSTALL_PACKAGES
        )
        for (perm in permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1001)
                break
            }
        }
    }

    // ========== MÉTODO PARA LANÇAR APPS COM RESET ==========
    private fun launchApp(packageName: String) {
        val installer = AppInstaller(this)
        val success = installer.launchApk(packageName, 0)
        val msg = if (success) "✅ $packageName aberto!" else "❌ App não encontrado"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ========== BOTÕES PARA OS APPS (exemplo) ==========
    fun launchUNITV(view: View) { launchApp("com.unitv") }
    fun launchYOUCINE(view: View) { launchApp("com.youcine") }
    // Adicione mais botões conforme necessário...

    // ========== INSTALAR APK ==========
    fun installApk(view: View) {
        val installer = AppInstaller(this)
        val apkPath = Environment.getExternalStorageDirectory().absolutePath + "/Download/app.apk"
        val success = installer.installApk(apkPath)
        Toast.makeText(this, if (success) "✅ APK instalado!" else "❌ Falha", Toast.LENGTH_SHORT).show()
    }

    // ========== SUPORTE A CONTROLE REMOTO ==========
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            currentFocus?.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
