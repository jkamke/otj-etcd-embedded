package com.opentable.etcd;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
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

import com.opentable.io.DeleteRecursively;

public class EtcdInstance implements Closeable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdInstance.class);
    private static final String ETCD_PACKAGE_PATH_FMT = "/etcd-binary-%s/etcd";
    private static final String ETCD_LOCATION;

    static {
        final String etcdPath = String.format(ETCD_PACKAGE_PATH_FMT, (System.getProperty("os.name") + "-" + System.getProperty("os.arch"))
                .replaceAll(" ", "").toLowerCase(Locale.ROOT));

        Path etcdFile = null;
        try {
            etcdFile = Files.createTempFile("etcd", "bin");
            try (InputStream etcd = EtcdInstance.class.getResourceAsStream(etcdPath)) {
                if (etcd == null) {
                    throw new IllegalStateException("Could not find " + etcdPath + " on classpath");
                }
                Files.copy(etcd, etcdFile, StandardCopyOption.REPLACE_EXISTING);
                Files.setPosixFilePermissions(etcdFile, PosixFilePermissions.fromString("r-x------"));
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

    public synchronized void start() throws IOException
    {
        if (etcdServer != null) {
            throw new IllegalStateException("already started");
        }

        clientPort = findPort(configuration.getClientPort());
        peerPort = findPort(configuration.getPeerPort());

        final String hostname = configuration.getHostname() != null ? configuration.getHostname() : findHostname();

        final String clientAddr = hostname + ':' + clientPort;
        final String peerAddr = hostname + ':' + peerPort;

        final List<String> arguments = new ArrayList<>();
        arguments.addAll(Arrays.asList(
                ETCD_LOCATION,
                "-data-dir", configuration.getDataDirectory().toString(),
                "-name", id,
                "-addr", clientAddr,
                "-peer-addr", peerAddr,
                "-peer-bind-addr", "0.0.0.0:" + peerPort));

        if (configuration.isVerbose()) {
            arguments.add("-v");
        }

        if (configuration.getDiscoveryUri() != null) {
            arguments.add("-discovery");
            arguments.add(configuration.getDiscoveryUri());
        }

        LOGGER.info("Launching etcd: {}", arguments);

        etcdServer = new ProcessBuilder(arguments)
                .redirectOutput(Redirect.INHERIT)
                .redirectError(Redirect.INHERIT)
                .start();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        LOGGER.info("etcd server launched: {}", etcdServer);
    }

    @Override
    public synchronized void close()
    {
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
        try {
            if (configuration.isDestroyNodeOnExit()) {
                Files.walkFileTree(configuration.getDataDirectory(), DeleteRecursively.INSTANCE);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete etcd data directoy", e);
        }
    }

    public int getClientPort()
    {
        return clientPort;
    }

    public int getPeerPort()
    {
        return peerPort;
    }

    public boolean isRunning()
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
        return InetAddress.getLocalHost().getHostName();
    }
}
