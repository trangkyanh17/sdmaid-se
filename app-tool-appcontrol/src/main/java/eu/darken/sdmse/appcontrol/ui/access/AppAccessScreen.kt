package eu.darken.sdmse.appcontrol.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppAccessScreenHost(
    route: AppAccessRoute,
    vm: AppAccessViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    AppAccessScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun AppAccessScreen(
    stateSource: StateFlow<AppAccessViewModel.State> =
        MutableStateFlow(AppAccessViewModel.State.Loading),
    onNavigateUp: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    if (state is AppAccessViewModel.State.NotFound) {
        LaunchedEffect(Unit) { onNavigateUp() }
    }

    val title = when (val current = state) {
        is AppAccessViewModel.State.Ready -> current.snapshot.installId.pkgId.name
        AppAccessViewModel.State.Loading,
        AppAccessViewModel.State.NotFound -> stringResource(R.string.appcontrol_access_title)
    }

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(eu.darken.sdmse.common.R.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
    ) { paddingValues ->
        when (val current = state) {
            AppAccessViewModel.State.Loading -> ProgressOverlay(
                data = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) { }

            AppAccessViewModel.State.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            is AppAccessViewModel.State.Ready -> PermissionList(
                snapshot = current.snapshot,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun PermissionList(
    snapshot: AppPermissionSnapshot,
    contentPadding: PaddingValues,
) {
    val grantedCount = snapshot.permissions.count { it.granted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item("summary") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.appcontrol_access_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.appcontrol_access_summary,
                        snapshot.permissions.size,
                        grantedCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (snapshot.permissions.isEmpty()) {
            item("empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.appcontrol_access_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(snapshot.permissions, key = { it.name }) { permission ->
                PermissionRow(permission)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: AppPermissionSnapshot.Entry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = permission.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                if (permission.granted) {
                    R.string.appcontrol_access_granted
                } else {
                    R.string.appcontrol_access_denied
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
