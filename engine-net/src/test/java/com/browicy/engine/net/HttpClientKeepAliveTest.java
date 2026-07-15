package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class HttpClientKeepAliveTest {

    private ServerSocket serverSocket;
    private ExecutorService serverThreads;
    private final AtomicInteger connections = new AtomicInteger();
    private volatile int responsesPerConnection = Integer.MAX_VALUE;
    private volatile boolean closed;

    @Before
    public void startServer() throws IOException {
        serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        serverThreads = Executors.newVirtualThreadPerTaskExecutor();
        serverThreads.execute(this::acceptLoop);
    }

    @After
    public void stopServer() throws IOException {
        closed = true;
        serverSocket.close();
        serverThreads.shutdownNow();
    }

    @Test
    public void reusesConnectionForSequentialRequests() throws IOException {
        HttpClient client = new HttpClient();
        URI url = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/");

        HttpResponse first = client.get(url);
        HttpResponse second = client.get(url);
        HttpResponse third = client.get(url);

        assertEquals(200, first.statusCode());
        assertEquals("ok", new String(second.body(), StandardCharsets.US_ASCII));
        assertEquals(200, third.statusCode());
        assertEquals("Sequenzielle Requests sollten dieselbe Verbindung wiederverwenden",
                1, connections.get());
        client.close();
    }

    @Test
    public void retriesGetOnStaleKeepAliveConnection() throws IOException {
        responsesPerConnection = 1;
        HttpClient client = new HttpClient();
        URI url = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/");

        HttpResponse first = client.get(url);
        HttpResponse second = client.get(url);

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());
        assertEquals("ok", new String(second.body(), StandardCharsets.US_ASCII));
        assertEquals(2, connections.get());
        client.close();
    }

    private void acceptLoop() {
        while (!closed) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException serverClosed) {
                return;
            }
            connections.incrementAndGet();
            serverThreads.execute(() -> handleConnection(socket));
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            int served = 0;
            while (served < responsesPerConnection && readRequestHead(in)) {
                byte[] body = "ok".getBytes(StandardCharsets.US_ASCII);
                out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
                served++;
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean readRequestHead(InputStream in) throws IOException {
        int sequence = 0;
        int next;
        while ((next = in.read()) != -1) {
            if (next == '\r' && (sequence == 0 || sequence == 2)) {
                sequence++;
            } else if (next == '\n' && (sequence == 1 || sequence == 3)) {
                sequence++;
                if (sequence == 4) {
                    return true;
                }
            } else {
                sequence = next == '\r' ? 1 : 0;
            }
        }
        return false;
    }
}
