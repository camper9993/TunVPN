package ru.tipowk.tunvpn

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.koin.android.ext.android.inject
import ru.tipowk.tunvpn.data.AndroidVpnController
import ru.tipowk.tunvpn.data.VpnController
import ru.tipowk.tunvpn.vpn.VpnConnectionManager

class MainActivity : ComponentActivity() {

    private val vpnController: VpnController by inject()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnConnectionManager.onVpnPermissionGranted(this)
        } else {
            VpnConnectionManager.onVpnPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Bind the VPN controller to this activity for permission handling
        (vpnController as? AndroidVpnController)?.bind(this) { intent ->
            vpnPermissionLauncher.launch(intent)
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        (vpnController as? AndroidVpnController)?.unbind()
        super.onDestroy()
    }
}
