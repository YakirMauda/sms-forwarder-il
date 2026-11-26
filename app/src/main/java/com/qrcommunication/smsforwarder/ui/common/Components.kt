package com.qrcommunication.smsforwarder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.qrcommunication.smsforwarder.ui.theme.FailBorderDark
import com.qrcommunication.smsforwarder.ui.theme.FailBorderLight
import com.qrcommunication.smsforwarder.ui.theme.FailContainerDark
import com.qrcommunication.smsforwarder.ui.theme.FailContainerLight
import com.qrcommunication.smsforwarder.ui.theme.FailIconDark
import com.qrcommunication.smsforwarder.ui.theme.FailIconLight
import com.qrcommunication.smsforwarder.ui.theme.OnFailContainerDark
import com.qrcommunication.smsforwarder.ui.theme.OnFailContainerLight
import com.qrcommunication.smsforwarder.ui.theme.OnSuccessContainerDark
import com.qrcommunication.smsforwarder.ui.theme.OnSuccessContainerLight
import com.qrcommunication.smsforwarder.ui.theme.SuccessBorderDark
import com.qrcommunication.smsforwarder.ui.theme.SuccessBorderLight
import com.qrcommunication.smsforwarder.ui.theme.SuccessContainerDark
import com.qrcommunication.smsforwarder.ui.theme.SuccessContainerLight
import com.qrcommunication.smsforwarder.ui.theme.SuccessIconDark
import com.qrcommunication.smsforwarder.ui.theme.SuccessIconLight

val CardShape = RoundedCornerShape(22.dp)

/** White rounded card with a soft shadow, used for every block (rules, history rows, settings). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** Small colored chip, e.g. a status in History. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** Light-grey tag showing one condition: "equals **Leumi**". */
@Composable
fun ConditionTag(operator: String, value: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(operator, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Green "will forward" / red "won't forward" box for the rule tester.
 * Not color alone: it also has a ✓/✗ icon and words.
 */
@Composable
fun ResultBanner(success: Boolean, title: String, subtitle: String?) {
    val dark = isSystemInDarkTheme()
    val container = when { success && dark -> SuccessContainerDark; success -> SuccessContainerLight; dark -> FailContainerDark; else -> FailContainerLight }
    val border = when { success && dark -> SuccessBorderDark; success -> SuccessBorderLight; dark -> FailBorderDark; else -> FailBorderLight }
    val icon = when { success && dark -> SuccessIconDark; success -> SuccessIconLight; dark -> FailIconDark; else -> FailIconLight }
    val text = when { success && dark -> OnSuccessContainerDark; success -> OnSuccessContainerLight; dark -> OnFailContainerDark; else -> OnFailContainerLight }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(container, RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(icon, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (success) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(title, color = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Current text style, forced left-to-right: phone numbers, regexes and URLs stay LTR even in Hebrew. */
val LtrText: TextStyle
    @Composable @ReadOnlyComposable
    get() = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
