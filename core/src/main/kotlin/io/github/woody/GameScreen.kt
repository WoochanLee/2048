package io.github.woody

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.abs
import kotlin.math.min


class GameScreen(private val game: My2048Game) : Screen {
    private val VIRTUAL_W = 450f
    private val VIRTUAL_H = 800f

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(VIRTUAL_W, VIRTUAL_H, camera)

    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()

    private val tileFont = BitmapFont(Gdx.files.internal("fonts/roboto_32.fnt"))
    private val hudFont = BitmapFont(Gdx.files.internal("fonts/roboto_32.fnt")).apply {
        data.setScale(0.8f)
    }
    private val titleFont = BitmapFont(Gdx.files.internal("fonts/roboto_32.fnt"))

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
    private val sOver: Sound = Gdx.audio.newSound(Gdx.files.internal("sounds/gameover.wav"))
    private val bgMusic: Music = Gdx.audio.newMusic(Gdx.files.internal("sounds/background.mp3"))

    init {
        board.reset()
        tileFont.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        hudFont.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        titleFont.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        titleFont.color = Color(0.47f, 0.43f, 0.40f, 1f)
    }

    override fun show() {
        bgMusic.isLooping = true
        bgMusic.volume = 0.2f
        bgMusic.play()
    }

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

        Gdx.gl.glClearColor(0.98f, 0.97f, 0.95f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        drawBoard()
        drawTitle()
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
            if (board.lastSpawn != null) {
                spawnAnimTime = spawnDuration
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
        val radius = 8f
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Board background
        shapes.color = Color(0.80f, 0.76f, 0.72f, 1f)
        shapes.rect(m.left, m.bottom, m.gridSize, m.gridSize)

        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (animTime > 0f && skipCellsForMerge.contains(r to c)) {
                    // skip drawing merged destination tile during slide to avoid double-draw
                    continue
                }
                val value = board.grid[r][c]
                shapes.color = tileColor(value)

                // Original position and size
                val x = m.left + gridGap + c * (m.tileSize + gridGap)
                val y = m.bottom + gridGap + r * (m.tileSize + gridGap)
                var size = m.tileSize
                var drawX = x
                var drawY = y

                // Check for spawn animation
                if (spawnAnimTime > 0f && board.lastSpawn == r to c) {
                    val t = 1f - (spawnAnimTime / spawnDuration)
                    val scale = t * t * (3 - 2 * t) // smoothstep for 0.0 -> 1.0
                    size *= scale
                    drawX = x + (m.tileSize - size) / 2f
                    drawY = y + (m.tileSize - size) / 2f
                }
                shapes.roundedRect(drawX, drawY, size, size, radius)
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
                shapes.roundedRect(x, y, m.tileSize, m.tileSize, radius)
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
                    // grow scale from 0.0 -> 1.0
                    val t = 1f - (spawnAnimTime / spawnDuration)
                    t * t * (3 - 2 * t) // smoothstep
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
        layout.setText(tileFont, text)
        tileFont.color = Color.WHITE
        tileFont.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
    }

    private fun cellCenter(m: QuadMetrics, r: Int, c: Int): Pair<Float, Float> {
        val x = m.left + gridGap + c * (m.tileSize + gridGap) + m.tileSize / 2f
        val y = m.bottom + gridGap + r * (m.tileSize + gridGap) + m.tileSize / 2f
        return x to y
    }

    private fun drawHUD() {
        batch.begin()
        hudFont.color = Color(0.3f, 0.3f, 0.3f, 1f)
        layout.setText(hudFont, "SCORE: ${board.score}")
        hudFont.draw(batch, layout, padding, VIRTUAL_H - padding)
        layout.setText(hudFont, "Best: ${board.best}")
        hudFont.draw(batch, layout, VIRTUAL_W - padding - layout.width, VIRTUAL_H - padding)
        batch.end()
    }

    private fun drawTitle() {
        batch.begin()
        val m = gridMetrics()
        val boardTop = m.bottom + m.gridSize
        val titleText = "Money Merge Puzzle"
        layout.setText(titleFont, titleText)
        val titleY = boardTop - 20f + (VIRTUAL_H - boardTop - layout.height) / 2f
        titleFont.draw(batch, layout, (VIRTUAL_W - layout.width) / 2f, titleY + layout.height)
        batch.end()
    }

    private fun drawOverlay() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.98f, 0.97f, 0.95f, 0.7f)
        shapes.rect(0f, 0f, VIRTUAL_W, VIRTUAL_H)
        shapes.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        val msg = when {
            board.lost -> "Game Over!\n\nScore: ${board.score}\n\nTap to Restart"
            board.won -> "You Win!\n\nScore: ${board.score}\n\nKeep Going or Press R to Restart"
            else -> ""
        }
        batch.begin()
        hudFont.color = Color(0.47f, 0.43f, 0.40f, 1f)
        // First, use the layout to determine the height of the wrapped text
        layout.setText(hudFont, msg, Color.WHITE, VIRTUAL_W, com.badlogic.gdx.utils.Align.center, true)
        // Now, draw the text centered horizontally across the entire screen
        hudFont.draw(batch, msg, 0f, (VIRTUAL_H + layout.height) / 2f, VIRTUAL_W, com.badlogic.gdx.utils.Align.center, true)
        batch.end()
    }

    private fun tileColor(value: Int): Color {
        return when (value) {
            0 -> Color(0.80f, 0.76f, 0.72f, 1f)
            2 -> Color(0.93f, 0.89f, 0.85f, 1f)
            4 -> Color(0.93f, 0.88f, 0.76f, 1f)
            8 -> Color(0.95f, 0.69f, 0.47f, 1f)
            16 -> Color(0.96f, 0.58f, 0.39f, 1f)
            32 -> Color(0.96f, 0.49f, 0.37f, 1f)
            64 -> Color(0.96f, 0.37f, 0.23f, 1f)
            128 -> Color(0.93f, 0.81f, 0.45f, 1f)
            256 -> Color(0.93f, 0.80f, 0.38f, 1f)
            512 -> Color(0.93f, 0.78f, 0.31f, 1f)
            1024 -> Color(0.93f, 0.77f, 0.25f, 1f)
            2048 -> Color(0.93f, 0.76f, 0.18f, 1f)
            else -> Color(0.20f, 0.20f, 0.20f, 1f)
        }
    }

    private fun ShapeRenderer.roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        // Central rectangle
        rect(x + radius, y, width - 2 * radius, height)
        // Side rectangles
        rect(x, y + radius, width, height - 2 * radius)

        // Four arches
        arc(x + radius, y + radius, radius, 180f, 90f)
        arc(x + width - radius, y + radius, radius, 270f, 90f)
        arc(x + width - radius, y + height - radius, radius, 0f, 90f)
        arc(x + radius, y + height - radius, radius, 90f, 90f)
    }

    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)
    override fun pause() {
        bgMusic.pause()
    }
    override fun resume() {
        bgMusic.play()
    }
    override fun hide() {
        bgMusic.stop()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        tileFont.dispose()
        hudFont.dispose()
        titleFont.dispose()
        sMove.dispose()
        sOver.dispose()
        bgMusic.dispose()
    }
}

data class QuadMetrics(val left: Float, val bottom: Float, val tileSize: Float, val gridSize: Float)
