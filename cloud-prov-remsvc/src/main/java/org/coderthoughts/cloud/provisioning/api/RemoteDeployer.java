package org.coderthoughts.cloud.provisioning.api;

import org.osgi.framework.BundleException;


public interface RemoteDeployer {
    long installBundle(String location, byte [] base64Data) throws BundleException;
    String getSymbolicName(long id);
    long [] listBundleID();
    long getBundleID(String location);
    void startBundle(long id) throws BundleException;
}
