package com.anotherchrisberry.spock.extensions.retry

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.util.ReflectionUtil

@Slf4j
class RetryInterceptor implements IMethodInterceptor {

    private static final String BEFORE_RETRY_METHOD_NAME = "beforeRetry"

    Integer retryMax
    Double delaySeconds

    RetryInterceptor(int retryMax, double delaySeconds) {
        this.retryMax = retryMax
        this.delaySeconds = delaySeconds
    }

    void intercept(IMethodInvocation invocation) throws Throwable {
        Integer attempts = 0
        while (attempts <= retryMax) {
            try {
                invocation.proceed()
                attempts = retryMax + 1
            } catch (org.junit.AssumptionViolatedException e) {
                throw e
            } catch (Throwable t) {
                log.info("Retry caught failure ${attempts + 1} / ${retryMax + 1}: ", t)
                attempts++
                if (attempts > retryMax) {
                    throw t
                }
                if (delaySeconds) {
                    Thread.sleep((delaySeconds*1000).toLong())
                }
                invocation.spec.specsBottomToTop.each { spec ->
                    spec.cleanupMethods.each {
                        try {
                            if (it.reflection) {
                                ReflectionUtil.invokeMethod(invocation.target, it.reflection)
                            }
                        } catch (Throwable t2) {
                            log.warn("Retry caught failure ${attempts + 1} / ${retryMax + 1} while cleaning up", t2)
                        }
                    }
                }
                invocation.spec.specsTopToBottom.each { spec ->
                    spec.setupMethods.each {
                        try {
                            if (it.reflection) {
                                ReflectionUtil.invokeMethod(invocation.target, it.reflection)
                            }
                        } catch (Throwable t2) {
                            // increment counter, since this is the start of the re-run
                            attempts++
                            if (attempts > retryMax) {
                                throw t
                            }
                            log.info("Retry caught failure ${attempts + 1} / ${retryMax + 1} while setting up", t2)
                        }
                    }
                }

                if(invocation.target.respondsTo(BEFORE_RETRY_METHOD_NAME)) {
                    try {
                        invocation.target."$BEFORE_RETRY_METHOD_NAME"()
                    } catch (Throwable t2) {
                        // increment counter, since this is the start of the re-run
                        log.info("Retry caught failure when invoking $BEFORE_RETRY_METHOD_NAME ", t2)
                    }
                }
            }
        }
    }
}
