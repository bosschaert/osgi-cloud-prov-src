package org.coderthoughts.cloud.provisioning.demo.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
import org.coderthoughts.cloud.provisioning.api.Base64;
import org.coderthoughts.cloud.provisioning.api.RemoteDeployer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class DemoProvisioner {
    private final BundleContext bundleContext;
    private ServiceTracker frameworkTracker;
    private List<ServiceReference> frameworkReferences = new CopyOnWriteArrayList<ServiceReference>();
    private ServiceTracker remoteDeployerServiceTracker;
    private boolean fixedDeploymentsDone;

    DemoProvisioner(BundleContext context) {
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
                    frameworkReferences.add(reference);
                    /* */
                    // testDeployment(reference);
                    /* */
                    handleTopologyChange(reference);
                    return super.addingService(reference);
                }

                @Override
                public void removedService(ServiceReference reference, Object service) {
                    frameworkReferences.remove(reference);
                    System.out.println("*** Remote Framework Removed: " + reference.getProperty("org.coderthoughts.framework.ip"));
                    super.removedService(reference, service);
                }
            };
            frameworkTracker.open();
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    protected void handleTopologyChange(ServiceReference reference) {
        if (fixedDeploymentsHandled())
            handleDynamicDeployments();
    }

    private boolean fixedDeploymentsHandled() {
        if (fixedDeploymentsDone)
            return true;

        String fixedFrameworkHost = "osgix-davidosgi.rhcloud.com";
        System.out.println("*** Looking for framework on host: " + fixedFrameworkHost);
        try {
            Filter filter = bundleContext.createFilter("(org.coderthoughts.framework.ip=" + fixedFrameworkHost +")");
            for (ServiceReference ref : frameworkReferences) {
                if (filter.match(ref)) {
                    System.out.println("*** Found fixed framework");
                    deployWebToFramework(ref);
                    fixedDeploymentsDone = true;
                    return true;
                }
            }

        } catch (InvalidSyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("*** Fixed framework not found");

        return false;
    }

    private void deployWebToFramework(ServiceReference frameworkReference) {
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        try {
            deployBundles(rd, "/cloud-disco-demo-api-1.0.0-SNAPSHOT.jar", "/cloud-disco-demo-web-ui-1.0.0-SNAPSHOT.jar");
            System.out.println("*** Fixed framework bundles deployed");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleDynamicDeployments() {
    }

    protected void testDeployment(ServiceReference frameworkReference) {
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        System.out.println("***** " + rd.getSymbolicName(1));

        try {
            deployBundles(rd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deployBundles(RemoteDeployer rd, String ... bundleURLs) throws IOException, BundleException {
        List<Long> ids = new ArrayList<Long>();
        for (String u : bundleURLs) {
            URL url = getClass().getResource(u);
            byte[] b64Data = Base64.encode(Streams.suck(url.openStream()));
            ids.add(rd.installBundle(u, b64Data));
        }

        for (long id : ids) {
            rd.startBundle(id);
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
