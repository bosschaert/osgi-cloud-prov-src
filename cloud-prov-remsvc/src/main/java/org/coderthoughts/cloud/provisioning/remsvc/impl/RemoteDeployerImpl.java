package org.coderthoughts.cloud.provisioning.remsvc.impl;

import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

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
}
