package com.jankowski.rafal.jobassistant.llm

/**
 * Builds a LangChain4j AI service bound to whichever model profile is configured for [task].
 *
 * The service interfaces themselves live in the modules that own them - the extraction contract
 * is an `analysis` concept, not an `llm` one - so this module never needs to know about job
 * offers or CVs.
 */
interface AiServiceFactory {

    fun <T : Any> create(serviceType: Class<T>, task: LlmTask): T
}

inline fun <reified T : Any> AiServiceFactory.create(task: LlmTask): T = create(T::class.java, task)
