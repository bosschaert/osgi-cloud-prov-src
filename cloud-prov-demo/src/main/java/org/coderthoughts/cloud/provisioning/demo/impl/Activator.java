package org.coderthoughts.cloud.provisioning.demo.impl;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {
    private Provisioner provisioner;

    @Override
    public void start(BundleContext context) throws Exception {
        provisioner = new Provisioner(context);
        provisioner.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        provisioner.stop();
    }
}
