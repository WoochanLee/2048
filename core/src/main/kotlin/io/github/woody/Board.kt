package io.github.woody

import com.badlogic.gdx.math.RandomXS128
import kotlin.math.max

enum class Direction { UP, DOWN, LEFT, RIGHT }

// Simple animation descriptor produced by a move() call
data class Anim(
    val fromR: Int, val fromC: Int,
    val toR: Int, val toC: Int,
    val value: Int,
    val merged: Boolean = false
)

class Board {
    val grid = Array(4) { IntArray(4) }
    private val rng = RandomXS128()
    var score = 0
        private set
    var best = 0
        private set
    var won = false
        private set
    var lost = false
        private set

    // animation/event info from the last move
    val lastAnims: MutableList<Anim> = mutableListOf()
    var lastSpawn: Pair<Int, Int>? = null
        private set

    fun reset() {
        for (r in 0 until 4) for (c in 0 until 4) grid[r][c] = 0
        score = 0
        won = false
        lost = false
        lastAnims.clear()
        lastSpawn = null
        spawn()
        spawn()
    }

    fun move(dir: Direction): Boolean {
        if (lost) return false
        val before = snapshot()
        lastAnims.clear()
        lastSpawn = null

        when (dir) {
            Direction.LEFT -> {
                for (r in 0 until 4) {
                    val positions = List(4) { idx -> r to idx }
                    val (line, anims) = compressAndMergeWithMoves(getRow(r), positions)
                    setRow(r, line)
                    lastAnims.addAll(anims)
                }
            }
            Direction.RIGHT -> {
                for (r in 0 until 4) {
                    val positions = List(4) { idx -> r to (3 - idx) }
                    val (lineReversed, anims) = compressAndMergeWithMoves(getRow(r).reversedArray(), positions)
                    setRow(r, lineReversed.reversedArray())
                    lastAnims.addAll(anims)
                }
            }
            Direction.UP -> {
                for (c in 0 until 4) {
                    val positions = List(4) { idx -> (3 - idx) to c }
                    val (colReversed, anims) = compressAndMergeWithMoves(getCol(c).reversedArray(), positions)
                    setCol(c, colReversed.reversedArray())
                    lastAnims.addAll(anims)
                }
            }
            Direction.DOWN -> {
                for (c in 0 until 4) {
                    val positions = List(4) { idx -> idx to c }
                    val (col, anims) = compressAndMergeWithMoves(getCol(c), positions)
                    setCol(c, col)
                    lastAnims.addAll(anims)
                }
            }
        }

        val moved = !equalsSnapshot(before)
        if (moved) {
            spawn()
            best = max(best, score)
            won = won || any { it >= 2048 }
            lost = !canMove()
        }
        return moved
    }

    private fun compressAndMergeWithMoves(line: IntArray, positions: List<Pair<Int, Int>>): Pair<IntArray, List<Anim>> {
        val nonZero = mutableListOf<Pair<Int, Int>>() // value, srcIndex (0..3 along motion direction)
        for (i in 0 until 4) if (line[i] != 0) nonZero.add(line[i] to i)

        val out = IntArray(4)
        val anims = mutableListOf<Anim>()
        var i = 0
        var dest = 0
        while (i < nonZero.size) {
            val (v, src) = nonZero[i]
            if (i + 1 < nonZero.size && nonZero[i + 1].first == v) {
                // merge src and src+
                val (v2, src2) = nonZero[i + 1]
                val newVal = v * 2
                out[dest] = newVal
                score += newVal
                // anim: both move to dest; mark one as merged to signal hiding target while sliding
                val to = positions[dest]
                val fromA = positions[src]
                val fromB = positions[src2]
                anims.add(Anim(fromA.first, fromA.second, to.first, to.second, v, merged = true))
                anims.add(Anim(fromB.first, fromB.second, to.first, to.second, v, merged = true))
                dest++
                i += 2
            } else {
                out[dest] = v
                val to = positions[dest]
                val from = positions[src]
                if (from != to) anims.add(Anim(from.first, from.second, to.first, to.second, v, merged = false))
                dest++
                i += 1
            }
        }
        return out to anims
    }

    private fun getRow(r: Int): IntArray = grid[r].copyOf()
    private fun setRow(r: Int, row: IntArray) { for (c in 0 until 4) grid[r][c] = row[c] }
    private fun getCol(c: Int): IntArray = IntArray(4) { r -> grid[r][c] }
    private fun setCol(c: Int, col: IntArray) { for (r in 0 until 4) grid[r][c] = col[r] }

    private fun spawn() {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until 4) for (c in 0 until 4) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return
        val (r, c) = empty[rng.nextInt(empty.size)]
        grid[r][c] = if (rng.nextFloat() < 0.9f) 2 else 4
        lastSpawn = r to c
    }

    private fun canMove(): Boolean {
        // any empty
        for (r in 0 until 4) for (c in 0 until 4) if (grid[r][c] == 0) return true
        // any mergeable neighbor
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = grid[r][c]
            if (r + 1 < 4 && grid[r + 1][c] == v) return true
            if (c + 1 < 4 && grid[r][c + 1] == v) return true
        }
        return false
    }

    private inline fun any(pred: (Int) -> Boolean): Boolean {
        for (r in 0 until 4) for (c in 0 until 4) if (pred(grid[r][c])) return true
        return false
    }

    private fun snapshot(): IntArray {
        val a = IntArray(16)
        var k = 0
        for (r in 0 until 4) for (c in 0 until 4) { a[k++] = grid[r][c] }
        return a
    }

    private fun equalsSnapshot(a: IntArray): Boolean {
        var k = 0
        for (r in 0 until 4) for (c in 0 until 4) if (grid[r][c] != a[k++]) return false
        return true
    }
}// ==============================
