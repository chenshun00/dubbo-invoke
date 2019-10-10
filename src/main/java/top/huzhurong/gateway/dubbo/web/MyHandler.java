package top.huzhurong.gateway.dubbo.web;

import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodProcessor;
import top.huzhurong.gateway.dubbo.web.handler.DefaultWriteHandler;
import top.huzhurong.gateway.dubbo.web.handler.WriteHandler;

import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;

/**
 * json的返回值写入
 *
 * @author chenshun00@gmail.com
 * @since 2019/7/2
 */
public class MyHandler extends AbstractMessageConverterMethodProcessor {

    private WriteHandler writeHandler;

    MyHandler(List<HttpMessageConverter<?>> converters, ContentNegotiationManager manager) {
        super(converters, manager);
        ServiceLoader<WriteHandler> writeHandlerServiceLoader = ServiceLoader.load(WriteHandler.class);
        for (WriteHandler aWriteHandlerServiceLoader : writeHandlerServiceLoader) {
            setWriteHandler(aWriteHandlerServiceLoader);
        }
        if (getWriteHandler() == null) {
            setWriteHandler(new DefaultWriteHandler());
        }
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return true;
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        mavContainer.setRequestHandled(true);
        ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
        ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
        this.writeWithMessageConverters(returnValue, returnType, inputMessage, outputMessage);
    }

    @Override
    protected <T> void writeWithMessageConverters(T value, MethodParameter returnType, ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage) throws IOException, HttpMessageNotWritableException {
        FastJsonHttpMessageConverter fastJsonHttpMessageConverter = new FastJsonHttpMessageConverter();
        Object write = getWriteHandler().write(value, null);
        logger.info("write message to client :" + write);
        fastJsonHttpMessageConverter.write(write, MediaType.APPLICATION_JSON, outputMessage);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return false;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return null;
    }

    private WriteHandler getWriteHandler() {
        return writeHandler;
    }

    private void setWriteHandler(WriteHandler writeHandler) {
        this.writeHandler = writeHandler;
    }
}
