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
    public long[] listBundleID() {
        Bundle[] bundles = bundleContext.getBundles();
        long[] ids = new long[bundles.length];
        for (int i=0; i < bundles.length; i++) {
            ids[i] = bundles[i].getBundleId();
        }
        return ids;
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
        Bundle bundle = bundleContext.getBundle(id);
        if (bundle == null)
            throw new IllegalStateException("No bundle with ID: " + id);

        bundle.start();
    }
}
