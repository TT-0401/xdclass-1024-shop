package net.xdclass.exception;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Component
public class SentinelBlockHandler implements BlockExceptionHandler {

    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, BlockException e) throws Exception {

        JsonData jsonData = null;

        if (e instanceof FlowException){
            // 限流异常
            jsonData = JsonData.buildResult(BizCodeEnum.CONTROL_FLOW);

        }else if (e instanceof DegradeException){
            // 降级异常
            jsonData = JsonData.buildResult(BizCodeEnum.CONTROL_DEGRADE);

        }else if (e instanceof AuthorityException){
            // 授权异常
            jsonData = JsonData.buildResult(BizCodeEnum.CONTROL_AUTH);

        }

        // 设置异常响应状态码：200
        httpServletResponse.setStatus(200);

        // 响应JSON数据给前端
        CommonUtil.sendJsonMessage(httpServletResponse, jsonData);
    }
}
