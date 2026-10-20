package com.bu.paperclip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.Routine
import com.bu.paperclip.data.RoutineTrigger
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.net.ApiException
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.ErrorState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.Schedule
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId

private val CONCURRENCY = listOf(
    Triple("coalesce_if_active", "Merge into the open run", "If a run is still open, fold the new trigger into it."),
    Triple("skip_if_active", "Skip while one is open", "Ignore triggers until the open run finishes."),
    Triple("always_enqueue", "Always start a new run", "Every trigger creates its own task."),
)
private val CATCH_UP = listOf(
    Triple("skip_missed", "Skip missed runs", "Schedules missed while paused or offline are dropped."),
    Triple("enqueue_missed_with_cap", "Catch up, with a cap", "Missed schedules run when possible, up to a limit."),
)

/** The editable part of a routine; Save sends all of it, like the web UI's save bar. */
private data class RoutineDraft(
    val title: String,
    val description: String,
    val assigneeAgentId: String?,
    val projectId: String?,
    val priority: String,
    val concurrencyPolicy: String,
    val catchUpPolicy: String,
) {
    constructor(r: Routine) : this(r.title, r.description.orEmpty(), r.assigneeAgentId, r.projectId, r.priority, r.concurrencyPolicy, r.catchUpPolicy)

    fun body(baseRevisionId: String?): JsonObject = jsonOf(
        "title" to title.trim(),
        "description" to (description.trim().ifEmpty { null } ?: JsonNull),
        "assigneeAgentId" to (assigneeAgentId ?: JsonNull),
        "projectId" to (projectId ?: JsonNull),
        "priority" to priority,
        "concurrencyPolicy" to concurrencyPolicy,
        "catchUpPolicy" to catchUpPolicy,
        "baseRevisionId" to baseRevisionId,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditScreen(nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var routine by remember { mutableStateOf<Routine?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<RoutineDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var editTrigger by remember { mutableStateOf<RoutineTrigger?>(null) }
    var addingTrigger by remember { mutableStateOf(false) }
    var hiddenTriggers by remember { mutableStateOf(emptySet<String>()) }

    suspend fun load(resetDraft: Boolean) {
        try {
            val r = Repo.routine(id)
            routine = r
            if (resetDraft || draft == null) draft = RoutineDraft(r)
            loadError = null
        } catch (c: CancellationException) { throw c } catch (t: Throwable) {
            if (routine == null) loadError = errorMessage(t) else AppState.toast(errorMessage(t))
        }
    }
    LaunchedEffect(id) { load(resetDraft = true) }
    // Trigger and status changes from elsewhere refresh the routine but never overwrite unsaved edits.
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityId") == id }) { scope.launch { load(resetDraft = false) } }

    val r = routine
    val d = draft
    val dirty = r != null && d != null && d != RoutineDraft(r)

    fun save(force: Boolean = false) {
        val cur = routine ?: return
        val body = draft ?: return
        if (body.title.isBlank() || saving) return
        saving = true
        scope.launch {
            try {
                Repo.updateRoutine(cur.id, body.body(if (force) null else cur.latestRevisionId))
                load(resetDraft = true)
                AppState.toast("Routine saved")
            } catch (c: CancellationException) { throw c } catch (t: Throwable) {
                if (t is ApiException.Http && t.status == 409) conflict = true else AppState.toast(errorMessage(t))
            } finally { saving = false }
        }
    }

    fun setStatus(next: String) {
        val cur = routine ?: return
        if (next == "active" && cur.assigneeAgentId == null) {
            AppState.toast("Set a default agent (and save) before enabling automation.")
            return
        }
        routine = cur.copy(status = next)
        scope.launch {
            try {
                Repo.updateRoutine(cur.id, jsonOf("status" to next))
                AppState.toast(if (next == "paused") "Automation paused" else "Automation enabled")
            } catch (c: CancellationException) { throw c } catch (t: Throwable) {
                routine = cur; AppState.toast(errorMessage(t))
            }
            load(resetDraft = false)
        }
    }

    fun triggerCall(okMessage: String?, block: suspend () -> Unit) {
        scope.launch {
            try { block(); okMessage?.let { AppState.toast(it) } }
            catch (c: CancellationException) { throw c }
            catch (t: Throwable) { AppState.toast(errorMessage(t)) }
            load(resetDraft = false)
        }
    }

    fun removeTrigger(t: RoutineTrigger) {
        hiddenTriggers = hiddenTriggers + t.id
        scope.launch {
            try {
                Repo.updateRoutineTrigger(t.id, jsonOf("archived" to true))
                load(resetDraft = false)
                if (snackbar.showSnackbar("Schedule removed", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    Repo.updateRoutineTrigger(t.id, jsonOf("archived" to false))
                    load(resetDraft = false)
                }
            } catch (c: CancellationException) { throw c } catch (e: Throwable) {
                AppState.toast(errorMessage(e))
            } finally { hiddenTriggers = hiddenTriggers - t.id }
        }
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { if (dirty) confirmDiscard = true else nav.back() } },
                title = { Text(if (dirty) "Edit routine •" else "Routine", maxLines = 1) },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    else if (dirty) {
                        TextButton(onClick = { draft = r?.let(::RoutineDraft) }) { Text("Discard") }
                        TextButton(onClick = { save() }, enabled = d?.title?.isNotBlank() == true) { Text("Save") }
                    } else if (r != null) {
                        IconButton(onClick = { confirmRun = true }, enabled = r.status != "archived") { Icon(Icons.Outlined.PlayArrow, "Run now") }
                    }
                },
            )
        },
    ) { pad ->
        when {
            r == null && loadError != null -> ErrorState(loadError!!, Modifier.padding(pad)) { scope.launch { load(true) } }
            r == null || d == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()),
            ) {
                // ---- automation status ----
                Card(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Automation", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                Pill(
                                    when {
                                        r.status == "archived" -> "Archived"
                                        r.assigneeAgentId == null -> "Draft"
                                        else -> statusLabel(r.status)
                                    },
                                    adminStatusColor(r.status),
                                )
                            }
                            Text(
                                if (r.assigneeAgentId == null) "Needs a default agent before schedules can run. It can still run manually."
                                else if (r.status == "active") "Schedules and triggers start runs." else "Triggers are ignored until you resume.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = r.status == "active",
                            enabled = r.status != "archived",
                            onCheckedChange = { setStatus(if (it) "active" else "paused") },
                        )
                    }
                }
                r.managedByPlugin?.let {
                    Text("Managed by a plugin; its defaults may override edits.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }

                // ---- overview ----
                SectionHeader("Overview")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = d.title,
                        onValueChange = { draft = d.copy(title = it) },
                        label = { Text("Title") },
                        isError = d.title.isBlank(),
                        supportingText = if (d.title.isBlank()) ({ Text("A title is required") }) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PropertyChip(
                            onClick = { sheet = "assignee" },
                            label = { Text(d.assigneeAgentId?.let { AppState.agentName(it) } ?: "No default agent") },
                            leadingIcon = {
                                if (d.assigneeAgentId != null) AgentAvatar(d.assigneeAgentId, 18.dp)
                                else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                            },
                        )
                        PropertyChip(
                            onClick = { sheet = "project" },
                            label = { Text(AppState.project(d.projectId)?.name ?: "No project") },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)) },
                        )
                        PropertyChip(
                            onClick = { sheet = "priority" },
                            label = { Text(d.priority.replaceFirstChar { it.uppercase() }) },
                            leadingIcon = { PriorityIcon(d.priority, 16.dp) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Instructions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        FilterChip(selected = !preview, onClick = { preview = false }, label = { Text("Edit") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = preview, onClick = { preview = true }, label = { Text("Preview") })
                    }
                    if (preview) {
                        Card {
                            if (d.description.isBlank()) Text("No instructions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Markdown(d.description, fontSizeSp = 14f)
                        }
                    } else {
                        OutlinedTextField(
                            value = d.description,
                            onValueChange = { draft = d.copy(description = it) },
                            placeholder = { Text("Add instructions… (markdown, {{variables}})") },
                            minLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        )
                    }
                    val vars = r.variables?.size ?: 0
                    if (vars > 0) Text(
                        "$vars variable${if (vars == 1) "" else "s"} from {{placeholders}}; edit their types and defaults in the web UI.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- schedules ----
                SectionHeader("Schedules") {
                    TextButton(onClick = { addingTrigger = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                    }
                }
                val triggers = r.triggers.orEmpty().filter { !it.archived && it.id !in hiddenTriggers }
                if (triggers.isEmpty()) Text(
                    "No triggers. Add a schedule, or run it manually.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                triggers.forEach { t ->
                    RoutineTriggerRow(
                        t,
                        onToggle = { on -> triggerCall(if (on) "Schedule enabled" else "Schedule paused") { Repo.updateRoutineTrigger(t.id, jsonOf("enabled" to on)) } },
                        onEdit = { editTrigger = t },
                        onRemove = { removeTrigger(t) },
                    )
                }

                // ---- delivery ----
                SectionHeader("When runs overlap")
                CONCURRENCY.forEach { (value, label, help) ->
                    RoutineRadio(d.concurrencyPolicy == value, label, help) { draft = d.copy(concurrencyPolicy = value) }
                }
                SectionHeader("Missed schedules")
                CATCH_UP.forEach { (value, label, help) ->
                    RoutineRadio(d.catchUpPolicy == value, label, help) { draft = d.copy(catchUpPolicy = value) }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    when (sheet) {
        "assignee" -> IssueAssigneeSheet(d?.assigneeAgentId, null, allowMe = false, title = "Default agent", onPick = { a ->
            draft = draft?.copy(assigneeAgentId = (a as? IssueAssignee.AgentA)?.id)
        }, onDismiss = { sheet = null })
        "project" -> IssueProjectSheet(d?.projectId, onPick = { draft = draft?.copy(projectId = it) }, onDismiss = { sheet = null })
        "priority" -> IssuePrioritySheet(d?.priority, onPick = { draft = draft?.copy(priority = it) }, onDismiss = { sheet = null })
    }

    if (addingTrigger || editTrigger != null) {
        val t = editTrigger
        ScheduleDialog(
            initialCron = t?.cronExpression ?: "0 9 * * 1-5",
            initialZone = t?.timezone ?: ZoneId.systemDefault().id,
            title = if (t == null) "Add schedule" else "Edit schedule",
            onDismiss = { addingTrigger = false; editTrigger = null },
            onSave = { cron, zone ->
                addingTrigger = false; editTrigger = null
                val cur = routine ?: return@ScheduleDialog
                if (t == null) triggerCall("Schedule added") {
                    Repo.createRoutineTrigger(cur.id, jsonOf("kind" to "schedule", "cronExpression" to cron, "timezone" to zone))
                } else triggerCall("Schedule saved") {
                    Repo.updateRoutineTrigger(t.id, jsonOf("cronExpression" to cron, "timezone" to zone))
                }
            },
        )
    }

    if (conflict) AlertDialog(
        onDismissRequest = { conflict = false },
        title = { Text("Routine changed elsewhere") },
        text = { Text("Someone else saved this routine since you opened it. Reload to see their version (your edits are discarded), or overwrite it with yours.") },
        confirmButton = { TextButton(onClick = { conflict = false; save(force = true) }) { Text("Overwrite") } },
        dismissButton = { TextButton(onClick = { conflict = false; scope.launch { load(resetDraft = true) } }) { Text("Reload latest") } },
    )

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text("Your edits to this routine haven't been saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; draft = r?.let(::RoutineDraft); nav.back() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )

    if (confirmRun && r != null) AlertDialog(
        onDismissRequest = { confirmRun = false },
        title = { Text("Run now?") },
        text = { Text("Start \"${r.title}\" now${AppState.agent(r.assigneeAgentId)?.let { " — it will be assigned to ${it.name}" } ?: ""}.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRun = false
                triggerCall("Started ${r.title}") { Repo.runRoutine(r.id) }
            }) { Text("Run") }
        },
        dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RoutineTriggerRow(t: RoutineTrigger, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (t.kind == "schedule") {
                Text(Schedule.describe(t.cronExpression), style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        t.timezone,
                        if (!t.enabled) "paused" else t.nextRunAt?.let { "next ${relativeTime(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(t.label ?: "${t.kind.replaceFirstChar { it.uppercase() }} trigger", style = MaterialTheme.typography.bodyLarge)
                Text("Manage ${t.kind} triggers in the web UI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = t.enabled, onCheckedChange = onToggle)
        if (t.kind == "schedule") {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit schedule") }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove schedule") }
        }
    }
}

@Composable
private fun RoutineRadio(selected: Boolean, label: String, help: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Preset-based schedule editor (the web UI's presets), with a raw cron escape hatch. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(initialCron: String, initialZone: String, title: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var s by remember { mutableStateOf(Schedule.parse(initialCron).let { if (it.preset == Schedule.Preset.Custom && it.custom.isEmpty()) it.copy(custom = initialCron) else it }) }
    var zone by remember { mutableStateOf(initialZone) }
    val cron = s.toCron()
    val cronError = Schedule.validate(cron)
    val zoneValid = remember(zone) { runCatching { ZoneId.of(zone.trim()) }.isSuccess }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Schedule.Preset.entries.forEach { p ->
                        FilterChip(selected = s.preset == p, onClick = {
                            s = if (p == Schedule.Preset.Custom) s.copy(preset = p, custom = cron) else s.copy(preset = p)
                        }, label = { Text(p.label) })
                    }
                }
                when (s.preset) {
                    Schedule.Preset.Custom -> OutlinedTextField(
                        value = s.custom, onValueChange = { s = s.copy(custom = it) }, singleLine = true,
                        label = { Text("Cron (minute hour day month weekday)") },
                        isError = cronError != null,
                        supportingText = { Text(cronError ?: Schedule.describe(s.custom)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Schedule.Preset.Hourly -> NumberField("Minute past the hour", s.minute, 0..59) { s = s.copy(minute = it) }
                    else -> {
                        if (s.preset == Schedule.Preset.Weekly) {
                            ScheduleDropdown("Day", Schedule.WEEKDAYS[s.weekday], Schedule.WEEKDAYS) { s = s.copy(weekday = Schedule.WEEKDAYS.indexOf(it)) }
                        }
                        if (s.preset == Schedule.Preset.Monthly) NumberField("Day of month", s.dayOfMonth, 1..31) { s = s.copy(dayOfMonth = it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { NumberField("Hour (0–23)", s.hour, 0..23) { s = s.copy(hour = it) } }
                            Box(Modifier.weight(1f)) { NumberField("Minute", s.minute, 0..59) { s = s.copy(minute = it) } }
                        }
                    }
                }
                OutlinedTextField(
                    value = zone, onValueChange = { zone = it }, singleLine = true,
                    label = { Text("Time zone") },
                    isError = !zoneValid,
                    supportingText = { Text(if (zoneValid) "Follows this zone, including daylight saving." else "Use an IANA name, e.g. America/Sao_Paulo") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (cronError == null) Text(Schedule.describe(cron), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(cron, zone.trim()) }, enabled = cronError == null && zoneValid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter(Char::isDigit).take(2); text.toIntOrNull()?.takeIf { it in range }?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        isError = n == null || n !in range,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}
