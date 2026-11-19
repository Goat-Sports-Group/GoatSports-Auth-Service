package iuh.fit.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kháng nghị mở khóa tài khoản mô phỏng (dev / demo) cho trang Kháng nghị của admin: ba người chơi mô phỏng ít hoạt
 * động bị khóa — một kháng nghị mới, một đã đọc, một đã được giải quyết (mở khóa lại, có phản hồi). Hai tài khoản còn
 * khóa đăng nhập bằng mật khẩu demo sẽ thấy hộp thông báo bị khóa kèm form kháng nghị.
 *
 * <p>Chạy một lần (bỏ qua nếu kháng nghị đã có): admin mở khóa trong lúc demo thì lần khởi động sau không khóa lại.
 * Bật bằng {@code app.demo-data.simulation.enabled=true}.
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data.simulation", name = "enabled", havingValue = "true")
public class DemoAppealSeeder implements CommandLineRunner {
    private static final String MODERATOR = "kiemduyet@goatsports.vn";

    private final JdbcTemplate jdbc;

    record Appeal(int playerIndex, String status, int daysAgo, String subject, String message, String reply) { }

    static final List<Appeal> APPEALS = List.of(
            new Appeal(291, "NEW", 1, "Xin mở khóa tài khoản",
                    "Mình bị khóa sau khi bị báo cáo hủy sân nhiều lần. Tuần đó mình bị ốm nên phải hủy, mong admin xem xét "
                            + "mở khóa giúp. Mình hứa sẽ hủy sớm hơn nếu có việc đột xuất.", null),
            new Appeal(293, "READ", 3, "Tài khoản bị khóa nhầm",
                    "Tài khoản của mình bị khóa vì nghi ngờ đăng bài quảng cáo, nhưng bài đó là mình chia sẻ giải đấu của "
                            + "CLB. Nhờ admin kiểm tra lại giúp mình.", null),
            new Appeal(297, "RESOLVED", 9, "Đề nghị mở khóa",
                    "Mình đã đọc lại quy định cộng đồng và xin lỗi vì bình luận thiếu tôn trọng. Mong được mở khóa.",
                    "Chào bạn, Ban kiểm duyệt đã mở khóa tài khoản. Mong bạn giữ thái độ tôn trọng khi bình luận nhé."));

    @Override
    @Transactional
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.now();
        List<DemoPopulation.Player> players = DemoPopulation.players();
        int inserted = 0;
        for (Appeal appeal : APPEALS) {
            if (appeal.playerIndex() >= players.size()) continue;
            DemoPopulation.Player player = players.get(appeal.playerIndex());
            UUID appealId = UUID.nameUUIDFromBytes(("goatsports:account-appeal:" + player.email())
                    .getBytes(StandardCharsets.UTF_8));
            Integer existing = jdbc.queryForObject("select count(*) from account_appeals where appeal_id = ?",
                    Integer.class, appealId);
            if (existing != null && existing > 0) continue;
            LocalDateTime createdAt = now.minusDays(appeal.daysAgo()).withHour(21).withMinute(10).withSecond(0).withNano(0);
            boolean resolved = "RESOLVED".equals(appeal.status());
            Timestamp readAt = "NEW".equals(appeal.status()) ? null : Timestamp.valueOf(createdAt.plusHours(14));
            Timestamp resolvedAt = resolved ? Timestamp.valueOf(createdAt.plusDays(1)) : null;
            inserted += jdbc.update("""
                    insert into account_appeals (appeal_id, user_id, email, full_name, subject, message, status, created_at,
                                                 read_at, resolved_at, reply, replied_at, replied_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, appealId, player.userId(), player.email(), player.fullName(), appeal.subject(), appeal.message(),
                    appeal.status(), Timestamp.valueOf(createdAt), readAt, resolvedAt, appeal.reply(), resolvedAt,
                    resolved ? MODERATOR : null);
            if (!resolved) {
                jdbc.update("update users set status = 'BLOCKED', updated_at = now(), updated_by = 'demo-sim' "
                        + "where user_id = ? and status = 'ACTIVE'", player.userId());
            }
        }
        log.info("Kháng nghị tài khoản mô phỏng: {} kháng nghị mới", inserted);
    }
}
