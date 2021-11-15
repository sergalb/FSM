import java.io.File
import java.io.PrintWriter
import kotlin.math.round

const val scale = 32_768

val leafs = mutableSetOf<FSM>()

class FSM(var depth: Int, parent: FSM?, val isZero: Boolean = true) {
    companion object {
        var stateCount = 0
        var root = FSM(0, null)
    }

    val parents: MutableSet<FSM> = parent?.let { mutableSetOf(it) } ?: mutableSetOf()

    init {
        stateCount++
        parent?.also { leafs.remove(it) }
        leafs.add(this)
    }

    var countNextZero = 0
    var count = 0L
    var byZero: FSM? = null
    var byOne: FSM? = null
    var cachedSize = 0L
    var id: Int = 0

    fun transition(bit: Int): FSM {
        count++
        if (stateCount > scale * 0.9 && (bit == 0 && byZero == null || bit == 1 && byOne == null)) {
            if (bit == 0) countNextZero++
            return root
        }
        val res = if (bit == 0) {
            countNextZero++
            if (byZero == null) byZero = FSM(depth + 1, this)
            byZero!!
        } else {
            if (byOne == null) byOne = FSM(depth + 1, this, false)
            byOne!!
        }
//        if (stateCount == 1 shl 15 && Random.nextInt(0, 100) < 5) truncate()
//        if (res.depth > 700 && Random.nextInt(0, 100) < 5) res.trancateBamboo()
        return res

    }

    private fun trancateBamboo() {
        val parent = parents.firstOrNull()
        if (parent != null) {
            if (parent.byZero == null && parent.byOne == null) {
                if (isZero) {
                    parent.byZero = byZero
                } else {
                    parent.byOne = byOne
                }
                parent.count += count
                parent.countNextZero += countNextZero
                stateCount--
            }
            parent.trancateBamboo()
            depth = parent.depth + 1
        }
    }

    fun truncate() {
        leafs.removeIf { it.byZero != null || it.byOne != null }
        leafs.filter { it.count == 0L && it.parents.size == 1 }.map { it.parents.first() }
            .groupBy { it.prob() }
            .forEach { (_, u) ->
                val merged = u.fold(FSM(u.first().depth, null, isZero = u.first().isZero)) { acc, fsm ->
                    acc.merge(fsm)
                }
                merged.parents.groupBy { it.prob() }
                stateCount -= u.size
            }
    }

    fun merge(other: FSM?): FSM {
        if (other == null || other == this) return this
//        parents.addAll(other.parents)
        count += other.count
        countNextZero += other.countNextZero
        byZero = byZero?.merge(other.byZero) ?: other.byZero
        byOne = byOne?.merge(other.byOne) ?: other.byOne
        return this
    }

    fun <R> dfs(defZero: R, defOne: R, accum: (own: R, zero: R, one: R) -> R, f: (t: FSM) -> R): R {
        return accum(
            f(this),
            byZero?.dfs(defZero, defOne, accum, f) ?: defZero,
            byOne?.dfs(defZero, defOne, accum, f) ?: defOne
        )
    }

    fun print(printWriter: PrintWriter) {
        printWriter.println("${byZero?.id ?: 0}, ${byOne?.id ?: 0}, ${prob()}")
        byZero?.print(printWriter)
        byOne?.print(printWriter)
    }

    fun calcId(prev: Int): Int {
        id = prev
        val left = byZero?.calcId(id + 1) ?: id
        val right = byOne?.calcId(left + 1) ?: left
        return right
    }

    fun prob() = round(countNextZero.toDouble() * scale / count.toDouble()).toInt()


    fun calcSize(): Long {
        cachedSize = count + (byOne?.calcSize() ?: 0L) + (byZero?.calcSize() ?: 0L)
        return cachedSize
    }
}


fun main(args: Array<String>) {
    val fsm = FSM.root
    val counters = MutableList(0x100 shl 16) { fsm to 0 }
    var context = 1 shl 16
    File(args[0]).forEachBlock { inp, size ->
        var remind = size
        context = inp.fold(context) { ctx, byte ->
            if (remind == 0) return@fold ctx
            remind--
            addByte(byte, ctx, counters)
        }
    }
//    for (i in 0..100_000) {
//        fsm.truncate()
//        if (FSM.stateCount <= scale) break
//        if (i % 1000 == 0) println(FSM.stateCount)

//    }
//    println(FSM.stateCount)
    fsm.calcId(0)
    File(args[1]).printWriter().use { fsm.print(it) }
}

fun addByte(byte: Byte, context: Int, counters: MutableList<Pair<FSM, Int>>): Int {
    var ctx = context
    for (bitInd in 7 downTo 0) {
        val bit = getBit(byte, bitInd)
        counters[ctx] = counters[ctx].first.transition(bit) to counters[ctx].second + 1
        ctx = (ctx shl 1) or bit
    }
    ctx = (ctx and 0xFF) or (1 shl 16)
    return ctx
}


fun getBit(value: Byte, position: Int): Int {
    return (value.toInt() shr position) and 1
}

