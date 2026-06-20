package ai.whatyousay.feature.conversation

import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.Language
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Turn
import ai.whatyousay.design.WhatYouSayTheme
import ai.whatyousay.engine.ConversationState
import ai.whatyousay.engine.EngineReadiness
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConversationScreen(
    onOpenModels: () -> Unit,
    viewModel: ConversationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ConversationContent(
        state = state,
        onOpenModels = onOpenModels,
        onPickSource = viewModel::setSource,
        onPickTarget = viewModel::setTarget,
        onSwap = viewModel::swap,
        onSubmitText = viewModel::submitText,
        onStartTalk = viewModel::startPushToTalk,
        onStopTalk = viewModel::stopPushToTalk,
        onMicResult = viewModel::onMicPermissionResult,
        onToggleHandsFree = viewModel::toggleHandsFree,
        onDismissError = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationContent(
    state: ConversationUiState,
    onOpenModels: () -> Unit,
    onPickSource: (Language) -> Unit,
    onPickTarget: (Language) -> Unit,
    onSwap: () -> Unit,
    onSubmitText: (String) -> Unit,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onMicResult: (Boolean) -> Unit,
    onToggleHandsFree: () -> Unit,
    onDismissError: () -> Unit,
) {
    val conv = state.conversation
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What You Say") },
                actions = { TextButton(onClick = onOpenModels) { Text("Models") } },
            )
        },
    ) { inset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .padding(horizontal = 16.dp),
        ) {
            PairHeader(
                pair = conv.pair,
                languages = state.availableLanguages,
                onPickSource = onPickSource,
                onPickTarget = onPickTarget,
                onSwap = onSwap,
            )
            EngineBanner(conv.readiness)
            StatusLine(conv.status)

            conv.error?.let { ErrorRow(it, onDismissError) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                conv.partial.takeIf { it.isNotBlank() }?.let { item { PartialRow(it) } }
                if (conv.turns.isEmpty() && conv.partial.isBlank()) item { EmptyState() }
                items(conv.turns.asReversed()) { turn -> TurnRow(turn) }
            }

            ListenControls(
                state = state,
                onSubmitText = onSubmitText,
                onStartTalk = onStartTalk,
                onStopTalk = onStopTalk,
                onMicResult = onMicResult,
                onToggleHandsFree = onToggleHandsFree,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairHeader(
    pair: LanguagePair,
    languages: List<Language>,
    onPickSource: (Language) -> Unit,
    onPickTarget: (Language) -> Unit,
    onSwap: () -> Unit,
) {
    var picking by remember { mutableStateOf<Side?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LanguageButton(pair.source, Modifier.weight(1f)) { picking = Side.SOURCE }
        TextButton(onClick = onSwap) { Text("<>") }
        LanguageButton(pair.target, Modifier.weight(1f)) { picking = Side.TARGET }
    }

    picking?.let { side ->
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { picking = null }, sheetState = sheet) {
            Text(
                text = if (side == Side.SOURCE) "SPEAK FROM" else "TRANSLATE TO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            LazyColumn {
                items(languages) { language ->
                    LanguageRow(language) {
                        if (side == Side.SOURCE) onPickSource(language) else onPickTarget(language)
                        picking = null
                    }
                }
            }
        }
    }
}

private enum class Side { SOURCE, TARGET }

@Composable
private fun LanguageButton(language: Language, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(language.name, style = MaterialTheme.typography.titleMedium)
            Text(language.code.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LanguageRow(language: Language, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(language.name, style = MaterialTheme.typography.bodyLarge)
        Text(language.code.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EngineBanner(readiness: EngineReadiness) {
    val label = if (readiness.allReal) {
        "ON-DEVICE MODELS ACTIVE"
    } else {
        "DEMO ENGINES: MT ${tag(readiness.mt)}, STT ${tag(readiness.stt)}, TTS ${tag(readiness.tts)}"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun tag(real: Boolean): String = if (real) "real" else "stub"

@Composable
private fun StatusLine(status: ConvStatus) {
    val label = when (status) {
        ConvStatus.IDLE -> "Hold to talk, or type. Works in airplane mode."
        ConvStatus.LISTENING -> "Listening, on-device"
        ConvStatus.WORKING -> "Translating"
        ConvStatus.SPEAKING -> "Speaking"
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (status == ConvStatus.IDLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ErrorRow(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "what you say?",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
    )
}

@Composable
private fun PartialRow(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text("HEARING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text(
            text = "${turn.source.code} to ${turn.target.code}".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = turn.heard, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = turn.spoken, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ListenControls(
    state: ConversationUiState,
    onSubmitText: (String) -> Unit,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onMicResult: (Boolean) -> Unit,
    onToggleHandsFree: () -> Unit,
) {
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onMicResult(granted)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalDivider()
        TextInputBar(onSubmitText)
        TalkButton(
            recording = state.recording,
            micGranted = state.micGranted,
            onStartTalk = onStartTalk,
            onStopTalk = onStopTalk,
            onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        HandsFreeRow(
            enabled = state.canHandsFree,
            on = state.conversation.handsFree,
            onToggle = onToggleHandsFree,
        )
    }
}

@Composable
private fun TextInputBar(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type to translate") },
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSubmit(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) { Text("Send") }
    }
}

@Composable
private fun TalkButton(
    recording: Boolean,
    micGranted: Boolean,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val label = when {
        !micGranted -> "TAP TO ENABLE MIC"
        recording -> "LISTENING. RELEASE TO TRANSLATE"
        else -> "HOLD TO TALK"
    }
    val background = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val border = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val content = if (recording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    val pressModifier = if (micGranted) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onStartTalk()
                    val released = tryAwaitRelease()
                    onStopTalk()
                    released
                },
            )
        }
    } else {
        Modifier.clickable(onClick = onRequestMic)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(64.dp)
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .then(pressModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
private fun HandsFreeRow(enabled: Boolean, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hands-free (auto-VAD)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (enabled) "Continuous listening in the background" else "Available after installing voice models",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationPreview() {
    WhatYouSayTheme {
        ConversationContent(
            state = ConversationUiState(
                conversation = ConversationState(
                    status = ConvStatus.LISTENING,
                    pair = LanguagePair(Languages.EN, Languages.ES),
                    partial = "where is the station",
                    turns = listOf(
                        Turn(Languages.EN, Languages.ES, "hello", "hola", 0L),
                        Turn(Languages.ES, Languages.EN, "a la derecha", "to the right", 0L),
                    ),
                    readiness = EngineReadiness(mt = false, stt = false, tts = false),
                ),
                availableLanguages = Languages.all,
                micGranted = true,
                recording = false,
            ),
            onOpenModels = {},
            onPickSource = {},
            onPickTarget = {},
            onSwap = {},
            onSubmitText = {},
            onStartTalk = {},
            onStopTalk = {},
            onMicResult = {},
            onToggleHandsFree = {},
            onDismissError = {},
        )
    }
}
