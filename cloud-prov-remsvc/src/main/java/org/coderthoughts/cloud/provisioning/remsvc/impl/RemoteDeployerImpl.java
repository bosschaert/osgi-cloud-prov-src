package org.coderthoughts.cloud.provisioning.remsvc.impl;

import java.io.ByteArrayInputStream;

import org.coderthoughts.cloud.provisioning.api.Base64;
import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class RemoteDeployerImpl implements RemoteDeployer {
    private final BundleContext bundleContext;

    public RemoteDeployerImpl(BundleContext context) {
        bundleContext = context;
    }

    @Override
    public long[] listBundleIDs() {
        Bundle[] bundles = bundleContext.getBundles();
        long[] ids = new long[bundles.length];
        for (int i=0; i < bundles.length; i++) {
            ids[i] = bundles[i].getBundleId();
        }
        return ids;
    }

    @Override
    public long getBundleID(String location) {
        for (Bundle b : bundleContext.getBundles()) {
            if (b.getLocation().equals(location))
                return b.getBundleId();
        }
        return -1;
    }

    @Override
    public String getSymbolicName(long id) {
        Bundle bundle = bundleContext.getBundle(id);
        if (bundle == null)
            return null;
        return bundle.getSymbolicName();
    }

    @Override
    public long installBundle(String location, byte [] base64Data) throws BundleException {
        ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(base64Data));
        Bundle bundle = bundleContext.installBundle(location, bais);
        return bundle.getBundleId();
    }

    @Override
    public void startBundle(long id) throws BundleException {
        getBundle(id).start();
    }

    @Override
    public void stopBundle(long id) throws BundleException {
        getBundle(id).stop();
    }

    @Override
    public void uninstallBundle(long id) throws BundleException {
        getBundle(id).uninstall();
    }

    private Bundle getBundle(long id) {
        Bundle bundle = bundleContext.getBundle(id);
        if (bundle == null)
            throw new IllegalStateException("No bundle with ID: " + id);
        return bundle;
    }
}
