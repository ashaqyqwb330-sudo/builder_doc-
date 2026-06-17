package com.example.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        
        // Sync tile state to active
        tile.state = Tile.STATE_ACTIVE
        tile.label = "المراقب الذكي"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        // Launch the application MainActivity for rapid, easy access
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        try {
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            // Fallback for different OS variations
            val fallbackIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
        }
    }
}
