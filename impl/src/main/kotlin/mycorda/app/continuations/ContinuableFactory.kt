package  mycorda.app.continuations

import mycorda.app.registry.Registry
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass

data class ContinuableRegistration(
    val continuable: KClass<out Continuable<*, *>>,
    val asContinuable: KClass<out Continuable<*, *>> = continuable
)

interface ContinuableRegistrations : Iterable<ContinuableRegistration>

/**
 * Register and create instances of Continuables by name or clazz
 */
class ContinuableFactory(private val registry: Registry) {
    private val lookup = HashMap<String, KClass<out Continuable<*, *>>>()

    /**
     * Register using a list of ContinuableRegistrations
     */
    fun register(continuableRegistrations: ContinuableRegistrations): ContinuableFactory {
        continuableRegistrations.forEach { register(it) }
        return this
    }

    /**
     * Register using a ContinuableRegistration
     */
    fun register(continuableRegistration: ContinuableRegistration): ContinuableFactory {
        register(continuableRegistration.continuable, continuableRegistration.asContinuable)
        return this
    }

    /**
     * Register the class, taking the class name as the registered name
     * - continuable = the implementingClass
     * - asContinuable = the interface (if different)
     */
    fun register(
        continuable: KClass<out Continuable<*, *>>,
        asContinuable: KClass<out Continuable<*, *>> = continuable
    ): ContinuableFactory {
        val name = asContinuable.qualifiedName!!
        if (lookup.containsKey(name)) throw RuntimeException("`$name` is already registered")
        lookup[name] = continuable
        return this
    }

    fun list(): List<String> {
        return lookup.keys.sorted()
    }

    /**
     * Create an instance of a Continuable by fully qualified name. This
     * is the "core" factory method, but in most cases one of the
     * more type safe variants will result in cleaner code.
     *
     */
    @Suppress("UNCHECKED_CAST")
    fun <I, O> createInstance(
        qualifiedName: String,
        continuationId: ContinuationId = ContinuationId.random()
    ): Continuable<I, O> {
        if (!lookup.containsKey(qualifiedName)) {
            throw RuntimeException("Continuable: `$qualifiedName` is not registered")
        }
        val clazz = lookup[qualifiedName]!!

        // try with Registry
        clazz.constructors.forEach {
            if (it.parameters.size == 2) {
                val p1Clazz = it.parameters[0].type.classifier as KClass<Any>
                val p2Clazz = it.parameters[1].type.classifier as KClass<Any>

                if ((p1Clazz == Registry::class) && (p2Clazz == ContinuationId::class)) {
                    try {
                        return it.call(registry, continuationId) as Continuable<I, O>
                    } catch (itex: InvocationTargetException) {
                        throw RuntimeException("Problem instantiating `$qualifiedName`. Original error: `${itex.targetException.message}`")
                    } catch (ex: Exception) {
                        throw RuntimeException("Problem instantiating `$qualifiedName`. Original error: `${ex.message}`")
                    }
                }
            }
        }

        throw RuntimeException("Couldn't find a suitable constructor for Continuable: `$qualifiedName`")
    }

    fun <I, O> createInstance(
        continuableClazz: KClass<out Continuable<I, O>>,
        continuationId: ContinuationId = ContinuationId.random()
    ): Continuable<I, O> {
        val continuableName: String = continuableClazz.qualifiedName!!
        return createInstance(continuableName, continuationId)
    }
}