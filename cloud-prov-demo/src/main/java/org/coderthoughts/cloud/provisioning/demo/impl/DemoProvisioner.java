package org.coderthoughts.cloud.provisioning.demo.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
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
    private List<ServiceReference> dynamicDeploymentFrameworks = new CopyOnWriteArrayList<ServiceReference>();
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
        handleFixedDeployments();
        handleDynamicDeployments();
    }

    private void handleFixedDeployments() {
        if (fixedDeploymentsDone)
            return;

        for (ServiceReference ref : frameworkReferences) {
            if (isFixedFramework(ref)) {
                System.out.println("*** Found fixed framework");
                deployWebToFramework(ref);
                fixedDeploymentsDone = true;
                return;
            }
        }
    }

    private boolean isFixedFramework(ServiceReference ref) {
        try {
            String fixedFrameworkHost = "osgix-davidosgi.rhcloud.com";
            Filter filter = bundleContext.createFilter("(org.coderthoughts.framework.ip=" + fixedFrameworkHost +")");
            return filter.match(ref);
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
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
        for (Iterator<ServiceReference> it = dynamicDeploymentFrameworks.iterator(); it.hasNext(); ) {
            ServiceReference ref = it.next();
            if (frameworkReferences.contains(ref)) {
                // Already deployed, nothing to do
                // TODO handle case where deployed to multiple frameworks
                return;
            } else {
                // Framework has disappeared, remove from the list and redeploy elsewhere
                it.remove();
            }
        }

        // Deploy to framework with largest amount of free memory
        ServiceReference fwRef = getDynamicDeploymentFramework();
        if (fwRef == null) {
            System.out.println("*** No dynamic framework to deploy to found yet");
            return;
        }

        deployServiceProviderToFramework(fwRef);
        dynamicDeploymentFrameworks.add(fwRef);

    }

    private void deployServiceProviderToFramework(ServiceReference frameworkReference) {
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        try {
            deployBundles(rd, "/cloud-disco-demo-api-1.0.0-SNAPSHOT.jar", "/cloud-disco-demo-provider-1.0.0-SNAPSHOT.jar");
            System.out.println("*** Service provider framework bundles deployed");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ServiceReference getDynamicDeploymentFramework() {
        SortedMap<Long, ServiceReference> frameworks = new TreeMap<Long, ServiceReference>();
        for (ServiceReference ref : frameworkReferences) {
            if (isFixedFramework(ref))
                continue;

            OSGiFramework fw = (OSGiFramework) bundleContext.getService(ref);
            long mem = Long.parseLong(fw.getFrameworkVariable(OSGiFramework.FV_AVAILABLE_MEMORY));
            frameworks.put(mem, ref);
        }

        if (frameworks.size() == 0)
            return null;
        else
            return frameworks.values().iterator().next();
    }

    private void deployBundles(RemoteDeployer rd, String ... bundleURLs) throws IOException, BundleException {
        List<Long> ids = new ArrayList<Long>();
        for (String u : bundleURLs) {
            if (rd.getBundleID(u) != -1) {
                System.out.println("*** Bundle with location: " + u + " is already deployed. Not redeploying.");
                continue;
            }

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

    /*
    protected void testDeployment(ServiceReference frameworkReference) {
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        System.out.println("***** " + rd.getSymbolicName(1));

        try {
            deployBundles(rd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    */

    void stop() {
        frameworkTracker.close();
        remoteDeployerServiceTracker.close();
    }
}
