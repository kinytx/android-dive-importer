package cn.diveplan.importer.ui.bind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 账号绑定主屏（首次启动 / 用户主动解绑后落到这）。
 *
 * 设计：顶部 Tab「输入码 / 扫码」二选一；底部根据 ViewModel state 显示按钮 / 错误 / Bound 反馈。
 *
 * @param onBound 绑定成功后 ViewModel 进 Bound 态，用户点「开始扫描设备」时调
 *                父层接管 navigation 跳到 P2 ScanScreen（P1 阶段只让用户回 Home 占位）
 */
@Composable
fun BindScreen(
    onBound: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BindViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableIntStateOf(0) }

    // 如果父层已经把 deepLinkCode 推给 ViewModel 并进 Submitting/Bound 状态，UI 自动反映
    LaunchedEffect(state.phase) {
        if (state.phase == BindPhase.Bound) {
            // 让父层有机会埋点 / 跳转；这里不主动跳，等用户点按钮
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "绑定账号",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "在小程序「我的 → 潜水电脑」生成绑定码后，扫描或输入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (state.phase == BindPhase.Bound) {
                BoundSuccessCard(
                    prefix = state.successPrefix.orEmpty(),
                    onContinue = {
                        viewModel.acknowledgeBound()
                        onBound()
                    },
                )
                return@Column
            }

            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("输入码") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("扫码") },
                )
            }
            Spacer(Modifier.height(24.dp))

            when (tabIndex) {
                0 -> InputCodePane(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onSubmit = { viewModel.submitCode() },
                )
                1 -> QrScanScreen(
                    onCodeDetected = { code -> viewModel.submitCode(code) },
                    onCancel = { tabIndex = 0 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun InputCodePane(
    state: BindUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CodeInputField(
            value = state.code,
            onValueChange = onCodeChange,
            onSubmit = onSubmit,
            enabled = state.phase != BindPhase.Submitting,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "6 位数字码 · 有效期 10 分钟",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = friendlyError(err),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = state.code.length == 6 && state.phase != BindPhase.Submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (state.phase) {
                    BindPhase.Submitting -> "绑定中…"
                    else                  -> "确认绑定"
                }
            )
        }
    }
}

@Composable
private fun BoundSuccessCard(prefix: String, onContinue: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "✅ 已绑定",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            if (prefix.isNotEmpty()) {
                Text(
                    text = "凭证前缀: $prefix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("开始扫描潜水电脑 →")
            }
            TextButton(onClick = onContinue) {
                Text("稍后再说")
            }
        }
    }
}

private fun friendlyError(err: BindError): String = when (err) {
    BindError.LengthMismatch -> "请输入完整的 6 位数字码"
    BindError.InvalidCode     -> "绑定码无效或已过期，请在小程序重新生成"
    is BindError.Network      -> "网络异常，请检查连接后重试（${err.detail}）"
    is BindError.Server       -> "服务器异常（${err.status}），请稍后重试"
}
