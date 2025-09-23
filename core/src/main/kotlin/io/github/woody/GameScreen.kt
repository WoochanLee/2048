// ==============================
// File: core/src/main/kotlin/com/yourcompany/gdx2048/GameScreen.kt
// ==============================
package io.github.woody

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.abs
import kotlin.math.min

class GameScreen(private val game: My2048Game) : Screen {
    private val VIRTUAL_W = 480f
    private val VIRTUAL_H = 800f

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(VIRTUAL_W, VIRTUAL_H, camera)

    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont()
    private val layout = GlyphLayout()

    private val board = Board()

    // swipe detection
    private var touchStart: Vector2? = null
    private val minSwipe = 24f

    // visuals
    private val padding = 24f
    private val gridGap = 10f

    // animation state
    private var animTime = 0f
    private val slideDuration = 0.12f
    private var currentAnims: List<Anim> = emptyList()
    private var skipCellsForMerge: MutableSet<Pair<Int,Int>> = mutableSetOf()
    private var spawnAnimTime = 0f
    private val spawnDuration = 0.10f

    // sounds (put small wav/ogg files under assets/sounds)
    private val sMove: Sound = Gdx.audio.newSound(Gdx.files.internal("sounds/move.wav"))
    private val sMerge: Sound = Gdx.audio.newSound(Gdx.files.internal("sounds/merge.wav"))
    private val sSpawn: Sound = Gdx.audio.newSound(Gdx.files.internal("sounds/spawn.wav"))
    private val sOver: Sound = Gdx.audio.newSound(Gdx.files.internal("sounds/gameover.wav"))

    init {
        board.reset()
        font.data.setScale(2f)
    }

    override fun show() {}

    override fun render(delta: Float) {
        handleInput()

        // advance animations
        if (animTime > 0f) {
            animTime -= delta
            if (animTime <= 0f) {
                animTime = 0f
                currentAnims = emptyList()
                skipCellsForMerge.clear()
            }
        }
        if (spawnAnimTime > 0f) {
            spawnAnimTime -= delta
            if (spawnAnimTime < 0f) spawnAnimTime = 0f
        }

        Gdx.gl.glClearColor(0.95f, 0.93f, 0.88f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        drawBoard()
        drawHUD()

        if (board.lost || board.won) drawOverlay()
    }

    private fun handleInput() {
        if (board.lost) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.justTouched()) {
                board.reset()
                spawnAnimTime = 0f
                animTime = 0f
            }
            return
        }

        if (animTime > 0f) return // don't accept input while animating

        var moved = false
        // Keyboard
        when {
            Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A) -> moved = board.move(Direction.LEFT)
            Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D) -> moved = board.move(Direction.RIGHT)
            Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W) -> moved = board.move(Direction.UP)
            Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S) -> moved = board.move(Direction.DOWN)
        }

        // Touch / Mouse swipe
        if (!moved) {
            if (Gdx.input.justTouched()) {
                touchStart = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            }
            if (touchStart != null && !Gdx.input.isTouched) {
                val end = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
                val start = touchStart!!
                viewport.unproject(start)
                viewport.unproject(end)
                val dx = end.x - start.x
                val dy = end.y - start.y
                if (Vector2(dx, dy).len() > minSwipe) {
                    moved = if (abs(dx) > abs(dy)) {
                        if (dx > 0) board.move(Direction.RIGHT) else board.move(Direction.LEFT)
                    } else {
                        if (dy > 0) board.move(Direction.UP) else board.move(Direction.DOWN)
                    }
                }
                touchStart = null
            }
        }

        if (moved) {
            // set up animations and sounds
            currentAnims = board.lastAnims.toList()
            skipCellsForMerge = currentAnims.filter { it.merged }.map { it.toR to it.toC }.toMutableSet()
            animTime = if (currentAnims.isNotEmpty()) slideDuration else 0f
            if (currentAnims.isNotEmpty()) sMove.play(0.2f)
            if (currentAnims.any { it.merged }) sMerge.play(0.25f)
            if (board.lastSpawn != null) {
                spawnAnimTime = spawnDuration
                sSpawn.play(0.2f)
            }
            if (board.lost) sOver.play(0.4f)
        }
        // Quick reset
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) board.reset()
    }

    private fun gridMetrics(): QuadMetrics {
        val gridSize = min(VIRTUAL_W - padding * 2, VIRTUAL_H - padding * 2 - 140f)
        val tileSize = (gridSize - gridGap * 5) / 4f
        val left = (VIRTUAL_W - gridSize) * 0.5f
        val bottom = (VIRTUAL_H - gridSize) * 0.5f - 40f
        return QuadMetrics(left, bottom, tileSize, gridSize)
    }

    private fun drawBoard() {
        val m = gridMetrics()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Board background
        shapes.color = Color(0.80f, 0.75f, 0.70f, 1f)
        shapes.rect(m.left, m.bottom, m.gridSize, m.gridSize)

        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (animTime > 0f && skipCellsForMerge.contains(r to c)) {
                    // skip drawing merged destination tile during slide to avoid double-draw
                    continue
                }
                val x = m.left + gridGap + c * (m.tileSize + gridGap)
                val y = m.bottom + gridGap + r * (m.tileSize + gridGap)
                val value = board.grid[r][c]
                shapes.color = tileColor(value)
                shapes.rect(x, y, m.tileSize, m.tileSize)
            }
        }
        shapes.end()

        // Draw moving tiles (overlay)
        if (animTime > 0f && currentAnims.isNotEmpty()) {
            val t = 1f - (animTime / slideDuration)
            val ease = t * t * (3 - 2 * t) // smoothstep
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            for (a in currentAnims) {
                val sx = m.left + gridGap + a.fromC * (m.tileSize + gridGap)
                val sy = m.bottom + gridGap + a.fromR * (m.tileSize + gridGap)
                val ex = m.left + gridGap + a.toC * (m.tileSize + gridGap)
                val ey = m.bottom + gridGap + a.toR * (m.tileSize + gridGap)
                val x = sx + (ex - sx) * ease
                val y = sy + (ey - sy) * ease
                shapes.color = tileColor(a.value)
                shapes.rect(x, y, m.tileSize, m.tileSize)
            }
            shapes.end()
        }

        // Numbers
        batch.begin()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (animTime > 0f && skipCellsForMerge.contains(r to c)) continue
                val value = board.grid[r][c]
                if (value == 0) continue
                val center = cellCenter(m, r, c)
                val scale = if (spawnAnimTime > 0f && board.lastSpawn == r to c) {
                    // pop-in scale from 0.8 -> 1.0
                    val tt = 1f - (spawnAnimTime / spawnDuration)
                    0.8f + 0.2f * (tt * tt * (3 - 2 * tt))
                } else 1f
                drawValue(center.first, center.second, m.tileSize, value, scale)
            }
        }
        // overlay numbers for moving tiles
        if (animTime > 0f && currentAnims.isNotEmpty()) {
            val t = 1f - (animTime / slideDuration)
            val ease = t * t * (3 - 2 * t)
            for (a in currentAnims) {
                val sx = m.left + gridGap + a.fromC * (m.tileSize + gridGap)
                val sy = m.bottom + gridGap + a.fromR * (m.tileSize + gridGap)
                val ex = m.left + gridGap + a.toC * (m.tileSize + gridGap)
                val ey = m.bottom + gridGap + a.toR * (m.tileSize + gridGap)
                val x = sx + (ex - sx) * ease + m.tileSize / 2f
                val y = sy + (ey - sy) * ease + m.tileSize / 2f
                drawNumberAt(x, y, a.value)
            }
        }
        batch.end()
    }

    private fun drawValue(cx: Float, cy: Float, size: Float, value: Int, scale: Float) {
        // draw number at cell center with optional scale (for spawn pop)
        drawNumberAt(cx, cy, value, scale)
    }

    private fun drawNumberAt(cx: Float, cy: Float, value: Int, scale: Float = 1f) {
        val text = value.toString()
        val oldScale = font.data.scaleX
        font.data.setScale(2f * scale)
        layout.setText(font, text)
        font.color = if (value <= 4) Color(0.29f, 0.25f, 0.22f, 1f) else Color.WHITE
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
        font.data.setScale(oldScale)
    }

    private fun cellCenter(m: QuadMetrics, r: Int, c: Int): Pair<Float, Float> {
        val x = m.left + gridGap + c * (m.tileSize + gridGap) + m.tileSize / 2f
        val y = m.bottom + gridGap + r * (m.tileSize + gridGap) + m.tileSize / 2f
        return x to y
    }

    private fun drawHUD() {
        batch.begin()
        font.color = Color(0.3f, 0.3f, 0.3f, 1f)
        layout.setText(font, "SCORE: ${board.score}")
        font.draw(batch, layout, padding, VIRTUAL_H - padding)
        layout.setText(font, "Best: ${board.best}")
        font.draw(batch, layout, VIRTUAL_W - padding - layout.width, VIRTUAL_H - padding)
        batch.end()
    }

    private fun drawOverlay() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, VIRTUAL_W, VIRTUAL_H)
        shapes.end()

        val msg = when {
            board.lost -> "Game Over — Press Space / Tap to Restart"
            board.won -> "You Win! Keep Going or Press R to Restart"
            else -> ""
        }
        batch.begin()
        font.color = Color.WHITE
        font.data.setScale(1.6f)
        layout.setText(font, msg)
        font.draw(batch, layout, (VIRTUAL_W - layout.width) / 2f, (VIRTUAL_H + layout.height) / 2f)
        font.data.setScale(2f)
        batch.end()
    }

    private fun tileColor(value: Int): Color {
        return when (value) {
            0 -> Color(0.90f, 0.87f, 0.82f, 1f)
            2 -> Color(0.93f, 0.89f, 0.78f, 1f)
            4 -> Color(0.93f, 0.88f, 0.74f, 1f)
            8 -> Color(0.94f, 0.68f, 0.47f, 1f)
            16 -> Color(0.96f, 0.58f, 0.39f, 1f)
            32 -> Color(0.96f, 0.48f, 0.37f, 1f)
            64 -> Color(0.96f, 0.36f, 0.23f, 1f)
            128 -> Color(0.93f, 0.81f, 0.44f, 1f)
            256 -> Color(0.93f, 0.80f, 0.40f, 1f)
            512 -> Color(0.93f, 0.78f, 0.36f, 1f)
            1024 -> Color(0.93f, 0.76f, 0.32f, 1f)
            2048 -> Color(0.93f, 0.74f, 0.28f, 1f)
            else -> Color(0.20f, 0.20f, 0.20f, 1f)
        }
    }

    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
        sMove.dispose()
        sMerge.dispose()
        sSpawn.dispose()
        sOver.dispose()
    }
}

data class QuadMetrics(val left: Float, val bottom: Float, val tileSize: Float, val gridSize: Float)
