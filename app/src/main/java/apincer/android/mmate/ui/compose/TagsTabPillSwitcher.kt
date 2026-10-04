package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.viewpager2.widget.ViewPager2
import apincer.android.mmate.R

/**
 * Fluid Audiophile Glass Pill Tab Switcher for TagsActivity
 * Implements 1:1 real-time drag tracking between "Song Info" and "Tech Info"
 * with champagne-gold ambient glow and tactile haptics.
 */
@Composable
fun TagsTabPillSwitcher(
    pageFraction: Float,
    onTabSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clampedFraction = pageFraction.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .border(0.75.dp, Color(0x24FFFFFF), RoundedCornerShape(20.dp))
            .padding(3.dp)
    ) {
        val tabWidth = maxWidth / 2
        val indicatorOffset = tabWidth * clampedFraction

        // 1. Fluid Sliding Indicator Pill
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x38FFD700), // Champagne gold ambient glow
                            Color(0x1CFFD700)  // Deep gold base
                        )
                    )
                )
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66FFD700), // Hairline metallic highlight
                            Color(0x26FFD700)
                        )
                    ),
                    RoundedCornerShape(17.dp)
                )
        )

        // 2. Interactive Tab Labels
        Row(modifier = Modifier.fillMaxSize()) {
            // Tab 0: Song Info
            val isSongInfoSelected = clampedFraction < 0.5f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(0)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_library_music_24),
                        contentDescription = null,
                        tint = if (isSongInfoSelected) Color(0xFFFFD700) else Color(0x66FFFFFF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Song Info",
                        color = if (isSongInfoSelected) Color(0xFFFFD700) else Color(0x99FFFFFF),
                        fontSize = 12.5.sp,
                        fontWeight = if (isSongInfoSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            // Tab 1: Tech Info
            val isTechInfoSelected = clampedFraction >= 0.5f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(1)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_equalizer_24),
                        contentDescription = null,
                        tint = if (isTechInfoSelected) Color(0xFFFFD700) else Color(0x66FFFFFF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tech Info",
                        color = if (isTechInfoSelected) Color(0xFFFFD700) else Color(0x99FFFFFF),
                        fontSize = 12.5.sp,
                        fontWeight = if (isTechInfoSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

/**
 * Interop bridge to embed TagsTabPillSwitcher into TagsActivity
 */
object TagsTabPillBridge {
    @JvmStatic
    fun setup(composeView: ComposeView, viewPager: ViewPager2) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            MusicMateTheme {
                val pageFractionState = remember { mutableFloatStateOf(viewPager.currentItem.toFloat()) }

                DisposableEffect(viewPager) {
                    val callback = object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageScrolled(
                            position: Int,
                            positionOffset: Float,
                            positionOffsetPixels: Int
                        ) {
                            pageFractionState.floatValue = position.toFloat() + positionOffset
                        }

                        override fun onPageSelected(position: Int) {
                            pageFractionState.floatValue = position.toFloat()
                        }
                    }
                    viewPager.registerOnPageChangeCallback(callback)
                    onDispose {
                        viewPager.unregisterOnPageChangeCallback(callback)
                    }
                }

                TagsTabPillSwitcher(
                    pageFraction = pageFractionState.floatValue,
                    onTabSelected = { tabIndex ->
                        viewPager.setCurrentItem(tabIndex, true)
                    }
                )
            }
        }
    }
}
