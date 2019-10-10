package top.huzhurong.gateway.dubbo.web.handler;

import com.alibaba.fastjson.JSONObject;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/9
 */
public class DefaultWriteHandler implements WriteHandler {
    @Override
    public Object write(Object ret, Throwable throwable) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", throwable == null ? 100 : 200);
        jsonObject.put("message", throwable == null ? "success" : throwable instanceof NullPointerException ? "空指针异常:" + throwable.getMessage() : throwable.getMessage());
        jsonObject.put("data", ret);
        return jsonObject;
    }
}
