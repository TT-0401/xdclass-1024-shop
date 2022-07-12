package net.xdclass.controller;

import com.google.code.kaptcha.Producer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.SendCodeEnum;
import net.xdclass.service.NotifyService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Api(tags = "通知模块")
@RestController
@RequestMapping("/api/user/v1")
@Slf4j
public class NotifyController {

    @Autowired
    private Producer captchaProducer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NotifyService notifyService;

    /**
     * 图形验证码有效期：10分钟
     */
    private static final long CAPTCHA_CODE_EXPIRED = 60 * 1000 * 10;


    /**
     * 用户获取图形验证码
     * @param request
     * @param response
     */
    @ApiOperation("获取图形验证码")
    @GetMapping("captcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response){

        // 生成验证码对应的文本
        String captchaText = captchaProducer.createText();
        log.info("图形验证码:{}", captchaText);

        // 存储验证码对应的文本至缓存中
        // key：根据用户信息加密生成 value：验证码对应的文本
        // key过期时间 时间单位
        redisTemplate.opsForValue().set(getCaptchaKey(request),captchaText,CAPTCHA_CODE_EXPIRED,TimeUnit.MILLISECONDS);

        // 根据验证码文本生成验证码图片
        BufferedImage bufferedImage = captchaProducer.createImage(captchaText);
        // 显示图形验证码
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            ImageIO.write(bufferedImage,"jpg",outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            log.error("获取图形验证码异常:{}",e);
        }
    }


    /**
     * 发送邮箱注册验证码
     *  1、先匹配图形验证码是否正常
     *  2、再发送验证码
     *
     * @param to
     * @param captcha
     * @return
     */
    @ApiOperation("发送邮箱注册验证码")
    @GetMapping("send_code")
    public JsonData sendRegisterCode(@RequestParam(value = "to",required = true) String to,
                                     @RequestParam(value = "captcha",required = true) String captcha,
                                     HttpServletRequest request){
        // 根据请求中的用户信息获取其对应的图形验证码缓存的key
        String key = getCaptchaKey(request);
        // 根据缓存的key获取对应的图形验证码
        String cacheCaptcha = redisTemplate.opsForValue().get(key);

        // 匹配请求参数传过来的图形验证码与缓存的图形验证码是否一样
        if(captcha != null && captcha.equalsIgnoreCase(cacheCaptcha)){
            // 成功
            // 先让该图形验证码失效
            redisTemplate.delete(key);
            // 再发送邮箱验证码至注册用户邮箱（通过请求参数获取收件人 to ）
            JsonData jsonData = notifyService.sendCode(SendCodeEnum.USER_REGISTER, to);
            // 返回发送成功与否的提示信息
            return jsonData;
        }else{
            // 失败，则返回验证码错误
            return JsonData.buildResult(BizCodeEnum.CODE_CAPTCHA_ERROR);
        }
    }


    /**
     * 生成用于缓存图形验证码的唯一key：先对 用户IP + 请求头中的userAgent 进行 MD5 加密，再加上前缀，进而得到 key
     *  key规范：业务划分、冒号隔离
     *      如：user-service:captcha:xxxx
     *      但长度不能过长
     *
     * @param request
     * @return
     */
    private String getCaptchaKey(HttpServletRequest request){

        String ip = CommonUtil.getIpAddr(request);
        String userAgent = request.getHeader("User-Agent");

        String key = "user-service:captcha:" + CommonUtil.MD5(ip+userAgent);

        log.info("ip={}",ip);
        log.info("userAgent={}",userAgent);
        log.info("key={}",key);

        return key;
    }
}
