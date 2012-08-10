package org.coderthoughts.cloud.provisioning.remsvc.impl;

import java.util.Hashtable;

import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {
    private ServiceRegistration reg;

    @Override
    public void start(BundleContext context) throws Exception {
        RemoteDeployer rd = new RemoteDeployerImpl(context);

        Hashtable<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.interfaces", "*");
        props.put("service.exported.configs", new String [] {"org.coderthoughts.configtype.cloud", "<<nodefault>>"});
        reg = context.registerService(RemoteDeployer.class.getName(), rd, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        reg.unregister();
    }
}
