package com.ironindex.app

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Non-breaking spaces, so a short phrase such as a date moves to the next line whole instead of splitting. */
fun String.keepTogether(): String = replace(' ', ' ')

/**
 * A label or short value that always stays on one line: on a narrow screen or with a large text setting it
 * shrinks (down to [minSize]) instead of wrapping or breaking a word ("Equipme / nt"). Use it for button
 * labels, field labels, chip labels and stat numbers; sentences should keep wrapping with Text.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    minSize: TextUnit = 9.sp,
) {
    val merged = style.merge(TextStyle(color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }, fontSize = fontSize, fontWeight = fontWeight))
    val max = merged.fontSize.takeIf { it.isSpecified } ?: 14.sp
    BasicText(text, modifier, style = merged, maxLines = 1, softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = if (minSize.value < max.value) minSize else max, maxFontSize = max, stepSize = 0.5.sp))
}
