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

import org.coderthoughts.cloud.framework.service.api.OSGiFramework;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public class DemoProvisioner extends DemoProvisionerBase {
    private static final String [] WEB_BUNDLES = {"/cloud-disco-demo-api-1.0.0-SNAPSHOT.jar", "/cloud-disco-demo-web-ui-1.0.0-SNAPSHOT.jar"};
    private static final String [] SERVICE_BUNDLES = {"/cloud-disco-demo-api-1.0.0-SNAPSHOT.jar", "/cloud-disco-demo-provider-1.0.0-SNAPSHOT.jar"};

    private static final DeploymentType WEB = new DeploymentType("WEB");
    private static final DeploymentType SERVICE = new DeploymentType("SERVICE");

    DemoProvisioner(BundleContext context) {
        super(context);
    }

    @Override
    protected void topologyChanged() {
        // Deploy the web frontend
        if (getDeployments(WEB).size() == 0)
            addDeployment(WEB);

        // Deploy the Service two times.
        if (getDeployments(SERVICE).size() < 2)
            addDeployment(SERVICE);

    }

    @Override
    protected String [] getDeploymentBundles(DeploymentType type, ServiceReference target) {
        if (WEB.equals(type))
            return WEB_BUNDLES;
        else if (SERVICE.equals(type))
            return SERVICE_BUNDLES;

        throw new IllegalStateException("Unregognized deployment type: " + type);
    }

    @Override
    protected long getSuitabilityIndicator(DeploymentType type, OSGiFramework fw, ServiceReference fwRef) throws Exception {
        if (WEB.equals(type))
            return getWebSuitabilityIndicator(fwRef);
        else if (SERVICE.equals(type))
            return getServiceSuitabilityIndicator(fw, fwRef);

        throw new IllegalStateException("Unregognized deployment type: " + type);
    }

    private long getServiceSuitabilityIndicator(OSGiFramework fw, ServiceReference fwRef) throws Exception {
        // Don't deploy the service where the web frontend goes
        if (getWebSuitabilityIndicator(fwRef) > 0)
            return -1;

        return Long.parseLong(fw.getFrameworkVariable(OSGiFramework.FV_AVAILABLE_MEMORY));
    }

    private long getWebSuitabilityIndicator(ServiceReference fwRef) throws InvalidSyntaxException {
        String fixedFrameworkHost = "web-*";
        Filter filter = getBundleContext().createFilter("(org.coderthoughts.framework.ip=" + fixedFrameworkHost +")");
        return filter.match(fwRef) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
