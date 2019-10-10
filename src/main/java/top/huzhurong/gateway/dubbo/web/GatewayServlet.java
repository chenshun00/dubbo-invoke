package top.huzhurong.gateway.dubbo.web;

import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.ContentNegotiationManagerFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory;
import top.huzhurong.gateway.dubbo.web.handler.DefaultWriteHandler;
import top.huzhurong.gateway.dubbo.web.handler.WriteHandler;
import top.huzhurong.gateway.dubbo.web.task.MockDubboTask;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author chenshun00@gmail.com
 * @since 2019/6/18
 */
public class GatewayServlet extends HttpServlet {
    private static final long serialVersionUID = -3374242278843351500L;

    private ApplicationContext applicationContext;
    private List<HttpMessageConverter<?>> messageConverters;

    private List<HttpMessageConverter<?>> getMessageConverters() {
        return messageConverters;
    }

    private ServletConfig config;

    private WriteHandler writeHandler;

    private WriteHandler getWriteHandler() {
        return writeHandler;
    }

    private void setWriteHandler(WriteHandler writeHandler) {
        this.writeHandler = writeHandler;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        System.out.println("=======初始化GatewayServlet=====");
        this.config = config;
        try {
            lazy();
        } catch (Throwable ex) {
            System.out.println("========dubbo服务提供失败,系统退出，失败原因:" + ex.getMessage());
            System.exit(-1);
        }
        ServiceLoader<WriteHandler> writeHandlerServiceLoader = ServiceLoader.load(WriteHandler.class);
        for (WriteHandler aWriteHandlerServiceLoader : writeHandlerServiceLoader) {
            setWriteHandler(aWriteHandlerServiceLoader);
        }
        if (getWriteHandler() == null) {
            setWriteHandler(new DefaultWriteHandler());
        }

        new Thread(new MockDubboTask(this.applicationContext), "mock-dubbo-message").start();
    }

    private String[] check(HttpServletRequest servletRequest) {
        String mm = servletRequest.getMethod();
        if (!mm.equalsIgnoreCase("POST")) {
            throw new IllegalArgumentException("仅支持POST方法");
        }
        String contentType = servletRequest.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("Content-Type请求头为空");
        }
        if (!contentType.contains("application/json")) {
            throw new IllegalArgumentException("请求头必须是application/json类型");
        }
        String requestURI = servletRequest.getRequestURI();
        Assert.notNull(requestURI, "请求路径不能为空");
        String[] infos = requestURI.split("/");
        Assert.isTrue(infos.length == 4, "请求路径错误/xx/interface/method");
        return infos;
    }

    @Override
    public void service(javax.servlet.ServletRequest servletRequest, ServletResponse servletResponse) {
        lazy();
        servletResponse.setContentType("application/json;charset=utf-8");
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;
        HandlerMethod handlerMethod;
        String cacheKey;
        ServletInvocableHandlerMethod invocableMethod;
        try {
            String[] infos = check(req);
            String inf = infos[2];
            Assert.notNull(inf, "接口不能为空!");
            String requestMethod = infos[3];
            Assert.notNull(requestMethod, "方法不能为空!");

            String uGroup = servletRequest.getParameter("group");
            String vVersion = servletRequest.getParameter("version");
            cacheKey = inf + "_" + requestMethod + "_" + vVersion + "_" + uGroup;
            invocableMethod = methodMap.get(cacheKey);
            if (invocableMethod == null) {
                String[] beanNamesForType = this.applicationContext.getBeanNamesForType(ServiceConfig.class);
                Object ref = null;
                for (String service : beanNamesForType) {
                    ServiceConfig serviceConfig = (ServiceConfig) this.applicationContext.getBean(service);
                    String version = serviceConfig.getVersion();
                    String anInterface = serviceConfig.getInterface();
                    String group = serviceConfig.getGroup();
                    if (!inf.equalsIgnoreCase(anInterface)) {
                        continue;
                    }
                    if (vVersion != null && !vVersion.equalsIgnoreCase(version)) {
                        continue;
                    }
                    if (uGroup != null && !group.equalsIgnoreCase(uGroup)) {
                        continue;
                    }
                    ref = serviceConfig.getRef();
                    break;
                }
                if (ref == null) {
                    throw new IllegalArgumentException("dubbo服务不存在!");
                }

                Method declaredMethods = null;
                for (Method method : ref.getClass().getDeclaredMethods()) {
                    if (method.getName().equalsIgnoreCase(requestMethod)) {
                        declaredMethods = method;
                        break;
                    }
                }
                Assert.notNull(declaredMethods, "方法不存在");
                handlerMethod = new HandlerMethod(ref, declaredMethods);
                WebDataBinderFactory binderFactory = getDataBinderFactory();
                invocableMethod = new ServletInvocableHandlerMethod(handlerMethod);
                invocableMethod.setDataBinderFactory(binderFactory);
                HandlerMethodArgumentResolverComposite handlerMethodArgumentResolverComposite = new HandlerMethodArgumentResolverComposite();
                handlerMethodArgumentResolverComposite.addResolvers(getDefaultArgumentResolvers());
                invocableMethod.setHandlerMethodArgumentResolvers(handlerMethodArgumentResolverComposite);
                List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
                invocableMethod.setHandlerMethodReturnValueHandlers(new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers));
                methodMap.put(cacheKey, invocableMethod);
            }
            invocableMethod.invokeAndHandle(new ServletWebRequest(req, resp), new ModelAndViewContainer());
        } catch (Exception e) {
            e.printStackTrace();
            fail(servletResponse, e);
        }
    }

    private void fail(ServletResponse servletResponse, Exception e) {
        try {
            if (writeHandler != null) {
                Object ret = writeHandler.write(null, e);
                if (ret instanceof JSONObject) {
                    servletResponse.setContentLength(((JSONObject) ret).toJSONString().getBytes().length);
                    servletResponse.getWriter().println(((JSONObject) ret).toJSONString());
                } else {
                    String value = JSONObject.toJSONString(ret);
                    servletResponse.setContentLength(value.getBytes().length);
                    servletResponse.getWriter().println(value);
                }
            } else {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("code", 200);
                jsonObject.put("message", e.getMessage());
                servletResponse.setContentLength(jsonObject.toJSONString().getBytes().length);
                servletResponse.getWriter().println(jsonObject.toJSONString());
            }
            servletResponse.getWriter().flush();
        } catch (Exception ignore) {

        }
    }

    private Map<String, ServletInvocableHandlerMethod> methodMap = new HashMap<>();

    private boolean lazy = false;

    private void lazy() {
        if (lazy) {
            return;
        }
        this.applicationContext = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());
        StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
        stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316
        this.messageConverters = new ArrayList<>(4);
        this.messageConverters.add(new ByteArrayHttpMessageConverter());
        this.messageConverters.add(stringHttpMessageConverter);
        try {
            this.messageConverters.add(new SourceHttpMessageConverter<>());
        } catch (Error err) {
            // Ignore when no TransformerFactory implementation is available
        }
        try {
            this.messageConverters.add(new SourceHttpMessageConverter<>());
        } catch (Error err) {
            // Ignore when no TransformerFactory implementation is available
        }
        try {
            this.messageConverters.add(new MappingJackson2HttpMessageConverter());
        } catch (Error err) {
            // Ignore when no TransformerFactory implementation is available
        }
        try {
            this.messageConverters.add(new FastJsonHttpMessageConverter());
        } catch (Error err) {
            // Ignore when no TransformerFactory implementation is available
        }
        this.beanFactory = (ConfigurableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        ContentNegotiationManagerFactoryBean bean = new ContentNegotiationManagerFactoryBean();
        bean.setFavorParameter(true);
        bean.setParameterName("format");
        bean.setIgnoreAcceptHeader(false);
        Properties properties = new Properties();
        properties.put("json", "application/json");
        properties.put("xml", "application/xml");
        properties.put("html", "text/html");
        bean.setMediaTypes(properties);
        try {
            bean.setDefaultContentType(MediaType.APPLICATION_JSON_UTF8);
        } catch (Throwable ignore) {
            bean.setDefaultContentType(MediaType.APPLICATION_JSON);
        }
        this.contentNegotiationManager = bean.getObject();
        init11();
        lazy = true;
    }

    private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();
        handlers.add(new MyHandler(getMessageConverters(), this.contentNegotiationManager));
        return handlers;
    }

    //解决参数解析的问题
    private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        // Annotation-based argument resolution
        resolvers.add(new RequestModelArgumentResolver());
        resolvers.add(new RequestParamMethodArgumentResolver(beanFactory, false));
        resolvers.add(new RequestParamMapMethodArgumentResolver());
        resolvers.add(new RequestHeaderMapMethodArgumentResolver());

        // Catch-all
        resolvers.add(new RequestParamMethodArgumentResolver(beanFactory, true));
        resolvers.add(new RequestModelArgumentResolver());
        return resolvers;
    }

    private ConfigurableBeanFactory beanFactory;

    private void init11() {
        List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(applicationContext);
        AnnotationAwareOrderComparator.sort(adviceBeans);
    }

    private ContentNegotiationManager contentNegotiationManager;

    private WebDataBinderFactory getDataBinderFactory() {
        return createDataBinderFactory(new ArrayList<InvocableHandlerMethod>());
    }

    private InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods) {
        return new ServletRequestDataBinderFactory(binderMethods, getConfigurableWebBindingInitializer());
    }

    private ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer() {
        ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
        initializer.setConversionService(mvcConversionService());
        initializer.setValidator(mvcValidator());
        return initializer;
    }

    private FormattingConversionService mvcConversionService() {
        return new DefaultFormattingConversionService();
    }

    private Validator mvcValidator() {
        Validator validator;
        if (ClassUtils.isPresent("javax.validation.Validator", getClass().getClassLoader())) {
            Class<?> clazz;
            try {
                String className = "org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean";
                clazz = ClassUtils.forName(className, WebMvcConfigurationSupport.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ex) {
                throw new BeanInitializationException("Failed to resolve default validator class", ex);
            }
            validator = (Validator) BeanUtils.instantiateClass(clazz);
        } else {
            validator = new NoOpValidator();
        }
        return validator;
    }

    private static final class NoOpValidator implements Validator {

        @Override
        public boolean supports(Class<?> clazz) {
            return false;
        }

        @Override
        public void validate(Object target, Errors errors) {
        }
    }
}
