package com.echcherqaoui.jobboard.userservice.util;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CollectionUtils {

    private CollectionUtils() {
    }

    /**
     * Synchronizes a managed JPA collection with an incoming list of requests (DTOs).
     * This method performs a differential update (Delta) to ensure the absolute minimum
     * number of SQL statements are executed.
     *
     * @param currentEntities   The managed collection currently attached to the JPA entity.
     * @param requests          The list of DTOs representing the desired target state.
     * @param entityIdSelector  Extracts the ID from an existing entity.
     * @param requestIdSelector Extracts the ID from a request DTO.
     * @param entityCreator     Logic to convert a new DTO into a new Entity instance.
     * @param entityUpdater     Logic to map data from a DTO onto an existing managed Entity.
     * @param <E>               The Entity type.
     * @param <R>               The Request/DTO type.
     * @param <I>               The ID type (usually UUID or Long).
     */
    public static <E, R, I> void synchronize(List<E> currentEntities,
                                             List<R> requests,
                                             Function<E, I> entityIdSelector,
                                             Function<R, I> requestIdSelector,
                                             Function<R, E> entityCreator,
                                             BiConsumer<R, E> entityUpdater) {

        if (requests == null) return;

        // Map existing entities by ID to avoids N*M nested loops and makes the sync O(N).
        Map<I, E> existingMap = currentEntities.stream()
              .filter(entity -> entityIdSelector.apply(entity) != null)
              .collect(Collectors.toMap(entityIdSelector, entity -> entity));

        // Reconcile the state by matching requests to existing entities.
        List<E> updatedState = requests.stream().map(req -> {
            I reqId = requestIdSelector.apply(req);

            if (reqId != null && existingMap.containsKey(reqId)) {
                // MATCH FOUND: Retrieve the managed instance and update its fields.
                // We REMOVE from the map to track that this entity is still "alive".
                E existing = existingMap.remove(reqId);
                entityUpdater.accept(req, existing);
                return existing;
            } else
                // NO MATCH: This is a new item.
                return entityCreator.apply(req);

        }).toList();

        // Update the persistent collection.
        // By clearing and adding all, we trigger Hibernate's orphanRemoval=true.
        // Any entity remaining in 'existingMap' is no longer in 'updatedState'
        // and will be deleted from the database.
        currentEntities.clear();
        currentEntities.addAll(updatedState);
    }
}