package dev.cherrypizza.mcserverkit.bootstrap.event

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class EventBus<E> {

    private class Subscription<E>(
        val order: Int,
        val context: CoroutineContext,
        val rollback: (suspend (E) -> Unit)?,
        val handler: suspend (E) -> Unit,
    )

    private val lock = Any()

    /**
     * Единый источник правды: неизменяемый снапшот подписчиков, отсортированный по [Subscription.order].
     * Пересобирается копированием при подписке/отписке, читается из [publish] без лока.
     */
    @Volatile
    private var subscriptions: List<Subscription<E>> = emptyList()

    /**
     * Регистрирует подписчика. [order] задаёт порядок вызова: меньше — раньше. [context] — желаемый контекст
     * исполнения хендлера (обычно диспатчер); по умолчанию — контекст вызывающего [publish].
     *
     * [rollback] (необязательный) — компенсация по семантике саги: вызывается, если ЭТОТ шаг успешно
     * отработал, но последующий шаг события упал (см. [publish]).
     *
     * Возвращает [AutoCloseable] для отписки.
     */
    fun subscribe(
        order: Int = 0,
        context: CoroutineContext = EmptyCoroutineContext,
        rollback: (suspend (E) -> Unit)? = null,
        handler: suspend (E) -> Unit,
    ): AutoCloseable {
        val subscription = Subscription(order, context, rollback, handler)
        synchronized(lock) {
            subscriptions = (subscriptions + subscription).sortedBy { it.order }
        }
        return AutoCloseable {
            synchronized(lock) {
                subscriptions = subscriptions.filter { it !== subscription }
            }
        }
    }

    /**
     * Публикует событие. Подписчики вызываются строго последовательно в порядке [Subscription.order];
     * подряд идущие подписчики с одним диспатчером делят один переход (переиспользование диспатчера).
     *
     * Семантика саги: при первой же ошибке распространение события ПРЕКРАЩАЕТСЯ — последующие подписчики
     * не вызываются, а уже успешно отработавшие шаги компенсируются их [Subscription.rollback] в обратном
     * порядке. Затем исходная ошибка пробрасывается вызывающему (ошибки откатов вешаются как suppressed).
     * [CancellationException] — это отмена, а не ошибка шага: пробрасывается сразу, без отката.
     *
     * Если подписчиков нет — мгновенно возвращается.
     */
    suspend fun publish(event: E) {
        val snapshot = subscriptions    // лок не нужен — читаем неизменяемый снапшот
        if (snapshot.isEmpty()) return

        // успешно отработавшие шаги с rollback — для компенсации при аборте (в обратном порядке)
        val completed = mutableListOf<Subscription<E>>()
        try {
            // снапшот отсортирован по order → идём слева направо, склеивая подряд идущих подписчиков
            // с одним диспатчером в серию: один переход на серию вместо хопа на каждого
            var i = 0
            while (i < snapshot.size) {
                val dispatcher = snapshot[i].context[ContinuationInterceptor]
                var j = i + 1
                while (j < snapshot.size && snapshot[j].context[ContinuationInterceptor] == dispatcher) j++
                val group = snapshot.subList(i, j)
                if (dispatcher == null) invokeAll(group, event, completed)            // на контексте вызывающего
                else withContext(dispatcher) { invokeAll(group, event, completed) }   // один переход на серию
                i = j
            }
        } catch (ce: CancellationException) {
            throw ce                                  // отмена ≠ ошибка шага → без отката
        } catch (primary: Throwable) {
            compensate(completed, event, primary)     // откатываем выполненное и пробрасываем исходную ошибку
            throw primary
        }
    }

    /**
     * Последовательно вызывает хендлеры серии (на уже верном диспатчере); успешный шаг с rollback
     * запоминается в [completed]. Ошибка хендлера пробрасывается (fail-fast) — остаток не выполняется.
     */
    private suspend fun invokeAll(
        group: List<Subscription<E>>,
        event: E,
        completed: MutableList<Subscription<E>>,
    ) {
        for (sub in group) {
            val ctx = sub.context
            // диспатчер уже верный → без хопа; накатываются лишь индивидуальные элементы контекста
            if (ctx === EmptyCoroutineContext) sub.handler(event)
            else withContext(ctx) { sub.handler(event) }
            completed += sub
        }
    }

    /** Компенсирует выполненные шаги в обратном порядке; ошибки откатов вешаются на [primary] как suppressed. */
    private suspend fun compensate(completed: List<Subscription<E>>, event: E, primary: Throwable) {
        for (idx in completed.indices.reversed()) {
            val sub = completed[idx]
            val rollback = sub.rollback ?: continue
            try {
                val ctx = sub.context
                if (ctx === EmptyCoroutineContext) rollback(event)
                else withContext(ctx) { rollback(event) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                primary.addSuppressed(t)
            }
        }
    }
}
