/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.concurrent.runtime;

import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.transaction.api.JavaEETransactionManager;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.concurrent.LogFacade;
import org.glassfish.concurrent.runtime.deployer.ContextServiceConfig;
import org.glassfish.concurrent.runtime.deployer.ManagedExecutorServiceConfig;
import org.glassfish.concurrent.runtime.deployer.ManagedScheduledExecutorServiceConfig;
import org.glassfish.concurrent.runtime.deployer.ManagedThreadFactoryConfig;
import org.glassfish.enterprise.concurrent.*;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.internal.data.ApplicationRegistry;
import org.glassfish.internal.deployment.Deployment;
import org.glassfish.resourcebase.resources.api.ResourceInfo;
import org.jvnet.hk2.annotations.Service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides API to create various Concurrency Utilities objects
 */
@Service
@Singleton
public class ConcurrentRuntime implements PostConstruct, PreDestroy {

    private static ConcurrentRuntime _runtime;

    private Map<String, ManagedExecutorServiceImpl> managedExecutorServiceMap;
    private Map<String, ManagedScheduledExecutorServiceImpl> managedScheduledExecutorServiceMap;
    private Map<String, ContextServiceImpl> contextServiceMap;
    private Map<String, ManagedThreadFactoryImpl> managedThreadFactoryMap;

    public static final String CONTEXT_INFO_CLASSLOADER = "Classloader";
    public static final String CONTEXT_INFO_JNDI = "JNDI";
    public static final String CONTEXT_INFO_SECURITY = "Security";
    public static final String CONTEXT_INFO_WORKAREA = "WorkArea";

    private ScheduledExecutorService internalScheduler;
    private static final Logger logger  = LogFacade.getLogger();

    @Inject
    InvocationManager invocationManager;

    @Inject
    Deployment deployment;

    @Inject
    Applications applications;

    @Inject
    JavaEETransactionManager transactionManager;

    @Inject
    ApplicationRegistry applicationRegistry;

    /**
     * Returns the ConcurrentRuntime instance.
     * It follows singleton pattern and only one instance exists at any point
     * of time. External entities need to call this method to get
     * ConcurrentRuntime instance
     *
     * @return ConcurrentRuntime instance
     */
    public static ConcurrentRuntime getRuntime() {
        if (_runtime == null) {
            throw new RuntimeException("ConcurrentRuntime not initialized");
        }
        return _runtime;
    }

    private static void setRuntime(ConcurrentRuntime runtime) {
        _runtime = runtime;
    }

    /**
     * Constructor should be private to follow singleton pattern, but package access for unit testing.
     */
    ConcurrentRuntime() {
        setRuntime(this);
    }

    InvocationManager getInvocationManager() {
        return invocationManager;
    }

    Deployment getDeployment() {
        return deployment;
    }

    Applications getApplications() {
        return applications;
    }

    JavaEETransactionManager getTransactionManager() {
        return transactionManager;
    }

    ApplicationRegistry getApplicationRegistry() {
        return applicationRegistry;
    }

    public synchronized ContextServiceImpl getContextService(ResourceInfo resource, ContextServiceConfig config) {
        String jndiName = config.getJndiName();
        if (contextServiceMap != null && contextServiceMap.containsKey(jndiName)) {
            return contextServiceMap.get(jndiName);
        }
        ContextServiceImpl contextService = createContextService(config.getJndiName(),
                config.getContextInfo(), config.getContextInfoEnabled(), false);
        if (contextServiceMap == null) {
            contextServiceMap = new HashMap();
        }
        contextServiceMap.put(jndiName, contextService);
        return contextService;
    }

    public void shutdownContextService(String jndiName) {
        synchronized(this) {
            if (contextServiceMap != null) {
                contextServiceMap.remove(jndiName);
            }
        }
    }

    public synchronized ManagedExecutorServiceImpl getManagedExecutorService(ResourceInfo resource, ManagedExecutorServiceConfig config) {
        String jndiName = config.getJndiName();
        if (managedExecutorServiceMap != null && managedExecutorServiceMap.containsKey(jndiName)) {
            return managedExecutorServiceMap.get(jndiName);
        }
        ManagedThreadFactoryImpl managedThreadFactory = new ManagedThreadFactoryImpl(
                config.getJndiName() + "-managedThreadFactory",
                null,
                config.getThreadPriority());
        ManagedExecutorServiceImpl mes = new ManagedExecutorServiceImpl(config.getJndiName(),
                managedThreadFactory,
                config.getHungAfterSeconds() * 1000L, // in millseconds
                config.isLongRunningTasks(),
                config.getCorePoolSize(),
                config.getMaximumPoolSize(),
                config.getKeepAliveSeconds(), TimeUnit.SECONDS,
                config.getThreadLifeTimeSeconds(),
                config.getTaskQueueCapacity(),
                createContextService(config.getJndiName() + "-contextservice",
                        config.getContextInfo(), config.getContextInfoEnabled(), true),
                AbstractManagedExecutorService.RejectPolicy.ABORT);
        if (managedExecutorServiceMap == null) {
            managedExecutorServiceMap = new HashMap();
        }
        managedExecutorServiceMap.put(jndiName, mes);
        if (config.getHungAfterSeconds() > 0L && !config.isLongRunningTasks()) {
            scheduleInternalTimer(config.getHungLoggerInitialDelaySeconds(), config.getHungLoggerIntervalSeconds(), config.isHungLoggerPrintOnce());
        }
        return mes;
    }

    public void shutdownManagedExecutorService(String jndiName) {
        ManagedExecutorServiceImpl mes = null;
        synchronized(this) {
            if (managedExecutorServiceMap != null) {
                mes = managedExecutorServiceMap.remove(jndiName);
            }
        }
        if (mes != null) {
            mes.shutdownNow();
        }
    }

    public synchronized ManagedScheduledExecutorServiceImpl getManagedScheduledExecutorService(ResourceInfo resource,
                                                                                               ManagedScheduledExecutorServiceConfig config) {
        String jndiName = config.getJndiName();
        if (managedScheduledExecutorServiceMap != null && managedScheduledExecutorServiceMap.containsKey(jndiName)) {
            return managedScheduledExecutorServiceMap.get(jndiName);
        }
        ManagedThreadFactoryImpl managedThreadFactory = new ManagedThreadFactoryImpl(
                config.getJndiName() + "-managedThreadFactory",
                null,
                config.getThreadPriority());
        ManagedScheduledExecutorServiceImpl mes = new ManagedScheduledExecutorServiceImpl(config.getJndiName(),
                managedThreadFactory,
                config.getHungAfterSeconds() * 1000L, // in millseconds
                config.isLongRunningTasks(),
                config.getCorePoolSize(),
                config.getKeepAliveSeconds(), TimeUnit.SECONDS,
                config.getThreadLifeTimeSeconds(),
                createContextService(config.getJndiName() + "-contextservice",
                        config.getContextInfo(), config.getContextInfoEnabled(), true),
                AbstractManagedExecutorService.RejectPolicy.ABORT);
        if (managedScheduledExecutorServiceMap == null) {
            managedScheduledExecutorServiceMap = new HashMap();
        }
        managedScheduledExecutorServiceMap.put(jndiName, mes);
        if (config.getHungAfterSeconds() > 0L && !config.isLongRunningTasks()) {
            scheduleInternalTimer(config.getHungLoggerInitialDelaySeconds(), config.getHungLoggerIntervalSeconds(), config.isHungLoggerPrintOnce());
        }
        return mes;
    }

    public void shutdownScheduledManagedExecutorService(String jndiName) {
        ManagedScheduledExecutorServiceImpl mses = null;
        synchronized(this) {
            if (managedScheduledExecutorServiceMap != null) {
                mses = managedScheduledExecutorServiceMap.remove(jndiName);
            }
        }
        if (mses != null) {
            mses.shutdownNow();
        }
    }

    public synchronized ManagedThreadFactoryImpl getManagedThreadFactory(ResourceInfo resource, ManagedThreadFactoryConfig config) {
        String jndiName = config.getJndiName();
        if (managedThreadFactoryMap != null && managedThreadFactoryMap.containsKey(jndiName)) {
            return managedThreadFactoryMap.get(jndiName);
        }
        ManagedThreadFactoryImpl managedThreadFactory = new ManagedThreadFactoryImpl(config.getJndiName(),
                createContextService(config.getJndiName() + "-contextservice",
                        config.getContextInfo(), config.getContextInfoEnabled(), true),
                config.getThreadPriority());
        if (managedThreadFactoryMap == null) {
            managedThreadFactoryMap = new HashMap();
        }
        managedThreadFactoryMap.put(jndiName, managedThreadFactory);
        return managedThreadFactory;
    }

    public void shutdownManagedThreadFactory(String jndiName) {
        ManagedThreadFactoryImpl mtf = null;
        synchronized(this) {
            if (managedThreadFactoryMap != null) {
                mtf = managedThreadFactoryMap.remove(jndiName);
            }
        }
        if (mtf != null) {
            mtf.stop();
        }
    }

    private ContextServiceImpl createContextService(String jndiName, String contextInfo,
                                                    String contextInfoEnabled, boolean cleanupTransaction) {
        boolean isContextInfoEnabled = Boolean.valueOf(contextInfoEnabled);
        ContextSetupProviderImpl.CONTEXT_TYPE[] contextTypes = parseContextInfo(contextInfo, isContextInfoEnabled);
        ContextSetupProviderImpl contextSetupProvider =
                new ContextSetupProviderImpl(invocationManager, deployment, applications,
                                             cleanupTransaction? transactionManager: null, contextTypes);
        ContextServiceImpl obj = new ContextServiceImpl(jndiName, contextSetupProvider,
                new TransactionSetupProviderImpl(transactionManager));
        return obj;
    }

    private ContextSetupProviderImpl.CONTEXT_TYPE[] parseContextInfo(String contextInfo, boolean isContextInfoEnabled) {
        ArrayList<ContextSetupProviderImpl.CONTEXT_TYPE> contextTypeArray = new ArrayList<>();
        ContextSetupProviderImpl.CONTEXT_TYPE[] contextTypes = new ContextSetupProviderImpl.CONTEXT_TYPE[] {};
        if (contextInfo == null) {
            // by default, if no context info is passed, we propagate all context types
            contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.CLASSLOADING);
            contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.NAMING);
            contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.SECURITY);
            contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.WORKAREA);
            return contextTypeArray.toArray(contextTypes);
        }
        if (isContextInfoEnabled) {
        StringTokenizer st = new StringTokenizer(contextInfo, ",", false);
            while(st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if (CONTEXT_INFO_CLASSLOADER.equalsIgnoreCase(token)) {
                    contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.CLASSLOADING);
                } else if (CONTEXT_INFO_JNDI.equalsIgnoreCase(token)) {
                    contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.NAMING);
                } else if (CONTEXT_INFO_SECURITY.equalsIgnoreCase(token)) {
                    contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.SECURITY);
                } else if (CONTEXT_INFO_WORKAREA.equalsIgnoreCase(token)) {
                    contextTypeArray.add(ContextSetupProviderImpl.CONTEXT_TYPE.WORKAREA);
                }
            }
        }
        return contextTypeArray.toArray(contextTypes);
    }

    private void scheduleInternalTimer(long initialDelay, long interval, boolean logOnce) {
        if (internalScheduler == null) {
            String name = "glassfish-internal";
            ManagedThreadFactoryImpl managedThreadFactory = new ManagedThreadFactoryImpl(
                    name + "-managedThreadFactory",
                    null,
                    Thread.NORM_PRIORITY);
            internalScheduler = new ManagedScheduledExecutorServiceImpl(name,
                    managedThreadFactory,
                    0L,
                    false,
                    1,
                    60, TimeUnit.SECONDS,
                    0L,
                    createContextService(name + "-contextservice",
                            CONTEXT_INFO_CLASSLOADER, "true", false),
                    AbstractManagedExecutorService.RejectPolicy.ABORT);
            internalScheduler.scheduleAtFixedRate(new HungTasksLogger(logOnce), initialDelay, interval, TimeUnit.SECONDS);
        }
    }

    @Override
    public void postConstruct() {
    }

    @Override
    public void preDestroy() {
        // TODO shut down objects here
    }

    class HungTasksLogger implements Runnable {

        private Boolean logOnce;
        private Map<String, Collection<AbstractManagedThread>> cachedHungThreadsMap = new HashMap<>();

        HungTasksLogger(Boolean logOnce) {
            this.logOnce = logOnce;
        }

        public void run() {
            ArrayList<ManagedExecutorServiceImpl> executorServices = new ArrayList();
            ArrayList<ManagedScheduledExecutorServiceImpl> scheduledExecutorServices = new ArrayList();
            synchronized (ConcurrentRuntime.this) {
                if (managedExecutorServiceMap != null) {
                    Collection<ManagedExecutorServiceImpl> mesColl = managedExecutorServiceMap.values();
                    executorServices.addAll(mesColl);
                }
            }
            synchronized (ConcurrentRuntime.this) {
                if (managedScheduledExecutorServiceMap != null) {
                    Collection<ManagedScheduledExecutorServiceImpl> msesColl = managedScheduledExecutorServiceMap.values();
                    scheduledExecutorServices.addAll(msesColl);
                }
            }
            for (ManagedExecutorServiceImpl mes: executorServices) {
                Collection<AbstractManagedThread> hungThreads = mes.getHungThreads();
                logHungThreads(hungThreads, mes.getManagedThreadFactory(), mes.getName());
            }
            for (ManagedScheduledExecutorServiceImpl mses: scheduledExecutorServices) {
                Collection<AbstractManagedThread> hungThreads = mses.getHungThreads();
                logHungThreads(hungThreads, mses.getManagedThreadFactory(), mses.getName());
            }
        }

        private void logHungThreads(Collection<AbstractManagedThread> hungThreads, ManagedThreadFactoryImpl mtf, String mesName) {
             if (!logOnce){
                 logRawHungThreads(hungThreads, mtf, mesName);
                 return;
             }
             if (hungThreads == null) {
                 cachedHungThreadsMap.remove(mesName);
                 return;
             }
             Collection<AbstractManagedThread> targetHungThreads = new HashSet<>();
             targetHungThreads.addAll(hungThreads);
             Collection<AbstractManagedThread> cachedHungThreads = cachedHungThreadsMap.get(mesName);
             if (cachedHungThreads != null) {
                 targetHungThreads.removeAll(cachedHungThreads);
             }
             logRawHungThreads(targetHungThreads, mtf, mesName);
             cachedHungThreadsMap.put(mesName, hungThreads);
        }

        private void logRawHungThreads(Collection<AbstractManagedThread> hungThreads, ManagedThreadFactoryImpl mtf, String mesName) {
            if (hungThreads != null) {
                for (AbstractManagedThread hungThread: hungThreads) {
                    Object[] params = {hungThread.getTaskIdentityName(),
                                       hungThread.getName(),
                                       hungThread.getTaskRunTime(System.currentTimeMillis()) / 1000,
                                       mtf.getHungTaskThreshold() / 1000,
                                       mesName};
                    logger.log(Level.WARNING, LogFacade.UNRESPONSIVE_TASK, params);
                }
            }
        }
    }

}
