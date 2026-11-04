package main;

import javafx.application.Platform;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the running server's own numbers every 2 seconds, on a background thread: its Java memory
 * (heap used / max), its CPU use, garbage collections and threads. It connects the way JDK tools do
 * (the attach API starts a local-only management agent inside the server's Java), which works for
 * any server started by this launcher. If the server's Java doesn't allow that, CPU is still measured
 * from the operating system and memory is reported as unavailable.
 */
final class ServerStats {

    /** One reading. Memory figures are NaN when unavailable. */
    record Sample(long time, double heapUsedMB, double heapMaxMB, double heapTakenMB, double cpu, int threads,
                  long gcCount, long gcMillis) {}   // heapTaken: memory Java currently holds from the PC

    private static final long EVERY_MS = 2000;

    private final Consumer<Sample> sink;   // called on the FX thread
    private volatile Thread thread;

    ServerStats(Consumer<Sample> sink) { this.sink = sink; }

    void start(Process server) {
        stop();
        Thread t = new Thread(() -> run(server), "server-stats");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        thread = t;
        t.start();
    }

    void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    private void run(Process server) {
        JMXConnector jmx = null;
        MemoryMXBean mem = null;
        com.sun.management.OperatingSystemMXBean os = null;
        ThreadMXBean threads = null;
        List<GarbageCollectorMXBean> gcs = List.of();
        long nextAttach = System.currentTimeMillis() + 3000;   // let the server's Java finish starting
        int attempts = 0;
        // CPU from the OS, for when the management connection isn't (yet) there
        ProcessHandle jvm = javaProcess(server.toHandle());
        long lastCpuNs = cpuNanos(jvm), lastWall = System.nanoTime();
        int cores = Runtime.getRuntime().availableProcessors();

        try {
            while (thread == Thread.currentThread() && server.isAlive()) {
                if (mem == null && attempts < 8 && System.currentTimeMillis() >= nextAttach) {
                    attempts++;
                    ProcessHandle real = javaProcess(server.toHandle());
                    if (real.pid() != jvm.pid()) {   // the real Java appeared under a launcher stub
                        jvm = real;
                        lastCpuNs = cpuNanos(jvm);
                        lastWall = System.nanoTime();
                    }
                    try {
                        jmx = connect(jvm.pid());
                        MBeanServerConnection mbs = jmx.getMBeanServerConnection();
                        mem = ManagementFactory.newPlatformMXBeanProxy(mbs, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
                        os = ManagementFactory.newPlatformMXBeanProxy(mbs, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
                            com.sun.management.OperatingSystemMXBean.class);
                        threads = ManagementFactory.newPlatformMXBeanProxy(mbs, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
                        gcs = ManagementFactory.getPlatformMXBeans(mbs, GarbageCollectorMXBean.class);
                    } catch (Exception | LinkageError e) {
                        closeQuietly(jmx);
                        jmx = null; mem = null;
                        nextAttach = System.currentTimeMillis() + 5000;   // still starting up: try again
                    }
                }

                long now = System.currentTimeMillis();
                Sample s;
                try {
                    if (mem != null) {
                        MemoryUsage heap = mem.getHeapMemoryUsage();
                        long gcCount = 0, gcMs = 0;
                        for (GarbageCollectorMXBean gc : gcs) { gcCount += Math.max(0, gc.getCollectionCount()); gcMs += Math.max(0, gc.getCollectionTime()); }
                        double cpu = Math.max(0, os.getProcessCpuLoad());
                        s = new Sample(now, heap.getUsed() / 1_048_576.0, heap.getMax() / 1_048_576.0,
                            heap.getCommitted() / 1_048_576.0, cpu,
                            threads.getThreadCount(), gcCount, gcMs);
                    } else {
                        long cpuNs = cpuNanos(jvm), wall = System.nanoTime();
                        double cpu = lastCpuNs < 0 || cpuNs < 0 ? Double.NaN
                            : (cpuNs - lastCpuNs) / (double) (wall - lastWall) / cores;
                        lastCpuNs = cpuNs; lastWall = wall;
                        s = new Sample(now, Double.NaN, Double.NaN, Double.NaN, Math.max(0, Math.min(1, cpu)), 0, -1, -1);
                    }
                } catch (Exception e) {
                    closeQuietly(jmx);   // the server is shutting down, or the connection dropped
                    jmx = null; mem = null;
                    nextAttach = System.currentTimeMillis() + 5000;
                    continue;
                }
                Sample out = s;
                Platform.runLater(() -> sink.accept(out));
                Thread.sleep(EVERY_MS);
            }
        } catch (InterruptedException ignored) {
            // stopped
        } finally {
            closeQuietly(jmx);
        }
    }

    /** Attaches to the server's Java and opens its local-only management connection. */
    private static JMXConnector connect(long pid) throws Exception {
        com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(String.valueOf(pid));
        String address;
        try {
            address = vm.startLocalManagementAgent();
        } finally {
            vm.detach();
        }
        return JMXConnectorFactory.connect(new JMXServiceURL(address));
    }

    /**
     * The process that really runs the server's Java. Oracle's installer puts a stub java.exe on the
     * PATH (…\Common Files\Oracle\Java\javapath) that starts the real one as its child — so look
     * among the descendants for a java.exe, and use the deepest.
     */
    static ProcessHandle javaProcess(ProcessHandle started) {
        return started.descendants()
            .filter(d -> d.info().command().map(c -> {
                String n = c.toLowerCase().replace('\\', '/');
                return n.endsWith("/java.exe") || n.endsWith("/javaw.exe") || n.endsWith("/java");
            }).orElse(false))
            .reduce((a, b) -> b)
            .orElse(started);
    }

    private static long cpuNanos(ProcessHandle p) {
        return p.info().totalCpuDuration().map(Duration::toNanos).orElse(-1L);
    }

    private static void closeQuietly(JMXConnector c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }
}
