package project.daihao18.panel.common.payment.stripe;

import cn.hutool.json.JSONUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.Source;
import com.stripe.param.SourceCreateParams;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import project.daihao18.panel.entity.CommonOrder;
import project.daihao18.panel.service.ConfigService;
import project.daihao18.panel.service.PackageService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
@Component
public class Stripe {

    private final static String RETURN_URI = "http://localhost:1024/result/success";

    @Autowired
    private ConfigService configService;

    @Autowired
    private PackageService packageService;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 通用下单
     *
     * @param order
     * @return
     */
    @Transactional
    public Map<String, Object> create(CommonOrder order) throws StripeException {
        // 获取stripe配置
        Map<String, Object> stripeConfig = JSONUtil.toBean(configService.getValueByName("stripeConfig"), Map.class);
        // 获取订单标题
        // String subject = "Order#";
        // 大写开头为混合支付
        if ("plan".equals(order.getType())) {
            // subject = subject.concat(order.getId());
            order.setId("p_".concat(order.getId()));
        } else {
            // subject = subject.concat(FlowSizeConverterUtil.BytesToGb(packageService.getById(order.getId()).getTransferEnable()) + " GB");
            order.setId("t_".concat(order.getId()));
        }

        // 查汇率 https://api.exchangerate.host/latest?symbols=JPY&base=CNY
        String api = "https://api.exchangerate.host/latest?symbols=" + stripeConfig.get("currency").toString().toUpperCase() + "&base=CNY";
        Map res = restTemplate.getForObject(api, Map.class);
        BigDecimal rate = BigDecimal.ZERO;
        try {
            Map<String, Object> rates = (Map<String, Object>) res.get("rates");
            rate = new BigDecimal(rates.get(stripeConfig.get("currency").toString().toUpperCase()).toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
        }
        com.stripe.Stripe.apiKey = stripeConfig.get("sk_live").toString();
        SourceCreateParams params = SourceCreateParams.builder()
                .setAmount(order.getPayAmount().multiply(rate).longValue())
                .setCurrency(stripeConfig.get("currency").toString())
                .setType("alipay")
                .setStatementDescriptor(order.getId())
                .putMetadata("out_trade_no", order.getId())
                .setRedirect(new SourceCreateParams.Redirect.Builder().setReturnUrl(RETURN_URI).build())
                .build();
        Source source = Source.create(params);
        String url = source.getRedirect().getUrl();
        Map<String, Object> map = new HashMap<>();
        map.put("url", url);
        map.put("type", "link");
        return map;
    }
}
