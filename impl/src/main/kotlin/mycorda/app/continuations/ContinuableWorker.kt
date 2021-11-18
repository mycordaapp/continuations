package mycorda.app.continuations

import mycorda.app.registry.Registry
import java.util.concurrent.Executors
import kotlin.concurrent.thread


class ContinuableWorker(private val registry: Registry) {
    private data class Scheduled(val continuable: String, val id: ContinuationId, val time: Long)

    private val schedule = ArrayList<Scheduled>()
    private val es = Executors.newFixedThreadPool(10)


    init {
        thread(start = true, block = monitorThread())
    }


    fun schedule(continuable: String, id: ContinuationId, time: Long) {
        synchronized(this) {
            this.schedule.add(Scheduled(continuable, id, time))
        }

    }

    private fun monitorThread(): () -> Unit = {
        synchronized(this) {
            val now = System.currentTimeMillis()
            val ready = schedule.filter { it.time <= now }

            ready.forEach {
                es.execute { workerThread(it.continuable, it.id) }
            }
        }

    }

    private fun workerThread(continuable: String, id: ContinuationId): () -> Unit = {
        println("do something")
    }

}