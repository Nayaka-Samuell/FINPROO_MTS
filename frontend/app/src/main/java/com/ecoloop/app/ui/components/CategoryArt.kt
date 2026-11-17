package com.ecoloop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Carpenter
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ecoloop.app.ui.theme.CategoryBottleBg
import com.ecoloop.app.ui.theme.CategoryBottleFg
import com.ecoloop.app.ui.theme.CategoryCardboardBg
import com.ecoloop.app.ui.theme.CategoryCardboardFg
import com.ecoloop.app.ui.theme.CategoryFabricBg
import com.ecoloop.app.ui.theme.CategoryFabricFg
import com.ecoloop.app.ui.theme.CategoryWoodBg
import com.ecoloop.app.ui.theme.CategoryWoodFg
import com.ecoloop.app.ui.theme.EcoMint
import com.ecoloop.app.ui.theme.EcoPrimary

data class CategoryStyle(
    val icon: ImageVector,
    val background: Color,
    val foreground: Color,
)

/**
 * Gaya ilustrasi per kategori. Dicocokkan lewat ID seed backend (000...1 s.d. 000...4)
 * lalu kata kunci nama, sehingga tetap bekerja bila ID berubah.
 */
fun categoryStyle(categoryId: String?, categoryName: String?): CategoryStyle {
    val key = "${categoryName.orEmpty()} ${categoryId.orEmpty()}".lowercase()
    return when {
        key.endsWith("0001") || "kain" in key || "perca" in key ->
            CategoryStyle(Icons.Filled.Checkroom, CategoryFabricBg, CategoryFabricFg)
        key.endsWith("0002") || "kardus" in key || "karton" in key ->
            CategoryStyle(Icons.Filled.Inventory2, CategoryCardboardBg, CategoryCardboardFg)
        key.endsWith("0003") || "botol" in key || "plastik" in key ->
            CategoryStyle(Icons.Filled.LocalDrink, CategoryBottleBg, CategoryBottleFg)
        key.endsWith("0004") || "kayu" in key ->
            CategoryStyle(Icons.Filled.Carpenter, CategoryWoodBg, CategoryWoodFg)
        else -> CategoryStyle(Icons.Filled.Recycling, EcoMint, EcoPrimary)
    }
}

/** Latar berwarna dengan ikon kategori di tengah, pengganti foto stok. */
@Composable
fun CategoryArt(
    categoryId: String?,
    categoryName: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
) {
    val style = categoryStyle(categoryId, categoryName)
    Box(modifier.background(style.background), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(iconSize * 1.8f)
                .background(Color.White.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.foreground, modifier = Modifier.size(iconSize))
        }
    }
}

/** Ikon kategori kecil untuk chip. */
@Composable
fun CategoryIcon(categoryId: String?, categoryName: String?, modifier: Modifier = Modifier) {
    val style = categoryStyle(categoryId, categoryName)
    Icon(style.icon, contentDescription = null, tint = style.foreground, modifier = modifier.size(18.dp))
}

/**
 * Sampul listing: foto penyedia bila ada, selain itu ilustrasi kategori.
 * Ilustrasi tetap berada di bawah foto sehingga menjadi fallback saat foto gagal dimuat.
 */
@Composable
fun ListingCover(
    photoUrl: String?,
    categoryId: String?,
    categoryName: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
) {
    Box(modifier) {
        CategoryArt(categoryId, categoryName, Modifier.matchParentSize(), iconSize)
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
