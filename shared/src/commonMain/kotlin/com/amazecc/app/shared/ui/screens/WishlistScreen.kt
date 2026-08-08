package com.amazecc.app.shared.ui.screens

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.api.AmazeClient

@Composable
fun WishlistScreen() {
    KeyValueResponseScreen(
        title = "Wishlist",
        description = "Course wishlist",
        loadingText = "Loading wishlist...",
        load = { AmazeClient.getWishlist() }
    )
}
