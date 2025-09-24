package io.github.woody


import com.badlogic.gdx.Game


class My2048Game : Game() {
    override fun create() {
        setScreen(GameScreen(this))
    }
}
