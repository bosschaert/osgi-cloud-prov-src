package org.coderthoughts.cloud.provisioning.demo.impl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
import org.coderthoughts.cloud.provisioning.api.DeploymentChannel;
import org.coderthoughts.cloud.provisioning.api.DeploymentChannelFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class Provisioner {
    private final BundleContext bundleContext;
    private ServiceTracker depChannelFactTracker;
    private ServiceTracker frameworkTracker;
    private List<DeploymentChannelFactory> depChannelFactories = new CopyOnWriteArrayList<DeploymentChannelFactory>();

    Provisioner(BundleContext context) {
        bundleContext = context;
    }

    void start() {
        depChannelFactTracker = new ServiceTracker(bundleContext, DeploymentChannelFactory.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference reference) {
                Object svc = super.addingService(reference);
                if (svc instanceof DeploymentChannelFactory)
                    depChannelFactories.add((DeploymentChannelFactory) svc);
                return svc;
            }

            @Override
            public void removedService(ServiceReference reference, Object service) {
                depChannelFactories.remove(service);
                super.removedService(reference, service);
            }
        };
        depChannelFactTracker.open();

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
                    testDeploymentChannel(reference);
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

    protected void testDeploymentChannel(ServiceReference reference) {
        if (depChannelFactories.size() == 0)
            throw new IllegalStateException("No services found that implement " + DeploymentChannelFactory.class);

        DeploymentChannel channel = depChannelFactories.get(0).createDeploymentChannel(reference);
        try {
            System.out.println("****** " + channel.getBundleSymbolicName(1));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            channel.close();
        }
    }

    void stop() {
        frameworkTracker.close();
        depChannelFactTracker.close();
    }
}
