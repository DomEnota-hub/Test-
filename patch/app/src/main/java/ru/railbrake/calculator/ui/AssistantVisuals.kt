package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.min

/**
 * Reusable native Compose styling for the Assistant visual concept, not screenshots-as-buttons.
 * Text, hit targets and scroll behavior remain native and can be accessibility-tested.
 */
internal enum class TechnicalMotif { TRAIN, CALCULATOR, EQUIPMENT, CIRCUIT, NOTES, ASSISTANT }

internal val DesignBlue = Color(0xFF4EA4FF)
internal val DesignAmber = Color(0xFFE6AC4F)
internal val DesignRose = Color(0xFFE77583)
internal val DesignCyan = Color(0xFF50C9D1)
internal val DesignViolet = Color(0xFFAB8CF4)

/** A moderate corner cut: engineering visual language without aggressive HUD borders. */
internal class TechnicalCornerShape(private val cut: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = min(cut.value * density.density, min(size.width, size.height) * 0.20f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
internal fun TechnicalPanel(
    accent: Color,
    motif: TechnicalMotif,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.38f
    val shape = TechnicalCornerShape(if (prominent) 12.dp else 9.dp)
    val base = if (dark) Color(0xFF101C2D) else Color(0xFFFCFDFE)
    val tint = lerp(base, accent, if (dark) { if (prominent) 0.20f else 0.115f } else 0.085f)
    val outline = accent.copy(alpha = if (dark) { if (prominent) 0.78f else 0.43f } else { if (prominent) 0.68f else 0.36f })
    Box(
        modifier = modifier
            .background(Brush.linearGradient(listOf(tint, base, if (dark) Color(0xFF0B1726) else Color(0xFFF7FAFD))), shape)
            .border(BorderStroke(if (prominent) 1.4.dp else 1.dp, outline), shape)
            .clip(shape)
    ) {
        TechnicalLineArt(motif = motif, accent = accent, modifier = Modifier.fillMaxSize(), subtle = !prominent)
        content()
    }
}

/** Procedural blueprint texture: vector, low-cost and independent of screen resolution. */
@Composable
internal fun TechnicalLineArt(
    motif: TechnicalMotif,
    accent: Color,
    modifier: Modifier = Modifier,
    subtle: Boolean = false
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.38f
    val a = if (dark) { if (subtle) 0.19f else 0.31f } else { if (subtle) 0.12f else 0.21f }
    Canvas(modifier) {
        val line = if (dark) accent.copy(alpha = a) else accent.copy(alpha = a * 0.95f)
        val fine = accent.copy(alpha = a * 0.23f)
        val w = size.width
        val h = size.height
        val unit = 26.dp.toPx()
        var x = unit
        while (x < w) {
            drawLine(fine, Offset(x, 0f), Offset(x, h), 0.7f)
            x += unit
        }
        var y = unit
        while (y < h) {
            drawLine(fine, Offset(0f, y), Offset(w, y), 0.7f)
            y += unit
        }
        fun p(xx: Float, yy: Float) = Offset(w * xx, h * yy)
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float, scale: Float = 1f) =
            drawLine(line, p(x1, y1), p(x2, y2), 1.45.dp.toPx() * scale)
        fun rect(x: Float, y: Float, ww: Float, hh: Float) {
            drawRoundRect(
                color = line, topLeft = p(x,y), size = Size(w * ww,h * hh),
                cornerRadius = CornerRadius(7.dp.toPx(),7.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        // Right-hand decoration only; left is reserved for actual readable UI text.
        when (motif) {
            TechnicalMotif.TRAIN, TechnicalMotif.ASSISTANT -> {
                val begin = if (motif == TechnicalMotif.ASSISTANT) 0.62f else 0.64f
                seg(begin,0.60f,0.95f,0.60f)
                seg(begin+0.045f,0.29f,0.92f,0.29f)
                seg(begin+0.045f,0.29f,begin+0.045f,0.75f)
                seg(0.92f,0.29f,0.96f,0.42f)
                seg(0.96f,0.42f,0.96f,0.74f)
                seg(begin+0.04f,0.75f,0.96f,0.74f)
                seg(begin+0.08f,0.35f,0.86f,0.35f)
                seg(0.86f,0.35f,0.90f,0.49f)
                seg(0.90f,0.49f,begin+0.08f,0.49f)
                seg(begin+0.08f,0.49f,begin+0.08f,0.35f)
                // Windshield split and roof pantograph, purely decorative.
                seg(0.79f,0.35f,0.79f,0.49f)
                seg(0.79f,0.29f,0.84f,0.20f)
                seg(0.84f,0.20f,0.90f,0.29f)
                seg(0.73f,0.60f,0.73f,0.67f)
                seg(0.87f,0.60f,0.87f,0.67f)
                seg(0.68f,0.80f,0.93f,0.80f)
            }
            TechnicalMotif.CALCULATOR -> {
                rect(0.70f,0.12f,0.23f,0.77f)
                rect(0.73f,0.18f,0.17f,0.16f)
                for (i in 0..2) {
                    for (j in 0..2) {
                        rect(0.735f + j*0.060f,0.43f + i*0.14f,0.040f,0.085f)
                    }
                }
                // Blueprint guide arcs / rails behind the calculator.
                seg(0.60f,0.89f,0.99f,0.89f)
                seg(0.64f,0.10f,0.64f,0.88f)
            }
            TechnicalMotif.EQUIPMENT -> {
                rect(0.66f,0.27f,0.29f,0.47f)
                val center = p(0.805f,0.505f)
                val radius = min(w*0.10f,h*0.24f)
                drawCircle(line,radius,center,style=Stroke(width=1.6.dp.toPx()))
                drawCircle(line,radius*0.50f,center,style=Stroke(width=1.2.dp.toPx()))
                drawCircle(line,radius*0.16f,center,style=Stroke(width=1.2.dp.toPx()))
                seg(0.67f,0.20f,0.95f,0.20f)
                seg(0.67f,0.80f,0.95f,0.80f)
            }
            TechnicalMotif.CIRCUIT -> {
                // Electrical schematic symbolism; illustrative, not an operational diagram.
                seg(0.64f,0.20f,0.64f,0.78f)
                seg(0.84f,0.20f,0.84f,0.78f)
                seg(0.64f,0.34f,0.92f,0.34f)
                seg(0.64f,0.66f,0.92f,0.66f)
                seg(0.74f,0.27f,0.74f,0.42f)
                seg(0.78f,0.27f,0.78f,0.42f)
                seg(0.74f,0.56f,0.80f,0.56f)
                seg(0.80f,0.56f,0.80f,0.74f)
                for (xx in listOf(0.64f,0.84f)) {
                    for (yy in listOf(0.34f,0.66f)) drawCircle(line,2.5.dp.toPx(),p(xx,yy))
                }
            }
            TechnicalMotif.NOTES -> {
                rect(0.71f,0.16f,0.20f,0.68f)
                for (i in 0..4) seg(0.735f,0.28f+i*0.11f,0.87f,0.28f+i*0.11f)
                seg(0.68f,0.22f,0.68f,0.78f)
                seg(0.66f,0.25f,0.71f,0.25f)
            }
        }
    }
}

/** Consistent vector icons replacing non-portable unicode and emoji glyphs. */
@Composable
internal fun TechnicalIcon(
    motif: TechnicalMotif,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val surface = tint.copy(alpha=0.13f)
    val shape = RoundedCornerShape(11.dp)
    Box(modifier.size(42.dp).background(surface,shape), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.size(25.dp)) {
            val stroke = 2.dp.toPx()
            val w=size.width; val h=size.height
            fun p(x:Float,y:Float)=Offset(w*x,h*y)
            fun line(x:Float,y:Float,xx:Float,yy:Float) = drawLine(tint,p(x,y),p(xx,yy),stroke)
            fun outline(x:Float,y:Float,ww:Float,hh:Float){
                drawRoundRect(tint,p(x,y),Size(w*ww,h*hh),CornerRadius(2.dp.toPx()),style=Stroke(stroke))
            }
            when(motif){
                TechnicalMotif.CALCULATOR -> {
                    outline(.22f,.05f,.56f,.90f)
                    outline(.30f,.17f,.40f,.17f)
                    for (r in 0..1) for(c in 0..2){
                        drawCircle(tint,stroke*1.2f,p(.34f+c*.16f,.52f+r*.18f))
                    }
                }
                TechnicalMotif.EQUIPMENT -> {
                    line(.08f,.52f,.27f,.52f);line(.27f,.52f,.38f,.25f)
                    line(.38f,.25f,.54f,.78f);line(.54f,.78f,.66f,.40f)
                    line(.66f,.40f,.75f,.52f);line(.75f,.52f,.92f,.52f)
                }
                TechnicalMotif.CIRCUIT -> {
                    line(.50f,.12f,.50f,.89f)
                    line(.50f,.12f,.10f,.05f); line(.50f,.12f,.90f,.05f)
                    line(.10f,.05f,.10f,.81f); line(.90f,.05f,.90f,.81f)
                    line(.10f,.81f,.50f,.94f); line(.50f,.94f,.90f,.81f)
                    line(.50f,.12f,.50f,.89f)
                }
                TechnicalMotif.NOTES -> {
                    outline(.21f,.08f,.59f,.84f)
                    line(.33f,.32f,.68f,.32f)
                    line(.33f,.50f,.68f,.50f)
                    line(.33f,.68f,.57f,.68f)
                }
                TechnicalMotif.TRAIN -> {
                    outline(.22f,.08f,.56f,.72f)
                    outline(.32f,.19f,.36f,.25f)
                    line(.22f,.58f,.78f,.58f)
                    line(.26f,.92f,.41f,.75f);line(.74f,.92f,.59f,.75f)
                }
                TechnicalMotif.ASSISTANT -> {
                    outline(.12f,.12f,.76f,.62f)
                    line(.30f,.74f,.25f,.91f); line(.25f,.91f,.50f,.73f)
                    drawCircle(tint,stroke*.75f,p(.36f,.43f))
                    drawCircle(tint,stroke*.75f,p(.64f,.43f))
                }
            }
        }
    }
}
