package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

final class AiVpnSession {
    private static final Object SESSION_LOCK = new Object();
    private static AiVpnSession activeSession;
    private static boolean stopInProgress;

    private final OpenRouterConfig config;
    private final Object stateLock = new Object();
    private boolean starting;
    private boolean ready;
    private boolean closed;
    private boolean stopScheduled;
    private Throwable failure;

    private AiVpnSession(OpenRouterConfig config) {
        this.config = config;
    }

    static AiVpnSession start(OpenRouterConfig config) {
        AiVpnSession session = new AiVpnSession(config);
        synchronized (SESSION_LOCK) {
            activeSession = session;
        }
        session.startAsync();
        return session;
    }

    void awaitReady() throws Exception {
        if (!config.vpnEnabled) {
            return;
        }
        synchronized (stateLock) {
            while (starting && !closed) {
                try {
                    stateLock.wait(500L);
                } catch (InterruptedException e) {
                    throw new RuntimeException("VPN wait cancelled.");
                }
            }
            if (closed) {
                throw new RuntimeException("VPN session closed.");
            }
            if (ready) {
                return;
            }
            starting = true;
            failure = null;
        }

        Throwable retryFailure = startProxy();
        boolean stopAfterRetry;
        synchronized (stateLock) {
            starting = false;
            failure = retryFailure;
            ready = retryFailure == null && !closed;
            stopAfterRetry = closed;
            stateLock.notifyAll();
        }
        if (stopAfterRetry) {
            scheduleStop();
        }
        synchronized (stateLock) {
            if (closed) {
                throw new RuntimeException("VPN session closed.");
            }
            if (!ready) {
                throw new RuntimeException("VPN unavailable: "
                        + (failure == null ? "unknown error" : failure.toString()));
            }
        }
    }

    void close() {
        boolean stopNow;
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;
            ready = false;
            stopNow = !starting && isActiveSession();
            stateLock.notifyAll();
        }
        if (stopNow) {
            scheduleStop();
        }
    }

    private void startAsync() {
        if (!config.vpnEnabled) {
            synchronized (stateLock) {
                ready = true;
            }
            return;
        }
        synchronized (stateLock) {
            starting = true;
        }
        Thread thread = new Thread(new Runnable() {
            public void run() {
                Throwable startupFailure = startProxy();
                boolean stopAfterStartup;
                synchronized (stateLock) {
                    starting = false;
                    failure = startupFailure;
                    ready = startupFailure == null && !closed;
                    stopAfterStartup = closed;
                    stateLock.notifyAll();
                }
                if (stopAfterStartup) {
                    scheduleStop();
                }
            }
        }, "AIAssistantVpnWarmup");
        try {
            thread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        thread.start();
    }

    private Throwable startProxy() {
        try {
            awaitPreviousStop();
            OpenRouterClient.ensureVpnProxy(config);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private void scheduleStop() {
        synchronized (stateLock) {
            if (stopScheduled) {
                return;
            }
            stopScheduled = true;
        }
        if (!config.vpnEnabled) {
            synchronized (SESSION_LOCK) {
                if (activeSession == this) {
                    activeSession = null;
                }
            }
            return;
        }
        Thread thread = new Thread(new Runnable() {
            public void run() {
                synchronized (SESSION_LOCK) {
                    if (activeSession != AiVpnSession.this) {
                        return;
                    }
                    activeSession = null;
                    stopInProgress = true;
                }
                try {
                    OpenRouterClient.stopVpnProxy(config);
                } finally {
                    synchronized (SESSION_LOCK) {
                        stopInProgress = false;
                        SESSION_LOCK.notifyAll();
                    }
                }
            }
        }, "AIAssistantVpnStop");
        try {
            thread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        thread.start();
    }

    private boolean isActiveSession() {
        synchronized (SESSION_LOCK) {
            return activeSession == this;
        }
    }

    private static void awaitPreviousStop() throws InterruptedException {
        synchronized (SESSION_LOCK) {
            while (stopInProgress) {
                SESSION_LOCK.wait(250L);
            }
        }
    }
}
