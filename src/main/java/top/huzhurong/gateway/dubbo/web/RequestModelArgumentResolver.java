package top.huzhurong.gateway.dubbo.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/5
 */
public class RequestModelArgumentResolver implements HandlerMethodArgumentResolver {

    private final Log logger = LogFactory.getLog(getClass());

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return true;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        final Type type = parameter.getGenericParameterType();
        JSONObject requestInfo = getRequestInfo(webRequest);
        if (requestInfo == null) {
            logger.warn("parse http parameter fail , parameter:" + parameter.getParameterName() + " is empty");
            return null;
        }
        Object o = requestInfo.getObject(parameter.getParameterName(), type);
        if (logger.isInfoEnabled()) {
            logger.info("[parse http parameter][" + parameter.getParameterName() + ":" + o + "],json:" + requestInfo.toJSONString());
        }
        return o;
    }

    private JSONObject getRequestInfo(NativeWebRequest webRequest) throws IOException {
        JSONObject para;
        HttpServletRequest httpServletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        Assert.notNull(httpServletRequest, "ServletRequest为空");
        if (null != httpServletRequest.getAttribute("para")) {
            try {
                para = JSON.parseObject(httpServletRequest.getAttribute("para").toString());
            } catch (Exception e) {
                logger.error("[parse http error]", e);
                throw new RuntimeException("解析json失败!");
            }
        } else {
            StringBuilder buffer = new StringBuilder();
            BufferedReader reader = httpServletRequest.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            httpServletRequest.setAttribute("para", buffer.toString());

            try {
                para = JSON.parseObject(buffer.toString());
            } catch (Exception e) {
                throw new RuntimeException("解析json失败!");
            }
        }
        return para;
    }

}
