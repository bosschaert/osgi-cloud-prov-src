package org.coderthoughts.cloud.provisioning.demo.impl;

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class Provisioner {
    private final BundleContext bundleContext;
    private ServiceTracker frameworkTracker;

    Provisioner(BundleContext context) {
        bundleContext = context;
    }

    void start() {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" +
                    OSGiFramework.class.getName() + ")(service.imported=*))");
            frameworkTracker = new ServiceTracker(bundleContext, filter, null) {
                @Override
                public Object addingService(ServiceReference reference) {
                    System.out.println("*** Remote Framework Added: " + reference.getProperty("org.coderthoughts.framework.ip"));
                    System.out.println("    Internal IP: " + reference.getProperty("org.coderthoughts.openshift.internal.ip"));
                    System.out.println("    Gear UUID: " + reference.getProperty("org.coderthoughts.openshift.gear.uuid"));

                    /* */
                    testDeployment(reference);
                    /* */
                    return super.addingService(reference);
                }

                @Override
                public void removedService(ServiceReference reference, Object service) {
                    System.out.println("*** Remote Framework Removed: " + reference.getProperty("org.coderthoughts.framework.ip"));
                    super.removedService(reference, service);
                }
            };
            frameworkTracker.open();
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    protected void testDeployment(ServiceReference frameworkReference) {
        Object fwkUUID = frameworkReference.getProperty("endpoint.framework.uuid");
        if (fwkUUID == null)
            throw new IllegalStateException("Framework UUID not found for framework: " + frameworkReference);

        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(RemoteDeployer.class.getName(), "(endpoint.framework.uuid=" + fwkUUID + ")");
            if (refs == null || refs.length == 0)
                throw new IllegalStateException("Unable to find RemoteDeployer for framework: " + frameworkReference);

            RemoteDeployer rd = (RemoteDeployer) bundleContext.getService(refs[0]);
            System.out.println("***** " + rd.getSymbolicName(1));
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    void stop() {
        frameworkTracker.close();
    }
}
