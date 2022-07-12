package net.xdclass.exception;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.util.JsonData;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
@Slf4j // SLF4J（Simple Logging Facade for Java）
public class CustomExceptionHandler {

    // 处理哪一类的异常
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public JsonData handle(Exception e){

        // 是不是自定义异常
        if(e instanceof BizException){

            BizException bizException = (BizException) e;
            log.error("[业务异常 {}]",e);

            // 此处要返回业务状态码和描述信息，故上面用 @ResponseBody
            return JsonData.buildCodeAndMsg(bizException.getCode(),bizException.getMsg());

        }else{
            log.error("[系统异常 {}]",e);
            return JsonData.buildError("全局异常，未知错误");
        }

    }

}
