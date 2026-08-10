package net.kdt.pojavlaunch.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How every sheet in the launcher opens: a heading, and one line under it.
 *
 * The hint is not decoration. A sheet is reached by a gesture and closed by one, and the second
 * line is the only place a non-obvious one gets named. It lived private inside the home sheets
 * until the Add sheet was written without it and came out looking like a different application.
 */
@Composable
fun AppSheetHeading(title: String, hint: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
