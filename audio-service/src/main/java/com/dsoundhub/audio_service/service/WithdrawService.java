package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.entity.Withdrawal;
import com.dsoundhub.audio_service.entity.WithdrawalStatus;
import com.dsoundhub.audio_service.repository.WithdrawalRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WithdrawService {

    private static final BigDecimal MIN_WITHDRAWAL = BigDecimal.valueOf(20000);

    private final WithdrawalRepository withdrawalRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public WithdrawService(WithdrawalRepository withdrawalRepository,
                            DataSource dataSource,
                            EmailService emailService) {
        this.withdrawalRepository = withdrawalRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.emailService = emailService;
    }

    @Transactional
    public Withdrawal requestWithdraw(UUID userId, BigDecimal amount) {
        if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new RuntimeException(
                "Minimal penarikan adalah " + MIN_WITHDRAWAL.intValue() + " pts"
            );
        }

        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT COALESCE(balance, 0) AS balance, email, username FROM users WHERE id = ?::uuid",
            userId.toString()
        );

        BigDecimal balance = (BigDecimal) user.get("balance");
        if (balance == null || balance.compareTo(amount) < 0) {
            throw new RuntimeException("Saldo tidak mencukupi untuk melakukan penarikan");
        }

        jdbcTemplate.update(
            "UPDATE users SET balance = COALESCE(balance, 0) - ? WHERE id = ?::uuid",
            amount, userId.toString()
        );

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUserId(userId);
        withdrawal.setAmount(amount);
        withdrawal.setStatus(WithdrawalStatus.COMPLETED);
        withdrawal = withdrawalRepository.save(withdrawal);

        String email = (String) user.get("email");
        String username = (String) user.get("username");
        emailService.sendWithdrawalNotification(email, username, amount);

        return withdrawal;
    }

    public List<Withdrawal> getHistory(UUID userId) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public BigDecimal getBalance(UUID userId) {
        BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM users WHERE id = ?::uuid",
            BigDecimal.class,
            userId.toString()
        );
        return balance != null ? balance : BigDecimal.ZERO;
    }
}
