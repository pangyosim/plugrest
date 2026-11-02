package ai.pleos.plugrest.session

import ai.pleos.plugrest.vehicle.CabinSnapshot
import ai.pleos.plugrest.vehicle.SeatMode
import android.content.Context
import androidx.core.content.edit

/** 프로세스 재생성 대비 Resting 상태·스냅샷 저장. ponytail: DataStore 대신 SharedPreferences(의존성 0). */
class SessionStore(context: Context) {
    private val p = context.getSharedPreferences("rest_session", Context.MODE_PRIVATE)

    fun save(resting: Phase.Resting, snapshot: CabinSnapshot) = p.edit {
        putBoolean("active", true)
        putInt("target", resting.target); putLong("startedAt", resting.startedAt)
        putString("seatMode", resting.seatMode.name); putBoolean("half", resting.halfRecline)
        putNullableInt("backrest", snapshot.backrestAngle); putNullableInt("vent", snapshot.seatVent); putNullableInt("heat", snapshot.seatHeat)
        putNullableInt("ambB", snapshot.ambientBrightness); putNullableInt("ambC", snapshot.ambientColor)
        putNullableBool("cabin", snapshot.cabinLightOn); putNullableBool("hvacPower", snapshot.hvacPower); putNullableBool("recirc", snapshot.hvacRecirculation)
        snapshot.hvacTemp?.let { putFloat("hvacTemp", it) } ?: remove("hvacTemp")
        putNullableInt("hvacFan", snapshot.hvacFan)
    }

    fun load(): Pair<Phase.Resting, CabinSnapshot>? {
        if (!p.getBoolean("active", false)) return null
        val resting = Phase.Resting(
            p.getInt("target", 80), p.getLong("startedAt", 0L),
            SeatMode.valueOf(p.getString("seatMode", SeatMode.NONE.name)!!), p.getBoolean("half", false),
        )
        val snap = CabinSnapshot(
            backrestAngle = int("backrest"), seatVent = int("vent"), seatHeat = int("heat"),
            ambientBrightness = int("ambB"), ambientColor = int("ambC"), cabinLightOn = bool("cabin"),
            hvacTemp = if (p.contains("hvacTemp")) p.getFloat("hvacTemp", 0f) else null,
            hvacFan = int("hvacFan"), hvacPower = bool("hvacPower"), hvacRecirculation = bool("recirc"),
        )
        return resting to snap
    }

    fun clear() = p.edit { clear() }

    private fun int(k: String) = if (p.contains(k)) p.getInt(k, 0) else null
    private fun bool(k: String) = if (p.contains(k)) p.getBoolean(k, false) else null
    private fun android.content.SharedPreferences.Editor.putNullableInt(k: String, v: Int?) { if (v == null) remove(k) else putInt(k, v) }
    private fun android.content.SharedPreferences.Editor.putNullableBool(k: String, v: Boolean?) { if (v == null) remove(k) else putBoolean(k, v) }
}
