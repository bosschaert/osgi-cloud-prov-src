package org.coderthoughts.cloud.provisioning.demo.impl;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
import org.coderthoughts.cloud.provisioning.api.Base64;
import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class Provisioner {
    private final BundleContext bundleContext;
    private ServiceTracker frameworkTracker;
    private ServiceTracker remoteDeployerServiceTracker;

    Provisioner(BundleContext context) {
        bundleContext = context;
    }

    void start() {
        // This makes sure that RemoteDeployer service in other frameworks are looked up. It shouldn't really be needed
        remoteDeployerServiceTracker = new ServiceTracker(bundleContext, RemoteDeployer.class.getName(), null);
        remoteDeployerServiceTracker.open();

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
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        System.out.println("***** " + rd.getSymbolicName(1));

        URL url = getClass().getResource("/BundleUsingEmbeddedJar_1.0.0.jar");
        try {
            byte[] b64Data = Base64.encode(Streams.suck(url.openStream()));
            long id = rd.installBundle("somelocation", b64Data);
            rd.startBundle(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RemoteDeployer getRemoteDeployer(ServiceReference frameworkReference) {
        int retries = 15;

        Object fwkUUID = frameworkReference.getProperty("endpoint.framework.uuid");
        if (fwkUUID == null)
            throw new IllegalStateException("Framework UUID not found for framework: " + frameworkReference);

        try {
            int i=0;
            while (i < retries) {
                ServiceReference[] refs = bundleContext.getServiceReferences(RemoteDeployer.class.getName(), "(endpoint.framework.uuid=" + fwkUUID + ")");
                if (refs != null && refs.length > 0)
                    return (RemoteDeployer) bundleContext.getService(refs[0]);

                TimeUnit.SECONDS.sleep(1);
                i++;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Unable to find RemoteDeployer for framework: " + frameworkReference);
    }

    void stop() {
        frameworkTracker.close();
        remoteDeployerServiceTracker.close();
    }
}
