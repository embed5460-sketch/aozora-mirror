package app.meisaku.reader.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import app.meisaku.reader.Graph
import app.meisaku.reader.data.Config

/** 从 Compose 的 Context 解包出宿主 Activity（启动内购流程需要）。 */
private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "読書設定",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
            ReaderSettingsPanel()
            HorizontalDivider()

            // プレミアム（Play Billing 一次性内购）
            val q by Graph.quota.state
            val billing by Graph.billing.state
            val context = LocalContext.current
            val activity = context.findActivity()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("プレミアム", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (q.premium) "ふりがな無制限・広告なし（予定）"
                    else "無料版：ふりがなは1日 ${app.meisaku.reader.data.FuriganaQuota.FREE_DAILY} 作品まで",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                when {
                    q.premium || billing.owned ->
                        Text("プレミアム 有効", style = MaterialTheme.typography.bodyMedium)
                    billing.available ->
                        Button(
                            onClick = { activity?.let { Graph.billing.launchPurchase(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("プレミアムを購入" + (billing.price?.let { " · $it" } ?: ""))
                        }
                    else ->
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("プレミアム（準備中）")
                        }
                }
            }
            HorizontalDivider()

            // デバッグ用：商品未公開の間、手動でプレミアムを切り替えてふりがな無制限を確認する。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("プレミアム（デバッグ切替）", style = MaterialTheme.typography.labelLarge)
                Switch(checked = q.premium, onCheckedChange = { Graph.quota.setPremium(it) })
            }
            HorizontalDivider()

            Text(
                "本文データは青空文庫（著作権消滅作品）に基づきます。本アプリは青空文庫公式とは無関係です。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )

            // 法務リンク（站点A 部署 + LEGAL_BASE 回填後に表示）
            if (Config.hasLegal()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    TextButton(onClick = { context.openUrl(Config.PRIVACY_URL) }) {
                        Text("プライバシーポリシー")
                    }
                    TextButton(onClick = { context.openUrl(Config.TERMS_URL) }) {
                        Text("利用規約")
                    }
                }
            }
        }
    }
}

/** 外部ブラウザで URL を開く。 */
private fun android.content.Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
