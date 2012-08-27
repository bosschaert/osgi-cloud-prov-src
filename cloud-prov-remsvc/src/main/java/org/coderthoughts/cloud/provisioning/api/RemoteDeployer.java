package org.coderthoughts.cloud.provisioning.api;

import org.osgi.framework.BundleException;

public interface RemoteDeployer {
    long getBundleID(String location);
    String getSymbolicName(long id);
    long installBundle(String location, byte [] base64Data) throws BundleException;
    long [] listBundleIDs();
    void startBundle(long id) throws BundleException;
    void stopBundle(long id) throws BundleException;
    void uninstallBundle(long id) throws BundleException;
}
