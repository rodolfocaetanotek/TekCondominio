package br.com.tekcondo.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP do cliente. Com {@code server.forward-headers-strategy=framework}, o Spring já resolve X-Forwarded-For
 * vindo do proxy reverso confiável, então {@code getRemoteAddr()} retorna o IP original.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }

    public static String of(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
