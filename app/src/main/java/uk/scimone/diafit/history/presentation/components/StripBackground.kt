package uk.scimone.diafit.history.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Field behind every strip: only a hair lighter than the page, so the strips are barely there. */
@Composable
fun stripBackground(): Color = with(MaterialTheme.colorScheme) { lerp(background, surface, 0.35f) }
