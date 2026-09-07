package com.switchpay.settlement;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** NR-14: the reads §16's `/v1/settlements` endpoints need, behind a service rather than a controller-held repository. */
@Service
public class SettlementQueryService {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository itemRepository;

    public SettlementQueryService(SettlementBatchRepository batchRepository, SettlementItemRepository itemRepository) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
    }

    public record BatchWithItems(SettlementBatchEntity batch, List<SettlementItemEntity> items) {}

    public List<SettlementBatchEntity> listBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<BatchWithItems> findBatchWithItems(UUID batchId) {
        return batchRepository.findById(batchId)
                .map(batch -> new BatchWithItems(batch, itemRepository.findByBatchId(batchId)));
    }
}
