package com.echcherqaoui.jobboard.userservice.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionUtilsTest {

    static class TestEntity {
        UUID id;
        String value;
        TestEntity(UUID id, String value) { this.id = id; this.value = value; }
    }

    record TestRequest(UUID id, String value) {}

    @Test
    void synchronize_doesNothing_whenRequestsNull() {
        TestEntity existing = new TestEntity(UUID.randomUUID(), "old");
        List<TestEntity> current = new ArrayList<>(List.of(existing));

        CollectionUtils.synchronize(
              current, null,
              e -> e.id, r -> ((TestRequest) r).id(),
              r -> new TestEntity(null, ((TestRequest) r).value()),
              (r, e) -> e.value = ((TestRequest) r).value()
        );

        assertThat(current).containsExactly(existing);
    }

    @Test
    void synchronize_addsNewEntity_whenRequestHasNullId() {
        List<TestEntity> current = new ArrayList<>();
        TestRequest newRequest = new TestRequest(null, "new-value");

        CollectionUtils.synchronize(
              current, List.of(newRequest),
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).hasSize(1);
        assertThat(current.get(0).value).isEqualTo("new-value");
    }

    @Test
    void synchronize_updatesExistingEntity_whenRequestIdMatches() {
        UUID id = UUID.randomUUID();
        TestEntity existing = new TestEntity(id, "old-value");
        List<TestEntity> current = new ArrayList<>(List.of(existing));
        TestRequest updateRequest = new TestRequest(id, "updated-value");

        CollectionUtils.synchronize(
              current, List.of(updateRequest),
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).hasSize(1);
        assertThat(current.get(0)).isSameAs(existing);
        assertThat(current.get(0).value).isEqualTo("updated-value");
    }

    @Test
    void synchronize_removesEntity_notPresentInRequests() {
        UUID keepId = UUID.randomUUID();
        UUID removeId = UUID.randomUUID();
        TestEntity toKeep = new TestEntity(keepId, "keep");
        TestEntity toRemove = new TestEntity(removeId, "remove");
        List<TestEntity> current = new ArrayList<>(List.of(toKeep, toRemove));

        CollectionUtils.synchronize(
              current, List.of(new TestRequest(keepId, "keep")),
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).hasSize(1);
        assertThat(current.get(0)).isSameAs(toKeep);
    }

    @Test
    void synchronize_clearsAllEntities_whenRequestsEmpty() {
        TestEntity existing = new TestEntity(UUID.randomUUID(), "value");
        List<TestEntity> current = new ArrayList<>(List.of(existing));

        CollectionUtils.synchronize(
              current, List.of(),
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).isEmpty();
    }

    @Test
    void synchronize_ignoresEntitiesWithNullId_treatingThemAsInvisibleToMatching() {
        TestEntity noIdEntity = new TestEntity(null, "orphaned");
        List<TestEntity> current = new ArrayList<>(List.of(noIdEntity));

        CollectionUtils.synchronize(
              current, List.of(new TestRequest(null, "unrelated-new")),
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).hasSize(1);
        assertThat(current.get(0).value).isEqualTo("unrelated-new");
        assertThat(current).doesNotContain(noIdEntity);
    }

    @Test
    void synchronize_handlesMixOfAddUpdateRemove_inSingleCall() {
        UUID updateId = UUID.randomUUID();
        UUID removeId = UUID.randomUUID();
        TestEntity toUpdate = new TestEntity(updateId, "old");
        TestEntity toRemove = new TestEntity(removeId, "gone");
        List<TestEntity> current = new ArrayList<>(List.of(toUpdate, toRemove));

        List<TestRequest> requests = List.of(
              new TestRequest(updateId, "updated"),
              new TestRequest(null, "brand-new")
        );

        CollectionUtils.synchronize(
              current, requests,
              e -> e.id, TestRequest::id,
              r -> new TestEntity(null, r.value()),
              (r, e) -> e.value = r.value()
        );

        assertThat(current).hasSize(2);
        assertThat(current).extracting(e -> e.value).containsExactlyInAnyOrder("updated", "brand-new");
        assertThat(current).contains(toUpdate);
        assertThat(current).doesNotContain(toRemove);
    }
}