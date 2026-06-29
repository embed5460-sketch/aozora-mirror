package app.meisaku.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.meisaku.reader.ui.MeisakuApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Graph.init(applicationContext)
        // 冷启动开屏广告（仅一次；premium 用户跳过；广告未就绪超时则放弃，不阻塞）。
        Graph.ads.showAppOpenOnColdStart(this)
        setContent { MeisakuApp() }
    }

    override fun onDestroy() {
        Graph.ads.detachActivity(this)
        super.onDestroy()
    }
}
