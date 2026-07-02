package com.im.agentic.api.transaction;

import com.im.agentic.api.support.IntegrationTestBase;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = User.builder()
                .email("tx@im.com")
                .password("test1234!")
                .nickname("거래테스터")
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("소비 내역 저장 -> 오늘 소비 합계 조회")
    void saveTransaction_sumsTodaySpend() {
        // given
        Transaction tx1 = Transaction.builder()
                .user(testUser)
                .merchant("카페A")
                .amount(4500L)
                .category(SpendingCategory.CAFE)
                .transactedAt(LocalDateTime.now())
                .build();

        Transaction tx2 = Transaction.builder()
                .user(testUser)
                .merchant("식당B")
                .amount(12000L)
                .category(SpendingCategory.FOOD)
                .transactedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx1);
        transactionRepository.save(tx2);

        // when
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todaySpend = transactionRepository.sumTodaySpend(testUser.getId(), startOfDay);

        // then
        assertThat(todaySpend).isEqualTo(16500L);
    }

    @Test
    @DisplayName("카테고리별 소비 조회 테스트")
    void getCategorySpend_returnsSum() {
        // given
        Transaction tx1 = Transaction.builder()
                .user(testUser)
                .merchant("스타벅스")
                .amount(5500L)
                .category(SpendingCategory.CAFE)
                .transactedAt(LocalDateTime.now())
                .build();

        Transaction tx2 = Transaction.builder()
                .user(testUser)
                .merchant("메가커피")
                .amount(2000L)
                .category(SpendingCategory.CAFE)
                .transactedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx1);
        transactionRepository.save(tx2);

        // when
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long cafeSpend = transactionRepository.sumAmountByUserIdAndCategoryAfter(testUser.getId(), SpendingCategory.CAFE, since);

        // then
        assertThat(cafeSpend).isEqualTo(7500L);
    }
}
