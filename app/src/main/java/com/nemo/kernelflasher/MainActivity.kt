package com.nemo.kernelflasher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nemo.kernelflasher.ui.navigation.FloatingNavigationBar
import com.nemo.kernelflasher.ui.navigation.NavTab
import com.nemo.kernelflasher.ui.screen.HomeScreen
import com.nemo.kernelflasher.ui.screen.KernelScreen
import com.nemo.kernelflasher.ui.screen.SettingsScreen
import com.nemo.kernelflasher.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    var currentTab by remember { mutableStateOf(NavTab.HOME) }

    Scaffold(
        bottomBar = {
            FloatingNavigationBar(
                selectedTab = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { width -> direction * width / 4 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -direction * width / 4 } + fadeOut())
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
}
