package com.samdevelop.samtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.samdevelop.samtv.navigation.AppNavigation
import com.samdevelop.samtv.ui.theme.SamTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamTvTheme { AppNavigation() }
        }
    }
}
