package ai.whatyousay.feature.onboarding

import ai.whatyousay.core.Language
import ai.whatyousay.core.Languages
import ai.whatyousay.design.WhatYouSayTheme
import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.PackState
import ai.whatyousay.engine.PackStatus
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::next,
        onSelectTier = viewModel::selectTier,
        onToggleLanguage = viewModel::toggleLanguage,
        onDownloadPack = viewModel::downloadPack,
        onDownloadAll = viewModel::downloadAll,
        onRemovePack = viewModel::removePack,
        onMicResult = viewModel::onMicPermissionResult,
        onFinish = {
            viewModel.finish()
            onFinish()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelectTier: (DeviceTier) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onDownloadPack: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onRemovePack: (String) -> Unit,
    onMicResult: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Set up What You Say") }) },
        bottomBar = {
            NavBar(
                step = state.step,
                continueEnabled = state.step != OnboardingStep.LANGUAGES || state.canContinueLanguages,
                onBack = onBack,
                onNext = onNext,
                onFinish = onFinish,
            )
        },
    ) { inset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .padding(horizontal = 16.dp),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.TIER -> TierStep(state, onSelectTier)
                OnboardingStep.LANGUAGES -> LanguageStep(state, onToggleLanguage)
                OnboardingStep.PACKS -> PacksStep(state, onDownloadPack, onDownloadAll, onRemovePack)
                OnboardingStep.PERMISSION -> PermissionStep(state, onMicResult)
            }
        }
    }
}

@Composable
private fun NavBar(
    step: OnboardingStep,
    continueEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != OnboardingStep.WELCOME) {
                TextButton(onClick = onBack) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (step == OnboardingStep.PERMISSION) {
                Button(onClick = onFinish) { Text("Start translating") }
            } else {
                Button(onClick = onNext, enabled = continueEnabled) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("Speech in, speech out.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Two people, two languages, one phone, hands-free. Everything runs on the device, so it works in airplane mode and nothing leaves the phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Next: pick a quality tier for your device, choose your languages, and install the model packs.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun TierStep(state: OnboardingUiState, onSelectTier: (DeviceTier) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("QUALITY TIER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Detected: ${tierName(state.detectedTier)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        DeviceTier.entries.forEach { tier ->
            TierRow(
                tier = tier,
                selected = tier == state.selectedTier,
                recommended = tier == state.detectedTier,
                onSelect = { onSelectTier(tier) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TierRow(tier: DeviceTier, selected: Boolean, recommended: Boolean, onSelect: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tierName(tier) + if (recommended) "  (recommended)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(tierBlurb(tier), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LanguageStep(state: OnboardingUiState, onToggle: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("LANGUAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${state.selectedLanguages.size} selected. Pick at least two.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.allLanguages.forEach { language ->
                val on = language.code in state.selectedLanguages
                FilterChip(
                    selected = on,
                    onClick = { onToggle(language.code) },
                    label = { Text(language.name) },
                )
            }
        }
    }
}

@Composable
private fun PacksStep(
    state: OnboardingUiState,
    onDownloadPack: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onRemovePack: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("MODEL PACKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("For ${tierName(state.selectedTier)}", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onDownloadAll, enabled = state.packs.any { it.state == PackState.ABSENT || it.state == PackState.FAILED }) {
                Text("Download all")
            }
        }
        LinearProgressIndicator(
            progress = { state.overallProgress },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        if (state.simulated) {
            Text(
                "Demo provisioning: packs are simulated with no network. Real packs install the same way once pack URLs are configured.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.packs) { status ->
                PackCard(status, onDownloadPack, onRemovePack)
            }
        }
    }
}

@Composable
private fun PackCard(status: PackStatus, onDownload: (String) -> Unit, onRemove: (String) -> Unit) {
    val pack = status.pack
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${pack.stage.name} . ${humanSize(pack.approxBytes)} . ${pack.quantization} . needs ${tierName(pack.minTier)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        when (status.state) {
            PackState.ABSENT -> Button(onClick = { onDownload(pack.id) }) { Text("Download") }
            PackState.QUEUED -> StatusText("Queued")
            PackState.DOWNLOADING -> {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                StatusText("Downloading ${(status.progress * 100).toInt()}%")
            }
            PackState.VERIFYING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                StatusText("Verifying sha256")
            }
            PackState.INSTALLED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatusText("Installed, sha256 verified", MaterialTheme.colorScheme.primary)
                        status.sha256?.let {
                            Text(
                                "sha256 ${it.take(16)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onRemove(pack.id) }) { Text("Remove") }
                }
            }
            PackState.FAILED -> {
                StatusText(status.error ?: "Download failed", MaterialTheme.colorScheme.primary)
                Button(onClick = { onDownload(pack.id) }) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PermissionStep(state: OnboardingUiState, onMicResult: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onMicResult)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("MICROPHONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "What You Say needs the microphone to hear speech. Audio is processed on the device and never recorded or uploaded.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        if (state.micGranted) {
            Text("Microphone access granted.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone") }
            Spacer(Modifier.height(8.dp))
            Text(
                "You can skip this and grant it later from the conversation screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun tierName(tier: DeviceTier): String = when (tier) {
    DeviceTier.LOW -> "Low"
    DeviceTier.MID -> "Mid"
    DeviceTier.FLAGSHIP -> "Flagship"
}

private fun tierBlurb(tier: DeviceTier): String = when (tier) {
    DeviceTier.LOW -> "Runs on 4 GB phones. Smallest packs, fastest install."
    DeviceTier.MID -> "6 to 12 GB RAM. Better translation quality."
    DeviceTier.FLAGSHIP -> "12 GB+ with NPU. Largest, highest quality models."
}

private fun humanSize(bytes: Long): String {
    val gb = bytes.toDouble() / 1_000_000_000.0
    val mb = bytes.toDouble() / 1_000_000.0
    return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", mb)
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPacksPreview() {
    WhatYouSayTheme {
        OnboardingContent(
            state = OnboardingUiState(
                step = OnboardingStep.PACKS,
                detectedTier = DeviceTier.MID,
                selectedTier = DeviceTier.MID,
                allLanguages = Languages.all,
                selectedLanguages = setOf("en", "es", "fr"),
                packs = ModelCatalog.defaultsFor(DeviceTier.MID).mapIndexed { i, pack ->
                    when (i) {
                        0 -> PackStatus(pack, PackState.INSTALLED, 1f, sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
                        1 -> PackStatus(pack, PackState.DOWNLOADING, 0.45f)
                        else -> PackStatus(pack, PackState.ABSENT)
                    }
                },
                overallProgress = 0.48f,
                simulated = true,
                micGranted = false,
            ),
            onBack = {},
            onNext = {},
            onSelectTier = {},
            onToggleLanguage = {},
            onDownloadPack = {},
            onDownloadAll = {},
            onRemovePack = {},
            onMicResult = {},
            onFinish = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingLanguagesPreview() {
    WhatYouSayTheme {
        OnboardingContent(
            state = OnboardingUiState(
                step = OnboardingStep.LANGUAGES,
                detectedTier = DeviceTier.MID,
                selectedTier = DeviceTier.MID,
                allLanguages = Languages.all,
                selectedLanguages = setOf("en", "es", "ja"),
                packs = emptyList(),
                overallProgress = 0f,
                simulated = true,
                micGranted = false,
            ),
            onBack = {},
            onNext = {},
            onSelectTier = {},
            onToggleLanguage = {},
            onDownloadPack = {},
            onDownloadAll = {},
            onRemovePack = {},
            onMicResult = {},
            onFinish = {},
        )
    }
}
