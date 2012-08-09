package org.coderthoughts.cloud.provisioning.demo.impl;

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
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
                    System.out.println("*** Remote Framework Added:" + reference);
                    return super.addingService(reference);
                }

                @Override
                public void removedService(ServiceReference reference, Object service) {
                    System.out.println("*** Remote Framework Removed:" + reference);
                    super.removedService(reference, service);
                }

            };
            frameworkTracker.open();
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    void stop() {
        frameworkTracker.close();
    }
}
