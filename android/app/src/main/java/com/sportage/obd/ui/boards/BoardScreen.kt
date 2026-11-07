package com.sportage.obd.ui.boards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import kotlinx.coroutines.launch

/**
 * The customizable boards screen: horizontally swipeable pages that the driver
 * fills with widgets of their choice. Layouts persist across restarts through
 * [BoardStore]. An edit mode exposes remove/reorder controls per widget and
 * board management (add board, remove board).
 */
@Composable
fun BoardScreen(
    data: BoardData,
    store: BoardStore,
    modifier: Modifier = Modifier
) {
    var layout by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { layout.boards.size })
    val scope = rememberCoroutineScope()

    fun persist(next: BoardLayout) {
        layout = next
        store.save(next)
    }

    fun addBoard() {
        if (layout.boards.size >= BoardLimits.MAX_BOARDS) return
        // Explicit mutable copy: "boards + emptyList()" would concatenate an empty
        // iterable and add nothing at all.
        val next = BoardLayout(layout.boards.toMutableList().apply { add(emptyList()) })
        persist(next)
        scope.launch { pagerState.animateScrollToPage(next.boards.size - 1) }
    }

    fun removeBoard(index: Int) {
        if (layout.boards.size <= 1) return
        val next = BoardLayout(layout.boards.filterIndexed { i, _ -> i != index })
        persist(next)
    }

    fun addWidget(type: BoardWidgetType) {
        val index = pagerState.currentPage
        val board = layout.boards.getOrNull(index) ?: return
        if (type in board || board.size >= BoardLimits.MAX_WIDGETS_PER_BOARD) return
        val next = BoardLayout(layout.boards.mapIndexed { i, b -> if (i == index) b + type else b })
        persist(next)
    }

    fun removeWidget(boardIndex: Int, widgetIndex: Int) {
        val next = BoardLayout(layout.boards.mapIndexed { i, b ->
            if (i == boardIndex) b.filterIndexed { j, _ -> j != widgetIndex } else b
        })
        persist(next)
    }

    fun moveWidget(boardIndex: Int, widgetIndex: Int, delta: Int) {
        val board = layout.boards.getOrNull(boardIndex) ?: return
        val target = widgetIndex + delta
        if (target < 0 || target >= board.size) return
        val swapped = board.toMutableList().apply {
            val w = removeAt(widgetIndex); add(target, w)
        }
        persist(BoardLayout(layout.boards.mapIndexed { i, b -> if (i == boardIndex) swapped else b }))
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header: title, board dots, edit toggle and add-widget action.
        Row(Modifier.testTag("boards_header").fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Boards", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (editing) "Edit mode · remove, reorder or add widgets" else "Swipe left and right between your boards",
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium
                )
            }
            if (layout.boards.size > 1) {
                Row(
                    Modifier.testTag("board_dots").padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    layout.boards.indices.forEach { i ->
                        Box(
                            Modifier.size(if (pagerState.currentPage == i) 9.dp else 6.dp)
                                .background(
                                    if (pagerState.currentPage == i) ElectricCyan() else SecondaryText().copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            if (editing && layout.boards.size > 1) {
                FilledIconButton(
                    onClick = { removeBoard(pagerState.currentPage) },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = CriticalRed()
                    )
                ) { Icon(Icons.Default.Delete, "Remove this board", Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { if (editing) addBoard() else pickerOpen = true },
                modifier = Modifier.testTag("board_add_button").size(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = ElectricCyan()
                )
            ) { Icon(Icons.Default.Add, if (editing) "Add board" else "Add widgets", Modifier.size(20.dp)) }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { editing = !editing },
                modifier = Modifier.testTag("board_edit_toggle").size(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (editing) ElectricCyan() else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (editing) MaterialTheme.colorScheme.onPrimary else ElectricCyan()
                )
            ) { Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, if (editing) "Finish editing" else "Edit boards", Modifier.size(20.dp)) }
        }

        // The swipeable boards themselves.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.testTag("boards_pager").weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0
        ) { pageIndex ->
            val widgets = layout.boards[pageIndex]
            if (widgets.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize().testTag("board_empty_${pageIndex}"),
                    color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Board ${pageIndex + 1} is empty", color = PrimaryText(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap the pencil icon to edit, then + to add widgets", color = SecondaryText(), fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize().testTag("board_grid_$pageIndex"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        widgets,
                        // The live map needs the full row width to stay readable.
                        span = { _, type ->
                            if (type == BoardWidgetType.MAP) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        }
                    ) { widgetIndex, type ->
                        BoardWidget(
                            type = type,
                            data = data,
                            editing = editing,
                            canMoveLeft = editing && widgetIndex > 0,
                            canMoveRight = editing && widgetIndex < widgets.size - 1,
                            onRemove = { removeWidget(pageIndex, widgetIndex) },
                            onMoveLeft = { moveWidget(pageIndex, widgetIndex, -1) },
                            onMoveRight = { moveWidget(pageIndex, widgetIndex, 1) }
                        )
                    }
                }
            }
        }
    }

    WidgetPicker(
        isOpen = pickerOpen,
        boardIndex = pagerState.currentPage,
        layout = layout,
        onAdd = { addWidget(it) },
        onDismiss = { pickerOpen = false }
    )
}
