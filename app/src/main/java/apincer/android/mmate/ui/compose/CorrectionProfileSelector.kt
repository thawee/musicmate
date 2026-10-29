package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CorrectionProfileChoice

internal val CalibrationOutput.label: String
    get() = if (this == CalibrationOutput.SPEAKER) "Speaker" else "Headphone / earphone"

/** Both output types share one library; a correction is only selectable for its intended output. */
@Composable
internal fun CorrectionProfileSelector(
    profiles: List<CorrectionProfileChoice>,
    selectedId: String?,
    selectedName: String?,
    output: CalibrationOutput,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    supportsOutput: (CalibrationOutput) -> Boolean = { it == output }
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MusicMateDesignTokens.Surfaces.BorderPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
                contentColor = MusicMateDesignTokens.Text.Primary,
                disabledContainerColor = MusicMateDesignTokens.Surfaces.Surface,
                disabledContentColor = MusicMateDesignTokens.Text.Muted
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("CORRECTION PROFILE", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                Text(selectedName ?: "No correction", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(androidx.compose.ui.res.painterResource(apincer.android.mmate.R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(24.dp),
                tint = MusicMateDesignTokens.Text.Muted)
        }
        val menuColors = MenuDefaults.itemColors(
            textColor = MusicMateDesignTokens.Text.Primary,
            disabledTextColor = MusicMateDesignTokens.Text.Muted
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
            shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(text = { Text("No correction") }, colors = menuColors,
                onClick = { expanded = false; onSelect(null) })
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(profile.name)
                            Text("${profile.output.label} · ${profile.method}${if (!supportsOutput(profile.output)) " · Connect a compatible output" else if (profile.id == selectedId) " · Selected" else ""}",
                                color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
                        }
                    },
                    enabled = supportsOutput(profile.output),
                    colors = menuColors,
                    onClick = { expanded = false; onSelect(profile.id) }
                )
            }
        }
    }
}
