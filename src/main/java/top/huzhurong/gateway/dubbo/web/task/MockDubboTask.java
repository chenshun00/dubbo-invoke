package top.huzhurong.gateway.dubbo.web.task;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mock.Mock;
import com.mock.TypeReference;
import org.springframework.context.ApplicationContext;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.PrioritizedParameterNameDiscoverer;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 明确哪些数据需要暴露出去
 *
 * @author chenshun00@gmail.com
 * @since 2019/7/22
 */
public class MockDubboTask implements Runnable {

    private Logger logger = LoggerFactory.getLogger(MockDubboTask.class);
    private static String LOCAL_HOST = StringUtils.isBlank(System.getProperty("dubbo.protocol.host")) ? NetUtils.getLocalHost() : System.getProperty("dubbo.protocol.host");
    private static Boolean ENABLE_VOID = System.getProperty("dubbo.enable.void") != null;
    private PrioritizedParameterNameDiscoverer prioritizedParameterNameDiscoverer = new PrioritizedParameterNameDiscoverer();
    private String COLLECT_URL = StringUtils.isEmpty(System.getProperty("dubbo.collect.url")) ? "http://collect.superboss.cc/dubbo/collect" : System.getProperty("dubbo.collect.url");

    private ApplicationContext applicationContext;

    public MockDubboTask(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        prioritizedParameterNameDiscoverer.addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
    }

    @Override
    public void run() {
        if (this.applicationContext == null) {
            logger.warn("this.applicationContext is null");
            return;
        }
        String[] beanNamesForType = this.applicationContext.getBeanNamesForType(ServiceConfig.class);
        logger.info("start load dubbo services,dubbo services size is " + beanNamesForType.length);
        JSONArray array = new JSONArray();
        for (String beanName : beanNamesForType) {
            ServiceConfig serviceConfig = (ServiceConfig) this.applicationContext.getBean(beanName);
            String version = serviceConfig.getVersion();
            String anInterface = serviceConfig.getInterface();
            String group = serviceConfig.getGroup();
            logger.info(String.format("load dubbo services :%s,version :%s,group:%s", anInterface, version, group));

            Object ref = serviceConfig.getRef();
            Method[] declaredMethods = ref.getClass().getDeclaredMethods();
            JSONObject json = new JSONObject();
            for (Method declaredMethod : declaredMethods) {
                String methodName = declaredMethod.getName();
                if (Modifier.isStatic(declaredMethod.getModifiers())) {
                    logger.info("skip static method :" + methodName);
                    continue;
                }
                if (methodName.equalsIgnoreCase("afterPropertiesSet")) {
                    logger.info("skip afterPropertiesSet method");
                    continue;
                }
                if (!ENABLE_VOID && declaredMethod.getReturnType().isAssignableFrom(Void.TYPE)) {
                    logger.info("skip void method,method:" + methodName + ",please set -Ddubbo.enable.void=1 enable void method!");
                    continue;
                }

                if (Modifier.isPublic(declaredMethod.getModifiers())) {
                    String url = String.format("http://%s:%s/dubbo/%s/%s", LOCAL_HOST, getPort(), anInterface, methodName);
                    if (StringUtils.isNotEmpty(group)) {
                        url += "?group=" + group;
                    }
                    if (StringUtils.isNotEmpty(version)) {
                        if (StringUtils.isNotEmpty(group)) {
                            url += "&version=" + version;
                        } else {
                            url += "?version=" + version;
                        }
                    }
                    logger.info("load dubbo method from service, final url is " + url);
                    String[] parameterNames = prioritizedParameterNameDiscoverer.getParameterNames(declaredMethod);
                    Type[] genericParameterTypes = declaredMethod.getGenericParameterTypes();
                    JSONObject jsonObject = new JSONObject();
                    JSONArray paramArray = new JSONArray();
                    JSONObject curlParameter = new JSONObject();
                    for (int i = 0; i < genericParameterTypes.length; i++) {
                        ParamBean paramBean = new ParamBean();
                        Type genericParameterType = genericParameterTypes[i];
                        Object mock;
                        if (genericParameterType instanceof ParameterizedType) {
                            ParameterizedType parameterizedType = (ParameterizedType) genericParameterType;
                            TypeReference typeReference = new TypeReference();
                            typeReference.setType(parameterizedType);
                            mock = Mock.mock(typeReference);
                            paramBean.setType(parameterizedType.toString());
                        } else {
                            Class<?> clazz = (Class<?>) genericParameterType;
                            paramBean.setType(clazz.getName());
                            mock = Mock.mock(clazz);
                        }
                        paramBean.setName(parameterNames[i]);
                        paramBean.setMock(mock);
                        paramArray.add(paramBean);

                        curlParameter.put(parameterNames[i], paramBean.getMock());
                    }
                    jsonObject.put("parameter", paramArray);
                    // '{"var3": {"YchUVTemD": "cjZf","nKhfzMA": "IbT"}, "var4": {"Yn": "4xdl5Stml","lPio": "WuE4cO7ioI","FjjAaBF": "f9Rd","NIUWLm": "cIv9RQK64n","bN": "IJVqABJ"},"var1": ["zOA"],"var2": ["J1W4ybydg","rd8","6n"]}'
                    String curl = "curl -X POST -H 'Content-Type: application/json' '" + url + "' --data '" + curlParameter.toJSONString() + "'";
                    jsonObject.put("curl", curl);
                    jsonObject.put("url", url);
                    json.put(anInterface + "#" + methodName + "#" + version, jsonObject);
                } else {
                    logger.info("skip no public method:" + methodName);
                }
            }
            array.add(json);
            //开始上传到服务器
            try {
                Map<String, String> stringStringHashMap = new HashMap<>();
                stringStringHashMap.put("dubboInfo", array.toJSONString());
                String result = WebUtils.doPost(COLLECT_URL, stringStringHashMap, "UTF-8", 10000, 10000);
                logger.info("upload dubbo message to server success:" + result);
            } catch (IOException io) {
                logger.error("upload dubbo message to server:" + COLLECT_URL + ",error message:" + io.getMessage(), io);
            }
            array.clear();
        }
        System.out.println("load dubbo mock info:" + array.toJSONString());
    }

    private String getPort() {
        String DEFAULT_PORT = "8080";
        try {
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                    Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
            String port = objectNames.iterator().next().getKeyProperty("port");
            if (port == null || port.trim().length() == 0) {
                logger.info("project is SpringBoot , load server.port from System.properties()");
                port = System.getProperty("server.port");
            }
            if (port == null || port.trim().length() == 0) {
                logger.warn("load tomcat port fail,use default port:8080");
                return DEFAULT_PORT;
            }
            return port;
        } catch (Exception e) {
            return DEFAULT_PORT;
        }
    }
}
