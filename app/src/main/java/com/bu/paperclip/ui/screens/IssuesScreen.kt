package com.bu.paperclip.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.ErrorState
import com.bu.paperclip.ui.components.ISSUE_STATUSES
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.statusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val ISSUE_PAGE = 50
private const val ISSUE_ACTIVE_STATUSES = "backlog,todo,in_progress,in_review,blocked"

private data class IssueStatusFilter(val key: String, val label: String, val statuses: String?)

private val issueStatusFilters = listOf(IssueStatusFilter("active", "Active", ISSUE_ACTIVE_STATUSES)) +
    listOf("todo", "in_progress", "in_review", "blocked", "backlog").map { IssueStatusFilter(it, statusLabel(it), it) } +
    listOf(
        IssueStatusFilter("done", "Done", "done"),
        IssueStatusFilter("cancelled", "Cancelled", "cancelled"),
        IssueStatusFilter("all", "All", null),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var statusKey by rememberSaveable { mutableStateOf("active") }
    var assignee by rememberSaveable { mutableStateOf<String?>(null) } // null = anyone, "me", "none", or agent id
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var grouped by rememberSaveable { mutableStateOf(false) }
    var debouncedQuery by remember { mutableStateOf(query.trim()) }
    var showAssignee by remember { mutableStateOf(false) }
    var showProject by remember { mutableStateOf(false) }
    LaunchedEffect(query) { delay(350); debouncedQuery = query.trim() }

    val items = remember { mutableStateListOf<Issue>() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var filterKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var moreJob by remember { mutableStateOf<Job?>(null) }

    val statusFilter = issueStatusFilters.first { it.key == statusKey }

    suspend fun fetch(offset: Int, limit: Int): List<Issue> = Repo.issues(
        cid,
        status = statusFilter.statuses,
        assigneeAgentId = when (assignee) { null, "me" -> null; "none" -> "null"; else -> assignee },
        assigneeUserId = if (assignee == "me") "me" else null,
        projectId = projectId,
        q = debouncedQuery.ifBlank { null },
        limit = limit,
        offset = offset,
    )

    // Reload on filter change (show spinner) or live/pull refresh (keep the list visible).
    LaunchedEffect(cid, statusKey, assignee, projectId, debouncedQuery, reloadTick) {
        if (cid.isEmpty()) return@LaunchedEffect
        moreJob?.cancel()
        val key = "$cid|$statusKey|$assignee|$projectId|$debouncedQuery"
        val sameFilters = key == filterKey && items.isNotEmpty()
        filterKey = key
        if (!sameFilters) { loading = true; items.clear() }
        try {
            val size = if (sameFilters) maxOf(ISSUE_PAGE, items.size) else ISSUE_PAGE
            val page = fetch(0, size)
            items.clear(); items.addAll(page)
            endReached = page.size < size
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (items.isEmpty()) error = errorMessage(t) else AppState.toast(errorMessage(t))
        } finally {
            loading = false; refreshing = false
        }
    }

    fun loadMore() {
        if (loadingMore || endReached || loading || items.isEmpty()) return
        loadingMore = true
        moreJob = scope.launch {
            try {
                val page = fetch(items.size, ISSUE_PAGE)
                val known = items.map { it.id }.toHashSet()
                items.addAll(page.filter { it.id !in known })
                endReached = page.size < ISSUE_PAGE
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppState.toast(errorMessage(t))
            } finally {
                loadingMore = false
            }
        }
    }

    LiveRefresh(debounceMs = 1500, filter = { it.type == "activity.logged" && it.s("entityType") == "issue" }) { reloadTick++ }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { loadMore() }
    }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search tasks") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    },
                    actions = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") } },
                )
            } else {
                TopAppBar(
                    title = { Text("Tasks") },
                    actions = {
                        IconButton(onClick = { grouped = !grouped }) {
                            Icon(if (grouped) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.ViewAgenda, if (grouped) "Flat list" else "Group by status")
                        }
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.newIssue(projectId = projectId) }) { Icon(Icons.Default.Add, "New task") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val assigneeLabel = when (assignee) {
                    null -> "Anyone"
                    "me" -> "Assigned to me"
                    "none" -> "Unassigned"
                    else -> AppState.agentName(assignee)
                }
                FilterChip(selected = assignee != null, onClick = { showAssignee = true }, label = { Text(assigneeLabel) })
                FilterChip(
                    selected = projectId != null,
                    onClick = { showProject = true },
                    label = { Text(AppState.project(projectId)?.name ?: "All projects") },
                )
                issueStatusFilters.forEach { f ->
                    val single = f.statuses?.takeIf { !it.contains(',') }
                    FilterChip(
                        selected = statusKey == f.key,
                        onClick = { statusKey = f.key },
                        label = { Text(f.label) },
                        leadingIcon = if (single != null) ({ StatusIcon(single, 12.dp) }) else null,
                    )
                }
            }
            Divider()
            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null && items.isEmpty() -> ErrorState(error!!) { reloadTick++ }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; reloadTick++ },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (items.isEmpty()) {
                            item { EmptyState(if (debouncedQuery.isNotBlank()) "No tasks match \"$debouncedQuery\"" else "No tasks here") }
                        } else if (grouped) {
                            val groups = items.groupBy { it.status }
                            ISSUE_STATUSES.forEach { s ->
                                val list = groups[s] ?: return@forEach
                                item(key = "h-$s") { SectionHeader("${statusLabel(s)} · ${list.size}") }
                                items(list, key = { it.id }) { issue ->
                                    IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) })
                                }
                            }
                        } else {
                            items(items, key = { it.id }) { issue ->
                                IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) })
                                Divider()
                            }
                        }
                        if (loadingMore) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        item { Spacer(Modifier.size(88.dp)) }
                    }
                }
            }
        }
    }

    if (showAssignee) {
        IssueAssigneeSheet(
            currentAgentId = assignee?.takeIf { it != "me" && it != "none" },
            currentUserId = if (assignee == "me") AppState.me.value?.id else null,
            title = "Filter by assignee",
            unassignedSelected = assignee == "none",
            onPick = { pick ->
                assignee = when (pick) {
                    is IssueAssignee.AgentA -> if (assignee == pick.id) null else pick.id
                    IssueAssignee.Me -> if (assignee == "me") null else "me"
                    IssueAssignee.Nobody -> if (assignee == "none") null else "none"
                }
            },
            onDismiss = { showAssignee = false },
        )
    }
    if (showProject) {
        IssueProjectSheet(projectId, onPick = { projectId = it }, onDismiss = { showProject = false })
    }
}
