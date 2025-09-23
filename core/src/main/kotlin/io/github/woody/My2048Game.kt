// Place these files under your core module (e.g., core/src/main/kotlin/com/yourcompany/gdx2048)
// Package name can be anything; keep it consistent across files.


// ==============================
// File: core/src/main/kotlin/com/yourcompany/gdx2048/My2048Game.kt
// ==============================
package io.github.woody


import com.badlogic.gdx.Game


class My2048Game : Game() {
    override fun create() {
        setScreen(GameScreen(this))
    }
}
