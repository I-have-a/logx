# 用户上下文示例
```java
package com.example.logx.config;

import com.domidodo.logx.sdk.spring.context.UserContextProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ========================================
 * 示例1: JWT Token 用户上下文提供器
 * ========================================
 * 从 JWT Token 中解析用户信息
 */
@Slf4j
@Component("jwtUserContextProvider")
public class JwtUserContextProvider implements UserContextProvider {
    
    @Value("${jwt.secret:your-secret-key}")
    private String jwtSecret;
    
    @Override
    public String getUserId(HttpServletRequest request) {
        Claims claims = parseJwt(request);
        return claims != null ? claims.get("userId", String.class) : null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        Claims claims = parseJwt(request);
        return claims != null ? claims.get("userName", String.class) : null;
    }
    
    @Override
    public String getTenantId(HttpServletRequest request) {
        Claims claims = parseJwt(request);
        return claims != null ? claims.get("tenantId", String.class) : null;
    }
    
    private Claims parseJwt(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token != null) {
                return Jwts.parser()
                        .setSigningKey(jwtSecret.getBytes())
                        .parseClaimsJws(token)
                        .getBody();
            }
        } catch (Exception e) {
            log.debug("Failed to parse JWT", e);
        }
        return null;
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

/**
 * ========================================
 * 示例2: Spring Security 用户上下文提供器
 * ========================================
 * 从 Spring Security 上下文中获取用户信息
 */
@Slf4j
@Component("securityUserContextProvider")
class SecurityUserContextProvider implements UserContextProvider {
    
    @Override
    public String getUserId(HttpServletRequest request) {
        return getUserDetails() != null ? 
                getUserDetails().getUsername() : null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        // 假设 UserDetails 实现类中有 getRealName() 方法
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getRealName();
        }
        
        return getUserDetails() != null ? 
                getUserDetails().getUsername() : null;
    }
    
    @Override
    public String getTenantId(HttpServletRequest request) {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getTenantId();
        }
        
        return null;
    }
    
    private org.springframework.security.core.userdetails.UserDetails getUserDetails() {
        try {
            Object principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();
            
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return (org.springframework.security.core.userdetails.UserDetails) principal;
            }
        } catch (Exception e) {
            log.debug("Failed to get UserDetails", e);
        }
        return null;
    }
    
    // 自定义 UserDetails 实现
    static class CustomUserDetails implements org.springframework.security.core.userdetails.UserDetails {
        private String userId;
        private String username;
        private String realName;
        private String tenantId;
        private String password;
        
        // Getters and other UserDetails methods...
        public String getRealName() { return realName; }
        public String getTenantId() { return tenantId; }
        
        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return java.util.Collections.emptyList();
        }
        
        @Override
        public String getPassword() { return password; }
        
        @Override
        public String getUsername() { return username; }
        
        @Override
        public boolean isAccountNonExpired() { return true; }
        
        @Override
        public boolean isAccountNonLocked() { return true; }
        
        @Override
        public boolean isCredentialsNonExpired() { return true; }
        
        @Override
        public boolean isEnabled() { return true; }
    }
}

/**
 * ========================================
 * 示例3: ThreadLocal 用户上下文提供器
 * ========================================
 * 从 ThreadLocal 中获取用户信息
 */
@Slf4j
@Component("threadLocalUserContextProvider")
class ThreadLocalUserContextProvider implements UserContextProvider {
    
    @Override
    public String getUserId(HttpServletRequest request) {
        UserContext context = UserContextHolder.getContext();
        return context != null ? context.getUserId() : null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        UserContext context = UserContextHolder.getContext();
        return context != null ? context.getUserName() : null;
    }
    
    @Override
    public String getTenantId(HttpServletRequest request) {
        UserContext context = UserContextHolder.getContext();
        return context != null ? context.getTenantId() : null;
    }
    
    // ThreadLocal 持有器
    static class UserContextHolder {
        private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();
        
        public static void setContext(UserContext context) {
            contextHolder.set(context);
        }
        
        public static UserContext getContext() {
            return contextHolder.get();
        }
        
        public static void clear() {
            contextHolder.remove();
        }
    }
    
    // 用户上下文对象
    @lombok.Data
    static class UserContext {
        private String userId;
        private String userName;
        private String tenantId;
    }
}

/**
 * ========================================
 * 示例4: 多租户 SaaS 用户上下文提供器
 * ========================================
 * 从子域名和请求头组合获取租户信息
 */
@Slf4j
@Component("multiTenantUserContextProvider")
class MultiTenantUserContextProvider implements UserContextProvider {
    
    @Override
    public String getUserId(HttpServletRequest request) {
        // 优先从请求头获取
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId;
        }
        
        // 从 Session 获取
        Object userIdObj = request.getSession(false) != null ? 
                request.getSession(false).getAttribute("userId") : null;
        return userIdObj != null ? userIdObj.toString() : null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        String userName = request.getHeader("X-User-Name");
        if (userName != null) {
            return userName;
        }
        
        Object userNameObj = request.getSession(false) != null ? 
                request.getSession(false).getAttribute("userName") : null;
        return userNameObj != null ? userNameObj.toString() : null;
    }
    
    @Override
    public String getTenantId(HttpServletRequest request) {
        // 方式1: 从子域名解析租户
        String host = request.getServerName();
        if (host != null && host.contains(".")) {
            String subdomain = host.substring(0, host.indexOf("."));
            // 例如: tenant1.example.com -> tenant1
            if (!subdomain.equals("www") && !subdomain.equals("api")) {
                return subdomain;
            }
        }
        
        // 方式2: 从请求头获取
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null) {
            return tenantId;
        }
        
        // 方式3: 从路径参数解析
        // 例如: /api/t1/users -> t1
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            String[] parts = path.split("/");
            if (parts.length > 2) {
                return parts[2]; // /api/{tenantId}/...
            }
        }
        
        return null;
    }
}

/**
 * ========================================
 * 示例5: Redis 缓存用户上下文提供器
 * ========================================
 * 从 Redis 中获取用户信息（适合 Token -> UserInfo 场景）
 */
@Slf4j
@Component("redisCacheUserContextProvider")
class RedisCacheUserContextProvider implements UserContextProvider {
    
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    
    @Override
    public String getUserId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            String userId = redisTemplate.opsForValue()
                    .get("token:userId:" + token);
            return userId;
        }
        return null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            String userName = redisTemplate.opsForValue()
                    .get("token:userName:" + token);
            return userName;
        }
        return null;
    }
    
    @Override
    public String getTenantId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            String tenantId = redisTemplate.opsForValue()
                    .get("token:tenantId:" + token);
            return tenantId;
        }
        return null;
    }
    
    private String extractToken(HttpServletRequest request) {
        // 从 Cookie 获取
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 从请求头获取
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        
        return null;
    }
}

/**
 * ========================================
 * 配置使用示例
 * ========================================
 */
/**
 * application.yml 配置:
 * 
 * logx:
 *   user-context:
 *     enabled: true
 *     # 选择使用哪个 Provider
 *     custom-provider-bean-name: jwtUserContextProvider
 *     # 或者: securityUserContextProvider
 *     # 或者: threadLocalUserContextProvider
 *     # 或者: multiTenantUserContextProvider
 *     # 或者: redisCacheUserContextProvider
 */

/**
 * ========================================
 * 拦截器配置示例 (配合 ThreadLocal 使用)
 * ========================================
 */
@Component
class UserContextInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            jakarta.servlet.http.HttpServletResponse response, 
                            Object handler) {
        // 在请求处理前，从 Token 解析用户信息并存入 ThreadLocal
        String token = request.getHeader("Authorization");
        if (token != null) {
            // 解析 Token 获取用户信息
            ThreadLocalUserContextProvider.UserContext context = 
                    new ThreadLocalUserContextProvider.UserContext();
            context.setUserId("user-001");
            context.setUserName("张三");
            context.setTenantId("tenant-001");
            
            ThreadLocalUserContextProvider.UserContextHolder.setContext(context);
        }
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               jakarta.servlet.http.HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        // 请求完成后清理 ThreadLocal
        ThreadLocalUserContextProvider.UserContextHolder.clear();
    }
}

/**
 * 注册拦截器
 */
@org.springframework.context.annotation.Configuration
class WebMvcConfiguration implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
    
    @org.springframework.beans.factory.annotation.Autowired
    private UserContextInterceptor userContextInterceptor;
    
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**");
    }
}
```