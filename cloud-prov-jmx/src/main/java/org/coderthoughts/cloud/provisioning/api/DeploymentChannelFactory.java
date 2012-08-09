package org.coderthoughts.cloud.provisioning.api;

import org.osgi.framework.ServiceReference;

public interface DeploymentChannelFactory {
    DeploymentChannel createDeploymentChannel(ServiceReference osgiFrameworkReference);
}
