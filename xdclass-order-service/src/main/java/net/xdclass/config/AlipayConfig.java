package net.xdclass.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;


public class AlipayConfig {

    /**
     * 支付宝网关地址  TODO
     */
    public static final String PAY_GATEWAY = "https://openapi.alipaydev.com/gateway.do";


    /**
     * 支付宝 APPID TODO
     */
    public static final String APPID = "2021000120613201";


    /**
     * 应用私钥 TODO
     */
    public static final String APP_PRI_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDayARIqBwJ/5hIB6qEXN5iADdoVcKD5DjisDr1Eo608Jh2/n1c1biW01168G/7rl6Q2jSDa0X+JaSMrImwQogsujQzAjup7K9Q9tKsnnweh8UphHOpWWlthygO20oyhenwafx4oUK7yFLlzh9LmjXAHosSFbhqvg9T+vACCsijIl5ixw9RuOSpaq/7vFTCtzeiKRPyay1P/Ba5wm6cM8y2Tsm91dSZieKhIkt2/s9DCeND/VacEpO0JzYYKjig7xG6vn0kpHJE8vQ1q14cTChskBvboP1eiXCd/GRlsGNT4axg5D3TvbaLKv9ZTl8lE+4u28aQOWYhh8mukUPvsN5pAgMBAAECggEAL06OtpM7NOJCsFiQA3z9TR2U3YelvtUrg8Dtjq6Lkw5vOVPAEcHY3ywnC31QCZDju9ijAEPC57iGAzEPuMA6J8m/ncP+2LhoFE66sT63wfZDqL2OMPE3fcp62/OI8LHKKwUP5ZmhD2+6lRxj4fofY0J3edqefN5J/DPHj/l69uMCQEleVnIT9z50pwBhIZ5x+vd5WN1t6+L3JM4jjcZDkmdh+OJRp5AMVuMTFwaxivMpRluAzfyFoVSwA6Mwu9X/XpZ0L7gUYNbeUrCAtikP/xtYpp9AM5dX1MVN+M02id2nOlGPOQqPK64mfk/i3Gjs2PWmVGhRegnsXSm4Ky2ALQKBgQDwBqqV2OQPV8zR1glUwxOiyOVf6ohHsgYe7cHxS3nDGSh5xCdo29oUmvXfrDngZVzrOdLxiLRX1f8JnDhuDz46ZGr3vv572dqQRyy7AtJcGMt5ucBJJU0gK/NBg5A0qpcd2uoQkdVGVaQpDBndRPLqj71cCcRHPl8t3IX6Div3gwKBgQDpV2cyyWmAVb72/xyWf6faHupGiEdcUR00aMiSWDoc0rIAs3UJV7ufAH5dVpQlqegS4AF5evcgGr3M6gLfxupSCuhmO1Ep1T26WETNuJjQIsmbCIjENsl3jeO6aT/A/KPVI+NHUIIrDTxeqpg5LSTpW2bn7o4EMuP9g8jkvIvCowKBgCvW691GqhDrVmuVDks+/h0jWFG2goGl0FxhzKSHxouN0si6vP/399L/opzh1ghYOypze0XGVDoeyeA1elEP06vSn370kYKJfdQQS+k0ULulg7SN6sLxXy7bGCkFIJed+M/pJgs0t6GLDtq4bRju+PdCnbFrzz+78qJrvsS3ReyFAoGAXv1EtokFmMfPOI973MxU0VeaeWIK4srmf6pUVDb0WT1wVxa3Azmi2ESELI0NPBPhtKVaFFD5fylDsMJmryAzzXhBSTTGgqgbO9upDrIiC0DOH32m53GCLFgnp4AnAvIV47N7V1Gv28TEmakK1OPKzfB2gN49E/p/k3ltc6hJoM8CgYEAkW1num3577M4OQQI0gfLcA7RtsihVkTGAOsl4AOax4MxSy74YMQ7Kl0lxtSnYT04VnT5Lyn3IIGc5ArCHXeAEr6w1Iwfoe1qi71VdPh9VviFN3Ls4/HgXvqP33DcdFIuhJET3IY3GbQZFEYzYBLh+28xA7Q2Tg8jmLWbYdk50lE=";


    /**
     * 支付宝公钥 TODO
     */
    public static final String ALIPAY_PUB_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlC3MOBe6tLz8gnquy4t7n4h4ksZCTWMTTrU6Oiw2wesjVlLSKGlNHbLg8UMNshvHzmSQjoBzRZzkSKP7CWXzMpwK/Urfaro2RGT0469jubqTathba+j47QAISBa9zU6SGYPOBRFvqtbe0Yefj9rZ3DvMCKjzZmdUiCjwD9UmEotAyFhqmyZPzkvACwCFLg4azVnqWpC5EupBT6ijOYb+enCWXbOoBoL71IEpw+Cmk1b8QMBcoPTQ9B+ujRlmPPnJDu9jpe1kkAIFeTsog643IFEAV+XjVrrGwDu/ZtBtuuoj9SDhcQk2sZVQzThV+f2gXky6MREN1raNW9IajfE2gQIDAQAB";


    /**
     * 签名类型
     */
    public static final String SIGN_TYPE = "RSA2";


    /**
     * 字符编码
     */
    public static final String CHARSET = "UTF-8";


    /**
     * 返回参数格式
     */
    public static final String FORMAT = "json";


    /**
     * 构造函数私有化
     */
    private AlipayConfig(){}



    private volatile static AlipayClient instance = null;

    /**
     * 单例模式获取，双重锁校验，防止指令重排
     */
    public static AlipayClient getInstance(){

        if (instance == null){
            synchronized (AlipayConfig.class){
                if (instance == null){
                    // 此处参数顺序不可变！！！
                    instance = new DefaultAlipayClient(PAY_GATEWAY,APPID,APP_PRI_KEY,FORMAT,CHARSET,ALIPAY_PUB_KEY,SIGN_TYPE);
                }
            }
        }

        return instance;
    }


}
