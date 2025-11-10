package com.clone.vti;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class BackendServer {

    // Cấu hình mặc định
    private static final int TCP_PORT = 9000;
    private static final int UDP_PORT = 9001;

    public static void main(String[] args) throws Exception {
        int tcpPort = TCP_PORT;
        int udpPort = UDP_PORT;
        if (args.length >= 1) tcpPort = Integer.parseInt(args[0]);
        if (args.length >= 2) udpPort = Integer.parseInt(args[1]);

        System.out.println("BackendServer starting...");
        System.out.println("TCP on port: " + tcpPort);
        System.out.println("UDP on port: " + udpPort);

        ExecutorService pool = Executors.newCachedThreadPool();

        // TCP server
        final int finalTcpPort = tcpPort;
        Thread tcpThread = new Thread(() -> runTcpServer(finalTcpPort, pool), "TCP-Server");
        tcpThread.setDaemon(true);
        tcpThread.start();

        // UDP server
        final int finalUdpPort = udpPort;
        Thread udpThread = new Thread(() -> runUdpServer(finalUdpPort), "UDP-Server");
        udpThread.setDaemon(true);
        udpThread.start();

        System.out.println("Servers are running. Press Ctrl+C to stop.");
        // Giữ tiến trình sống
        Thread.currentThread().join();
    }

    private static void runTcpServer(int port, ExecutorService pool) {
        try (ServerSocket server = new ServerSocket(port)) {
            server.setReuseAddress(true);
            while (true) {
                Socket s = server.accept();
                pool.submit(() -> handleTcpClient(s));
            }
        } catch (IOException e) {
            System.err.println("TCP server error: " + e.getMessage());
        }
    }

    private static void handleTcpClient(Socket s) {
        try (Socket socket = s) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Đọc payload client gửi (đọc đơn giản tới khi client đóng ghi)
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
                // Nếu payload nhỏ, có thể break theo \n để nhanh hơn
                if (buf.size() > 0 && new String(buf.toByteArray(), StandardCharsets.UTF_8).endsWith("\n")) break;
            }

            String req = buf.toString(StandardCharsets.UTF_8);
            System.out.println("Received request: " + req);
            // Lấy địa chỉ IP thực tế của máy server
            String localIp = getServerIPAddress();

            // Port là port mà server đang listen (truyền vào hàm)
            int localPort = 9000;

            // Phản hồi kèm IP/Port thật của server
            String resp = String.format(
                    "OK - đã gửi thành công: %s\nServer IP: %s\nServer Port: %d\n",
                    req, localIp, localPort
            );
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static void runUdpServer(int port) {
        byte[] buffer = new byte[2048];
        try (DatagramSocket sock = new DatagramSocket(port)) {
            sock.setReuseAddress(true);
            while (true) {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                sock.receive(p);
                String req = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8).trim();

                String localIp = p.getAddress() != null ? p.getAddress().getHostAddress() : "unknown";
                int localPort = 9001;

                // Nếu địa chỉ là loopback hoặc wildcard, cố tìm một địa chỉ khả dụng trên các network interfaces
                if (localIp == null || localIp.isEmpty() ||
                        localIp.equals("0.0.0.0") ||
                        localIp.equals("127.0.0.1") ||
                        localIp.equals("::") ||
                        localIp.equals("::1")) {
                    localIp = getServerIPAddress();
                    localPort = 9001;
                }
                String resp = String.format("OK - đã gửi thành công: %s\nServer IP: %s\nServer Port: %d\n",
                        req.trim(), localIp, 9001);
                DatagramPacket reply = new DatagramPacket(resp.getBytes(StandardCharsets.UTF_8), resp.getBytes(StandardCharsets.UTF_8).length, p.getAddress(), p.getPort());
                sock.send(reply);
            }
        } catch (IOException e) {
            System.err.println("UDP server error: " + e.getMessage());
        }
    }
    private static String getServerIPAddress() throws UnknownHostException {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "unknown";
        }
    }
}