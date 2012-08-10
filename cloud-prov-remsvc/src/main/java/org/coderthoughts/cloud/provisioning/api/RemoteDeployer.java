package org.coderthoughts.cloud.provisioning.api;

public interface RemoteDeployer {
    long [] listBundleID();
    String getSymbolicName(long id);
}
