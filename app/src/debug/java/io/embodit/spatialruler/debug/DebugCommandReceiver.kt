package io.embodit.spatialruler.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.spatialruler.platform.RulerLog

/**
 * Debug builds only. Forwards adb broadcasts to the running Stage (action
 * `io.embodit.spatialruler.DEBUG`); see [DebugCommands] for the command list.
 *
 *   adb -s emulator-5556 shell "am broadcast -a io.embodit.spatialruler.DEBUG -p io.embodit.spatialruler --es cmd place --es a '0.3,0,-1.2'"
 *
 * Extras: `cmd` = command name, `a` = comma-separated numbers, `s` = text argument. For convenience
 * the arguments may also follow the name inside `cmd` (`--es cmd "unit cm"`).
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val a = intent.getStringExtra("a")
        val s = intent.getStringExtra("s")
        RulerLog.i("CMD recv '$cmd' a='$a' s='$s'")
        // broadcasts arrive on the main thread, which is where the ViewModel and the scene live
        DebugCommands.run(context.applicationContext, cmd, a, s)
    }
}
