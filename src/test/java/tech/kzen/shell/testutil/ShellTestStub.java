package tech.kzen.shell.testutil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;


/**
 * Stands in for a project's main.jar in kzen-shell's process tests: it honours the same spawn contract
 * (--server.port, the stdin lifeline) while its lifetime and output are scripted, so start / readiness /
 * crash / dismiss paths can be exercised against real child JVMs.
 *
 * <p>Deliberately plain JDK Java with no dependencies, so the jar the fixture packages around it needs
 * no classpath of its own.
 *
 * <p>Configured by stub-config.properties in the working directory, overridden by key=value arguments:
 * <ul>
 *     <li>serve — answer HTTP 200 on the --server.port port, so readiness polling succeeds</li>
 *     <li>dieAfterMillis — exit this long after startup (default: live until the lifeline closes)</li>
 *     <li>exitCode — the status to exit with (default 0)</li>
 *     <li>line, line.1, line.2, … — printed to stdout at startup, in key order</li>
 * </ul>
 */
public final class ShellTestStub {
    private static final String configFileName = "stub-config.properties";
    private static final String portArgumentPrefix = "--server.port=";
    private static final String shutdownSentinel = "SHUTDOWN";
    private static final String stderrMarker = "stub stderr marker";
    private static final String httpResponse =
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok";


    public static void main(String[] args) throws Exception {
        Properties config = loadConfig(args);

        for (String line : outputLines(config)) {
            System.out.println(line);
        }
        System.err.println(stderrMarker);

        watchLifeline();

        if (Boolean.parseBoolean(config.getProperty("serve"))) {
            serve(port(args));
        }

        String dieAfterMillis = config.getProperty("dieAfterMillis");
        if (dieAfterMillis != null) {
            Thread.sleep(Long.parseLong(dieAfterMillis));
            System.exit(Integer.parseInt(config.getProperty("exitCode", "0")));
        }

        // Nothing scheduled: stay up until the lifeline closes (or the test kills the process).
        Thread.sleep(Long.MAX_VALUE);
    }


    private static Properties loadConfig(String[] args) throws IOException {
        Properties config = new Properties();

        Path configFile = Paths.get(configFileName);
        if (Files.exists(configFile)) {
            try (InputStream input = Files.newInputStream(configFile)) {
                config.load(input);
            }
        }

        for (String arg : args) {
            int separator = arg.indexOf('=');
            if (separator == -1 || arg.startsWith("--")) {
                continue;
            }
            config.setProperty(arg.substring(0, separator), arg.substring(separator + 1));
        }

        return config;
    }


    private static List<String> outputLines(Properties config) {
        List<String> keys = new ArrayList<>();
        for (String key : config.stringPropertyNames()) {
            if (key.equals("line") || key.startsWith("line.")) {
                keys.add(key);
            }
        }
        Collections.sort(keys);

        List<String> lines = new ArrayList<>();
        for (String key : keys) {
            lines.add(config.getProperty(key));
        }
        return lines;
    }


    private static int port(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(portArgumentPrefix)) {
                return Integer.parseInt(arg.substring(portArgumentPrefix.length()));
            }
        }
        throw new IllegalArgumentException("Missing " + portArgumentPrefix);
    }


    /** Honours the shell's stdin lifeline, so a kill() in a test completes without waiting out the force timeout. */
    private static void watchLifeline() {
        daemon(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            try {
                String line;
                do {
                    line = reader.readLine();
                }
                while (line != null && !line.equals(shutdownSentinel));
            }
            catch (IOException ignored) {
            }
            System.exit(0);
        });
    }


    private static void serve(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port, 1);
        daemon(() -> {
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    consumeRequest(socket);
                    OutputStream output = socket.getOutputStream();
                    output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                catch (IOException e) {
                    return;
                }
            }
        });
    }


    private static void consumeRequest(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Request headers are irrelevant; reading to the blank line avoids a reset on close.
        }
    }


    private static void daemon(Runnable body) {
        Thread thread = new Thread(body);
        thread.setDaemon(true);
        thread.start();
    }
}
