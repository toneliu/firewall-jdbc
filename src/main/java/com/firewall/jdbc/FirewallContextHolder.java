package com.firewall.jdbc;

/**
 * 防火墙用户上下文持有者
 * 使用 ThreadLocal 存储当前请求的用户ID，用于在建立数据库连接时透传给防火墙
 */
public class FirewallContextHolder {

    private static final ThreadLocal<String> CURRENT_USER_HOLDER = new ThreadLocal<>();

    private FirewallContextHolder() {
    }

    /**
     * 设置当前用户ID
     * 在请求入口（如Filter/Interceptor）中调用
     *
     * @param userId 当前登录用户的唯一标识
     */
    public static void setCurrentUser(String userId) {
        CURRENT_USER_HOLDER.set(userId);
    }

    /**
     * 获取当前用户ID
     *
     * @return 当前用户ID，如果未设置则返回 null
     */
    public static String getCurrentUser() {
        return CURRENT_USER_HOLDER.get();
    }

    /**
     * 清除当前用户上下文
     * 必须在请求结束的 finally 块中调用，防止内存泄漏
     */
    public static void clear() {
        CURRENT_USER_HOLDER.remove();
    }
}
