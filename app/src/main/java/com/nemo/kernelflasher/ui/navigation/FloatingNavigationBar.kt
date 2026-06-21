package com.nemo.kernelflasher.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Apple 风格悬浮底栏，使用 Backdrop API 实现液态玻璃效果
 */
@Composable
fun FloatingNavigationBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true,
) {
    val isInLightTheme = MiuixTheme.colorScheme.surface.luminance() >= 0.5f
    val containerColor = if (isBlurEnabled) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val accentColor = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onSurface

    val backdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = {
                    if (isBlurEnabled) {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (isBlurEnabled) 1f else 0f)
                },
                shadow = {
                    Shadow.Default.copy(
                        alpha = if (isInLightTheme) 0.1f else 0.2f,
                    )
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavTab.entries.forEach { tab ->
                FloatingNavItem(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    icon = tab.icon,
                    label = tab.label,
                    accentColor = accentColor,
                    contentColor = contentColor,
                )
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    accentColor: Color,
    contentColor: Color,
) {
    val itemColor = if (selected) accentColor else contentColor.copy(alpha = 0.6f)
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.15f else 0f,
        animationSpec = tween(200),
        label = "itemBg",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(accentColor.copy(alpha = bgAlpha))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = itemColor,
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = itemColor,
            )
        }
    }
}
