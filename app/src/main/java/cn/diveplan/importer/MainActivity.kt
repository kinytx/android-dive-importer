package cn.diveplan.importer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.data.ApiKeyStore
import cn.diveplan.importer.ui.PlaceholderHomeScreen
import cn.diveplan.importer.ui.bind.BindScreen
import cn.diveplan.importer.ui.bind.BindViewModel
import cn.diveplan.importer.ui.bind.extractBindCode
import cn.diveplan.importer.ui.theme.DivePlanImporterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主 Activity — 单 Activity Compose 架构。
 *
 * 路由策略（P1）：
 *   - 启动时根据 [ApiKeyStore.apiKey] 决定首屏：
 *       - 有 key → 占位 Home（P2 替换成 ScanScreen）
 *       - 无 key → BindScreen
 *   - 处理 deep link `diveplan://ble-probe/bind?code=123456`：
 *       - onCreate 的 intent / onNewIntent 都会被路由到 [handleIncomingIntent]
 *       - 提取出 6 位码后塞进 [BindViewModel.submitCode]，UI 自动跳到「绑定中」
 *
 * P2 时这里改成 NavHost；P1 阶段两屏切换够用了。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiKeyStore: ApiKeyStore

    /** Activity 重建（如旋转 / 进程恢复）后还能在 BindScreen 拿到同一个 VM */
    private val bindViewModel: BindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 冷启动时 intent 可能带 deep link
        handleIncomingIntent(intent)

        setContent {
            DivePlanImporterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        val code = extractBindCode(data) ?: return
        // 把绑定码推给 ViewModel；UI 层 LaunchedEffect 看到 phase=Submitting 自动跳到「绑定中」
        bindViewModel.submitCode(code)
    }

    @Composable
    private fun RootScreen() {
        val apiKey by apiKeyStore.apiKey.collectAsStateWithLifecycle()
        if (apiKey.isNullOrBlank()) {
            // 未绑定 → BindScreen（ViewModel 用 by viewModels()，跟 deep link 共享同一实例）
            BindScreen(
                viewModel = bindViewModel,
                onBound = { /* RootScreen 会自动 recompose，因为 apiKey 变了 */ },
            )
        } else {
            // 已绑定 → 暂时 PlaceholderHome；P2 替换成 ScanScreen
            PlaceholderHomeScreen()
        }

        // 冷启动如果 intent 已经把 code 喂给 ViewModel 进 Submitting，
        // 但 apiKey 还没 update（IO 是异步的），UI 会先短暂显示 BindScreen 的 Submitting 状态，
        // consume 成功后 apiKey 变化触发 recompose 自动切到 Home。
    }
}
