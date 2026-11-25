package kr.kw.libri.backend

import android.content.Context

/** A device is provisioned once by its operator, not by each visitor. */
data class RobotIdentity(val id: String, val floor: String) {
    companion object {
        private const val PREFS = "robot_identity"
        fun load(context: Context): RobotIdentity? = when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("floor", null)) {
            "L" -> RobotIdentity("temi_L", "L")
            "1" -> RobotIdentity("temi_1", "1")
            else -> null
        }

        fun save(context: Context, floor: String): RobotIdentity {
            require(floor == "L" || floor == "1")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("floor", floor).apply()
            return requireNotNull(load(context))
        }
    }
}
