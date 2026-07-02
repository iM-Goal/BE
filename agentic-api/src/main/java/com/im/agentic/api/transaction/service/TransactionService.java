package com.im.agentic.api.transaction.service;

import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Transaction saveTransaction(Long userId, String merchant, Long amount, String categoryName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        com.im.agentic.domain.mission.entity.SpendingCategory cat = null;
        if (categoryName != null) {
            try {
                cat = com.im.agentic.domain.mission.entity.SpendingCategory.valueOf(categoryName.toUpperCase());
            } catch (Exception e) {
                // Ignore
            }
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .merchant(merchant)
                .amount(amount)
                .category(cat)
                .transactedAt(java.time.LocalDateTime.now())
                .build();

        return transactionRepository.save(transaction);
    }

    public List<Transaction> getHistory(Long userId) {
        return transactionRepository.findByUserIdOrderByTransactedAtDesc(userId);
    }
}
