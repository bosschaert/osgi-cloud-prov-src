package org.coderthoughts.cloud.provisioning.jmx.impl;

import java.util.List;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.jmx.framework.BundleStateMBean;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://:9999/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

        ObjectName mbeanName = new ObjectName("osgi.core:type=bundleState,version=1.5");
        BundleStateMBean mbeanProxy = JMX.newMBeanProxy(mbsc, mbeanName, BundleStateMBean.class);
        TabularData bundles = mbeanProxy.listBundles();
        for (List<?> key : (Set<List<?>>) bundles.keySet()) {
            CompositeData entry = bundles.get(key.toArray(new Object [] {}));
            System.out.println("*** entry: " + entry.get(BundleStateMBean.IDENTIFIER) + " " + entry.get(BundleStateMBean.SYMBOLIC_NAME));
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }
}
