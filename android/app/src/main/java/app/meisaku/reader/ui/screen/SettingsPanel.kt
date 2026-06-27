package app.meisaku.reader.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.meisaku.reader.Graph
import app.meisaku.reader.data.ReaderSettings

/** 阅读设置面板，复用于设置页与阅读器内的快捷面板。 */
@Composable
fun ReaderSettingsPanel(modifier: Modifier = Modifier) {
    val s by Graph.settings.state
    val store = Graph.settings

    Column(modifier.padding(16.dp)) {
        Text("文字サイズ  ${s.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = s.fontSizeSp,
            onValueChange = { store.setFontSize(it) },
            valueRange = ReaderSettings.MIN_FONT..ReaderSettings.MAX_FONT,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("行間  ${"%.1f".format(s.lineSpacingMult)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = s.lineSpacingMult,
            onValueChange = { store.setLineSpacing(it) },
            valueRange = ReaderSettings.MIN_SPACING..ReaderSettings.MAX_SPACING,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("夜間モード", style = MaterialTheme.typography.labelLarge)
            Switch(checked = s.night, onCheckedChange = { store.setNight(it) })
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ふりがな（自動）", style = MaterialTheme.typography.labelLarge)
                Text(
                    "本文の漢字に読み仮名を自動表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.autoFurigana, onCheckedChange = { store.setAutoFurigana(it) })
        }
    }
}
