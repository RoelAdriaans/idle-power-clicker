package com.example.idlepowerhelper

/** A swipe from one grid cell to another. */
data class MergeMove(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int) {
    override fun toString() = "(${fromRow},${fromCol}) → (${toRow},${toCol})"
}

object BatteryMerger {

    /**
     * Full-grid recursive merge sequence targeting corner (0,3).
     *
     * Uses all 16 cells in a snake path through the grid:
     *
     *   (3,3)→(3,2)→(3,1)→(3,0)→(2,0)→(2,1)→(2,2)→(2,3)
     *                              ↓
     *   (1,3)←(1,2)←(1,1)←(1,0)←╯
     *     ↓
     *   (0,0)→(0,1)→(0,2)→(0,3) ← target
     *
     * Leaf = (3,3), always tier A.  Each cell's max reachable tier equals its
     * position in the path + 1, so the target (0,3) reaches tier 16.
     *
     * Total sequence length: 2^15 − 1 = 32 767 valid same-tier merges per cycle.
     */
    private val SEQUENCE: List<MergeMove> by lazy { generateSequence() }

    val SWEEP_SIZE: Int get() = SEQUENCE.size

    fun sweepMove(iteration: Int): MergeMove =
        SEQUENCE[((iteration % SEQUENCE.size) + SEQUENCE.size) % SEQUENCE.size]

    // ── Snake-path tables ─────────────────────────────────────────────────────

    // Index of each cell in the snake path (leaf = 0, target = 15).
    private val PATH_INDEX: Map<Pair<Int,Int>, Int> = mapOf(
        (3 to 3) to  0,
        (3 to 2) to  1,
        (3 to 1) to  2,
        (3 to 0) to  3,
        (2 to 0) to  4,
        (2 to 1) to  5,
        (2 to 2) to  6,
        (2 to 3) to  7,
        (1 to 3) to  8,
        (1 to 2) to  9,
        (1 to 1) to 10,
        (1 to 0) to 11,
        (0 to 0) to 12,
        (0 to 1) to 13,
        (0 to 2) to 14,
        (0 to 3) to 15
    )

    // Each cell's single source = the previous cell in the snake path.
    private val PARENT: Map<Pair<Int,Int>, Pair<Int,Int>> = mapOf(
        (3 to 2) to (3 to 3),
        (3 to 1) to (3 to 2),
        (3 to 0) to (3 to 1),
        (2 to 0) to (3 to 0),
        (2 to 1) to (2 to 0),
        (2 to 2) to (2 to 1),
        (2 to 3) to (2 to 2),
        (1 to 3) to (2 to 3),
        (1 to 2) to (1 to 3),
        (1 to 1) to (1 to 2),
        (1 to 0) to (1 to 1),
        (0 to 0) to (1 to 0),
        (0 to 1) to (0 to 0),
        (0 to 2) to (0 to 1),
        (0 to 3) to (0 to 2)
    )

    private fun maxTier(r: Int, c: Int): Int = (PATH_INDEX[r to c] ?: 0) + 1
    private fun source(r: Int, c: Int): Pair<Int, Int>? = PARENT[r to c]

    // ── Sequence generation ───────────────────────────────────────────────────

    private fun generateSequence(): List<MergeMove> {
        val grid = Array(4) { IntArray(4) { 1 } }
        val moves = mutableListOf<MergeMove>()
        while (tryAdvance(grid, moves, 0, 3)) { }
        return moves
    }

    /**
     * Advances grid[r][c] by one tier.
     * Returns false when (r,c) has reached its max tier.
     */
    private fun tryAdvance(
        grid: Array<IntArray>,
        moves: MutableList<MergeMove>,
        r: Int,
        c: Int
    ): Boolean {
        if (grid[r][c] >= maxTier(r, c)) return false
        val (sr, sc) = source(r, c) ?: return false
        val needed = grid[r][c]
        buildTo(grid, moves, sr, sc, needed)   // always succeeds: maxTier(src) >= needed
        moves.add(MergeMove(sr, sc, r, c))
        grid[r][c]++
        grid[sr][sc] = 1                        // source refills to A
        return true
    }

    /**
     * Advances grid[r][c] up to [tier] by repeatedly calling tryAdvance.
     * Caller guarantees [tier] <= maxTier(r,c).
     */
    private fun buildTo(
        grid: Array<IntArray>,
        moves: MutableList<MergeMove>,
        r: Int,
        c: Int,
        tier: Int
    ) {
        while (grid[r][c] < tier) tryAdvance(grid, moves, r, c)
    }
}

