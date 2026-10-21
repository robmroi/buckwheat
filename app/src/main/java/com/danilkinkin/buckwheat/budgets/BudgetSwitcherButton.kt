package com.danilkinkin.buckwheat.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.SpendsViewModel

// Shows the active budget on the editor screen. One tap opens the budget list
@Composable
fun BudgetSwitcherButton(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val activeBudget by spendsViewModel.activeBudget.observeAsState()

    activeBudget?.let { budget ->
        BudgetEmoji(
            budget = budget,
            modifier = Modifier
                .padding(4.dp)
                .clickable(
                    onClick = { appViewModel.openSheet(PathState(BUDGETS_SHEET)) },
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ),
        )
    }
}
