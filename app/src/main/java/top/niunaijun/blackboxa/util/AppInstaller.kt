package top.niunaijun.blackboxa.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppInstaller(private val context: Context) {

    private val TAG = "🔥RESET_TRIAL"
    private val PREFS_NAME = "trial_control"
    private val KEY_FIRST_OPEN = "first_open_"
    private val TRIAL_DAYS = 2  // Período de teste em dias (definido global)

    // ========== MÉTODO PRINCIPAL - LANÇAR APP COM RESET ==========
    fun launchApk(packageName: String, userId: Int): Boolean {
        Log.d(TAG, "🚀 Iniciando $packageName")

        // Verificar se o trial expirou
        if (isTrialExpired(packageName)) {
            Log.d(TAG, "⏰ Trial expirado! Resetando...")
            resetTrial(packageName)
            // Atualizar data para renovar o ciclo
            saveFirstOpenDate(packageName)
        } else {
            Log.d(TAG, "✅ Trial ainda válido para $packageName")
        }

        // Tentar iniciar o app
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.e(TAG, "❌ App não encontrado: $packageName")
            return false
        }

        // Suporte a múltiplos usuários
        intent.putExtra("android.intent.extra.USER_ID", userId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        Log.d(TAG, "✅ $packageName iniciado com sucesso")
        return true
    }

    // ========== VERIFICAR SE O TRIAL EXPIRou ==========
    private fun isTrialExpired(packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_FIRST_OPEN + packageName
        val firstOpenStr = prefs.getString(key, null)

        if (firstOpenStr == null) {
            // Primeira vez que abre, salvar data atual
            saveFirstOpenDate(packageName)
            return false // Não expirou porque acabou de instalar
        }

        // Converter string para data
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val firstOpen = sdf.parse(firstOpenStr) ?: return false

        // Calcular diferença em dias
        val hoje = Date()
        val diff = (hoje.time - firstOpen.time) / (1000 * 60 * 60 * 24)

        Log.d(TAG, "📅 Dias desde a primeira abertura: $diff dias (limite: $TRIAL_DAYS)")

        return diff >= TRIAL_DAYS
    }

    // ========== SALVAR DATA DA PRIMEIRA ABERTURA ==========
    private fun saveFirstOpenDate(packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val hoje = sdf.format(Date())
        prefs.edit().putString(KEY_FIRST_OPEN + packageName, hoje).apply()
        Log.d(TAG, "💾 Data salva: $hoje para $packageName")
    }

    // ========== RESET COMPLETO DO TRIAL ==========
    private fun resetTrial(packageName: String) {
        try {
            val cacheDir = context.cacheDir
            val externalCacheDir = context.externalCacheDir
            val filesDir = context.filesDir
            val externalFilesDir = context.externalFilesDir(null)

            // 1. Limpar arquivos de trial
            val trialFiles = listOf("trial", "demo", "test", "premium", "vip", "subscription", "license", "activation")
            for (name in trialFiles) {
                deleteRecursive(File(cacheDir, name))
                deleteRecursive(File(externalCacheDir, name))
                deleteRecursive(File(filesDir, name))
                deleteRecursive(File(externalFilesDir, name))
            }

            // 2. Limpar SharedPreferences de trial
            val prefNames = listOf("trial", "demo", "premium", "vip", "subscription", "license", "activation")
            for (prefName in prefNames) {
                try {
                    val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    sp.edit().clear().apply()
                    Log.d(TAG, "✅ SP limpa: $prefName")
                } catch (e: Exception) { /* ignora */ }
            }

            // 3. Limpar dados do app (se possível)
            try {
                val dataDir = File("/data/data/$packageName")
                if (dataDir.exists() && dataDir.isDirectory) {
                    deleteRecursive(dataDir)
                    Log.d(TAG, "✅ Dados do app deletados: $packageName")
                }
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Não foi possível deletar /data/data (sem root)")
            }

            Log.d(TAG, "🔥 RESET TRIAL EXECUTADO PARA $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reset: ${e.message}")
        }
    }

    // ========== DELETE RECURSIVO ==========
    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursive(it) }
            }
            file.delete()
        } catch (e: Exception) { /* ignora */ }
    }

    // ========== MÉTODO PARA INSTALAR APK ==========
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