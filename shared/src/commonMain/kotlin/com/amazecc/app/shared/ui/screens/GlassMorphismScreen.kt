package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme
import kotlin.math.sin

private val GlassWhite = Color.White.copy(alpha = 0.12f)
private val GlassStroke = Color.White.copy(alpha = 0.25f)
private val GlassShadow = Color.Black.copy(alpha = 0.15f)

private val GradientStart = Color(0xFFFF6B6B)
private val GradientMid = Color(0xFFFFA07A)
private val GradientEnd = Color(0xFFFFD93D)

private val LeatherDark = Color(0xFF3E2723)
private val LeatherLight = Color(0xFF5D4037)
private val LeatherHighlight = Color(0xFF8D6E63)

private val MetalLight = Color(0xFFE0E0E0)
private val MetalDark = Color(0xFF757575)

@Composable
fun GlassMorphismScreen() {
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition()
    val shimmerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(GradientStart, GradientMid, GradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
    ) {
        AnimatedFloatingBubbles()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Glass Studio",
                style = AmazeTheme.typography.display.copy(
                    fontSize = 36.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                ).copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(2f, 4f), blurRadius = 8f)
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Skeuomorphic Glassmorphism",
                style = AmazeTheme.typography.body.copy(
                    color = Color.White.copy(alpha = 0.8f)
                ).copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 2f), blurRadius = 4f)
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LeatherAvatar()
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alexander Pierce",
                            style = AmazeTheme.typography.subheading.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), blurRadius = 2f)
                            )
                        )
                        Text(
                            text = "Product Designer",
                            style = AmazeTheme.typography.caption.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 1f), blurRadius = 2f)
                            )
                        )
                    }
                    SkeuoBadge(text = "PRO")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = " Portfolio",
                    style = AmazeTheme.typography.heading.copy(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), blurRadius = 4f)
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PortfolioStat("Projects", "24", Icons.Rounded.Folder)
                    PortfolioStat("Clients", "18", Icons.Rounded.Person)
                    PortfolioStat("Awards", "7", Icons.Rounded.Star)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Recent Work",
                    style = AmazeTheme.typography.subheading.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), blurRadius = 4f)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                WorkItem(
                    title = "Nebula Dashboard",
                    subtitle = "UI/UX Design · 2025",
                    color = Color(0xFF6C63FF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                WorkItem(
                    title = "EcoTrack Mobile",
                    subtitle = "App Design · 2025",
                    color = Color(0xFF00BFA5)
                )
                Spacer(modifier = Modifier.height(8.dp))
                WorkItem(
                    title = "PixelForge Studio",
                    subtitle = "Brand Identity · 2024",
                    color = Color(0xFFFF6B6B)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = " Skills",
                    style = AmazeTheme.typography.subheading.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), blurRadius = 4f)
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkillBar("UI/UX Design", 0.95f, Color(0xFF6C63FF))
                    SkillBar("Motion Design", 0.85f, Color(0xFF00BFA5))
                    SkillBar("3D Modeling", 0.70f, Color(0xFFFF6B6B))
                    SkillBar("Frontend Dev", 0.80f, Color(0xFFFFD93D))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = "Message",
                    icon = Icons.AutoMirrored.Rounded.Send,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Hire Me",
                    icon = Icons.Rounded.ThumbUp,
                    modifier = Modifier.weight(1f),
                    variant = GlassButtonVariant.PRIMARY
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "© 2025 Alexander Pierce",
                style = AmazeTheme.typography.caption.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 1f), blurRadius = 2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnimatedFloatingBubbles() {
    val infiniteTransition = rememberInfiniteTransition()

    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse)
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse)
    )
    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val waveOffset1 = sin(offset1 * kotlin.math.PI * 2).toFloat() * 40f
        val waveOffset2 = sin(offset2 * kotlin.math.PI * 2).toFloat() * 30f
        val waveOffset3 = sin(offset3 * kotlin.math.PI * 2).toFloat() * 50f

        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = 160f,
            center = Offset(size.width * 0.8f + waveOffset1, size.height * 0.2f + waveOffset1)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = 220f,
            center = Offset(size.width * 0.15f + waveOffset2, size.height * 0.5f + waveOffset2)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = 120f,
            center = Offset(size.width * 0.5f + waveOffset3, size.height * 0.8f + waveOffset3)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.04f),
            radius = 80f,
            center = Offset(size.width * 0.9f + waveOffset2, size.height * 0.7f + waveOffset2)
        )
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, GlassStroke, RoundedCornerShape(24.dp))
            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = GlassShadow, spotColor = GlassShadow)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun LeatherAvatar() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(LeatherLight, LeatherDark)
                )
            )
            .border(2.dp, LeatherHighlight.copy(alpha = 0.6f), CircleShape)
            .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2
            for (i in 0 until 12) {
                val angle = (i * 30f) * (kotlin.math.PI.toFloat() / 180f)
                val startX = cx + (r * 0.3f) * kotlin.math.cos(angle)
                val startY = cy + (r * 0.3f) * kotlin.math.sin(angle)
                val endX = cx + (r * 0.85f) * kotlin.math.cos(angle)
                val endY = cy + (r * 0.85f) * kotlin.math.sin(angle)
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.5f
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = r * 0.15f,
                center = Offset(cx, cy)
            )
        }
        Text(
            text = "AP",
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), blurRadius = 2f)
            )
        )
    }
}

@Composable
private fun SkeuoBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Color(0xFFFFA500).copy(alpha = 0.5f), spotColor = Color(0xFFFFA500).copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color(0xFF3E2723),
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                shadow = Shadow(Color.White.copy(alpha = 0.3f), Offset(0f, 1f), blurRadius = 1f)
            )
        )
    }
}

@Composable
private fun PortfolioStat(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = AmazeTheme.typography.subheading.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), blurRadius = 4f)
            )
        )
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(
                color = Color.White.copy(alpha = 0.7f),
                shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 1f), blurRadius = 2f)
            )
        )
    }
}

@Composable
private fun WorkItem(title: String, subtitle: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.3f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AmazeTheme.typography.body.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 1f), blurRadius = 2f)
                )
            )
            Text(
                text = subtitle,
                style = AmazeTheme.typography.caption.copy(
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SkillBar(label: String, progress: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(color, color.copy(alpha = 0.6f)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, 0f)
                        )
                    )
                    .then(
                        if (progress > 0.5f) {
                            Modifier.shadow(2.dp, RoundedCornerShape(4.dp), ambientColor = color.copy(alpha = 0.4f), spotColor = color.copy(alpha = 0.2f))
                        } else Modifier
                    )
            )
        }
    }
}

enum class GlassButtonVariant { PRIMARY, SECONDARY }

@Composable
private fun GlassButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.SECONDARY
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale: Float by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f)
    val elevation: Float by animateFloatAsState(targetValue = if (isPressed) 2f else 8f)

    val bgBrush = when (variant) {
        GlassButtonVariant.PRIMARY -> Brush.horizontalGradient(
            colors = listOf(Color(0xFF6C63FF), Color(0xFF8B5CF6))
        )
        GlassButtonVariant.SECONDARY -> Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.08f))
        )
    }

    val borderColor = when (variant) {
        GlassButtonVariant.PRIMARY -> Color.White.copy(alpha = 0.3f)
        GlassButtonVariant.SECONDARY -> Color.White.copy(alpha = 0.2f)
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = Color(0xFF6C63FF).copy(alpha = if (variant == GlassButtonVariant.PRIMARY) 0.4f else 0.1f), spotColor = Color(0xFF6C63FF).copy(alpha = if (variant == GlassButtonVariant.PRIMARY) 0.2f else 0.05f))
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { }
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), blurRadius = 2f)
                )
            )
        }
    }
}
