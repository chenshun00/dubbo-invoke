### http 调用dubbo


pom 文件的mock依赖 https://github.com/chenshun00/mock , 修改了需要手动install到本地

```xml
    <dependency>
        <groupId>com.mock</groupId>
        <artifactId>mock</artifactId>
        <version>1.0.0</version>
        <exclusions>
            <exclusion>
                <groupId>com.alibaba</groupId>
                <artifactId>fastjson</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
```

mock代码来自于 https://github.com/dakuohao/mock ，修改了部分代码，感谢这么优秀的mock库 :+1:


### 使用

* tomcat在web.xml 新增servlet映射

```text
    <servlet>
        <servlet-name>GatewayServlet</servlet-name>
        <servlet-class>top.huzhurong.gateway.dubbo.web.GatewayServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>GatewayServlet</servlet-name>
        <url-pattern>/dubbo/*</url-pattern>
    </servlet-mapping>    
```

* SpringBoot可参考 `zuul` 新增 `@EnableXXXX`