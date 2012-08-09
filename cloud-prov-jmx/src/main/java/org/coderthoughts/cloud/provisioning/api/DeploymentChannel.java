package org.coderthoughts.cloud.provisioning.api;

import java.io.IOException;

public interface DeploymentChannel {
    long [] getBundleIDs() throws IOException;
    String getBundleSymbolicName(long id) throws IOException;
    void close();
}
