package com.chitfund.paymentservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * WHY RestTemplate and not WebClient?
 * payment-service is a servlet-stack (Tomcat) app. WebClient is from the reactive stack
 * and works in both, but RestTemplate is simpler here since we're not doing async I/O.
 * In a full reactive service: WebClient. In a servlet service with a few sync calls: RestTemplate.
 *
 * WHY not Feign client?
 * Feign (Spring Cloud OpenFeign) generates the HTTP client from an interface — less boilerplate.
 * We'd need spring-cloud-starter-openfeign dependency and @EnableFeignClients.
 * For a single downstream call, RestTemplate is simpler. Feign shines when you have many clients.
 *
 * INTERVIEW: "We call member-service synchronously here because payment processing
 * must fail fast if the member is inactive. An async check would let the payment proceed
 * before we know the result, which is wrong for a financial transaction."
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.member-service-url:http://localhost:8083}")
    private String memberServiceUrl;

    @Value("${app.internal-key:chitfund-internal-service-key}")
    private String internalKey;

    /**
     * Returns true if the member exists and is ACTIVE.
     * On any network error, fails open (returns true) to avoid blocking payments
     * when member-service is temporarily unavailable.
     *
     * WHY fail open here?
     * This is a best-effort guard. The authoritative source of truth is member-service.
     * In production, you'd add a circuit breaker (Resilience4j) to cache the last-known
     * state. Failing closed (reject all payments) when member-service is down would
     * be worse for the business than the small risk of one invalid payment slipping through.
     */
    @SuppressWarnings("unchecked")
    public boolean isMemberActive(UUID memberId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    memberServiceUrl + "/internal/members/" + memberId + "/validate",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            Map<String, Object> body = response.getBody();
            return body != null && Boolean.TRUE.equals(body.get("active"));

        } catch (RestClientException e) {
            log.warn("member-service unreachable for memberId={}, failing open: {}", memberId, e.getMessage());
            return true;
        }
    }
}
