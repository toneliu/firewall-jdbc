package com.firewall.jdbc;

import com.mysql.cj.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 防火墙 JDBC 包装驱动
 * 继承 MySQL 原生驱动，在建立连接时将当前用户身份透传给数据库防火墙
 *
 * 使用方式：
 * 1. 业务系统 JDBC URL 保持不变，指向防火墙地址（如 jdbc:mysql://firewall-host:13306/db）
 * 2. 驱动类使用 com.firewall.jdbc.FirewallDriver（或依赖 SPI 自动注册）
 * 3. 在请求入口调用 FirewallContextHolder.setCurrentUser(userId)
 * 4. 请求结束后调用 FirewallContextHolder.clear()
 */
public class FirewallDriver extends Driver {

    private static final Logger log = LoggerFactory.getLogger(FirewallDriver.class);

    /**
     * MySQL 标准的连接属性键名，用于通过 MySQL 协议透传自定义属性
     */
    public static final String CONNECTION_ATTRIBUTES_KEY = "connectionAttributes";

    /**
     * 自定义防火墙用户键名，用于属性传递
     */
    public static final String FIREWALL_USER_KEY = "firewall_user";

    /**
     * 在 connectionAttributes 中使用的用户标识键
     */
    public static final String ATTR_USER_KEY = "firewall_user";

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        Properties mergedInfo = (info != null) ? (Properties) info.clone() : new Properties();

        String currentUser = FirewallContextHolder.getCurrentUser();
        if (currentUser != null && !currentUser.trim().isEmpty()) {
            injectUserIdentity(mergedInfo, currentUser.trim());
            log.debug("Injecting firewall user identity to connection: user={}", currentUser);
        } else {
            log.warn("No current user found in FirewallContextHolder. " +
                    "Connection will be established without user identity. " +
                    "Firewall may apply default permissions.");
        }

        return super.connect(url, mergedInfo);
    }

    /**
     * 将用户身份信息注入到连接属性中
     * 同时通过两种方式传递：
     * 1. MySQL 标准 connectionAttributes 属性（以 key=value 格式拼接）
     * 2. 自定义 firewall_user 顶层属性（方便防火墙直接读取）
     *
     * @param info   连接属性对象
     * @param userId 当前用户ID
     */
    private void injectUserIdentity(Properties info, String userId) {
        info.setProperty(FIREWALL_USER_KEY, userId);

        String existingAttrs = info.getProperty(CONNECTION_ATTRIBUTES_KEY, "");
        String userAttr = ATTR_USER_KEY + "=" + userId;

        if (existingAttrs.isEmpty()) {
            info.setProperty(CONNECTION_ATTRIBUTES_KEY, userAttr);
        } else {
            if (!existingAttrs.contains(ATTR_USER_KEY + "=")) {
                info.setProperty(CONNECTION_ATTRIBUTES_KEY, existingAttrs + "," + userAttr);
            } else {
                String updatedAttrs = replaceOrAppendAttribute(existingAttrs, ATTR_USER_KEY, userId);
                info.setProperty(CONNECTION_ATTRIBUTES_KEY, updatedAttrs);
            }
        }
    }

    /**
     * 在 connectionAttributes 字符串中替换或追加属性值
     *
     * @param attrs     原始属性字符串（格式：key1=value1,key2=value2）
     * @param key       目标属性键
     * @param newValue  新的属性值
     * @return 更新后的属性字符串
     */
    private String replaceOrAppendAttribute(String attrs, String key, String newValue) {
        String[] pairs = attrs.split(",");
        StringBuilder result = new StringBuilder();
        boolean found = false;

        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (i > 0) {
                result.append(",");
            }

            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0 && pair.substring(0, eqIndex).trim().equals(key)) {
                result.append(key).append("=").append(newValue);
                found = true;
            } else {
                result.append(pair);
            }
        }

        if (!found) {
            if (result.length() > 0) {
                result.append(",");
            }
            result.append(key).append("=").append(newValue);
        }

        return result.toString();
    }
}
