package com.opentable.etcd;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.rules.ExternalResource;

public class EtcdServerRule extends ExternalResource
{
    private final EtcdInstance instance;

    private EtcdServerRule(EtcdInstance instance)
    {
        this.instance = instance;
    }

    public static EtcdServerRule singleNode()
    {
        return makeNode(new EtcdConfiguration());
    }

    public static List<EtcdServerRule> cluster(int nNodes)
    {
        final String discoveryUrl = newDiscoveryUrl();
        final List<EtcdServerRule> result = new ArrayList<>();
        for (int i = 0; i < nNodes; i++) {
            result.add(makeNode(new EtcdConfiguration().setDiscoveryUri(discoveryUrl)));
        }
        return result;
    }

    private static String newDiscoveryUrl()
    {
        try {
            return IOUtils.toString(new URL("https://discovery.etcd.io/new"), Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static EtcdServerRule makeNode(EtcdConfiguration config)
    {
        Path dir;
        try {
            dir = Files.createTempDirectory("etcd");
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        config.setDataDirectory(dir);
        config.setDestroyNodeOnExit(true);
        return new EtcdServerRule(new EtcdInstance(config));
    }

    @Override
    protected synchronized void before() throws Throwable
    {
        instance.start();
        waitForServerInit();
    }

    private void waitForServerInit() throws IOException {
        final URL versionUrl = new URL(getConnectString() + "/version");
        IOException exc = null;
        for (int i = 0; i < 100; i++) {
            try {
                IOUtils.toString(versionUrl.openStream(), Charsets.UTF_8);
                exc = null;
                break;
            } catch (IOException e) {
                exc = e;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        if (exc != null) {
            throw exc;
        }
    }

    @Override
    protected synchronized void after()
    {
        instance.close();
    }

    public synchronized String getConnectString()
    {
        if (!instance.isRunning()) {
            throw new IllegalStateException("etcd server was not started");
        }
        return "http://127.0.0.1:" + instance.getClientPort();
    }

    public Map<String, String> getConfiguration()
    {
        Map<String, String> result = new HashMap<>();
        result.put("ot.discovery", "ETCD");
        result.put("ot.discovery.servers", getConnectString());
        return result;
    }
}
