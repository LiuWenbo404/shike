package io.liuwenbo.common;

public class BaseContext {
    private static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setUserId(Long userId){
        threadLocal.set(userId);
    }

    public static Long getUserId(){
        return threadLocal.get();
    }

    public static void removeUserId(){
        threadLocal.remove();
    }
}
