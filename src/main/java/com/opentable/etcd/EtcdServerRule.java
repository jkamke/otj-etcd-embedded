package com.opentable.etcd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.kitei.testing.lessio.AllowAll;

@AllowAll
public class EtcdServerRule extends ExternalResource
{
    private final EtcdInstance instance;

    private EtcdServerRule(EtcdInstance instance)
    {
        this.instance = instance;
    }

    public static EtcdServerRule singleNode()
    {
        Path dir;
        try {
            dir = Files.createTempDirectory("etcd");
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        final EtcdConfiguration config = new EtcdConfiguration();
        config.setDataDirectory(dir);
        config.setDestroyNodeOnExit(true);
        return new EtcdServerRule(new EtcdInstance(config));
    }

    @Override
    protected synchronized void before() throws Throwable
    {
        instance.start();
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
