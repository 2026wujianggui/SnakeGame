package com.yuanbao.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.random.Random

class SnakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        const val COLS = 20
        const val ROWS = 28
    }

    enum class State { READY, RUNNING, PAUSED, OVER }

    data class Point(val x: Int, val y: Int)

    // ---- 游戏状态 ----
    @Volatile
    var state: State = State.READY
        private set

    private val snake = mutableListOf<Point>()
    private var food = Point(0, 0)
    private var dir = Point(1, 0)
    private var pendingDir = Point(1, 0)
    private var score = 0
    private var best = 0

    private var loopThread: Thread? = null
    @Volatile
    private var alive = false
    private var lastStepMs = 0L

    private var cell = 0f
    private var offX = 0f
    private var offY = 0f

    var onScoreChanged: ((score: Int, best: Int) -> Unit)? = null
    var onStateChanged: ((state: State) -> Unit)? = null

    private val prefs by lazy {
        context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
    }

    // ---- 画笔 ----
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#101418") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#181E24"); style = Paint.Style.FILL
    }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#141A20"); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2BE07A"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7CF7A4") }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2BE07A") }
    private val bodyPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22B862") }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5A5F") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 44f
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA7B2"); textAlign = Paint.Align.CENTER; textSize = 26f
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F13") }

    init {
        holder.addCallback(this)
        best = prefs.getInt("best", 0)
        reset()
    }

    // ================= 生命周期 =================

    override fun surfaceCreated(h: SurfaceHolder) {
        alive = true
        computeMetrics()
        loopThread = Thread { loop() }.also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, h2: Int) {
        computeMetrics()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        alive = false
        loopThread?.join(500)
        loopThread = null
    }

    private fun computeMetrics() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        cell = minOf(w / COLS, h / ROWS)
        offX = (w - cell * COLS) / 2f
        offY = (h - cell * ROWS) / 2f
        textPaint.textSize = cell * 1.4f
        subPaint.textSize = cell * 0.85f
    }

    // ================= 游戏逻辑 =================

    fun reset() {
        snake.clear()
        val cx = COLS / 2
        val cy = ROWS / 2
        snake.add(Point(cx, cy))
        snake.add(Point(cx - 1, cy))
        snake.add(Point(cx - 2, cy))
        dir = Point(1, 0)
        pendingDir = dir
        score = 0
        spawnFood()
        setState(State.READY)
        emitScore()
    }

    fun start() {
        when (state) {
            State.READY, State.OVER -> { if (state == State.OVER) reset(); setState(State.RUNNING) }
            State.PAUSED -> setState(State.RUNNING)
            State.RUNNING -> Unit
        }
        lastStepMs = System.currentTimeMillis()
    }

    fun pause() {
        if (state == State.RUNNING) setState(State.PAUSED)
    }

    fun toggle() {
        if (state == State.RUNNING) pause() else start()
    }

    private fun setState(s: State) {
        state = s
        post { onStateChanged?.invoke(s) }
    }

    private fun emitScore() {
        post { onScoreChanged?.invoke(score, best) }
    }

    private fun spawnFood() {
        val occupied = snake.toSet()
        val free = mutableListOf<Point>()
        for (x in 0 until COLS) for (y in 0 until ROWS) {
            val p = Point(x, y)
            if (p !in occupied) free.add(p)
        }
        food = if (free.isEmpty()) Point(-1, -1) else free[Random.nextInt(free.size)]
    }

    private fun stepDelay(): Long = (160L - score * 3L).coerceAtLeast(70L)

    private fun step() {
        // 取待转向
        if (pendingDir.x != -dir.x || pendingDir.y != -dir.y) dir = pendingDir

        val head = snake.first()
        val nx = head.x + dir.x
        val ny = head.y + dir.y

        // 撞墙
        if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) { gameOver(); return }

        val next = Point(nx, ny)
        // 撞自己（尾巴那格会让开，不算撞）
        val body = if (next == food) snake else snake.subList(0, snake.size - 1)
        if (body.contains(next)) { gameOver(); return }

        snake.add(0, next)
        if (next == food) {
            score += 10
            if (score > best) {
                best = score
                prefs.edit().putInt("best", best).apply()
            }
            emitScore()
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun gameOver() {
        setState(State.OVER)
    }

    // ================= 绘制 =================

    private fun loop() {
        while (alive) {
            if (state == State.RUNNING) {
                val now = System.currentTimeMillis()
                if (now - lastStepMs >= stepDelay()) {
                    lastStepMs = now
                    step()
                }
            }
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) drawGame(canvas)
            } catch (_: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
            try { Thread.sleep(16) } catch (_: InterruptedException) { }
        }
    }

    private fun drawGame(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (cell <= 0f) return

        val bw = cell * COLS
        val bh = cell * ROWS
        c.drawRect(offX, offY, offX + bw, offY + bh, boardPaint)
        c.drawRect(offX, offY, offX + bw, offY + bh, borderPaint)

        // 棋盘格
        for (y in 0 until ROWS) {
            for (x in 0 until COLS) {
                if ((x + y) % 2 == 1) {
                    c.drawRect(
                        offX + x * cell, offY + y * cell,
                        offX + (x + 1) * cell, offY + (y + 1) * cell, gridPaint
                    )
                }
            }
        }

        // 食物
        if (food.x >= 0) {
            val cx = offX + food.x * cell + cell / 2
            val cy = offY + food.y * cell + cell / 2
            c.drawCircle(cx, cy, cell * 0.36f, foodPaint)
        }

        // 蛇身
        for (i in snake.indices) {
            val p = snake[i]
            val r = RectF(
                offX + p.x * cell + cell * 0.08f,
                offY + p.y * cell + cell * 0.08f,
                offX + (p.x + 1) * cell - cell * 0.08f,
                offY + (p.y + 1) * cell - cell * 0.08f
            )
            val paint = when (i) {
                0 -> headPaint
                else -> if (i % 2 == 0) bodyPaint else bodyPaint2
            }
            c.drawRoundRect(r, cell * 0.22f, cell * 0.22f, paint)
        }

        // 蛇眼
        if (snake.isNotEmpty()) {
            val h = snake[0]
            val cx = offX + h.x * cell + cell / 2
            val cy = offY + h.y * cell + cell / 2
            val r = cell * 0.09f
            val off = cell * 0.20f
            when {
                dir.x > 0 -> { c.drawCircle(cx + off, cy - off, r, eyePaint); c.drawCircle(cx + off, cy + off, r, eyePaint) }
                dir.x < 0 -> { c.drawCircle(cx - off, cy - off, r, eyePaint); c.drawCircle(cx - off, cy + off, r, eyePaint) }
                dir.y > 0 -> { c.drawCircle(cx - off, cy + off, r, eyePaint); c.drawCircle(cx + off, cy + off, r, eyePaint) }
                else -> { c.drawCircle(cx - off, cy - off, r, eyePaint); c.drawCircle(cx + off, cy - off, r, eyePaint) }
            }
        }

        // 遮罩文字
        when (state) {
            State.READY -> overlay(c, "准备好了吗？", "滑动屏幕或点方向键开始")
            State.PAUSED -> overlay(c, "已暂停", "点「继续」接着玩")
            State.OVER -> overlay(c, "游戏结束", "得分 $score　·　点击重来")
            State.RUNNING -> Unit
        }
    }

    private fun overlay(c: Canvas, title: String, sub: String) {
        c.drawColor(Color.parseColor("#B0101418"))
        val cx = width / 2f
        val cy = height / 2f
        c.drawText(title, cx, cy, textPaint)
        c.drawText(sub, cx, cy + cell * 1.8f, subPaint)
    }

    // ================= 输入 =================

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) < 24 && abs(dy) < 24) {
                    // 轻点：开始 / 暂停
                    if (state == State.OVER || state == State.READY) start() else toggle()
                    return true
                }
                if (abs(dx) > abs(dy)) {
                    turn(if (dx > 0) Point(1, 0) else Point(-1, 0))
                } else {
                    turn(if (dy > 0) Point(0, 1) else Point(0, -1))
                }
                if (state == State.READY) setState(State.RUNNING)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun turn(d: Point) {
        // 禁止 180 度掉头
        if (snake.size > 1 && d.x == -dir.x && d.y == -dir.y) return
        pendingDir = d
        if (state == State.READY) {
            setState(State.RUNNING)
            lastStepMs = System.currentTimeMillis()
        }
    }
}
