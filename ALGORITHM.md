# Merge Algorithm — Tower of Hanoi on a 4×4 Grid

## The Game Rules

The game has a 4×4 grid of batteries, each with a **tier** (A, B, C, …).

- Drag source cell **S** onto target cell **T**: if both are the **same tier**, T advances one tier and S **resets to tier A**.
- If the tiers don't match, nothing happens.

Goal: build the highest possible tier battery in the target corner **(row 0, col 3)**.

---

## The Snake Path

Instead of treating the grid as 16 independent cells, the algorithm assigns every cell a fixed role in a **chain**. The chain visits all 16 cells in a snake pattern:

```
(3,3)→(3,2)→(3,1)→(3,0)→(2,0)→(2,1)→(2,2)→(2,3)
                                              ↓
(1,3)←(1,2)←(1,1)←(1,0)←────────────────────╯
  ↓
(0,0)→(0,1)→(0,2)→(0,3)  ← TARGET
```

Each cell's **source** is the previous cell in the chain:

| Cell   | Source  | Max tier |
|--------|---------|----------|
| (3,3)  | —       | 1 (leaf) |
| (3,2)  | (3,3)   | 2        |
| (3,1)  | (3,2)   | 3        |
| (3,0)  | (3,1)   | 4        |
| (2,0)  | (3,0)   | 5        |
| (2,1)  | (2,0)   | 6        |
| (2,2)  | (2,1)   | 7        |
| (2,3)  | (2,2)   | 8        |
| (1,3)  | (2,3)   | 9        |
| (1,2)  | (1,3)   | 10       |
| (1,1)  | (1,2)   | 11       |
| (1,0)  | (1,1)   | 12       |
| (0,0)  | (1,0)   | 13       |
| (0,1)  | (0,0)   | 14       |
| (0,2)  | (0,1)   | 15       |
| (0,3)  | (0,2)   | **16**   |

The **leaf** `(3,3)` is always tier A and never needs to be built — it acts as an infinite supplier.  
The **target** `(0,3)` can reach tier 16 (the 16th letter of the alphabet: P).

---

## The Core Idea: Binary Counting

The chain behaves exactly like **binary digits**:

- The leaf `(3,3)` is bit 0 — always 1 (A).
- Each successive cell is the next bit.
- "Advancing" a cell one tier = **flipping a bit from 0→1**.
- When a merge fires, the source resets to A = **the carry propagates**.

Advancing the target from tier 1 to tier 16 is equivalent to **counting from 0 to 2¹⁵ in binary**, one increment at a time. That takes exactly **2¹⁵ − 1 = 32 767 merges**.

---

## The Recursion Explained

```
tryAdvance(cell):
    if cell is already at its max tier → stop (done)
    src = source of cell
    needed = cell's current tier          ← src must match this to merge
    buildTo(src, needed)                  ← recursively raise src to match
    merge(src → cell)                     ← the actual swipe
    cell advances one tier, src resets to A

buildTo(cell, tier):
    while cell.tier < tier:
        tryAdvance(cell)                  ← keep advancing until we reach the target tier
```

**Key guarantee:** `buildTo` always succeeds because `maxTier(src) = maxTier(cell) − 1 ≥ needed`. The source can always be built high enough.

---

## Worked Example: Building (0,3) to Tier C

Starting state: all cells at tier A (1).

### Step 1 — advance (0,3) from A→B (tier 1→2)

```
tryAdvance(0,3):
  needed = 1 (current tier of (0,3))
  src = (0,2), currently at tier A = 1  ← already matches, no build needed
  MERGE (0,2) → (0,3)
  (0,3) = B,  (0,2) resets to A
```

**Move 1:** `(0,2) → (0,3)`

Grid after:
```
 A  A  A  B
 A  A  A  A
 A  A  A  A
 A  A  A  A
```

---

### Step 2 — advance (0,3) from B→C (tier 2→3)

```
tryAdvance(0,3):
  needed = 2  (current tier of (0,3))
  src = (0,2), currently tier A = 1  ← needs to be tier 2, call buildTo((0,2), 2)

  buildTo((0,2), 2):
    tryAdvance(0,2):
      needed = 1
      src = (0,1), tier A = 1  ← matches, no build needed
      MERGE (0,1) → (0,2)
      (0,2) = B,  (0,1) resets to A

  Now (0,2) = B = tier 2  ✓
  MERGE (0,2) → (0,3)
  (0,3) = C,  (0,2) resets to A
```

**Move 2:** `(0,1) → (0,2)`  
**Move 3:** `(0,2) → (0,3)`

Grid after:
```
 A  A  A  C
 A  A  A  A
 A  A  A  A
 A  A  A  A
```

---

### Step 3 — advance (0,3) from C→D (tier 3→4)

This requires `(0,2)` at tier C. Building `(0,2)` to tier C requires `(0,1)` at tier B. Building `(0,1)` to tier B requires `(0,0)` at tier A (already there). The recursion unrolls:

```
tryAdvance(0,3):  needs (0,2) at tier 3
  buildTo(0,2, 3):
    tryAdvance(0,2):  needs (0,1) at tier 2
      buildTo(0,1, 2):
        tryAdvance(0,1):  needs (0,0) at tier 1 ← already A, no build
          MERGE (0,0) → (0,1)                    Move 4
          (0,1)=B, (0,0)=A
      MERGE (0,1) → (0,2)                        Move 5
      (0,2)=B, (0,1)=A
    tryAdvance(0,2):  needs (0,1) at tier 2
      buildTo(0,1, 2):
        tryAdvance(0,1):  needs (0,0) at tier 1 ← already A
          MERGE (0,0) → (0,1)                    Move 6
          (0,1)=B, (0,0)=A
      MERGE (0,1) → (0,2)                        Move 7
      (0,2)=C, (0,1)=A
  MERGE (0,2) → (0,3)                            Move 8
  (0,3)=D, (0,2)=A
```

**Moves 4–8:** `(0,0)→(0,1)`, `(0,1)→(0,2)`, `(0,0)→(0,1)`, `(0,1)→(0,2)`, `(0,2)→(0,3)`

Grid after:
```
 A  A  A  D
 A  A  A  A
 A  A  A  A
 A  A  A  A
```

Notice the pattern of moves so far:
```
Move 1:  (0,2)→(0,3)
Move 2:  (0,1)→(0,2)
Move 3:  (0,2)→(0,3)
Move 4:  (0,0)→(0,1)
Move 5:  (0,1)→(0,2)
Move 6:  (0,0)→(0,1)
Move 7:  (0,1)→(0,2)
Move 8:  (0,2)→(0,3)
```

This is **binary counting**: moves 1–8 mirror the sequence of bit flips when counting 001 → 010 → 011 → 100 → 101 → 110 → 111 → 1000.

---

## Why It Never Makes an Invalid Merge

The invariant: **`buildTo(src, needed)` always completes before the merge fires.**

Because `maxTier(src) = maxTier(cell) − 1`, and `needed = cell's current tier ≤ maxTier(cell) − 1 = maxTier(src)`, the source can always reach `needed`. There are no speculative merges, no tier mismatches, no wasted moves.

---

## Timing: How Long Does One Full Cycle Take?

One full cycle = 32 767 moves, building (0,3) from tier A all the way to tier P (16).

The delay between moves depends on the speed setting:

| Speed      | Delay/move | Total seconds | Total minutes | Total hours |
|------------|-----------|---------------|---------------|-------------|
| Slow       | 1.500 s   | 49 150 s      | 819 min       | **13.7 h**  |
| Normal     | 0.800 s   | 26 214 s      | 437 min       | **7.3 h**   |
| Fast       | 0.350 s   | 11 468 s      | 191 min       | **3.2 h**   |

> Formula: `32 767 moves × delay_per_move`

Note that these are **per cycle** — after reaching tier 16, the sequence restarts and builds toward tier 16 again. The game will automatically provide fresh tier-A batteries (sources refill), so the algorithm runs indefinitely.

### Moves per tier

Not all tiers take the same number of moves to build. Each tier requires **twice as many moves** as the previous one (doubling, like binary):

| Target tier | Moves to reach it (cumulative) | Moves for this tier only |
|-------------|-------------------------------|--------------------------|
| A → B       | 1                             | 1                        |
| B → C       | 3                             | 2                        |
| C → D       | 7                             | 4                        |
| D → E       | 15                            | 8                        |
| E → F       | 31                            | 16                       |
| F → G       | 63                            | 32                       |
| G → H       | 127                           | 64                       |
| H → I       | 255                           | 128                      |
| I → J       | 511                           | 256                      |
| J → K       | 1 023                         | 512                      |
| K → L       | 2 047                         | 1 024                    |
| L → M       | 4 095                         | 2 048                    |
| M → N       | 8 191                         | 4 096                    |
| N → O       | 16 383                        | 8 192                    |
| O → P       | 32 767                        | 16 384                   |

The last tier (O→P) alone requires **16 384 moves** — half the entire sequence.

### Time to reach each tier (Fast / Normal / Slow)

| Target tier | Fast (0.35s) | Normal (0.8s) | Slow (1.5s) |
|-------------|-------------|---------------|-------------|
| B           | < 1 s       | < 1 s         | 1.5 s       |
| D           | 2.5 s       | 5.6 s         | 10.5 s      |
| F           | 10.9 s      | 24.8 s        | 46.5 s      |
| H           | 44.5 s      | ~1.7 min      | ~3.2 min    |
| J           | ~3.0 min    | ~6.8 min      | ~12.8 min   |
| L           | ~12.0 min   | ~27.3 min     | ~51.2 min   |
| N           | ~1.6 h      | ~3.6 h        | ~6.8 h      |
| P (max)     | ~3.2 h      | ~7.3 h        | ~13.7 h     |

The exponential growth means the **first 8 tiers are reached in under a minute** on Fast, while the final tier takes hours.

---



The analogy is exact:

| Idle Power                     | Tower of Hanoi              |
|--------------------------------|-----------------------------|
| Cell at position k in chain    | Disk k                      |
| Tier of a cell                 | Stack the disk is on        |
| Merge S → T                    | Move a disk                 |
| Source resets to A             | Smaller disks must move first|
| `buildTo(src, needed)`         | Move all smaller disks out  |
| 2ⁿ−1 total merges for n cells  | 2ⁿ−1 moves for n disks      |

The sequence of cells touched in each move is identical to the **Gray code** / Hanoi move sequence.

---

## Summary

| Property        | Value                        |
|-----------------|------------------------------|
| Grid size       | 4×4 = 16 cells               |
| Chain length    | 16 cells (snake path)        |
| Leaf cell       | (3,3) — always tier A        |
| Target cell     | (0,3) — reaches tier 16 (P)  |
| Sequence length | 2¹⁵ − 1 = **32 767 moves**   |
| Algorithm class | Tower of Hanoi / binary carry|
| Validity        | 100% — no invalid merges     |
