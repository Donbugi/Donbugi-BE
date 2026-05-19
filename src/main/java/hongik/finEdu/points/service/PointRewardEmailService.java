package hongik.finEdu.points.service;

import hongik.finEdu.points.dto.RedeemLedgerResult;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointRewardEmailService {

    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.points.mail-from:noreply@example.com}")
    private String mailFrom;

    @Value("${app.points.qr-delivery-days-hint:3}")
    private int qrDeliveryDaysHint;

    /**
     * 교환 안내 메일. SMTP 미구성 시 false 반환 및 로그에 본문 출력.
     */
    public boolean sendRedemptionNotice(RedeemLedgerResult result) {
        String subject = "[finEdu] 포인트 혜택 교환이 완료되었습니다";
        String textBody = buildTextBody(result);
        String htmlBody = buildHtmlBody(result);

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("""
                    [포인트 교환 메일·SMTP 미설정] spring.mail.host 등을 설정하면 실제 발송됩니다.
                    to={} ref={}
                    ---
                    {}
                    ---
                    {}
                    """, result.email(), result.redemptionRef(), subject, textBody);
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(result.email());
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.error("포인트 교환 안내 메일 발송 실패 to={} ref={}", result.email(), result.redemptionRef(), e);
            return false;
        }
    }

    private String buildTextBody(RedeemLedgerResult r) {
        return """
                안녕하세요, finEdu입니다.

                포인트로 아래 혜택 교환이 완료되었습니다.

                · 교환 번호: %s
                · 혜택: %s (%s)
                · 사용 포인트: %,dP
                · 교환 후 잔여 포인트: %,dP

                실제 이용을 위한 QR코드(또는 쿠폰 코드)는 영업일 기준 약 %d일 이내에
                본 메일 주소(%s)로 별도 안내드릴 예정입니다.
                (데모·졸업작품 환경에서는 실제 쿠폰이 발송되지 않을 수 있습니다.)

                문의: 고객센터(예시)
                """.formatted(
                r.redemptionRef(),
                r.benefitCode().getBenefitName(),
                r.benefitCode().getDescription(),
                r.pointsSpent(),
                r.balanceAfter(),
                qrDeliveryDaysHint,
                r.email()
        );
    }

    private String buildHtmlBody(RedeemLedgerResult r) {
        return """
                <html><body style="font-family:sans-serif;line-height:1.6;">
                <p>안녕하세요, <strong>finEdu</strong>입니다.</p>
                <p>포인트로 아래 혜택 교환이 완료되었습니다.</p>
                <ul>
                  <li><b>교환 번호</b>: %s</li>
                  <li><b>혜택</b>: %s — %s</li>
                  <li><b>사용 포인트</b>: %,dP</li>
                  <li><b>교환 후 잔여</b>: %,dP</li>
                </ul>
                <p>실제 이용을 위한 <strong>QR코드</strong>(또는 쿠폰 코드)는 영업일 기준
                약 <strong>%d일 이내</strong>에 본 메일 주소(<code>%s</code>)로 별도 안내드릴 예정입니다.<br/>
                <small>(데모·졸업작품 환경에서는 실제 쿠폰이 발송되지 않을 수 있습니다.)</small></p>
                </body></html>
                """.formatted(
                escapeHtml(r.redemptionRef()),
                escapeHtml(r.benefitCode().getBenefitName()),
                escapeHtml(r.benefitCode().getDescription()),
                r.pointsSpent(),
                r.balanceAfter(),
                qrDeliveryDaysHint,
                escapeHtml(r.email())
        );
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
