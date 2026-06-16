package ai.whatyousay.feature.conversation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Turn
import ai.whatyousay.design.WhatYouSayTheme

@Composable
fun ConversationScreen(viewModel: ConversationViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    ConversationContent(
        status = state.status,
        pair = state.pair,
        turns = state.turns,
        onSwap = viewModel::swap,
        onToggle = viewModel::toggleListening,
        onSubmit = viewModel::submitUtterance,
    )
}

@Composable
private fun ConversationContent(
    status: ConvStatus,
    pair: LanguagePair,
    turns: List<Turn>,
    onSwap: () -> Unit,
    onToggle: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PairHeader(pair = pair, onSwap = onSwap)
        Spacer(Modifier.width(0.dp))
        StatusLine(status)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (turns.isEmpty()) {
                item { EmptyState() }
            }
            items(turns) { turn -> TurnRow(turn) }
        }

        InputBar(listening = status != ConvStatus.IDLE, onToggle = onToggle, onSubmit = onSubmit)
    }
}

@Composable
private fun PairHeader(pair: LanguagePair, onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${pair.source.name}  to  ${pair.target.name}",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedButton(onClick = onSwap) { Text("Swap") }
    }
}

@Composable
private fun StatusLine(status: ConvStatus) {
    val label = when (status) {
        ConvStatus.IDLE -> "Tap start. Works in airplane mode."
        ConvStatus.LISTENING -> "Listening, on-device"
        ConvStatus.WORKING -> "Translating"
        ConvStatus.SPEAKING -> "Speaking"
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (status == ConvStatus.IDLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
    )
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
private fun InputBar(listening: Boolean, onToggle: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type to translate") },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSubmit(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
            ) { Text("Send") }
        }
        Spacer(Modifier.width(0.dp))
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(if (listening) "Stop" else "Start conversation") }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationPreview() {
    WhatYouSayTheme {
        ConversationContent(
            status = ConvStatus.LISTENING,
            pair = LanguagePair(Languages.EN, Languages.ES),
            turns = listOf(
                Turn(Languages.EN, Languages.ES, "where is the station", "donde esta la estacion", 0L),
                Turn(Languages.ES, Languages.EN, "a la derecha", "to the right", 0L),
            ),
            onSwap = {},
            onToggle = {},
            onSubmit = {},
        )
    }
}
