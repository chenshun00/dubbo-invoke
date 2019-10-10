package top.huzhurong.gateway.dubbo.web;

import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/2
 */
public class GatewayHandlerMapping extends AbstractUrlHandlerMapping {

    private GatewayController gatewayController;

    public GatewayHandlerMapping(GatewayController gatewayController) {
        this.gatewayController = gatewayController;
        setOrder(-200);
        registerHandlers();
    }

    private boolean isRegistry = false;

    @Override
    protected Object lookupHandler(String urlPath, HttpServletRequest request) throws Exception {

        if (!isRegistry) {
            registerHandlers();
            this.isRegistry = true;
        }
        return super.lookupHandler(urlPath, request);
    }

    private void registerHandlers() {
        registerHandler("/dubbo/*", this.gatewayController);
    }
}
