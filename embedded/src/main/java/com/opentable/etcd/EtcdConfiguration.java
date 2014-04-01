package com.opentable.etcd;

import java.nio.file.Path;

public class EtcdConfiguration
{
    private Path dataDirectory;
    private boolean destroyNodeOnExit;

    public void setDataDirectory(Path dataDirectory)
    {
        this.dataDirectory = dataDirectory;
    }

    public Path getDataDirectory()
    {
        return dataDirectory;
    }

    public void setDestroyNodeOnExit(boolean destroyNodeOnExit)
    {
        this.destroyNodeOnExit = destroyNodeOnExit;
    }

    public boolean isDestroyNodeOnExit()
    {
        return destroyNodeOnExit;
    }
}
