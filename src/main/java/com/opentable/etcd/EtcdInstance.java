/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.etcd;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.opentable.io.DeleteRecursively;

public class EtcdInstance implements Closeable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdInstance.class);
    private static final String ETCD_PACKAGE_PATH_FMT = "/etcd/etcd-%s";
    private static final String ETCD_LOCATION;

    static {
        final String etcdPath = String.format(ETCD_PACKAGE_PATH_FMT, (System.getProperty("os.name") + "-" + System.getProperty("os.arch"))
                .replaceAll(" ", "").toLowerCase(Locale.ROOT));

        final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

        Path etcdFile = null;
        try {
            etcdFile = Files.createTempFile("etcd", "bin");
            try (InputStream etcd = EtcdInstance.class.getResourceAsStream(etcdPath)) {
                if (etcd == null) {
                    throw new IllegalStateException("Could not find " + etcdPath + " on classpath");
                }
                Files.copy(etcd, etcdFile, StandardCopyOption.REPLACE_EXISTING);
                if (!isWindows) {
                    Files.setPosixFilePermissions(etcdFile, PosixFilePermissions.fromString("r-x------"));
                }
            }
            etcdFile.toFile().deleteOnExit();
            ETCD_LOCATION = etcdFile.toString();
        } catch (IOException e) {
            if (etcdFile != null) {
                try {
                    Files.delete(etcdFile);
                } catch (IOException e1) {
                    throw new ExceptionInInitializerError(e1);
                }
            }
            throw new ExceptionInInitializerError(e);
        }
    }

    public EtcdInstance(EtcdConfiguration configuration)
    {
        this.configuration = configuration;
        this.id = ObjectUtils.firstNonNull(configuration.getNodeName(), RandomStringUtils.randomAlphabetic(10));
    }

    private final EtcdConfiguration configuration;
    private final String id;

    @GuardedBy("this")
    private Process etcdServer;

    @GuardedBy("this")
    private int clientPort;

    @GuardedBy("this")
    private int peerPort;


    public void startUnchecked()
    {
        try {
            start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressFBWarnings("SWL_SLEEP_WITH_LOCK_HELD")
    public synchronized void start() throws IOException
    {
        if (etcdServer != null) {
            throw new IllegalStateException("already started");
        }

        if (clientPort == 0) {
            clientPort = findPort(configuration.getClientPort());
        }
        if (peerPort == 0) {
            peerPort = findPort(configuration.getPeerPort());
        }

        final String hostname = configuration.getHostname() != null ? configuration.getHostname() : findHostname();

        final String clientAddr = hostname + ':' + clientPort;
        final String peerAddr = hostname + ':' + peerPort;

        final List<String> arguments = new ArrayList<>();
        String peerUrl = "http://" + peerAddr;
        arguments.addAll(Arrays.asList(
                ETCD_LOCATION,
                "--data-dir", configuration.getDataDirectory().toString(),
                "--name", id,
                "--max-wals", "1",
                "--max-snapshots", "1",
                "--listen-client-urls", "http://0.0.0.0:" + clientPort,
                "--advertise-client-urls", "http://" + clientAddr,
                "--initial-advertise-peer-urls", peerUrl,
                "--listen-peer-urls", "http://0.0.0.0:" + peerPort));

        if (configuration.isVerbose()) {
            arguments.add("-v");
        }

        if (configuration.getInitialCluster() != null) {
            arguments.add("--initial-cluster");
            arguments.add(configuration.getInitialCluster());
        } else if (configuration.getDiscoveryUri() != null) {
            arguments.add("--discovery");
            arguments.add(configuration.getDiscoveryUri());
        } else {
            arguments.add("--initial-cluster");
            arguments.add(id + "=" + peerUrl);
        }

        if (configuration.getSnapshotCount() != null) {
            arguments.add("--snapshot-count=" + configuration.getSnapshotCount());
        }

        LOGGER.info("Launching etcd: {}", arguments);

        etcdServer = new ProcessBuilder(arguments)
                .redirectOutput(Redirect.INHERIT)
                .redirectError(Redirect.INHERIT)
                .start();

        long until = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                isAliveYet();
                break;
            } catch (IOException e) {
                if (System.currentTimeMillis() >= until) {
                    throw e;
                }
            }
        }

        LOGGER.info("etcd server launched: {}", etcdServer);
    }

    private void isAliveYet() throws IOException {
        try (InputStream is = new URL("http://127.0.0.1:" + getClientPort() + "/version").openStream();
             LineNumberReader r = new LineNumberReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
            final String version = r.readLine();

            if (version == null || !version.contains("etcdcluster")) {
                throw new IOException("unexpected response '" + version + "'");
            }
        }
    }

    @Override
    public synchronized void close()
    {
        stop();
        try {
            if (configuration.isDestroyNodeOnExit()) {
                Files.walkFileTree(configuration.getDataDirectory(), DeleteRecursively.INSTANCE);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete etcd data directoy", e);
        }
    }

    public synchronized void stop() {
        if (etcdServer == null) {
            return;
        }

        if (!etcdServer.isAlive()) {
            LOGGER.error("etcd server is already dead?!");
        }

        etcdServer.destroy();
        try {
            if (!etcdServer.waitFor(10, TimeUnit.SECONDS)) {
                LOGGER.warn("etcd server failed to exit after 10 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        final int exitValue = etcdServer.exitValue();
        etcdServer = null;
        LOGGER.info("etcd server terminated {}", exitValue == 0 || exitValue == 143 ? "normally" : "with code " + exitValue);
    }

    public synchronized int getClientPort()
    {
        return clientPort;
    }

    public synchronized int getPeerPort()
    {
        return peerPort;
    }

    public synchronized boolean isRunning()
    {
        return etcdServer != null;
    }

    private static int findPort(int configuredPort) throws IOException
    {
        if (configuredPort != 0) {
            return configuredPort;
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String findHostname() throws UnknownHostException
    {
        return InetAddress.getLocalHost().getHostAddress();
    }
}
