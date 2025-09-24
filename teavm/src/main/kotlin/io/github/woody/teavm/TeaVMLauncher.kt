@file:JvmName("TeaVMLauncher")

package io.github.woody.teavm

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import io.github.woody.My2048Game
import org.teavm.jso.dom.html.HTMLDocument
import org.teavm.jso.dom.html.HTMLElement

/** Launches the TeaVM/HTML application. */
fun main() {
    val config = TeaApplicationConfiguration("canvas").apply {
        //// If width and height are each greater than 0, then the app will use a fixed size.
        //width = 640
        //height = 480
        //// If width and height are both 0, then the app will use all available space.
        //width = 0
        //height = 0
        //// If width and height are both -1, then the app will fill the canvas size.
        width = -1
        height = -1
    }
    TeaApplication(My2048Game(), config)
    val document = HTMLDocument.current()
    val body = document.body
    body.style.setProperty("margin", "0")
    body.style.setProperty("padding", "0")
    body.style.setProperty("height", "100vh")
    body.style.setProperty("display", "flex")
    body.style.setProperty("justify-content", "center")
    body.style.setProperty("align-items", "center")
    body.style.setProperty("background-color", "#333")

    val canvas = document.getElementById("canvas") as HTMLElement
    canvas.style.setProperty("width", "100%")
    canvas.style.setProperty("height", "100%")
}
