package top.niunaijun.blackboxa.view.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.databinding.ActivityMainBinding
import top.niunaijun.blackboxa.util.AppInstaller
import top.niunaijun.blackboxa.util.Resolution
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.apps.AppsFragment
import top.niunaijun.blackboxa.view.base.LoadingActivity
import top.niunaijun.blackboxa.view.fake.FakeManagerActivity
import top.niunaijun.blackboxa.view.list.ListActivity
import top.niunaijun.blackboxa.view.setting.SettingActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : LoadingActivity() {

    private val viewBinding: ActivityMainBinding by inflate()
    private lateinit var mViewPagerAdapter: ViewPagerAdapter
    private val fragmentList = mutableListOf<AppsFragment>()
    private var currentUser = 0

    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        private const val VPN_PERMISSION_REQUEST_CODE = 1002

        // ========== RESET TRIAL CONFIG ==========
        private const val PREFS_NAME = "trial_control"
        private const val KEY_FIRST_OPEN = "first_open_"
        private const val TRIAL_DAYS = 2

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            // Hook do BlackBox (antes)
            try {
                BlackBoxCore.get().onBeforeMainActivityOnCreate(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBeforeMainActivityOnCreate: ${e.message}")
            }

            setContentView(viewBinding.root)
            initToolbar(viewBinding.toolbarLayout.toolbar, R.string.app_name)
            initViewPager()
            initFab()
            initToolbarSubTitle()

            // Verifica permissões
            checkStoragePermission()
            checkVpnPermission()

            // Hook do BlackBox (depois)
            try {
                BlackBoxCore.get().onAfterMainActivityOnCreate(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onAfterMainActivityOnCreate: ${e.message}")
            }

            Log.d(TAG, "✅ App Reuse Geovane iniciado!")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate: ${e.message}")
            showErrorDialog("Failed to initialize app: ${e.message}")
        }
    }

    // ============================================================
    // ========== RESET TRIAL - MÉTODOS PÚBLICOS ==========
    // ============================================================

    /**
     * Método público para ser chamado pelo AppsFragment.
     * Verifica se o trial expirou e, se sim, reseta e renova.
     * @param packageName pacote do app a ser verificado
     * @return true se o reset foi executado, false caso contrário
     */
    fun checkAndResetTrial(packageName: String): Boolean {
        if (isTrialExpired(packageName)) {
            Log.d(TAG, "⏰ Trial expirado para $packageName. Resetando...")
            resetTrial(packageName)
            saveFirstOpenDate(packageName)
            return true
        } else {
            Log.d(TAG, "✅ Trial ainda válido para $packageName")
            return false
        }
    }

    private fun isTrialExpired(packageName: String): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_FIRST_OPEN + packageName
        val firstOpenStr = prefs.getString(key, null)

        if (firstOpenStr == null) {
            saveFirstOpenDate(packageName)
            return false
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val firstOpen = sdf.parse(firstOpenStr) ?: return false
        val hoje = Date()
        val diff = (hoje.time - firstOpen.time) / (1000 * 60 * 60 * 24)

        Log.d(TAG, "📅 Dias desde primeira abertura para $packageName: $diff (limite: $TRIAL_DAYS)")
        return diff >= TRIAL_DAYS
    }

    private fun saveFirstOpenDate(packageName: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val hoje = sdf.format(Date())
        prefs.edit().putString(KEY_FIRST_OPEN + packageName, hoje).apply()
        Log.d(TAG, "💾 Data salva para $packageName: $hoje")
    }

    private fun resetTrial(packageName: String) {
        try {
            val cacheDir = cacheDir
            val externalCacheDir = externalCacheDir
            val filesDir = filesDir
            val externalFilesDir = getExternalFilesDir(null)

            val trialFiles = listOf("trial", "demo", "test", "premium", "vip", "subscription", "license", "activation")
            for (name in trialFiles) {
                deleteRecursive(File(cacheDir, name))
                if (externalCacheDir != null) deleteRecursive(File(externalCacheDir, name))
                deleteRecursive(File(filesDir, name))
                if (externalFilesDir != null) deleteRecursive(File(externalFilesDir, name))
            }

            val prefNames = listOf("trial", "demo", "premium", "vip", "subscription", "license", "activation")
            for (prefName in prefNames) {
                try {
                    getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
                } catch (_: Exception) {}
            }

            try {
                val dataDir = File("/data/data/$packageName")
                if (dataDir.exists() && dataDir.isDirectory) {
                    deleteRecursive(dataDir)
                }
            } catch (_: Exception) {}

            Log.d(TAG, "🔥 Reset trial executado para $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reset para $packageName: ${e.message}")
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursive(it) }
            }
            file.delete()
        } catch (_: Exception) {}
    }

    // ============================================================
    // ========== MÉTODOS PARA LANÇAR APPS COM RESET ==========
    // ============================================================

    private fun launchApp(packageName: String) {
        Log.d(TAG, "🚀 Tentando abrir: $packageName")
        // Aplica reset se necessário
        checkAndResetTrial(packageName)

        val installer = AppInstaller(this)
        val success = installer.launchApk(packageName, 0)
        val msg = if (success) "✅ $packageName aberto com reset!" else "❌ App não encontrado"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ========== BOTÕES PARA TODOS OS 17 APPS ==========
    // (Para uso em layouts com botões específicos)
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

    // ========== INSTALAR APK ==========
    fun installApk(view: View) {
        val installer = AppInstaller(this)
        val apkPath = android.os.Environment.getExternalStorageDirectory().absolutePath + "/Download/app.apk"
        val success = installer.installApk(apkPath)
        Toast.makeText(this, if (success) "✅ APK instalado!" else "❌ Falha", Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // ========== SUPORTE A CONTROLE REMOTO (TV) ==========
    // ============================================================
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            currentFocus?.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ============================================================
    // ========== MÉTODOS ORIGINAIS DO NEWBLACKBOX (NÃO ALTERADOS) ==========
    // ============================================================

    private fun checkStoragePermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Log.w(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted")
                    showStoragePermissionDialog()
                }
            } else {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Storage permissions not granted on Android ${android.os.Build.VERSION.SDK_INT}")
                    requestLegacyStoragePermission()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage permission: ${e.message}")
        }
    }

    private fun requestLegacyStoragePermission() {
        try {
            androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting storage permission: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            ) {
                Log.d(TAG, "Storage permissions granted")
            } else {
                Log.w(TAG, "Storage permissions denied")
            }
        }
    }

    private fun showStoragePermissionDialog() {
        try {
            MaterialDialog(this).show {
                title(text = "Storage Permission Required")
                message(text = "This app needs 'All Files Access' permission to properly run sandboxed apps. Without this permission, some apps may not work correctly.\n\nPlease grant permission in the next screen.")
                positiveButton(text = "Grant Permission") { openAllFilesAccessSettings() }
                negativeButton(text = "Later") { Log.w(TAG, "User postponed storage permission") }
                cancelable(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing storage permission dialog: ${e.message}")
        }
    }

    private fun openAllFilesAccessSettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionResult.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening storage settings: ${e.message}")
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionResult.launch(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening fallback storage settings: ${e2.message}")
            }
        }
    }

    private val storagePermissionResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (android.os.Environment.isExternalStorageManager()) {
                            Log.d(TAG, "Storage permission granted!")
                        } else {
                            Log.w(TAG, "Storage permission still not granted")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling storage permission result: ${e.message}")
                }
            }

    private fun checkVpnPermission() {
        try {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                Log.d(TAG, "VPN permission not granted, requesting...")
                vpnPermissionResult.launch(vpnIntent)
            } else {
                Log.d(TAG, "VPN permission already granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPN permission: ${e.message}")
        }
    }

    private val vpnPermissionResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                try {
                    if (result.resultCode == RESULT_OK) {
                        Log.d(TAG, "VPN permission granted!")
                    } else {
                        Log.w(TAG, "VPN permission denied by user")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling VPN permission result: ${e.message}")
                }
            }

    private fun showErrorDialog(message: String) {
        try {
            MaterialDialog(this).show {
                title(text = "Error")
                message(text = message)
                positiveButton(text = "OK") { finish() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error dialog: ${e.message}")
            finish()
        }
    }

    private fun initToolbarSubTitle() {
        try {
            updateUserRemark(0)
            viewBinding.toolbarLayout.toolbar.getChildAt(1)?.setOnClickListener {
                try {
                    MaterialDialog(this).show {
                        title(res = R.string.userRemark)
                        input(
                                hintRes = R.string.userRemark,
                                prefill = viewBinding.toolbarLayout.toolbar.subtitle
                        ) { _, input ->
                            try {
                                AppManager.mRemarkSharedPreferences.edit {
                                    putString("Remark$currentUser", input.toString())
                                    viewBinding.toolbarLayout.toolbar.subtitle = input
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving user remark: ${e.message}")
                            }
                        }
                        positiveButton(res = R.string.done)
                        negativeButton(res = R.string.cancel)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing remark dialog: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initToolbarSubTitle: ${e.message}")
        }
    }

    private fun initViewPager() {
        try {
            val userList = BlackBoxCore.get().users
            userList.forEach { fragmentList.add(AppsFragment.newInstance(it.id)) }

            currentUser = userList.firstOrNull()?.id ?: 0
            fragmentList.add(AppsFragment.newInstance(userList.size))

            mViewPagerAdapter = ViewPagerAdapter(this)
            mViewPagerAdapter.replaceData(fragmentList)
            viewBinding.viewPager.adapter = mViewPagerAdapter
            viewBinding.dotsIndicator.setViewPager2(viewBinding.viewPager)
            viewBinding.viewPager.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            try {
                                super.onPageSelected(position)
                                currentUser = fragmentList[position].userID
                                updateUserRemark(currentUser)
                                showFloatButton(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in onPageSelected: ${e.message}")
                            }
                        }
                    }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in initViewPager: ${e.message}")
        }
    }

    private fun initFab() {
        try {
            viewBinding.fab.setOnClickListener {
                try {
                    val userId = viewBinding.viewPager.currentItem
                    val intent = Intent(this, ListActivity::class.java)
                    intent.putExtra("userID", userId)
                    apkPathResult.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching ListActivity: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initFab: ${e.message}")
        }
    }

    fun showFloatButton(show: Boolean) {
        try {
            val tranY: Float = Resolution.convertDpToPixel(120F, App.getContext())
            val time = 200L
            if (show) {
                viewBinding.fab.animate().translationY(0f).alpha(1f).setDuration(time).start()
            } else {
                viewBinding.fab.animate().translationY(tranY).alpha(0f).setDuration(time).start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showFloatButton: ${e.message}")
        }
    }

    fun scanUser() {
        try {
            val userList = BlackBoxCore.get().users

            if (fragmentList.size == userList.size) {
                fragmentList.add(AppsFragment.newInstance(fragmentList.size))
            } else if (fragmentList.size > userList.size + 1) {
                fragmentList.removeLast()
            }

            mViewPagerAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateUserRemark(userId: Int) {
        try {
            var remark = AppManager.mRemarkSharedPreferences.getString("Remark$userId", "User $userId")
            if (remark.isNullOrEmpty()) {
                remark = "User $userId"
            }
            viewBinding.toolbarLayout.toolbar.subtitle = remark
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user remark: ${e.message}")
            viewBinding.toolbarLayout.toolbar.subtitle = "User $userId"
        }
    }

    private val apkPathResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                try {
                    if (it.resultCode == RESULT_OK) {
                        it.data?.let { data ->
                            val userId = data.getIntExtra("userID", 0)
                            val source = data.getStringExtra("source")
                            if (source != null) {
                                fragmentList[userId].installApk(source)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling APK path result: ${e.message}")
                }
            }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            menuInflater.inflate(R.menu.menu_main, menu)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating options menu: ${e.message}")
            return false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.main_git -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ALEX5402/NewBlackbox"))
                    startActivity(intent)
                }
                R.id.main_setting -> {
                    SettingActivity.start(this)
                }
                R.id.main_tg -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/newblackboxa"))
                    startActivity(intent)
                }
                R.id.fake_location -> {
                    val intent = Intent(this, FakeManagerActivity::class.java)
                    intent.putExtra("userID", 0)
                    startActivity(intent)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling menu item selection: ${e.message}")
            return false
        }
    }
}