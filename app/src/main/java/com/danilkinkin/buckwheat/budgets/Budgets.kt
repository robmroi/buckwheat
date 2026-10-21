package com.danilkinkin.buckwheat.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.base.Divider
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.data.entities.Budget
import com.danilkinkin.buckwheat.util.numberFormat
import androidx.compose.foundation.clickable

const val BUDGETS_SHEET = "budgets"

const val DEFAULT_BUDGET_EMOJI = "💰"

fun Budget.displayEmoji() = emoji.ifBlank { DEFAULT_BUDGET_EMOJI }

@Composable
fun Budget.displayName() = name.ifBlank { stringResource(R.string.budget_default_name) }

@Composable
fun BudgetEmoji(budget: Budget, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = budget.displayEmoji(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Budgets(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding()
        .coerceAtLeast(16.dp)

    val budgets by spendsViewModel.budgets.observeAsState(emptyList())
    val activeBudget by spendsViewModel.activeBudget.observeAsState()
    val restBudgets by spendsViewModel.restBudgets.observeAsState(emptyMap())
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())

    var editedBudget by remember { mutableStateOf<Budget?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.budgets_title),
                    style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = navigationBarHeight)
            ) {
                budgets.forEach { budget ->
                    val rest = restBudgets[budget.uid]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (budget.uid != activeBudget?.uid) {
                                    spendsViewModel.switchBudget(budget.uid)
                                }
                                onClose()
                            }
                            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BudgetEmoji(budget)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = budget.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (budget.uid == activeBudget?.uid) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = if (rest != null) {
                                    stringResource(
                                        R.string.budget_left,
                                        numberFormat(context, rest, currency = currency),
                                    )
                                } else {
                                    stringResource(R.string.budget_not_configured)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (budget.uid == activeBudget?.uid) {
                            Icon(
                                painter = painterResource(R.drawable.ic_apply),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = { editedBudget = budget }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.edit_budget),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Divider()
                ButtonRow(
                    icon = painterResource(R.drawable.ic_add),
                    text = stringResource(R.string.add_budget),
                    onClick = { showCreateDialog = true },
                )
            }
        }
    }

    if (showCreateDialog) {
        BudgetInfoDialog(
            budget = null,
            canDelete = false,
            onSave = { name, emoji ->
                spendsViewModel.createBudget(name, emoji)
                showCreateDialog = false
                onClose()
            },
            onDelete = {},
            onClose = { showCreateDialog = false },
        )
    }

    editedBudget?.let { budget ->
        BudgetInfoDialog(
            budget = budget,
            canDelete = budgets.size > 1,
            onSave = { name, emoji ->
                spendsViewModel.updateBudgetInfo(budget.copy(name = name, emoji = emoji))
                editedBudget = null
            },
            onDelete = {
                spendsViewModel.deleteBudget(budget.uid)
                editedBudget = null
            },
            onClose = { editedBudget = null },
        )
    }
}

@Composable
fun BudgetInfoDialog(
    budget: Budget?,
    canDelete: Boolean,
    onSave: (name: String, emoji: String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(budget?.name ?: "") }
    var emoji by remember { mutableStateOf(budget?.emoji ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_budget)) },
            text = { Text(stringResource(R.string.delete_budget_description)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete_budget),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                stringResource(
                    if (budget == null) R.string.add_budget else R.string.edit_budget
                )
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { value ->
                            // Keep only the first symbol (emoji may take several chars)
                            emoji = value.codePoints().limit(2).toArray()
                                .let { String(it, 0, it.size) }
                        },
                        label = { Text(stringResource(R.string.budget_emoji_label)) },
                        placeholder = { Text(DEFAULT_BUDGET_EMOJI) },
                        singleLine = true,
                        modifier = Modifier.size(width = 88.dp, height = 64.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.budget_name_label)) },
                        placeholder = { Text(stringResource(R.string.budget_default_name)) },
                        singleLine = true,
                    )
                }
                if (budget != null && canDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.delete_budget),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), emoji.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
