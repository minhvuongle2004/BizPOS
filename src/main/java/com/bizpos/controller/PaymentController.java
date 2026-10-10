package com.bizpos.controller;

import com.bizpos.dto.PaymentConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Value("${bizpos.payment.bank.bank-id:VietinBank}")
    private String bankId;

    @Value("${bizpos.payment.bank.account-no:000000000000}")
    private String accountNo;

    @Value("${bizpos.payment.bank.account-name:BIZPOS STORE}")
    private String accountName;

    @Value("${bizpos.payment.bank.template:compact2}")
    private String template;

    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> getPaymentConfig() {
        PaymentConfigResponse config = PaymentConfigResponse.builder()
                .bankId(bankId)
                .accountNo(accountNo)
                .accountName(accountName)
                .template(template)
                .build();
        return ResponseEntity.ok(config);
    }
}
