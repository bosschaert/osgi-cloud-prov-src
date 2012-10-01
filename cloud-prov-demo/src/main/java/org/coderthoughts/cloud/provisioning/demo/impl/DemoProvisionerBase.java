/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.coderthoughts.cloud.provisioning.demo.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

public abstract class DemoProvisionerBase  {
    private final BundleContext bundleContext;
    private final ConcurrentMap<DeploymentType, List<ServiceReference>> deployments = new ConcurrentHashMap<DeploymentType, List<ServiceReference>>();
    private final List<ServiceReference> frameworkReferences = new CopyOnWriteArrayList<ServiceReference>();
    private ServiceTracker frameworkTracker;
    private ServiceTracker remoteDeployerServiceTracker;

    protected DemoProvisionerBase(BundleContext bc) {
        bundleContext = bc;
    }

    /**
     * Called when the topology of frameworks in the Ecosystem changes. An implementation can decide to change the active
     * deployments.
     */
    protected abstract void topologyChanged();

    /**
     * Must provide all the bundles required for a particular deployment type.
     * @param type The deployment type for which the bundles are requested. The deployment types are
     * defined by the provisioner implementation.
     * @param target The target to deploy to.
     * @return The bundles to be deployed.
     */
    protected abstract String [] getDeploymentBundles(DeploymentType type, ServiceReference target);


    /**
     * Return a long value to indicate the suitability of the provided framework for the specified deployment type.
     * @param type The deployment type concerned.
     * @param fw The framework which is a candidate for the deployment. Note that is most likely a remote object
     * and that method calls most likely result in remote service invocations.
     * @param ref The service reference representing the framework passed in.
     * @return A long value. The higher the value the more suitable a framework is. Negative values mean that a
     * framework is not suitable and will generally not cause in deployment to this framwork.
     */
    protected abstract long getSuitabilityIndicator(DeploymentType type, OSGiFramework fw, ServiceReference ref) throws Exception;

    public void start() {
        try {
            // This makes sure that RemoteDeployer service in other frameworks are looked up. It shouldn't really be needed
            Filter filter = bundleContext.createFilter("(&(objectClass=" + RemoteDeployer.class.getName() + ")(service.imported=*))");
            remoteDeployerServiceTracker = new ServiceTracker(bundleContext, filter, null) {
                @Override
                public Object addingService(ServiceReference reference) {
                    System.out.println("*** RemoteDeployer service found for framework: " + reference.getProperty("endpoint.framework.uuid"));
                    return super.addingService(reference);
                }
            };
            remoteDeployerServiceTracker.open();
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" +
                    OSGiFramework.class.getName() + ")(service.imported=*))");
            frameworkTracker = new ServiceTracker(bundleContext, filter, null) {
                @Override
                public Object addingService(ServiceReference reference) {
                    handleFrameworkAdded(reference);
                    return super.addingService(reference);
                }

                @Override
                public void removedService(ServiceReference reference, Object service) {
                    handleFrameworkRemoved(reference);
                    super.removedService(reference, service);
                }
            };
            frameworkTracker.open();
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        frameworkTracker.close();
        remoteDeployerServiceTracker.close();
    }

    protected BundleContext getBundleContext() {
        return bundleContext;
    }

    protected List<ServiceReference> getDeployments(DeploymentType type) {
        List<ServiceReference> refs = deployments.get(type);
        if (refs == null)
            return Collections.emptyList();

        List<ServiceReference> deployed = new ArrayList<ServiceReference>();
        for (ServiceReference ref : new ArrayList<ServiceReference>(refs)) {
            if (getFrameworkReferences().contains(ref)) {
                deployed.add(ref);
            } else {
                // Framework has disappeared, remove from the list
                refs.remove(ref);
            }
        }
        return deployed;
    }

    // Returns a copy that can be modified further by the caller.
    protected List<ServiceReference> getFrameworkReferences() {
        return new ArrayList<ServiceReference>(frameworkReferences);
    }

    protected ServiceReference addDeployment(DeploymentType type) {
        deployments.putIfAbsent(type, new CopyOnWriteArrayList<ServiceReference>());

        List<ServiceReference> possibleFrameworks = getFrameworkReferences();
        possibleFrameworks.removeAll(deployments.get(type));

        ServiceReference target = getMostSuitableFramework(type, possibleFrameworks);
        if (target == null) {
            System.out.println("*** No suitable framework found for deployment of " + type);
            return null;
        }

        System.out.println("*** Adding " + type + " deployment to framework: " + target);
        String[] bundles = getDeploymentBundles(type, target);
        deployToFramework(target, bundles);
        deployments.get(type).add(target);
        return target;
    }

    protected ServiceReference getMostSuitableFramework(DeploymentType type, Collection<ServiceReference> possibleFrameworks) {
        SortedMap<Long, ServiceReference> frameworks = new TreeMap<Long, ServiceReference>();
        for (ServiceReference ref : possibleFrameworks) {
            try {
                OSGiFramework fw = (OSGiFramework) getBundleContext().getService(ref);
                long indicator = getSuitabilityIndicator(type, fw, ref);

                // A don't deploy if the indicator is negative
                if (indicator < 0)
                    continue;

                boolean increase = true;
                while(frameworks.get(indicator) != null) {
                    if (indicator == Long.MAX_VALUE)
                        increase = false;

                    // in case there is another framework with the same indication value we find a
                    // free spot on the map close to this value
                    indicator += (increase ? 1 : -1);
                }

                frameworks.put(indicator, ref);
            } catch (Exception e) {
                // log error and continue
                System.out.println("*** Exception while obtaining framework suitability: " + e);
            }
        }

        if (frameworks.size() == 0)
            return null;

        Long highVal = frameworks.lastKey();
        ServiceReference mostSuitable = frameworks.get(highVal);
        System.out.println("*** Found most suitable framework for deployment of " + type + ":" +
                mostSuitable.getProperty("org.coderthoughts.framework.ip"));
        return mostSuitable;
    }

    private void handleFrameworkAdded(final ServiceReference reference) {
        // Do this asynchronously
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("*** Remote Framework Added: " + reference.getProperty("org.coderthoughts.framework.ip"));
                    frameworkReferences.add(reference);

                    topologyChanged();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleFrameworkRemoved(final ServiceReference reference) {
        // Do this asynchronously
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("*** Remote Framework Removed: " + reference.getProperty("org.coderthoughts.framework.ip"));
                    frameworkReferences.remove(reference);

                    topologyChanged();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void deployToFramework(ServiceReference frameworkReference, String[] bundles) {
        RemoteDeployer rd = getRemoteDeployer(frameworkReference);
        try {
            deployBundles(rd, bundles);
            System.out.println("*** Bundles deployed to framework " + frameworkReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                ServiceReference[] refs = getBundleContext().getServiceReferences(RemoteDeployer.class.getName(), "(endpoint.framework.uuid=" + fwkUUID + ")");
                if (refs != null && refs.length > 0)
                    return (RemoteDeployer) getBundleContext().getService(refs[0]);

                TimeUnit.SECONDS.sleep(1);
                i++;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Unable to find RemoteDeployer for framework: " + frameworkReference);
    }

    protected static class DeploymentType {
        private final String type;

        protected DeploymentType(String t) {
            type = t;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            DeploymentType other = (DeploymentType) obj;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }
}
