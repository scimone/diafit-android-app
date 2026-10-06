package uk.scimone.diafit.history.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Field behind every strip: a little lighter than the page, so each day reads as one quiet track. */
@Composable
fun stripBackground(): Color = with(MaterialTheme.colorScheme) { lerp(background, surface, 0.7f) }

/** The 6-hourly guides drawn through every track. */
@Composable
fun hourGuideColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
