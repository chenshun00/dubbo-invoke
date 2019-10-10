package top.huzhurong.gateway.dubbo.web;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author chenshun00@gmail.com
 * @since 2019/7/2
 */
public class GatewayController extends ServletWrappingController {
    public GatewayController() {
        this.setServletClass(GatewayServlet.class);
        this.setServletName("gatewayServlet");
        setSupportedMethods((String[]) null); // Allow all
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return super.handleRequestInternal(request, response);
    }
}
