package mycorda.app.continuations

import kotlin.reflect.KClass

data class ContinuableRegistration(
    val continuable: KClass<out Continuable<*, *>>,
    val asContinuable: KClass<out Continuable<*, *>> = continuable
)

interface ContinuableRegistrations : Iterable<ContinuableRegistration> {
    companion object {
        fun fromClazzName(clazzName: String): ContinuableRegistrations {
            val clazz = safeClassForName(clazzName)

            // try wih no params constructor
            clazz.constructors.forEach {
                if (it.parameters.isEmpty()) {
                    try {
                        val registrations = it.call()
                        if (registrations is ContinuableRegistrations) return registrations
                        throw RuntimeException("Must implement ContinuableRegistrations")
                    } catch (ex: Exception) {
                        throw RuntimeException("Problem instantiating `$clazzName`. Original error ${ex.message}")
                    }
                }
            }
            throw RuntimeException("Problem instantiating `$clazzName`. Couldn't find a no args constructor")


        }

        private fun safeClassForName(clazzName: String): KClass<out Any> {
            try {
                return Class.forName(clazzName).kotlin

            } catch (cnfe: ClassNotFoundException) {
                throw RuntimeException("Problem instantiating `$clazzName`. ClassNotFoundException")

            } catch (ex: Exception) {
                throw RuntimeException("Problem instantiating `$clazzName`. Original error ${ex.message}")
            }
        }
    }
}

open class SimpleContinuationsRegistrations(private val registrations: List<ContinuableRegistration>) : ContinuableRegistrations {
    override fun iterator(): Iterator<ContinuableRegistration> {
        return registrations.iterator()
    }
}
