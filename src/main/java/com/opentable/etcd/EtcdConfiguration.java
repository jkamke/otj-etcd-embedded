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

import java.nio.file.Path;

public class EtcdConfiguration
{
    private String nodeName;
    private Path dataDirectory;
    private boolean destroyNodeOnExit;
    private String discoveryUri;
    private String hostname;
    private int clientPort;
    private int peerPort;
    private boolean verbose;

    public void setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
    }

    public String getNodeName()
    {
        return nodeName;
    }

    public EtcdConfiguration setDataDirectory(Path dataDirectory)
    {
        this.dataDirectory = dataDirectory;
        return this;
    }

    public Path getDataDirectory()
    {
        return dataDirectory;
    }

    public EtcdConfiguration setDestroyNodeOnExit(boolean destroyNodeOnExit)
    {
        this.destroyNodeOnExit = destroyNodeOnExit;
        return this;
    }

    public boolean isDestroyNodeOnExit()
    {
        return destroyNodeOnExit;
    }

    public EtcdConfiguration setDiscoveryUri(String discoveryUri)
    {
        this.discoveryUri = discoveryUri;
        return this;
    }

    public String getDiscoveryUri()
    {
        return discoveryUri;
    }

    public EtcdConfiguration setHostname(String hostname)
    {
        this.hostname = hostname;
        return this;
    }

    public String getHostname()
    {
        return hostname;
    }

    public EtcdConfiguration setClientPort(int clientPort)
    {
        this.clientPort = clientPort;
        return this;
    }

    public int getClientPort()
    {
        return clientPort;
    }

    public EtcdConfiguration setPeerPort(int peerPort)
    {
        this.peerPort = peerPort;
        return this;
    }

    public int getPeerPort()
    {
        return peerPort;
    }

    public EtcdConfiguration setVerbose(boolean verbose)
    {
        this.verbose = verbose;
        return this;
    }

    public boolean isVerbose()
    {
        return verbose;
    }
}
