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

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
        return singleNode(new EtcdConfiguration());
    }

    public static EtcdServerRule singleNode(EtcdConfiguration config)
    {
        return makeNode(config.setDiscoveryUri(newDiscoveryUrl(1)));
    }

    private static String newDiscoveryUrl(int clusterSize)
    {
        try {
            return IOUtils.toString(new URL("https://discovery.etcd.io/new?size=" + clusterSize), StandardCharsets.UTF_8);
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
                IOUtils.toString(versionUrl.openStream(), StandardCharsets.UTF_8);
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

    public EtcdInstance getInstance() {
        return instance;
    }
}
