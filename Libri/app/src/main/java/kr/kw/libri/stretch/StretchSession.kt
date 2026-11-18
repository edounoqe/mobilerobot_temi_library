package kr.kw.libri.stretch

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 통과했을 때 흩날리는 종이 조각 — 좌표는 아바타 평면 기준 (x: 가운데 0, y: 바닥 0, 단위는 몸통 길이) */
class Confetti {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var rot = 0f; var spin = 0f
    var flip = 0f; var flipSpeed = 0f
    var size = 0f
    var color = 0
    var lifeMs = 0f; var maxLifeMs = 1f
}

/** 부딪혔을 때 부서지는 벽 조각 — 벽 그림을 격자로 잘라 날린다 */
class Shard {
    var wall = 0
    var col = 0; var row = 0
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var rot = 0f; var spin = 0f
    var zoom = 1f
    var lifeMs = 0f
}

/** 화면 효과 — 종이 조각, 벽 파편, 흔들림, 번쩍임 */
class StageFx {
    val confetti = ArrayList<Confetti>()
    val shards = ArrayList<Shard>()
    /** 화면 흔들림 세기 0~1 */
    var shake = 0f
        private set
    /** 번쩍임 세기 0~1 과 종류 (true = 부딪힘) */
    var flash = 0f
        private set
    var flashHit = false
        private set
    /** 통과 링 진행 0~1 (음수면 없음) */
    var ring = -1f
        private set
    var ringPerfect = false
        private set

    private val rnd = Random(20261118)

    fun pass(grade: Grade) {
        val n = if (grade == Grade.PERFECT) 46 else 20
        repeat(n) {
            val ang = rnd.nextFloat() * 6.2832f
            val speed = 1.8f + rnd.nextFloat() * (if (grade == Grade.PERFECT) 4.6f else 3.2f)
            confetti += Confetti().apply {
                x = (rnd.nextFloat() - 0.5f) * 1.2f
                y = 1.6f + rnd.nextFloat() * 1.4f
                vx = cos(ang) * speed
                vy = sin(ang) * speed + 1.8f
                rot = rnd.nextFloat() * 360f
                spin = (rnd.nextFloat() - 0.5f) * 720f
                flip = rnd.nextFloat() * 6.2832f
                flipSpeed = 6f + rnd.nextFloat() * 8f
                size = 0.09f + rnd.nextFloat() * 0.08f
                color = rnd.nextInt(CONFETTI_COLORS)
                maxLifeMs = 1000f + rnd.nextFloat() * 700f
                lifeMs = maxLifeMs
            }
        }
        ring = 0f
        ringPerfect = grade == Grade.PERFECT
        if (grade == Grade.PERFECT) { flash = 0.5f; flashHit = false }
    }

    fun miss(wallIndex: Int) {
        val tileW = WALL_W / SHARD_COLS
        val tileH = WALL_H / SHARD_ROWS
        for (r in 0 until SHARD_ROWS) for (c in 0 until SHARD_COLS) {
            val cx = -WALL_W / 2f + (c + 0.5f) * tileW
            val cy = WALL_H - (r + 0.5f) * tileH
            // 아바타 가슴에서 바깥으로 튕겨 나간다
            val dx = cx
            val dy = cy - 2.3f
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.3f)
            val speed = 2.2f + rnd.nextFloat() * 3.2f
            shards += Shard().apply {
                wall = wallIndex
                col = c; row = r
                x = cx; y = cy
                vx = dx / len * speed + (rnd.nextFloat() - 0.5f) * 1.5f
                vy = dy / len * speed + 1.5f + rnd.nextFloat() * 2f
                rot = 0f
                spin = (rnd.nextFloat() - 0.5f) * 520f
                zoom = 1f
                lifeMs = SHARD_LIFE_MS
            }
        }
        shake = 1f
        flash = 1f
        flashHit = true
        ring = -1f
    }

    fun update(dtMs: Float) {
        val dt = dtMs / 1000f
        var i = confetti.size - 1
        while (i >= 0) {
            val p = confetti[i]
            p.lifeMs -= dtMs
            if (p.lifeMs <= 0f) { confetti.removeAt(i); i--; continue }
            val drag = 1f - 1.6f * dt
            p.vx *= drag
            p.vy = p.vy * drag - 5.2f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.spin * dt
            p.flip += p.flipSpeed * dt
            i--
        }
        i = shards.size - 1
        while (i >= 0) {
            val s = shards[i]
            s.lifeMs -= dtMs
            if (s.lifeMs <= 0f) { shards.removeAt(i); i--; continue }
            s.vy -= 11f * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.rot += s.spin * dt
            s.zoom += 0.5f * dt
            i--
        }
        shake *= 1f - StretchEngine.ease(dtMs, 170f)
        if (shake < 0.01f) shake = 0f
        flash *= 1f - StretchEngine.ease(dtMs, 200f)
        if (flash < 0.01f) flash = 0f
        if (ring >= 0f) {
            ring += dtMs / RING_MS
            if (ring >= 1f) ring = -1f
        }
    }

    companion object {
        const val SHARD_COLS = 6
        const val SHARD_ROWS = 6
        const val SHARD_LIFE_MS = 950f
        const val CONFETTI_COLORS = 6
        private const val RING_MS = 480f
    }
}

/**
 * 게임 한 판 — 카메라(또는 자동 시연)에서 온 자세를 부드럽게 다듬어 엔진에 넘기고, 화면에 그릴 자세를 만든다.
 */
class StretchSession(val course: StretchCourse, val demo: Boolean) {
    val engine = StretchEngine(course)
    val fx = StageFx()

    /** 화면에 그리는 자세 — 스무딩한 사용자 자세에, 통과 순간에는 구멍 모양을 섞는다 */
    val shown: BodyPose = BodyPose.rest()
    /** 몸(상체)이 카메라에 잡혀 있는가 */
    var tracked = false
        private set
    var legsTracked = false
        private set

    private val observed = BodyPose.rest()     // 카메라가 마지막으로 본 자세
    private val smooth = BodyPose.rest()       // 프레임마다 observed 를 따라가는 자세 (판정에 쓴다)
    private val rest = BodyPose.rest()
    private val pilot = if (demo) DemoPilot() else null
    private var sinceSeenMs = Float.MAX_VALUE
    private var cameraLegs = false

    /** 카메라 프레임 결과 — 사람이 안 보이면 null */
    fun onObservation(o: BodyObservation?) {
        if (o == null || demo) return
        observed.set(o.pose)
        cameraLegs = o.legsTracked
        sinceSeenMs = 0f
    }

    fun frame(dtMs: Float, onEvent: (StretchEvent) -> Unit) {
        if (pilot != null) {
            pilot.update(dtMs, engine)
            observed.set(pilot.pose)
            tracked = true
            legsTracked = true
        } else {
            sinceSeenMs = (sinceSeenMs + dtMs).coerceAtMost(1e7f)
            tracked = sinceSeenMs < LOST_AFTER_MS
            legsTracked = tracked && cameraLegs
            if (!legsTracked) {
                // 다리가 안 보이면(상체만 찍힐 때) 아바타 다리는 목표 동작을 따라간다
                val legs = engine.target?.pose?.pose ?: rest
                for (b in BONES) if (b.isLeg) observed.a[b.ordinal] = legs.a[b.ordinal]
            }
            if (!tracked) observed.chase(rest, StretchEngine.ease(dtMs, 600f))
        }

        smooth.chase(observed, StretchEngine.ease(dtMs, SMOOTH_TAU_MS))
        engine.update(dtMs, smooth, tracked, legsTracked)

        shown.set(smooth)
        val snapPose = engine.snapPose
        if (snapPose != null && engine.snap > 0.001f) shown.chase(snapPose.pose, engine.snap)

        engine.drainEvents { e ->
            if (e is StretchEvent.Judged) {
                if (e.grade == Grade.MISS) fx.miss(e.index) else fx.pass(e.grade)
            } else if (e is StretchEvent.Finished && (engine.result?.stars ?: 0) >= 2) {
                fx.pass(Grade.PERFECT)       // 잘 마쳤으면 한 번 더 축하
            }
            onEvent(e)
        }
        fx.update(dtMs)
    }

    private companion object {
        const val SMOOTH_TAU_MS = 70f       // 카메라(10~30fps) 결과를 화면 주사율로 부드럽게 잇는 시간 상수
        const val LOST_AFTER_MS = 600f      // 이 시간 동안 사람이 안 보이면 놓친 것으로 본다
    }
}
