package com.nemo.updater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemo.updater.ui.component.FloatingBottomBar
import com.nemo.updater.ui.navigation.NavTab
import com.nemo.updater.ui.screen.HomeScreen
import com.nemo.updater.ui.screen.KernelScreen
import com.nemo.updater.ui.screen.SettingsScreen
import com.nemo.updater.ui.theme.AppTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { MainScreen() } }
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = color)
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MainScreen() {
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally { width -> dir * width / 4 } + fadeIn())
                            .togetherWith(slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
                    },
                    label = "TabContent",
                ) { tab ->
                    when (tab) {
                        NavTab.HOME -> HomeScreen()
                        NavTab.KERNEL -> KernelScreen()
                        NavTab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }

        // 底栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                modifier = Modifier,
                selectedIndex = currentTab.ordinal,
                onSelected = { currentTab = NavTab.entries[it] },
                backdrop = backdrop,
                tabsCount = 3,
                isBlurEnabled = true,
            ) {
                NavTab.entries.forEach { tab ->
                    TabItem(tab.label, tab.icon, currentTab == tab) { currentTab = tab }
                }
            }
        }
    }
}
