package org.coderthoughts.cloud.provisioning.api.xxx;

import org.osgi.framework.ServiceReference;

public interface DeploymentChannelFactory {
    DeploymentChannel createDeploymentChannel(ServiceReference osgiFrameworkReference);
}
