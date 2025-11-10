import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class ClientLogic {

    public static class PodStats {
        public final String podIp;
        public final int count;
        public final double avgMs;
        public final double minMs;
        public final double maxMs;

        public PodStats(String podIp, int count, double avgMs, double minMs, double maxMs) {
            this.podIp = podIp;
            this.count = count;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
        }
    }

    public interface Listener {
        default void onLog(String line) {}
        default void onDoneTcp(int ok, int fail, List<PodStats> summary) {}
        default void onDoneUdp(int ok, int fail, List<PodStats> summary) {}
    }

    private final ExecutorService exec;

    public ClientLogic() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.exec = Executors.newFixedThreadPool(Math.min(8, cores * 2));
    }

    public void shutdown() {
        exec.shutdownNow();
    }

    // -------------------- TCP --------------------
    public void sendTcp(String host, int port, int count, Listener cb) {
        exec.submit(() -> runTcp(host, port, count, cb));
    }

    private void runTcp(String host, int port, int count, Listener cb) {
        int ok = 0, fail = 0;
        Map<String, List<Long>> perPodTimes = new HashMap<>();

        cb.onLog(String.format("TCP -> %s:%d | count=%d", host, port, count));

        for (int i = 1; i <= count; i++) {
            long startTimeNs;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 2000);
                s.setSoTimeout(3000);

                OutputStream out = s.getOutputStream();
                InputStream in = s.getInputStream();

                String payload = "REQ-" + i + "\n";
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();

                startTimeNs = System.nanoTime();
                byte[] tmp = new byte[512];
                int n = in.read(tmp);
                long elapsedNs = System.nanoTime() - startTimeNs;

                if (n > 0) {
                    String resp = new String(tmp, 0, n, StandardCharsets.UTF_8).trim();
                    ok++;

                    String podIp = extractServerIp(resp);
                    if ("unknown".equals(podIp) || podIp.isEmpty()) {
                        podIp = s.getInetAddress().getHostAddress(); // fallback
                    }
                    perPodTimes.computeIfAbsent(podIp, k -> new ArrayList<>()).add(elapsedNs);

                    if (i % 100 == 0 || i == count) {
                        cb.onLog("TCP OK: " + resp);
                        logStatsSnapshot(perPodTimes, cb, false);
                    }
                } else {
                    fail++;
                    cb.onLog("TCP timeout/no response cho REQ-" + i);
                }
            } catch (IOException ex) {
                fail++;
                cb.onLog("TCP lỗi cho REQ-" + i + ": " + ex.getMessage());
            }
        }

        List<PodStats> summary = buildSummary(perPodTimes);
        cb.onLog(String.format("TCP DONE: ok=%d, fail=%d", ok, fail));
        cb.onDoneTcp(ok, fail, summary);
    }

    // -------------------- UDP --------------------
    public void sendUdp(String host, int port, int count, Listener cb) {
        exec.submit(() -> runUdp(host, port, count, cb));
    }

    private void runUdp(String host, int port, int count, Listener cb) {
        int ok = 0, fail = 0;
        Map<String, List<Long>> perPodTimes = new HashMap<>();

        cb.onLog(String.format("UDP -> %s:%d | count=%d", host, port, count));

        try {
            InetAddress addr = InetAddress.getByName(host);
            try (DatagramSocket sock = new DatagramSocket()) {
                sock.setSoTimeout(1500);

                for (int i = 1; i <= count; i++) {
                    String payload = "REQ-" + i;
                    byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket p = new DatagramPacket(data, data.length, addr, port);

                    long startTimeNs = System.nanoTime();
                    sock.send(p);

                    byte[] buf = new byte[512];
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    try {
                        sock.receive(r);
                        long elapsedNs = System.nanoTime() - startTimeNs;

                        String resp = new String(r.getData(), r.getOffset(), r.getLength(), StandardCharsets.UTF_8).trim();
                        ok++;

                        String podIp = extractServerIp(resp);
                        if ("unknown".equals(podIp) || podIp.isEmpty()) {
                            podIp = r.getAddress().getHostAddress(); // fallback
                        }
                        perPodTimes.computeIfAbsent(podIp, k -> new ArrayList<>()).add(elapsedNs);

                        if (i % 100 == 0 || i == count) {
                            cb.onLog("UDP OK: " + resp);
                            logStatsSnapshot(perPodTimes, cb, true);
                        }
                    } catch (SocketTimeoutException te) {
                        fail++;
                        cb.onLog("UDP timeout cho REQ-" + i);
                    }
                }
            }
        } catch (IOException e) {
            cb.onLog("UDP lỗi: " + e.getMessage());
        }

        List<PodStats> summary = buildSummary(perPodTimes);
        cb.onLog(String.format("UDP DONE: ok=%d, fail=%d", ok, fail));
        cb.onDoneUdp(ok, fail, summary);
    }

    // -------------------- Helpers --------------------
    private static String extractServerIp(String resp) {
        if (resp == null) return "unknown";
        String[] lines = resp.split("\\r?\\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.toLowerCase(Locale.ROOT).startsWith("server ip:")) {
                return t.substring("server ip:".length()).trim();
            }
        }
        return "unknown";
    }

    private static List<PodStats> buildSummary(Map<String, List<Long>> timesMap) {
        List<PodStats> out = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : timesMap.entrySet()) {
            String ip = e.getKey();
            List<Long> times = e.getValue();
            if (times == null || times.isEmpty()) continue;

            long sum = 0L, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (long t : times) {
                sum += t;
                if (t < min) min = t;
                if (t > max) max = t;
            }
            double avgMs = sum / (double) times.size() / 1_000_000.0;
            double minMs = min / 1_000_000.0;
            double maxMs = max / 1_000_000.0;
            out.add(new PodStats(ip, times.size(), avgMs, minMs, maxMs));
        }
        // sort giảm dần theo Requests
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        return out;
    }

    private static void logStatsSnapshot(Map<String, List<Long>> map, Listener cb, boolean udp) {
        for (PodStats ps : buildSummary(map)) {
            String line = String.format(Locale.US,
                    "%s Stats [%s] -> min=%.3fms, max=%.3fms, avg=%.3fms (count=%d)",
                    udp ? "UDP" : "Stats", ps.podIp, ps.minMs, ps.maxMs, ps.avgMs, ps.count);
            cb.onLog(line);
        }
    }
}
