package org.fusesource.fabric.api.jmx;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * @author Stan Lewis
 */
public class JMXUtils {

    static void registerMBean(Object bean, MBeanServer mBeanServer, ObjectName objectName) throws Exception {
        if (!mBeanServer.isRegistered(objectName)) {
            mBeanServer.registerMBean(bean, objectName);
        } else {
            unregisterMBean(mBeanServer, objectName);
            mBeanServer.registerMBean(bean, objectName);
        }
    }

    static void unregisterMBean(MBeanServer mBeanServer, ObjectName objectName) throws Exception {
        if (mBeanServer.isRegistered(objectName)) {
            mBeanServer.unregisterMBean(objectName);
        }
    }

}
