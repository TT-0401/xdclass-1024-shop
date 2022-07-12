package net.xdclass.service.impl;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.constant.CacheKey;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.SendCodeEnum;
import net.xdclass.component.MailService;
import net.xdclass.service.NotifyService;
import net.xdclass.util.CheckUtil;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NotifyServiceImpl implements NotifyService {

    @Autowired
    private MailService mailService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 验证码邮件的主题
     */
    private static final String SUBJECT = "小滴课堂验证码";

    /**
     * 验证码邮件的内容
     */
    private static final String CONTENT = "您的验证码是%s，有效时间为10分钟，打死也不要告诉任何人";

    /**
     * 验证码10分钟有效
     */
    private static final int CODE_EXPIRED = 60 * 1000 * 10;

    /**
     * 前置：判断是否重复发送
     *
     * 1、存储邮箱验证码到缓存
     *
     * 2、发送邮箱验证码
     *
     * 后置：存储发送记录
     *
     * @param sendCodeEnum
     * @param to
     * @return
     */
    @Override
    public JsonData sendCode(SendCodeEnum sendCodeEnum, String to) {

        // 生成邮箱验证码对应的缓存key
        String cacheKey = String.format(CacheKey.CHECK_CODE_KEY, sendCodeEnum.name(), to);

        // 获取对应value：随机验证码_时间戳
        String cacheValue = redisTemplate.opsForValue().get(cacheKey);

        // 如果value不为空，则说明之前发过验证码，判断是否60秒内重复发送
        if(StringUtils.isNotBlank(cacheValue)){
            // 获取之前发送的时间戳 Time To Live
            long ttl = Long.parseLong(cacheValue.split("_")[1]);
            // 当前时间戳-验证码发送时间戳，如果小于60秒，则不给重复发送
            if(CommonUtil.getCurrentTimestamp() - ttl < 1000*60){
                log.info("重复发送验证码，时间间隔:{}秒", (CommonUtil.getCurrentTimestamp()-ttl)/1000);
                // 返回“验证码发送过快”的提示信息
                return JsonData.buildResult(BizCodeEnum.CODE_LIMITED);
            }
        }

        // 如果value为空，则生成验证码：随机验证码_时间戳
        String code = CommonUtil.getRandomCode(6);
        String value = code + "_" + CommonUtil.getCurrentTimestamp();

        // 缓存用户的邮箱验证码
        redisTemplate.opsForValue().set(cacheKey, value, CODE_EXPIRED, TimeUnit.MILLISECONDS);

        // 通过正则，验证邮箱格式或手机号是否合法
        if(CheckUtil.isEmail(to)){
            // 若合法，则发送邮箱验证码
            mailService.sendMail(to,SUBJECT,String.format(CONTENT,code));
            // 发送成功
            return JsonData.buildSuccess();
        } else if(CheckUtil.isPhone(to)){
            //短信验证码

        }

        // 若不合法，则返回“接收号码不合规”错误代码
        return JsonData.buildResult(BizCodeEnum.CODE_TO_ERROR);
    }

    /**
     * 校验验证码
     * @param sendCodeEnum
     * @param to
     * @param code
     * @return
     */
    @Override
    public boolean checkCode(SendCodeEnum sendCodeEnum, String to, String code) {
        // 拼接缓存的key
        String cacheKey = String.format(CacheKey.CHECK_CODE_KEY,sendCodeEnum.name(),to);
        // 获取缓存的 随机验证码_时间戳
        String cacheValue = redisTemplate.opsForValue().get(cacheKey);

        if(StringUtils.isNotBlank(cacheValue)){
            // 获取缓存的验证码
            String cacheCode = cacheValue.split("_")[0];
            // 判断用户提交的验证码是否与缓存的验证码一致
            if(cacheCode.equals(code)){
                // 删除验证码
                redisTemplate.delete(cacheKey);
                // 校验成功
                return true;
            }

        }
        return false;
    }
}
