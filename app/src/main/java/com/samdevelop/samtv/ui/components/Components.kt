package com.samdevelop.samtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.ui.theme.BrandTitleStyle
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// ---------------------------------------------------------------- marca

@Composable
fun BrandMark(modifier: Modifier = Modifier, subtitle: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "SAM TV OFICIAL" }) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = SamColors.TextPrimary)) { append("SAM ") }
                withStyle(SpanStyle(color = SamColors.PurpleAccent)) { append("TV") }
                withStyle(SpanStyle(color = SamColors.TextSecondary, fontWeight = FontWeight.Medium)) { append("  OFICIAL") }
            },
            style = BrandTitleStyle,
        )
        if (subtitle) {
            Text("by SAM DEVELOP", style = MaterialTheme.typography.labelSmall, color = SamColors.TextSecondary)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

// ---------------------------------------------------------------- estados

@Composable
fun LoadingView(message: String = "Carregando…", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = SamColors.Purple)
        Spacer(Modifier.height(16.dp))
        Text(message, color = SamColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(SamColors.Surface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = SamColors.PurpleAccent, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = SamColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyView(
        icon = Icons.Filled.ErrorOutline,
        title = "Algo deu errado",
        message = message,
        modifier = modifier,
        actionLabel = onRetry?.let { "Tentar novamente" },
        onAction = onRetry,
    )
}

@Composable
fun InlineBanner(text: String, modifier: Modifier = Modifier, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isError) SamColors.Error.copy(alpha = 0.14f) else SamColors.Surface)
            .border(1.dp, if (isError) SamColors.Error.copy(alpha = 0.4f) else SamColors.Outline, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) SamColors.TextPrimary else SamColors.TextSecondary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ---------------------------------------------------------------- entrada

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("search_field"),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Limpar busca") }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SamColors.Purple,
            unfocusedBorderColor = SamColors.Outline,
            focusedContainerColor = SamColors.Surface,
            unfocusedContainerColor = SamColors.Surface,
        ),
    )
}

/** Chips de categoria com opção "Todos" (id null). */
@Composable
fun CategoryChips(categories: List<ChannelCategory>, selectedId: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (categories.isEmpty()) return
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "__all") { SamChip("Todos", selectedId == null) { onSelect(null) } }
        items(categories, key = { it.id }) { c -> SamChip(c.name, selectedId == c.id) { onSelect(c.id) } }
    }
}

@Composable
fun SamChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SamColors.Purple,
            selectedLabelColor = SamColors.TextPrimary,
            containerColor = SamColors.Surface,
            labelColor = SamColors.TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = SamColors.Outline, selectedBorderColor = SamColors.Purple),
    )
}

// ---------------------------------------------------------------- imagens e cartões

/** Imagem remota com espaço reservado; nada é baixado até o item aparecer na tela. */
@Composable
fun Artwork(url: String?, fallback: ImageVector, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(SamColors.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(fallback, contentDescription = null, tint = SamColors.TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                error = { Icon(fallback, contentDescription = null, tint = SamColors.TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(28.dp)) },
                loading = { Box(Modifier.fillMaxSize().background(SamColors.SurfaceHigh)) },
            )
        }
    }
}

@Composable
fun ChannelRow(
    name: String,
    number: Int?,
    logoUrl: String?,
    isFavorite: Boolean?,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Assistir", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            logoUrl, Icons.Outlined.LiveTv,
            Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(number?.toString(), name).joinToString("  "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isFavorite != null && onToggleFavorite != null) {
            FavoriteButton(isFavorite, onToggleFavorite)
        }
        trailing?.invoke()
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
            tint = if (isFavorite) SamColors.PurpleAccent else SamColors.TextSecondary,
        )
    }
}

@Composable
fun PosterCard(title: String, imageUrl: String?, fallback: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
    ) {
        Artwork(imageUrl, fallback, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small))
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = SamColors.TextSecondary, maxLines = 1)
    }
}

@Composable
fun ShortcutCard(title: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) SamColors.BrandGradient else androidx.compose.ui.graphics.SolidColor(SamColors.Surface))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) SamColors.TextPrimary else SamColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) SamColors.TextPrimary else SamColors.TextSecondary)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = SamColors.TextSecondary, maxLines = 2)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = SamColors.Error) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = SamColors.Surface,
    )
}

// ---------------------------------------------------------------- paginação

/** Dispara [onLoadMore] quando o usuário se aproxima do fim da lista. */
@Composable
fun LoadMoreEffect(state: LazyListState, threshold: Int = 15, onLoadMore: () -> Unit) {
    val shouldLoad by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - threshold
        }
    }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow { shouldLoad }.distinctUntilChanged().filter { it }.collect { currentOnLoadMore() }
    }
}

@Composable
fun LoadMoreEffect(state: LazyGridState, threshold: Int = 18, onLoadMore: () -> Unit) {
    val shouldLoad by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - threshold
        }
    }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow { shouldLoad }.distinctUntilChanged().filter { it }.collect { currentOnLoadMore() }
    }
}
