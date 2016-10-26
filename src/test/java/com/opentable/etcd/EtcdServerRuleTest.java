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

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.nio.charset.Charset;

import org.junit.Rule;
import org.junit.Test;

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

            assertTrue(version.contains("etcdcluster"));
        }
    }
}
