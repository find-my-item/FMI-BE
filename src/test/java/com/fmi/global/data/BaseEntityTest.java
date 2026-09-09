package com.fmi.global.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BaseEntity")
class BaseEntityTest {

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("기본 상태는 ACTIVE이고 삭제 시각은 없다")
        void initializesActiveState() {
            TestEntity entity = new TestEntity();

            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.ACTIVE);
            assertThat(entity.isActive()).isTrue();
            assertThat(entity.isDeleted()).isFalse();
            assertThat(entity.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("ACTIVE 상태를 DELETED로 바꾸고 전달받은 삭제 시각을 기록한다")
        void deletesActiveEntity() {
            TestEntity entity = new TestEntity();
            LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 9, 12, 30);

            boolean changed = entity.delete(deletedAt);

            assertThat(changed).isTrue();
            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.DELETED);
            assertThat(entity.isActive()).isFalse();
            assertThat(entity.isDeleted()).isTrue();
            assertThat(entity.getDeletedAt()).isEqualTo(deletedAt);
        }

        @Test
        @DisplayName("이미 DELETED 상태이면 false를 반환하고 기존 삭제 시각을 유지한다")
        void ignoresDuplicateDeletion() {
            TestEntity entity = new TestEntity();
            LocalDateTime firstDeletedAt = LocalDateTime.of(2026, 9, 9, 12, 30);
            entity.delete(firstDeletedAt);

            boolean changed = entity.delete(firstDeletedAt.plusHours(1));

            assertThat(changed).isFalse();
            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.DELETED);
            assertThat(entity.getDeletedAt()).isEqualTo(firstDeletedAt);
        }

        @Test
        @DisplayName("삭제 시각이 없으면 false를 반환하고 ACTIVE 상태를 유지한다")
        void ignoresNullDeletionTime() {
            TestEntity entity = new TestEntity();

            boolean changed = entity.delete(null);

            assertThat(changed).isFalse();
            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.ACTIVE);
            assertThat(entity.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("복구")
    class Activate {

        @Test
        @DisplayName("DELETED 상태를 ACTIVE로 바꾸고 삭제 시각을 제거한다")
        void activatesDeletedEntity() {
            TestEntity entity = new TestEntity();
            entity.delete(LocalDateTime.of(2026, 9, 9, 12, 30));

            boolean changed = entity.active();

            assertThat(changed).isTrue();
            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.ACTIVE);
            assertThat(entity.isActive()).isTrue();
            assertThat(entity.isDeleted()).isFalse();
            assertThat(entity.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("이미 ACTIVE 상태이면 false를 반환하고 상태를 유지한다")
        void ignoresActivationOfActiveEntity() {
            TestEntity entity = new TestEntity();

            boolean changed = entity.active();

            assertThat(changed).isFalse();
            assertThat(entity.getEntityStatus()).isEqualTo(EntityStatus.ACTIVE);
            assertThat(entity.getDeletedAt()).isNull();
        }
    }

    private static final class TestEntity extends BaseEntity {}
}
