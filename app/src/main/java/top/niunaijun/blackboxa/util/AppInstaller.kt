package top.niunaijun.blackboxa.util

import android.content.Context
import android.content.Intent
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

        // Reset do trial (se expirado)
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

    // ========== MÉTODO PÚBLICO PARA RESET ==========
    fun resetAppData(packageName: String, userId: Int) {
        resetTrial(packageName, userId)
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
            Log.d(TAG, "🔥 resetTrial chamado para $packageName (userId=$userId)")

            // ============================================================
            // ===== RESET MANUAL (DELETAR PASTAS + LIMPAR SP) =====
            // ============================================================
            val baseDir = context.filesDir
            val cacheDir = context.cacheDir
            val externalFilesDir = context.getExternalFilesDir(null)
            val externalCacheDir = context.externalCacheDir

            Log.d(TAG, "📂 BaseDir: ${baseDir.absolutePath}")

            // Lista de caminhos possíveis para dados do clone
            val caminhos = mutableListOf(
                File(baseDir, "users/$userId/apps/$packageName"),
                File(baseDir, "virtual/$userId/$packageName"),
                File(baseDir, "apps/$packageName"),
                File(baseDir, "data/$userId/$packageName"),
                File(cacheDir, "users/$userId/$packageName"),
                File(cacheDir, "virtual/$userId/$packageName")
            )

            externalFilesDir?.let {
                caminhos.add(File(it, "users/$userId/apps/$packageName"))
                caminhos.add(File(it, "virtual/$userId/$packageName"))
                caminhos.add(File(it, "apps/$packageName"))
            }
            externalCacheDir?.let {
                caminhos.add(File(it, "users/$userId/apps/$packageName"))
                caminhos.add(File(it, "virtual/$userId/$packageName"))
            }

            var encontrou = false
            for (caminho in caminhos) {
                if (caminho.exists()) {
                    Log.d(TAG, "✅ Encontrado: ${caminho.absolutePath}")
                    deleteRecursive(caminho)
                    encontrou = true
                } else {
                    Log.d(TAG, "❌ Não encontrado: ${caminho.absolutePath}")
                }
            }

            // Busca recursiva por pastas com o nome do pacote
            val dirsToSearch = listOfNotNull(baseDir, cacheDir, externalFilesDir, externalCacheDir)
            for (dir in dirsToSearch) {
                val encontrouSub = deleteAllMatching(dir, packageName)
                if (encontrouSub) encontrou = true
            }

            // Limpar SharedPreferences
            val prefNames = listOf(
                "trial", "demo", "premium", "vip", "subscription", 
                "license", "activation", "user_data", "prefs", "settings"
            )
            for (prefName in prefNames) {
                try {
                    val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    sp.edit().clear().apply()
                    Log.d(TAG, "✅ SP limpa: $prefName")
                } catch (_: Exception) {}
            }

            if (encontrou) {
                Log.d(TAG, "🔥 RESET MANUAL COMPLETO PARA $packageName (userId=$userId)")
                Toast.makeText(context, "✅ Reset executado para $packageName", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "⚠️ NENHUMA PASTA DE DADOS ENCONTRADA PARA $packageName")
                Toast.makeText(context, "⚠️ Reset: dados não encontrados", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reset: ${e.message}")
            Toast.makeText(context, "❌ Erro no reset: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Deleta recursivamente todas as pastas que contêm o nome do pacote
     */
    private fun deleteAllMatching(rootDir: File, packageName: String): Boolean {
        var deletou = false
        try {
            if (!rootDir.exists()) return false
            
            val files = rootDir.listFiles()
            if (files == null) return false

            for (file in files) {
                if (file.isDirectory) {
                    if (file.name.contains(packageName) || file.absolutePath.contains(packageName)) {
                        Log.d(TAG, "🗑️ Deletando pasta: ${file.absolutePath}")
                        deleteRecursive(file)
                        deletou = true
                    } else {
                        val subDeletou = deleteAllMatching(file, packageName)
                        if (subDeletou) deletou = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar pastas: ${e.message}")
        }
        return deletou
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursive(it) }
            }
            val deleted = file.delete()
            Log.d(TAG, "🗑️ ${if (deleted) "✅" else "❌"} Deletado: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar ${file?.absolutePath}: ${e.message}")
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