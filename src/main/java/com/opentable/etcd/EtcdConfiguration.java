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

    public void setDiscoveryUri(String discoveryUri)
    {
        this.discoveryUri = discoveryUri;
    }

    public String getDiscoveryUri()
    {
        return discoveryUri;
    }

    public void setHostname(String hostname)
    {
        this.hostname = hostname;
    }

    public String getHostname()
    {
        return hostname;
    }

    public void setClientPort(int clientPort)
    {
        this.clientPort = clientPort;
    }

    public int getClientPort()
    {
        return clientPort;
    }

    public void setPeerPort(int peerPort)
    {
        this.peerPort = peerPort;
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
