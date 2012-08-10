package org.coderthoughts.cloud.provisioning.jmx.impl;

import org.coderthoughts.cloud.provisioning.api.xxx.DeploymentChannel;
import org.coderthoughts.cloud.provisioning.api.xxx.DeploymentChannelFactory;
import org.osgi.framework.ServiceReference;

class DeploymentChannelFactoryImpl implements DeploymentChannelFactory {
    @Override
    public DeploymentChannel createDeploymentChannel(ServiceReference osgiFrameworkReference) {
        return new DeploymentChannelImpl(osgiFrameworkReference);
    }
}
