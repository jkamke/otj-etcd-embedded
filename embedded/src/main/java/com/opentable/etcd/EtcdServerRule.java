package com.opentable.etcd;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.rules.ExternalResource;
import org.kitei.testing.lessio.AllowAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.io.DeleteRecursively;

@AllowAll
public class EtcdServerRule extends ExternalResource
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdServerRule.class);
    private static final String ETCD_PACKAGE_PATH_FMT = "/etcd-binary-%s/etcd";
    private static final String ETCD_LOCATION;

    static {
        final String etcdPath = String.format(ETCD_PACKAGE_PATH_FMT, (System.getProperty("os.name") + "-" + System.getProperty("os.arch"))
                .replaceAll(" ", "").toLowerCase(Locale.ROOT));

        Path etcdFile = null;
        try {
            etcdFile = Files.createTempFile("etcd", "bin");
            try (InputStream etcd = EtcdServerRule.class.getResourceAsStream(etcdPath)) {
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

    private final Path dataDir;
    private final String id = RandomStringUtils.randomAlphabetic(10);

    @GuardedBy("this")
    private Process etcdServer;

    @GuardedBy("this")
    private int clientPort;

    @GuardedBy("this")
    private int peerPort;

    public static EtcdServerRule singleNode()
    {
        return new EtcdServerRule();
    }

    private EtcdServerRule()
    {
        try {
            dataDir = Files.createTempDirectory("etcd");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int findPort() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    protected synchronized void before() throws Throwable
    {
        if (etcdServer != null) {
            throw new IllegalStateException("already started");
        }

        clientPort = findPort();
        peerPort = findPort();

        final String clientAddr = "0.0.0.0:" + clientPort;
        final String peerAddr = "0.0.0.0:" + peerPort;
        etcdServer = new ProcessBuilder(ETCD_LOCATION,
                "-v",
                "-data-dir", dataDir.toString(),
                "-name", id,
                "-addr", clientAddr,
                "-bind-addr", clientAddr,
                "-peer-addr", peerAddr,
                "-peer-bind-addr", peerAddr).inheritIO().start();

        Thread.sleep(200);
        LOGGER.info("etcd server launched: {}", etcdServer);
    }

    @Override
    protected synchronized void after()
    {
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
        LOGGER.info("etcd server terminated {}", exitValue == 0 || exitValue == 143 ? "normally" : "with code " + exitValue);
        try {
            Files.walkFileTree(dataDir, DeleteRecursively.INSTANCE);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete etcd data directoy", e);
        }
    }

    public synchronized String getConnectString()
    {
        if (etcdServer == null) {
            throw new IllegalStateException("etcd server was not started");
        }
        return "http://127.0.0.1:" + clientPort;
    }

    public Map<String, String> getConfiguration()
    {
        Map<String, String> result = new HashMap<>();
        result.put("ot.discovery", "ETCD");
        result.put("ot.discovery.servers", getConnectString());
        return result;
    }
}
