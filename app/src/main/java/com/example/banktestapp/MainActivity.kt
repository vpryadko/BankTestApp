package com.example.banktestapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.example.banktestapp.ui.AmountPickerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val picker = AmountPickerView(this)
        setContentView(picker)

        // Экран нарисован в собственной логической коробке 418 x 935 и включает
        // свою полосу статуса — системные вставки игнорируем намеренно.
        ViewCompat.setOnApplyWindowInsetsListener(picker) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }
    }
}

