package com.el.solr.listener;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.*;

public class SolrInitListener implements ServletContextListener {
    public static final String TOMCAT_PORT = "tomcat.port";
    public Logger log = Logger.getLogger(SolrInitListener.class);

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        setConfigs(System.getProperty("solr.solr.home"));
    }

    private void getTomcatInfos(List<Map> connectors, MBeanServer platformMBeanServer, ObjectName objectPool) {
        String[] poolKeys = new String[]{"protocol", "scheme", "address", "port", "redirectPort", "compression", "bufferSize", "maxPostSize", "maxHttpHeaderSize", "connectionUploadTimeout", "disableUploadTimeout", "URIEncoding", "useBodyEncodingForURI", "enableLookups", "proxyName", "proxyPort", "maxThreads", "maxSpareThreads", "minSpareThreads", "acceptCount", "connectionLinger", "connectionTimeout", "tcpNoDelay", "maxKeepAliveRequests", "keepAliveTimeout", "strategy", "xpoweredBy", "allowTrace"};
        Map value = getValueFromMbean(connectors, platformMBeanServer, objectPool, poolKeys);
        if (value != null) {
            if (value.get("protocol") != null && value.get("protocol").toString().toLowerCase().contains("http")) {
                connectors.add(value);
            }
        }
    }

    private Map getValueFromMbean(List list, MBeanServer beanServer, ObjectName objectName, String[] poolKeys) {
        Map value = new LinkedHashMap();
        try {
            for (String poolKey : poolKeys) {
                Object attribute = beanServer.getAttribute(objectName, poolKey);
                System.out.println("tomcat info " + poolKey + " = " + attribute);
                if ("minTime".equals(poolKey) && attribute.equals(0x7FFFFFFFFFFFFFFFL)) {
                    attribute = 0;
                }
                value.put(poolKey, attribute);
            }
            String name = objectName.getKeyProperty("name");
            if (name != null) {
                list.add(value);
            }
        } catch (Exception e) {
            log.error("=getValueFromMbean=>error: get mbean value error! namedObject=" + objectName, e);
        }
        return value;
    }

    private void setConfigs(String solrHome) {
        if (StringUtils.isEmpty(System.getProperty(TOMCAT_PORT))) {
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            try {
                Set<ObjectName> objectNames = platformMBeanServer.queryNames(new ObjectName("Catalina:type=Connector,*"), null);
                List<Map> connectors = new LinkedList<Map>();
                String port = "";
                for (ObjectName objectName : objectNames) {
                    getTomcatInfos(connectors, platformMBeanServer, objectName);
                    if (connectors != null) {
                        System.out.println("===========================================================================");
                        for (Map map : connectors) {
                            System.out.println("tomcat env: " + map.toString().replace(",", "\n"));
                            log.warn("tomcat env: " + map.toString().replace(",", "\n"));
                        }
                        System.out.println("===========================================================================");
                        if (CollectionUtils.isNotEmpty(connectors)) {
                            if (connectors.get(0) != null) {
                                port = connectors.get(0).get("port").toString();
                                System.setProperty(TOMCAT_PORT, port);
                                System.out.println("tomcat端口号：" + port);
                                break;
                            }
                        } else {
                            port = platformMBeanServer.getAttribute(objectName, "port").toString();
                            System.setProperty(TOMCAT_PORT, port);
                            System.out.println("tomcat端口号1：" + port);
                            log.warn("tomcat env: no connectors");
                            System.out.println("tomcat env: no connectors");
                        }
                    }
                }
                if (StringUtils.isBlank(port)) {
                    throw new RuntimeException("端口获取失败.");
                }
            } catch (Exception e) {
                log.error("tomcat端口获取失败", e);
                throw new IllegalArgumentException("tomcat端口获取失败", e);
            }
        }

        URL propertiesUrl = SolrInitListener.class.getClassLoader().getResource("solr/csmfeedbackrecord/conf/important.properties");
        if (propertiesUrl == null) {
            log.error("配置文件加载错误");
            throw new ExceptionInInitializerError("配置文件加载出错");
        }
        Properties properties = new Properties();
        try {
            properties.load(SolrInitListener.class.getClassLoader().getResourceAsStream("solr/csmfeedbackrecord/conf/important.properties"));
        } catch (IOException e) {
            log.error("配置文件加载错误2", e);
            throw new ExceptionInInitializerError("配置文件加载出错");
        }
        System.out.println("zkHost:" + properties.get("zkHost"));
        if (null != properties.get("zkHost")) {
            System.setProperty("zkHost", properties.get("zkHost").toString().trim());
        } else {
            log.error("配置文件加载错误2,没有获得zkhost");
            throw new ExceptionInInitializerError("配置文件加载错误2,没有获得zkhost");
        }
        System.out.println("bootstrap_conf:" + properties.get("bootstrap_conf"));
        if (null != properties.get("bootstrap_conf")) {
            System.setProperty("bootstrap_conf", "true");
        }
        System.out.println("numShards:" + properties.get("numShards"));
        if (null != properties.get("numShards")) {
            System.setProperty("numShards", properties.get("numShards").toString());
        }
        System.out.println("shardName:" + properties.get("shardName"));
        if (null != properties.get("shardName")) {
            System.setProperty("shardName", properties.get("shardName").toString());
        }
        System.out.println("solr.data.dir:" + properties.get("solr.data.dir"));
        if (null != properties.get("solr.data.dir")) {
            System.setProperty("solr.data.dir", properties.get("solr.data.dir").toString());
        } else {
            log.error("solr data dir配置错误");
            throw new ExceptionInInitializerError("solr data dir配置错误");
        }
        if (StringUtils.isBlank(solrHome))
            try {
                URL url = super.getClass().getClassLoader().getResource("/solr");
                if (url != null) {
                    File file = new File(url.toURI());
                    if (file.exists())
                        System.setProperty("solr.solr.home", file.getCanonicalPath());
                }
            } catch (Exception e) {
                throw new RuntimeException("启动设置solr/home失败，重启应用", e);
            }
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    }
}
