package top.huzhurong.gateway.dubbo.web.handler;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/9
 */
public interface WriteHandler {

    /**
     * @param ret       正常返回值
     * @param throwable 可能出现的异常
     * @return 包装之后的返回值
     */
    Object write(Object ret, Throwable throwable);
}
