package org.coderthoughts.cloud.provisioning.jmx.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.coderthoughts.cloud.provisioning.api.DeploymentChannel;
import org.osgi.framework.ServiceReference;
import org.osgi.jmx.framework.BundleStateMBean;

class DeploymentChannelImpl implements DeploymentChannel {
    private static final String INSTALL_ROOT;
    static {
        String dir = System.getenv("OPENSHIFT_GEAR_DIR");
        if (dir == null) {
            dir = new File(System.getProperty("user.dir")).getParentFile().getParentFile().getAbsolutePath();
            System.out.println("Environment variable OPENSHIFT_GEAR_DIR is not set, defaulting to: " + dir);
        } else {
            dir = dir + "/repo";
        }
        INSTALL_ROOT = dir;
    }
    private static final String [] SSH_COMMAND_PREFIX = {"ssh",
        "-q",
        "-g",
        "-o",
        "StrictHostKeyChecking no",
        "-i",
        INSTALL_ROOT + "/repo/disco-tunnel/disco_id_rsa",
        "-N",
        "-L"};
//    ,
//        "127.0.0.1:21810:127.8.228.1:21810",
//        "20c7346e55af4403ae6cbeb415ae1203@discoserver-davidosgi.rhcloud.com"};

    private final JMXConnector jmxConnectorFactory;
    private final Process sshTunnelProcess;
    private final BundleStateMBean mbeanProxy;
    private final String remoteExternalIP;

    DeploymentChannelImpl(ServiceReference osgiFrameworkReference) {
        String internalIP = System.getenv("OPENSHIFT_INTERNAL_IP");
        if (internalIP == null)
            throw new RuntimeException("Environment variable OPENSHIFT_INTERNAL_IP is not set. It should be set to the internal IP address of the current instance");

        String remoteInternalIP = (String) osgiFrameworkReference.getProperty("org.coderthoughts.openshift.internal.ip");
        if (remoteInternalIP == null)
            throw new RuntimeException("Remote Framework does not have the service property 'org.coderthoughts.openshift.internal.ip' set");

        String remoteGearUUID = (String) osgiFrameworkReference.getProperty("org.coderthoughts.openshift.gear.uuid");
        if (remoteGearUUID == null)
            throw new RuntimeException("Remote Framework does not have the service property 'org.coderthoughts.openshift.gear.uuid' set");

        remoteExternalIP = (String) osgiFrameworkReference.getProperty("org.coderthoughts.framework.ip");
        if (remoteExternalIP == null)
            throw new RuntimeException("Remote Framework does not have the service property 'org.coderthoughts.framework.ip' set");

        List<String> cmdList = new ArrayList<String>(Arrays.asList(SSH_COMMAND_PREFIX));
        String localPort = "23456";
        cmdList.add(internalIP + ":" + localPort + ":" + remoteInternalIP + ":29999");
        cmdList.add(remoteGearUUID + "@" + remoteExternalIP);

        System.out.println("*** Creating SSH tunnel: " + cmdList);
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmdList.toArray(new String [] {}));
        } catch (IOException e) {
//            if (process != null)
//                process.destroy();
            throw new RuntimeException(e);
        }
        sshTunnelProcess = process;

        System.out.println("*** Created SSH Tunnel, now creating JMX connection");
        try {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://:" + localPort + "/jmxrmi");
            jmxConnectorFactory = JMXConnectorFactory.connect(url, null);
            MBeanServerConnection mbsc = jmxConnectorFactory.getMBeanServerConnection();

            ObjectName mbeanName = new ObjectName("osgi.core:type=bundleState,version=1.5");
            mbeanProxy = JMX.newMBeanProxy(mbsc, mbeanName, BundleStateMBean.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        waitUntilChannelActive(10000);
    }

    private void waitUntilChannelActive(int timeout) {
        int i = 0;
        while ((i*1000) < timeout) {
            try {
                String res = mbeanProxy.getSymbolicName(0);
                System.out.println("*** Remote System Bundle: " + res);
                return;
            } catch (IOException e) {
                // Channel is still being set up
            }

            i++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        throw new RuntimeException("Unable to open Deployment Channel to " + remoteExternalIP);
    }

    @Override
    public long[] getBundleIDs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getBundleSymbolicName(long id) throws IOException {
        return mbeanProxy.getSymbolicName(0);
    }

    @Override
    public void close() {
        try {
            jmxConnectorFactory.close();
            sshTunnelProcess.destroy();
        } catch (Throwable e) {
            // ignore
        }
    }
}
