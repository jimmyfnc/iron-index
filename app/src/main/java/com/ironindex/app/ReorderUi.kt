package com.ironindex.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderExercises(entries: List<WorkoutExercise>, dismiss: () -> Unit, saveOrder: (List<String>) -> Unit) {
    var order by remember { mutableStateOf(entries) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val state = rememberLazyListState()
    val save by rememberUpdatedState(saveOrder)
    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun followPointer() {
        val id = dragged ?: return
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull { center >= it.offset && center <= it.offset + it.size && it.key != id } ?: return
        move(order.indexOfFirst { it.id == id }, order.indexOfFirst { it.id == target.key })
    }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val layout = state.layoutInfo
            val step = when {
                center < layout.viewportStartOffset + 90 -> -16f
                center > layout.viewportEndOffset - 90 -> 16f
                else -> 0f
            }
            if (step != 0f) { state.scrollBy(step); followPointer() }
            delay(16)
        }
    }
    ModalBottomSheet(onDismissRequest = { save(order.map { it.id }); dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Exercise order", style = MaterialTheme.typography.headlineSmall)
            Text("Hold a drag handle and move it. You can also use the up/down buttons.", modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(order, key = { _, e -> e.id }) { index, e ->
                    val dragging = dragged == e.id
                    Card(modifier = Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer {
                        val info = state.layoutInfo.visibleItemsInfo.find { it.key == e.id }
                        translationY = if (dragging && info != null) center - info.offset - info.size / 2f else 0f
                    }, colors = CardDefaults.cardColors(containerColor = if (dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DragHandle, null, modifier = Modifier.size(48.dp).semantics { contentDescription = "Drag ${e.exercise.name}" }
                                .pointerInput(e.id) { detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        state.layoutInfo.visibleItemsInfo.find { it.key == e.id }?.let { item -> center = item.offset + item.size / 2f; dragged = e.id }
                                    }, onDragEnd = { dragged = null; save(order.map { it.id }) },
                                    onDragCancel = { dragged = null; save(order.map { it.id }) },
                                    onDrag = { change, amount -> change.consume(); center += amount.y; followPointer() },
                                ) })
                            Text("${index + 1}. ${e.exercise.name}", Modifier.weight(1f))
                            IconButton(enabled = index > 0, onClick = { move(index, index - 1); save(order.map { it.id }) }) { Icon(Icons.Rounded.ArrowUpward, "Move ${e.exercise.name} up") }
                            IconButton(enabled = index < order.lastIndex, onClick = { move(index, index + 1); save(order.map { it.id }) }) { Icon(Icons.Rounded.ArrowDownward, "Move ${e.exercise.name} down") }
                        }
                    }
                }
            }
            Button(onClick = { save(order.map { it.id }); dismiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Done ordering") }
        }
    }
}
