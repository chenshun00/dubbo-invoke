package top.huzhurong.gateway.dubbo.web.task;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/22
 */
public class ParamBean {
    //参数类型
    private String type;
    //参数名字
    private String name;
    //mock出来的数据
    private Object mock;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getMock() {
        return mock;
    }

    public void setMock(Object mock) {
        this.mock = mock;
    }
}
