package com.opentable.etcd;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.nio.charset.Charset;

import org.junit.Rule;
import org.junit.Test;
import org.kitei.testing.lessio.AllowNetworkAccess;

@AllowNetworkAccess(endpoints= {"127.0.0.1:*"})
public class EtcdServerRuleTest
{
    @Rule
    public EtcdServerRule etcdServer = EtcdServerRule.singleNode();

    @Test
    public void smokeTest() throws Exception
    {
        try (InputStream is = new URL(etcdServer.getConnectString() + "/version").openStream()) {
            final String version = new LineNumberReader(
                    new InputStreamReader(is, Charset.forName("UTF-8"))).readLine();

            assertTrue(version.startsWith("etcd "));
        }
    }
}
