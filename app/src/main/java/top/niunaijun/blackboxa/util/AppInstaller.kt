package top.niunaijun.blackboxa.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import top.niunaijun.blackbox.BlackBoxCore
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppInstaller(private val context: Context) {

    private val TAG = "🔥RESET_TRIAL"
    private val PREFS_NAME = "trial_control"
    private val KEY_FIRST_OPEN = "first_open_"
    private val TRIAL_DAYS = 2

    fun launchApk(packageName: String, userId: Int): Boolean {
        Log.d(TAG, "🚀 Iniciando $packageName com userId=$userId")

        // Verifica se o trial expirou e reseta os dados do clone
        if (isTrialExpired(packageName)) {
            Log.d(TAG, "⏰ Trial expirado! Resetando dados do clone...")
            resetTrial(packageName, userId)
            saveFirstOpenDate(packageName)
        } else {
            Log.d(TAG, "✅ Trial ainda válido para $packageName")
        }

        // Lança o app clonado via BlackBox Core
        return try {
            BlackBoxCore.get().launchApk(packageName, userId)
            Log.d(TAG, "✅ $packageName lançado com sucesso via BlackBox")
            Toast.makeText(context, "✅ $packageName aberto!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao lançar $packageName: ${e.message}")
            Toast.makeText(context, "❌ Erro: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    // ========== MÉTODOS DE RESET ==========
    private fun isTrialExpired(packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        Log.d(TAG, "📅 Dias desde a primeira abertura: $diff dias (limite: $TRIAL_DAYS)")
        return diff >= TRIAL_DAYS
    }

    private fun saveFirstOpenDate(packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val hoje = sdf.format(Date())
        prefs.edit().putString(KEY_FIRST_OPEN + packageName, hoje).apply()
        Log.d(TAG, "💾 Data salva: $hoje para $packageName")
    }

    private fun resetTrial(packageName: String, userId: Int) {
        try {
            // ========== 1. DELETAR A PASTA DE DADOS DO CLONE ==========
            // Usamos a API do BlackBox para obter o diretório de dados do clone
            val dataDir = BlackBoxCore.get().getPackageDataDir(packageName, userId)
            if (dataDir != null && dataDir.exists()) {
                deleteRecursive(dataDir)
                Log.d(TAG, "✅ Dados do clone deletados: $dataDir")
            } else {
                Log.d(TAG, "⚠️ Diretório de dados não encontrado para $packageName (userId=$userId)")
            }

            // ========== 2. DELETAR AS SHARED PREFERENCES DO CLONE ==========
            if (dataDir != null) {
                val prefsDir = File(dataDir, "shared_prefs")
                if (prefsDir.exists()) {
                    deleteRecursive(prefsDir)
                    Log.d(TAG, "✅ SharedPreferences do clone deletadas")
                }
            }

            // ========== 3. LIMPAR AS SHARED PREFERENCES DO APP HOSPEDEIRO ==========
            val prefNames = listOf("trial", "demo", "premium", "vip", "subscription", "license", "activation")
            for (prefName in prefNames) {
                try {
                    val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    sp.edit().clear().apply()
                    Log.d(TAG, "✅ SP limpa: $prefName")
                } catch (_: Exception) {}
            }

            Log.d(TAG, "🔥 RESET TRIAL COMPLETO PARA $packageName (userId=$userId)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reset: ${e.message}")
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursive(it) }
            }
            file.delete()
            Log.d(TAG, "🗑️ Deletado: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar ${file.absolutePath}: ${e.message}")
        }
    }

    fun installApk(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "❌ APK não encontrado: $apkPath")
            return false
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao instalar: ${e.message}")
            return false
        }
    }
}