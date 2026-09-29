package com.ironindex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Health Connect opens this from its permission screen to show how IronIndex uses the data it's allowed to write. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = IronIndexColors) { HealthPrivacy(onDone = ::finish) } }
    }
}

@Composable
fun HealthPrivacy(onDone: () -> Unit) {
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("IronIndex and Health Connect", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("When you turn on sharing (Train → Backup), IronIndex writes each finished session to Health Connect so apps you choose, such as Discipline or Samsung Health, can see that you trained.")
            Text("What's shared", fontWeight = FontWeight.Bold)
            Text("• The session's name, start and end time, and type (strength training)\n• A short note: how many exercises and sets you logged", color = Muted)
            Text("What isn't", fontWeight = FontWeight.Bold)
            Text("• Your sets, weights, machines, setup or notes\n• Anything about your training partners\n• IronIndex never reads data from Health Connect", color = Muted)
            Text("IronIndex keeps everything else on this phone. There's no IronIndex account or server. Turn sharing off any time; sessions already shared stay in Health Connect until you remove them there.", color = Muted)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Got it") }
        }
    }
}
